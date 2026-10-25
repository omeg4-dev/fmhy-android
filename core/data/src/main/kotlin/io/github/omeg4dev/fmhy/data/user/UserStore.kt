package io.github.omeg4dev.fmhy.data.user

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Owns the user SQLite database (`user.db`).
 * Stores bookmarks, folders, browsing history, followed sections, pinned sections,
 * hidden sections, per-site preferences, and key-value pairs.
 */
open class UserStore(
    val dbFile: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    internal val targetVersion: Int = USER_DB_VERSION,
    internal val customMigrations: Map<Int, (SQLiteConnection) -> Unit> = emptyMap(),
) : Closeable {

    private val mutex = Mutex()
    private var connection: SQLiteConnection? = null
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    protected val _changes = MutableStateFlow(0L)
    open val changes: StateFlow<Long> = _changes.asStateFlow()

    init {
        if (dbFile.exists()) {
            openInternal()
        }
    }

    private fun openInternal() {
        if (!dbFile.exists()) {
            initDatabase(dbFile)
        }
        val conn = BundledSQLiteDriver().open(dbFile.absolutePath)
        conn.execSQL("PRAGMA busy_timeout = 5000;")
        conn.execSQL("PRAGMA journal_mode = WAL;")
        conn.execSQL("PRAGMA foreign_keys = ON;")
        migrateIfNeeded(conn)
        connection = conn
    }

    private fun bumpChanges() {
        _changes.value = _changes.value + 1L
    }

    open suspend fun <T> withConnection(block: (SQLiteConnection) -> T): T = withContext(dispatcher) {
        mutex.withLock {
            var conn = connection
            if (conn == null) {
                openInternal()
                conn = connection
            }
            checkNotNull(conn) { "User database not open ($dbFile does not exist or was closed)" }
            block(conn)
        }
    }

    open suspend fun <T> withTransaction(block: (SQLiteConnection) -> T): T = withConnection { conn ->
        conn.execSQL("BEGIN IMMEDIATE;")
        try {
            val result = block(conn)
            conn.execSQL("COMMIT;")
            result
        } catch (e: Throwable) {
            try {
                conn.execSQL("ROLLBACK;")
            } catch (_: Exception) {}
            throw e
        }
    }

    // --- Bookmarks ---

    suspend fun bookmarks(): List<UserBookmark> = withConnection { conn ->
        readBookmarksInternal(conn)
    }

    suspend fun bookmark(entryId: String): UserBookmark? = withConnection { conn ->
        readBookmarkInternal(conn, entryId)
    }

    suspend fun upsertBookmark(b: UserBookmark) = withConnection { conn ->
        val sql = """
            INSERT INTO bookmarks (entry_id, name, url, section_id, section_title, heading_path, saved_at, note, folder_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(entry_id) DO UPDATE SET
                name = excluded.name,
                url = excluded.url,
                section_id = excluded.section_id,
                section_title = excluded.section_title,
                heading_path = excluded.heading_path,
                saved_at = excluded.saved_at,
                note = excluded.note,
                folder_id = excluded.folder_id
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            bindBookmark(stmt, b)
            stmt.step()
        }
        bumpChanges()
    }

    suspend fun removeBookmark(entryId: String) = withConnection { conn ->
        conn.prepare("DELETE FROM bookmarks WHERE entry_id = ?").use { stmt ->
            stmt.bindText(1, entryId)
            stmt.step()
        }
        bumpChanges()
    }

    suspend fun removeBookmarkWithSnapshot(entryId: String): UserBookmark? = withTransaction { conn ->
        val bookmark = readBookmarkInternal(conn, entryId) ?: return@withTransaction null
        conn.prepare("DELETE FROM bookmarks WHERE entry_id = ?").use { stmt ->
            stmt.bindText(1, entryId)
            stmt.step()
        }
        bumpChanges()
        bookmark
    }

    suspend fun restoreBookmark(b: UserBookmark) = withConnection { conn ->
        val sql = """
            INSERT INTO bookmarks (entry_id, name, url, section_id, section_title, heading_path, saved_at, note, folder_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(entry_id) DO UPDATE SET
                name = excluded.name,
                url = excluded.url,
                section_id = excluded.section_id,
                section_title = excluded.section_title,
                heading_path = excluded.heading_path,
                saved_at = excluded.saved_at,
                note = excluded.note,
                folder_id = excluded.folder_id
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            bindBookmark(stmt, b)
            stmt.step()
        }
        bumpChanges()
    }

    suspend fun setNote(entryId: String, note: String) = withConnection { conn ->
        conn.prepare("UPDATE bookmarks SET note = ? WHERE entry_id = ?").use { stmt ->
            stmt.bindText(1, note)
            stmt.bindText(2, entryId)
            stmt.step()
        }
        bumpChanges()
    }

    suspend fun moveToFolder(entryId: String, folderId: Long?) = withConnection { conn ->
        conn.prepare("UPDATE bookmarks SET folder_id = ? WHERE entry_id = ?").use { stmt ->
            if (folderId != null) stmt.bindLong(1, folderId) else stmt.bindNull(1)
            stmt.bindText(2, entryId)
            stmt.step()
        }
        bumpChanges()
    }

    suspend fun insertBookmarksIfAbsent(list: List<UserBookmark>): Int = withTransaction { conn ->
        if (list.isEmpty()) return@withTransaction 0
        val countBefore = countRows(conn, "bookmarks")
        val sql = """
            INSERT OR IGNORE INTO bookmarks (entry_id, name, url, section_id, section_title, heading_path, saved_at, note, folder_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            for (b in list) {
                bindBookmark(stmt, b)
                stmt.step()
                stmt.reset()
            }
        }
        val countAfter = countRows(conn, "bookmarks")
        val inserted = (countAfter - countBefore).toInt()
        if (inserted > 0) {
            bumpChanges()
        }
        inserted
    }

    // --- Folders ---

    suspend fun folders(): List<Folder> = withConnection { conn ->
        readFoldersInternal(conn)
    }

    suspend fun createFolder(name: String): Folder = withTransaction { conn ->
        val nextOrd = conn.prepare("SELECT COALESCE(MAX(ord), -1) + 1 FROM folders").use { stmt ->
            if (stmt.step()) stmt.getLong(0).toInt() else 0
        }
        val createdAt = clock()
        conn.prepare("INSERT INTO folders (name, ord, created_at) VALUES (?, ?, ?)").use { stmt ->
            stmt.bindText(1, name)
            stmt.bindLong(2, nextOrd.toLong())
            stmt.bindLong(3, createdAt)
            stmt.step()
        }
        val id = conn.prepare("SELECT last_insert_rowid()").use { stmt ->
            if (stmt.step()) stmt.getLong(0) else 0L
        }
        bumpChanges()
        Folder(id = id, name = name, ord = nextOrd, createdAt = createdAt)
    }

    suspend fun renameFolder(id: Long, name: String) = withConnection { conn ->
        conn.prepare("UPDATE folders SET name = ? WHERE id = ?").use { stmt ->
            stmt.bindText(1, name)
            stmt.bindLong(2, id)
            stmt.step()
        }
        bumpChanges()
    }

    suspend fun deleteFolder(id: Long) = withConnection { conn ->
        conn.prepare("DELETE FROM folders WHERE id = ?").use { stmt ->
            stmt.bindLong(1, id)
            stmt.step()
        }
        bumpChanges()
    }

    data class DeletedFolderSnapshot(
        val folder: Folder,
        val bookmarkEntryIds: List<String>,
    )

    suspend fun deleteFolderWithSnapshot(id: Long): DeletedFolderSnapshot? = withTransaction { conn ->
        val folder = conn.prepare("SELECT id, name, ord, created_at FROM folders WHERE id = ?").use { stmt ->
            stmt.bindLong(1, id)
            if (stmt.step()) {
                Folder(
                    id = stmt.getLong(0),
                    name = stmt.getText(1),
                    ord = stmt.getLong(2).toInt(),
                    createdAt = stmt.getLong(3),
                )
            } else null
        } ?: return@withTransaction null

        val bookmarkIds = mutableListOf<String>()
        conn.prepare("SELECT entry_id FROM bookmarks WHERE folder_id = ?").use { stmt ->
            stmt.bindLong(1, id)
            while (stmt.step()) {
                bookmarkIds.add(stmt.getText(0))
            }
        }

        conn.prepare("DELETE FROM folders WHERE id = ?").use { stmt ->
            stmt.bindLong(1, id)
            stmt.step()
        }
        bumpChanges()
        DeletedFolderSnapshot(folder, bookmarkIds)
    }

    suspend fun restoreFolder(snapshot: DeletedFolderSnapshot) = withTransaction { conn ->
        val sql = """
            INSERT INTO folders (id, name, ord, created_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET name = excluded.name, ord = excluded.ord, created_at = excluded.created_at
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            stmt.bindLong(1, snapshot.folder.id)
            stmt.bindText(2, snapshot.folder.name)
            stmt.bindLong(3, snapshot.folder.ord.toLong())
            stmt.bindLong(4, snapshot.folder.createdAt)
            stmt.step()
        }
        if (snapshot.bookmarkEntryIds.isNotEmpty()) {
            conn.prepare("UPDATE bookmarks SET folder_id = ? WHERE entry_id = ?").use { stmt ->
                for (entryId in snapshot.bookmarkEntryIds) {
                    stmt.bindLong(1, snapshot.folder.id)
                    stmt.bindText(2, entryId)
                    stmt.step()
                    stmt.reset()
                }
            }
        }
        bumpChanges()
    }

    suspend fun reorderFolders(idsInOrder: List<Long>) = withTransaction { conn ->
        conn.prepare("UPDATE folders SET ord = ? WHERE id = ?").use { stmt ->
            for ((idx, id) in idsInOrder.withIndex()) {
                stmt.bindLong(1, idx.toLong())
                stmt.bindLong(2, id)
                stmt.step()
                stmt.reset()
            }
        }
        bumpChanges()
    }

    // --- History ---

    suspend fun addHistory(url: String, entryId: String?, title: String) = withTransaction { conn ->
        val now = clock()
        val window = 30L * 60L * 1000L
        val recentId = conn.prepare(
            "SELECT id FROM history WHERE url = ? AND ts >= ? ORDER BY ts DESC, id DESC LIMIT 1"
        ).use { stmt ->
            stmt.bindText(1, url)
            stmt.bindLong(2, now - window)
            if (stmt.step()) stmt.getLong(0) else null
        }

        if (recentId != null) {
            conn.prepare("UPDATE history SET ts = ?, title = ?, entry_id = ? WHERE id = ?").use { stmt ->
                stmt.bindLong(1, now)
                stmt.bindText(2, title)
                if (entryId != null) stmt.bindText(3, entryId) else stmt.bindNull(3)
                stmt.bindLong(4, recentId)
                stmt.step()
            }
        } else {
            conn.prepare("INSERT INTO history (url, entry_id, title, ts) VALUES (?, ?, ?, ?)").use { stmt ->
                stmt.bindText(1, url)
                if (entryId != null) stmt.bindText(2, entryId) else stmt.bindNull(2)
                stmt.bindText(3, title)
                stmt.bindLong(4, now)
                stmt.step()
            }
        }

        // Cap at 500 newest
        conn.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY ts DESC, id DESC LIMIT 500);")
        bumpChanges()
    }

    suspend fun history(limit: Int = 500): List<HistoryItem> = withConnection { conn ->
        val result = mutableListOf<HistoryItem>()
        val sql = "SELECT id, url, entry_id, title, ts FROM history ORDER BY ts DESC, id DESC LIMIT ?"
        conn.prepare(sql).use { stmt ->
            stmt.bindLong(1, limit.toLong())
            while (stmt.step()) {
                result.add(
                    HistoryItem(
                        id = stmt.getLong(0),
                        url = stmt.getText(1),
                        entryId = if (stmt.isNull(2)) null else stmt.getText(2),
                        title = stmt.getText(3),
                        ts = stmt.getLong(4),
                    )
                )
            }
        }
        result
    }

    suspend fun clearHistory() = withConnection { conn ->
        conn.execSQL("DELETE FROM history;")
        bumpChanges()
    }

    /** Removes the given history rows (the start screen's "remove this site"). */
    suspend fun deleteHistory(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        withTransaction { conn ->
            val sql = "DELETE FROM history WHERE id = ?"
            conn.prepare(sql).use { stmt ->
                for (id in ids) {
                    stmt.bindLong(1, id)
                    stmt.step()
                    stmt.reset()
                }
            }
            bumpChanges()
        }
    }

    suspend fun clearHistoryWithSnapshot(): List<HistoryItem> = withTransaction { conn ->
        val result = mutableListOf<HistoryItem>()
        val sql = "SELECT id, url, entry_id, title, ts FROM history ORDER BY ts DESC, id DESC"
        conn.prepare(sql).use { stmt ->
            while (stmt.step()) {
                result.add(
                    HistoryItem(
                        id = stmt.getLong(0),
                        url = stmt.getText(1),
                        entryId = if (stmt.isNull(2)) null else stmt.getText(2),
                        title = stmt.getText(3),
                        ts = stmt.getLong(4),
                    )
                )
            }
        }
        conn.execSQL("DELETE FROM history;")
        bumpChanges()
        result
    }

    suspend fun restoreHistory(items: List<HistoryItem>) = withTransaction { conn ->
        if (items.isEmpty()) return@withTransaction
        val sql = """
            INSERT INTO history (id, url, entry_id, title, ts)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET url = excluded.url, entry_id = excluded.entry_id, title = excluded.title, ts = excluded.ts
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            for (item in items) {
                stmt.bindLong(1, item.id)
                stmt.bindText(2, item.url)
                if (item.entryId != null) stmt.bindText(3, item.entryId) else stmt.bindNull(3)
                stmt.bindText(4, item.title)
                stmt.bindLong(5, item.ts)
                stmt.step()
                stmt.reset()
            }
        }
        bumpChanges()
    }

    // --- Follows / Pins / Hidden ---

    suspend fun follows(): List<Follow> = withConnection { conn ->
        val result = mutableListOf<Follow>()
        val sql = "SELECT section_id, heading_id, title, created_at FROM follows ORDER BY created_at DESC, section_id ASC, heading_id ASC"
        conn.prepare(sql).use { stmt ->
            while (stmt.step()) {
                result.add(
                    Follow(
                        sectionId = stmt.getText(0),
                        headingId = stmt.getText(1),
                        title = stmt.getText(2),
                        createdAt = stmt.getLong(3),
                    )
                )
            }
        }
        result
    }

    suspend fun follow(sectionId: String, headingId: String, title: String) = withConnection { conn ->
        val sql = """
            INSERT INTO follows (section_id, heading_id, title, created_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(section_id, heading_id) DO UPDATE SET title = excluded.title, created_at = excluded.created_at
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            stmt.bindText(1, sectionId)
            stmt.bindText(2, headingId)
            stmt.bindText(3, title)
            stmt.bindLong(4, clock())
            stmt.step()
        }
        bumpChanges()
    }

    suspend fun unfollow(sectionId: String, headingId: String) = withConnection { conn ->
        conn.prepare("DELETE FROM follows WHERE section_id = ? AND heading_id = ?").use { stmt ->
            stmt.bindText(1, sectionId)
            stmt.bindText(2, headingId)
            stmt.step()
        }
        bumpChanges()
    }

    suspend fun restoreFollow(follow: Follow) = withConnection { conn ->
        val sql = """
            INSERT INTO follows (section_id, heading_id, title, created_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(section_id, heading_id) DO UPDATE SET title = excluded.title, created_at = excluded.created_at
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            stmt.bindText(1, follow.sectionId)
            stmt.bindText(2, follow.headingId)
            stmt.bindText(3, follow.title)
            stmt.bindLong(4, follow.createdAt)
            stmt.step()
        }
        bumpChanges()
    }

    suspend fun pins(): List<Pin> = withConnection { conn ->
        val result = mutableListOf<Pin>()
        val sql = "SELECT section_id, heading_id, title, ord FROM pins ORDER BY ord ASC"
        conn.prepare(sql).use { stmt ->
            while (stmt.step()) {
                result.add(
                    Pin(
                        sectionId = stmt.getText(0),
                        headingId = stmt.getText(1),
                        title = stmt.getText(2),
                        ord = stmt.getLong(3).toInt(),
                    )
                )
            }
        }
        result
    }

    suspend fun pin(sectionId: String, headingId: String, title: String) = withTransaction { conn ->
        val exists = conn.prepare("SELECT count(*) FROM pins WHERE section_id = ? AND heading_id = ?").use { stmt ->
            stmt.bindText(1, sectionId)
            stmt.bindText(2, headingId)
            if (stmt.step()) stmt.getLong(0) > 0 else false
        }
        if (exists) {
            conn.prepare("UPDATE pins SET title = ? WHERE section_id = ? AND heading_id = ?").use { stmt ->
                stmt.bindText(1, title)
                stmt.bindText(2, sectionId)
                stmt.bindText(3, headingId)
                stmt.step()
            }
        } else {
            val nextOrd = conn.prepare("SELECT COALESCE(MAX(ord), -1) + 1 FROM pins").use { stmt ->
                if (stmt.step()) stmt.getLong(0).toInt() else 0
            }
            conn.prepare("INSERT INTO pins (section_id, heading_id, title, ord) VALUES (?, ?, ?, ?)").use { stmt ->
                stmt.bindText(1, sectionId)
                stmt.bindText(2, headingId)
                stmt.bindText(3, title)
                stmt.bindLong(4, nextOrd.toLong())
                stmt.step()
            }
        }
        bumpChanges()
    }

    suspend fun unpin(sectionId: String, headingId: String) = withConnection { conn ->
        conn.prepare("DELETE FROM pins WHERE section_id = ? AND heading_id = ?").use { stmt ->
            stmt.bindText(1, sectionId)
            stmt.bindText(2, headingId)
            stmt.step()
        }
        bumpChanges()
    }

    suspend fun restorePin(pin: Pin) = withConnection { conn ->
        val sql = """
            INSERT INTO pins (section_id, heading_id, title, ord)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(section_id, heading_id) DO UPDATE SET title = excluded.title, ord = excluded.ord
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            stmt.bindText(1, pin.sectionId)
            stmt.bindText(2, pin.headingId)
            stmt.bindText(3, pin.title)
            stmt.bindLong(4, pin.ord.toLong())
            stmt.step()
        }
        bumpChanges()
    }

    suspend fun reorderPins(keys: List<Pair<String, String>>) = withTransaction { conn ->
        conn.prepare("UPDATE pins SET ord = ? WHERE section_id = ? AND heading_id = ?").use { stmt ->
            for ((idx, key) in keys.withIndex()) {
                stmt.bindLong(1, idx.toLong())
                stmt.bindText(2, key.first)
                stmt.bindText(3, key.second)
                stmt.step()
                stmt.reset()
            }
        }
        bumpChanges()
    }

    suspend fun hiddenSections(): Set<String> = withConnection { conn ->
        val result = mutableSetOf<String>()
        conn.prepare("SELECT section_id FROM hidden_sections").use { stmt ->
            while (stmt.step()) {
                result.add(stmt.getText(0))
            }
        }
        result
    }

    suspend fun setSectionHidden(sectionId: String, hidden: Boolean) = withConnection { conn ->
        if (hidden) {
            conn.prepare("INSERT OR IGNORE INTO hidden_sections (section_id) VALUES (?)").use { stmt ->
                stmt.bindText(1, sectionId)
                stmt.step()
            }
        } else {
            conn.prepare("DELETE FROM hidden_sections WHERE section_id = ?").use { stmt ->
                stmt.bindText(1, sectionId)
                stmt.step()
            }
        }
        bumpChanges()
    }

    // --- Site Prefs ---

    suspend fun sitePrefs(host: String): SitePrefs? = withConnection { conn ->
        val sql = "SELECT host, adblock, popups, javascript, desktop, dark, updated_at FROM site_prefs WHERE host = ?"
        conn.prepare(sql).use { stmt ->
            stmt.bindText(1, host)
            if (stmt.step()) readSitePrefs(stmt) else null
        }
    }

    suspend fun allSitePrefs(): List<SitePrefs> = withConnection { conn ->
        val result = mutableListOf<SitePrefs>()
        val sql = "SELECT host, adblock, popups, javascript, desktop, dark, updated_at FROM site_prefs ORDER BY host ASC"
        conn.prepare(sql).use { stmt ->
            while (stmt.step()) {
                result.add(readSitePrefs(stmt))
            }
        }
        result
    }

    suspend fun setSitePrefs(p: SitePrefs) = withConnection { conn ->
        if (p.adblock == null && p.popups == null && p.javascript == null && p.desktop == null && p.dark == null) {
            conn.prepare("DELETE FROM site_prefs WHERE host = ?").use { stmt ->
                stmt.bindText(1, p.host)
                stmt.step()
            }
        } else {
            val sql = """
                INSERT INTO site_prefs (host, adblock, popups, javascript, desktop, dark, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(host) DO UPDATE SET
                    adblock = excluded.adblock,
                    popups = excluded.popups,
                    javascript = excluded.javascript,
                    desktop = excluded.desktop,
                    dark = excluded.dark,
                    updated_at = excluded.updated_at
            """.trimIndent()
            conn.prepare(sql).use { stmt ->
                stmt.bindText(1, p.host)
                bindBooleanOrNull(stmt, 2, p.adblock)
                bindBooleanOrNull(stmt, 3, p.popups)
                bindBooleanOrNull(stmt, 4, p.javascript)
                bindBooleanOrNull(stmt, 5, p.desktop)
                bindBooleanOrNull(stmt, 6, p.dark)
                stmt.bindLong(7, if (p.updatedAt > 0) p.updatedAt else clock())
                stmt.step()
            }
        }
        bumpChanges()
    }

    suspend fun clearSitePrefs(host: String) = withConnection { conn ->
        conn.prepare("DELETE FROM site_prefs WHERE host = ?").use { stmt ->
            stmt.bindText(1, host)
            stmt.step()
        }
        bumpChanges()
    }

    // --- Key / Value ---

    suspend fun get(key: String): String? = withConnection { conn ->
        conn.prepare("SELECT value FROM kv WHERE key = ?").use { stmt ->
            stmt.bindText(1, key)
            if (stmt.step()) stmt.getText(0) else null
        }
    }

    suspend fun put(key: String, value: String) = withConnection { conn ->
        val sql = "INSERT INTO kv (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value"
        conn.prepare(sql).use { stmt ->
            stmt.bindText(1, key)
            stmt.bindText(2, value)
            stmt.step()
        }
        bumpChanges()
    }

    /** Deletes every key/value row whose key starts with [prefix]. Returns the number removed. */
    suspend fun deleteKvPrefix(prefix: String): Int = withConnection { conn ->
        val n = conn.prepare("SELECT COUNT(*) FROM kv WHERE substr(key, 1, ?) = ?").use { stmt ->
            stmt.bindLong(1, prefix.length.toLong())
            stmt.bindText(2, prefix)
            if (stmt.step()) stmt.getLong(0).toInt() else 0
        }
        conn.prepare("DELETE FROM kv WHERE substr(key, 1, ?) = ?").use { stmt ->
            stmt.bindLong(1, prefix.length.toLong())
            stmt.bindText(2, prefix)
            stmt.step()
        }
        if (n > 0) bumpChanges()
        n
    }

    // --- Backup & Restore ---

    suspend fun exportJson(includeHistory: Boolean): String {
        val folderList = folders()
        val folderIdToName = folderList.associate { it.id to it.name }
        val bookmarkList = bookmarks().map { b ->
            BackupBookmark(
                entryId = b.entryId,
                name = b.name,
                url = b.url,
                sectionId = b.sectionId,
                sectionTitle = b.sectionTitle,
                headingPath = b.headingPath,
                savedAt = b.savedAt,
                note = b.note,
                folder = b.folderId?.let { folderIdToName[it] },
            )
        }
        val followList = follows()
        val pinList = pins()
        val hiddenList = hiddenSections().sorted()
        val historyList = if (includeHistory) {
            history().map { BackupHistoryItem(it.url, it.entryId, it.title, it.ts) }
        } else {
            null
        }

        val payload = BackupPayload(
            format = BACKUP_FORMAT,
            version = BACKUP_VERSION,
            exportedAt = clock(),
            folders = folderList.map { BackupFolder(it.name, it.ord, it.createdAt) },
            bookmarks = bookmarkList,
            follows = followList,
            pins = pinList,
            hidden = hiddenList,
            history = historyList,
        )
        return json.encodeToString(payload)
    }

    suspend fun importJson(text: String): ImportResult {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            throw IllegalArgumentException("Not an FMHY backup")
        }
        val format = try { root["format"]?.jsonPrimitive?.content } catch (_: Exception) { null }
        if (format != BACKUP_FORMAT) {
            throw IllegalArgumentException("Not an FMHY backup")
        }
        val version = try { root["version"]?.jsonPrimitive?.intOrNull ?: 1 } catch (_: Exception) { 1 }
        if (version > BACKUP_VERSION) {
            throw IllegalArgumentException("Backup is from a newer app version")
        }

        val payload = json.decodeFromString<BackupPayload>(text)

        return withTransaction { conn ->
            // 1. Folders: merge by name
            val existingFolders = readFoldersInternal(conn)
            val folderMap = existingFolders.associate { it.name to it.id }.toMutableMap()
            var insertedFoldersCount = 0
            for (bf in payload.folders) {
                if (!folderMap.containsKey(bf.name)) {
                    val nextOrd = conn.prepare("SELECT COALESCE(MAX(ord), -1) + 1 FROM folders").use { stmt ->
                        if (stmt.step()) stmt.getLong(0).toInt() else 0
                    }
                    conn.prepare("INSERT INTO folders (name, ord, created_at) VALUES (?, ?, ?)").use { stmt ->
                        stmt.bindText(1, bf.name)
                        stmt.bindLong(2, nextOrd.toLong())
                        stmt.bindLong(3, bf.createdAt)
                        stmt.step()
                    }
                    val newId = conn.prepare("SELECT last_insert_rowid()").use { stmt ->
                        if (stmt.step()) stmt.getLong(0) else 0L
                    }
                    folderMap[bf.name] = newId
                    insertedFoldersCount++
                }
            }

            // 2. Bookmarks: merge by entryId (keep newer savedAt; keep non-empty note)
            var mergedBookmarksCount = 0
            for (bb in payload.bookmarks) {
                val targetFolderId = bb.folder?.let { folderMap[it] }
                val existing = readBookmarkInternal(conn, bb.entryId)
                if (existing == null) {
                    val b = UserBookmark(
                        entryId = bb.entryId,
                        name = bb.name,
                        url = bb.url,
                        sectionId = bb.sectionId,
                        sectionTitle = bb.sectionTitle,
                        headingPath = bb.headingPath,
                        savedAt = bb.savedAt,
                        note = bb.note,
                        folderId = targetFolderId,
                    )
                    conn.prepare("""
                        INSERT INTO bookmarks (entry_id, name, url, section_id, section_title, heading_path, saved_at, note, folder_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()).use { stmt ->
                        bindBookmark(stmt, b)
                        stmt.step()
                    }
                    mergedBookmarksCount++
                } else {
                    if (bb.savedAt > existing.savedAt) {
                        // Incoming is newer
                        val finalNote = if (bb.note.isNotEmpty()) bb.note else existing.note
                        val b = UserBookmark(
                            entryId = bb.entryId,
                            name = bb.name,
                            url = bb.url,
                            sectionId = bb.sectionId,
                            sectionTitle = bb.sectionTitle,
                            headingPath = bb.headingPath,
                            savedAt = bb.savedAt,
                            note = finalNote,
                            folderId = targetFolderId ?: existing.folderId,
                        )
                        conn.prepare("""
                            UPDATE bookmarks SET name = ?, url = ?, section_id = ?, section_title = ?,
                                heading_path = ?, saved_at = ?, note = ?, folder_id = ?
                            WHERE entry_id = ?
                        """.trimIndent()).use { stmt ->
                            stmt.bindText(1, b.name)
                            stmt.bindText(2, b.url)
                            stmt.bindText(3, b.sectionId)
                            stmt.bindText(4, b.sectionTitle)
                            stmt.bindText(5, b.headingPath)
                            stmt.bindLong(6, b.savedAt)
                            stmt.bindText(7, b.note)
                            if (b.folderId != null) stmt.bindLong(8, b.folderId) else stmt.bindNull(8)
                            stmt.bindText(9, b.entryId)
                            stmt.step()
                        }
                        mergedBookmarksCount++
                    } else if (existing.note.isEmpty() && bb.note.isNotEmpty()) {
                        // Existing is same or newer, but keep non-empty note from backup
                        conn.prepare("UPDATE bookmarks SET note = ? WHERE entry_id = ?").use { stmt ->
                            stmt.bindText(1, bb.note)
                            stmt.bindText(2, existing.entryId)
                            stmt.step()
                        }
                        mergedBookmarksCount++
                    }
                }
            }

            // 3. Follows: union
            var insertedFollowsCount = 0
            val followSql = """
                INSERT OR IGNORE INTO follows (section_id, heading_id, title, created_at)
                VALUES (?, ?, ?, ?)
            """.trimIndent()
            conn.prepare(followSql).use { stmt ->
                for (f in payload.follows) {
                    val before = countRows(conn, "follows")
                    stmt.bindText(1, f.sectionId)
                    stmt.bindText(2, f.headingId)
                    stmt.bindText(3, f.title)
                    stmt.bindLong(4, f.createdAt)
                    stmt.step()
                    stmt.reset()
                    val after = countRows(conn, "follows")
                    if (after > before) insertedFollowsCount++
                }
            }

            // 4. Pins: union
            var insertedPinsCount = 0
            for (p in payload.pins) {
                val exists = conn.prepare("SELECT count(*) FROM pins WHERE section_id = ? AND heading_id = ?").use { stmt ->
                    stmt.bindText(1, p.sectionId)
                    stmt.bindText(2, p.headingId)
                    if (stmt.step()) stmt.getLong(0) > 0 else false
                }
                if (!exists) {
                    conn.prepare("INSERT INTO pins (section_id, heading_id, title, ord) VALUES (?, ?, ?, ?)").use { stmt ->
                        stmt.bindText(1, p.sectionId)
                        stmt.bindText(2, p.headingId)
                        stmt.bindText(3, p.title)
                        stmt.bindLong(4, p.ord.toLong())
                        stmt.step()
                    }
                    insertedPinsCount++
                }
            }

            // 5. Hidden: union
            for (secId in payload.hidden) {
                conn.prepare("INSERT OR IGNORE INTO hidden_sections (section_id) VALUES (?)").use { stmt ->
                    stmt.bindText(1, secId)
                    stmt.step()
                }
            }

            // 6. History: only if present
            if (payload.history != null) {
                val histSql = "INSERT INTO history (url, entry_id, title, ts) VALUES (?, ?, ?, ?)"
                conn.prepare(histSql).use { stmt ->
                    for (h in payload.history) {
                        stmt.bindText(1, h.url)
                        if (h.entryId != null) stmt.bindText(2, h.entryId) else stmt.bindNull(2)
                        stmt.bindText(3, h.title)
                        stmt.bindLong(4, h.ts)
                        stmt.step()
                        stmt.reset()
                    }
                }
                conn.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY ts DESC, id DESC LIMIT 500);")
            }

            bumpChanges()
            ImportResult(
                bookmarks = mergedBookmarksCount,
                folders = insertedFoldersCount,
                follows = insertedFollowsCount,
                pins = insertedPinsCount,
            )
        }
    }

    override fun close() {
        connection?.close()
        connection = null
    }

    companion object {
        const val USER_DB_VERSION = 1
        const val BACKUP_FORMAT = "fmhy-backup"
        const val BACKUP_VERSION = 1

        fun loadSchemaSql(): String {
            val stream = UserStore::class.java.getResourceAsStream("/user.sql")
                ?: error("Cannot find /user.sql on classpath")
            return stream.bufferedReader().use { it.readText() }
        }

        fun executeSqlScript(conn: SQLiteConnection, sql: String) {
            val statements = mutableListOf<String>()
            val current = StringBuilder()

            for (line in sql.lines()) {
                val lineWithoutComment = if (line.contains("--")) line.substringBefore("--") else line
                val trimmed = lineWithoutComment.trim()
                if (trimmed.isEmpty()) continue

                current.appendLine(trimmed)
                if (trimmed.endsWith(";")) {
                    statements.add(current.toString().trim())
                    current.clear()
                }
            }
            if (current.isNotBlank()) {
                statements.add(current.toString().trim())
            }

            for (stmt in statements) {
                if (stmt.isNotBlank()) {
                    conn.execSQL(stmt)
                }
            }
        }

        fun initDatabase(file: File, schemaSql: String = loadSchemaSql()) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.init")
            tmp.delete()
            val conn = BundledSQLiteDriver().open(tmp.absolutePath)
            try {
                conn.execSQL("PRAGMA foreign_keys = ON;")
                executeSqlScript(conn, schemaSql)
            } finally {
                conn.close()
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}
