package io.github.omeg4dev.fmhy.browser

/**
 * The Wi-Fi-only and No-VPN pages are generated HTML loaded with `loadDataWithBaseURL(null, ...)`,
 * so the WebView reports `about:blank` (or a `data:` URL) as their address. The tab has to keep
 * showing the address the user asked for; taking `about:blank` over would turn it into an empty tab.
 */
object BlockPagePolicy {
    fun keepsTabUrl(showsBlockPage: Boolean, reportedUrl: String): Boolean =
        showsBlockPage && (reportedUrl == "about:blank" || reportedUrl.startsWith("data:"))

    /** True when the WebView shows a page we generated (no real origin): no URL, about:blank or a data: URL. */
    fun isGeneratedPage(webViewUrl: String?): Boolean =
        webViewUrl == null || webViewUrl == "about:blank" || webViewUrl.startsWith("data:")
}
