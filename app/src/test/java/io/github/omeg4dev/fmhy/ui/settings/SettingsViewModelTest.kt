package io.github.omeg4dev.fmhy.ui.settings

import android.content.Context
import io.github.omeg4dev.fmhy.data.AppIcon
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.BlockingPrefs
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.DataPrefs
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.HomePrefs
import io.github.omeg4dev.fmhy.data.NotificationPrefs
import io.github.omeg4dev.fmhy.data.OpenLinksWith
import io.github.omeg4dev.fmhy.data.PrivacyPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.SectionOrder
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.sync.FmhySync
import io.github.omeg4dev.fmhy.data.sync.SyncResult
import io.github.omeg4dev.fmhy.data.sync.SyncState
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.di.SyncStatus
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeDatasetStore : DatasetStore(File("fake_nonexistent.db")) {
        override val isAvailable = true
        override val changes = MutableStateFlow(1L)
        override suspend fun entryCount(): Int = 21665
    }

    private class FakeContainer(
        override val datasetStore: DatasetStore,
        override val settingsRepository: SettingsRepository,
        val syncResultToReturn: SyncResult = SyncResult.UpToDate(50),
        val syncStateToReturn: SyncState = SyncState(
            lastSuccess = System.currentTimeMillis() / 1000 - 120, // 2 mins ago
            source = "primary",
        ),
    ) : AppContainer {
        override val context: Context get() = error("Not needed")
        override val appScope: CoroutineScope get() = CoroutineScope(Dispatchers.Unconfined)
        override val okHttpClient: OkHttpClient get() = error("Not needed")
        override val userStore: io.github.omeg4dev.fmhy.data.user.UserStore get() = error("Not needed")
        override val searchEngine get() = error("Not needed")
        override val bookmarks: io.github.omeg4dev.fmhy.data.BookmarksRepository get() = error("Not needed")
        override val safeGuard: io.github.omeg4dev.fmhy.data.safety.SafeGuard get() = io.github.omeg4dev.fmhy.data.safety.SafeGuard(datasetStore)
        override val crossLinks: StateFlow<io.github.omeg4dev.fmhy.data.link.CrossLinks?> = MutableStateFlow(null)
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
        override val syncStatus: StateFlow<SyncStatus> = MutableStateFlow<SyncStatus>(SyncStatus.Idle).asStateFlow()
        override val snackbarEvents: SharedFlow<String> = MutableSharedFlow<String>().asSharedFlow()
        override val isSyncBannerConsumed: Boolean = false
        override fun markSyncBannerConsumed() {}
        override fun showSnackbar(message: String) {}
        override fun initialize() {}

        override val fmhySync: FmhySync = object : FmhySync(
            store = datasetStore,
            dir = File("/tmp"),
            client = OkHttpClient(),
            clock = { 1000L },
            log = {},
        ) {
            override fun loadState(): SyncState = syncStateToReturn
        }

        var forceSyncCalledWith: Boolean? = null
        override suspend fun sync(force: Boolean): SyncResult {
            forceSyncCalledWith = force
            return syncResultToReturn
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val lastVisitTs: Flow<Long> get() = error("Not needed")
        override suspend fun setLastVisitTs(ts: Long) {}
        override val recentSearches: Flow<List<String>> get() = error("Not needed")
        override suspend fun addRecentSearch(query: String) {}
        override suspend fun removeRecentSearch(query: String) {}
        override suspend fun clearRecentSearches() {}

        private val _appearance = MutableStateFlow(Appearance())
        override val appearance: Flow<Appearance> = _appearance.asStateFlow()
        override suspend fun setThemeMode(mode: ThemeMode) { _appearance.value = _appearance.value.copy(themeMode = mode) }
        override suspend fun setColorSource(source: ColorSource) { _appearance.value = _appearance.value.copy(colorSource = source) }
        override suspend fun setPureBlack(enabled: Boolean) { _appearance.value = _appearance.value.copy(pureBlack = enabled) }
        override suspend fun setAccentSeed(argb: Int) { _appearance.value = _appearance.value.copy(accentSeed = argb) }
        override suspend fun setSectionLayout(layout: SectionLayout) { _appearance.value = _appearance.value.copy(sectionLayout = layout) }
        override suspend fun setTextScale(scale: Float) { _appearance.value = _appearance.value.copy(textScale = scale) }
        override suspend fun setSectionOrder(order: SectionOrder) { _appearance.value = _appearance.value.copy(sectionOrder = order) }
        override suspend fun setShowRandomPick(show: Boolean) { _appearance.value = _appearance.value.copy(showRandomPick = show) }
        override suspend fun setStarredOnly(starredOnly: Boolean) { _appearance.value = _appearance.value.copy(starredOnly = starredOnly) }
        override suspend fun setShowIndexes(showIndexes: Boolean) { _appearance.value = _appearance.value.copy(showIndexes = showIndexes) }
        override suspend fun setHapticFeedback(enabled: Boolean) { _appearance.value = _appearance.value.copy(hapticFeedback = enabled) }
        override suspend fun setAppIcon(icon: AppIcon) { _appearance.value = _appearance.value.copy(appIcon = icon) }

        private val _home = MutableStateFlow(HomePrefs())
        override val homePrefs: Flow<HomePrefs> = _home.asStateFlow()
        override suspend fun updateHome(f: (HomePrefs) -> HomePrefs) { _home.value = f(_home.value) }

        private val _browser = MutableStateFlow(BrowserPrefs())
        override val browserPrefs: Flow<BrowserPrefs> = _browser.asStateFlow()
        override suspend fun updateBrowser(f: (BrowserPrefs) -> BrowserPrefs) { _browser.value = f(_browser.value) }

        private val _blocking = MutableStateFlow(BlockingPrefs())
        override val blockingPrefs: Flow<BlockingPrefs> = _blocking.asStateFlow()
        override suspend fun updateBlocking(f: (BlockingPrefs) -> BlockingPrefs) { _blocking.value = f(_blocking.value) }

        private val _protection = MutableStateFlow(ProtectionPrefs())
        override val protectionPrefs: Flow<ProtectionPrefs> = _protection.asStateFlow()
        override suspend fun updateProtection(f: (ProtectionPrefs) -> ProtectionPrefs) { _protection.value = f(_protection.value) }

        private val _privacy = MutableStateFlow(PrivacyPrefs())
        override val privacyPrefs: Flow<PrivacyPrefs> = _privacy.asStateFlow()
        override suspend fun updatePrivacy(f: (PrivacyPrefs) -> PrivacyPrefs) { _privacy.value = f(_privacy.value) }

        private val _notifications = MutableStateFlow(NotificationPrefs())
        override val notificationPrefs: Flow<NotificationPrefs> = _notifications.asStateFlow()
        override suspend fun updateNotifications(f: (NotificationPrefs) -> NotificationPrefs) { _notifications.value = f(_notifications.value) }

        private val _data = MutableStateFlow(DataPrefs())
        override val dataPrefs: Flow<DataPrefs> = _data.asStateFlow()
        override suspend fun updateData(f: (DataPrefs) -> DataPrefs) { _data.value = f(_data.value) }

        private val _onboarding = MutableStateFlow(0)
        override val onboardingVersion: Flow<Int> = _onboarding.asStateFlow()
        override suspend fun setOnboardingVersion(v: Int) { _onboarding.value = v }
    }

    @Test
    fun testSettingsUiStateLoadsEntryCountAndLastSynced() = runTest {
        val store = FakeDatasetStore()
        val settings = FakeSettingsRepository()
        val container = FakeContainer(store, settings)

        val viewModel = SettingsViewModel(store, settings, container)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.first { it.entryCount > 0 }
        assertEquals(21665, state.entryCount)
        assertTrue(state.lastSyncedText.contains("via GitHub"))
        assertTrue(state.lastSyncedText.contains("minutes ago") || state.lastSyncedText.contains("Just now"))
    }

    @Test
    fun testUpdateNowTriggersSyncForceAndDisplaysResult() = runTest {
        val store = FakeDatasetStore()
        val settings = FakeSettingsRepository()
        val container = FakeContainer(
            store,
            settings,
            syncResultToReturn = SyncResult.Updated(added = 5, removed = 2, changedSections = 1, tookMs = 500)
        )

        val viewModel = SettingsViewModel(store, settings, container)
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateNow()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, container.forceSyncCalledWith)
        val state = viewModel.uiState.value
        assertFalse(state.isUpdating)
        assertEquals("Updated: 5 added, 2 removed", state.updateStatusMessage)
    }

    @Test
    fun testBrowserPrefsUpdate() = runTest {
        val store = FakeDatasetStore()
        val settings = FakeSettingsRepository()
        val container = FakeContainer(store, settings)

        val viewModel = SettingsViewModel(store, settings, container)
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.browserPrefs.collect() }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(OpenLinksWith.IN_APP, viewModel.browserPrefs.value.openLinksWith)
        viewModel.updateBrowser { it.copy(openLinksWith = OpenLinksWith.EXTERNAL) }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(OpenLinksWith.EXTERNAL, viewModel.browserPrefs.value.openLinksWith)
    }

    @Test
    fun testSetThemeModeUpdatesUiState() = runTest {
        val store = FakeDatasetStore()
        val settings = FakeSettingsRepository()
        val container = FakeContainer(store, settings)

        val viewModel = SettingsViewModel(store, settings, container)
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.appearance.themeMode)
        viewModel.setThemeMode(ThemeMode.DARK)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.appearance.themeMode)
    }

    @Test
    fun testNewAppearanceSettingsUpdateUiState() = runTest {
        val store = FakeDatasetStore()
        val settings = FakeSettingsRepository()
        val container = FakeContainer(store, settings)

        val viewModel = SettingsViewModel(store, settings, container)
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1.0f, viewModel.uiState.value.appearance.textScale)
        viewModel.setTextScale(1.15f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1.15f, viewModel.uiState.value.appearance.textScale)

        assertEquals(SectionOrder.WEBSITE, viewModel.uiState.value.appearance.sectionOrder)
        viewModel.setSectionOrder(SectionOrder.ALPHABETICAL)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SectionOrder.ALPHABETICAL, viewModel.uiState.value.appearance.sectionOrder)

        assertTrue(viewModel.uiState.value.appearance.showRandomPick)
        viewModel.setShowRandomPick(false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.appearance.showRandomPick)

        assertFalse(viewModel.uiState.value.appearance.starredOnly)
        viewModel.setStarredOnly(true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.appearance.starredOnly)

        assertTrue(viewModel.uiState.value.appearance.showIndexes)
        viewModel.setShowIndexes(false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.appearance.showIndexes)

        assertTrue(viewModel.uiState.value.appearance.hapticFeedback)
        viewModel.setHapticFeedback(false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.appearance.hapticFeedback)
    }
}
