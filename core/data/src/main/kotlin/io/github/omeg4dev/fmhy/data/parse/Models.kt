package io.github.omeg4dev.fmhy.data.parse

import kotlinx.serialization.Serializable

@Serializable
data class SectionInfo(
    val id: String,
    val title: String,
    val description: String,
    val file: String,
    val ord: Int
)

@Serializable
data class HeadingRow(
    val id: String,
    val sectionId: String,
    val parentId: String?,
    val level: Int,
    val title: String,
    val anchor: String,
    val ord: Int
)

@Serializable
data class LinkRow(
    val label: String,
    val url: String
)

@Serializable
data class EntryRow(
    val id: String,
    val sectionId: String,
    val headingId: String?,
    val ord: Int,
    val name: String,
    val url: String,
    val originalUrl: String?,
    val description: String,
    val tags: String,
    val headingPath: String,
    val tier: Int,
    val kind: Int = 0,
    val line: Int = 0,
    val isInlineMore: Boolean,
    val isBase64: Boolean,
    val mirrors: List<LinkRow>,
    val extraLinks: List<LinkRow>
)

@Serializable
data class NoteRow(
    val id: String,
    val sectionId: String,
    val headingId: String?,
    val ord: Int,
    val textMd: String
)

data class ParsedSection(
    val section: SectionInfo,
    val headings: List<HeadingRow>,
    val entries: List<EntryRow>,
    val notes: List<NoteRow>,
    val unparsedLines: Int
)
