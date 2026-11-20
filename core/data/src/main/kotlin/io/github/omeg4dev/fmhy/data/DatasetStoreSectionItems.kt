package io.github.omeg4dev.fmhy.data

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Note
import io.github.omeg4dev.fmhy.data.model.Post
import io.github.omeg4dev.fmhy.data.model.SectionItem

// DatasetStore queries: posts and section items assembly.

internal suspend fun DatasetStore.postsImpl(): List<Post> = withConnection { conn ->
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

internal suspend fun DatasetStore.sectionItemsImpl(sectionId: String): List<SectionItem> = withConnection { conn ->
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
