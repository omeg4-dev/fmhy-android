package io.github.omeg4dev.fmhy.data.nsfw

import io.github.omeg4dev.fmhy.data.buildSectionItems
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Link
import io.github.omeg4dev.fmhy.data.model.Note
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.parse.Base64Map
import io.github.omeg4dev.fmhy.data.parse.FmhyParser
import io.github.omeg4dev.fmhy.data.parse.SectionInfo

/** Parsed in-memory representation of the NSFW section ready for section UI state. */
data class BuiltNsfwSection(
    val headings: List<Heading>,
    val items: List<SectionItem>,
    val entries: List<Entry>,
)

/** Parses already-filtered NSFW wiki Markdown into section domain models. */
object NsfwSectionBuilder {

    private val NSFW_SECTION_INFO = SectionInfo(
        id = NSFW_SECTION_ID,
        title = NSFW_SECTION_TITLE,
        description = "",
        file = "nsfw.md",
        ord = Int.MAX_VALUE,
    )

    fun build(filteredMarkdown: String): BuiltNsfwSection {
        val parsed = FmhyParser.parseSection(
            section = NSFW_SECTION_INFO,
            markdown = filteredMarkdown,
            b64 = Base64Map(""),
        )

        val headings = parsed.headings.map { row ->
            Heading(
                id = row.id,
                sectionId = row.sectionId,
                parentId = row.parentId,
                level = row.level,
                title = row.title,
                anchor = row.anchor,
                ord = row.ord,
            )
        }

        val notes = parsed.notes.map { row ->
            Note(
                id = row.id,
                sectionId = row.sectionId,
                headingId = row.headingId,
                ord = row.ord,
                textMd = row.textMd,
            )
        }

        val entries = parsed.entries.mapIndexed { idx, row ->
            Entry(
                id = row.id,
                num = idx + 1,
                sectionId = row.sectionId,
                headingId = row.headingId,
                ord = row.ord,
                name = row.name,
                url = row.url,
                originalUrl = row.originalUrl,
                description = row.description,
                tags = if (row.tags.isEmpty()) emptyList() else row.tags.split('|'),
                headingPath = row.headingPath,
                tier = Tier.fromInt(row.tier),
                kind = EntryKind.fromInt(row.kind),
                line = row.line,
                isInlineMore = row.isInlineMore,
                isBase64 = row.isBase64,
                mirrors = row.mirrors.map { Link(it.label, it.url) },
                extraLinks = row.extraLinks.map { Link(it.label, it.url) },
                firstSeen = 0L,
                lastChanged = 0L,
                starredAt = null,
            )
        }

        val items = buildSectionItems(headings, notes, entries)
        return BuiltNsfwSection(headings = headings, items = items, entries = entries)
    }
}
