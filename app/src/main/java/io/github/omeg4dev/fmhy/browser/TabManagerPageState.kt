package io.github.omeg4dev.fmhy.browser

/** Page state, site preferences, ad-blocking statistics, and history for [TabManager]. */

import android.graphics.Bitmap
import android.net.Uri
import io.github.omeg4dev.fmhy.browser.adblock.AllowRule
import io.github.omeg4dev.fmhy.data.user.SitePrefs
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
fun TabManager.clearAllBrowsingData() {
    postToMain {
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
            android.webkit.WebView(appContext).clearCache(true)
            android.webkit.WebViewDatabase.getInstance(appContext).clearFormData()
        } catch (_: Exception) {}
    }
}

fun TabManager.updateTabTitle(tabId: Long, title: String) {
    val record = getTabRecord(tabId) ?: return
    record.title = title
    updateTabsFlow()
}

fun TabManager.updateTabFavicon(tabId: Long, favicon: Bitmap?) {
    val record = getTabRecord(tabId) ?: return
    record.favicon = favicon
    updateTabsFlow()
}

fun TabManager.updateTabProgress(tabId: Long, isLoading: Boolean, progress: Float) {
    val record = getTabRecord(tabId) ?: return
    record.isLoading = isLoading
    record.progress = progress
    updateTabsFlow()
}

fun TabManager.incrementBlocked(tabId: Long, blocked: BlockedRequest) {
    val record = getTabRecord(tabId) ?: return
    record.blockedCount.incrementAndGet()
    if (record.blockedRequests.size < 300) {
        record.blockedRequests.add(blocked)
    }
    updateTabsFlow(immediate = false)
}

fun TabManager.incrementBlocked(tabId: Long) {
    val record = getTabRecord(tabId) ?: return
    record.blockedCount.incrementAndGet()
    updateTabsFlow(immediate = false)
}

fun TabManager.resetBlocked(tabId: Long) {
    val record = getTabRecord(tabId) ?: return
    record.blockedCount.set(0)
    record.blockedRequests.clear()
    updateTabsFlow(immediate = false)
}

fun TabManager.allowBlockedHost(tabId: Long, host: String) {
    val record = getTabRecord(tabId) ?: return
    val site = AllowRule.pageSite(record.url) ?: return
    record.bypass.add(AllowRule.bypassKey(site, host))
    appScope.launch {
        settingsRepository.updateBlocking {
            it.copy(customRules = AllowRule.append(it.customRules, AllowRule.forHost(host, site)))
        }
    }
    reloadTab(tabId)
}

fun TabManager.unallowBlockedHost(tabId: Long, host: String) {
    val record = getTabRecord(tabId) ?: return
    val site = AllowRule.pageSite(record.url) ?: return
    record.bypass.remove(AllowRule.bypassKey(site, host))
    appScope.launch {
        settingsRepository.updateBlocking {
            it.copy(customRules = AllowRule.remove(it.customRules, host, site))
        }
    }
    reloadTab(tabId)
}

fun TabManager.allowUrlOnce(tabId: Long, url: String) {
    val record = getTabRecord(tabId) ?: return
    record.allowedOnceUrls.add(url)
}

fun TabManager.notifyTabChanged(tabId: Long) {
    updateTabsFlow()
}

fun TabManager.recordHistory(tabId: Long, url: String, title: String) {
    val record = getTabRecord(tabId) ?: return
    if (!record.isPrivate && currentDataPrefs.keepHistory && url.isNotBlank() && url != "about:blank") {
        appScope.launch {
            userStore.addHistory(url, record.entryId, title.ifEmpty { url })
        }
    }
}

fun TabManager.updateSitePrefs(tabId: Long, host: String, update: (SitePrefs) -> SitePrefs) =
    updateSitePrefs(tabId, host, reload = true, update)

fun TabManager.updateSitePrefs(tabId: Long, host: String, reload: Boolean, update: (SitePrefs) -> SitePrefs) {
    appScope.launch {
        val existing = userStore.sitePrefs(host) ?: SitePrefs(host = host)
        val updated = update(existing)
        userStore.setSitePrefs(updated)
        val record = getTabRecord(tabId)
        if (record != null) {
            record.sitePrefs = updated
            if (reload) {
                record.webView?.let { wv ->
                    postToMain {
                        rebaseWebViewContext(record)
                        webViewFactory.applySettings(
                            wv,
                            record,
                            currentBrowserPrefs,
                            currentProtectionPrefs,
                            currentAppearance,
                            isAppDark
                        )
                        wv.reload()
                    }
                }
            }
        }
        updateTabsFlow()
    }
}

fun TabManager.clearSitePrefs(tabId: Long, host: String) {
    appScope.launch {
        userStore.clearSitePrefs(host)
        val record = getTabRecord(tabId)
        if (record != null) {
            record.sitePrefs = null
            record.webView?.let { wv ->
                postToMain {
                    rebaseWebViewContext(record)
                    webViewFactory.applySettings(
                        wv,
                        record,
                        currentBrowserPrefs,
                        currentProtectionPrefs,
                        currentAppearance,
                        isAppDark
                    )
                    wv.reload()
                }
            }
        }
        updateTabsFlow()
    }
}

fun TabManager.toggleDesktopSite(tabId: Long) {
    val record = getTabRecord(tabId) ?: return
    val newDesktop = !record.desktopSite
    record.desktopSite = newDesktop
    val host = try { Uri.parse(record.url).host } catch (_: Exception) { null }
    if (!host.isNullOrEmpty()) {
        updateSitePrefs(tabId, host) { it.copy(desktop = newDesktop) }
    } else {
        record.webView?.let { wv ->
            rebaseWebViewContext(record)
            webViewFactory.applySettings(wv, record, currentBrowserPrefs, currentProtectionPrefs, currentAppearance, isAppDark)
            wv.reload()
        }
        updateTabsFlow()
    }
}

fun TabManager.startElementPicker(tabId: Long) {
    val record = getTabRecord(tabId) ?: return
    val js = io.github.omeg4dev.fmhy.browser.adblock.SelectorPicker.buildPickerJs(bridgeName)
    record.webView?.evaluateJavascript(js, null)
}
