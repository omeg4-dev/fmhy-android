package io.github.omeg4dev.fmhy.data.model

/**
 * Display items for a section's content. Headings, entries, notes are merged by `ord`
 * and consecutive `is_inline_more` entries under the same heading are grouped into [MoreGroup].
 */
sealed interface SectionItem {
    data class HeadingItem(val heading: Heading) : SectionItem
    data class EntryItem(val entry: Entry, val alternatives: List<Entry> = emptyList()) : SectionItem
    data class NoteItem(val note: Note) : SectionItem
    data class MoreGroup(val entries: List<Entry>) : SectionItem
}
