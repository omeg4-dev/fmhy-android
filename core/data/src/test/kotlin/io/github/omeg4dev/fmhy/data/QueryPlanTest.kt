package io.github.omeg4dev.fmhy.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.user.DownloadRecord
import io.github.omeg4dev.fmhy.data.user.Follow
import io.github.omeg4dev.fmhy.data.user.HistoryItem
import io.github.omeg4dev.fmhy.data.user.Pin
import io.github.omeg4dev.fmhy.data.user.SitePrefs
import io.github.omeg4dev.fmhy.data.user.UserBookmark
import io.github.omeg4dev.fmhy.data.user.UserStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.Modifier
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QueryPlanTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private data class RecordedStatement(
        val store: String,
        val method: String,
        val sql: String,
    )

    private class RecordingDriver(
        val storeName: String,
        private val delegate: SQLiteDriver = BundledSQLiteDriver()
    ) : SQLiteDriver {
        val recorded = mutableListOf<RecordedStatement>()
        var currentMethod: String = ""

        override fun open(fileName: String): SQLiteConnection {
            val conn = delegate.open(fileName)
            return RecordingConnection(conn)
        }

        private inner class RecordingConnection(
            private val delegateConn: SQLiteConnection
        ) : SQLiteConnection {
            override fun prepare(sql: String): SQLiteStatement {
                recorded.add(RecordedStatement(storeName, currentMethod, sql))
                return delegateConn.prepare(sql)
            }

            override fun close() {
                delegateConn.close()
            }

            override fun inTransaction(): Boolean {
                return delegateConn.inTransaction()
            }
        }
    }

    private val allowlist: Map<String, String> = mapOf(
        "SELECT id, section_id, parent_id, level, title, anchor, ord FROM headings" to
            "full heading tree built in memory; 1k rows",
        "SELECT COUNT(*) + COALESCE(SUM(json_array_length(mirrors)), 0) + COALESCE(SUM(json_array_length(extra_links)), 0) FROM entries WHERE kind = 0" to
            "whole-catalog aggregate by design; cache candidate",
        "SELECT host, adblock, popups, javascript, desktop, dark, fix_site, block_redirects, updated_at FROM site_prefs ORDER BY host ASC" to
            "full export ordered by PK",
    )

    private val knownFindings: Map<String, String> = mapOf(
        "WHERE tier >= 1 AND kind = 0 AND is_base64 = 0 ORDER BY RANDOM()" to
            "hunt finding: randomTopPick ORDER BY RANDOM full scan",
        "mirrors LIKE ? ESCAPE '\\'" to
            "hunt finding: entryMatchesForHost leading-wildcard LIKE scan",
        "WHERE url LIKE ? ESCAPE '\\' OR name LIKE ? ESCAPE '\\' LIMIT 100" to
            "hunt finding: findLookalikeCandidates leading-wildcard LIKE scan",
        "SELECT entry_id FROM bookmarks WHERE folder_id = ?" to
            "hunt finding: bookmarks lacks index on folder_id",
        "DELETE FROM downloads WHERE status = ?" to
            "hunt finding: downloads lacks index on status",
    )

    private val skipped: Map<String, String> = emptyMap()

    private fun findRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "settings.gradle.kts").exists()) {
            val parent = dir.parentFile ?: break
            dir = parent
        }
        return dir
    }

    private fun normalizeSql(sql: String): String {
        return sql.replace(Regex("\\s+"), " ").trim()
    }

    private fun isPlanCheckable(sql: String): Boolean {
        val upper = normalizeSql(sql).uppercase()
        val skipKeywords = listOf(
            "PRAGMA", "BEGIN", "COMMIT", "END", "ROLLBACK", "SAVEPOINT",
            "RELEASE", "CREATE", "DROP", "ALTER", "ATTACH", "DETACH",
            "VACUUM", "ANALYZE", "REINDEX"
        )
        for (kw in skipKeywords) {
            if (upper.startsWith("$kw ") || upper == kw || upper.startsWith("$kw;")) {
                return false
            }
        }
        if ((upper.startsWith("INSERT ") || upper.startsWith("REPLACE ")) && !upper.contains("SELECT")) {
            return false
        }
        return true
    }

    private fun resolveTable(aliasOrTable: String, sql: String, conn: SQLiteConnection): String? {
        val upperAlias = aliasOrTable.uppercase()
        if (upperAlias == "CONSTANT" || upperAlias == "ROW" || aliasOrTable.startsWith("(") || aliasOrTable.isEmpty()) {
            return null
        }

        // Try to match alias in SQL: (FROM|JOIN)\s+(\w+)\s+(AS\s+)?<alias>\b
        val aliasRegex = Regex(
            """(?i)\b(?:FROM|JOIN)\s+([a-zA-Z0-9_]+)(?:\s+AS)?\s+""" + Regex.escape(aliasOrTable) + """\b"""
        )
        val match = aliasRegex.find(sql)
        val candidate = match?.groupValues?.get(1) ?: aliasOrTable

        // Check if candidate exists in sqlite_master as a table
        val exists = conn.prepare("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = ?").use { stmt ->
            stmt.bindText(1, candidate)
            if (stmt.step()) stmt.getLong(0) > 0 else false
        }
        return if (exists) candidate else null
    }

    private fun getPublicMethodNames(clazz: Class<*>): Set<String> {
        return clazz.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .filter { !it.isSynthetic && !it.isBridge }
            .filter { !it.name.endsWith("\$default") }
            .filter { !it.name.startsWith("access\$") }
            .filter { it.name != "close" }
            .filter { m ->
                val name = m.name
                val isProp = (name.startsWith("get") && m.parameterCount == 0 &&
                    (name in setOf("getDbFile", "getChanges") || "$" in name)) ||
                    (name.startsWith("set") && m.parameterCount == 1 && "$" in name)
                !isProp
            }
            .map { it.name }
            .toSet()
    }

    @Test
    fun testExplainQueryPlan() = runTest {
        val repoRoot = findRepoRoot()
        val snapshotGz = File(repoRoot, "app/src/main/assets/dataset/snapshot.gzbin")
        if (!snapshotGz.exists()) {
            Assume.assumeTrue(
                "QueryPlanTest skipped: bundled snapshot missing at ${snapshotGz.absolutePath}; build it with ./gw :core:data:buildSnapshot",
                false
            )
        }

        // 1. Fixture: dataset DB from snapshot
        val datasetDbFile = tempFolder.newFile("dataset_snapshot.db")
        GZIPInputStream(FileInputStream(snapshotGz)).use { input ->
            FileOutputStream(datasetDbFile).use { output ->
                input.copyTo(output)
            }
        }

        // 2. Fixture: user DB seeded to >= 1500 rows
        val userDbFile = tempFolder.newFile("user_test.db")
        UserStore.initDatabase(userDbFile)
        val seedConn = BundledSQLiteDriver().open(userDbFile.absolutePath)
        try {
            seedConn.execSQL("BEGIN IMMEDIATE;")
            seedConn.execSQL("INSERT INTO folders (id, name, ord, created_at) VALUES (1, 'Folder 1', 1, 1000);")
            for (i in 1..1500) {
                seedConn.execSQL(
                    "INSERT INTO bookmarks (entry_id, name, url, section_id, section_title, heading_path, saved_at, note, folder_id) " +
                        "VALUES ('b$i', 'Bookmark $i', 'https://example.com/b$i', 'ai', 'AI', 'AI > Chat', $i, '', 1);"
                )
                seedConn.execSQL(
                    "INSERT INTO history (id, url, entry_id, title, ts) VALUES ($i, 'https://example.com/h$i', 'e$i', 'History $i', $i);"
                )
                seedConn.execSQL(
                    "INSERT INTO downloads (id, url, file_name, status, created_at) VALUES ($i, 'https://example.com/d$i', 'file$i.zip', 'completed', $i);"
                )
                seedConn.execSQL(
                    "INSERT INTO site_prefs (host, adblock, popups, javascript, desktop, dark, fix_site, block_redirects, updated_at) " +
                        "VALUES ('host$i.com', 1, 0, 1, 0, 1, 0, 0, $i);"
                )
            }
            seedConn.execSQL("COMMIT;")
        } finally {
            seedConn.close()
        }

        val datasetDriver = RecordingDriver("DatasetStore")
        val userDriver = RecordingDriver("UserStore")
        val datasetStore = DatasetStore(datasetDbFile, driver = datasetDriver)
        val userStore = UserStore(userDbFile, driver = userDriver)

        val exercisedDataset = mutableSetOf<String>()
        val exercisedUser = mutableSetOf<String>()
        val failures = mutableListOf<String>()

        suspend fun call(storeName: String, methodName: String, block: suspend () -> Unit) {
            if (storeName == "DatasetStore") {
                datasetDriver.currentMethod = methodName
            } else {
                userDriver.currentMethod = methodName
            }
            try {
                block()
                if (storeName == "DatasetStore") {
                    exercisedDataset.add(methodName)
                } else {
                    exercisedUser.add(methodName)
                }
            } catch (t: Throwable) {
                failures.add("$storeName.$methodName failed with ${t.javaClass.name}: ${t.message}")
            }
        }

        // --- Exercise DatasetStore ---
        call("DatasetStore", "reopen") { datasetStore.reopen() }
        call("DatasetStore", "replaceDatabaseFiles") { datasetStore.replaceDatabaseFiles { Unit } }
        call("DatasetStore", "notifyChanged") { datasetStore.notifyChanged() }
        call("DatasetStore", "isAvailable") { datasetStore.isAvailable }
        call("DatasetStore", "withConnection") {
            datasetStore.withConnection { conn -> conn.prepare("SELECT 1").use { it.step() } }
        }
        call("DatasetStore", "withTransaction") {
            datasetStore.withTransaction { conn -> conn.prepare("SELECT 1").use { it.step() } }
        }
        call("DatasetStore", "maxNum") { datasetStore.maxNum() }
        call("DatasetStore", "entryCount") { datasetStore.entryCount() }
        call("DatasetStore", "oldestChangeTs") { datasetStore.oldestChangeTs() }
        call("DatasetStore", "entriesForSection") { datasetStore.entriesForSection("ai") }
        call("DatasetStore", "meta") { datasetStore.meta() }
        call("DatasetStore", "version") { datasetStore.version() }
        call("DatasetStore", "schemaVersion") { datasetStore.schemaVersion() }
        call("DatasetStore", "isSchemaCurrent") { datasetStore.isSchemaCurrent() }
        call("DatasetStore", "sections") {
            datasetStore.sections(SectionOrder.WEBSITE)
            datasetStore.sections(SectionOrder.ALPHABETICAL)
        }
        call("DatasetStore", "headings") { datasetStore.headings("ai") }
        call("DatasetStore", "allHeadings") { datasetStore.allHeadings() }
        call("DatasetStore", "entry") { datasetStore.entry("acba399d4271") }
        call("DatasetStore", "entries") { datasetStore.entries(listOf("acba399d4271", "nonexistent")) }
        call("DatasetStore", "randomTopPick") { datasetStore.randomTopPick() }
        call("DatasetStore", "randomTopEntries") { datasetStore.randomTopEntries("ai", 5) }
        call("DatasetStore", "changes") {
            datasetStore.changes(setOf(ChangeKind.ADDED), 0L, "ai", 10, 0)
            datasetStore.changes()
        }
        call("DatasetStore", "countAddedSince") { datasetStore.countAddedSince(0L) }
        call("DatasetStore", "recentChanges") {
            datasetStore.recentChanges(
                sinceTs = 0L,
                kinds = setOf(ChangeKind.ADDED, ChangeKind.EDITED),
                sectionIds = setOf("ai"),
                headingIds = setOf("ai/ai-chatbots"),
                limit = 10
            )
        }
        call("DatasetStore", "entryHistory") { datasetStore.entryHistory("acba399d4271") }
        call("DatasetStore", "linkCount") { datasetStore.linkCount() }
        call("DatasetStore", "siteListEntries") { datasetStore.siteListEntries(listOf("1377x.to")) }
        call("DatasetStore", "entryMatchesForHost") { datasetStore.entryMatchesForHost("reddit.com") }
        call("DatasetStore", "entriesMatchingHost") { datasetStore.entriesMatchingHost("reddit.com") }
        call("DatasetStore", "findLookalikeCandidates") { datasetStore.findLookalikeCandidates("reddit") }
        call("DatasetStore", "unsafeReason") { datasetStore.unsafeReason("https://1377x.to/torrent") }
        call("DatasetStore", "posts") { datasetStore.posts() }
        call("DatasetStore", "sectionItems") { datasetStore.sectionItems("ai") }
        call("DatasetStore", "criticalNotes") { datasetStore.criticalNotes() }
        call("DatasetStore", "criticalNote") { datasetStore.criticalNote("foo-critical") }
        call("DatasetStore", "isCriticalNote") { datasetStore.isCriticalNote("foo-critical") }
        call("DatasetStore", "criticalNotesForEntry") { datasetStore.criticalNotesForEntry("acba399d4271") }
        call("DatasetStore", "criticalNoteForEntry") { datasetStore.criticalNoteForEntry("acba399d4271") }
        call("DatasetStore", "criticalNoteIdsForEntries") { datasetStore.criticalNoteIdsForEntries(listOf("acba399d4271")) }
        call("DatasetStore", "criticalNoteForUrl") { datasetStore.criticalNoteForUrl("https://example.com") }
        call("DatasetStore", "closeSync") {
            datasetStore.closeSync()
            datasetStore.reopen()
        }

        // --- Exercise UserStore ---
        call("UserStore", "withConnection") {
            userStore.withConnection { conn -> conn.prepare("SELECT 1").use { it.step() } }
        }
        call("UserStore", "withTransaction") {
            userStore.withTransaction { conn -> conn.prepare("SELECT 1").use { it.step() } }
        }
        call("UserStore", "bookmarks") { userStore.bookmarks() }
        call("UserStore", "bookmark") { userStore.bookmark("b1") }
        call("UserStore", "upsertBookmark") {
            userStore.upsertBookmark(
                UserBookmark("b_test", "Test", "https://example.com/test", "sec1", "Sec 1", "Path", 2000, "", 1)
            )
        }
        call("UserStore", "removeBookmark") { userStore.removeBookmark("b_test") }
        call("UserStore", "removeBookmarkWithSnapshot") {
            userStore.upsertBookmark(
                UserBookmark("b_snap", "Test", "https://example.com/test", "sec1", "Sec 1", "Path", 2000, "", 1)
            )
            userStore.removeBookmarkWithSnapshot("b_snap")
        }
        call("UserStore", "restoreBookmark") {
            userStore.restoreBookmark(
                UserBookmark("b_snap", "Test", "https://example.com/test", "sec1", "Sec 1", "Path", 2000, "", 1)
            )
        }
        call("UserStore", "setNote") { userStore.setNote("b1", "Test note") }
        call("UserStore", "moveToFolder") { userStore.moveToFolder("b1", 1L) }
        call("UserStore", "insertBookmarksIfAbsent") {
            userStore.insertBookmarksIfAbsent(
                listOf(UserBookmark("b_new", "New", "https://example.com/new", "sec1", "Sec 1", "Path", 2000, "", 1))
            )
        }
        call("UserStore", "folders") { userStore.folders() }
        call("UserStore", "createFolder") {
            val f = userStore.createFolder("New Folder")
            userStore.renameFolder(f.id, "Renamed Folder")
        }
        call("UserStore", "renameFolder") { userStore.renameFolder(1L, "Folder 1 Renamed") }
        call("UserStore", "deleteFolder") {
            val f = userStore.createFolder("To Delete")
            userStore.deleteFolder(f.id)
        }
        call("UserStore", "deleteFolderWithSnapshot") {
            val f = userStore.createFolder("Snap Folder")
            val snap = userStore.deleteFolderWithSnapshot(f.id)
            if (snap != null) {
                userStore.restoreFolder(snap)
            }
        }
        call("UserStore", "restoreFolder") {
            // Checked via deleteFolderWithSnapshot restore
        }
        call("UserStore", "reorderFolders") { userStore.reorderFolders(listOf(1L)) }
        call("UserStore", "addHistory") { userStore.addHistory("https://example.com/hist", "e_hist", "History Title") }
        call("UserStore", "history") { userStore.history(20) }
        call("UserStore", "deleteHistory") { userStore.deleteHistory(listOf(1L)) }
        call("UserStore", "clearHistoryWithSnapshot") {
            val snap = userStore.clearHistoryWithSnapshot()
            userStore.restoreHistory(snap)
        }
        call("UserStore", "clearHistory") { userStore.clearHistory() }
        call("UserStore", "restoreHistory") {
            userStore.restoreHistory(listOf(HistoryItem(9999L, "https://example.com/9999", "e9999", "Restored", 1000L)))
        }
        call("UserStore", "follows") { userStore.follows() }
        call("UserStore", "follow") { userStore.follow("ai", "ai/ai-chatbots", "AI Chatbots") }
        call("UserStore", "unfollow") { userStore.unfollow("ai", "ai/ai-chatbots") }
        call("UserStore", "followSection") { userStore.followSection("ai", "AI Section") }
        call("UserStore", "unfollowSection") { userStore.unfollowSection("ai") }
        call("UserStore", "setSubsectionFollowed") { userStore.setSubsectionFollowed("ai", "ai/ai-chatbots", "AI Chatbots", true) }
        call("UserStore", "restoreFollow") { userStore.restoreFollow(Follow("ai", "ai/ai-chatbots", "AI Chatbots", 1000L, false)) }
        call("UserStore", "pins") { userStore.pins() }
        call("UserStore", "pin") { userStore.pin("ai", "ai/ai-chatbots", "AI Chatbots") }
        call("UserStore", "unpin") { userStore.unpin("ai", "ai/ai-chatbots") }
        call("UserStore", "restorePin") { userStore.restorePin(Pin("ai", "ai/ai-chatbots", "AI Chatbots", 1)) }
        call("UserStore", "reorderPins") { userStore.reorderPins(listOf("ai" to "ai/ai-chatbots")) }
        call("UserStore", "hiddenSections") { userStore.hiddenSections() }
        call("UserStore", "setSectionHidden") { userStore.setSectionHidden("ai", true) }
        call("UserStore", "sitePrefs") { userStore.sitePrefs("host1.com") }
        call("UserStore", "allSitePrefs") { userStore.allSitePrefs() }
        call("UserStore", "setSitePrefs") { userStore.setSitePrefs(SitePrefs("host_custom.com", adblock = true)) }
        call("UserStore", "clearSitePrefs") { userStore.clearSitePrefs("host_custom.com") }
        call("UserStore", "get") { userStore.get("test_key") }
        call("UserStore", "put") { userStore.put("test_key", "test_val") }
        call("UserStore", "deleteKvPrefix") { userStore.deleteKvPrefix("test_") }
        call("UserStore", "hideCriticalWarning") { userStore.hideCriticalWarning("test_note", "test_hash") }
        call("UserStore", "hiddenCriticalWarnings") { userStore.hiddenCriticalWarnings() }
        call("UserStore", "clearHiddenCriticalWarnings") { userStore.clearHiddenCriticalWarnings() }
        call("UserStore", "recordSectionOpen") { userStore.recordSectionOpen("ai") }
        call("UserStore", "topOpenedSections") { userStore.topOpenedSections(5) }
        call("UserStore", "exportJson") { userStore.exportJson(true) }
        call("UserStore", "importJson") {
            val json = userStore.exportJson(false)
            userStore.importJson(json)
        }
        call("UserStore", "extractBackupSettings") { userStore.extractBackupSettings(userStore.exportJson(false)) }
        call("UserStore", "insertDownload") {
            val id = userStore.insertDownload(
                DownloadRecord(url = "https://example.com/dl.zip", fileName = "dl.zip", status = "pending", createdAt = 1000L)
            )
            userStore.updateDownload(
                DownloadRecord(id = id, url = "https://example.com/dl.zip", fileName = "dl.zip", status = "completed", createdAt = 1000L)
            )
            userStore.deleteDownload(id)
        }
        call("UserStore", "updateDownload") {
            userStore.updateDownload(
                DownloadRecord(id = 1L, url = "https://example.com/d1", fileName = "file1.zip", status = "completed", createdAt = 1L)
            )
        }
        call("UserStore", "downloads") { userStore.downloads() }
        call("UserStore", "deleteDownload") { userStore.deleteDownload(2L) }
        call("UserStore", "deleteDownloadsWithStatus") { userStore.deleteDownloadsWithStatus("nonexistent_status") }

        if (failures.isNotEmpty()) {
            fail("Errors during exercise:\n" + failures.joinToString("\n"))
        }

        // --- Coverage Guard ---
        val datasetPublic = getPublicMethodNames(DatasetStore::class.java)
        val unexercisedDataset = datasetPublic - exercisedDataset - skipped.keys
        assertTrue(
            "QueryPlanTest does not exercise: $unexercisedDataset. Call them in exercise() or add to skipped with a reason.",
            unexercisedDataset.isEmpty()
        )

        val userPublic = getPublicMethodNames(UserStore::class.java)
        val unexercisedUser = userPublic - exercisedUser - skipped.keys
        assertTrue(
            "QueryPlanTest does not exercise: $unexercisedUser. Call them in exercise() or add to skipped with a reason.",
            unexercisedUser.isEmpty()
        )

        // --- Plan Check ---
        val plainDatasetConn = BundledSQLiteDriver().open(datasetDbFile.absolutePath)
        val plainUserConn = BundledSQLiteDriver().open(userDbFile.absolutePath)

        val violations = mutableListOf<String>()
        val matchedAllowlist = mutableSetOf<String>()
        val matchedFindings = mutableSetOf<String>()

        var datasetStmtCount = 0
        var userStmtCount = 0
        var violationCount = 0
        var allowlistCount = 0
        var knownFindingsCount = 0

        // Distinct statements
        val allRecorded = datasetDriver.recorded + userDriver.recorded
        val uniqueStatements = allRecorded.distinctBy { it.store to normalizeSql(it.sql) }

        for (item in uniqueStatements) {
            val sql = normalizeSql(item.sql)
            if (!isPlanCheckable(sql)) continue

            val conn = if (item.store == "DatasetStore") plainDatasetConn else plainUserConn
            if (item.store == "DatasetStore") {
                datasetStmtCount++
            } else {
                userStmtCount++
            }

            try {
                conn.prepare("EXPLAIN QUERY PLAN $sql").use { stmt ->
                    while (stmt.step()) {
                        val detail = stmt.getText(3)
                        if (detail.startsWith("SCAN ")) {
                            val isIndexed = listOf(
                                "USING INDEX",
                                "USING COVERING INDEX",
                                "USING INTEGER PRIMARY KEY",
                                "USING PRIMARY KEY",
                                "VIRTUAL TABLE"
                            ).any { detail.contains(it) }

                            if (!isIndexed) {
                                val scanRest = detail.removePrefix("SCAN ").trim()
                                val afterTable = if (scanRest.startsWith("TABLE ")) {
                                    scanRest.removePrefix("TABLE ").trim()
                                } else {
                                    scanRest
                                }
                                val rawToken = afterTable.split(Regex("\\s+"))[0]
                                val realTable = resolveTable(rawToken, sql, conn)
                                if (realTable != null) {
                                    val rowCount = conn.prepare("SELECT count(*) FROM $realTable").use { countStmt ->
                                        if (countStmt.step()) countStmt.getLong(0) else 0L
                                    }
                                    if (rowCount >= 1000) {
                                        val allowMatch = allowlist.entries.firstOrNull { sql.contains(it.key) }
                                        val findMatch = knownFindings.entries.firstOrNull { sql.contains(it.key) }

                                        if (findMatch != null) {
                                            matchedFindings.add(findMatch.key)
                                            knownFindingsCount++
                                            println("QUERY-PLAN FINDING store=${item.store} method=${item.method} table=$realTable rows=$rowCount plan=\"$detail\" sql=\"${sql.take(200)}\"")
                                        } else if (allowMatch != null) {
                                            matchedAllowlist.add(allowMatch.key)
                                            allowlistCount++
                                        } else {
                                            violationCount++
                                            violations.add(
                                                "store=${item.store} method=${item.method} table=$realTable rows=$rowCount plan=\"$detail\" sql=\"${sql.take(200)}\""
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // If EXPLAIN QUERY PLAN fails on dynamic SQL without parameters, record as error
                fail("EXPLAIN QUERY PLAN failed for SQL: $sql, error: ${e.message}")
            }
        }

        plainDatasetConn.close()
        plainUserConn.close()
        datasetStore.close()
        userStore.close()

        val totalChecked = datasetStmtCount + userStmtCount
        println("QueryPlanTest: $totalChecked statements checked ($datasetStmtCount dataset, $userStmtCount user), $violationCount violations, $allowlistCount allowlisted, $knownFindingsCount known findings")

        val staleAllow = allowlist.keys - matchedAllowlist
        val staleFind = knownFindings.keys - matchedFindings
        if (staleAllow.isNotEmpty()) {
            fail("Stale allowlist entries (matched no recorded statement): $staleAllow")
        }
        if (staleFind.isNotEmpty()) {
            fail("Stale knownFindings entries (matched no recorded statement): $staleFind")
        }

        if (violations.isNotEmpty()) {
            fail("Query plan violations found (${violations.size}):\n" + violations.joinToString("\n"))
        }
    }
}
