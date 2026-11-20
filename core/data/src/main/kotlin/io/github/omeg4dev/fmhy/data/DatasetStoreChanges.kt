package io.github.omeg4dev.fmhy.data

import io.github.omeg4dev.fmhy.data.DatasetStore.Companion.normalizeEntryUrl
import io.github.omeg4dev.fmhy.data.model.Change
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Heading

// DatasetStore queries: changes, history, added timestamps.

internal suspend fun DatasetStore.changesImpl(kinds: Set<ChangeKind>, sinceTs: Long?, sectionId: String?, limit: Int, offset: Int): List<Change> = withConnection { conn ->
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

internal suspend fun DatasetStore.countAddedSinceImpl(ts: Long): Int = withConnection { conn ->
    conn.prepare("SELECT count(*) FROM changes WHERE kind = 'added' AND ts > ?").use { stmt ->
        stmt.bindLong(1, ts)
        if (stmt.step()) stmt.getLong(0).toInt() else 0
    }
}

internal suspend fun DatasetStore.recentChangesImpl(sinceTs: Long, kinds: Set<ChangeKind>, sectionIds: Set<String>?, headingIds: Set<String>?, limit: Int): List<ChangeItem> = withConnection { conn ->
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

internal suspend fun DatasetStore.entryHistoryImpl(entryId: String): List<ChangeItem> = withConnection { conn ->
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

internal suspend fun DatasetStore.addedAtImpl(entryIds: Collection<String>): Map<String, Long> = withConnection { conn ->
    if (entryIds.isEmpty()) return@withConnection emptyMap()
    val result = mutableMapOf<String, Long>()

    for (chunk in entryIds.chunked(500)) {
        val placeholders = chunk.joinToString(",") { "?" }
        val sql = "SELECT entry_id, MIN(ts) FROM changes WHERE kind = 'added' AND entry_id IN ($placeholders) GROUP BY entry_id"
        conn.prepare(sql).use { stmt ->
            chunk.forEachIndexed { i, id -> stmt.bindText(i + 1, id) }
            while (stmt.step()) {
                result[stmt.getText(0)] = stmt.getLong(1)
            }
        }
    }

    result
}
