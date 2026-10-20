package io.github.omeg4dev.fmhy.browser.adblock

import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.BlockingPrefs
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.DataPrefs
import io.github.omeg4dev.fmhy.data.HomePrefs
import io.github.omeg4dev.fmhy.data.NotificationPrefs
import io.github.omeg4dev.fmhy.data.PrivacyPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.adblock.EngineStats
import io.github.omeg4dev.fmhy.data.adblock.FilterEngine
import io.github.omeg4dev.fmhy.data.adblock.FilterEngineBuilder
import io.github.omeg4dev.fmhy.data.adblock.ListState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdblockServiceTest {

    private class FakeAdblockUpdater(
        initialEngine: FilterEngine? = null
    ) : AdblockUpdater {
        var engine: FilterEngine? = initialEngine
        val rebuildCalls = mutableListOf<Pair<Set<String>, String>>()
        val updateCalls = mutableListOf<Triple<Set<String>, String, Boolean>>()

        override suspend fun update(enabled: Set<String>, customRules: String, force: Boolean): FilterEngine {
            updateCalls.add(Triple(enabled, customRules, force))
            val newEngine = FilterEngineBuilder().apply {
                if (customRules.isNotEmpty()) addCustomRules(customRules)
            }.build()
            engine = newEngine
            return newEngine
        }

        override suspend fun rebuild(enabled: Set<String>, customRules: String): FilterEngine {
            rebuildCalls.add(Pair(enabled, customRules))
            val newEngine = FilterEngineBuilder().apply {
                if (customRules.isNotEmpty()) addCustomRules(customRules)
            }.build()
            engine = newEngine
            return newEngine
        }

        override fun loadEngine(): FilterEngine? = engine

        override fun states(): Map<String, ListState> = emptyMap()
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val lastVisitTs: Flow<Long> get() = error("Not needed")
        override suspend fun setLastVisitTs(ts: Long) {}
        override val recentSearches: Flow<List<String>> get() = error("Not needed")
        override suspend fun addRecentSearch(query: String) {}
        override suspend fun removeRecentSearch(query: String) {}
        override suspend fun clearRecentSearches() {}

        private val _appearance = MutableStateFlow(Appearance())
        override val appearance: Flow<Appearance> = _appearance
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

        private val _home = MutableStateFlow(HomePrefs())
        override val homePrefs: Flow<HomePrefs> = _home
        override suspend fun updateHome(f: (HomePrefs) -> HomePrefs) { _home.value = f(_home.value) }

        private val _browser = MutableStateFlow(BrowserPrefs())
        override val browserPrefs: Flow<BrowserPrefs> = _browser
        override suspend fun updateBrowser(f: (BrowserPrefs) -> BrowserPrefs) { _browser.value = f(_browser.value) }

        val _blocking = MutableStateFlow(BlockingPrefs())
        override val blockingPrefs: Flow<BlockingPrefs> = _blocking
        override suspend fun updateBlocking(f: (BlockingPrefs) -> BlockingPrefs) { _blocking.value = f(_blocking.value) }

        private val _protection = MutableStateFlow(ProtectionPrefs())
        override val protectionPrefs: Flow<ProtectionPrefs> = _protection
        override suspend fun updateProtection(f: (ProtectionPrefs) -> ProtectionPrefs) { _protection.value = f(_protection.value) }

        private val _privacy = MutableStateFlow(PrivacyPrefs())
        override val privacyPrefs: Flow<PrivacyPrefs> = _privacy
        override suspend fun updatePrivacy(f: (PrivacyPrefs) -> PrivacyPrefs) { _privacy.value = f(_privacy.value) }

        private val _notifications = MutableStateFlow(NotificationPrefs())
        override val notificationPrefs: Flow<NotificationPrefs> = _notifications
        override suspend fun updateNotifications(f: (NotificationPrefs) -> NotificationPrefs) { _notifications.value = f(_notifications.value) }

        private val _data = MutableStateFlow(DataPrefs())
        override val dataPrefs: Flow<DataPrefs> = _data
        override suspend fun updateData(f: (DataPrefs) -> DataPrefs) { _data.value = f(_data.value) }

        private val _onboarding = MutableStateFlow(0)
        override val onboardingVersion: Flow<Int> = _onboarding
        override suspend fun setOnboardingVersion(v: Int) { _onboarding.value = v }
    }

    @Test
    fun testInitializationLoadsEngine() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        val initialEngine = FilterEngineBuilder().apply {
            addCustomRules("||ad.com^")
        }.build()

        val updater = FakeAdblockUpdater(initialEngine)
        val repo = FakeSettingsRepository().apply {
            _blocking.value = BlockingPrefs(autoUpdateLists = false)
        }
        val scriptletLib = ScriptletLibrary { null }

        val service = AdblockService(
            settingsRepository = repo,
            updater = updater,
            scriptletLibrary = scriptletLib,
            coroutineScope = scope,
            ioDispatcher = testDispatcher,
        )

        service.initialize()
        testScheduler.advanceUntilIdle()

        assertTrue(service.status.value.ready)
        assertEquals(initialEngine, service.currentEngine)
        assertTrue(service.status.value.ruleCount > 0)
    }

    @Test
    fun testRebuildDebounceOnPrefsChange() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        val updater = FakeAdblockUpdater(null)
        val repo = FakeSettingsRepository()
        val scriptletLib = ScriptletLibrary { null }

        val service = AdblockService(
            settingsRepository = repo,
            updater = updater,
            scriptletLibrary = scriptletLib,
            coroutineScope = scope,
            ioDispatcher = testDispatcher,
        )

        testScheduler.advanceUntilIdle()
        assertEquals(0, updater.rebuildCalls.size)

        // Change custom rules
        repo.updateBlocking { it.copy(customRules = "||tracker.com^") }
        testScheduler.advanceTimeBy(500)
        assertEquals("Should not rebuild before debounce expires", 0, updater.rebuildCalls.size)

        // Another rapid change
        repo.updateBlocking { it.copy(customRules = "||tracker.com^\n||ads.org^") }
        testScheduler.advanceTimeBy(500)
        assertEquals("Should still debounce", 0, updater.rebuildCalls.size)

        // Allow debounce (1000ms total after last change)
        testScheduler.advanceTimeBy(600)
        assertEquals("Should trigger rebuild after 1s debounce", 1, updater.rebuildCalls.size)
        assertEquals("||tracker.com^\n||ads.org^", updater.rebuildCalls[0].second)
    }

    @Test
    fun testAllowlistMatching() {
        val scriptletLib = ScriptletLibrary { null }
        val repo = FakeSettingsRepository()
        val updater = FakeAdblockUpdater(null)
        val service = AdblockService(repo, updater, scriptletLib, TestScope())

        val allowlist = setOf("example.com", "allowed.org")

        assertTrue(service.isHostAllowlisted("example.com", allowlist))
        assertTrue(service.isHostAllowlisted("sub.example.com", allowlist))
        assertTrue(service.isHostAllowlisted("a.b.allowed.org", allowlist))
        assertFalse(service.isHostAllowlisted("notexample.com", allowlist))
        assertFalse(service.isHostAllowlisted("example.org", allowlist))
    }
}
