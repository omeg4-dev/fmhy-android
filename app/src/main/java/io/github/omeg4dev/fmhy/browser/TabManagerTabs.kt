package io.github.omeg4dev.fmhy.browser

/** Tab lifecycle: restore, open, select, and close operations for [TabManager]. */

import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

suspend fun TabManager.restoreTabsIfNeeded(initialUrl: String?, entryId: String? = null) {
    if (linkQueue.isRestored.get()) {
        if (initialUrl != null && initialUrl.isNotBlank()) {
            linkQueue.offerOrExecute(QueuedLink(initialUrl, entryId)) { link, select ->
                openQueuedLink(link, select)
            }
        }
        return
    }

    if (initialUrl != null && initialUrl.isNotBlank()) {
        linkQueue.enqueue(QueuedLink(initialUrl, entryId))
    }

    val prefs = settingsRepository.browserPrefs.first()
    var restoredAny = false
    if (prefs.restoreTabs) {
        val jsonStr = userStore.get("browser_tabs")
        if (!jsonStr.isNullOrBlank()) {
            val list = try {
                Json.decodeFromString<List<SavedTab>>(jsonStr)
            } catch (_: Exception) {
                emptyList()
            }
            val restoreTime = clock()
            for (st in list) {
                openTab(url = st.url, title = st.title, isPrivate = false, select = false, restoredAt = restoreTime)
                restoredAny = true
            }
        }
    }

    var hadPending = false
    _browserTabCount.value = tabs.value.size
    linkQueue.completeRestore { link, select ->
        hadPending = true
        openQueuedLink(link, select)
    }
    if (!hadPending) {
        if (!restoredAny) {
            openTab(url = "about:blank", title = "New tab", isPrivate = false, select = true)
        } else if (records.isNotEmpty() && _currentTabId.value == null) {
            selectTab(records.first().id)
        }
    }
    _restored.value = true
}

fun TabManager.openTab(
    url: String,
    title: String = "",
    entryId: String? = null,
    isPrivate: Boolean = false,
    select: Boolean = true,
    restoredAt: Long = 0L,
): Long {
    val id = nextId.getAndIncrement()
    val actualPrivate = isPrivate || (url == "about:blank" && currentPrivacyPrefs.privateTabsByDefault)
    val initialHost = try { Uri.parse(url).host } catch (_: Exception) { null }
    val record = TabRecord(
        id = id,
        url = url,
        title = if (title.isNotEmpty()) title else if (url == "about:blank" || url.isEmpty()) "New tab" else url,
        isPrivate = actualPrivate,
        entryId = entryId,
        entryHost = initialHost,
        chainStartHost = initialHost,
        lastLoadedHost = initialHost,
        desktopSite = currentBrowserPrefs.desktopByDefault,
        hadSite = !EmptyTabPolicy.isStartUrl(url),
        restoredAt = restoredAt,
    )

    synchronized(records) {
        records.add(record)
    }

    // Fetch site prefs for this host
    val host = initialHost
    if (!host.isNullOrEmpty()) {
        appScope.launch {
            val sp = userStore.sitePrefs(host)
            if (sp != null) {
                record.sitePrefs = sp
                notifyTabChanged(record.id)
            }
        }
    }

    updateTabsFlow(immediate = true)

    if (select || _currentTabId.value == null) {
        selectTab(id)
    }

    return id
}

fun TabManager.selectTab(tabId: Long) {
    val oldId = _currentTabId.value
    if (oldId != null && oldId != tabId) {
        previousTabId = oldId
        captureThumbnail(oldId)
        val oldRecord = getTabRecord(oldId)
        if (oldRecord != null) {
            pauseTab(oldRecord)
        }
    }
    val record = synchronized(records) {
        val r = records.firstOrNull { it.id == tabId } ?: return
        r.lastActiveTime = System.currentTimeMillis()
        _currentTabId.value = tabId
        ensureWebView(r)
        r
    }
    if (isActivityResumed) {
        resumeTab(record)
    }
    updateTabsFlow(immediate = true)
    // Close the previously active tab if it was empty (never showed a real site).
    // The newly selected tab is never closed, even if it is currently empty.
    if (oldId != null && oldId != tabId) {
        val old = getTabRecord(oldId)
        if (old != null && EmptyTabPolicy.isEmpty(old.url, old.hadSite)) {
            closeTab(oldId)
        }
    }
}

fun TabManager.restoreTab(
    url: String,
    title: String,
    index: Int,
    isPrivate: Boolean,
    entryId: String? = null,
    select: Boolean = true,
): Long {
    val id = nextId.getAndIncrement()
    val initialHost = try { Uri.parse(url).host } catch (_: Exception) { null }
    val record = TabRecord(
        id = id,
        url = url,
        title = if (title.isNotEmpty()) title else if (url == "about:blank" || url.isEmpty()) "New tab" else url,
        isPrivate = isPrivate,
        entryId = entryId,
        entryHost = initialHost,
        chainStartHost = initialHost,
        lastLoadedHost = initialHost,
        desktopSite = currentBrowserPrefs.desktopByDefault,
        hadSite = !EmptyTabPolicy.isStartUrl(url),
    )

    synchronized(records) {
        val insertIndex = index.coerceIn(0, records.size)
        records.add(insertIndex, record)
    }

    if (!initialHost.isNullOrEmpty()) {
        appScope.launch {
            val sp = userStore.sitePrefs(initialHost)
            if (sp != null) {
                record.sitePrefs = sp
                notifyTabChanged(record.id)
            }
        }
    }

    updateTabsFlow(immediate = true)
    if (select) {
        selectTab(id)
    }
    return id
}

