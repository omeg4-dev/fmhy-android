package io.github.omeg4dev.fmhy.data.model

data class Change(
    val id: Long,
    val ts: Long,
    val kind: ChangeKind,
    val entryId: String,
    val sectionId: String,
    val name: String,
    val url: String,
    val detail: String,
)
