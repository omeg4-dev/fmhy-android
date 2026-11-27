package io.github.omeg4dev.fmhy.data

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Section

// DatasetStore queries: entries, sections, headings, random picks.

internal suspend fun DatasetStore.entriesForSectionImpl(sectionId: String): List<Entry> = withConnection { conn ->
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

internal suspend fun DatasetStore.sectionsImpl(order: SectionOrder): List<Section> = withConnection { conn ->
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

internal suspend fun DatasetStore.headingsImpl(sectionId: String): List<Heading> = withConnection { conn ->
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

internal suspend fun DatasetStore.allHeadingsImpl(): List<Heading> = withConnection { conn ->
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

internal suspend fun DatasetStore.entryImpl(id: String): Entry? = withConnection { conn ->
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

internal suspend fun DatasetStore.entriesImpl(ids: List<String>): List<Entry> = withConnection { conn ->
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

internal suspend fun DatasetStore.entriesByUrlsImpl(urls: Collection<String>): List<Entry> = withConnection { conn ->
    if (urls.isEmpty()) return@withConnection emptyList()
    val result = mutableListOf<Entry>()
    val distinctUrls = urls.distinct()
    for (chunk in distinctUrls.chunked(400)) {
        val placeholders = chunk.joinToString(",") { "?" }
        val sql = """
            SELECT num, id, section_id, heading_id, ord, name, url, original_url,
                   description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
                   mirrors, extra_links, first_seen, last_changed, starred_at
            FROM entries
            WHERE url IN ($placeholders) OR original_url IN ($placeholders)
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            val n = chunk.size
            for (i in 0 until n) {
                val u = chunk[i]
                stmt.bindText(i + 1, u)
                stmt.bindText(n + i + 1, u)
            }
            while (stmt.step()) {
                result.add(readEntry(stmt))
            }
        }
    }
    result
}

internal suspend fun DatasetStore.randomTopPickImpl(): Entry? {
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

internal suspend fun DatasetStore.randomTopEntriesImpl(sectionId: String, limit: Int): List<Entry> {
    if (limit <= 0) return emptyList()
    val candidates = withConnection { conn ->
        val sql = """
            SELECT num, id, section_id, heading_id, ord, name, url, original_url,
                   description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
                   mirrors, extra_links, first_seen, last_changed, starred_at
            FROM entries
            WHERE tier >= 1 AND kind = 0 AND is_base64 = 0 AND section_id = ?
            ORDER BY RANDOM()
            LIMIT ?
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            stmt.bindText(1, sectionId)
            stmt.bindLong(2, (limit * 5).toLong())
            buildList { while (stmt.step()) add(readEntry(stmt)) }
        }
    }
    return candidates
        .filter { unsafeReason(it.url) == null }
        .take(limit)
}
