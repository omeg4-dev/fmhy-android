package io.github.omeg4dev.fmhy.data.search

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Section

data class SearchHit(
    val entry: Entry,
    val nameHl: String,
    val descriptionHl: String,
)

data class SearchGroup(
    val section: Section,
    val hits: List<SearchHit>,
)

data class SearchResults(
    val groups: List<SearchGroup>,
    val total: Int,
    val tookMs: Long,
)
