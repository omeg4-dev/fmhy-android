package io.github.omeg4dev.fmhy.ui.onboarding

import android.content.Context
import io.github.omeg4dev.fmhy.data.AppIcon
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.BlockingPrefs
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.DataPrefs
import io.github.omeg4dev.fmhy.data.HomePrefs
import io.github.omeg4dev.fmhy.data.NotificationPrefs
import io.github.omeg4dev.fmhy.data.OpenLinksWith
import io.github.omeg4dev.fmhy.data.PrivacyPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.SectionOrder
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.adblock.FilterListCatalog
import io.github.omeg4dev.fmhy.data.sync.SyncResult
import io.github.omeg4dev.fmhy.data.user.UserStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempDbFile: File
    private lateinit var userStore: UserStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDbFile = File.createTempFile("onboarding_user_test", ".db")
        userStore = UserStore(tempDbFile, dispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        userStore.close()
        tempDbFile.delete()
    }

    private class FakeSettingsRepository(
        initialAppearance: Appearance = Appearance(),
        initialBrowser: BrowserPrefs = BrowserPrefs(),
        initialBlocking: BlockingPrefs = BlockingPrefs(),
        initialProtection: ProtectionPrefs = ProtectionPrefs(),
        initialPrivacy: PrivacyPrefs = PrivacyPrefs(),
        initialHome: HomePrefs = HomePrefs(),
        initialNotification: NotificationPrefs = NotificationPrefs(),
        initialOnboardingVersion: Int = 0,
    ) : SettingsRepository {
        override val lastVisitTs: Flow<Long> get() = error("Not needed")
        override suspend fun setLastVisitTs(ts: Long) {}
        override val recentSearches: Flow<List<String>> get() = error("Not needed")
        override suspend fun addRecentSearch(query: String) {}
        override suspend fun removeRecentSearch(query: String) {}
        override suspend fun clearRecentSearches() {}

        val appearanceState = MutableStateFlow(initialAppearance)
        override val appearance: Flow<Appearance> = appearanceState.asStateFlow()
        override suspend fun setThemeMode(mode: ThemeMode) { appearanceState.value = appearanceState.value.copy(themeMode = mode) }
        override suspend fun setColorSource(source: ColorSource) { appearanceState.value = appearanceState.value.copy(colorSource = source) }
        override suspend fun setPureBlack(enabled: Boolean) { appearanceState.value = appearanceState.value.copy(pureBlack = enabled) }
        override suspend fun setAccentSeed(argb: Int) { appearanceState.value = appearanceState.value.copy(accentSeed = argb) }
        override suspend fun setSectionLayout(layout: SectionLayout) { appearanceState.value = appearanceState.value.copy(sectionLayout = layout) }
        override suspend fun setTextScale(scale: Float) { appearanceState.value = appearanceState.value.copy(textScale = scale) }
        override suspend fun setSectionOrder(order: SectionOrder) { appearanceState.value = appearanceState.value.copy(sectionOrder = order) }
        override suspend fun setShowRandomPick(show: Boolean) { appearanceState.value = appearanceState.value.copy(showRandomPick = show) }
        override suspend fun setStarredOnly(starredOnly: Boolean) { appearanceState.value = appearanceState.value.copy(starredOnly = starredOnly) }
        override suspend fun setShowIndexes(showIndexes: Boolean) { appearanceState.value = appearanceState.value.copy(showIndexes = showIndexes) }
        override suspend fun setHapticFeedback(enabled: Boolean) { appearanceState.value = appearanceState.value.copy(hapticFeedback = enabled) }
        override suspend fun setAppIcon(icon: AppIcon) { appearanceState.value = appearanceState.value.copy(appIcon = icon) }

        val homeState = MutableStateFlow(initialHome)
        override val homePrefs: Flow<HomePrefs> = homeState.asStateFlow()
        override suspend fun updateHome(f: (HomePrefs) -> HomePrefs) { homeState.value = f(homeState.value) }

        val browserState = MutableStateFlow(initialBrowser)
        override val browserPrefs: Flow<BrowserPrefs> = browserState.asStateFlow()
        override suspend fun updateBrowser(f: (BrowserPrefs) -> BrowserPrefs) { browserState.value = f(browserState.value) }

        val blockingState = MutableStateFlow(initialBlocking)
        override val blockingPrefs: Flow<BlockingPrefs> = blockingState.asStateFlow()
        override suspend fun updateBlocking(f: (BlockingPrefs) -> BlockingPrefs) { blockingState.value = f(blockingState.value) }

        val protectionState = MutableStateFlow(initialProtection)
        override val protectionPrefs: Flow<ProtectionPrefs> = protectionState.asStateFlow()
        override suspend fun updateProtection(f: (ProtectionPrefs) -> ProtectionPrefs) { protectionState.value = f(protectionState.value) }

        val privacyState = MutableStateFlow(initialPrivacy)
        override val privacyPrefs: Flow<PrivacyPrefs> = privacyState.asStateFlow()
        override suspend fun updatePrivacy(f: (PrivacyPrefs) -> PrivacyPrefs) { privacyState.value = f(privacyState.value) }

        val notificationState = MutableStateFlow(initialNotification)
        override val notificationPrefs: Flow<NotificationPrefs> = notificationState.asStateFlow()
        override suspend fun updateNotifications(f: (NotificationPrefs) -> NotificationPrefs) { notificationState.value = f(notificationState.value) }

        private val dataState = MutableStateFlow(DataPrefs())
        override val dataPrefs: Flow<DataPrefs> = dataState.asStateFlow()
        override suspend fun updateData(f: (DataPrefs) -> DataPrefs) { dataState.value = f(dataState.value) }

        val onboardingState = MutableStateFlow(initialOnboardingVersion)
        override val onboardingVersion: Flow<Int> = onboardingState.asStateFlow()
        override suspend fun setOnboardingVersion(v: Int) { onboardingState.value = v }
    }

    private class FakeContainer(
        override val settingsRepository: SettingsRepository,
        override val userStore: UserStore,
    ) : AppContainer {
        override val context: Context get() = error("Not needed")
        override val docFetcher: io.github.omeg4dev.fmhy.data.doc.DocFetcher get() = error("Not needed")
        override val appScope: CoroutineScope get() = CoroutineScope(Dispatchers.Unconfined)
        override val okHttpClient: OkHttpClient get() = error("Not needed")
        override val datasetStore get() = error("Not needed")
        override val searchEngine get() = error("Not needed")
        override val bookmarks get() = error("Not needed")
        override val safeGuard get() = error("Not needed")
        override val crossLinks get() = error("Not needed")
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
        override val syncStatus: StateFlow<SyncStatus> = MutableStateFlow<SyncStatus>(SyncStatus.Idle).asStateFlow()
        override val snackbarEvents: SharedFlow<String> = MutableSharedFlow<String>().asSharedFlow()
        override val isSyncBannerConsumed: Boolean = false
        override val fmhySync get() = error("Not needed")
        override fun markSyncBannerConsumed() {}
        override fun showSnackbar(message: String) {}
        override fun initialize() {}
        override suspend fun sync(force: Boolean): SyncResult = SyncResult.UpToDate(0L)

        var filterDownloadStartedCount = 0
        override fun startFilterDownload() {
            filterDownloadStartedCount++
        }
    }

    @Test
    fun testVersionGating() = runTest {
        assertEquals(1, ONBOARDING_VERSION)
        assertTrue(OnboardingModel.shouldShowOnboarding(0))
        assertFalse(OnboardingModel.shouldShowOnboarding(1))
        assertFalse(OnboardingModel.shouldShowOnboarding(2))
    }

    @Test
    fun testRecommendedDefaultsForNewInstalls() = runTest {
        val repo = FakeSettingsRepository()
        val container = FakeContainer(repo, userStore)
        val model = OnboardingModel(repo, userStore, container)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertEquals(ColorSource.FMHY, state.colorSource)
        assertEquals(0xFF7C5CFF.toInt(), state.accentSeed)
        assertEquals(OpenLinksWith.IN_APP, state.openLinksWith)
        assertEquals(AdblockLevel.BALANCED, state.adblockLevel)
        assertTrue(state.blockPopupsAndRedirects)
        assertTrue(state.followedSections.isEmpty())
        assertFalse(state.notifyChanges)
        assertFalse(state.personalHome)
    }

    @Test
    fun testUpgraderPreselection() = runTest {
        val customAppearance = Appearance(
            themeMode = ThemeMode.DARK,
            colorSource = ColorSource.MATERIAL_YOU,
            accentSeed = 0xFF43A047.toInt(),
        )
        val customBrowser = BrowserPrefs(openLinksWith = OpenLinksWith.CUSTOM_TAB)
        val customBlocking = BlockingPrefs(
            adblock = true,
            enabledLists = FilterListCatalog.strictIds,
        )
        val customProtection = ProtectionPrefs(
            popupBlocker = false,
            redirectGuard = false,
        )
        val customHome = HomePrefs(personalHome = true)
        val customNotification = NotificationPrefs(enabled = true)

        userStore.follow("video", "", "Video")
        userStore.follow("reading", "", "Reading")

        val repo = FakeSettingsRepository(
            initialAppearance = customAppearance,
            initialBrowser = customBrowser,
            initialBlocking = customBlocking,
            initialProtection = customProtection,
            initialHome = customHome,
            initialNotification = customNotification,
            initialOnboardingVersion = 0,
        )
        val container = FakeContainer(repo, userStore)
        val model = OnboardingModel(repo, userStore, container)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals(ColorSource.MATERIAL_YOU, state.colorSource)
        assertEquals(0xFF43A047.toInt(), state.accentSeed)
        assertEquals(OpenLinksWith.CUSTOM_TAB, state.openLinksWith)
        assertEquals(AdblockLevel.STRICT, state.adblockLevel)
        assertFalse(state.blockPopupsAndRedirects)
        assertTrue(state.personalHome)
        assertTrue(state.notifyChanges)
        assertEquals(setOf("video", "reading"), state.followedSections)
    }

    @Test
    fun testAdblockLevelMappingToAndFromPrefs() = runTest {
        // Off mapping
        val offPrefs = BlockingPrefs(adblock = false)
        assertEquals(AdblockLevel.OFF, AdblockLevel.fromBlockingPrefs(offPrefs))

        // Strict mapping
        val strictPrefs = BlockingPrefs(adblock = true, enabledLists = FilterListCatalog.strictIds)
        assertEquals(AdblockLevel.STRICT, AdblockLevel.fromBlockingPrefs(strictPrefs))

        // Balanced mapping
        val balancedPrefs = BlockingPrefs(adblock = true, enabledLists = FilterListCatalog.balancedIds)
        assertEquals(AdblockLevel.BALANCED, AdblockLevel.fromBlockingPrefs(balancedPrefs))

        // Test persistence of AdblockLevel through model.persistPage(OnboardingPage.BROWSING)
        val repo = FakeSettingsRepository()
        val container = FakeContainer(repo, userStore)
        val model = OnboardingModel(repo, userStore, container)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Balanced
        model.setOpenLinksWith(OpenLinksWith.IN_APP)
        model.setAdblockLevel(AdblockLevel.BALANCED)
        model.persistPage(OnboardingPage.BROWSING)
        assertTrue(repo.blockingPrefs.first().adblock)
        assertEquals(FilterListCatalog.balancedIds, repo.blockingPrefs.first().enabledLists)

        // 2. Strict
        model.setAdblockLevel(AdblockLevel.STRICT)
        model.persistPage(OnboardingPage.BROWSING)
        assertTrue(repo.blockingPrefs.first().adblock)
        assertEquals(FilterListCatalog.strictIds, repo.blockingPrefs.first().enabledLists)

        // 3. Off
        model.setAdblockLevel(AdblockLevel.OFF)
        model.persistPage(OnboardingPage.BROWSING)
        assertFalse(repo.blockingPrefs.first().adblock)

        // 4. Popups and redirects
        model.setBlockPopupsAndRedirects(false)
        model.persistPage(OnboardingPage.BROWSING)
        assertFalse(repo.protectionPrefs.first().popupBlocker)
        assertFalse(repo.protectionPrefs.first().redirectGuard)

        model.setBlockPopupsAndRedirects(true)
        model.persistPage(OnboardingPage.BROWSING)
        assertTrue(repo.protectionPrefs.first().popupBlocker)
        assertTrue(repo.protectionPrefs.first().redirectGuard)
    }

    @Test
    fun testFourPageFlowAndVisiblePages() = runTest {
        val repo = FakeSettingsRepository()
        val container = FakeContainer(repo, userStore)
        val model = OnboardingModel(repo, userStore, container)
        testDispatcher.scheduler.advanceUntilIdle()

        // 4-page flow: HELLO, BROWSING, FOLLOW, DONE
        val pages = model.visiblePages
        assertEquals(4, pages.size)
        assertEquals(
            listOf(OnboardingPage.HELLO, OnboardingPage.BROWSING, OnboardingPage.FOLLOW, OnboardingPage.DONE),
            pages,
        )

        assertEquals(OnboardingPage.BROWSING, model.nextPage(OnboardingPage.HELLO))
        assertEquals(OnboardingPage.FOLLOW, model.nextPage(OnboardingPage.BROWSING))
        assertEquals(OnboardingPage.DONE, model.nextPage(OnboardingPage.FOLLOW))
        assertNull(model.nextPage(OnboardingPage.DONE))

        assertEquals(OnboardingPage.FOLLOW, model.previousPage(OnboardingPage.DONE))
        assertEquals(OnboardingPage.BROWSING, model.previousPage(OnboardingPage.FOLLOW))
        assertEquals(OnboardingPage.HELLO, model.previousPage(OnboardingPage.BROWSING))
        assertNull(model.previousPage(OnboardingPage.HELLO))

        // Same 4 visible pages when openLinksWith is changed
        model.setOpenLinksWith(OpenLinksWith.CUSTOM_TAB)
        assertEquals(4, model.visiblePages.size)

        model.setOpenLinksWith(OpenLinksWith.EXTERNAL)
        assertEquals(4, model.visiblePages.size)
    }

    @Test
    fun testProtectionPrefsOnlyPersistedWhenInApp() = runTest {
        val repo = FakeSettingsRepository()
        val container = FakeContainer(repo, userStore)
        val model = OnboardingModel(repo, userStore, container)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initially blocking is default
        assertTrue(repo.blockingPrefs.first().adblock)

        // When external browser is chosen:
        model.setOpenLinksWith(OpenLinksWith.EXTERNAL)
        model.setAdblockLevel(AdblockLevel.OFF)
        model.setBlockPopupsAndRedirects(false)
        model.persistPage(OnboardingPage.BROWSING)

        // Browser pref is persisted
        assertEquals(OpenLinksWith.EXTERNAL, repo.browserPrefs.first().openLinksWith)
        // Protection/blocking prefs are NOT changed because not IN_APP
        assertTrue(repo.blockingPrefs.first().adblock)
        assertTrue(repo.protectionPrefs.first().popupBlocker)

        // When IN_APP is chosen:
        model.setOpenLinksWith(OpenLinksWith.IN_APP)
        model.persistPage(OnboardingPage.BROWSING)

        assertEquals(OpenLinksWith.IN_APP, repo.browserPrefs.first().openLinksWith)
        // Protection/blocking prefs ARE persisted now
        assertFalse(repo.blockingPrefs.first().adblock)
        assertFalse(repo.protectionPrefs.first().popupBlocker)
    }

    @Test
    fun testFilterDownloadTriggeredWhenLeavingPageTwo() = runTest {
        val repo = FakeSettingsRepository()
        val container = FakeContainer(repo, userStore)
        val model = OnboardingModel(repo, userStore, container)
        testDispatcher.scheduler.advanceUntilIdle()

        model.setOpenLinksWith(OpenLinksWith.IN_APP)
        model.setAdblockLevel(AdblockLevel.BALANCED)
        assertEquals(0, container.filterDownloadStartedCount)
        assertFalse(model.state.value.filterDownloadStarted)

        // Leaving page 2 persists BROWSING
        model.persistPage(OnboardingPage.BROWSING)

        assertEquals(1, container.filterDownloadStartedCount)
        assertTrue(model.state.value.filterDownloadStarted)

        // Persisting again does not trigger duplicate download
        model.persistPage(OnboardingPage.BROWSING)
        assertEquals(1, container.filterDownloadStartedCount)
    }

    @Test
    fun testFilterDownloadNotTriggeredWhenAdblockOff() = runTest {
        val repo = FakeSettingsRepository()
        val container = FakeContainer(repo, userStore)
        val model = OnboardingModel(repo, userStore, container)
        testDispatcher.scheduler.advanceUntilIdle()

        model.setOpenLinksWith(OpenLinksWith.IN_APP)
        model.setAdblockLevel(AdblockLevel.OFF)

        model.persistPage(OnboardingPage.BROWSING)

        assertEquals(0, container.filterDownloadStartedCount)
        assertFalse(model.state.value.filterDownloadStarted)
    }

    @Test
    fun testSkipPersistsOnlyVisitedPages() = runTest {
        val initialAppearance = Appearance(themeMode = ThemeMode.LIGHT)
        val initialBrowser = BrowserPrefs(openLinksWith = OpenLinksWith.CUSTOM_TAB)
        val initialHome = HomePrefs(personalHome = false)
        val initialNotification = NotificationPrefs(enabled = false)

        val repo = FakeSettingsRepository(
            initialAppearance = initialAppearance,
            initialBrowser = initialBrowser,
            initialHome = initialHome,
            initialNotification = initialNotification,
        )
        val container = FakeContainer(repo, userStore)
        val model = OnboardingModel(repo, userStore, container)
        testDispatcher.scheduler.advanceUntilIdle()

        // Modify settings across multiple pages
        model.setThemeMode(ThemeMode.DARK) // page 1 (HELLO)
        model.setOpenLinksWith(OpenLinksWith.EXTERNAL) // page 2 (BROWSING)
        model.setPersonalHome(true) // page 3 (FOLLOW)

        // User only visited HELLO (default visited)
        assertEquals(setOf(OnboardingPage.HELLO), model.visitedPages)

        var skipped = false
        model.skip { skipped = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(skipped)
        assertEquals(1, repo.onboardingState.value)
        // Visited page (HELLO) choice WAS persisted
        assertEquals(ThemeMode.DARK, repo.appearanceState.value.themeMode)
        // Unvisited pages (BROWSING, FOLLOW) choices were NOT persisted, retaining defaults
        assertEquals(OpenLinksWith.CUSTOM_TAB, repo.browserState.value.openLinksWith)
        assertFalse(repo.homeState.value.personalHome)
        assertFalse(repo.notificationState.value.enabled)
    }

    @Test
    fun testFinishSavesAllChoicesAndSetsVersion() = runTest {
        val repo = FakeSettingsRepository()
        val container = FakeContainer(repo, userStore)
        val model = OnboardingModel(repo, userStore, container)
        testDispatcher.scheduler.advanceUntilIdle()

        model.setThemeMode(ThemeMode.LIGHT)
        model.setAccentSeed(0xFF00897B.toInt())
        model.setOpenLinksWith(OpenLinksWith.IN_APP)
        model.setAdblockLevel(AdblockLevel.BALANCED)
        model.toggleSectionFollowed("gaming")
        model.setNotifyChanges(true)
        model.setPersonalHome(true)

        var finished = false
        model.finish { finished = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(finished)
        assertEquals(1, repo.onboardingState.value)
        assertEquals(1, container.filterDownloadStartedCount)
        assertEquals(ThemeMode.LIGHT, repo.appearanceState.value.themeMode)
        assertEquals(0xFF00897B.toInt(), repo.appearanceState.value.accentSeed)
        assertEquals(OpenLinksWith.IN_APP, repo.browserState.value.openLinksWith)
        assertTrue(repo.notificationState.value.enabled)
        assertTrue(repo.homeState.value.personalHome)
        assertEquals(listOf("gaming"), userStore.follows().map { it.sectionId })
    }
}
