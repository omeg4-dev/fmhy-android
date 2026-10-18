package io.github.omeg4dev.fmhy.data.sync

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DATASET_SCHEMA
import io.github.omeg4dev.fmhy.data.DatasetStore
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class DatasetSync(
    private val store: DatasetStore,
    private val dir: File,
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val clock: () -> Long,
    private val log: (String) -> Unit,
    private val minIntervalSec: Long = 900L,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val stateFile = File(dir, "sync-state.json")

    private fun loadState(): SyncState {
        if (!stateFile.exists()) return SyncState()
        return try {
            json.decodeFromString<SyncState>(stateFile.readText())
        } catch (_: Exception) {
            SyncState()
        }
    }

    private fun saveState(etag: String?, lastCheck: Long, version: Long) {
        try {
            dir.mkdirs()
            val state = SyncState(etag = etag, lastCheck = lastCheck, version = version)
            stateFile.writeText(json.encodeToString(state))
        } catch (e: Exception) {
            log("DatasetSync: failed to save state: ${e.message}")
        }
    }

    suspend fun sync(force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            val state = loadState()
            val now = clock()

            if (!force && (now - state.lastCheck < minIntervalSec)) {
                return@withContext SyncResult.Skipped
            }

            val manifestUrl = "${baseUrl.trimEnd('/')}/manifest.json"
            val requestBuilder = Request.Builder().url(manifestUrl)
            if (state.etag != null) {
                requestBuilder.header("If-None-Match", state.etag)
            }

            val response = try {
                client.newCall(requestBuilder.build()).execute()
            } catch (e: Exception) {
                return@withContext SyncResult.Failed("Network error: ${e.message}")
            }

            response.use { resp ->
                if (resp.code == 304) {
                    log("DatasetSync: 304 not modified")
                    saveState(etag = state.etag, lastCheck = now, version = store.version())
                    return@withContext SyncResult.NotModified
                }

                if (!resp.isSuccessful) {
                    return@withContext SyncResult.Failed("HTTP ${resp.code}")
                }

                val bodyStr = resp.body.string()
                val manifest = try {
                    json.decodeFromString<Manifest>(bodyStr)
                } catch (e: Exception) {
                    return@withContext SyncResult.Failed("Failed to parse manifest: ${e.message}")
                }

                if (manifest.schema != DATASET_SCHEMA) {
                    return@withContext SyncResult.Failed("schema")
                }

                val localVersion = store.version()
                val newEtag = resp.header("ETag") ?: state.etag

                if (manifest.version <= localVersion) {
                    saveState(etag = newEtag, lastCheck = now, version = localVersion)
                    return@withContext SyncResult.UpToDate
                }

                // Check for unbroken delta chain
                val deltaByFrom = manifest.deltas.associateBy { it.from }
                val chain = mutableListOf<ManifestDelta>()
                var current = localVersion
                while (current < manifest.version) {
                    val nextDelta = deltaByFrom[current] ?: break
                    if (nextDelta.to <= current) break
                    chain.add(nextDelta)
                    current = nextDelta.to
                }

                val hasUnbrokenChain = (current == manifest.version && chain.isNotEmpty())

                if (hasUnbrokenChain) {
                    val deltaResult = downloadAndApplyDeltas(chain, manifest.version, localVersion)
                    if (deltaResult is SyncResult.Updated) {
                        saveState(etag = newEtag, lastCheck = now, version = manifest.version)
                        log("DatasetSync: updated $localVersion -> ${manifest.version} (+${deltaResult.added})")
                        return@withContext deltaResult
                    }
                    // If delta application failed, fallback or report failure?
                    // Brief says: "Any failure -> rollback, data unchanged, Failed."
                    return@withContext deltaResult
                } else {
                    val fullResult = downloadAndSwapFull(manifest, localVersion)
                    if (fullResult is SyncResult.Replaced) {
                        saveState(etag = newEtag, lastCheck = now, version = manifest.version)
                        log("DatasetSync: replaced $localVersion -> ${manifest.version}")
                        return@withContext fullResult
                    }
                    return@withContext fullResult
                }
            }
        } catch (e: Exception) {
            SyncResult.Failed("Unexpected error: ${e.message}")
        }
    }

    private suspend fun downloadAndApplyDeltas(
        chain: List<ManifestDelta>,
        targetVersion: Long,
        localVersion: Long,
    ): SyncResult {
        val downloadedDeltas = mutableListOf<Pair<ManifestDelta, DeltaPayload>>()

        for (deltaInfo in chain) {
            val deltaUrl = "${baseUrl.trimEnd('/')}/${deltaInfo.file}"
            val resp = try {
                client.newCall(Request.Builder().url(deltaUrl).build()).execute()
            } catch (e: Exception) {
                return SyncResult.Failed("Failed to download delta ${deltaInfo.file}: ${e.message}")
            }

            val bytes = resp.use { r ->
                if (!r.isSuccessful) return SyncResult.Failed("HTTP ${r.code} for delta ${deltaInfo.file}")
                r.body.bytes()
            }

            val actualSha = sha256(bytes)
            if (!actualSha.equals(deltaInfo.sha256, ignoreCase = true)) {
                return SyncResult.Failed("sha256 mismatch for delta ${deltaInfo.file}")
            }

            val payload = try {
                json.decodeFromString<DeltaPayload>(bytes.toString(Charsets.UTF_8))
            } catch (e: Exception) {
                return SyncResult.Failed("Failed to parse delta ${deltaInfo.file}: ${e.message}")
            }

            downloadedDeltas.add(deltaInfo to payload)
        }

        var totalAdded = 0

        val applyError = store.withConnection { conn ->
            conn.execSQL("PRAGMA foreign_keys = OFF;")
            conn.execSQL("BEGIN IMMEDIATE;")
            try {
                for ((_, delta) in downloadedDeltas) {
                    applySingleDelta(conn, delta)
                    for (ch in delta.changes_append) {
                        if (ch.str("kind") == "added") {
                            totalAdded++
                        }
                    }
                }
                conn.execSQL("COMMIT;")
                null
            } catch (e: Exception) {
                conn.execSQL("ROLLBACK;")
                e
            }
        }

        if (applyError != null) {
            return SyncResult.Failed("Failed to apply deltas: ${applyError.message}")
        }

        store.notifyChanged()
        return SyncResult.Updated(from = localVersion, to = targetVersion, added = totalAdded)
    }

    private fun applySingleDelta(conn: SQLiteConnection, delta: DeltaPayload) {
        // 1. Deletes in order: notes, entries, headings, sections, posts, unsafe_domains
        deleteRows(conn, "notes", "id", delta.delete.notes)
        deleteRows(conn, "entries", "id", delta.delete.entries)
        deleteRows(conn, "headings", "id", delta.delete.headings)
        deleteRows(conn, "sections", "id", delta.delete.sections)
        deleteRows(conn, "posts", "id", delta.delete.posts)
        deleteRows(conn, "unsafe_domains", "domain", delta.delete.unsafe_domains)

        // 2. Upserts in order: sections, headings, entries, notes, posts, unsafe_domains
        upsertSections(conn, delta.upsert.sections)
        upsertHeadings(conn, delta.upsert.headings)
        upsertEntries(conn, delta.upsert.entries)
        upsertNotes(conn, delta.upsert.notes)
        upsertPosts(conn, delta.upsert.posts)
        upsertUnsafeDomains(conn, delta.upsert.unsafe_domains)

        // 3. changes_append (plain INSERT)
        appendChanges(conn, delta.changes_append)

        // 4. changes_prune_before
        if (delta.changes_prune_before != null) {
            conn.prepare("DELETE FROM changes WHERE ts < ?").use { stmt ->
                stmt.bindLong(1, delta.changes_prune_before)
                stmt.step()
            }
        }

        // 5. meta with INSERT OR REPLACE
        for ((k, v) in delta.meta) {
            conn.prepare("INSERT OR REPLACE INTO meta (key, value) VALUES (?, ?)").use { stmt ->
                stmt.bindText(1, k)
                stmt.bindText(2, v)
                stmt.step()
            }
        }
    }

    private fun deleteRows(conn: SQLiteConnection, table: String, keyCol: String, ids: List<String>) {
        if (ids.isEmpty()) return
        conn.prepare("DELETE FROM $table WHERE $keyCol = ?").use { stmt ->
            for (id in ids) {
                stmt.bindText(1, id)
                stmt.step()
                stmt.reset()
            }
        }
    }

    private fun upsertSections(conn: SQLiteConnection, rows: List<JsonObject>) {
        if (rows.isEmpty()) return
        val sql = """
            INSERT INTO sections (id, title, description, file, ord)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                description = excluded.description,
                file = excluded.file,
                ord = excluded.ord
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            for (row in rows) {
                stmt.bindText(1, row.str("id"))
                stmt.bindText(2, row.str("title"))
                stmt.bindText(3, row.str("description"))
                stmt.bindText(4, row.str("file"))
                stmt.bindLong(5, row.long("ord"))
                stmt.step()
                stmt.reset()
            }
        }
    }

    private fun upsertHeadings(conn: SQLiteConnection, rows: List<JsonObject>) {
        if (rows.isEmpty()) return
        val sql = """
            INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                section_id = excluded.section_id,
                parent_id = excluded.parent_id,
                level = excluded.level,
                title = excluded.title,
                anchor = excluded.anchor,
                ord = excluded.ord
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            for (row in rows) {
                stmt.bindText(1, row.str("id"))
                stmt.bindText(2, row.str("section_id"))
                bindStringOrNull(stmt, 3, row.strOrNull("parent_id"))
                stmt.bindLong(4, row.long("level"))
                stmt.bindText(5, row.str("title"))
                stmt.bindText(6, row.str("anchor"))
                stmt.bindLong(7, row.long("ord"))
                stmt.step()
                stmt.reset()
            }
        }
    }

    private fun upsertEntries(conn: SQLiteConnection, rows: List<JsonObject>) {
        if (rows.isEmpty()) return
        val sql = """
            INSERT INTO entries (
                num, id, section_id, heading_id, ord, name, url, original_url,
                description, tags, heading_path, tier, is_inline_more, is_base64,
                mirrors, extra_links, first_seen, last_changed, starred_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                section_id = excluded.section_id,
                heading_id = excluded.heading_id,
                ord = excluded.ord,
                name = excluded.name,
                url = excluded.url,
                original_url = excluded.original_url,
                description = excluded.description,
                tags = excluded.tags,
                heading_path = excluded.heading_path,
                tier = excluded.tier,
                is_inline_more = excluded.is_inline_more,
                is_base64 = excluded.is_base64,
                mirrors = excluded.mirrors,
                extra_links = excluded.extra_links,
                first_seen = excluded.first_seen,
                last_changed = excluded.last_changed,
                starred_at = excluded.starred_at
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            for (row in rows) {
                stmt.bindLong(1, row.long("num"))
                stmt.bindText(2, row.str("id"))
                stmt.bindText(3, row.str("section_id"))
                bindStringOrNull(stmt, 4, row.strOrNull("heading_id"))
                stmt.bindLong(5, row.long("ord"))
                stmt.bindText(6, row.str("name"))
                stmt.bindText(7, row.str("url"))
                bindStringOrNull(stmt, 8, row.strOrNull("original_url"))
                stmt.bindText(9, row.str("description"))
                stmt.bindText(10, row.str("tags"))
                stmt.bindText(11, row.str("heading_path"))
                stmt.bindLong(12, row.long("tier"))
                stmt.bindLong(13, row.bool("is_inline_more"))
                stmt.bindLong(14, row.bool("is_base64"))
                stmt.bindText(15, row.jsonStr("mirrors"))
                stmt.bindText(16, row.jsonStr("extra_links"))
                stmt.bindLong(17, row.long("first_seen"))
                stmt.bindLong(18, row.long("last_changed"))
                bindLongOrNull(stmt, 19, row.longOrNull("starred_at"))
                stmt.step()
                stmt.reset()
            }
        }
    }

    private fun upsertNotes(conn: SQLiteConnection, rows: List<JsonObject>) {
        if (rows.isEmpty()) return
        val sql = """
            INSERT INTO notes (id, section_id, heading_id, ord, text_md)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                section_id = excluded.section_id,
                heading_id = excluded.heading_id,
                ord = excluded.ord,
                text_md = excluded.text_md
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            for (row in rows) {
                stmt.bindText(1, row.str("id"))
                stmt.bindText(2, row.str("section_id"))
                bindStringOrNull(stmt, 3, row.strOrNull("heading_id"))
                stmt.bindLong(4, row.long("ord"))
                stmt.bindText(5, row.str("text_md"))
                stmt.step()
                stmt.reset()
            }
        }
    }

    private fun upsertPosts(conn: SQLiteConnection, rows: List<JsonObject>) {
        if (rows.isEmpty()) return
        val sql = """
            INSERT INTO posts (id, title, date, body_md)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                date = excluded.date,
                body_md = excluded.body_md
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            for (row in rows) {
                stmt.bindText(1, row.str("id"))
                stmt.bindText(2, row.str("title"))
                stmt.bindLong(3, row.long("date"))
                stmt.bindText(4, row.str("body_md"))
                stmt.step()
                stmt.reset()
            }
        }
    }

    private fun upsertUnsafeDomains(conn: SQLiteConnection, rows: List<JsonObject>) {
        if (rows.isEmpty()) return
        val sql = """
            INSERT INTO unsafe_domains (domain, reason)
            VALUES (?, ?)
            ON CONFLICT(domain) DO UPDATE SET
                reason = excluded.reason
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            for (row in rows) {
                stmt.bindText(1, row.str("domain"))
                stmt.bindText(2, row.str("reason"))
                stmt.step()
                stmt.reset()
            }
        }
    }

    private fun appendChanges(conn: SQLiteConnection, rows: List<JsonObject>) {
        if (rows.isEmpty()) return
        val sql = """
            INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            for (row in rows) {
                stmt.bindLong(1, row.long("id"))
                stmt.bindLong(2, row.long("ts"))
                stmt.bindText(3, row.str("kind"))
                stmt.bindText(4, row.str("entry_id"))
                stmt.bindText(5, row.str("section_id"))
                stmt.bindText(6, row.str("name"))
                stmt.bindText(7, row.str("url"))
                stmt.bindText(8, row.str("detail"))
                stmt.bindText(9, row.str("commit_sha"))
                stmt.step()
                stmt.reset()
            }
        }
    }

    private suspend fun downloadAndSwapFull(manifest: Manifest, localVersion: Long): SyncResult {
        val fullTmpGz = File(dir, "full.tmp.gz")
        val nextDb = File(dir, "next.db")

        try {
            fullTmpGz.delete()
            nextDb.delete()

            val fullUrl = "${baseUrl.trimEnd('/')}/${manifest.full.file}"
            val resp = try {
                client.newCall(Request.Builder().url(fullUrl).build()).execute()
            } catch (e: Exception) {
                return SyncResult.Failed("Failed to download full db: ${e.message}")
            }

            resp.use { r ->
                if (!r.isSuccessful) return SyncResult.Failed("HTTP ${r.code} for full db")
                fullTmpGz.outputStream().use { out ->
                    r.body.byteStream().copyTo(out)
                }
            }

            val actualSha = sha256(fullTmpGz)
            if (!actualSha.equals(manifest.full.sha256, ignoreCase = true)) {
                fullTmpGz.delete()
                return SyncResult.Failed("Full download sha256 mismatch")
            }

            // Decompress gzip
            try {
                GZIPInputStream(fullTmpGz.inputStream()).use { gzIn ->
                    nextDb.outputStream().use { dbOut ->
                        gzIn.copyTo(dbOut)
                    }
                }
            } catch (e: Exception) {
                fullTmpGz.delete()
                nextDb.delete()
                return SyncResult.Failed("Failed to gunzip full db: ${e.message}")
            } finally {
                fullTmpGz.delete()
            }

            // Validate integrity and schema
            var valid = false
            try {
                val checkConn = BundledSQLiteDriver().open(nextDb.absolutePath)
                try {
                    var integrityOk = false
                    checkConn.prepare("PRAGMA integrity_check;").use { stmt ->
                        if (stmt.step() && stmt.getText(0).equals("ok", ignoreCase = true)) {
                            integrityOk = true
                        }
                    }
                    var schemaOk = false
                    checkConn.prepare("SELECT value FROM meta WHERE key = 'schema'").use { stmt ->
                        if (stmt.step() && stmt.getText(0) == DATASET_SCHEMA.toString()) {
                            schemaOk = true
                        }
                    }
                    valid = integrityOk && schemaOk
                } finally {
                    checkConn.close()
                }
            } catch (_: Exception) {
                valid = false
            }

            if (!valid) {
                nextDb.delete()
                return SyncResult.Failed("Integrity or schema check failed on full db")
            }

            // Atomic swap
            store.close()
            val currentDb = store.dbFile
            val wal = File(currentDb.parentFile, "${currentDb.name}-wal")
            val shm = File(currentDb.parentFile, "${currentDb.name}-shm")
            if (wal.exists()) wal.delete()
            if (shm.exists()) shm.delete()

            Files.move(
                nextDb.toPath(),
                currentDb.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )

            store.reopen()
            return SyncResult.Replaced(from = localVersion, to = manifest.version)
        } catch (e: Exception) {
            fullTmpGz.delete()
            nextDb.delete()
            return SyncResult.Failed("Full swap failed: ${e.message}")
        }
    }

    suspend fun installFromSnapshot(gzStream: InputStream, version: Long) = withContext(Dispatchers.IO) {
        val currentDb = store.dbFile
        if (currentDb.exists()) {
            val curVer = store.version()
            if (curVer >= version) return@withContext
        }

        val tmpDb = File(dir, "snapshot.tmp.db")
        try {
            tmpDb.delete()
            dir.mkdirs()

            GZIPInputStream(gzStream).use { gzIn ->
                tmpDb.outputStream().use { out ->
                    gzIn.copyTo(out)
                }
            }

            var valid = false
            try {
                val conn = BundledSQLiteDriver().open(tmpDb.absolutePath)
                try {
                    var integrityOk = false
                    conn.prepare("PRAGMA integrity_check;").use { stmt ->
                        if (stmt.step() && stmt.getText(0).equals("ok", ignoreCase = true)) {
                            integrityOk = true
                        }
                    }
                    var schemaOk = false
                    conn.prepare("SELECT value FROM meta WHERE key = 'schema'").use { stmt ->
                        if (stmt.step() && stmt.getText(0) == DATASET_SCHEMA.toString()) {
                            schemaOk = true
                        }
                    }
                    valid = integrityOk && schemaOk
                } finally {
                    conn.close()
                }
            } catch (_: Exception) {
                valid = false
            }

            if (!valid) {
                tmpDb.delete()
                log("DatasetSync: snapshot integrity/schema check failed")
                return@withContext
            }

            store.close()
            val wal = File(currentDb.parentFile, "${currentDb.name}-wal")
            val shm = File(currentDb.parentFile, "${currentDb.name}-shm")
            if (wal.exists()) wal.delete()
            if (shm.exists()) shm.delete()

            currentDb.parentFile?.mkdirs()
            Files.move(
                tmpDb.toPath(),
                currentDb.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )

            store.reopen()
            saveState(etag = null, lastCheck = clock(), version = store.version())
            log("DatasetSync: installed snapshot v$version")
        } catch (e: Exception) {
            tmpDb.delete()
            log("DatasetSync: failed to install snapshot: ${e.message}")
        }
    }

    private fun bindStringOrNull(stmt: SQLiteStatement, idx: Int, value: String?) {
        if (value == null) stmt.bindNull(idx) else stmt.bindText(idx, value)
    }

    private fun bindLongOrNull(stmt: SQLiteStatement, idx: Int, value: Long?) {
        if (value == null) stmt.bindNull(idx) else stmt.bindLong(idx, value)
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun JsonObject.str(key: String, default: String = ""): String =
        this[key]?.jsonPrimitive?.content ?: default

    private fun JsonObject.strOrNull(key: String): String? {
        val elem = this[key] ?: return null
        return if (elem is JsonNull) null else elem.jsonPrimitive.content
    }

    private fun JsonObject.long(key: String, default: Long = 0L): Long =
        this[key]?.jsonPrimitive?.longOrNull ?: default

    private fun JsonObject.longOrNull(key: String): Long? {
        val elem = this[key] ?: return null
        return if (elem is JsonNull) null else elem.jsonPrimitive.longOrNull
    }

    private fun JsonObject.bool(key: String): Long {
        val prim = this[key]?.jsonPrimitive ?: return 0L
        return if (prim.booleanOrNull == true || prim.longOrNull == 1L) 1L else 0L
    }

    private fun JsonObject.jsonStr(key: String, default: String = "[]"): String {
        val elem = this[key] ?: return default
        return if (elem is JsonPrimitive) elem.content else elem.toString()
    }
}