fun TabManager.closeTab(tabId: Long) {
    mobileOkTabs.remove(tabId)
    vpnOkTabs.remove(tabId)
    releaseThumbnail(tabId)
    if (previousTabId == tabId) {
        previousTabId = null
    }
    synchronized(records) {
        val index = records.indexOfFirst { it.id == tabId }
        if (index == -1) return

        val removed = records.removeAt(index)
        removed.rescueJob?.cancel()
        removed.rescueJob = null
        removed.stallJob?.cancel()
        removed.stallJob = null
        removed.blankJob?.cancel()
        removed.blankJob = null
        removed.isWaitingRetry = false
        removed.isWaitingGraceRetry = false
        removed.webView?.destroy()
        removed.webView = null
        removed.mutableContext = null

        val remainingPrivate = records.any { it.isPrivate }
        if (removed.isPrivate && !remainingPrivate) {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                try {
                    androidx.webkit.ProfileStore.getInstance().deleteProfile("private")
                } catch (_: Exception) {}
            } else {
                // Fallback limitation: On devices without WebView multi-profile support, private tabs share
                // cookie and storage space with standard tabs. Cookies and WebStorage are cleared when
                // the last private tab closes.
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.WebStorage.getInstance().deleteAllData()
            }
        }

        if (records.isEmpty() && currentPrivacyPrefs.clearDataOnExit) {
            clearAllBrowsingData()
        }

        if (_currentTabId.value == tabId) {
            val nextTab = if (records.isNotEmpty()) {
                if (index < records.size) records[index] else records.last()
            } else null
            _currentTabId.value = nextTab?.id
            if (nextTab != null) {
                ensureWebView(nextTab)
            }
        }
    }
    updateTabsFlow(immediate = true)
}

fun TabManager.closeAllTabs() {
    mobileOkTabs.clear()
    vpnOkTabs.clear()
    releaseAllThumbnails()
    previousTabId = null
    val hadPrivate: Boolean
    synchronized(records) {
        hadPrivate = records.any { it.isPrivate }
        for (r in records) {
            r.rescueJob?.cancel()
            r.rescueJob = null
            r.stallJob?.cancel()
            r.stallJob = null
            r.blankJob?.cancel()
            r.blankJob = null
            r.isWaitingRetry = false
            r.isWaitingGraceRetry = false
            r.webView?.destroy()
            r.webView = null
            r.mutableContext = null
        }
        records.clear()
        _currentTabId.value = null
    }
    if (hadPrivate) {
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
            try {
                androidx.webkit.ProfileStore.getInstance().deleteProfile("private")
            } catch (_: Exception) {}
        } else {
            // Same fallback limitation as in closeTab.
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.WebStorage.getInstance().deleteAllData()
        }
    }
    if (currentPrivacyPrefs.clearDataOnExit) {
        clearAllBrowsingData()
    }
    updateTabsFlow(immediate = true)
}

/**
 * Closes every live tab AND forgets tabs that exist only in the restore store (browser not started this
 * session), so the FMHY chip count drops to 0. Used by "Close all tabs".
 */
fun TabManager.closeAllTabsIncludingRestorable() {
    closeAllTabs()
    _browserTabCount.value = 0
    appScope.launch {
        try { userStore.put("browser_tabs", "[]") } catch (_: Exception) {}
    }
}

/**
 * Closes every empty tab (including the currently active one). Called when the browser is
 * fully leaving the screen (back to FMHY, activity finishing). Returns the number of tabs
 * remaining after the closes.
 */
fun TabManager.closeEmptyTabsOnLeave(): Int {
    val toClose = EmptyTabPolicy.toCloseOnLeave(liveStates(), keepId = null)
    for (id in toClose) closeTab(id)
    return synchronized(records) { records.size }
}

/**
 * Opens a new empty tab page, reusing an existing empty tab when one is available instead of
 * stacking a second one. Returns the id of the tab that ended up selected.
 *
 * @param isPrivate Whether the new tab should be private.
 */
fun TabManager.openNewTabPage(isPrivate: Boolean): Long {
    val actualPrivate = isPrivate || (currentPrivacyPrefs.privateTabsByDefault)
    val reusable = EmptyTabPolicy.reusable(liveStates(), actualPrivate, _currentTabId.value)
    if (reusable != null) {
        selectTab(reusable.id)
        return reusable.id
    }
    return openTab(url = "about:blank", title = "New tab", isPrivate = isPrivate, select = true)
}

