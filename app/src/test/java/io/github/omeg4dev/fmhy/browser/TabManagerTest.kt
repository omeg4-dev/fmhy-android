package io.github.omeg4dev.fmhy.browser

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.os.Message
import android.webkit.WebResourceRequest
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.AdblockStatus
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
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TabManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class TestSettingsRepository : SettingsRepository {
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
        override suspend fun setAppIcon(icon: io.github.omeg4dev.fmhy.data.AppIcon) {}

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
        val settingsRepo = TestSettingsRepository()
        val scriptletLib = ScriptletLibrary { null }
        val updater = object : io.github.omeg4dev.fmhy.browser.adblock.AdblockUpdater {
            override suspend fun update(
                enabled: Set<String>,
                customRules: String,
                force: Boolean,
                onProgress: (io.github.omeg4dev.fmhy.data.adblock.FilterListProgress) -> Unit
            ): io.github.omeg4dev.fmhy.data.adblock.FilterEngine = error("")
            override suspend fun rebuild(
                enabled: Set<String>,
                customRules: String,
                onProgress: (io.github.omeg4dev.fmhy.data.adblock.FilterListProgress) -> Unit
            ): io.github.omeg4dev.fmhy.data.adblock.FilterEngine = error("")
            override fun loadEngine(): io.github.omeg4dev.fmhy.data.adblock.FilterEngine? = null
            override fun states(): Map<String, io.github.omeg4dev.fmhy.data.adblock.ListState> = emptyMap()
        }
        val adblockService = AdblockService(
            settingsRepository = settingsRepo,
            updater = updater,
            scriptletLibrary = scriptletLib,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
        )
        val hooks = object : BrowserHooks {
            override val siteStatusIcon: @androidx.compose.runtime.Composable (TabState) -> Unit = {}
            override fun handleCreateWindow(
                context: Context,
                tab: TabState,
                isUserGesture: Boolean,
                resultMsg: Message?,
                openNewTab: (url: String) -> Unit
            ): Boolean = false

            override fun onDownloadStart(
                context: Context,
                url: String,
                userAgent: String,
                contentDisposition: String,
                mimeType: String,
                contentLength: Long
            ) {}

            override fun shouldOverrideNavigation(
                context: Context,
                tab: TabState,
                request: WebResourceRequest,
                onAskOpenExternal: (Intent, String) -> Unit
            ): Boolean = false
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

    private fun createDummyBitmap(): Bitmap {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(Bitmap::class.java) as Bitmap
    }

    @Test
    fun testThumbnailReleaseOnClose() = runTest {
        val tabManager = createTabManager()
        val tabId = tabManager.openTab(url = "https://example.com/1", title = "Tab 1", select = false)

        val bitmap = createDummyBitmap()
        tabManager.setThumbnailForTest(tabId, bitmap)
        assertTrue(tabManager.hasThumbnail(tabId))

        tabManager.closeTab(tabId)
        assertFalse(tabManager.hasThumbnail(tabId))
    }

    @Test
    fun testUndoRestoreOrder() = runTest {
        val tabManager = createTabManager()
        val t0 = tabManager.openTab(url = "https://example.com/0", title = "Tab 0", select = false)
        val t1 = tabManager.openTab(url = "https://example.com/1", title = "Tab 1", select = false)
        val t2 = tabManager.openTab(url = "https://example.com/2", title = "Tab 2", select = false)

        assertEquals(3, tabManager.tabs.value.size)
        assertEquals("https://example.com/1", tabManager.tabs.value[1].url)

        // Close middle tab (index 1)
        tabManager.closeTab(t1)
        assertEquals(2, tabManager.tabs.value.size)
        assertEquals("https://example.com/0", tabManager.tabs.value[0].url)
        assertEquals("https://example.com/2", tabManager.tabs.value[1].url)

        // Undo restore at index 1
        val restoredId = tabManager.restoreTab(
            url = "https://example.com/1",
            title = "Tab 1",
            index = 1,
            isPrivate = false,
        )

        val restoredTabs = tabManager.tabs.value
        assertEquals(3, restoredTabs.size)
        assertEquals("https://example.com/0", restoredTabs[0].url)
        assertEquals("https://example.com/1", restoredTabs[1].url)
        assertEquals("https://example.com/2", restoredTabs[2].url)
        assertEquals(restoredId, restoredTabs[1].id)
    }

    @Test
    fun testReleaseAllThumbnailsOnCloseAll() = runTest {
        val tabManager = createTabManager()
        val t0 = tabManager.openTab(url = "https://example.com/0", title = "Tab 0", select = false)
        val t1 = tabManager.openTab(url = "https://example.com/1", title = "Tab 1", select = false)

        val b0 = createDummyBitmap()
        val b1 = createDummyBitmap()
        tabManager.setThumbnailForTest(t0, b0)
        tabManager.setThumbnailForTest(t1, b1)

        assertTrue(tabManager.hasThumbnail(t0))
        assertTrue(tabManager.hasThumbnail(t1))

        tabManager.closeAllTabs()

        assertFalse(tabManager.hasThumbnail(t0))
        assertFalse(tabManager.hasThumbnail(t1))
        assertTrue(tabManager.tabs.value.isEmpty())
    }
}
