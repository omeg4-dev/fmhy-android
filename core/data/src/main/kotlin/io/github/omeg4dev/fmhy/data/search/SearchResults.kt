package io.github.omeg4dev.fmhy.data.search

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Section

data class SearchHit(
    val entry: Entry,
    val nameHl: String,
    val descriptionHl: String,
    /** Set when the match sits in a column the row does not show: "link", "category" or "tags". */
    val extraLabel: String? = null,
    /** [extraLabel]'s text with the matched terms wrapped in \u0002 / \u0003. */
    val extraHl: String? = null,
)

data class SearchGroup(
    val section: Section,
    val hits: List<SearchHit>,
)

data class SearchResults(
    val groups: List<SearchGroup>,
    val total: Int,
    val tookMs: Long,
    val categories: List<CategoryHit> = emptyList(),
)

/** A section or heading whose title matches the query. [heading] == null means the section itself;
 *  [path] holds the breadcrumb titles from the section down to the heading's parent. */
data class CategoryHit(
    val section: Section,
    val heading: Heading?,
    val path: List<String>,
)
