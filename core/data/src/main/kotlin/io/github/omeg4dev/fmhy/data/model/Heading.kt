package io.github.omeg4dev.fmhy.data.model

data class Heading(
    val id: String,
    val sectionId: String,
    val parentId: String?,
    val level: Int,
    val title: String,
    val anchor: String,
    val ord: Int,
)
