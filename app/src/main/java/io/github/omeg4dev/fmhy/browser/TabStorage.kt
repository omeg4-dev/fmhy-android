package io.github.omeg4dev.fmhy.browser

import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.webkit.WebView
import io.github.omeg4dev.fmhy.browser.download.VideoSource
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
    val blockedRequests: java.util.concurrent.ConcurrentLinkedQueue<BlockedRequest> = java.util.concurrent.ConcurrentLinkedQueue(),
    val bypass: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet(),
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var isLoading: Boolean = false,
    var progress: Float = 0f,
    var desktopSite: Boolean = false,
    var zoomLevel: Float = 1f,
    var zoomBaseScale: Float = 0f,
    var isProgrammaticZoom: Boolean = false,
    var isBackForwardNavigation: Boolean = false,
    var darkPage: Boolean = false,
    var sitePrefs: SitePrefs? = null,
    var rescueState: RescueState? = null,
    var siteBarState: SiteBarState? = null,
    var interstitialState: InterstitialState? = null,
    var isVideoPlaying: Boolean = false,
    @Volatile var videoWidth: Int = 0,
    @Volatile var videoHeight: Int = 0,
    @Volatile var hasMedia: Boolean = false,
    @Volatile var videoSources: List<VideoSource> = emptyList(),
    /** Video lists reported per frame ('' = top page); main thread only. */
    val videoFrames: MutableMap<String, List<VideoSource>> = mutableMapOf(),
    /** Playback speed to apply to this page's media; 0 = leave the page alone. */
    @Volatile var desiredRate: Float = 0f,
    var hasStartedFirstLoad: Boolean = false,
    var entryHost: String? = null,
    var lastLoadedHost: String? = null,
    var lastActiveTime: Long = System.currentTimeMillis(),
    @Volatile var lastTouchAt: Long = 0L,
    val allowedOnceUrls: MutableSet<String> = mutableSetOf(),
    val allowedHosts: MutableSet<String> = mutableSetOf(),
    val dismissedSiteBarHosts: MutableSet<String> = mutableSetOf(),
    @Volatile var sourceUrl: String = url,
    var webView: WebView? = null,
    var mutableContext: MutableContextWrapper? = null,
    var crashed: Boolean = false,
    var savedState: android.os.Bundle? = null,
    /** Something of the current page (not an error page) reached the screen. */
    var contentShown: Boolean = false,
    /** The tab's address was a real website at least once; never reset. */
    var hadSite: Boolean = false,
    /** Main-frame failure of the current load attempt; decided in onPageFinished. */
    var failedLoad: FailedLoad? = null,
    /** Quiet background retries already done for [silentRetryUrl]. */
    var silentRetries: Int = 0,
    var silentRetryUrl: String? = null,
    /** Pending silent retry, 5xx probe or rescue lookup; cancelled when a new page starts. */
    var rescueJob: kotlinx.coroutines.Job? = null,
    var stallJob: kotlinx.coroutines.Job? = null,
    var blankJob: kotlinx.coroutines.Job? = null,
    @Volatile var skipBlockingOnceUrl: String? = null,
    /** Main-frame URL the ad blocker last replaced with its block page (set off the main thread). */
    @Volatile var blockedMainFrameUrl: String? = null,
    /** Filter rule that replaced [blockedMainFrameUrl] (set off the main thread). */
    @Volatile var blockedMainFrameRule: String? = null,
    var showsBlockPage: Boolean = false,
    var showsWifiOnlyPage: Boolean = false,
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
        hadSite = hadSite,
        entryId = entryId,
        blocked = blockedCount.get(),
        blockedHosts = BlockedHosts.group(blockedRequests),
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        isLoading = isLoading,
        progress = progress,
        desktopSite = desktopSite,
        zoomLevel = zoomLevel,
        darkPage = darkPage,
        sitePrefs = sitePrefs,
        rescueState = rescueState,
        siteBarState = siteBarState,
        interstitialState = interstitialState,
        showsBlockPage = showsBlockPage,
        isVideoPlaying = isVideoPlaying,
        hasMedia = hasMedia,
        playbackSpeed = if (desiredRate > 0f) desiredRate else 1f,
        videoSources = videoSources,
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
    val saved = EmptyTabPolicy.restorable(tabList).map { SavedTab(it.url, it.title, isPrivate = false) }
    val jsonStr = Json.encodeToString(saved)
    userStore.put("browser_tabs", jsonStr)
}
