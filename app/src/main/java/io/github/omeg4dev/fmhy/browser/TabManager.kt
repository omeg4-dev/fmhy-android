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
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.SettingsPage
import io.github.omeg4dev.fmhy.SettingsPageNavKey
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.AllowRule
import io.github.omeg4dev.fmhy.browser.adblock.ScriptletLibrary
import io.github.omeg4dev.fmhy.browser.download.VideoSource
import io.github.omeg4dev.fmhy.browser.download.VideoSources
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.AutoplayMode
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.DataPrefs
import io.github.omeg4dev.fmhy.data.DataUsagePrefs
import io.github.omeg4dev.fmhy.data.PrivacyPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.user.SitePrefs
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.work.NetworkKind
import io.github.omeg4dev.fmhy.work.NetworkMonitor
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(FlowPreview::class)
class TabManager(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    val userStore: UserStore,
    val adblockService: AdblockService,
    private val scriptletLibrary: ScriptletLibrary,
    private val browserHooks: BrowserHooks,
    val appScope: CoroutineScope,
    networkMonitor: NetworkMonitor? = null,
) {
    fun isOnline(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val network = cm?.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        } catch (_: Throwable) {
            true
        }
    }
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

    private val _restored = MutableStateFlow(false)

    /** True once [restoreTabsIfNeeded] finished and queued links were opened; before that an empty tab list is normal. */
    val restored: StateFlow<Boolean> = _restored.asStateFlow()

    private val _showTabsRequested = MutableStateFlow(false)

    /** Set by an intent (Home card "All tabs"); the browser screen opens the tabs sheet and calls [consumeShowTabs]. */
    val showTabsRequested: StateFlow<Boolean> = _showTabsRequested.asStateFlow()

    fun requestShowTabs() { _showTabsRequested.value = true }
    fun consumeShowTabs() { _showTabsRequested.value = false }

    private val _showDownloadsRequested = MutableStateFlow(false)
    val showDownloadsRequested: StateFlow<Boolean> = _showDownloadsRequested.asStateFlow()

    fun requestShowDownloads() { _showDownloadsRequested.value = true }
    fun consumeShowDownloads() { _showDownloadsRequested.value = false }

    private val _currentTabId = MutableStateFlow<Long?>(null)
    val currentTabId: StateFlow<Long?> = _currentTabId.asStateFlow()

    private val _expandedTabGroups = MutableStateFlow<Set<String>>(emptySet())
    val expandedTabGroups: StateFlow<Set<String>> = _expandedTabGroups.asStateFlow()

    fun toggleTabGroup(key: String) {
        _expandedTabGroups.update { if (key in it) it - key else it + key }
    }

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
    var onLinkMenuRequestCallback: ((LinkMenuRequest) -> Unit)? = null

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
    @Volatile var currentDataUsagePrefs = DataUsagePrefs()
    @Volatile var currentNetworkKind = NetworkKind.NONE
    @Volatile var networkReadyAt: Long = 0L
    internal var clock: () -> Long = {
        try {
            android.os.SystemClock.uptimeMillis()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }
    @Volatile var currentAppearance = Appearance()
    @Volatile var isAppDark = false
    val mobileOkTabs: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    var onOpenDataUsageSettings: (() -> Unit)? = null

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
            settingsRepository.dataUsagePrefs.collect { currentDataUsagePrefs = it }
        }
        if (networkMonitor != null) {
            appScope.launch {
                var prevKind = networkMonitor.kind.value
                networkMonitor.kind.collect { kind ->
                    val hadKind = prevKind
                    prevKind = kind
                    currentNetworkKind = kind
                    if (hadKind == NetworkKind.NONE && kind != NetworkKind.NONE) {
                        onNetworkBecameReady()
                    }
                }
            }
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

    fun getTabRecord(tabId: Long): TabRecord? = synchronized(records) {
        records.firstOrNull { it.id == tabId }
    }

    fun findTabByUrl(url: String): TabRecord? = synchronized(records) {
        records.firstOrNull { it.url == url && !it.isPrivate }
    }

    fun inRestoreGrace(record: TabRecord, now: Long = clock()): Boolean {
        val start = maxOf(record.restoredAt, networkReadyAt)
        if (start <= 0L) return false
        val elapsed = (now - start).coerceAtLeast(0L)
        return elapsed < RescueDecision.GRACE_MS
    }

    fun graceElapsedMs(record: TabRecord, now: Long = clock()): Long {
        val start = maxOf(record.restoredAt, networkReadyAt)
        if (start <= 0L) return 0L
        return (now - start).coerceAtLeast(0L)
    }

    internal fun onNetworkBecameReady() {
        networkReadyAt = clock()
        wakePendingRetries()
    }

    internal fun wakePendingRetries() {
        val toReload = mutableListOf<TabRecord>()
        synchronized(records) {
            for (record in records) {
                if (record.isWaitingRetry && record.rescueJob?.isActive == true) {
                    record.rescueJob?.cancel()
                    record.rescueJob = null
                    val isGrace = record.isWaitingGraceRetry
                    record.isWaitingRetry = false
                    record.isWaitingGraceRetry = false
                    if (!isGrace) {
                        record.silentRetries++
                        record.silentRetryUrl = record.url
                    }
                    toReload.add(record)
                }
            }
        }
        for (rec in toReload) {
            postToMain {
                rec.webView?.reload()
            }
        }
    }

    fun openTab(
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
        // Close the previously active tab if it was empty (never showed a real site).
        // The newly selected tab is never closed, even if it is currently empty.
        if (oldId != null && oldId != tabId) {
            val old = getTabRecord(oldId)
            if (old != null && EmptyTabPolicy.isEmpty(old.url, old.hadSite)) {
                closeTab(oldId)
            }
        }
    }

    fun restoreTab(
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

    fun closeTab(tabId: Long) {
        mobileOkTabs.remove(tabId)
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

    fun closeAllTabs() {
        mobileOkTabs.clear()
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

    /**
     * Closes every live tab AND forgets tabs that exist only in the restore store (browser not started this
     * session), so the FMHY chip count drops to 0. Used by "Close all tabs".
     */
    fun closeAllTabsIncludingRestorable() {
        closeAllTabs()
        _browserTabCount.value = 0
        appScope.launch {
            try { userStore.put("browser_tabs", "[]") } catch (_: Exception) {}
        }
    }

    /** Current tab states straight from the records ([tabs] is throttled and may lag behind). */
    private fun liveStates(): List<TabState> = synchronized(records) { records.map { it.toState() } }

    /**
     * Closes every empty tab (including the currently active one). Called when the browser is
     * fully leaving the screen (back to FMHY, activity finishing). Returns the number of tabs
     * remaining after the closes.
     */
    fun closeEmptyTabsOnLeave(): Int {
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
    fun openNewTabPage(isPrivate: Boolean): Long {
        val actualPrivate = isPrivate || (currentPrivacyPrefs.privateTabsByDefault)
        val reusable = EmptyTabPolicy.reusable(liveStates(), actualPrivate, _currentTabId.value)
        if (reusable != null) {
            selectTab(reusable.id)
            return reusable.id
        }
        return openTab(url = "about:blank", title = "New tab", isPrivate = isPrivate, select = true)
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
                    if (BrowsePolicy.shouldBlock(record.url, currentDataUsagePrefs.browseWifiOnly, currentNetworkKind, record.id in mobileOkTabs)) {
                        loadWifiOnlyPage(record)
                    } else {
                        record.showsWifiOnlyPage = false
                        webView.loadUrl(record.url)
                    }
                }
            } else if (record.url.isNotEmpty() && record.url != "about:blank") {
                if (BrowsePolicy.shouldBlock(record.url, currentDataUsagePrefs.browseWifiOnly, currentNetworkKind, record.id in mobileOkTabs)) {
                    loadWifiOnlyPage(record)
                } else {
                    record.showsWifiOnlyPage = false
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

    fun allowMobileOnce(tabId: Long) {
        mobileOkTabs.add(tabId)
    }

    fun openDataUsageSettings() {
        onOpenDataUsageSettings?.invoke() ?: run {
            val container = (appContext as? FmhyApp)?.container
            container?.pushNavigation(SettingsPageNavKey(SettingsPage.DATA_USAGE))
            bringFmhyToFront(appContext)
        }
    }

    fun openFileCheckSettings() {
        val container = (appContext as? FmhyApp)?.container
        container?.pushNavigation(SettingsPageNavKey(SettingsPage.PROTECTION))
        bringFmhyToFront(appContext)
    }

    fun buildWifiOnlyHtml(): String {
        val accent = String.format("#%06X", 0xFFFFFF and currentAppearance.accentSeed)
        val surface = if (isAppDark) {
            if (currentAppearance.pureBlack) "#121212" else "#1e1e1e"
        } else {
            "#ffffff"
        }
        val onSurface = if (isAppDark) "#e0e0e0" else "#1f1f1f"
        return WifiOnlyPage.html(
            isDark = isAppDark,
            isBlack = isAppDark && currentAppearance.pureBlack,
            accentHex = accent,
            surfaceHex = surface,
            onSurfaceHex = onSurface,
            bridgeName = bridgeName,
        )
    }

    fun loadWifiOnlyPage(record: TabRecord) {
        record.showsWifiOnlyPage = true
        record.webView?.loadDataWithBaseURL(null, buildWifiOnlyHtml(), "text/html", "utf-8", null)
        updateTabsFlow()
    }

    fun reloadTab(tabId: Long) {
        val record = getTabRecord(tabId) ?: return
        if (record.crashed || record.webView == null) {
            record.crashed = false
            record.savedState = null
            ensureWebView(record)
        } else {
            if (BrowsePolicy.shouldBlock(record.url, currentDataUsagePrefs.browseWifiOnly, currentNetworkKind, tabId in mobileOkTabs)) {
                loadWifiOnlyPage(record)
            } else {
                record.showsWifiOnlyPage = false
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

    fun stopTab(tabId: Long) {
        val record = getTabRecord(tabId) ?: return
        record.webView?.stopLoading()
    }

    fun canGoBack(tabId: Long): Boolean = getTabRecord(tabId)?.webView?.canGoBack() == true

    fun goBack(tabId: Long): Boolean {
        val record = getTabRecord(tabId) ?: return false
        if (record.webView?.canGoBack() == true) {
            record.isBackForwardNavigation = true
            record.webView?.goBack()
            return true
        }
        return false
    }

    fun goForward(tabId: Long): Boolean {
        val record = getTabRecord(tabId) ?: return false
        if (record.webView?.canGoForward() == true) {
            record.isBackForwardNavigation = true
            record.webView?.goForward()
            return true
        }
        return false
    }

    fun loadUrl(tabId: Long, url: String, entryId: String? = null) {
        val record = getTabRecord(tabId) ?: return
        record.isBackForwardNavigation = false
        record.zoomLevel = 1f
        record.zoomBaseScale = 0f
        if (entryId != null) {
            record.entryId = entryId
        }
        record.url = url
        record.sourceUrl = url
        if (!EmptyTabPolicy.isStartUrl(url)) record.hadSite = true
        ensureWebView(record)
        if (BrowsePolicy.shouldBlock(url, currentDataUsagePrefs.browseWifiOnly, currentNetworkKind, tabId in mobileOkTabs)) {
            loadWifiOnlyPage(record)
        } else {
            record.showsWifiOnlyPage = false
            if (currentPrivacyPrefs.sendGpc) {
                record.webView?.loadUrl(url, mapOf("Sec-GPC" to "1", "DNT" to "1"))
            } else {
                record.webView?.loadUrl(url)
            }
        }
        updateTabsFlow()
    }

    fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) {
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

    /** "Continue anyway" on the warning: lets the page through the unsafe check and the filter, for this tab only. */
    fun continueAnyway(tabId: Long) {
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

    fun setVideoSources(tabId: Long, frame: String, list: List<VideoSource>) {
        val record = getTabRecord(tabId) ?: return
        if (list.isEmpty()) record.videoFrames.remove(frame) else record.videoFrames[frame] = list
        val merged = VideoSources.merge(record.videoFrames[""].orEmpty(), record.videoFrames.filterKeys { it.isNotEmpty() }.values)
        if (record.videoSources == merged) return
        record.videoSources = merged
        updateTabsFlow()
    }

    /** Called when a page starts loading: forgets the last page's media and looks up its remembered speed. */
    fun onPlaybackPageStarted(tabId: Long, url: String) {
        val record = getTabRecord(tabId) ?: return
        record.hasMedia = false
        record.desiredRate = 0f
        record.videoWidth = 0
        record.videoHeight = 0
        record.videoSources = emptyList()
        record.videoFrames.clear()
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

    fun incrementBlocked(tabId: Long, blocked: BlockedRequest) {
        val record = getTabRecord(tabId) ?: return
        record.blockedCount.incrementAndGet()
        if (record.blockedRequests.size < 300) {
            record.blockedRequests.add(blocked)
        }
        updateTabsFlow(immediate = false)
    }

    fun incrementBlocked(tabId: Long) {
        val record = getTabRecord(tabId) ?: return
        record.blockedCount.incrementAndGet()
        updateTabsFlow(immediate = false)
    }

    fun resetBlocked(tabId: Long) {
        val record = getTabRecord(tabId) ?: return
        record.blockedCount.set(0)
        record.blockedRequests.clear()
        updateTabsFlow(immediate = false)
    }

    fun allowBlockedHost(tabId: Long, host: String) {
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

    fun unallowBlockedHost(tabId: Long, host: String) {
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
