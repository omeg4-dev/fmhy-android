package io.github.omeg4dev.fmhy.ui.search

import io.github.omeg4dev.fmhy.data.search.CategoryHit

internal const val CATEGORY_PREVIEW = 3

/** What the "Categories" block of the search results shows: nothing at all when [rows] is empty. */
internal data class CategoryBlock(val rows: List<CategoryHit>, val toggleLabel: String?)

internal fun categoryBlock(
    categories: List<CategoryHit>,
    expanded: Boolean,
    preview: Int = CATEGORY_PREVIEW,
): CategoryBlock {
    if (categories.isEmpty()) return CategoryBlock(emptyList(), null)
    val rows = if (expanded) categories else categories.take(preview)
    val toggleLabel = if (categories.size <= preview) {
        null
    } else if (expanded) {
        "Show fewer categories"
    } else {
        val n = categories.size
        "View all ${if (n >= 50) "50+" else n} categories"
    }
    return CategoryBlock(rows, toggleLabel)
}
