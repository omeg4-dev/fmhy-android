package io.github.omeg4dev.fmhy.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.model.Change
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Link
import io.github.omeg4dev.fmhy.data.model.Note
import io.github.omeg4dev.fmhy.data.model.Post
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.safety.SiteListRow
import java.io.File
import java.net.URI
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
) {

    private val mutex = Mutex()
    private var connection: SQLiteConnection? = null
    private val json = Json { ignoreUnknownKeys = true }

    protected val _changes = MutableStateFlow(0L)
    open val changes: StateFlow<Long> = _changes.asStateFlow()

    init {
        if (dbFile.exists()) {
            openInternal()
        }
    }

    private fun openInternal() {
        if (!dbFile.exists()) return
        val conn = BundledSQLiteDriver().open(dbFile.absolutePath)
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

    open suspend fun entriesForSection(sectionId: String): List<Entry> = withConnection { conn ->
        val result = mutableListOf<Entry>()
        val sql = """
            SELECT num, id, section_id, heading_id, ord, name, url, original_url,
                   description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
                   mirrors, extra_links, first_seen, last_changed, starred_at
            FROM entries
            WHERE section_id = ?
            ORDER BY ord ASC
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            stmt.bindText(1, sectionId)
            while (stmt.step()) {
                result.add(readEntry(stmt))
            }
        }
        result
    }

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

    open suspend fun sections(order: SectionOrder = SectionOrder.WEBSITE): List<Section> = withConnection { conn ->
        val result = mutableListOf<Section>()
        val sql = """
            SELECT s.id, s.title, s.description, s.ord, count(e.id) AS entry_count
            FROM sections s
            LEFT JOIN entries e ON s.id = e.section_id
            GROUP BY s.id
            ORDER BY s.ord ASC
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            while (stmt.step()) {
                result.add(
                    Section(
                        id = stmt.getText(0),
                        title = stmt.getText(1),
                        description = stmt.getText(2),
                        ord = stmt.getLong(3).toInt(),
                        entryCount = stmt.getLong(4).toInt(),
                    )
                )
            }
        }
        when (order) {
            SectionOrder.WEBSITE -> {
                result.sortedWith(
                    compareBy<Section> {
                        val idx = io.github.omeg4dev.fmhy.data.parse.Sections.SIDEBAR_ORDER.indexOf(it.id)
                        if (idx >= 0) idx else Int.MAX_VALUE
                    }.thenBy { it.ord }
                )
            }
            SectionOrder.ALPHABETICAL -> {
                result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            }
        }
    }

    open suspend fun headings(sectionId: String): List<Heading> = withConnection { conn ->
        val result = mutableListOf<Heading>()
        val sql = """
            SELECT id, section_id, parent_id, level, title, anchor, ord
            FROM headings
            WHERE section_id = ?
            ORDER BY ord ASC
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            stmt.bindText(1, sectionId)
            while (stmt.step()) {
                result.add(readHeading(stmt))
            }
        }
        result
    }

    /** Every heading of every section, in section then document order (for [link.CrossLinks]). */
    open suspend fun allHeadings(): List<Heading> = withConnection { conn ->
        val result = mutableListOf<Heading>()
        val sql = """
            SELECT h.id, h.section_id, h.parent_id, h.level, h.title, h.anchor, h.ord
            FROM headings h JOIN sections s ON s.id = h.section_id
            ORDER BY s.ord ASC, h.ord ASC
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            while (stmt.step()) {
                result.add(readHeading(stmt))
            }
        }
        result
    }

    open suspend fun entry(id: String): Entry? = withConnection { conn ->
        val sql = """
            SELECT num, id, section_id, heading_id, ord, name, url, original_url,
                   description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
                   mirrors, extra_links, first_seen, last_changed, starred_at
            FROM entries
            WHERE id = ?
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            stmt.bindText(1, id)
            if (stmt.step()) readEntry(stmt) else null
        }
    }

    open suspend fun entries(ids: List<String>): List<Entry> = withConnection { conn ->
        if (ids.isEmpty()) return@withConnection emptyList()
        val result = mutableListOf<Entry>()
        val sql = """
            SELECT num, id, section_id, heading_id, ord, name, url, original_url,
                   description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
                   mirrors, extra_links, first_seen, last_changed, starred_at
            FROM entries
            WHERE id = ?
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            for (id in ids) {
                stmt.bindText(1, id)
                if (stmt.step()) {
                    result.add(readEntry(stmt))
                }
                stmt.reset()
            }
        }
        result
    }

    /** A random safe star: starred by FMHY, a real site (not an index or a link to another section),
     *  not a hidden base64 link, and not on FMHY's unsafe list. */
    open suspend fun randomTopPick(): Entry? {
        val candidates = withConnection { conn ->
            val sql = """
                SELECT num, id, section_id, heading_id, ord, name, url, original_url,
                       description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
                       mirrors, extra_links, first_seen, last_changed, starred_at
                FROM entries
                WHERE tier >= 1 AND kind = 0 AND is_base64 = 0
                ORDER BY RANDOM()
                LIMIT 20
            """.trimIndent()
            conn.prepare(sql).use { stmt ->
                buildList { while (stmt.step()) add(readEntry(stmt)) }
            }
        }
        return candidates.firstOrNull { unsafeReason(it.url) == null }
    }

    open suspend fun changes(
        kinds: Set<ChangeKind> = emptySet(),
        sinceTs: Long? = null,
        sectionId: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): List<Change> = withConnection { conn ->
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (kinds.isNotEmpty()) {
            val placeholders = kinds.joinToString(",") { "?" }
            conditions.add("kind IN ($placeholders)")
            kinds.forEach { params.add(it.value) }
        }
        if (sinceTs != null) {
            conditions.add("ts >= ?")
            params.add(sinceTs)
        }
        if (sectionId != null) {
            conditions.add("section_id = ?")
            params.add(sectionId)
        }

        val whereClause = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
        val sql = """
            SELECT id, ts, kind, entry_id, section_id, name, url, detail
            FROM changes
            $whereClause
            ORDER BY ts DESC, id DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val result = mutableListOf<Change>()
        conn.prepare(sql).use { stmt ->
            var idx = 1
            for (p in params) {
                when (p) {
                    is String -> stmt.bindText(idx++, p)
                    is Long -> stmt.bindLong(idx++, p)
                    is Int -> stmt.bindLong(idx++, p.toLong())
                }
            }
            stmt.bindLong(idx++, limit.toLong())
            stmt.bindLong(idx, offset.toLong())

            while (stmt.step()) {
                result.add(
                    Change(
                        id = stmt.getLong(0),
                        ts = stmt.getLong(1),
                        kind = ChangeKind.fromString(stmt.getText(2)),
                        entryId = stmt.getText(3),
                        sectionId = stmt.getText(4),
                        name = stmt.getText(5),
                        url = stmt.getText(6),
                        detail = stmt.getText(7),
                    )
                )
            }
        }
        result
    }

    open suspend fun countAddedSince(ts: Long): Int = withConnection { conn ->
        conn.prepare("SELECT count(*) FROM changes WHERE kind = 'added' AND ts > ?").use { stmt ->
            stmt.bindLong(1, ts)
            if (stmt.step()) stmt.getLong(0).toInt() else 0
        }
    }

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
    ): List<ChangeItem> = withConnection { conn ->
        // 1. If headingIds filter is present, resolve all descendant headings
        val matchingHeadingIds: Set<String>? = if (headingIds != null) {
            val allH = mutableListOf<Heading>()
            conn.prepare("SELECT id, section_id, parent_id, level, title, anchor, ord FROM headings").use { stmt ->
                while (stmt.step()) allH.add(readHeading(stmt))
            }
            val parentToChildren = allH.groupBy { it.parentId }
            val resolved = mutableSetOf<String>()
            val queue = ArrayDeque(headingIds)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (resolved.add(current)) {
                    parentToChildren[current]?.forEach { queue.add(it.id) }
                }
            }
            resolved
        } else null

        // 2. Query changes matching sinceTs, kinds, and sectionIds
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        conditions.add("ts >= ?")
        params.add(sinceTs)

        if (kinds.isNotEmpty()) {
            val placeholders = kinds.joinToString(",") { "?" }
            conditions.add("kind IN ($placeholders)")
            kinds.forEach { params.add(it.value) }
        }
        if (sectionIds != null && sectionIds.isNotEmpty()) {
            val placeholders = sectionIds.joinToString(",") { "?" }
            conditions.add("section_id IN ($placeholders)")
            sectionIds.forEach { params.add(it) }
        }

        val whereClause = "WHERE " + conditions.joinToString(" AND ")
        val sql = """
            SELECT id, ts, kind, entry_id, section_id, name, url, detail
            FROM changes
            $whereClause
            ORDER BY ts ASC, id ASC
        """.trimIndent()

        val rawChanges = mutableListOf<Change>()
        conn.prepare(sql).use { stmt ->
            var idx = 1
            for (p in params) {
                when (p) {
                    is String -> stmt.bindText(idx++, p)
                    is Long -> stmt.bindLong(idx++, p)
                    is Int -> stmt.bindLong(idx++, p.toLong())
                }
            }
            while (stmt.step()) {
                rawChanges.add(
                    Change(
                        id = stmt.getLong(0),
                        ts = stmt.getLong(1),
                        kind = ChangeKind.fromString(stmt.getText(2)),
                        entryId = stmt.getText(3),
                        sectionId = stmt.getText(4),
                        name = stmt.getText(5),
                        url = stmt.getText(6),
                        detail = stmt.getText(7),
                    )
                )
            }
        }

        // 3. Deduplicate across sources by (kind, normalised url) keeping EARLIEST ts
        val earliestByKindAndNormUrl = LinkedHashMap<Pair<ChangeKind, String>, Change>()
        for (c in rawChanges) {
            val key = c.kind to normalizeEntryUrl(c.url)
            if (!earliestByKindAndNormUrl.containsKey(key)) {
                earliestByKindAndNormUrl[key] = c
            }
        }

        // 4. Batch fetch entries for the deduplicated changes
        val entryIds = earliestByKindAndNormUrl.values.map { it.entryId }.distinct()
        val entryMap = mutableMapOf<String, Entry>()
        if (entryIds.isNotEmpty()) {
            val sqlEntry = """
                SELECT num, id, section_id, heading_id, ord, name, url, original_url,
                       description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
                       mirrors, extra_links, first_seen, last_changed, starred_at
                FROM entries
                WHERE id = ?
            """.trimIndent()
            conn.prepare(sqlEntry).use { stmt ->
                for (eid in entryIds) {
                    stmt.bindText(1, eid)
                    if (stmt.step()) {
                        entryMap[eid] = readEntry(stmt)
                    }
                    stmt.reset()
                }
            }
        }

        // 5. Filter by headingIds if requested
        val filtered = earliestByKindAndNormUrl.values.filter { change ->
            if (matchingHeadingIds != null) {
                val entry = entryMap[change.entryId]
                entry != null && entry.headingId != null && matchingHeadingIds.contains(entry.headingId)
            } else {
                true
            }
        }

        // 6. Sort newest first (ts DESC, id DESC), limit, and map to ChangeItem
        filtered
            .sortedWith(compareByDescending<Change> { it.ts }.thenByDescending { it.id })
            .take(limit)
            .map { change ->
                ChangeItem(
                    id = change.id,
                    ts = change.ts,
                    kind = change.kind,
                    entryId = change.entryId,
                    sectionId = change.sectionId,
                    name = change.name,
                    url = change.url,
                    detail = change.detail,
                    entry = entryMap[change.entryId],
                )
            }
    }

    /**
     * Change history for an entry, oldest first.
     */
    open suspend fun entryHistory(entryId: String): List<ChangeItem> = withConnection { conn ->
        val sqlEntry = """
            SELECT num, id, section_id, heading_id, ord, name, url, original_url,
                   description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
                   mirrors, extra_links, first_seen, last_changed, starred_at
            FROM entries
            WHERE id = ?
        """.trimIndent()
        val currentEntry = conn.prepare(sqlEntry).use { stmt ->
            stmt.bindText(1, entryId)
            if (stmt.step()) readEntry(stmt) else null
        }

        val sql = """
            SELECT id, ts, kind, entry_id, section_id, name, url, detail
            FROM changes
            WHERE entry_id = ?
            ORDER BY ts ASC, id ASC
        """.trimIndent()
        val result = mutableListOf<ChangeItem>()
        conn.prepare(sql).use { stmt ->
            stmt.bindText(1, entryId)
            while (stmt.step()) {
                result.add(
                    ChangeItem(
                        id = stmt.getLong(0),
                        ts = stmt.getLong(1),
                        kind = ChangeKind.fromString(stmt.getText(2)),
                        entryId = stmt.getText(3),
                        sectionId = stmt.getText(4),
                        name = stmt.getText(5),
                        url = stmt.getText(6),
                        detail = stmt.getText(7),
                        entry = currentEntry,
                    )
                )
            }
        }
        result
    }

    /**
     * Map of entryId to timestamp (unix epoch seconds) when it was added:
     * earliest 'added' ts in changes table, falling back to entries.first_seen.
     */
    open suspend fun addedAt(entryIds: Collection<String>): Map<String, Long> = withConnection { conn ->
        if (entryIds.isEmpty()) return@withConnection emptyMap()
        val result = mutableMapOf<String, Long>()

        for (chunk in entryIds.chunked(500)) {
            val placeholders = chunk.joinToString(",") { "?" }

            // First get first_seen from entries
            val sqlEntries = "SELECT id, first_seen FROM entries WHERE id IN ($placeholders)"
            conn.prepare(sqlEntries).use { stmt ->
                chunk.forEachIndexed { i, id -> stmt.bindText(i + 1, id) }
                while (stmt.step()) {
                    result[stmt.getText(0)] = stmt.getLong(1)
                }
            }

            // Then override with earliest 'added' ts from changes if available
            val sqlChanges = "SELECT entry_id, MIN(ts) FROM changes WHERE kind = 'added' AND entry_id IN ($placeholders) GROUP BY entry_id"
            conn.prepare(sqlChanges).use { stmt ->
                chunk.forEachIndexed { i, id -> stmt.bindText(i + 1, id) }
                while (stmt.step()) {
                    result[stmt.getText(0)] = stmt.getLong(1)
                }
            }
        }

        result
    }

    /**
     * Link count: entries (kind = 0) + all mirrors + all extra_links (JSON arrays).
     */
    open suspend fun linkCount(): Int = withConnection { conn ->
        val sql = "SELECT COUNT(*) + COALESCE(SUM(json_array_length(mirrors)), 0) + COALESCE(SUM(json_array_length(extra_links)), 0) FROM entries WHERE kind = 0"
        try {
            conn.prepare(sql).use { stmt ->
                if (stmt.step()) stmt.getLong(0).toInt() else 0
            }
        } catch (_: Exception) {
            conn.prepare("SELECT mirrors, extra_links FROM entries WHERE kind = 0").use { stmt ->
                var count = 0
                while (stmt.step()) {
                    count++
                    val m = stmt.getText(0)
                    val e = stmt.getText(1)
                    if (m.isNotEmpty() && m != "[]") {
                        count += try { json.decodeFromString<List<Link>>(m).size } catch (_: Exception) { 0 }
                    }
                    if (e.isNotEmpty() && e != "[]") {
                        count += try { json.decodeFromString<List<Link>>(e).size } catch (_: Exception) { 0 }
                    }
                }
                count
            }
        }
    }

    open suspend fun siteListEntries(keys: Collection<String>): List<SiteListRow> = withConnection { conn ->
        if (keys.isEmpty()) return@withConnection emptyList()
        val result = mutableListOf<SiteListRow>()
        for (chunk in keys.chunked(500)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val sql = "SELECT key, list, reason FROM site_lists WHERE key IN ($placeholders)"
            conn.prepare(sql).use { stmt ->
                chunk.forEachIndexed { i, k -> stmt.bindText(i + 1, k) }
                while (stmt.step()) {
                    result.add(
                        SiteListRow(
                            key = stmt.getText(0),
                            list = stmt.getText(1),
                            reason = stmt.getText(2),
                        )
                    )
                }
            }
        }
        result
    }

    open suspend fun entriesMatchingHost(host: String): List<Entry> = withConnection { conn ->
        val cleanHost = host.lowercase().removePrefix("www.")
        val sql = """
            SELECT num, id, section_id, heading_id, ord, name, url, original_url,
                   description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
                   mirrors, extra_links, first_seen, last_changed, starred_at
            FROM entries
            WHERE url LIKE ? OR url LIKE ? OR url LIKE ? OR url LIKE ?
        """.trimIndent()
        val result = mutableListOf<Entry>()
        conn.prepare(sql).use { stmt ->
            stmt.bindText(1, "https://$cleanHost%")
            stmt.bindText(2, "http://$cleanHost%")
            stmt.bindText(3, "https://www.$cleanHost%")
            stmt.bindText(4, "http://www.$cleanHost%")
            while (stmt.step()) {
                result.add(readEntry(stmt))
            }
        }
        result
    }

    open suspend fun unsafeReason(url: String): String? = withConnection { conn ->
        val host = extractHost(url) ?: return@withConnection null
        val candidates = generateHostCandidates(host)
        if (candidates.isEmpty()) return@withConnection null

        val placeholders = candidates.joinToString(",") { "?" }
        val sql = "SELECT domain, reason FROM unsafe_domains WHERE domain IN ($placeholders)"

        val matches = mutableListOf<Pair<String, String>>()
        conn.prepare(sql).use { stmt ->
            for (i in candidates.indices) {
                stmt.bindText(i + 1, candidates[i])
            }
            while (stmt.step()) {
                matches.add(stmt.getText(0) to stmt.getText(1))
            }
        }

        // Return reason for the longest matching domain (most specific), or first
        matches.maxByOrNull { it.first.length }?.second
    }

    open suspend fun posts(): List<Post> = withConnection { conn ->
        val result = mutableListOf<Post>()
        val sql = "SELECT id, title, date, body_md FROM posts ORDER BY date DESC"
        conn.prepare(sql).use { stmt ->
            while (stmt.step()) {
                result.add(
                    Post(
                        id = stmt.getText(0),
                        title = stmt.getText(1),
                        date = stmt.getLong(2),
                        bodyMd = stmt.getText(3),
                    )
                )
            }
        }
        result
    }

    open suspend fun sectionItems(sectionId: String): List<SectionItem> = withConnection { conn ->
        // Retrieve headings
        val headingList = mutableListOf<Heading>()
        conn.prepare("""
            SELECT id, section_id, parent_id, level, title, anchor, ord
            FROM headings
            WHERE section_id = ?
            ORDER BY ord ASC
        """.trimIndent()).use { stmt ->
            stmt.bindText(1, sectionId)
            while (stmt.step()) {
                headingList.add(readHeading(stmt))
            }
        }

        // Retrieve notes
        val noteList = mutableListOf<Note>()
        conn.prepare("""
            SELECT id, section_id, heading_id, ord, text_md
            FROM notes
            WHERE section_id = ?
            ORDER BY ord ASC
        """.trimIndent()).use { stmt ->
            stmt.bindText(1, sectionId)
            while (stmt.step()) {
                noteList.add(
                    Note(
                        id = stmt.getText(0),
                        sectionId = stmt.getText(1),
                        headingId = if (stmt.isNull(2)) null else stmt.getText(2),
                        ord = stmt.getLong(3).toInt(),
                        textMd = stmt.getText(4),
                    )
                )
            }
        }

        // Retrieve entries
        val entryList = mutableListOf<Entry>()
        conn.prepare("""
            SELECT num, id, section_id, heading_id, ord, name, url, original_url,
                   description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
                   mirrors, extra_links, first_seen, last_changed, starred_at
            FROM entries
            WHERE section_id = ?
            ORDER BY ord ASC
        """.trimIndent()).use { stmt ->
            stmt.bindText(1, sectionId)
            while (stmt.step()) {
                entryList.add(readEntry(stmt))
            }
        }

        // Headings and notes/entries are numbered by separate counters (headings: document order
        // among headings; notes + entries: one shared counter), so their ords must not be compared.
        // Place each heading's own notes/entries right after it; orphans go first.
        val content = ArrayList<RawItem>(noteList.size + entryList.size)
        noteList.forEach { content.add(RawItem.N(it)) }
        entryList.forEach { content.add(RawItem.E(it)) }
        content.sortBy { it.ord }
        val knownHeadings = headingList.mapTo(HashSet()) { it.id }
        val byHeading = content.groupBy { it.headingId?.takeIf(knownHeadings::contains) }

        val allRaw = ArrayList<RawItem>(headingList.size + content.size)
        byHeading[null]?.let(allRaw::addAll)
        for (heading in headingList) {
            allRaw.add(RawItem.H(heading))
            byHeading[heading.id]?.let(allRaw::addAll)
        }

        val finalItems = mutableListOf<SectionItem>()
        var currentMoreEntries = mutableListOf<Entry>()
        var currentHeadingId: String? = null

        fun flushMoreGroup() {
            if (currentMoreEntries.isNotEmpty()) {
                finalItems.add(SectionItem.MoreGroup(currentMoreEntries.toList()))
                currentMoreEntries.clear()
            }
        }

        for (item in allRaw) {
            when (item) {
                is RawItem.H -> {
                    flushMoreGroup()
                    currentHeadingId = item.heading.id
                    finalItems.add(SectionItem.HeadingItem(item.heading))
                }
                is RawItem.N -> {
                    flushMoreGroup()
                    finalItems.add(SectionItem.NoteItem(item.note))
                }
                is RawItem.E -> {
                    val entry = item.entry
                    if (entry.isInlineMore) {
                        // Check if heading changed
                        if (entry.headingId != null && entry.headingId != currentHeadingId) {
                            flushMoreGroup()
                            currentHeadingId = entry.headingId
                        }
                        currentMoreEntries.add(entry)
                    } else {
                        flushMoreGroup()
                        if (entry.headingId != null) {
                            currentHeadingId = entry.headingId
                        }
                        val lastItem = finalItems.lastOrNull()
                        if (lastItem is SectionItem.EntryItem &&
                            lastItem.entry.line > 0 &&
                            lastItem.entry.line == entry.line &&
                            lastItem.entry.headingId == entry.headingId
                        ) {
                            finalItems[finalItems.size - 1] = lastItem.copy(
                                alternatives = lastItem.alternatives + entry
                            )
                        } else {
                            finalItems.add(SectionItem.EntryItem(entry))
                        }
                    }
                }
            }
        }
        flushMoreGroup()

        finalItems
    }

    private fun readHeading(stmt: androidx.sqlite.SQLiteStatement): Heading {
        return Heading(
            id = stmt.getText(0),
            sectionId = stmt.getText(1),
            parentId = if (stmt.isNull(2)) null else stmt.getText(2),
            level = stmt.getLong(3).toInt(),
            title = stmt.getText(4),
            anchor = stmt.getText(5),
            ord = stmt.getLong(6).toInt(),
        )
    }

    private fun readEntry(stmt: androidx.sqlite.SQLiteStatement): Entry {
        val tagsRaw = stmt.getText(9)
        val tagsList = if (tagsRaw.isEmpty()) emptyList() else tagsRaw.split('|')
        val mirrorsRaw = stmt.getText(16)
        val mirrorsList = try {
            json.decodeFromString<List<Link>>(mirrorsRaw)
        } catch (_: Exception) {
            emptyList()
        }
        val extraLinksRaw = stmt.getText(17)
        val extraLinksList = try {
            json.decodeFromString<List<Link>>(extraLinksRaw)
        } catch (_: Exception) {
            emptyList()
        }

        return Entry(
            num = stmt.getLong(0).toInt(),
            id = stmt.getText(1),
            sectionId = stmt.getText(2),
            headingId = if (stmt.isNull(3)) null else stmt.getText(3),
            ord = stmt.getLong(4).toInt(),
            name = stmt.getText(5),
            url = stmt.getText(6),
            originalUrl = if (stmt.isNull(7)) null else stmt.getText(7),
            description = stmt.getText(8),
            tags = tagsList,
            headingPath = stmt.getText(10),
            tier = Tier.fromInt(stmt.getLong(11).toInt()),
            kind = EntryKind.fromInt(stmt.getLong(12).toInt()),
            line = stmt.getLong(13).toInt(),
            isInlineMore = stmt.getLong(14) != 0L,
            isBase64 = stmt.getLong(15) != 0L,
            mirrors = mirrorsList,
            extraLinks = extraLinksList,
            firstSeen = stmt.getLong(18),
            lastChanged = stmt.getLong(19),
            starredAt = if (stmt.isNull(20)) null else stmt.getLong(20),
        )
    }

    private fun extractHost(url: String): String? {
        val clean = url.trim()
        val rawHost = try {
            val u = if (!clean.contains("://")) "http://$clean" else clean
            URI(u).host
        } catch (_: Exception) {
            null
        } ?: clean.substringAfter("://").substringBefore('/').substringBefore(':').substringBefore('?')

        return rawHost.lowercase().removePrefix("www.")
    }

    private fun generateHostCandidates(host: String): List<String> {
        val parts = host.split('.')
        if (parts.size < 2) return listOf(host)
        val candidates = mutableListOf<String>()
        for (i in 0 until parts.size - 1) {
            candidates.add(parts.subList(i, parts.size).joinToString("."))
        }
        return candidates
    }

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

private sealed interface RawItem {
    val ord: Int
    val headingId: String?
    data class H(val heading: Heading) : RawItem {
        override val ord get() = heading.ord
        override val headingId get() = heading.id
    }
    data class N(val note: Note) : RawItem {
        override val ord get() = note.ord
        override val headingId get() = note.headingId
    }
    data class E(val entry: Entry) : RawItem {
        override val ord get() = entry.ord
        override val headingId get() = entry.headingId
    }
}
