package io.github.omeg4dev.fmhy.browser

/** WebView lifecycle, recycling, activity integration, and thumbnails for [TabManager]. */

import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView

fun TabManager.getThumbnail(tabId: Long): Bitmap? = thumbnails[tabId]

fun TabManager.hasThumbnail(tabId: Long): Boolean = thumbnails.containsKey(tabId)

fun TabManager.setThumbnailForTest(tabId: Long, bitmap: Bitmap) {
    thumbnails[tabId] = bitmap
    bumpThumbnailVersion()
}

fun TabManager.releaseThumbnail(tabId: Long) {
    thumbnails.remove(tabId)
    lastThumbnailCapture.remove(tabId)
    bumpThumbnailVersion()
}

fun TabManager.releaseAllThumbnails() {
    thumbnails.clear()
    lastThumbnailCapture.clear()
    bumpThumbnailVersion()
}

fun TabManager.captureThumbnail(tabId: Long) {
    val record = getTabRecord(tabId) ?: return
    if (record.isPrivate) {
        releaseThumbnail(tabId)
        return
    }
    val now = System.currentTimeMillis()
    val last = lastThumbnailCapture[tabId] ?: 0L
    if (now - last < 2000L) {
        return
    }
    lastThumbnailCapture[tabId] = now

    val webView = record.webView ?: return
    postToMain {
        try {
            val wvWidth = webView.width
            val wvHeight = webView.height
            if (wvWidth > 0 && wvHeight > 0) {
                val targetWidth = 320
                val targetHeight = ((wvHeight.toFloat() / wvWidth.toFloat()) * targetWidth).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                val scale = targetWidth.toFloat() / wvWidth.toFloat()
                canvas.scale(scale, scale)
                webView.draw(canvas)
                thumbnails[tabId] = bitmap
                bumpThumbnailVersion()
            }
        } catch (_: Throwable) {}
    }
}

fun TabManager.pauseTab(record: TabRecord) {
    val wv = record.webView ?: return
    try {
        wv.evaluateJavascript("document.querySelectorAll('video,audio').forEach(m=>m.pause())", null)
        wv.onPause()
    } catch (_: Throwable) {}
}

fun TabManager.resumeTab(record: TabRecord) {
    val wv = record.webView ?: return
    try {
        wv.onResume()
    } catch (_: Throwable) {}
}

/**
 * The activity is going away from the foreground. With [keepPlaying] the active tab keeps running
 * (its WebView is not paused and timers keep ticking) so its media goes on in the background;
 * every other tab is paused individually.
 */
fun TabManager.onActivityPause(keepPlaying: Boolean = false) {
    isActivityResumed = false
    val active = getActiveTabRecord()
    if (keepPlaying && active != null) {
        keepAliveTabId = active.id
        val others = synchronized(records) { records.filter { it.id != active.id } }
        others.forEach { pauseTab(it) }
        return
    }
    keepAliveTabId = null
    if (active != null && active.id != keepAliveTabId) {
        pauseTab(active)
    }
    try {
        val wv = active?.webView ?: synchronized(records) { records.firstNotNullOfOrNull { it.webView } }
        wv?.pauseTimers()
    } catch (_: Throwable) {}
}

fun TabManager.onActivityResume() {
    isActivityResumed = true
    keepAliveTabId = null
    val active = getActiveTabRecord()
    try {
        val wv = active?.webView ?: synchronized(records) { records.firstNotNullOfOrNull { it.webView } }
        wv?.resumeTimers()
    } catch (_: Throwable) {}
    if (active != null) {
        resumeTab(active)
    }
}

fun TabManager.evictWebView(record: TabRecord) {
    val wv = record.webView ?: return
    try {
        val b = Bundle()
        wv.saveState(b)
        record.savedState = b
    } catch (_: Throwable) {}
    try {
        val parent = wv.parent as? ViewGroup
        parent?.removeView(wv)
        wv.destroy()
    } catch (_: Throwable) {}
    record.webView = null
    record.mutableContext = null
    bumpWebViewVersion()
}

fun TabManager.onTrimMemory() {
    synchronized(records) {
        val currentId = _currentTabId.value
        val prevId = previousTabId
        for (r in records) {
            if (r.id != currentId && r.id != prevId && r.webView != null) {
                evictWebView(r)
            }
        }
    }
    updateTabsFlow()
}

