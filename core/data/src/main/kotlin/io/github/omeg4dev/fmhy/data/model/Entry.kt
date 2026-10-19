package io.github.omeg4dev.fmhy.data.model

data class Entry(
    val id: String,
    val num: Int,
    val sectionId: String,
    val headingId: String?,
    val ord: Int,
    val name: String,
    val url: String,
    val originalUrl: String?,
    val description: String,
    val tags: List<String>,
    val headingPath: String,
    val tier: Tier,
    val kind: EntryKind = EntryKind.NORMAL,
    val isInlineMore: Boolean,
    val isBase64: Boolean,
    val mirrors: List<Link>,
    val extraLinks: List<Link>,
    val firstSeen: Long,
    val lastChanged: Long,
    val starredAt: Long?,
)
