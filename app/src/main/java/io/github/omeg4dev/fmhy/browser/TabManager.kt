package io.github.omeg4dev.fmhy.browser

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.ScriptletLibrary
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.DataPrefs
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
    var lastActiveTime: Long = System.currentTimeMillis(),
    val allowedOnceUrls: MutableSet<String> = mutableSetOf(),
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
    )
}

@OptIn(FlowPreview::class)
class TabManager(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    val userStore: UserStore,
    private val adblockService: AdblockService,
    private val scriptletLibrary: ScriptletLibrary,
    private val browserHooks: BrowserHooks,
    private val appScope: CoroutineScope,
) {
    val bridgeName = "_fmhy" + (1..8).map { ('a'..'z').random() }.joinToString("")
    private val mainHandler = Handler(Looper.getMainLooper())
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

    // Cache of prefs to configure WebViews synchronously
    @Volatile var currentBrowserPrefs = BrowserPrefs()
    @Volatile var currentProtectionPrefs = ProtectionPrefs()
    @Volatile var currentDataPrefs = DataPrefs()
    @Volatile var currentAppearance = Appearance()
    @Volatile var isAppDark = false

    init {
        appScope.launch {
            settingsRepository.browserPrefs.collect { currentBrowserPrefs = it }
        }
        appScope.launch {
            settingsRepository.protectionPrefs.collect { currentProtectionPrefs = it }
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
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
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
        val record = TabRecord(
            id = id,
            url = url,
            title = if (title.isNotEmpty()) title else if (url == "about:blank" || url.isEmpty()) "New tab" else url,
            isPrivate = isPrivate,
            entryId = entryId,
            desktopSite = currentBrowserPrefs.desktopByDefault,
        )

        synchronized(records) {
            records.add(record)
        }

        // Fetch site prefs for this host
        val host = try { Uri.parse(url).host } catch (_: Exception) { null }
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
        synchronized(records) {
            val record = records.firstOrNull { it.id == tabId } ?: return
            record.lastActiveTime = System.currentTimeMillis()
            _currentTabId.value = tabId
            ensureWebView(record)
        }
        updateTabsFlow()
    }

    fun closeTab(tabId: Long) {
        synchronized(records) {
            val index = records.indexOfFirst { it.id == tabId }
            if (index == -1) return

            val removed = records.removeAt(index)
            removed.webView?.destroy()
            removed.webView = null
            removed.mutableContext = null

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
        synchronized(records) {
            for (r in records) {
                r.webView?.destroy()
                r.webView = null
                r.mutableContext = null
            }
            records.clear()
            _currentTabId.value = null
        }
        updateTabsFlow()
    }

    private fun ensureWebView(record: TabRecord) {
        if (record.webView != null) return

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
        record.webView?.loadUrl(url)
        updateTabsFlow()
    }

    fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) {
        val record = getTabRecord(tabId) ?: return
        record.url = url
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
