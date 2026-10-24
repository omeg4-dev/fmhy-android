package io.github.omeg4dev.fmhy.data.sync

import androidx.sqlite.SQLiteConnection
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.parse.EntryRow
import io.github.omeg4dev.fmhy.data.parse.ParsedSection
import io.github.omeg4dev.fmhy.data.tracker.ChangeClassifier
import kotlinx.serialization.encodeToString

    internal data class EntryDiff(
        val sectionId: String,
        val vanished: List<Entry>,
        val inserted: List<EntryRow>,
        val updated: List<Pair<Entry, EntryRow>>,
        val starredOldEntries: List<Entry>, // old entries where tier goes 0 -> >= 1
        val lineOnlyUpdated: List<Pair<Entry, EntryRow>> = emptyList(),
    )

internal data class SectionDiffs(
    val sectionDiffs: List<EntryDiff>,
    val totalAdded: Int,
    val totalRemoved: Int,
    val totalUpdated: Int,
    val changedHeadingsOrNotes: Boolean,
)

internal suspend fun FmhySync.computeSectionDiffs(parsedSections: List<ParsedSection>): SectionDiffs {
    var totalAdded = 0
    var totalRemoved = 0
    var totalUpdated = 0
    var changedHeadingsOrNotes = false

    val sectionDiffs = mutableListOf<EntryDiff>()

    for (parsedSec in parsedSections) {
        val secId = parsedSec.section.id
        val existingEntries = store.entriesForSection(secId).associateBy { it.id }
        val newEntries = parsedSec.entries
        val newEntriesById = newEntries.associateBy { it.id }

        val vanished = existingEntries.values.filter { it.id !in newEntriesById }
        val inserted = newEntries.filter { it.id !in existingEntries }
        val toUpdate = mutableListOf<Pair<Entry, EntryRow>>()
        val lineOnlyUpdate = mutableListOf<Pair<Entry, EntryRow>>()
        val starred = mutableListOf<Entry>()

        for (newRow in newEntries) {
            val old = existingEntries[newRow.id] ?: continue
            val newMirrorsJson = json.encodeToString(newRow.mirrors)
            val oldMirrorsJson = json.encodeToString(old.mirrors)
            val newExtraJson = json.encodeToString(newRow.extraLinks)
            val oldExtraJson = json.encodeToString(old.extraLinks)
            val oldTagsStr = old.tags.joinToString("|")

            val changed = old.headingId != newRow.headingId ||
                old.ord != newRow.ord ||
                old.name != newRow.name ||
                old.url != newRow.url ||
                old.originalUrl != newRow.originalUrl ||
                old.description != newRow.description ||
                oldTagsStr != newRow.tags ||
                old.headingPath != newRow.headingPath ||
                old.tier.value != newRow.tier ||
                old.kind.value != newRow.kind ||
                old.isInlineMore != newRow.isInlineMore ||
                old.isBase64 != newRow.isBase64 ||
                oldMirrorsJson != newMirrorsJson ||
                oldExtraJson != newExtraJson

            val lineChanged = old.line != newRow.line

            if (changed) {
                toUpdate.add(old to newRow)
                if (old.tier.value == 0 && newRow.tier >= 1) {
                    starred.add(old)
                }
            } else if (lineChanged) {
                lineOnlyUpdate.add(old to newRow)
            }
        }

        totalAdded += inserted.size
        totalRemoved += vanished.size
        totalUpdated += toUpdate.size
        if (parsedSec.headings.isNotEmpty() || parsedSec.notes.isNotEmpty()) {
            changedHeadingsOrNotes = true
        }

        sectionDiffs.add(EntryDiff(secId, vanished, inserted, toUpdate, starred, lineOnlyUpdate))
    }
    return SectionDiffs(sectionDiffs, totalAdded, totalRemoved, totalUpdated, changedHeadingsOrNotes)
}

internal fun applyAbsentSections(conn: SQLiteConnection, absentSections: List<Section>) {
    // A. Absent sections: delete their rows
    for (absent in absentSections) {
        conn.prepare("DELETE FROM entries WHERE section_id = ?").use { stmt ->
            stmt.bindText(1, absent.id)
            stmt.step()
        }
        conn.prepare("DELETE FROM headings WHERE section_id = ?").use { stmt ->
            stmt.bindText(1, absent.id)
            stmt.step()
        }
        conn.prepare("DELETE FROM notes WHERE section_id = ?").use { stmt ->
            stmt.bindText(1, absent.id)
            stmt.step()
        }
        conn.prepare("DELETE FROM sections WHERE id = ?").use { stmt ->
            stmt.bindText(1, absent.id)
            stmt.step()
        }
    }
}

