package io.github.omeg4dev.fmhy.browser

/** Navigation, URL loading, network/VPN gating, and site warnings for [TabManager]. */

import android.net.Uri
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.SettingsPage
import io.github.omeg4dev.fmhy.SettingsPageNavKey
import kotlinx.coroutines.launch

fun TabManager.allowMobileOnce(tabId: Long) {
    mobileOkTabs.add(tabId)
}

fun TabManager.allowWithoutVpnOnce(tabId: Long) {
    vpnOkTabs.add(tabId)
}

fun TabManager.openVpnSettings() {
    io.github.omeg4dev.fmhy.ui.vpn.openVpnSettings(appContext)
}

fun TabManager.blocksWithoutVpn(url: String, tabId: Long): Boolean =
    VpnGate.shouldBlock(
        url = url,
        blockWithoutVpn = currentProtectionPrefs.blockWithoutVpn,
        showVpnState = currentProtectionPrefs.showVpnState,
        vpn = currentVpnState,
        tabOverride = tabId in vpnOkTabs,
    )

fun TabManager.openDataUsageSettings() {
    onOpenDataUsageSettings?.invoke() ?: run {
        val container = (appContext as? FmhyApp)?.container
        container?.pushNavigation(SettingsPageNavKey(SettingsPage.DATA, focusSetting = "data_usage.browse_wifi_only"))
        bringFmhyToFront(appContext)
    }
}

fun TabManager.openFileCheckSettings(focusSetting: String? = null) {
    val container = (appContext as? FmhyApp)?.container
    container?.pushNavigation(SettingsPageNavKey(SettingsPage.DOWNLOADS, focusSetting))
    bringFmhyToFront(appContext)
}

fun TabManager.openProxySettings() {
    val container = (appContext as? FmhyApp)?.container
    container?.pushNavigation(SettingsPageNavKey(SettingsPage.CONNECTION, "protection.proxy"))
    bringFmhyToFront(appContext)
}

private data class HtmlThemeColors(
    val accentHex: String,
    val surfaceHex: String,
    val onSurfaceHex: String,
)

private fun TabManager.htmlThemeColors(): HtmlThemeColors {
    val accent = String.format("#%06X", 0xFFFFFF and currentAppearance.accentSeed)
    val surface = if (isAppDark) {
        if (currentAppearance.pureBlack) "#121212" else "#1e1e1e"
    } else {
        "#ffffff"
    }
    val onSurface = if (isAppDark) "#e0e0e0" else "#1f1f1f"
    return HtmlThemeColors(accent, surface, onSurface)
}

fun TabManager.buildWifiOnlyHtml(): String {
    val colors = htmlThemeColors()
    return WifiOnlyPage.html(
        isDark = isAppDark,
        isBlack = isAppDark && currentAppearance.pureBlack,
        accentHex = colors.accentHex,
        surfaceHex = colors.surfaceHex,
        onSurfaceHex = colors.onSurfaceHex,
        bridgeName = bridgeName,
    )
}

fun TabManager.loadWifiOnlyPage(record: TabRecord) {
    record.showsWifiOnlyPage = true
    record.webView?.loadDataWithBaseURL(null, buildWifiOnlyHtml(), "text/html", "utf-8", null)
    updateTabsFlow()
}

fun TabManager.buildNoVpnHtml(): String {
    val colors = htmlThemeColors()
    return NoVpnPage.html(
        isDark = isAppDark,
        isBlack = isAppDark && currentAppearance.pureBlack,
        accentHex = colors.accentHex,
        surfaceHex = colors.surfaceHex,
        onSurfaceHex = colors.onSurfaceHex,
        bridgeName = bridgeName,
    )
}

fun TabManager.loadNoVpnPage(record: TabRecord) {
    record.showsNoVpnPage = true
    record.webView?.loadDataWithBaseURL(null, buildNoVpnHtml(), "text/html", "utf-8", null)
    updateTabsFlow()
}

fun TabManager.reloadTab(tabId: Long) {
    val record = getTabRecord(tabId) ?: return
    if (record.crashed || record.webView == null) {
        record.crashed = false
        record.savedState = null
        ensureWebView(record)
    } else {
        if (BrowsePolicy.shouldBlock(record.url, currentDataUsagePrefs.browseWifiOnly, currentNetworkKind, tabId in mobileOkTabs)) {
            loadWifiOnlyPage(record)
        } else if (blocksWithoutVpn(record.url, tabId)) {
            loadNoVpnPage(record)
        } else {
            record.showsWifiOnlyPage = false
            record.showsNoVpnPage = false
            val currentWvUrl = record.webView?.url
            if (currentWvUrl == null || currentWvUrl == "about:blank" || currentWvUrl.startsWith("data:")) {
                if (currentPrivacyPrefs.sendGpc) {
                    record.webView?.loadUrl(record.url, mapOf("Sec-GPC" to "1", "DNT" to "1"))
                } else {
                    record.webView?.loadUrl(record.url)
                }
            } else {
                record.webView?.reload()
            }
        }
    }
    updateTabsFlow()
}

