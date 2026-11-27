package io.github.omeg4dev.fmhy.data

enum class SavedSort {
    RECENT,
    A_Z,
    SECTION,
}

data class SavedViewPrefs(
    val sort: SavedSort = SavedSort.RECENT,
    val groupBySection: Boolean = false,
)
