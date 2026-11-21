package io.github.omeg4dev.fmhy.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.model.Change
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.CriticalNote
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Post
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.data.safety.HostMatch
import io.github.omeg4dev.fmhy.data.safety.SiteListRow
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
import kotlinx.serialization.json.Json

/**
 * Owns one dataset file (`current.db`). Opens it with [BundledSQLiteDriver] (WAL mode, foreign keys OFF).
 * All access goes through a single connection guarded by a [Mutex] on an injected [CoroutineDispatcher].
 */
open class DatasetStore(
    val dbFile: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Test seam: QueryPlanTest passes a recording driver. */
    private val driver: SQLiteDriver = BundledSQLiteDriver(),
) {

    private val mutex = Mutex()
    private var connection: SQLiteConnection? = null
    internal val json = Json { ignoreUnknownKeys = true }

    protected val _changes = MutableStateFlow(0L)
    open val changes: StateFlow<Long> = _changes.asStateFlow()

    init {
        if (dbFile.exists()) {
            openInternal()
        }
    }

    private fun openInternal() {
        if (!dbFile.exists()) return
        val conn = driver.open(dbFile.absolutePath)
        conn.execSQL("PRAGMA busy_timeout = 5000;")
        conn.execSQL("PRAGMA journal_mode = WAL;")
        conn.execSQL("PRAGMA foreign_keys = OFF;")
        connection = conn
        _changes.value = readVersionInternal(conn)
    }

    private fun readVersionInternal(conn: SQLiteConnection): Long {
        return try {
            conn.prepare("SELECT value FROM meta WHERE key = 'version'").use { stmt ->
                if (stmt.step()) stmt.getText(0).toLongOrNull() ?: 0L else 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun readSchemaInternal(conn: SQLiteConnection): Int? {
        return try {
            conn.prepare("SELECT value FROM meta WHERE key = 'schema'").use { stmt ->
                if (stmt.step()) stmt.getText(0).toIntOrNull() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    open suspend fun close() = withContext(dispatcher) {
        mutex.withLock {
            connection?.close()
            connection = null
        }
    }

    open suspend fun reopen() = withContext(dispatcher) {
        mutex.withLock {
            connection?.close()
            connection = null
            openInternal()
        }
    }

    /**
     * Closes the active connection, runs [block] to replace or mutate the underlying database
     * files while holding [mutex], and reopens the database before releasing the lock.
     * Readers calling [withConnection] will not observe an intermediate closed or half-replaced state.
     */
    open suspend fun <T> replaceDatabaseFiles(block: () -> T): T = withContext(dispatcher) {
        mutex.withLock {
            connection?.close()
            connection = null
            var blockException: Throwable? = null
            val result = try {
                block()
            } catch (t: Throwable) {
                blockException = t
                throw t
            } finally {
                if (dbFile.exists()) {
                    try {
                        openInternal()
                    } catch (openException: Throwable) {
                        blockException?.addSuppressed(openException)
                        if (blockException == null) {
                            throw openException
                        }
                    }
                }
            }
            result
        }
    }

    open suspend fun notifyChanged() = withContext(dispatcher) {
        mutex.withLock {
            connection?.let { conn ->
                _changes.value = readVersionInternal(conn)
            }
        }
    }

    /** False until a dataset file exists (first launch without a bundled snapshot). Readers must
     *  check this instead of calling queries, which throw when there is no database. */
    open val isAvailable: Boolean get() = connection != null || dbFile.exists()

    open suspend fun <T> withConnection(block: (SQLiteConnection) -> T): T = withContext(dispatcher) {
        mutex.withLock {
            var conn = connection
            if (conn == null && dbFile.exists()) {
                openInternal()
                conn = connection
            }
            checkNotNull(conn) { "Dataset database not open ($dbFile does not exist or was closed)" }
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

    open suspend fun maxNum(): Long = withConnection { conn ->
        conn.prepare("SELECT COALESCE(MAX(num), 0) FROM entries").use { stmt ->
            if (stmt.step()) stmt.getLong(0) else 0L
        }
    }

    open suspend fun entryCount(): Int = withConnection { conn ->
        conn.prepare("SELECT count(*) FROM entries").use { stmt ->
            if (stmt.step()) stmt.getLong(0).toInt() else 0
        }
    }

    /** Timestamp of the oldest recorded change, or null when there is no history yet. */
    open suspend fun oldestChangeTs(): Long? = withConnection { conn ->
        conn.prepare("SELECT MIN(ts) FROM changes").use { stmt ->
            if (stmt.step() && !stmt.isNull(0)) stmt.getLong(0) else null
        }
    }

    open suspend fun entriesForSection(sectionId: String): List<Entry> = entriesForSectionImpl(sectionId)

    open suspend fun meta(): Map<String, String> = withConnection { conn ->
        val result = mutableMapOf<String, String>()
        conn.prepare("SELECT key, value FROM meta").use { stmt ->
            while (stmt.step()) {
                result[stmt.getText(0)] = stmt.getText(1)
            }
        }
        result
    }

    open suspend fun version(): Long = withConnection { conn ->
        readVersionInternal(conn)
    }

    open suspend fun schemaVersion(): Int? = withContext(dispatcher) {
        mutex.withLock {
            if (!dbFile.exists()) return@withContext null
            var conn = connection
            if (conn == null) {
                openInternal()
                conn = connection
            }
            conn?.let { readSchemaInternal(it) }
        }
    }

    open suspend fun isSchemaCurrent(): Boolean = withContext(dispatcher) {
        mutex.withLock {
            if (!dbFile.exists()) return@withContext false
            var conn = connection
            if (conn == null) {
                openInternal()
                conn = connection
            }
            conn?.let { readSchemaInternal(it) == DATASET_SCHEMA } ?: false
        }
    }

    open suspend fun sections(order: SectionOrder = SectionOrder.WEBSITE): List<Section> = sectionsImpl(order)

    open suspend fun headings(sectionId: String): List<Heading> = headingsImpl(sectionId)

    /** Every heading of every section, in section then document order (for [link.CrossLinks]). */
    open suspend fun allHeadings(): List<Heading> = allHeadingsImpl()

    open suspend fun entry(id: String): Entry? = entryImpl(id)

    open suspend fun entries(ids: List<String>): List<Entry> = entriesImpl(ids)

    /** A random safe star: starred by FMHY, a real site (not an index or a link to another section),
     *  not a hidden base64 link, and not on FMHY's unsafe list. */
    open suspend fun randomTopPick(): Entry? = randomTopPickImpl()

    /** Random safe starred entries in a section.
     *  Filter: tier >= 1 AND kind = 0 AND is_base64 = 0 AND section_id = ?, ORDER BY RANDOM(),
     *  over-fetch 5x limit, drop unsafe links, take limit. */
    open suspend fun randomTopEntries(sectionId: String, limit: Int): List<Entry> = randomTopEntriesImpl(sectionId, limit)

    open suspend fun changes(
        kinds: Set<ChangeKind> = emptySet(),
        sinceTs: Long? = null,
        sectionId: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): List<Change> = changesImpl(kinds, sinceTs, sectionId, limit, offset)

    open suspend fun countAddedSince(ts: Long): Int = countAddedSinceImpl(ts)

    /**
     * Recent changes newest first, de-duplicated across sources by (kind, normalised url)
     * keeping the EARLIEST ts.
     *
     * @param sinceTs cutoff timestamp in unix epoch seconds
     * @param kinds kinds of changes to match (if empty, matches all)
     * @param sectionIds optional set of section IDs to restrict to
     * @param headingIds optional set of heading IDs to restrict to (matches heading or any descendant)
     * @param limit maximum number of items to return (default 500)
     */
    open suspend fun recentChanges(
        sinceTs: Long,
        kinds: Set<ChangeKind>,
        sectionIds: Set<String>? = null,
        headingIds: Set<String>? = null,
        limit: Int = 500,
    ): List<ChangeItem> = recentChangesImpl(sinceTs, kinds, sectionIds, headingIds, limit)

    /**
     * Change history for an entry, oldest first.
     */
    open suspend fun entryHistory(entryId: String): List<ChangeItem> = entryHistoryImpl(entryId)

    /**
     * Link count: entries (kind = 0) + all mirrors + all extra_links (JSON arrays).
     */
    open suspend fun linkCount(): Int = linkCountImpl()

    open suspend fun siteListEntries(keys: Collection<String>): List<SiteListRow> = siteListEntriesImpl(keys)

    open suspend fun entryMatchesForHost(host: String): List<HostMatch> = entryMatchesForHostImpl(host)

    open suspend fun entriesMatchingHost(host: String): List<Entry> = entriesMatchingHostImpl(host)

    open suspend fun findLookalikeCandidates(mainLabel: String): List<Entry> = findLookalikeCandidatesImpl(mainLabel)

    open suspend fun unsafeReason(url: String): String? = unsafeReasonImpl(url)

    open suspend fun posts(): List<Post> = postsImpl()

    open suspend fun sectionItems(sectionId: String): List<SectionItem> = sectionItemsImpl(sectionId)

    open suspend fun criticalNotes(): List<CriticalNote> = criticalNotesImpl()

    open suspend fun criticalNote(id: String): CriticalNote? = criticalNoteImpl(id)

    open suspend fun isCriticalNote(id: String): Boolean = isCriticalNoteImpl(id)

    open suspend fun criticalNotesForEntry(entryId: String): List<CriticalNote> = criticalNotesForEntryImpl(entryId)

    open suspend fun criticalNoteForEntry(entryId: String): CriticalNote? = criticalNoteForEntryImpl(entryId)

    open suspend fun criticalNoteIdsForEntries(entryIds: Collection<String>): Map<String, String> = criticalNoteIdsForEntriesImpl(entryIds)

    fun closeSync() {
        connection?.close()
        connection = null
    }

    companion object {
        fun normalizeEntryUrl(url: String): String {
            val clean = url.trim()
            val noFragment = clean.substringBefore('#')
            val scheme = if (noFragment.contains("://")) noFragment.substringBefore("://").lowercase() else "https"
            val rest = if (noFragment.contains("://")) noFragment.substringAfter("://") else noFragment
            val slashIdx = rest.indexOf('/')
            val hostPart = if (slashIdx != -1) rest.substring(0, slashIdx) else rest
            val pathPart = if (slashIdx != -1) rest.substring(slashIdx) else ""
            val normHost = hostPart.lowercase().removePrefix("www.")
            val normPath = pathPart.trimEnd('/')
            return "$scheme://$normHost$normPath"
        }

        fun loadSchemaSql(): String {
            val stream = DatasetStore::class.java.getResourceAsStream("/dataset.sql")
                ?: error("Cannot find /dataset.sql on classpath")
            return stream.bufferedReader().use { it.readText() }
        }

        fun executeSqlScript(conn: SQLiteConnection, sql: String) {
            val statements = mutableListOf<String>()
            val current = StringBuilder()
            var inTrigger = false

            for (line in sql.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("--") || trimmed.isEmpty()) continue

                current.appendLine(line)
                val upper = trimmed.uppercase()
                if (upper.contains("BEGIN")) {
                    inTrigger = true
                }

                if (trimmed.endsWith(";")) {
                    if (inTrigger) {
                        if (upper == "END;" || upper.endsWith(" END;")) {
                            inTrigger = false
                            statements.add(current.toString().trim())
                            current.clear()
                        }
                    } else {
                        statements.add(current.toString().trim())
                        current.clear()
                    }
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
            // Built under another name and moved in: readers poll dbFile.exists(), and opening a
            // half-created database while the schema script holds its lock crashed with SQLITE_BUSY.
            val tmp = File(file.parentFile, "${file.name}.init")
            tmp.delete()
            val conn = BundledSQLiteDriver().open(tmp.absolutePath)
            try {
                executeSqlScript(conn, schemaSql)
            } finally {
                conn.close()
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}
