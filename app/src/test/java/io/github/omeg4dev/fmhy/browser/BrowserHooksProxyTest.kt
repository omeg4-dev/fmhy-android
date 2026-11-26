package io.github.omeg4dev.fmhy.browser

import android.content.ContextWrapper
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.AdblockUpdater
import io.github.omeg4dev.fmhy.browser.adblock.ScriptletLibrary
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.BlockingPrefs
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.DataPrefs
import io.github.omeg4dev.fmhy.data.HomePrefs
import io.github.omeg4dev.fmhy.data.NotificationPrefs
import io.github.omeg4dev.fmhy.data.PrivacyPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.ProxyMode
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.adblock.FilterEngine
import io.github.omeg4dev.fmhy.data.adblock.FilterListProgress
import io.github.omeg4dev.fmhy.data.adblock.ListState
import io.github.omeg4dev.fmhy.data.user.UserStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BrowserHooksProxyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeSettingsRepo : SettingsRepository {
        override val lastVisitTs: Flow<Long> = MutableStateFlow(0L)
        override suspend fun setLastVisitTs(ts: Long) {}
        override val recentSearches: Flow<List<String>> = MutableStateFlow(emptyList())
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
        override val browserPrefs: Flow<BrowserPrefs> = MutableStateFlow(BrowserPrefs(deadSiteRescue = false))
        override suspend fun updateBrowser(f: (BrowserPrefs) -> BrowserPrefs) {}
        override val blockingPrefs: Flow<BlockingPrefs> = MutableStateFlow(BlockingPrefs())
        override suspend fun updateBlocking(f: (BlockingPrefs) -> BlockingPrefs) {}
        override val protectionPrefs: Flow<ProtectionPrefs> = MutableStateFlow(ProtectionPrefs(proxyMode = ProxyMode.ORBOT))
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

    @Test
    fun proxyErrorTriggersRescueEvenWhenDeadSiteRescueIsOff() {
        val repo = FakeSettingsRepo()
        val userStore = UserStore(File(tmp.root, "user.db"))
        val updater = object : AdblockUpdater {
            override suspend fun update(
                enabled: Set<String>,
                customRules: String,
                force: Boolean,
                onProgress: (FilterListProgress) -> Unit,
            ): FilterEngine = error("")
            override suspend fun rebuild(
                enabled: Set<String>,
                customRules: String,
                onProgress: (FilterListProgress) -> Unit,
            ): FilterEngine = error("")
            override fun loadEngine(): FilterEngine? = null
            override fun states(): Map<String, ListState> = emptyMap()
        }
        val adblockService = AdblockService(
            settingsRepository = repo,
            updater = updater,
            scriptletLibrary = ScriptletLibrary { null },
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
        )
        val hooks = DefaultBrowserHooks(datasetStore = null)
        val tm = TabManager(
            appContext = ContextWrapper(null),
            settingsRepository = repo,
            userStore = userStore,
            adblockService = adblockService,
            scriptletLibrary = ScriptletLibrary { null },
            browserHooks = hooks,
            appScope = CoroutineScope(Dispatchers.Unconfined),
        )
        tm.currentBrowserPrefs = BrowserPrefs(deadSiteRescue = false)
        tm.currentProtectionPrefs = ProtectionPrefs(proxyMode = ProxyMode.ORBOT)

        val record = TabRecord(id = 1L, url = "https://example.com", title = "Example")
        synchronized(tm.records) { tm.records.add(record) }

        hooks.onMainFrameError(
            tab = record.toState(),
            errorCode = RescueReason.ERROR_CONNECT,
            description = "net::ERR_SOCKS_CONNECTION_FAILED",
            failingUrl = "https://example.com",
        )
        val failed = record.failedLoad
        assertNotNull(failed)
        assertEquals(LoadFailureKind.PROXY, failed!!.kind)
        assertEquals("Cannot connect through Orbot (127.0.0.1:9050)", failed.reason)

        hooks.decideRescue(record, failed)
        val rescue = record.rescueState
        assertNotNull(rescue)
        assertEquals(LoadFailureKind.PROXY, rescue!!.kind)
        assertEquals("Cannot connect through Orbot (127.0.0.1:9050)", rescue.reason)
        assertFalse(rescue.canSkipBlocking)
        assertTrue(rescue.mirrors.isEmpty())
        assertTrue(rescue.alternatives.isEmpty())
    }
}
