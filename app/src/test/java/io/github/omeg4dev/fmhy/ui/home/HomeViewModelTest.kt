package io.github.omeg4dev.fmhy.ui.home

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
import io.github.omeg4dev.fmhy.data.PrivacyPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.SectionOrder
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.search.SearchEngine
import io.github.omeg4dev.fmhy.data.sync.FmhySync
import io.github.omeg4dev.fmhy.data.sync.SyncResult
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

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
        private val sectionsList: List<Section> = emptyList(),
        private val topPick: Entry? = null,
        private val versionVal: Long = 10L,
        private val pickSequence: List<Entry> = emptyList(),
    ) : DatasetStore(File("fake_nonexistent.db")) {
        override val isAvailable = true
        override val changes = MutableStateFlow(versionVal)
        override suspend fun version(): Long = versionVal
        var lastSectionOrderCalledWith: SectionOrder? = null
        override suspend fun sections(order: SectionOrder): List<Section> {
            lastSectionOrderCalledWith = order
            return sectionsList
        }
        var randomTopPickCallCount: Int = 0
        override suspend fun randomTopPick(): Entry? {
            randomTopPickCallCount++
            if (pickSequence.isNotEmpty()) return pickSequence[(randomTopPickCallCount - 1) % pickSequence.size]
            return topPick
        }
        override suspend fun entry(id: String): Entry? = (pickSequence + listOfNotNull(topPick)).firstOrNull { it.id == id }
        override suspend fun countAddedSince(ts: Long): Int = 5
        override suspend fun sectionItems(sectionId: String): List<SectionItem> = emptyList()
        override suspend fun meta(): Map<String, String> = mapOf(
            "version" to versionVal.toString(),
            "created" to "1700000000"
        )
    }

    private class FakeContainer(
        override val datasetStore: DatasetStore,
        override val settingsRepository: SettingsRepository,
        initialStatus: SyncStatus = SyncStatus.Idle,
        override val userStore: io.github.omeg4dev.fmhy.data.user.UserStore = io.github.omeg4dev.fmhy.data.user.UserStore(
            File.createTempFile("fake_user", ".db").apply { deleteOnExit() }
        ),
    ) : AppContainer {
        override val context: Context get() = error("Not needed")
        override val appScope: CoroutineScope get() = CoroutineScope(Dispatchers.Unconfined)
        override val okHttpClient: OkHttpClient get() = error("Not needed")
        override val fmhySync: FmhySync get() = error("Not needed")
        override val searchEngine: SearchEngine get() = SearchEngine(datasetStore)
        override val bookmarks: io.github.omeg4dev.fmhy.data.BookmarksRepository get() = error("Not needed")
        override val docFetcher: io.github.omeg4dev.fmhy.data.doc.DocFetcher get() = error("Not needed")
        override val safeGuard: io.github.omeg4dev.fmhy.data.safety.SafeGuard get() = io.github.omeg4dev.fmhy.data.safety.SafeGuard(datasetStore)
        override val crossLinks: StateFlow<io.github.omeg4dev.fmhy.data.link.CrossLinks?> = MutableStateFlow(null)
        private val _isInit = MutableStateFlow(true)
        override val isInitialized: StateFlow<Boolean> = _isInit.asStateFlow()
        private val _events = MutableSharedFlow<String>()
        override val snackbarEvents: SharedFlow<String> = _events.asSharedFlow()
        override fun showSnackbar(message: String) { _events.tryEmit(message) }
        override fun initialize() {}

        val mutableSyncStatus = MutableStateFlow<SyncStatus>(initialStatus)
        override val syncStatus: StateFlow<SyncStatus> = mutableSyncStatus.asStateFlow()

        override var isSyncBannerConsumed: Boolean = false
        override fun markSyncBannerConsumed() {
            isSyncBannerConsumed = true
        }

        var forceSyncCalledWith: Boolean? = null
        override suspend fun sync(force: Boolean): SyncResult {
            forceSyncCalledWith = force
            return SyncResult.UpToDate(10)
        }
    }

    private class FakeSettingsRepository(
        initialAppearance: Appearance = Appearance(),
    ) : SettingsRepository {
        private val _lastVisit = MutableStateFlow(1000L)
        private val _recents = MutableStateFlow<List<String>>(emptyList())

        override val lastVisitTs: Flow<Long> = _lastVisit.asStateFlow()
        override suspend fun setLastVisitTs(ts: Long) { _lastVisit.value = ts }

        override val recentSearches: Flow<List<String>> = _recents.asStateFlow()
        override suspend fun addRecentSearch(query: String) { _recents.value = listOf(query) + _recents.value }
        override suspend fun removeRecentSearch(query: String) { _recents.value = _recents.value - query }
        override suspend fun clearRecentSearches() { _recents.value = emptyList() }

        private val _appearance = MutableStateFlow(initialAppearance)
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
        override suspend fun setWebsiteStyle(enabled: Boolean) { _appearance.value = _appearance.value.copy(websiteStyle = enabled) }
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
    fun testHomeViewModelLoadsSectionsAndData() = runTest(testDispatcher) {
        val sampleSections = listOf(
            Section(id = "video", title = "Video", description = "Streaming", ord = 1, entryCount = 42),
            Section(id = "audio", title = "Audio", description = "Music", ord = 2, entryCount = 10),
        )
        val samplePick = Entry(
            num = 1,
            id = "e1",
            sectionId = "video",
            headingId = null,
            ord = 1,
            name = "Top Movie",
            url = "https://movie.org",
            originalUrl = null,
            description = "Top movies",
            tags = emptyList(),
            headingPath = "Video",
            tier = Tier.TOP,
            isInlineMore = false,
            isBase64 = false,
            mirrors = emptyList(),
            extraLinks = emptyList(),
            firstSeen = 1000,
            lastChanged = 1000,
            starredAt = 1000,
        )

        val store = FakeDatasetStore(sectionsList = sampleSections, topPick = samplePick, versionVal = 10L)
        val fakeSettings = FakeSettingsRepository()
        val fakeContainer = FakeContainer(store, fakeSettings)

        val viewModel = HomeViewModel(store, fakeSettings, fakeContainer)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testDispatcher.scheduler.runCurrent()

        val readyState = viewModel.uiState.filter { it.isReady }.first()
        assertTrue(readyState.isReady)
        assertEquals(10L, readyState.datasetVersion)
        assertEquals(2, readyState.sections.size)
        assertEquals("video", readyState.sections[0].id)
        assertNotNull(readyState.randomPick)
        assertEquals("e1", readyState.randomPick?.id)
        assertEquals(5, readyState.newSinceLastVisitCount)
        assertFalse(readyState.isEmptyState)
    }

    @Test
    fun testRandomPickStaysPutUntilRefresh() = runTest(testDispatcher) {
        fun e(id: String) = Entry(
            num = 1, id = id, sectionId = "video", headingId = null, ord = 1, name = id,
            url = "https://$id.org", originalUrl = null, description = "", tags = emptyList(),
            headingPath = "Video", tier = Tier.TOP, isInlineMore = false, isBase64 = false,
            mirrors = emptyList(), extraLinks = emptyList(), firstSeen = 1, lastChanged = 1, starredAt = 1,
        )
        val store = FakeDatasetStore(pickSequence = listOf(e("a"), e("b"), e("c")))
        val fakeSettings = FakeSettingsRepository()
        val viewModel = HomeViewModel(store, fakeSettings, FakeContainer(store, fakeSettings))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testDispatcher.scheduler.runCurrent()

        val first = viewModel.uiState.value.randomPick?.id
        assertEquals("a", first)
        repeat(5) { i ->
            fakeSettings.setTextScale(1f + (i + 1) / 10f)
            fakeSettings.updateHome { it.copy(personalHome = !it.personalHome) }
            store.changes.value = 11L + i
            testDispatcher.scheduler.runCurrent()
            assertEquals(first, viewModel.uiState.value.randomPick?.id)
        }
        assertEquals(1, store.randomTopPickCallCount)

        viewModel.refreshRandomPick()
        testDispatcher.scheduler.runCurrent()
        assertEquals(2, store.randomTopPickCallCount)
        assertEquals("b", viewModel.uiState.first { it.randomPick?.id != first }.randomPick?.id)
    }

    @Test
    fun testTriggerSyncCallsContainerSyncForce() = runTest(testDispatcher) {
        val store = FakeDatasetStore()
        val fakeSettings = FakeSettingsRepository()
        val fakeContainer = FakeContainer(store, fakeSettings)
        val viewModel = HomeViewModel(store, fakeSettings, fakeContainer)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testDispatcher.scheduler.runCurrent()

        viewModel.triggerSync()
        testDispatcher.scheduler.runCurrent()

        assertEquals(true, fakeContainer.forceSyncCalledWith)
    }

    @Test
    fun testShowRandomPickFalseAndAlphabeticalOrder() = runTest(testDispatcher) {
        val sampleSections = listOf(
            Section(id = "audio", title = "Audio", description = "Music", ord = 1, entryCount = 10),
            Section(id = "video", title = "Video", description = "Streaming", ord = 2, entryCount = 42),
        )
        val samplePick = Entry(
            num = 1,
            id = "e1",
            sectionId = "video",
            headingId = null,
            ord = 1,
            name = "Top Movie",
            url = "https://movie.org",
            originalUrl = null,
            description = "Top movies",
            tags = emptyList(),
            headingPath = "Video",
            tier = Tier.TOP,
            isInlineMore = false,
            isBase64 = false,
            mirrors = emptyList(),
            extraLinks = emptyList(),
            firstSeen = 1000,
            lastChanged = 1000,
            starredAt = 1000,
        )

        val store = FakeDatasetStore(sectionsList = sampleSections, topPick = samplePick, versionVal = 10L)
        val fakeSettings = FakeSettingsRepository(
            initialAppearance = Appearance(
                showRandomPick = false,
                sectionOrder = SectionOrder.ALPHABETICAL,
            )
        )
        val fakeContainer = FakeContainer(store, fakeSettings)

        val viewModel = HomeViewModel(store, fakeSettings, fakeContainer)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testDispatcher.scheduler.runCurrent()

        val readyState = viewModel.uiState.filter { it.isReady }.first()
        assertTrue(readyState.isReady)
        assertNull(readyState.randomPick)
        assertEquals(0, store.randomTopPickCallCount)
        assertEquals(SectionOrder.ALPHABETICAL, store.lastSectionOrderCalledWith)
    }

    @Test
    fun testGroupSectionsToolsContainsAudioAndEducationalToolsVirtualRows() {
        val sections = listOf(
            Section(id = "privacy", title = "Privacy", description = "", ord = 1, entryCount = 10),
            Section(id = "system-tools", title = "System Tools", description = "", ord = 2, entryCount = 10),
            Section(id = "image-tools", title = "Image Tools", description = "", ord = 3, entryCount = 10),
            Section(id = "video-tools", title = "Video Tools", description = "", ord = 4, entryCount = 10),
            Section(id = "developer-tools", title = "Developer Tools", description = "", ord = 5, entryCount = 10),
            Section(id = "storage", title = "Storage", description = "", ord = 6, entryCount = 10),
        )

        val groups = groupSections(sections, SectionOrder.WEBSITE)
        val toolsGroup = groups.firstOrNull { it.title == "Tools" }
        assertNotNull(toolsGroup)
        val toolIds = toolsGroup!!.sections.map { it.id }
        assertEquals(
            listOf("system-tools", "image-tools", "video-tools", "audio-tools", "educational-tools", "developer-tools"),
            toolIds,
        )

        val videoIdx = toolIds.indexOf("video-tools")
        val audioIdx = toolIds.indexOf("audio-tools")
        val eduIdx = toolIds.indexOf("educational-tools")
        val devIdx = toolIds.indexOf("developer-tools")
        assertEquals(videoIdx + 1, audioIdx)
        assertEquals(audioIdx + 1, eduIdx)
        assertEquals(eduIdx + 1, devIdx)

        val moreGroup = groups.firstOrNull { it.title == "More" }
        assertNotNull(moreGroup)
        val moreIds = moreGroup!!.sections.map { it.id }
        assertTrue(moreIds.contains("beginners-guide"))
        assertTrue(moreIds.contains("faq"))
        assertTrue(moreIds.contains("backups"))
        assertTrue(moreIds.contains("news-and-posts"))

        val alphaSections = sections.sortedBy { it.title }
        val alphaGroups = groupSections(alphaSections, SectionOrder.ALPHABETICAL)
        assertEquals(1, alphaGroups.size)
        assertNull(alphaGroups[0].title)
        val alphaTitles = alphaGroups[0].sections.map { it.title }
        assertEquals(alphaTitles.sortedWith(String.CASE_INSENSITIVE_ORDER), alphaTitles)
        assertTrue(alphaTitles.contains("Audio Tools"))
        assertTrue(alphaTitles.contains("Educational Tools"))
        assertTrue(alphaTitles.contains("Beginner's guide"))
        assertTrue(alphaTitles.contains("FAQ"))
        assertTrue(alphaTitles.contains("Backups & mirrors"))
        assertTrue(alphaTitles.contains("News & posts"))

        val counted = groupSections(sections, SectionOrder.WEBSITE, mapOf("audio-tools" to 41, "educational-tools" to 7))
            .first { it.title == "Tools" }.sections.associate { it.id to it.entryCount }
        assertEquals(41, counted["audio-tools"])
        assertEquals(7, counted["educational-tools"])
    }

}
