package io.github.omeg4dev.fmhy.browser

/**
 * Pure policy object for "empty tab" decisions. No Android types; fully unit-testable.
 *
 * A tab is considered **empty** if it only ever showed the browser start screen — i.e. its url
 * is blank or `about:blank` **and** it never navigated to a real site ([TabState.hadSite] is
 * false). A tab where the user went back from a page to the start screen is *not* empty because
 * [TabState.hadSite] stays true once set.
 */
object EmptyTabPolicy {

    /**
     * Returns `true` when [url] is the browser start screen address (empty string or
     * `"about:blank"`).
     */
    fun isStartUrl(url: String): Boolean = url.isBlank() || url == "about:blank"

    /**
     * Returns `true` when the tab at [url] with history flag [hadSite] is considered empty:
     * it shows the start screen and never reached a real website.
     */
    fun isEmpty(url: String, hadSite: Boolean): Boolean = isStartUrl(url) && !hadSite

    /** Convenience overload that reads [TabState.url] and [TabState.hadSite]. */
    fun isEmpty(tab: TabState): Boolean = isEmpty(tab.url, tab.hadSite)

    /**
     * Returns the ids of every empty tab in [tabs] that is **not** [keepId]. These are the tabs
     * that should be closed when the user leaves the browser, while [keepId] (the tab currently
     * on screen) is left alone.
     *
     * @param keepId The id of the tab currently visible; never closed. Pass `null` to close all
     *   empty tabs (e.g. when the browser screen is fully leaving, not just switching tabs).
     */
    fun toCloseOnLeave(tabs: List<TabState>, keepId: Long?): List<Long> =
        tabs.filter { isEmpty(it) && it.id != keepId }.map { it.id }

    /**
     * Returns the first reusable empty tab with matching privacy, or `null` when none exists.
     * Prefers [currentId] if it is itself empty and has the right privacy flag; otherwise returns
     * the first other qualifying tab.
     *
     * @param tabs    The current tab list.
     * @param isPrivate The privacy mode of the tab to open.
     * @param currentId The id of the currently active tab; checked first.
     */
    fun reusable(tabs: List<TabState>, isPrivate: Boolean, currentId: Long?): TabState? {
        val current = if (currentId != null) tabs.firstOrNull { it.id == currentId } else null
        if (current != null && isEmpty(current) && current.isPrivate == isPrivate) return current
        return tabs.firstOrNull { isEmpty(it) && it.isPrivate == isPrivate }
    }

    /**
     * Filters [tabs] to only those that should be written to the restore store:
     * non-private tabs whose url is a real website (not blank, not `about:blank`).
     * This preserves exactly the same semantics as today's [saveTabsToStore] filter.
     */
    fun restorable(tabs: List<TabState>): List<TabState> =
        tabs.filter { !it.isPrivate && !isStartUrl(it.url) }
}