fun TabManager.stopTab(tabId: Long) {
    val record = getTabRecord(tabId) ?: return
    record.webView?.stopLoading()
}

fun TabManager.canGoBack(tabId: Long): Boolean = getTabRecord(tabId)?.webView?.canGoBack() == true

fun TabManager.goBack(tabId: Long): Boolean {
    val record = getTabRecord(tabId) ?: return false
    if (record.webView?.canGoBack() == true) {
        record.isBackForwardNavigation = true
        record.webView?.goBack()
        return true
    }
    return false
}

fun TabManager.goForward(tabId: Long): Boolean {
    val record = getTabRecord(tabId) ?: return false
    if (record.webView?.canGoForward() == true) {
        record.isBackForwardNavigation = true
        record.webView?.goForward()
        return true
    }
    return false
}

fun TabManager.loadUrl(tabId: Long, url: String, entryId: String? = null) {
    val record = getTabRecord(tabId) ?: return
    record.isBackForwardNavigation = false
    record.zoomLevel = 1f
    record.zoomBaseScale = 0f
    if (entryId != null) {
        record.entryId = entryId
    }
    record.url = url
    record.sourceUrl = url
    record.chainStartHost = try { Uri.parse(url).host } catch (_: Exception) { null }
    if (!EmptyTabPolicy.isStartUrl(url)) record.hadSite = true
    ensureWebView(record)
    if (BrowsePolicy.shouldBlock(url, currentDataUsagePrefs.browseWifiOnly, currentNetworkKind, tabId in mobileOkTabs)) {
        loadWifiOnlyPage(record)
    } else if (blocksWithoutVpn(url, tabId)) {
        loadNoVpnPage(record)
    } else {
        record.showsWifiOnlyPage = false
        record.showsNoVpnPage = false
        if (currentPrivacyPrefs.sendGpc) {
            record.webView?.loadUrl(url, mapOf("Sec-GPC" to "1", "DNT" to "1"))
        } else {
            record.webView?.loadUrl(url)
        }
    }
    updateTabsFlow()
}

fun TabManager.updateTabUrlAndTitle(tabId: Long, url: String, title: String) {
    val record = getTabRecord(tabId) ?: return
    record.url = url
    if (!EmptyTabPolicy.isStartUrl(url)) record.hadSite = true
    record.hasStartedFirstLoad = true
    if (title.isNotEmpty()) record.title = title
    val host = try { Uri.parse(url).host } catch (_: Exception) { null }
    if (!host.isNullOrEmpty() && host != record.lastLoadedHost) {
        record.lastLoadedHost = host
        appScope.launch {
            val sp = userStore.sitePrefs(host)
            record.sitePrefs = sp
            notifyTabChanged(tabId)
        }
    }
    updateTabsFlow(immediate = false)
}

fun TabManager.setRescueState(tabId: Long, rescueState: RescueState?) {
    val record = getTabRecord(tabId) ?: return
    record.rescueState = rescueState
    updateTabsFlow()
}

fun TabManager.setSiteBarState(tabId: Long, siteBarState: SiteBarState?) {
    val record = getTabRecord(tabId) ?: return
    record.siteBarState = siteBarState
    updateTabsFlow()
}

fun TabManager.setInterstitialState(tabId: Long, interstitialState: InterstitialState?) {
    val record = getTabRecord(tabId) ?: return
    record.interstitialState = interstitialState
    updateTabsFlow()
}

fun TabManager.allowHost(tabId: Long, host: String) {
    val record = getTabRecord(tabId) ?: return
    record.allowedHosts.add(host)
    record.interstitialState = null
    updateTabsFlow()
}

/** "Continue anyway" on the warning: lets the page through the unsafe check and the filter, for this tab only. */
fun TabManager.continueAnyway(tabId: Long) {
    val record = getTabRecord(tabId) ?: return
    val state = record.interstitialState ?: return
    record.allowedHosts.add(state.host)
    record.interstitialState = null
    if (state.filterRule != null) {
        record.allowedOnceUrls.add(record.blockedMainFrameUrl ?: state.url)
        record.webView?.reload()
    }
    updateTabsFlow()
}

fun TabManager.dismissSiteBar(tabId: Long, host: String) {
    val record = getTabRecord(tabId) ?: return
    record.dismissedSiteBarHosts.add(host)
    record.siteBarState = null
    updateTabsFlow()
}

