package io.github.omeg4dev.fmhy.browser

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.webkit.WebView
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.ScriptletLibrary
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.AutoplayMode
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.DataPrefs
import io.github.omeg4dev.fmhy.data.DataUsagePrefs
import io.github.omeg4dev.fmhy.data.PrivacyPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.work.NetworkKind
import io.github.omeg4dev.fmhy.work.NetworkMonitor
import io.github.omeg4dev.fmhy.work.VpnMonitor
import io.github.omeg4dev.fmhy.work.VpnState
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
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
    internal val appContext: Context,
    internal val settingsRepository: SettingsRepository,
    val userStore: UserStore,
    val adblockService: AdblockService,
    private val scriptletLibrary: ScriptletLibrary,
    private val browserHooks: BrowserHooks,
    val appScope: CoroutineScope,
    networkMonitor: NetworkMonitor? = null,
    vpnMonitor: VpnMonitor? = null,
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
    internal val mainHandler by lazy {
        try {
            Handler(Looper.getMainLooper())
        } catch (_: Throwable) {
            null
        }
    }
    internal val nextId = AtomicLong(1L)

    internal val records = mutableListOf<TabRecord>()
    private val _tabs = MutableStateFlow<List<TabState>>(emptyList())
    val tabs: StateFlow<List<TabState>> = _tabs.asStateFlow()

    internal val _browserTabCount = MutableStateFlow(0)

    /**
     * Number of open tabs, including tabs that will be restored the next time the browser starts
     * (read once from the same store restore uses), so FMHY screens can show the switch chip after an app restart.
     */
    val browserTabCount: StateFlow<Int> = _browserTabCount.asStateFlow()

    internal val _restored = MutableStateFlow(false)

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

    internal val _currentTabId = MutableStateFlow<Long?>(null)
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
    internal var isActivityResumed = false

    internal var attachedActivity: Activity? = null
    val linkQueue = BrowserLinkQueue()

    internal val webViewFactory = BrowserWebViewFactory(
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

    internal val thumbnails = java.util.concurrent.ConcurrentHashMap<Long, Bitmap>()
    internal val lastThumbnailCapture = java.util.concurrent.ConcurrentHashMap<Long, Long>()

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
    @Volatile var currentVpnState = VpnState.ACTIVE
    val vpnOkTabs: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()
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
        if (vpnMonitor != null) {
            appScope.launch {
                var prev = vpnMonitor.state.value
                currentVpnState = prev
                vpnMonitor.state.collect { s ->
                    currentVpnState = s
                    if (s != prev) {
                        prev = s
                        vpnOkTabs.clear()
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
            rebaseWebViewContext(r)
        }
    }

    /**
     * WebView derives dark mode (algorithmic darkening and `prefers-color-scheme`) from the context theme's
     * `isLightTheme`; the app theme is Material.Light, so a tab that should be dark needs a dark theme overlay.
     */
    internal fun webViewBase(base: Context, record: TabRecord): Context =
        if (shouldDarken(currentBrowserPrefs, isAppDark, record)) ContextThemeWrapper(base, R.style.Theme_FMHY_WebDark) else base

    internal fun rebaseWebViewContext(record: TabRecord) {
        record.mutableContext?.baseContext = webViewBase(attachedActivity ?: appContext, record)
    }

    fun detachActivity(activity: Activity? = null) {
        if (activity != null && attachedActivity !== activity) {
            return
        }
        attachedActivity = null
        for (r in records) {
            rebaseWebViewContext(r)
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

    /** Current tab states straight from the records ([tabs] is throttled and may lag behind). */
    internal fun liveStates(): List<TabState> = synchronized(records) { records.map { it.toState() } }

}