fun TabManager.handleRenderProcessGone(view: WebView): Boolean {
    try {
        val parent = view.parent as? ViewGroup
        parent?.removeView(view)
        view.destroy()
    } catch (_: Throwable) {}
    synchronized(records) {
        val record = records.firstOrNull { it.webView === view }
        if (record != null) {
            record.webView = null
            record.mutableContext = null
            record.crashed = true
            bumpWebViewVersion()
        }
    }
    updateTabsFlow()
    return true
}

internal fun TabManager.ensureWebView(record: TabRecord) {
    if (record.webView != null) return

    try {
        // Check max live WebViews limit
        val maxTabs = currentBrowserPrefs.maxTabs.coerceAtLeast(1)
        val liveCount = synchronized(records) { records.count { it.webView != null } }
        if (liveCount >= maxTabs) {
            val lru = synchronized(records) {
                selectLruVictim(records, _currentTabId.value, previousTabId, record.id)
            }
            if (lru != null) {
                evictWebView(lru)
            }
        }

        val wrapper = MutableContextWrapper(webViewBase(attachedActivity ?: appContext, record))
        val webView = webViewFactory.createWebView(
            context = wrapper,
            record = record,
            tabManager = this,
            browserPrefs = currentBrowserPrefs,
            protectionPrefs = currentProtectionPrefs,
            appearance = currentAppearance,
            isAppDark = isAppDark,
            onPickerResult = { tId, sel -> onPickerResultCallback?.invoke(tId, sel) },
            onPickerCancel = { tId -> onPickerCancelCallback?.invoke(tId) },
            onExternalAppRequest = { intent, appName -> onExternalAppCallback?.invoke(intent, appName) },
        )
        record.mutableContext = wrapper
        record.webView = webView
        record.crashed = false
        bumpWebViewVersion()

        if (record.id != _currentTabId.value || !isActivityResumed) {
            try {
                webView.onPause()
            } catch (_: Throwable) {}
        } else {
            try {
                webView.onResume()
            } catch (_: Throwable) {}
        }

        if (record.savedState != null) {
            val restored = webView.restoreState(record.savedState!!)
            if ((restored == null || restored.size == 0) && record.url.isNotEmpty() && record.url != "about:blank") {
                if (BrowsePolicy.shouldBlock(record.url, currentDataUsagePrefs.browseWifiOnly, currentNetworkKind, record.id in mobileOkTabs)) {
                    loadWifiOnlyPage(record)
                } else if (blocksWithoutVpn(record.url, record.id)) {
                    loadNoVpnPage(record)
                } else {
                    record.showsWifiOnlyPage = false
                    record.showsNoVpnPage = false
                    webView.loadUrl(record.url)
                }
            }
        } else if (record.url.isNotEmpty() && record.url != "about:blank") {
            if (BrowsePolicy.shouldBlock(record.url, currentDataUsagePrefs.browseWifiOnly, currentNetworkKind, record.id in mobileOkTabs)) {
                loadWifiOnlyPage(record)
            } else if (blocksWithoutVpn(record.url, record.id)) {
                loadNoVpnPage(record)
            } else {
                record.showsWifiOnlyPage = false
                record.showsNoVpnPage = false
                webView.loadUrl(record.url)
            }
        }
    } catch (e: Throwable) {
        try {
            Log.w("TabManager", "ensureWebView failed for tab ${record.id}", e)
        } catch (_: Throwable) {}
        record.crashed = true
        record.webView = null
        record.mutableContext = null
        bumpWebViewVersion()
    }
}

/**
 * Destroys every live tab WebView (normal and private) through the eviction path, keeping tab
 * URL/state, then recreates and reattaches the current tab's WebView so the screen stays active.
 */
fun TabManager.recreateAllWebViews() {
    val currentId = _currentTabId.value
    val toEvict = synchronized(records) {
        for (r in records) {
            if (r.rescueState?.kind == LoadFailureKind.PROXY) {
                r.rescueState = null
            }
        }
        records.filter { it.webView != null }
    }
    var currentWasLive = false
    for (r in toEvict) {
        if (r.id == currentId) {
            currentWasLive = true
        }
        evictWebView(r)
    }
    if (currentWasLive && currentId != null) {
        val current = getTabRecord(currentId)
        if (current != null) {
            current.crashed = false
            ensureWebView(current)
        }
    }
    updateTabsFlow(immediate = true)
}

