package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.work.NetworkKind

/**
 * Pure policy object for "Browse only on Wi-Fi" gating.
 * Determines whether a URL load should be blocked and replaced with the Wi-Fi only message page.
 */
object BrowsePolicy {

    /**
     * Returns true if loading [url] should be blocked when browsing is restricted to Wi-Fi.
     *
     * Returns false when:
     * - [browseWifiOnly] is false
     * - [kind] is [NetworkKind.WIFI]
     * - [kind] is [NetworkKind.NONE] (preserves offline/failed-load behavior)
     * - [tabOverride] is true (user explicitly allowed mobile data for this tab)
     * - [url] is not a web URL (start pages, `about:blank`, `data:`, `file:`, or internal schemes)
     */
    fun shouldBlock(
        url: String,
        browseWifiOnly: Boolean,
        kind: NetworkKind,
        tabOverride: Boolean,
    ): Boolean {
        if (!browseWifiOnly || tabOverride) return false
        if (kind != NetworkKind.MOBILE) return false
        if (EmptyTabPolicy.isStartUrl(url)) return false
        val isHttp = url.startsWith("http://", ignoreCase = true)
        val isHttps = url.startsWith("https://", ignoreCase = true)
        if (!isHttp && !isHttps) return false
        return true
    }
}
