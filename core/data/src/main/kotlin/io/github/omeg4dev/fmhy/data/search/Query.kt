package io.github.omeg4dev.fmhy.data.search

data class Query(
    val raw: String,
    val words: List<String> = emptyList(),
    val phrases: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
    val inSections: List<String> = emptyList(),
    val isStarred: Boolean = false,
    val isTop: Boolean = false,
    val isB64: Boolean = false,
    val site: String? = null,
) {
    val isEmpty: Boolean
        get() = words.isEmpty() && phrases.isEmpty() && excludes.isEmpty() &&
                inSections.isEmpty() && !isStarred && !isTop && !isB64 && site == null
}
