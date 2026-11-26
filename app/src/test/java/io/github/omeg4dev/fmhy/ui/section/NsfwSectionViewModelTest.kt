package io.github.omeg4dev.fmhy.ui.section

import android.content.Context
import io.github.omeg4dev.fmhy.BuildConfig
import io.github.omeg4dev.fmhy.data.AppIcon
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.BlockingPrefs
import io.github.omeg4dev.fmhy.data.BookmarksRepository
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.DataPrefs
import io.github.omeg4dev.fmhy.data.DataUsagePrefs
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.HomePrefs
import io.github.omeg4dev.fmhy.data.NotificationPrefs
import io.github.omeg4dev.fmhy.data.PrivacyPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.SectionOrder
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.doc.DocFetcher
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.data.nsfw.NSFW_SECTION_ID
import io.github.omeg4dev.fmhy.data.nsfw.NSFW_SECTION_TITLE
import io.github.omeg4dev.fmhy.data.nsfw.NsfwLoadResult
import io.github.omeg4dev.fmhy.data.nsfw.NsfwSectionBuilder
import io.github.omeg4dev.fmhy.data.nsfw.NsfwSource
import io.github.omeg4dev.fmhy.data.safety.SafeGuard
import io.github.omeg4dev.fmhy.data.search.SearchEngine
import io.github.omeg4dev.fmhy.data.sync.FmhySync
import io.github.omeg4dev.fmhy.data.sync.SyncResult
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.di.SyncStatus
import io.github.omeg4dev.fmhy.nav.BottomBarStyle
import io.github.omeg4dev.fmhy.ui.saved.SavedViewModel
import io.github.omeg4dev.fmhy.ui.settings.SettingsCatalog
import io.github.omeg4dev.fmhy.ui.settings.SettingsViewModel
import io.github.omeg4dev.fmhy.work.NetworkKind
import io.github.omeg4dev.fmhy.work.NetworkMonitor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class NsfwSectionViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeDatasetStore(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    ) : DatasetStore(File("fake_nonexistent.db"), dispatcher = dispatcher) {
        override val isAvailable = false
        override val changes = MutableStateFlow(0L)
        var sectionsCalled = false
        var sectionItemsCalled = false
        override suspend fun sections(order: SectionOrder): List<Section> {
            sectionsCalled = true
            return emptyList()
        }
        var entriesQueryIds: List<String>? = null
        override suspend fun sectionItems(sectionId: String): List<SectionItem> {
            sectionItemsCalled = true
            return emptyList()
        }
        override suspend fun entries(ids: List<String>): List<Entry> {
            entriesQueryIds = ids
            return emptyList()
        }
    }

    private class FakeNsfwSource(
        cacheDir: File,
        private val resultProvider: (forceRefresh: Boolean, allowNetwork: Boolean) -> NsfwLoadResult,
        private val cachedResult: NsfwLoadResult.Success? = null,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    ) : NsfwSource(OkHttpClient(), cacheDir, ioDispatcher = ioDispatcher) {
        var loadCalls = 0
        var lastForce = false
        var lastAllowNetwork = true

        override suspend fun load(forceRefresh: Boolean, allowNetwork: Boolean): NsfwLoadResult {
            loadCalls++
            lastForce = forceRefresh
            lastAllowNetwork = allowNetwork
            return resultProvider(forceRefresh, allowNetwork)
        }

        override suspend fun readCached(): NsfwLoadResult.Success? = cachedResult
    }

    private class FakeSettingsRepository(
        showNsfw: Boolean = true,
        wifiOnly: Boolean = false,
    ) : SettingsRepository {
        private val _lastVisit = MutableStateFlow(1000L)
        private val _recents = MutableStateFlow<List<String>>(emptyList())
        override val lastVisitTs: Flow<Long> = _lastVisit.asStateFlow()
        override suspend fun setLastVisitTs(ts: Long) { _lastVisit.value = ts }
        override val recentSearches: Flow<List<String>> = _recents.asStateFlow()
        override suspend fun addRecentSearch(query: String) {}
        override suspend fun removeRecentSearch(query: String) {}
        override suspend fun clearRecentSearches() {}

        private val _appearance = MutableStateFlow(Appearance())
        override val appearance: Flow<Appearance> = _appearance.asStateFlow()
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setColorSource(source: ColorSource) {}
        override suspend fun setPureBlack(enabled: Boolean) {}
        override suspend fun setAccentSeed(argb: Int) {}
        override suspend fun setSectionLayout(layout: SectionLayout) {}
        override suspend fun setTextScale(scale: Float) {}
        override suspend fun setSectionOrder(order: SectionOrder) {}
        override suspend fun setShowRandomPick(show: Boolean) {}
        override suspend fun setStarredOnly(starredOnly: Boolean) {
            _appearance.value = _appearance.value.copy(starredOnly = starredOnly)
        }
        override suspend fun setShowIndexes(showIndexes: Boolean) {
            _appearance.value = _appearance.value.copy(showIndexes = showIndexes)
        }
        override suspend fun setHapticFeedback(enabled: Boolean) {}
        override suspend fun setWebsiteStyle(enabled: Boolean) {}
        override suspend fun setAppIcon(icon: AppIcon) {}
        override suspend fun setBottomBarStyle(style: BottomBarStyle) {}

        private val _home = MutableStateFlow(HomePrefs(showNsfwLink = showNsfw))
        override val homePrefs: Flow<HomePrefs> = _home.asStateFlow()
        override suspend fun updateHome(f: (HomePrefs) -> HomePrefs) { _home.value = f(_home.value) }

        private val _browser = MutableStateFlow(BrowserPrefs())
        override val browserPrefs: Flow<BrowserPrefs> = _browser.asStateFlow()
        override suspend fun updateBrowser(f: (BrowserPrefs) -> BrowserPrefs) {}

        private val _blocking = MutableStateFlow(BlockingPrefs())
        override val blockingPrefs: Flow<BlockingPrefs> = _blocking.asStateFlow()
        override suspend fun updateBlocking(f: (BlockingPrefs) -> BlockingPrefs) {}

        private val _protection = MutableStateFlow(ProtectionPrefs())
        override val protectionPrefs: Flow<ProtectionPrefs> = _protection.asStateFlow()
        override suspend fun updateProtection(f: (ProtectionPrefs) -> ProtectionPrefs) {}

        private val _privacy = MutableStateFlow(PrivacyPrefs())
        override val privacyPrefs: Flow<PrivacyPrefs> = _privacy.asStateFlow()
        override suspend fun updatePrivacy(f: (PrivacyPrefs) -> PrivacyPrefs) {}

        private val _notifications = MutableStateFlow(NotificationPrefs())
        override val notificationPrefs: Flow<NotificationPrefs> = _notifications.asStateFlow()
        override suspend fun updateNotifications(f: (NotificationPrefs) -> NotificationPrefs) {}

        private val _data = MutableStateFlow(DataPrefs())
        override val dataPrefs: Flow<DataPrefs> = _data.asStateFlow()
        override suspend fun updateData(f: (DataPrefs) -> DataPrefs) {}

        private val _dataUsage = MutableStateFlow(DataUsagePrefs(appDataWifiOnly = wifiOnly))
        override val dataUsagePrefs: Flow<DataUsagePrefs> = _dataUsage.asStateFlow()
        override suspend fun updateDataUsage(f: (DataUsagePrefs) -> DataUsagePrefs) {
            _dataUsage.value = f(_dataUsage.value)
        }

        private val _onboarding = MutableStateFlow(0)
        override val onboardingVersion: Flow<Int> = _onboarding.asStateFlow()
        override suspend fun setOnboardingVersion(v: Int) {}
    }

    private fun sampleBuilt(): NsfwLoadResult.Success {
        val built = NsfwSectionBuilder.build(
            """
            ## Category A
            * ⭐ **[StarSite](https://star.example)** - Starred
            * [NormalSite](https://norm.example) - Normal
            """.trimIndent()
        )
        return NsfwLoadResult.Success(
            headings = built.headings,
            items = built.items,
            entries = built.entries,
            stale = false,
        )
    }

    @Test
    fun servesNsfwItemsAndDoesNotRecordSectionOpenOrTouchDb() = runTest(testDispatcher) {
        val userStore = UserStore(tempFolder.newFile("user.db"), dispatcher = testDispatcher)
        val store = FakeDatasetStore(testDispatcher)
        val settings = FakeSettingsRepository(showNsfw = true)
        val built = sampleBuilt()
        val source = FakeNsfwSource(tempFolder.newFolder("c"), { _, _ -> built }, ioDispatcher = testDispatcher)

        val vm = SectionViewModel(
            sectionId = NSFW_SECTION_ID,
            anchor = null,
            store = store,
            settingsRepository = settings,
            userStore = userStore,
            nsfwSource = source,
            networkMonitor = NetworkMonitor(initialKind = NetworkKind.WIFI),
            nsfwEnabledInBuild = true,
        )

        val state = vm.uiState.first { !it.isLoading }
        assertEquals(NsfwScreenStatus.NONE, state.nsfwStatus)
        assertEquals("NSFW", state.section?.title)
        assertEquals(3, state.items.size)
        assertEquals(1, state.headings.size)
        assertTrue(state.newBadges.isEmpty())
        assertEquals(1, source.loadCalls)
        assertFalse(store.sectionItemsCalled)

        // Filtering does not re-fetch
        vm.setStarredOnly(true)
        advanceUntilIdle()
        assertEquals(1, source.loadCalls)

        // Never recorded in topOpenedSections
        assertTrue(userStore.topOpenedSections(10).isEmpty())
        userStore.close()
    }

    @Test
    fun settingOffShowsDisabledStateAndFetchesNothing() = runTest(testDispatcher) {
        val store = FakeDatasetStore(testDispatcher)
        val settings = FakeSettingsRepository(showNsfw = false)
        val source = FakeNsfwSource(tempFolder.newFolder("c"), { _, _ -> sampleBuilt() })

        val vm = SectionViewModel(
            sectionId = NSFW_SECTION_ID,
            anchor = null,
            store = store,
            settingsRepository = settings,
            nsfwSource = source,
            networkMonitor = NetworkMonitor(initialKind = NetworkKind.WIFI),
            nsfwEnabledInBuild = true,
        )

        val state = vm.uiState.first { !it.isLoading }
        assertEquals(NsfwScreenStatus.DISABLED, state.nsfwStatus)
        assertTrue(state.items.isEmpty())
        assertEquals(0, source.loadCalls)
    }

    @Test
    fun fdroidFlavorNeverLoadsEvenWhenSettingIsOn() = runTest(testDispatcher) {
        val store = FakeDatasetStore(testDispatcher)
        val settings = FakeSettingsRepository(showNsfw = true)
        val source = FakeNsfwSource(tempFolder.newFolder("c"), { _, _ -> sampleBuilt() })

        // Explicit false
        val vmDisabled = SectionViewModel(
            sectionId = NSFW_SECTION_ID,
            anchor = null,
            store = store,
            settingsRepository = settings,
            nsfwSource = source,
            networkMonitor = NetworkMonitor(initialKind = NetworkKind.WIFI),
            nsfwEnabledInBuild = false,
        )
        val disabledState = vmDisabled.uiState.first { !it.isLoading }
        assertEquals(NsfwScreenStatus.DISABLED, disabledState.nsfwStatus)
        assertEquals(0, source.loadCalls)

        // Default BuildConfig flag respects flavor
        val vmDefault = SectionViewModel(
            sectionId = NSFW_SECTION_ID,
            anchor = null,
            store = store,
            settingsRepository = settings,
            nsfwSource = source,
            networkMonitor = NetworkMonitor(initialKind = NetworkKind.WIFI),
        )
        val defaultState = vmDefault.uiState.first { !it.isLoading }
        if (BuildConfig.FLAVOR == "fdroid") {
            assertEquals(NsfwScreenStatus.DISABLED, defaultState.nsfwStatus)
            assertEquals(0, source.loadCalls)
        } else {
            assertEquals(NsfwScreenStatus.NONE, defaultState.nsfwStatus)
            assertEquals(1, source.loadCalls)
        }
    }

    @Test
    fun respectsWifiPolicyAndSupportsMobileOverride() = runTest(testDispatcher) {
        val store = FakeDatasetStore(testDispatcher)
        val settings = FakeSettingsRepository(showNsfw = true, wifiOnly = true)
        val monitor = NetworkMonitor(initialKind = NetworkKind.MOBILE)
        val built = sampleBuilt()
        val source = FakeNsfwSource(tempFolder.newFolder("c"), { _, allowNet ->
            if (!allowNet) NsfwLoadResult.BlockedByWifi else built
        })

        val vm = SectionViewModel(
            sectionId = NSFW_SECTION_ID,
            anchor = null,
            store = store,
            settingsRepository = settings,
            nsfwSource = source,
            networkMonitor = monitor,
            nsfwEnabledInBuild = true,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        val waitingState = vm.uiState.first { !it.isLoading }
        assertEquals(NsfwScreenStatus.WAITING_FOR_WIFI, waitingState.nsfwStatus)

        vm.useMobileDataThisTime()
        advanceUntilIdle()
        assertEquals(NsfwScreenStatus.NONE, vm.uiState.value.nsfwStatus)
        assertEquals(3, vm.uiState.value.items.size)
        assertTrue(source.lastAllowNetwork)
    }

    @Test
    fun settingsCatalogNsfwDescriptionUpdatedInGithubBuild() {
        if (BuildConfig.FLAVOR == "github") {
            val entry = SettingsCatalog.byId("home.nsfw_link")!!
            assertEquals("Show NSFW link", entry.title)
            assertEquals("Add FMHY's NSFW section to Home (18+)", entry.description)
        }
    }

    @Test
    fun refreshFailureKeepsDisplayedItemsAndEmitsSnackbar() = runTest(testDispatcher) {
        val built = sampleBuilt()
        var callCount = 0
        val source = FakeNsfwSource(tempFolder.newFolder("c"), { force, _ ->
            callCount++
            if (force) NsfwLoadResult.Failed("network") else built
        })

        val vm = SectionViewModel(
            sectionId = NSFW_SECTION_ID,
            anchor = null,
            store = FakeDatasetStore(testDispatcher),
            settingsRepository = FakeSettingsRepository(showNsfw = true),
            nsfwSource = source,
            networkMonitor = NetworkMonitor(initialKind = NetworkKind.WIFI),
            nsfwEnabledInBuild = true,
        )

        val snackbarDeferred = async { vm.snackbarEvents.first() }
        vm.uiState.first { !it.isLoading }
        assertEquals(3, vm.uiState.value.items.size)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(2, callCount)
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(NsfwScreenStatus.NONE, vm.uiState.value.nsfwStatus)
        assertEquals(3, vm.uiState.value.items.size)
        assertEquals("Couldn't refresh the NSFW list", snackbarDeferred.await())
    }

    @Test
    fun savedViewModelResolvesNsfwBookmarkFromCacheOrFallbackWithoutDbLookup() = runTest(testDispatcher) {
        val userStore = UserStore(tempFolder.newFile("user_saved.db"), dispatcher = testDispatcher)
        val bookmarksRepo = BookmarksRepository(userStore)
        val built = sampleBuilt()
        val liveEntry = built.entries.first()
        bookmarksRepo.toggle(liveEntry, NSFW_SECTION_TITLE)

        val store = FakeDatasetStore(testDispatcher)
        val settings = FakeSettingsRepository(showNsfw = true)
        val cacheSource = FakeNsfwSource(
            cacheDir = tempFolder.newFolder("c1"),
            resultProvider = { _, _ -> built },
            cachedResult = built,
            ioDispatcher = testDispatcher,
        )

        val savedVmWithCache = SavedViewModel(
            store = store,
            bookmarksRepository = bookmarksRepo,
            userStore = userStore,
            settingsRepository = settings,
            nsfwSource = cacheSource,
            nsfwEnabledInBuild = true,
        )
        val cachedState = savedVmWithCache.uiState.first { !it.isLoading }
        assertEquals(null, store.entriesQueryIds)
        val cachedItem = cachedState.groups.single().items.single()
        assertEquals(liveEntry.id, cachedItem.liveEntry?.id)
        assertEquals(liveEntry.headingId, cachedItem.liveEntry?.headingId)

        // When setting is off / cache removed, fallback entry keeps Open & Show in section working
        settings.updateHome { it.copy(showNsfwLink = false) }
        advanceUntilIdle()
        val fallbackState = savedVmWithCache.uiState.first { state ->
            state.groups.firstOrNull()?.items?.firstOrNull()?.liveEntry?.headingId == null
        }
        val fallbackItem = fallbackState.groups.single().items.single()
        assertEquals(liveEntry.url, fallbackItem.liveEntry?.url)
        assertEquals(NSFW_SECTION_ID, fallbackItem.liveEntry?.sectionId)

        userStore.close()
    }

    @Test
    fun turningOffShowNsfwLinkClearsCacheFile() = runTest(testDispatcher) {
        val cacheDir = tempFolder.newFolder("nsfw_clear")
        val nsfwDir = File(cacheDir, "nsfw").apply { mkdirs() }
        val cacheFile = File(nsfwDir, "list.md").apply { writeText("## Streaming\n* [Site](https://site.example) - desc") }
        assertTrue(cacheFile.exists())

        val source = NsfwSource(OkHttpClient(), cacheDir, ioDispatcher = testDispatcher)
        val store = FakeDatasetStore(testDispatcher)
        val settings = FakeSettingsRepository(showNsfw = true)
        val userStore = UserStore(tempFolder.newFile("user_settings.db"), dispatcher = testDispatcher)

        val container = object : AppContainer {
            override val context: Context get() = error("unused")
            override val appScope: CoroutineScope get() = CoroutineScope(testDispatcher)
            override val okHttpClient: OkHttpClient get() = OkHttpClient()
            override val datasetStore: DatasetStore get() = store
            override val userStore: UserStore get() = userStore
            override val fmhySync: FmhySync get() = error("unused")
            override val searchEngine: SearchEngine get() = error("unused")
            override val settingsRepository: SettingsRepository get() = settings
            override val bookmarks: BookmarksRepository get() = error("unused")
            override val docFetcher: DocFetcher get() = error("unused")
            override val nsfwSource: NsfwSource get() = source
            override val crossLinks: StateFlow<CrossLinks?> = MutableStateFlow(null)
            override val safeGuard: SafeGuard get() = SafeGuard(store)
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val syncStatus: StateFlow<SyncStatus> = MutableStateFlow<SyncStatus>(SyncStatus.Idle).asStateFlow()
            override val snackbarEvents: SharedFlow<String> = MutableSharedFlow<String>().asSharedFlow()
            override val isSyncBannerConsumed: Boolean = false
            override fun markSyncBannerConsumed() {}
            override fun showSnackbar(message: String) {}
            override fun initialize() {}
            override suspend fun sync(force: Boolean): SyncResult = SyncResult.UpToDate(0)
        }

        val vm = SettingsViewModel(store, settings, container)
        vm.setShowNsfwLink(false)
        advanceUntilIdle()

        assertFalse(settings.homePrefs.first().showNsfwLink)
        assertFalse(cacheFile.exists())
        userStore.close()
    }
}