internal fun FmhySync.applySectionDiffs(
    conn: SQLiteConnection,
    parsedSections: List<ParsedSection>,
    sectionDiffs: List<EntryDiff>,
    startNum: Long,
    now: Long,
): Long {
    var nextNum = startNum
    // B. Per changed section
    val parsedMap = parsedSections.associateBy { it.section.id }
    for (diff in sectionDiffs) {
        val secId = diff.sectionId
        val parsedSec = parsedMap[secId] ?: continue

        // Replace headings
        conn.prepare("DELETE FROM headings WHERE section_id = ?").use { stmt ->
            stmt.bindText(1, secId)
            stmt.step()
        }
        val sqlHeading = "INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES (?, ?, ?, ?, ?, ?, ?)"
        conn.prepare(sqlHeading).use { stmt ->
            for (h in parsedSec.headings) {
                stmt.bindText(1, h.id)
                stmt.bindText(2, h.sectionId)
                if (h.parentId != null) stmt.bindText(3, h.parentId) else stmt.bindNull(3)
                stmt.bindLong(4, h.level.toLong())
                stmt.bindText(5, h.title)
                stmt.bindText(6, h.anchor)
                stmt.bindLong(7, h.ord.toLong())
                stmt.step()
                stmt.reset()
            }
        }

        // Replace notes
        conn.prepare("DELETE FROM notes WHERE section_id = ?").use { stmt ->
            stmt.bindText(1, secId)
            stmt.step()
        }
        val sqlNote = "INSERT INTO notes (id, section_id, heading_id, ord, text_md) VALUES (?, ?, ?, ?, ?)"
        conn.prepare(sqlNote).use { stmt ->
            for (n in parsedSec.notes) {
                stmt.bindText(1, n.id)
                stmt.bindText(2, n.sectionId)
                if (n.headingId != null) stmt.bindText(3, n.headingId) else stmt.bindNull(3)
                stmt.bindLong(4, n.ord.toLong())
                stmt.bindText(5, n.textMd)
                stmt.step()
                stmt.reset()
            }
        }

        // Vanished entries: delete from entries table
        conn.prepare("DELETE FROM entries WHERE id = ?").use { delStmt ->
            for (v in diff.vanished) {
                delStmt.bindText(1, v.id)
                delStmt.step()
                delStmt.reset()
            }
        }

        // Inserted entries: insert into entries table
        val sqlInsertEntry = """
            INSERT INTO entries (
                num, id, section_id, heading_id, ord, name, url, original_url,
                description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
                mirrors, extra_links, first_seen, last_changed, starred_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        conn.prepare(sqlInsertEntry).use { insStmt ->
            for (ins in diff.inserted) {
                val num = ++nextNum
                val starredAt = if (ins.tier >= 1) now else null

                insStmt.bindLong(1, num)
                insStmt.bindText(2, ins.id)
                insStmt.bindText(3, ins.sectionId)
                if (ins.headingId != null) insStmt.bindText(4, ins.headingId) else insStmt.bindNull(4)
                insStmt.bindLong(5, ins.ord.toLong())
                insStmt.bindText(6, ins.name)
                insStmt.bindText(7, ins.url)
                if (ins.originalUrl != null) insStmt.bindText(8, ins.originalUrl) else insStmt.bindNull(8)
                insStmt.bindText(9, ins.description)
                insStmt.bindText(10, ins.tags)
                insStmt.bindText(11, ins.headingPath)
                insStmt.bindLong(12, ins.tier.toLong())
                insStmt.bindLong(13, ins.kind.toLong())
                insStmt.bindLong(14, ins.line.toLong())
                insStmt.bindLong(15, if (ins.isInlineMore) 1L else 0L)
                insStmt.bindLong(16, if (ins.isBase64) 1L else 0L)
                insStmt.bindText(17, json.encodeToString(ins.mirrors))
                insStmt.bindText(18, json.encodeToString(ins.extraLinks))
                insStmt.bindLong(19, now)
                insStmt.bindLong(20, now)
                if (starredAt != null) insStmt.bindLong(21, starredAt) else insStmt.bindNull(21)
                insStmt.step()
                insStmt.reset()
            }
        }

        // Updated entries: update fields in entries table
        val sqlUpdateEntry = """
            UPDATE entries SET
                section_id = ?, heading_id = ?, ord = ?, name = ?, url = ?,
                original_url = ?, description = ?, tags = ?, heading_path = ?,
                tier = ?, kind = ?, line = ?, is_inline_more = ?, is_base64 = ?, mirrors = ?,
                extra_links = ?, last_changed = ?, starred_at = ?
            WHERE id = ?
        """.trimIndent()
        conn.prepare(sqlUpdateEntry).use { upStmt ->
            for ((old, row) in diff.updated) {
                val starredAt = if (old.tier.value == 0 && row.tier >= 1) {
                    now
                } else if (row.tier == 0) {
                    null
                } else {
                    old.starredAt ?: now
                }

                upStmt.bindText(1, row.sectionId)
                if (row.headingId != null) upStmt.bindText(2, row.headingId) else upStmt.bindNull(2)
                upStmt.bindLong(3, row.ord.toLong())
                upStmt.bindText(4, row.name)
                upStmt.bindText(5, row.url)
                if (row.originalUrl != null) upStmt.bindText(6, row.originalUrl) else upStmt.bindNull(6)
                upStmt.bindText(7, row.description)
                upStmt.bindText(8, row.tags)
                upStmt.bindText(9, row.headingPath)
                upStmt.bindLong(10, row.tier.toLong())
                upStmt.bindLong(11, row.kind.toLong())
                upStmt.bindLong(12, row.line.toLong())
                upStmt.bindLong(13, if (row.isInlineMore) 1L else 0L)
                upStmt.bindLong(14, if (row.isBase64) 1L else 0L)
                upStmt.bindText(15, json.encodeToString(row.mirrors))
                upStmt.bindText(16, json.encodeToString(row.extraLinks))
                upStmt.bindLong(17, now)
                if (starredAt != null) upStmt.bindLong(18, starredAt) else upStmt.bindNull(18)
                upStmt.bindText(19, row.id)
                upStmt.step()
                upStmt.reset()
            }
        }

        // Line-only updated entries: update line silently without recording change or updating last_changed
        if (diff.lineOnlyUpdated.isNotEmpty()) {
            conn.prepare("UPDATE entries SET line = ? WHERE id = ?").use { stmt ->
                for ((_, row) in diff.lineOnlyUpdated) {
                    stmt.bindLong(1, row.line.toLong())
                    stmt.bindText(2, row.id)
                    stmt.step()
                    stmt.reset()
                }
            }
        }
    }
    return nextNum
}

        internal data class SyncChange(
            val kind: String,
            val entryId: String,
            val sectionId: String,
            val name: String,
            val url: String,
            val detail: String,
        )

internal fun classifyChanges(
    absentSections: List<Section>,
    absentSectionEntries: Map<Section, List<Entry>>,
    sectionDiffs: List<EntryDiff>,
): List<SyncChange> {
    val allRemoved = mutableListOf<Entry>()
    for (absent in absentSections) {
        allRemoved.addAll(absentSectionEntries[absent] ?: emptyList())
    }
    for (diff in sectionDiffs) {
        allRemoved.addAll(diff.vanished)
    }

    val allInserted = mutableListOf<EntryRow>()
    for (diff in sectionDiffs) {
        allInserted.addAll(diff.inserted)
    }

    val processedRemoved = mutableSetOf<String>()
    val processedInserted = mutableSetOf<String>()
    val changesToRecord = mutableListOf<SyncChange>()

    // 1. Moves: removed from section A and added in section B with same normalised URL -> one moved row (detail = A)
    for (rem in allRemoved) {
        if (rem.id in processedRemoved) continue
        val normRemUrl = DatasetStore.normalizeEntryUrl(rem.url)
        val ins = allInserted.firstOrNull {
            it.id !in processedInserted &&
            it.sectionId != rem.sectionId &&
            DatasetStore.normalizeEntryUrl(it.url) == normRemUrl
        }
        if (ins != null) {
            processedRemoved.add(rem.id)
            processedInserted.add(ins.id)
            changesToRecord.add(
                SyncChange(
                    kind = ChangeKind.MOVED.value,
                    entryId = ins.id,
                    sectionId = ins.sectionId,
                    name = ins.name,
                    url = ins.url,
                    detail = rem.sectionId,
                )
            )
        }
    }

    // 2. Renamed/url-changed entries:
    // pair a removed and an added entry in the same section and heading with:
    // same lower-cased name -> url_changed
    for (rem in allRemoved) {
        if (rem.id in processedRemoved) continue
        val normRemUrl = DatasetStore.normalizeEntryUrl(rem.url)
        val ins = allInserted.firstOrNull {
            it.id !in processedInserted &&
            it.sectionId == rem.sectionId &&
            it.headingId == rem.headingId &&
            it.name.trim().lowercase() == rem.name.trim().lowercase() &&
            DatasetStore.normalizeEntryUrl(it.url) != normRemUrl
        }
        if (ins != null) {
            processedRemoved.add(rem.id)
            processedInserted.add(ins.id)
            changesToRecord.add(
                SyncChange(
                    kind = ChangeKind.URL_CHANGED.value,
                    entryId = ins.id,
                    sectionId = ins.sectionId,
                    name = ins.name,
                    url = ins.url,
                    detail = rem.url,
                )
            )
        }
    }

    // same normalised URL and different name -> renamed
    for (rem in allRemoved) {
        if (rem.id in processedRemoved) continue
        val normRemUrl = DatasetStore.normalizeEntryUrl(rem.url)
        val ins = allInserted.firstOrNull {
            it.id !in processedInserted &&
            it.sectionId == rem.sectionId &&
            it.headingId == rem.headingId &&
            DatasetStore.normalizeEntryUrl(it.url) == normRemUrl &&
            it.name.trim() != rem.name.trim()
        }
        if (ins != null) {
            processedRemoved.add(rem.id)
            processedInserted.add(ins.id)
            changesToRecord.add(
                SyncChange(
                    kind = ChangeKind.RENAMED.value,
                    entryId = ins.id,
                    sectionId = ins.sectionId,
                    name = ins.name,
                    url = ins.url,
                    detail = rem.name,
                )
            )
        }
    }

    // 3. Unpaired removed stay removed
    for (rem in allRemoved) {
        if (rem.id !in processedRemoved) {
            changesToRecord.add(
                SyncChange(
                    kind = ChangeKind.REMOVED.value,
                    entryId = rem.id,
                    sectionId = rem.sectionId,
                    name = rem.name,
                    url = rem.url,
                    detail = "",
                )
            )
        }
    }

    // 4. Unpaired inserted stay added
    for (ins in allInserted) {
        if (ins.id !in processedInserted) {
            changesToRecord.add(
                SyncChange(
                    kind = ChangeKind.ADDED.value,
                    entryId = ins.id,
                    sectionId = ins.sectionId,
                    name = ins.name,
                    url = ins.url,
                    detail = "",
                )
            )
        }
    }

    // 5. Updated entries present in both (diff.updated)
    for (diff in sectionDiffs) {
        for ((old, row) in diff.updated) {
            val oldLine = ChangeClassifier.buildComparableLine(
                old.name,
                old.url,
                old.description,
                old.tier.value,
                old.extraLinks.map { it.label to it.url }
            )
            val newLine = ChangeClassifier.buildComparableLine(
                row.name,
                row.url,
                row.description,
                row.tier,
                row.extraLinks.map { it.label to it.url }
            )
            val lineDiff = ChangeClassifier.classify(oldLine, newLine)
            for (k in lineDiff.kinds) {
                val detail = when (k) {
                    ChangeKind.RENAMED -> lineDiff.oldName ?: old.name
                    ChangeKind.URL_CHANGED -> lineDiff.oldUrl ?: old.url
                    ChangeKind.EDITED -> ChangeClassifier.editedDetail(oldLine, newLine)
                    else -> ""
                }
                changesToRecord.add(
                    SyncChange(
                        kind = k.value,
                        entryId = row.id,
                        sectionId = row.sectionId,
                        name = row.name,
                        url = row.url,
                        detail = detail,
                    )
                )
            }
        }
    }
    return changesToRecord
}

internal fun insertChanges(conn: SQLiteConnection, changesToRecord: List<SyncChange>, now: Long, commitSha: String) {
    val sqlInsertChange = "INSERT OR IGNORE INTO changes (ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
    conn.prepare(sqlInsertChange).use { chStmt ->
        for (ch in changesToRecord) {
            chStmt.bindLong(1, now)
            chStmt.bindText(2, ch.kind)
            chStmt.bindText(3, ch.entryId)
            chStmt.bindText(4, ch.sectionId)
            chStmt.bindText(5, ch.name)
            chStmt.bindText(6, ch.url)
            chStmt.bindText(7, ch.detail)
            chStmt.bindText(8, commitSha)
            chStmt.step()
            chStmt.reset()
        }
    }
}
