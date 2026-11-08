package io.github.omeg4dev.fmhy.browser

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Message
import android.webkit.WebResourceRequest
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.ScriptletLibrary
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.BlockingPrefs
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.DataPrefs
import io.github.omeg4dev.fmhy.data.HomePrefs
import io.github.omeg4dev.fmhy.data.NotificationPrefs
import io.github.omeg4dev.fmhy.data.PrivacyPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.user.UserStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BrowserBridgeAndJobsRegressionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class TestSettingsRepo : SettingsRepository {
        override val lastVisitTs: Flow<Long> get() = error("Not needed")
        override suspend fun setLastVisitTs(ts: Long) {}
        override val recentSearches: Flow<List<String>> get() = error("Not needed")
        override suspend fun addRecentSearch(query: String) {}
        override suspend fun removeRecentSearch(query: String) {}
        override suspend fun clearRecentSearches() {}
        override val appearance: Flow<Appearance> = MutableStateFlow(Appearance())
        override suspend fun setThemeMode(mode: io.github.omeg4dev.fmhy.data.ThemeMode) {}
        override suspend fun setColorSource(source: io.github.omeg4dev.fmhy.data.ColorSource) {}
        override suspend fun setPureBlack(enabled: Boolean) {}
        override suspend fun setAccentSeed(argb: Int) {}
        override suspend fun setSectionLayout(layout: io.github.omeg4dev.fmhy.data.SectionLayout) {}
        override suspend fun setTextScale(scale: Float) {}
        override suspend fun setSectionOrder(order: io.github.omeg4dev.fmhy.data.SectionOrder) {}
        override suspend fun setShowRandomPick(show: Boolean) {}
        override suspend fun setStarredOnly(starredOnly: Boolean) {}
        override suspend fun setShowIndexes(showIndexes: Boolean) {}
        override suspend fun setHapticFeedback(enabled: Boolean) {}
        override suspend fun setWebsiteStyle(enabled: Boolean) {}
        override suspend fun setAppIcon(icon: io.github.omeg4dev.fmhy.data.AppIcon) {}
        override suspend fun setBottomBarStyle(style: io.github.omeg4dev.fmhy.nav.BottomBarStyle) {}
        override val homePrefs: Flow<HomePrefs> = MutableStateFlow(HomePrefs())
        override suspend fun updateHome(f: (HomePrefs) -> HomePrefs) {}
        override val browserPrefs: Flow<BrowserPrefs> = MutableStateFlow(BrowserPrefs())
        override suspend fun updateBrowser(f: (BrowserPrefs) -> BrowserPrefs) {}
        override val blockingPrefs: Flow<BlockingPrefs> = MutableStateFlow(BlockingPrefs())
        override suspend fun updateBlocking(f: (BlockingPrefs) -> BlockingPrefs) {}
        override val protectionPrefs: Flow<ProtectionPrefs> = MutableStateFlow(ProtectionPrefs())
        override suspend fun updateProtection(f: (ProtectionPrefs) -> ProtectionPrefs) {}
        override val privacyPrefs: Flow<PrivacyPrefs> = MutableStateFlow(PrivacyPrefs())
        override suspend fun updatePrivacy(f: (PrivacyPrefs) -> PrivacyPrefs) {}
        override val notificationPrefs: Flow<NotificationPrefs> = MutableStateFlow(NotificationPrefs())
        override suspend fun updateNotifications(f: (NotificationPrefs) -> NotificationPrefs) {}
        override val dataPrefs: Flow<DataPrefs> = MutableStateFlow(DataPrefs())
        override suspend fun updateData(f: (DataPrefs) -> DataPrefs) {}
        override val onboardingVersion: Flow<Int> = MutableStateFlow(0)
        override suspend fun setOnboardingVersion(v: Int) {}
    }

    private fun createTabManager(): TabManager {
        val context = ContextWrapper(null)
        val dbFile = File(tmp.root, "test_user.db")
        val userStore = UserStore(dbFile)
        val settingsRepo = TestSettingsRepo()
        val scriptletLib = ScriptletLibrary { null }
        val updater = object : io.github.omeg4dev.fmhy.browser.adblock.AdblockUpdater {
            override suspend fun update(
                enabled: Set<String>,
                customRules: String,
                force: Boolean,
                onProgress: (io.github.omeg4dev.fmhy.data.adblock.FilterListProgress) -> Unit
            ) = error("")
            override suspend fun rebuild(
                enabled: Set<String>,
                customRules: String,
                onProgress: (io.github.omeg4dev.fmhy.data.adblock.FilterListProgress) -> Unit
            ) = error("")
            override fun loadEngine() = null
            override fun states() = emptyMap<String, io.github.omeg4dev.fmhy.data.adblock.ListState>()
        }
        val adblockService = AdblockService(
            settingsRepository = settingsRepo,
            updater = updater,
            scriptletLibrary = scriptletLib,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
        )
        val hooks = object : BrowserHooks {
            override val siteStatusIcon: @androidx.compose.runtime.Composable (TabState) -> Unit = {}
            override fun handleCreateWindow(context: Context, tab: TabState, isUserGesture: Boolean, resultMsg: Message?, openNewTab: (url: String) -> Unit) = false
            override fun onDownloadStart(context: Context, record: TabRecord, url: String, userAgent: String, contentDisposition: String, mimeType: String, contentLength: Long) {}
            override fun shouldOverrideNavigation(context: Context, tab: TabState, request: WebResourceRequest, onAskOpenExternal: (Intent, String) -> Unit) = false
        }
        return TabManager(
            appContext = context,
            settingsRepository = settingsRepo,
            userStore = userStore,
            adblockService = adblockService,
            scriptletLibrary = scriptletLib,
            browserHooks = hooks,
            appScope = CoroutineScope(Dispatchers.Unconfined),
        )
    }

    @Test
    fun canOpenAnywayPureDecision() {
        // Tab not showing block page -> reject
        assertFalse(BrowserJsBridge.canOpenAnyway(showsBlockPage = false, blockedMainFrameUrl = "https://example.com/blocked", url = "https://example.com/blocked"))
        assertFalse(BrowserJsBridge.canOpenAnyway(showsBlockPage = false, blockedMainFrameUrl = null, url = "https://example.com/blocked"))

        // Null or empty blocked url -> reject
        assertFalse(BrowserJsBridge.canOpenAnyway(showsBlockPage = true, blockedMainFrameUrl = null, url = "https://example.com/blocked"))
        assertFalse(BrowserJsBridge.canOpenAnyway(showsBlockPage = true, blockedMainFrameUrl = "", url = "https://example.com/blocked"))

        // Empty target url -> reject
        assertFalse(BrowserJsBridge.canOpenAnyway(showsBlockPage = true, blockedMainFrameUrl = "https://example.com/blocked", url = ""))

        // Mismatched target url -> reject
        assertFalse(BrowserJsBridge.canOpenAnyway(showsBlockPage = true, blockedMainFrameUrl = "https://example.com/blocked", url = "https://evil.example"))
        assertFalse(BrowserJsBridge.canOpenAnyway(showsBlockPage = true, blockedMainFrameUrl = "https://example.com/blocked", url = "https://example.com/blocked/"))
        assertFalse(BrowserJsBridge.canOpenAnyway(showsBlockPage = true, blockedMainFrameUrl = "https://example.com/blocked", url = "https://example.com/blocked#hash"))

        // Exact match with block page showing -> allow
        assertTrue(BrowserJsBridge.canOpenAnyway(showsBlockPage = true, blockedMainFrameUrl = "https://example.com/blocked", url = "https://example.com/blocked"))
    }

    @Test
    fun openAnywayIgnoredWhenTabDoesNotShowBlockPage() = runTest {
        val tabManager = createTabManager()
        val tabId = tabManager.openTab(url = "https://fmhy.net", title = "FMHY", select = true)
        val record = tabManager.getTabRecord(tabId)!!
        record.showsBlockPage = false
        record.blockedMainFrameUrl = null

        val bridge = BrowserJsBridge(
            tabId = tabId,
            tabManager = tabManager,
            adblockService = tabManager.adblockService,
            scriptletLibrary = ScriptletLibrary { null },
            onPickerResult = { _, _ -> },
            onPickerCancel = { _ -> },
        )

        bridge.openAnyway("https://evil.example/blocked")

        assertFalse(record.allowedOnceUrls.contains("https://evil.example/blocked"))
        assertFalse(record.allowedHosts.contains("evil.example"))
    }

    @Test
    fun openAnywayIgnoredWhenUrlDoesNotMatchBlockedMainFrameUrl() = runTest {
        val tabManager = createTabManager()
        val tabId = tabManager.openTab(url = "https://fmhy.net", title = "FMHY", select = true)
        val record = tabManager.getTabRecord(tabId)!!
        record.showsBlockPage = true
        record.blockedMainFrameUrl = "https://blocked.example/real"

        val bridge = BrowserJsBridge(
            tabId = tabId,
            tabManager = tabManager,
            adblockService = tabManager.adblockService,
            scriptletLibrary = ScriptletLibrary { null },
            onPickerResult = { _, _ -> },
            onPickerCancel = { _ -> },
        )

        bridge.openAnyway("https://evil.example/other")

        assertFalse(record.allowedOnceUrls.contains("https://evil.example/other"))
        assertFalse(record.allowedHosts.contains("evil.example"))
    }

    @Test
    fun closeTabCancelsAndNullsRescueStallAndBlankJobs() = runTest {
        val tabManager = createTabManager()
        val tabId = tabManager.openTab(url = "https://example.com", select = true)
        val record = tabManager.getTabRecord(tabId)!!
        val rescueJob = tabManager.appScope.launch { awaitCancellation() }
        val stallJob = tabManager.appScope.launch { awaitCancellation() }
        val blankJob = tabManager.appScope.launch { awaitCancellation() }
        record.rescueJob = rescueJob
        record.stallJob = stallJob
        record.blankJob = blankJob

        tabManager.closeTab(tabId)

        assertTrue(rescueJob.isCancelled)
        assertTrue(stallJob.isCancelled)
        assertTrue(blankJob.isCancelled)
        assertNull(record.rescueJob)
        assertNull(record.stallJob)
        assertNull(record.blankJob)
    }

    @Test
    fun closeAllTabsCancelsAndNullsRescueStallAndBlankJobs() = runTest {
        val tabManager = createTabManager()
        val tab1 = tabManager.openTab(url = "https://example.com/1", select = true)
        val tab2 = tabManager.openTab(url = "https://example.com/2", select = false)
        val record1 = tabManager.getTabRecord(tab1)!!
        val record2 = tabManager.getTabRecord(tab2)!!

        val rescueJob1 = tabManager.appScope.launch { awaitCancellation() }
        val stallJob1 = tabManager.appScope.launch { awaitCancellation() }
        val blankJob1 = tabManager.appScope.launch { awaitCancellation() }
        record1.rescueJob = rescueJob1
        record1.stallJob = stallJob1
        record1.blankJob = blankJob1

        val rescueJob2 = tabManager.appScope.launch { awaitCancellation() }
        val stallJob2 = tabManager.appScope.launch { awaitCancellation() }
        val blankJob2 = tabManager.appScope.launch { awaitCancellation() }
        record2.rescueJob = rescueJob2
        record2.stallJob = stallJob2
        record2.blankJob = blankJob2

        tabManager.closeAllTabs()

        assertTrue(rescueJob1.isCancelled)
        assertTrue(stallJob1.isCancelled)
        assertTrue(blankJob1.isCancelled)
        assertNull(record1.rescueJob)
        assertNull(record1.stallJob)
        assertNull(record1.blankJob)

        assertTrue(rescueJob2.isCancelled)
        assertTrue(stallJob2.isCancelled)
        assertTrue(blankJob2.isCancelled)
        assertNull(record2.rescueJob)
        assertNull(record2.stallJob)
        assertNull(record2.blankJob)
    }
}
