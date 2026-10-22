package io.github.omeg4dev.fmhy.browser

import java.net.URI

/** Pure helpers behind the full-screen tab overview. */
object TabsLogic {
    /** Search field appears from this many tabs on. */
    const val SEARCH_MIN_TABS = 6

    data class Grouped(val normal: List<TabState>, val priv: List<TabState>)

    fun group(tabs: List<TabState>): Grouped =
        Grouped(tabs.filter { !it.isPrivate }, tabs.filter { it.isPrivate })

    /** Host without a leading "www."; blank pages and unparsable urls give "". */
    fun domain(url: String): String {
        if (url.isBlank() || url.startsWith("about:")) return ""
        val host = try { URI(url).host } catch (_: Exception) { null }
            ?: url.substringAfter("://", url).substringBefore('/').substringBefore('?').substringBefore('#')
        return host.removePrefix("www.")
    }

    /** Case-insensitive match on title or url; blank query keeps everything. */
    fun filter(tabs: List<TabState>, query: String): List<TabState> {
        val q = query.trim()
        if (q.isEmpty()) return tabs
        return tabs.filter { it.title.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true) }
    }

    /** "5 tabs" or "5 tabs · 2 private" (private counted inside the total). */
    fun title(total: Int, privateCount: Int): String {
        val base = if (total == 1) "1 tab" else "$total tabs"
        return if (privateCount > 0) "$base · $privateCount private" else base
    }

    /**
     * Index of the current tab in the displayed order (normal section first, then private), or -1.
     * Used to scroll it into view when the overview opens.
     */
    fun currentIndex(grouped: Grouped, currentTabId: Long?): Int {
        if (currentTabId == null) return -1
        val ordered = grouped.normal + grouped.priv
        return ordered.indexOfFirst { it.id == currentTabId }
    }
}
