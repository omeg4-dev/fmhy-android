package io.github.omeg4dev.fmhy.browser

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** One row of the tab overview: section header, tab, or a folded/unfolded group of same-site tabs. */
sealed interface TabsRow {
    val key: String
    data class Header(val label: String, val count: Int) : TabsRow {
        override val key: String get() = "h:$label"
    }
    data class Item(val tab: TabState, val indented: Boolean = false) : TabsRow {
        override val key: String get() = "t:${tab.id}"
    }
    data class Group(
        val site: String,
        val isPrivate: Boolean,
        val tabs: List<TabState>,
        val expanded: Boolean,
        val containsCurrent: Boolean,
    ) : TabsRow {
        val groupKey: String get() = TabsLogic.groupKey(site, isPrivate)
        override val key: String get() = "g:$groupKey"
    }
}

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

    /**
     * Site's main address using OkHttp's topPrivateDomain(), falling back to domain(url).
     * Blank / about: urls -> "". Lowercase.
     */
    fun siteKey(url: String): String {
        if (url.isBlank() || url.startsWith("about:")) return ""
        val httpUrl = url.toHttpUrlOrNull() ?: return domain(url).lowercase()
        val host = httpUrl.host
        val isIp = host.split('.').let { parts ->
            parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
        }
        if (isIp || host == "localhost" || !host.contains('.')) {
            return domain(url).lowercase()
        }
        val result = PopupPolicy.getTopPrivateDomain(httpUrl)
        return result.lowercase()
    }

    /** Key of a group in [TabManager.expandedTabGroups]; normal and private groups never share one. */
    fun groupKey(site: String, isPrivate: Boolean): String = (if (isPrivate) "p:" else "n:") + site

    /**
     * Rows of the overview. [grouping] (list view only) folds 2+ tabs of the same [siteKey] per section into a
     * [TabsRow.Group]; a non-blank [query] shows only matching tabs, inside their groups, unfolded.
     */
    fun rows(
        tabs: List<TabState>,
        query: String,
        grouping: Boolean,
        expanded: Set<String>,
        currentTabId: Long?,
    ): List<TabsRow> {
        if (!grouping) {
            val filtered = filter(tabs, query)
            val g = group(filtered)
            return buildList {
                if (g.normal.isNotEmpty()) {
                    add(TabsRow.Header("Tabs", g.normal.size))
                    g.normal.forEach { add(TabsRow.Item(it)) }
                }
                if (g.priv.isNotEmpty()) {
                    add(TabsRow.Header("Private tabs", g.priv.size))
                    g.priv.forEach { add(TabsRow.Item(it)) }
                }
            }
        }

        val normalTabs = tabs.filter { !it.isPrivate }
        val privTabs = tabs.filter { it.isPrivate }

        return buildSectionRows(
            sectionLabel = "Tabs",
            sectionTabs = normalTabs,
            isPrivate = false,
            query = query,
            expanded = expanded,
            currentTabId = currentTabId,
        ) + buildSectionRows(
            sectionLabel = "Private tabs",
            sectionTabs = privTabs,
            isPrivate = true,
            query = query,
            expanded = expanded,
            currentTabId = currentTabId,
        )
    }

    private fun buildSectionRows(
        sectionLabel: String,
        sectionTabs: List<TabState>,
        isPrivate: Boolean,
        query: String,
        expanded: Set<String>,
        currentTabId: Long?,
    ): List<TabsRow> {
        if (sectionTabs.isEmpty()) return emptyList()

        val q = query.trim()
        val isSearch = q.isNotEmpty()
        fun matches(tab: TabState): Boolean =
            !isSearch || tab.title.contains(q, ignoreCase = true) || tab.url.contains(q, ignoreCase = true)

        val filteredCount = sectionTabs.count { matches(it) }
        if (filteredCount == 0) return emptyList()

        // Count tabs by siteKey across all tabs in section (siteKey == "" never grouped)
        val siteCounts = mutableMapOf<String, Int>()
        for (tab in sectionTabs) {
            val s = siteKey(tab.url)
            if (s.isNotEmpty()) {
                siteCounts[s] = (siteCounts[s] ?: 0) + 1
            }
        }

        val siteTabs = mutableMapOf<String, MutableList<TabState>>()
        for (tab in sectionTabs) {
            val s = siteKey(tab.url)
            if (s.isNotEmpty() && (siteCounts[s] ?: 0) >= 2) {
                siteTabs.getOrPut(s) { mutableListOf() }.add(tab)
            }
        }

        val seenSites = mutableSetOf<String>()
        val result = mutableListOf<TabsRow>()
        result.add(TabsRow.Header(sectionLabel, filteredCount))

        for (tab in sectionTabs) {
            val s = siteKey(tab.url)
            val isGroup = s.isNotEmpty() && (siteCounts[s] ?: 0) >= 2
            if (isGroup) {
                if (s in seenSites) continue
                seenSites.add(s)
                val allGroupTabs = siteTabs[s] ?: emptyList()
                val containsCurrent = currentTabId != null && allGroupTabs.any { it.id == currentTabId }
                val gKey = groupKey(s, isPrivate)

                if (isSearch) {
                    val matchingGroupTabs = allGroupTabs.filter { matches(it) }
                    if (matchingGroupTabs.isNotEmpty()) {
                        result.add(
                            TabsRow.Group(
                                site = s,
                                isPrivate = isPrivate,
                                tabs = allGroupTabs,
                                expanded = true,
                                containsCurrent = containsCurrent,
                            ),
                        )
                        matchingGroupTabs.forEach { matchingTab ->
                            result.add(TabsRow.Item(matchingTab, indented = true))
                        }
                    }
                } else {
                    val isExpanded = gKey in expanded
                    result.add(
                        TabsRow.Group(
                            site = s,
                            isPrivate = isPrivate,
                            tabs = allGroupTabs,
                            expanded = isExpanded,
                            containsCurrent = containsCurrent,
                        ),
                    )
                    if (isExpanded) {
                        allGroupTabs.forEach { groupTab ->
                            result.add(TabsRow.Item(groupTab, indented = true))
                        }
                    }
                }
            } else {
                if (matches(tab)) {
                    result.add(TabsRow.Item(tab, indented = false))
                }
            }
        }

        return result
    }

    /** Row to scroll to on open: the current tab, or the folded group that holds it. */
    fun scrollKey(rows: List<TabsRow>, currentTabId: Long?): String? {
        if (currentTabId == null) return null
        val itemRow = rows.filterIsInstance<TabsRow.Item>().firstOrNull { it.tab.id == currentTabId }
        if (itemRow != null) return itemRow.key
        val groupRow = rows.filterIsInstance<TabsRow.Group>().firstOrNull { group ->
            group.tabs.any { it.id == currentTabId }
        }
        return groupRow?.key
    }
}
