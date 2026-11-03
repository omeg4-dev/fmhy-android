package io.github.omeg4dev.fmhy.ui.section

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
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.nav.BottomBarStyle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SectionViewModelTest {

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
        private val sectionsList: List<Section> = emptyList(),
    ) : DatasetStore(File("fake_nonexistent.db")) {
        override val isAvailable = true
        override val changes = MutableStateFlow(1L)
        override suspend fun sections(order: SectionOrder): List<Section> = sectionsList
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
        override suspend fun setBottomBarStyle(style: BottomBarStyle) { _appearance.value = _appearance.value.copy(bottomBarStyle = style) }

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

    private fun sampleSection(id: String) = Section(
        id = id,
        title = "Section $id",
        description = "Description of $id",
        ord = 1,
        entryCount = 5,
    )

    @Test
    fun testRecordSectionOpenOnCreationForRealSection() = runTest(testDispatcher) {
        val userStore = UserStore(tempFolder.newFile("user.db"))
        val store = FakeDatasetStore(listOf(sampleSection("real_sec")))
        val settingsRepo = FakeSettingsRepository()

        SectionViewModel(
            sectionId = "real_sec",
            anchor = null,
            store = store,
            settingsRepository = settingsRepo,
            userStore = userStore,
        )

        advanceUntilIdle()

        assertEquals(listOf("real_sec"), userStore.topOpenedSections(5))
        userStore.close()
    }

    @Test
    fun testIgnoresVirtualOrUnknownSection() = runTest(testDispatcher) {
        val userStore = UserStore(tempFolder.newFile("user2.db"))
        val store = FakeDatasetStore(listOf(sampleSection("real_sec")))
        val settingsRepo = FakeSettingsRepository()

        SectionViewModel(
            sectionId = "virtual_or_missing",
            anchor = null,
            store = store,
            settingsRepository = settingsRepo,
            userStore = userStore,
        )

        advanceUntilIdle()

        assertTrue(userStore.topOpenedSections(5).isEmpty())
        userStore.close()
    }

    @Test
    fun testNullUserStoreDoesNotFail() = runTest(testDispatcher) {
        val store = FakeDatasetStore(listOf(sampleSection("real_sec")))
        val settingsRepo = FakeSettingsRepository()

        val vm = SectionViewModel(
            sectionId = "real_sec",
            anchor = null,
            store = store,
            settingsRepository = settingsRepo,
            userStore = null,
        )

        advanceUntilIdle()
        assertEquals("real_sec", vm.sectionId)
    }
}
