package io.github.omeg4dev.fmhy.browser

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.view.drawToBitmap
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.ScriptletLibrary
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.AutoplayMode
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.DataPrefs
import io.github.omeg4dev.fmhy.data.PrivacyPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.user.SitePrefs
import io.github.omeg4dev.fmhy.data.user.UserStore
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

@OptIn(FlowPreview::class)
class TabManager(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    val userStore: UserStore,
    val adblockService: AdblockService,
    private val scriptletLibrary: ScriptletLibrary,
    private val browserHooks: BrowserHooks,
    val appScope: CoroutineScope,
) {
    val bridgeName = "_fmhy" + (1..8).map { ('a'..'z').random() }.joinToString("")
    private val mainHandler by lazy {
        try {
            Handler(Looper.getMainLooper())
        } catch (_: Throwable) {
            null
        }
    }
    private val nextId = AtomicLong(1L)

    private val records = mutableListOf<TabRecord>()
    private val _tabs = MutableStateFlow<List<TabState>>(emptyList())
    val tabs: StateFlow<List<TabState>> = _tabs.asStateFlow()

    private val _browserTabCount = MutableStateFlow(0)

    /**
     * Number of open tabs, including tabs that will be restored the next time the browser starts
     * (read once from the same store restore uses), so FMHY screens can show the switch chip after an app restart.
     */
    val browserTabCount: StateFlow<Int> = _browserTabCount.asStateFlow()

    private val _showTabsRequested = MutableStateFlow(false)

    /** Set by an intent (Home card "All tabs"); the browser screen opens the tabs sheet and calls [consumeShowTabs]. */
    val showTabsRequested: StateFlow<Boolean> = _showTabsRequested.asStateFlow()

    fun requestShowTabs() { _showTabsRequested.value = true }
    fun consumeShowTabs() { _showTabsRequested.value = false }

    private val _currentTabId = MutableStateFlow<Long?>(null)
    val currentTabId: StateFlow<Long?> = _currentTabId.asStateFlow()

    private val _webViewVersion = MutableStateFlow(0)
    val webViewVersion: StateFlow<Int> = _webViewVersion.asStateFlow()

    fun bumpWebViewVersion() {
        _webViewVersion.value++
    }

    private val _thumbnailVersion = MutableStateFlow(0)
    val thumbnailVersion: StateFlow<Int> = _thumbnailVersion.asStateFlow()

    fun bumpThumbnailVersion() {
        _thumbnailVersion.value++
    }

    private val throttler = TabUpdateThrottler(
        intervalMs = 100L,
        postDelayed = { delayMs, action ->
            if (delayMs <= 0L) {
                postToMain(action)
            } else {
                val handler = mainHandler
                if (handler != null) {
                    handler.postDelayed(action, delayMs)
                } else {
                    postToMain(action)
                }
            }
        },
        emit = {
            postToMain {
                val list = synchronized(records) {
                    records.map { it.toState() }
                }
                _tabs.value = list
            }
        }
    )

    fun updateTabsFlow(immediate: Boolean = false) {
        if (immediate) {
            throttler.emitImmediate()
        } else {
            throttler.requestThrottled()
        }
    }

    var keepAliveTabId: Long? = null
    var previousTabId: Long? = null
    private var isActivityResumed = false

    private var attachedActivity: Activity? = null
    val linkQueue = BrowserLinkQueue()

    private val webViewFactory = BrowserWebViewFactory(
        adblockService = adblockService,
        scriptletLibrary = scriptletLibrary,
        browserHooks = browserHooks,
        bridgeName = bridgeName,
    )

    // Callbacks for UI events
    var onPickerResultCallback: ((tabId: Long, selector: String) -> Unit)? = null
    var onPickerCancelCallback: ((tabId: Long) -> Unit)? = null
    var onExternalAppCallback: ((intent: android.content.Intent, appName: String) -> Unit)? = null
    var onVideoPlayingChanged: ((Boolean) -> Unit)? = null
    /** Fired (main thread) when something picture-in-picture depends on changed for the current tab. */
    var onPipStateChanged: (() -> Unit)? = null

    private val thumbnails = java.util.concurrent.ConcurrentHashMap<Long, Bitmap>()
    private val lastThumbnailCapture = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    fun getThumbnail(tabId: Long): Bitmap? = thumbnails[tabId]

    fun hasThumbnail(tabId: Long): Boolean = thumbnails.containsKey(tabId)

    fun setThumbnailForTest(tabId: Long, bitmap: Bitmap) {
        thumbnails[tabId] = bitmap
        bumpThumbnailVersion()
    }

    fun releaseThumbnail(tabId: Long) {
        thumbnails.remove(tabId)
        lastThumbnailCapture.remove(tabId)
        bumpThumbnailVersion()
    }

    fun releaseAllThumbnails() {
        thumbnails.clear()
        lastThumbnailCapture.clear()
        bumpThumbnailVersion()
    }

    fun captureThumbnail(tabId: Long) {
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

    // Cache of prefs to configure WebViews synchronously
    @Volatile var currentBrowserPrefs = BrowserPrefs()
    @Volatile var currentProtectionPrefs = ProtectionPrefs()
    @Volatile var currentPrivacyPrefs = PrivacyPrefs()
    @Volatile var currentDataPrefs = DataPrefs()
    @Volatile var currentAppearance = Appearance()
    @Volatile var isAppDark = false

    init {
        (browserHooks as? DefaultBrowserHooks)?.attachTabManager(this)
        appScope.launch {
            var lastAutoplay: AutoplayMode? = null
            settingsRepository.browserPrefs.collect {
                currentBrowserPrefs = it
                if (lastAutoplay != null && lastAutoplay != it.autoplay) applyAutoplayToWebViews(it.autoplay)
                lastAutoplay = it.autoplay
            }
        }
        appScope.launch {
            try {
                if (settingsRepository.browserPrefs.first().restoreTabs && !linkQueue.isRestored.get() && tabs.value.isEmpty()) {
                    val saved = userStore.get("browser_tabs")
                    if (!saved.isNullOrBlank()) {
                        val n = Json.decodeFromString<List<SavedTab>>(saved).size
                        if (!linkQueue.isRestored.get() && tabs.value.isEmpty()) _browserTabCount.value = n
                    }
                }
            } catch (_: Exception) {}
        }
        appScope.launch {
            tabs.collect { list ->
                if (list.isNotEmpty() || linkQueue.isRestored.get()) _browserTabCount.value = list.size
            }
        }
        appScope.launch {
            settingsRepository.protectionPrefs.collect { currentProtectionPrefs = it }
        }
        appScope.launch {
            settingsRepository.privacyPrefs.collect { p ->
                currentPrivacyPrefs = p
                postToMain {
                    try {
                        val cm = android.webkit.CookieManager.getInstance()
                        synchronized(records) { records.toList() }.forEach { r ->
                            r.webView?.let { cm.setAcceptThirdPartyCookies(it, !p.blockThirdPartyCookies) }
                        }
                    } catch (_: Throwable) {}
                }
            }
        }
        appScope.launch {
            settingsRepository.dataPrefs.collect { currentDataPrefs = it }
        }
        appScope.launch {
            settingsRepository.appearance.collect { appearance ->
                currentAppearance = appearance
                isAppDark = when (appearance.themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isAppDark
                }
            }
        }

        // Save tabs on every change debounced 1s
        appScope.launch {
            tabs
                .drop(1)
                .debounce(1000L)
                .collect { currentTabs ->
                    saveTabsToStore(currentTabs)
                }
        }
    }

    private suspend fun saveTabsToStore(tabList: List<TabState>) {
        val nonPrivate = tabList.filter { !it.isPrivate && it.url.isNotBlank() && it.url != "about:blank" }
        val saved = nonPrivate.map { SavedTab(it.url, it.title, isPrivate = false) }
        val jsonStr = Json.encodeToString(saved)
        userStore.put("browser_tabs", jsonStr)
    }

    fun postToMain(action: () -> Unit) {
        try {
            val mainLooper = Looper.getMainLooper()
            if (mainLooper != null && Looper.myLooper() == mainLooper) {
                action()
            } else {
                val handler = mainHandler
                if (handler != null) {
                    handler.post(action)
                } else {
                    action()
                }
            }
        } catch (_: Throwable) {
            action()
        }
    }

    fun attachActivity(activity: Activity, isDark: Boolean) {
        attachedActivity = activity
        isAppDark = isDark
        for (r in records) {
            r.mutableContext?.baseContext = activity
        }
    }

    fun detachActivity(activity: Activity? = null) {
        if (activity != null && attachedActivity !== activity) {
            return
        }
        attachedActivity = null
        for (r in records) {
            r.mutableContext?.baseContext = appContext
        }
    }

    fun openQueuedLink(link: QueuedLink, select: Boolean) {
        if (!link.newTab) {
            val existing = findTabByUrl(link.url)
            if (existing != null) {
                if (select) {
                    selectTab(existing.id)
                }
                return
            }
        }
        openTab(url = link.url, entryId = link.entryId, isPrivate = false, select = select)
    }

    suspend fun restoreTabsIfNeeded(initialUrl: String?, entryId: String? = null) {
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
                for (st in list) {
                    openTab(url = st.url, title = st.title, isPrivate = false, select = false)
                    restoredAny = true
                }
            }
        }

        linkQueue.isRestored.set(true)
        _browserTabCount.value = tabs.value.size

        if (linkQueue.hasPendingLinks()) {
            linkQueue.drain { link, select ->
                openQueuedLink(link, select)
            }
        } else if (!restoredAny) {
            openTab(url = "about:blank", title = "New tab", isPrivate = false, select = true)
        } else if (records.isNotEmpty() && _currentTabId.value == null) {
            selectTab(records.first().id)
        }
    }

    fun getTabRecord(tabId: Long): TabRecord? = synchronized(records) {
        records.firstOrNull { it.id == tabId }
    }

    fun findTabByUrl(url: String): TabRecord? = synchronized(records) {
        records.firstOrNull { it.url == url && !it.isPrivate }
    }

    fun openTab(
        url: String,
        title: String = "",
        entryId: String? = null,
        isPrivate: Boolean = false,
        select: Boolean = true
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
            lastLoadedHost = initialHost,
            desktopSite = currentBrowserPrefs.desktopByDefault,
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

    fun selectTab(tabId: Long) {
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
    }

    fun restoreTab(
        url: String,
        title: String,
        index: Int,
        isPrivate: Boolean,
        entryId: String? = null,
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
            lastLoadedHost = initialHost,
            desktopSite = currentBrowserPrefs.desktopByDefault,
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
        selectTab(id)
        return id
    }

    fun closeTab(tabId: Long) {
        releaseThumbnail(tabId)
        if (previousTabId == tabId) {
            previousTabId = null
        }
        synchronized(records) {
            val index = records.indexOfFirst { it.id == tabId }
            if (index == -1) return

            val removed = records.removeAt(index)
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

    fun closeAllTabs() {
        releaseAllThumbnails()
        previousTabId = null
        val hadPrivate: Boolean
        synchronized(records) {
            hadPrivate = records.any { it.isPrivate }
            for (r in records) {
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

    fun pauseTab(record: TabRecord) {
        val wv = record.webView ?: return
        try {
            wv.evaluateJavascript("document.querySelectorAll('video,audio').forEach(m=>m.pause())", null)
            wv.onPause()
        } catch (_: Throwable) {}
    }

    fun resumeTab(record: TabRecord) {
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
    fun onActivityPause(keepPlaying: Boolean = false) {
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

    fun onActivityResume() {
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

    fun evictWebView(record: TabRecord) {
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

    fun onTrimMemory() {
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

    fun handleRenderProcessGone(view: WebView): Boolean {
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

    private fun ensureWebView(record: TabRecord) {
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

            val wrapper = MutableContextWrapper(attachedActivity ?: appContext)
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
                if (restored == null && record.url.isNotEmpty() && record.url != "about:blank") {
                    webView.loadUrl(record.url)
                }
            } else if (record.url.isNotEmpty() && record.url != "about:blank") {
                webView.loadUrl(record.url)
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

    fun reloadTab(tabId: Long) {
        val record = getTabRecord(tabId) ?: return
        if (record.crashed || record.webView == null) {
            record.crashed = false
            record.savedState = null
            ensureWebView(record)
        } else {
            record.webView?.reload()
        }
        updateTabsFlow()
    }

    fun stopTab(tabId: Long) {
        val record = getTabRecord(tabId) ?: return
        record.webView?.stopLoading()
    }

    fun goBack(tabId: Long): Boolean {
        val record = getTabRecord(tabId) ?: return false
        if (record.webView?.canGoBack() == true) {
            record.webView?.goBack()
            return true
        }
        return false
    }

    fun goForward(tabId: Long): Boolean {
        val record = getTabRecord(tabId) ?: return false
        if (record.webView?.canGoForward() == true) {
            record.webView?.goForward()
            return true
        }
        return false
    }

    fun loadUrl(tabId: Long, url: String) {
        val record = getTabRecord(tabId) ?: return
        record.url = url
        record.sourceUrl = url
        ensureWebView(record)
        if (currentPrivacyPrefs.sendGpc) {
            record.webView?.loadUrl(url, mapOf("Sec-GPC" to "1", "DNT" to "1"))
        } else {
            record.webView?.loadUrl(url)
        }
        updateTabsFlow()
    }

    fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) {
        val record = getTabRecord(tabId) ?: return
        record.url = url
        record.hasStartedFirstLoad = true
        record.rescueState = null
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

    fun setRescueState(tabId: Long, rescueState: RescueState?) {
        val record = getTabRecord(tabId) ?: return
        record.rescueState = rescueState
        updateTabsFlow()
    }

    fun setSiteBarState(tabId: Long, siteBarState: SiteBarState?) {
        val record = getTabRecord(tabId) ?: return
        record.siteBarState = siteBarState
        updateTabsFlow()
    }

    fun setInterstitialState(tabId: Long, interstitialState: InterstitialState?) {
        val record = getTabRecord(tabId) ?: return
        record.interstitialState = interstitialState
        updateTabsFlow()
    }

    fun allowHost(tabId: Long, host: String) {
        val record = getTabRecord(tabId) ?: return
        record.allowedHosts.add(host)
        record.interstitialState = null
        updateTabsFlow()
    }

    fun dismissSiteBar(tabId: Long, host: String) {
        val record = getTabRecord(tabId) ?: return
        record.dismissedSiteBarHosts.add(host)
        record.siteBarState = null
        updateTabsFlow()
    }

    fun setVideoPlaying(tabId: Long, isPlaying: Boolean) {
        val record = getTabRecord(tabId) ?: return
        record.isVideoPlaying = isPlaying
        updateTabsFlow()
        if (_currentTabId.value == tabId) {
            onVideoPlayingChanged?.invoke(isPlaying)
            onPipStateChanged?.invoke()
        }
    }

    /** Applies the autoplay setting to every live WebView straight away. */
    private fun applyAutoplayToWebViews(mode: AutoplayMode) {
        val views = synchronized(records) { records.mapNotNull { it.webView } }
        val js = VideoJs.setAutoplayCall(autoplayScriptMode(mode))
        postToMain {
            views.forEach { wv ->
                try {
                    wv.settings.mediaPlaybackRequiresUserGesture = autoplayNeedsGesture(mode)
                    wv.evaluateJavascript(js, null)
                } catch (_: Throwable) {}
            }
        }
    }

    /** Pauses or resumes the media of [tabId]; used by the background playback notification. */
    fun controlMedia(tabId: Long, play: Boolean) {
        val record = getTabRecord(tabId) ?: return
        postToMain {
            val wv = record.webView ?: return@postToMain
            try {
                if (play) {
                    // A programmatic play() has no user gesture; allow it for this call only.
                    val strict = wv.settings.mediaPlaybackRequiresUserGesture
                    if (strict) wv.settings.mediaPlaybackRequiresUserGesture = false
                    wv.evaluateJavascript(VideoJs.RESUME_MEDIA, null)
                    if (strict) mainHandler?.postDelayed({
                        try { wv.settings.mediaPlaybackRequiresUserGesture = currentBrowserPrefs.autoplay == AutoplayMode.BLOCK } catch (_: Throwable) {}
                    }, 1500L)
                } else {
                    wv.evaluateJavascript(VideoJs.PAUSE_MEDIA, null)
                }
            } catch (_: Throwable) {}
        }
    }

    /** Background playback is over (closed, idle too long): stop the media and pause the tab like normal. */
    fun endBackgroundPlayback() {
        val id = keepAliveTabId
        keepAliveTabId = null
        if (id == null) return
        val record = getTabRecord(id)
        postToMain {
            val wv = record?.webView
            if (wv != null) {
                try { wv.evaluateJavascript(VideoJs.PAUSE_MEDIA, null) } catch (_: Throwable) {}
            }
            if (!isActivityResumed) {
                if (record != null) pauseTab(record)
                // Timers are global: the kept tab's WebView was the one left running.
                try {
                    val any = wv ?: synchronized(records) { records.firstNotNullOfOrNull { it.webView } }
                    any?.pauseTimers()
                } catch (_: Throwable) {}
            }
        }
    }

    /** Skips the active tab's video by [seconds] (negative = back). */
    fun seekActiveVideo(seconds: Int) {
        val record = getActiveTabRecord() ?: return
        val js = VideoJs.seekCall(seconds)
        postToMain {
            try { record.webView?.evaluateJavascript(js, null) } catch (_: Throwable) {}
        }
    }

    fun setHasMedia(tabId: Long) {
        val record = getTabRecord(tabId) ?: return
        if (record.hasMedia) return
        record.hasMedia = true
        updateTabsFlow()
    }

    /** Called when a page starts loading: forgets the last page's media and looks up its remembered speed. */
    fun onPlaybackPageStarted(tabId: Long, url: String) {
        val record = getTabRecord(tabId) ?: return
        record.hasMedia = false
        record.desiredRate = 0f
        record.videoWidth = 0
        record.videoHeight = 0
        val host = try { Uri.parse(url).host } catch (_: Exception) { null }
        appScope.launch {
            val stored = if (host.isNullOrEmpty()) null else try {
                parseStoredSpeed(userStore.get(videoSpeedKey(host)))
            } catch (_: Exception) { null }
            val rate = stored ?: currentBrowserPrefs.defaultSpeed.takeIf { it != 1f } ?: 0f
            record.desiredRate = rate
            if (rate > 0f) pushPlaybackRate(record)
            updateTabsFlow()
        }
    }

    /** Sets the playback speed for the tab's media and remembers it for the site (not in private tabs). */
    fun setPlaybackSpeed(tabId: Long, speed: Float) {
        val record = getTabRecord(tabId) ?: return
        record.desiredRate = speed
        pushPlaybackRate(record)
        updateTabsFlow(immediate = true)
        val host = try { Uri.parse(record.url).host } catch (_: Exception) { null }
        if (!record.isPrivate && !host.isNullOrEmpty()) {
            appScope.launch {
                try { userStore.put(videoSpeedKey(host), formatStoredSpeed(speed)) } catch (_: Exception) {}
            }
        }
    }

    private fun pushPlaybackRate(record: TabRecord) {
        val js = VideoJs.setRateCall(record.desiredRate)
        postToMain {
            try { record.webView?.evaluateJavascript(js, null) } catch (_: Throwable) {}
        }
    }

    fun setVideoSize(tabId: Long, width: Int, height: Int) {
        val record = getTabRecord(tabId) ?: return
        if (width <= 0 || height <= 0) return
        if (record.videoWidth == width && record.videoHeight == height) return
        record.videoWidth = width
        record.videoHeight = height
        updateTabsFlow()
        if (_currentTabId.value == tabId) onPipStateChanged?.invoke()
    }

    fun isVideoPlaying(): Boolean {
        val id = _currentTabId.value ?: return false
        return getTabRecord(id)?.isVideoPlaying == true
    }

    fun getActiveTabRecord(): TabRecord? {
        val id = _currentTabId.value ?: return null
        return getTabRecord(id)
    }

    @Suppress("DEPRECATION")
    fun clearAllBrowsingData() {
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

    fun updateTabTitle(tabId: Long, title: String) {
        val record = getTabRecord(tabId) ?: return
        record.title = title
        updateTabsFlow()
    }

    fun updateTabFavicon(tabId: Long, favicon: Bitmap?) {
        val record = getTabRecord(tabId) ?: return
        record.favicon = favicon
        updateTabsFlow()
    }

    fun updateTabProgress(tabId: Long, isLoading: Boolean, progress: Float) {
        val record = getTabRecord(tabId) ?: return
        record.isLoading = isLoading
        record.progress = progress
        updateTabsFlow()
    }

    fun incrementBlocked(tabId: Long) {
        val record = getTabRecord(tabId) ?: return
        record.blockedCount.incrementAndGet()
        updateTabsFlow(immediate = false)
    }

    fun allowUrlOnce(tabId: Long, url: String) {
        val record = getTabRecord(tabId) ?: return
        record.allowedOnceUrls.add(url)
    }

    fun notifyTabChanged(tabId: Long) {
        updateTabsFlow()
    }

    fun recordHistory(tabId: Long, url: String, title: String) {
        val record = getTabRecord(tabId) ?: return
        if (!record.isPrivate && currentDataPrefs.keepHistory && url.isNotBlank() && url != "about:blank") {
            appScope.launch {
                userStore.addHistory(url, record.entryId, title.ifEmpty { url })
            }
        }
    }

    fun updateSitePrefs(tabId: Long, host: String, update: (SitePrefs) -> SitePrefs) {
        appScope.launch {
            val existing = userStore.sitePrefs(host) ?: SitePrefs(host = host)
            val updated = update(existing)
            userStore.setSitePrefs(updated)
            val record = getTabRecord(tabId)
            if (record != null) {
                record.sitePrefs = updated
                record.webView?.let { wv ->
                    postToMain {
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

    fun clearSitePrefs(tabId: Long, host: String) {
        appScope.launch {
            userStore.clearSitePrefs(host)
            val record = getTabRecord(tabId)
            if (record != null) {
                record.sitePrefs = null
                record.webView?.let { wv ->
                    postToMain {
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

    fun toggleDesktopSite(tabId: Long) {
        val record = getTabRecord(tabId) ?: return
        val newDesktop = !record.desktopSite
        record.desktopSite = newDesktop
        val host = try { Uri.parse(record.url).host } catch (_: Exception) { null }
        if (!host.isNullOrEmpty()) {
            updateSitePrefs(tabId, host) { it.copy(desktop = newDesktop) }
        } else {
            record.webView?.let { wv ->
                webViewFactory.applySettings(wv, record, currentBrowserPrefs, currentProtectionPrefs, currentAppearance, isAppDark)
                wv.reload()
            }
            updateTabsFlow()
        }
    }

    fun startElementPicker(tabId: Long) {
        val record = getTabRecord(tabId) ?: return
        val js = io.github.omeg4dev.fmhy.browser.adblock.SelectorPicker.buildPickerJs(bridgeName)
        record.webView?.evaluateJavascript(js, null)
    }
}
