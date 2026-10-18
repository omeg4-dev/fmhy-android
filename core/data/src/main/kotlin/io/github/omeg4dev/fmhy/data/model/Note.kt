package io.github.omeg4dev.fmhy.data.model

data class Note(
    val id: String,
    val sectionId: String,
    val headingId: String?,
    val ord: Int,
    val textMd: String,
)
