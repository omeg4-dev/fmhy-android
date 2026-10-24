package io.github.omeg4dev.fmhy.browser

import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.webkit.WebView
import io.github.omeg4dev.fmhy.data.user.SitePrefs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TabRecord(
    val id: Long,
    var url: String,
    var title: String,
    var favicon: Bitmap? = null,
    val isPrivate: Boolean = false,
    var entryId: String? = null,
    val blockedCount: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(0),
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var isLoading: Boolean = false,
    var progress: Float = 0f,
    var desktopSite: Boolean = false,
    var darkPage: Boolean = false,
    var sitePrefs: SitePrefs? = null,
    var rescueState: RescueState? = null,
    var siteBarState: SiteBarState? = null,
    var interstitialState: InterstitialState? = null,
    var isVideoPlaying: Boolean = false,
    @Volatile var videoWidth: Int = 0,
    @Volatile var videoHeight: Int = 0,
    @Volatile var hasMedia: Boolean = false,
    /** Playback speed to apply to this page's media; 0 = leave the page alone. */
    @Volatile var desiredRate: Float = 0f,
    var hasStartedFirstLoad: Boolean = false,
    var entryHost: String? = null,
    var lastLoadedHost: String? = null,
    var lastActiveTime: Long = System.currentTimeMillis(),
    val allowedOnceUrls: MutableSet<String> = mutableSetOf(),
    val allowedHosts: MutableSet<String> = mutableSetOf(),
    val dismissedSiteBarHosts: MutableSet<String> = mutableSetOf(),
    @Volatile var sourceUrl: String = url,
    var webView: WebView? = null,
    var mutableContext: MutableContextWrapper? = null,
    var crashed: Boolean = false,
    var savedState: android.os.Bundle? = null,
) {
    var blocked: Int
        get() = blockedCount.get()
        set(value) { blockedCount.set(value) }

    fun toState(): TabState = TabState(
        id = id,
        url = url,
        title = title,
        favicon = favicon,
        isPrivate = isPrivate,
        entryId = entryId,
        blocked = blockedCount.get(),
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        isLoading = isLoading,
        progress = progress,
        desktopSite = desktopSite,
        darkPage = darkPage,
        sitePrefs = sitePrefs,
        rescueState = rescueState,
        siteBarState = siteBarState,
        interstitialState = interstitialState,
        isVideoPlaying = isVideoPlaying,
        hasMedia = hasMedia,
        playbackSpeed = if (desiredRate > 0f) desiredRate else 1f,
        videoAspect = if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight else null,
        crashed = crashed,
        lastActiveTime = lastActiveTime,
    )
}

fun selectLruVictim(
    records: List<TabRecord>,
    currentTabId: Long?,
    previousTabId: Long?,
    candidateTabId: Long? = null,
): TabRecord? {
    return records
        .filter {
            it.webView != null &&
            it.id != currentTabId &&
            it.id != previousTabId &&
            it.id != candidateTabId
        }
        .minByOrNull { it.lastActiveTime }
}

internal suspend fun TabManager.saveTabsToStore(tabList: List<TabState>) {
    val nonPrivate = tabList.filter { !it.isPrivate && it.url.isNotBlank() && it.url != "about:blank" }
    val saved = nonPrivate.map { SavedTab(it.url, it.title, isPrivate = false) }
    val jsonStr = Json.encodeToString(saved)
    userStore.put("browser_tabs", jsonStr)
}
