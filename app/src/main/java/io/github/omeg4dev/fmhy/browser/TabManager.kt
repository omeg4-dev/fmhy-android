package io.github.omeg4dev.fmhy.browser

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.core.view.drawToBitmap
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.ScriptletLibrary
import io.github.omeg4dev.fmhy.data.Appearance
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
    var blocked: Int = 0,
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
    var hasStartedFirstLoad: Boolean = false,
    var entryHost: String? = null,
    var lastActiveTime: Long = System.currentTimeMillis(),
    val allowedOnceUrls: MutableSet<String> = mutableSetOf(),
    val allowedHosts: MutableSet<String> = mutableSetOf(),
    val dismissedSiteBarHosts: MutableSet<String> = mutableSetOf(),
    @Volatile var sourceUrl: String = url,
    var webView: WebView? = null,
    var mutableContext: MutableContextWrapper? = null,
) {
    fun toState(): TabState = TabState(
        id = id,
        url = url,
        title = title,
        favicon = favicon,
        isPrivate = isPrivate,
        entryId = entryId,
        blocked = blocked,
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
    )
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

    private val _currentTabId = MutableStateFlow<Long?>(null)
    val currentTabId: StateFlow<Long?> = _currentTabId.asStateFlow()

    private var attachedActivity: Activity? = null
    private var isRestored = false

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

    private val thumbnails = java.util.concurrent.ConcurrentHashMap<Long, Bitmap>()

    fun getThumbnail(tabId: Long): Bitmap? = thumbnails[tabId]

    fun hasThumbnail(tabId: Long): Boolean = thumbnails.containsKey(tabId)

    fun setThumbnailForTest(tabId: Long, bitmap: Bitmap) {
        thumbnails[tabId] = bitmap
    }

    fun releaseThumbnail(tabId: Long) {
        val old = thumbnails.remove(tabId)
        if (old != null) {
            try {
                if (!old.isRecycled) {
                    old.recycle()
                }
            } catch (_: Throwable) {}
        }
    }

    fun releaseAllThumbnails() {
        for ((_, bm) in thumbnails) {
            try {
                if (!bm.isRecycled) {
                    bm.recycle()
                }
            } catch (_: Throwable) {}
        }
        thumbnails.clear()
    }

    fun captureThumbnail(tabId: Long) {
        val record = getTabRecord(tabId) ?: return
        if (record.isPrivate) {
            releaseThumbnail(tabId)
            return
        }
        val webView = record.webView ?: return
        postToMain {
            try {
                if (webView.width > 0 && webView.height > 0) {
                    val original = webView.drawToBitmap()
                    val targetWidth = 320
                    val targetHeight = ((original.height.toFloat() / original.width.toFloat()) * targetWidth).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
                    if (scaled != original) {
                        original.recycle()
                    }
                    val old = thumbnails.put(tabId, scaled)
                    if (old != null && old != scaled && !old.isRecycled) {
                        old.recycle()
                    }
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
            settingsRepository.browserPrefs.collect { currentBrowserPrefs = it }
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

    fun detachActivity() {
        attachedActivity = null
        for (r in records) {
            r.mutableContext?.baseContext = appContext
        }
    }

    suspend fun restoreTabsIfNeeded(initialUrl: String?, entryId: String? = null) {
        if (isRestored) return
        isRestored = true

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

        if (initialUrl != null && initialUrl.isNotBlank()) {
            openTab(url = initialUrl, entryId = entryId, isPrivate = false, select = true)
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

        updateTabsFlow()

        if (select || _currentTabId.value == null) {
            selectTab(id)
        }

        return id
    }

    fun selectTab(tabId: Long) {
        val oldId = _currentTabId.value
        if (oldId != null && oldId != tabId) {
            captureThumbnail(oldId)
        }
        synchronized(records) {
            val record = records.firstOrNull { it.id == tabId } ?: return
            record.lastActiveTime = System.currentTimeMillis()
            _currentTabId.value = tabId
            ensureWebView(record)
        }
        updateTabsFlow()
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

        updateTabsFlow()
        selectTab(id)
        return id
    }

    fun closeTab(tabId: Long) {
        releaseThumbnail(tabId)
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
        updateTabsFlow()
    }

    fun closeAllTabs() {
        releaseAllThumbnails()
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
        updateTabsFlow()
    }

    private fun ensureWebView(record: TabRecord) {
        if (record.webView != null) return

        try {
            // Check max live WebViews limit
            val maxTabs = currentBrowserPrefs.maxTabs.coerceAtLeast(1)
            val liveCount = records.count { it.webView != null }
            if (liveCount >= maxTabs) {
                val lru = records
                    .filter { it.id != record.id && it.webView != null }
                    .minByOrNull { it.lastActiveTime }
                if (lru != null) {
                    lru.webView?.destroy()
                    lru.webView = null
                    lru.mutableContext = null
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

            if (record.url.isNotEmpty() && record.url != "about:blank") {
                webView.loadUrl(record.url)
            }
        } catch (_: Throwable) {
            // Graceful fallback when unmocked Android WebView is running in JVM unit tests
        }
    }

    fun reloadTab(tabId: Long) {
        val record = getTabRecord(tabId) ?: return
        record.webView?.reload()
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
        if (!host.isNullOrEmpty()) {
            appScope.launch {
                val sp = userStore.sitePrefs(host)
                record.sitePrefs = sp
                notifyTabChanged(tabId)
            }
        }
        updateTabsFlow()
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
        }
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
        record.blocked++
        updateTabsFlow()
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

    private fun updateTabsFlow() {
        val list = synchronized(records) {
            records.map { it.toState() }
        }
        _tabs.value = list
    }
}
