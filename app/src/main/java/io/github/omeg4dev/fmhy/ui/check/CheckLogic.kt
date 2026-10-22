package io.github.omeg4dev.fmhy.ui.check

/**
 * Drops recent checks that are only a typing stage of a newer check ("exa" -> "example.com"),
 * which older versions saved on every pause. Also removes case-insensitive duplicates.
 * Order is kept; the newest entry wins.
 */
fun pruneRecentChecks(items: List<RecentCheck>): List<RecentCheck> {
    fun key(r: RecentCheck) = r.url.trim().lowercase()
    val kept = ArrayList<RecentCheck>(items.size)
    for (item in items) {
        val k = key(item)
        val shadowed = items.any { other ->
            other !== item && other.ts >= item.ts && key(other).let { o ->
                (o.length > k.length && o.startsWith(k)) || (o == k && (other.ts > item.ts || items.indexOf(other) < items.indexOf(item)))
            }
        }
        if (!shadowed) kept += item
    }
    return kept
}

/** Breadcrumb parts "Section > heading > ..." without repeating the section title. */
fun matchBreadcrumb(sectionTitle: String?, headingPath: String): List<String> {
    val parts = headingPath.split(">").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    val section = sectionTitle?.trim().orEmpty()
    if (section.isEmpty()) return parts
    if (parts.firstOrNull()?.equals(section, ignoreCase = true) == true) parts.removeAt(0)
    return listOf(section) + parts
}
