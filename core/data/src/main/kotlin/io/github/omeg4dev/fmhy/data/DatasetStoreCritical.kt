package io.github.omeg4dev.fmhy.data

import io.github.omeg4dev.fmhy.data.model.CriticalNote
import io.github.omeg4dev.fmhy.data.parse.CriticalNoteRow

// DatasetStore queries: critical notes and note links.

internal suspend fun DatasetStore.criticalNotesImpl(): List<CriticalNote> = withConnection { conn ->
    val result = mutableListOf<CriticalNote>()
    val sql = "SELECT id, title, text_md, hash FROM critical_notes ORDER BY id ASC"
    conn.prepare(sql).use { stmt ->
        while (stmt.step()) {
            result.add(
                CriticalNote(
                    id = stmt.getText(0),
                    title = stmt.getText(1),
                    textMd = stmt.getText(2),
                    hash = stmt.getText(3),
                )
            )
        }
    }
    result
}

internal suspend fun DatasetStore.criticalNoteImpl(id: String): CriticalNote? = withConnection { conn ->
    val sql = "SELECT id, title, text_md, hash FROM critical_notes WHERE id = ?"
    conn.prepare(sql).use { stmt ->
        stmt.bindText(1, id)
        if (stmt.step()) {
            CriticalNote(
                id = stmt.getText(0),
                title = stmt.getText(1),
                textMd = stmt.getText(2),
                hash = stmt.getText(3),
            )
        } else {
            null
        }
    }
}

internal suspend fun DatasetStore.isCriticalNoteImpl(id: String): Boolean = withConnection { conn ->
    val sql = "SELECT 1 FROM critical_notes WHERE id = ?"
    conn.prepare(sql).use { stmt ->
        stmt.bindText(1, id)
        stmt.step()
    }
}

internal suspend fun DatasetStore.criticalNotesForEntryImpl(entryId: String): List<CriticalNote> = withConnection { conn ->
    val result = mutableListOf<CriticalNote>()
    val sql = """
        SELECT c.id, c.title, c.text_md, c.hash
        FROM note_links l
        JOIN critical_notes c ON c.id = l.note_id
        WHERE l.entry_id = ?
        ORDER BY l.ord, c.id
    """.trimIndent()
    conn.prepare(sql).use { stmt ->
        stmt.bindText(1, entryId)
        while (stmt.step()) {
            result.add(
                CriticalNote(
                    id = stmt.getText(0),
                    title = stmt.getText(1),
                    textMd = stmt.getText(2),
                    hash = stmt.getText(3),
                )
            )
        }
    }
    result
}

internal suspend fun DatasetStore.criticalNoteForEntryImpl(entryId: String): CriticalNote? = withConnection { conn ->
    val sql = """
        SELECT c.id, c.title, c.text_md, c.hash
        FROM note_links l
        JOIN critical_notes c ON c.id = l.note_id
        WHERE l.entry_id = ?
        ORDER BY l.ord, c.id
        LIMIT 1
    """.trimIndent()
    conn.prepare(sql).use { stmt ->
        stmt.bindText(1, entryId)
        if (stmt.step()) {
            CriticalNote(
                id = stmt.getText(0),
                title = stmt.getText(1),
                textMd = stmt.getText(2),
                hash = stmt.getText(3),
            )
        } else {
            null
        }
    }
}

internal suspend fun DatasetStore.criticalNoteIdsForEntriesImpl(entryIds: Collection<String>): Map<String, String> = withConnection { conn ->
    if (entryIds.isEmpty()) return@withConnection emptyMap()
    val result = mutableMapOf<String, String>()
    val lowestOrd = mutableMapOf<String, Int>()

    for (chunk in entryIds.chunked(500)) {
        val placeholders = chunk.joinToString(",") { "?" }
        val sql = """
            SELECT l.entry_id, l.note_id, l.ord
            FROM note_links l
            JOIN critical_notes c ON c.id = l.note_id
            WHERE l.entry_id IN ($placeholders)
            ORDER BY l.entry_id, l.ord, l.note_id
        """.trimIndent()
        conn.prepare(sql).use { stmt ->
            chunk.forEachIndexed { i, id -> stmt.bindText(i + 1, id) }
            while (stmt.step()) {
                val entryId = stmt.getText(0)
                val noteId = stmt.getText(1)
                val ord = stmt.getLong(2).toInt()
                val prevOrd = lowestOrd[entryId]
                if (prevOrd == null || ord < prevOrd) {
                    lowestOrd[entryId] = ord
                    result[entryId] = noteId
                }
            }
        }
    }
    result
}

internal suspend fun DatasetStore.linkedNoteIdsImpl(): Set<String> = withConnection { conn ->
    val result = mutableSetOf<String>()
    val sql = "SELECT DISTINCT note_id FROM note_links"
    conn.prepare(sql).use { stmt ->
        while (stmt.step()) {
            result.add(stmt.getText(0))
        }
    }
    result
}

internal suspend fun DatasetStore.storedCriticalNotesImpl(): Map<String, CriticalNoteRow> = withConnection { conn ->
    val result = mutableMapOf<String, CriticalNoteRow>()
    val sql = "SELECT id, title, text_md, hash FROM critical_notes"
    conn.prepare(sql).use { stmt ->
        while (stmt.step()) {
            val id = stmt.getText(0)
            result[id] = CriticalNoteRow(
                id = id,
                title = stmt.getText(1),
                textMd = stmt.getText(2),
                hash = stmt.getText(3),
            )
        }
    }
    result
}

internal suspend fun DatasetStore.criticalNoteForUrlImpl(url: String): CriticalNote? = withConnection { conn ->
    val sql = """
        SELECT c.id, c.title, c.text_md, c.hash
        FROM critical_notes c
        JOIN note_links l ON l.note_id = c.id
        JOIN entries e ON e.id = l.entry_id
        WHERE e.url = ? OR e.original_url = ? OR EXISTS (
            SELECT 1 FROM json_each(e.mirrors) j WHERE json_extract(j.value, '$.url') = ?
        )
        ORDER BY l.ord, c.id
        LIMIT 1
    """.trimIndent()
    conn.prepare(sql).use { stmt ->
        stmt.bindText(1, url)
        stmt.bindText(2, url)
        stmt.bindText(3, url)
        if (stmt.step()) {
            CriticalNote(
                id = stmt.getText(0),
                title = stmt.getText(1),
                textMd = stmt.getText(2),
                hash = stmt.getText(3),
            )
        } else {
            null
        }
    }
}
