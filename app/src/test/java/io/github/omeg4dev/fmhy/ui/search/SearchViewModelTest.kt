package io.github.omeg4dev.fmhy.ui.search

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
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.search.CategoryHit
import io.github.omeg4dev.fmhy.data.search.MatchMode
import io.github.omeg4dev.fmhy.data.search.Query
import io.github.omeg4dev.fmhy.data.search.SuggestionKind
import io.github.omeg4dev.fmhy.data.search.SearchEngine
import io.github.omeg4dev.fmhy.data.search.SearchGroup
import io.github.omeg4dev.fmhy.data.search.SearchHit
import io.github.omeg4dev.fmhy.data.search.SearchResults
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchViewModelTest {

    private class FakeDatasetStore : DatasetStore(File("fake_nonexistent.db")) {
        private val sectionsList = listOf(
            Section("video", "Video", "", 1, 10),
            Section("tools", "Tools", "", 2, 5),
        )
        override val isAvailable = true
        override val changes = MutableStateFlow(1L)
        override suspend fun version(): Long = 1L
        override suspend fun sections(order: SectionOrder): List<Section> = sectionsList
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val _lastVisit = MutableStateFlow(0L)
        private val _recents = MutableStateFlow<List<String>>(listOf("stremio", "torrent"))

        override val lastVisitTs: Flow<Long> = _lastVisit.asStateFlow()
        override suspend fun setLastVisitTs(ts: Long) { _lastVisit.value = ts }

        override val recentSearches: Flow<List<String>> = _recents.asStateFlow()
        override suspend fun addRecentSearch(query: String) { _recents.value = listOf(query) + (_recents.value - query) }
        override suspend fun removeRecentSearch(query: String) { _recents.value = _recents.value - query }
        override suspend fun clearRecentSearches() { _recents.value = emptyList() }

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

    private class FakeSearchEngine(
        store: DatasetStore,
        private val categoryProvider: ((String) -> List<CategoryHit>)? = null,
    ) : SearchEngine(store) {
        val sampleEntry = Entry(
            num = 1,
            id = "e1",
            sectionId = "video",
            headingId = null,
            ord = 1,
            name = "Stremio Player",
            url = "https://stremio.com",
            originalUrl = null,
            description = "Streaming",
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

        var lastQuery: String? = null
        var lastMode: MatchMode? = null
        var lastSectionFilter: Set<String>? = null
        val queries = MutableStateFlow<List<String>>(emptyList())

        override suspend fun search(query: String, sectionFilter: Set<String>, limit: Int, mode: MatchMode): SearchResults {
            lastQuery = query
            lastMode = mode
            lastSectionFilter = sectionFilter
            queries.value = queries.value + query
            val group = SearchGroup(
                section = Section("video", "Video", "", 1, 10),
                hits = listOf(SearchHit(sampleEntry, sampleEntry.name, sampleEntry.description))
            )
            val categories = categoryProvider?.invoke(query) ?: emptyList()
            return SearchResults(groups = listOf(group), total = 1, tookMs = 5L, categories = categories)
        }

        override suspend fun search(query: Query, sectionFilter: Set<String>, limit: Int, mode: MatchMode): SearchResults {
            return search(query.raw, sectionFilter, limit, mode)
        }

        override suspend fun suggestHosts(prefix: String, limit: Int): List<String> =
            listOf("github.com", "gitlab.com").filter { it.startsWith(prefix) }
    }

    @Test
    fun testSearchViewModelSearchesAndFilters() = runTest {
        val store = FakeDatasetStore()
        val fakeSettings = FakeSettingsRepository()
        val searchEngine = FakeSearchEngine(store)
        val viewModel = SearchViewModel(
            initialQuery = "stremio",
            store = store,
            searchEngine = searchEngine,
            settingsRepository = fakeSettings,
        )

        // Wait for search results
        val state = viewModel.uiState.filter { it.hasSearched && it.groups.isNotEmpty() }.first()
        assertEquals("stremio", state.query)
        assertEquals(1, state.total)
        assertEquals("e1", state.groups[0].hits[0].entry.id)

        // Test section filter toggle
        viewModel.toggleSectionFilter("tools")
        val filterState = viewModel.uiState.filter { it.selectedSectionFilters.contains("tools") }.first()
        assertTrue(filterState.selectedSectionFilters.contains("tools"))

        // Test query change
        viewModel.onQueryChange("converter")
        val converterState = viewModel.uiState.filter { it.query == "converter" }.first()
        assertEquals("converter", converterState.query)

        // Test recent searches
        viewModel.onSearchSubmitted("new search")
        val recents = fakeSettings.recentSearches.filter { it.contains("new search") }.first()
        assertEquals("new search", recents[0])
    }

    @Test
    fun testTogglingStarredOnlyPassesIsStarredToEngine() = runTest {
        val store = FakeDatasetStore()
        val fakeSettings = FakeSettingsRepository()
        val searchEngine = FakeSearchEngine(store)
        val viewModel = SearchViewModel(
            initialQuery = "stremio",
            store = store,
            searchEngine = searchEngine,
            settingsRepository = fakeSettings,
        )

        // Wait for initial search
        viewModel.uiState.filter { it.hasSearched }.first()
        assertEquals("stremio", searchEngine.lastQuery)

        // Toggle starredOnly
        viewModel.toggleStarredOnly()
        val starredState = viewModel.uiState.filter { it.starredOnly }.first()
        assertTrue(starredState.starredOnly)

        // Wait for searchEngine to receive query containing is:starred
        searchEngine.queries.filter { list -> list.any { it.contains("is:starred") } }.first()
        assertTrue(searchEngine.queries.value.last().contains("is:starred"))

        // The filter is a typed parameter now: it shows up in the query itself (pill first, text after)
        assertEquals("is:starred stremio", viewModel.uiState.value.query)
        assertEquals("is:starred stremio", viewModel.queryText)
        assertEquals(1, viewModel.uiState.value.filterCount)
    }

    @Test
    fun testUrlQueryTriggersSafeGuard() = runTest {
        val store = FakeDatasetStore()
        val fakeSettings = FakeSettingsRepository()
        val searchEngine = FakeSearchEngine(store)
        val viewModel = SearchViewModel(
            initialQuery = "",
            store = store,
            searchEngine = searchEngine,
            settingsRepository = fakeSettings,
        )

        viewModel.onQueryChange("google.com")
        val state = viewModel.uiState.filter { it.query == "google.com" && it.verdict != null }.first()
        org.junit.Assert.assertNotNull(state.verdict)
        assertEquals(io.github.omeg4dev.fmhy.data.safety.SafetyStatus.SEARCH_ENGINE, state.verdict?.status)
    }

    private fun newVm(initial: String = "", engine: FakeSearchEngine? = null, settings: FakeSettingsRepository = FakeSettingsRepository()): SearchViewModel {
        val store = FakeDatasetStore()
        return SearchViewModel(initial, store, engine ?: FakeSearchEngine(store), settings)
    }

    @Test
    fun filterTogglesEditTheQueryString() = runTest {
        val vm = newVm("anime")
        vm.toggleStarredOnly()
        assertEquals("is:starred anime", vm.queryText)
        vm.toggleB64Only()
        assertEquals("is:starred is:b64 anime", vm.queryText)
        vm.uiState.filter { it.starredOnly && it.b64Only }.first()
        vm.toggleStarredOnly()
        assertEquals("is:b64 anime", vm.queryText)
        vm.uiState.filter { !it.starredOnly && it.b64Only }.first()
        vm.clearAllFilters()
        assertEquals("anime", vm.queryText)
    }

    @Test
    fun sectionFiltersBecomeInPillsAndNeverReachTheEngineSeparately() = runTest {
        val store = FakeDatasetStore()
        val engine = FakeSearchEngine(store)
        val vm = SearchViewModel("anime", store, engine, FakeSettingsRepository())
        vm.uiState.filter { it.availableSections.isNotEmpty() }.first()
        vm.toggleSectionFilter("video")
        assertEquals("in:video anime", vm.queryText)
        val st = vm.uiState.filter { "video" in it.selectedSectionFilters }.first()
        assertEquals(1, st.filterCount)
        engine.queries.filter { list -> list.any { it == "in:video anime" } }.first()
        assertEquals(emptySet<String>(), engine.lastSectionFilter)
        vm.toggleSectionFilter("video")
        assertEquals("anime", vm.queryText)
    }

    @Test
    fun removingAPillEditsTheQuery() = runTest {
        val vm = newVm("in:video -foo is:top anime")
        assertEquals("in:video -foo is:top anime", vm.queryText)
        vm.removePill("-foo")
        assertEquals("in:video is:top anime", vm.queryText)
        vm.removePill("in:video")
        assertEquals("is:top anime", vm.queryText)
    }

    @Test
    fun recentSearchComesBackAsPills() = runTest {
        val vm = newVm()
        vm.selectRecentSearch("anime in:video is:starred")
        assertEquals("in:video is:starred anime", vm.queryText)
        assertEquals(2, vm.uiState.filter { it.filterCount == 2 }.first().filterCount)
    }

    @Test
    fun typingRoundTrip() {
        val vm = newVm()
        // what the screen does: field text -> split -> canonical raw
        val s = io.github.omeg4dev.fmhy.data.search.QueryEdit.split("anime in:video ")
        vm.onQueryChange(io.github.omeg4dev.fmhy.data.search.QueryEdit.compose(s.pills, s.free))
        assertEquals("in:video anime ", vm.queryText)
    }

    @Test
    fun halfTypedParametersAreNotSearchedAsWords() {
        assertEquals("anime", searchableQuery("anime is:st"))
        assertEquals("anime", searchableQuery("anime in:"))
        assertEquals("anime in:video", searchableQuery("anime in:video"))
        assertEquals("a b", searchableQuery("a  site:  b".replace("  site:  ", " ")))
    }

    @Test
    fun exactModeReachesTheEngineAndIsPersisted() = runTest {
        val store = FakeDatasetStore()
        val engine = FakeSearchEngine(store)
        val settings = FakeSettingsRepository()
        val vm = SearchViewModel("stremio", store, engine, settings)
        vm.uiState.filter { it.hasSearched }.first()
        assertEquals(MatchMode.FUZZY, engine.lastMode)
        vm.setExact(true)
        vm.uiState.filter { it.exact }.first()
        engine.queries.filter { it.size >= 2 || engine.lastMode == MatchMode.EXACT }.first()
        kotlinx.coroutines.delay(200)
        assertEquals(MatchMode.EXACT, engine.lastMode)
        assertTrue(settings.homePrefs.first().searchExact)
    }

    @Test
    fun suggestionsFollowTheCaret() = runTest {
        val vm = newVm()
        vm.uiState.filter { it.availableSections.isNotEmpty() }.first()
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { vm.suggestions.collect { } }
        vm.onCaretChange("anime in:vi", 11)
        val sec = vm.suggestions.filter { it.isNotEmpty() }.first()
        assertEquals(SuggestionKind.SECTION, sec[0].kind)
        assertEquals("in:video", sec[0].insert)
        vm.onCaretChange("site:git", 8)
        assertEquals(listOf("site:github.com", "site:gitlab.com"), vm.suggestions.filter { it.firstOrNull()?.kind == SuggestionKind.SITE }.first().map { it.insert })
        vm.onCaretChange("is:", 3)
        assertEquals(3, vm.suggestions.filter { it.firstOrNull()?.kind == SuggestionKind.IS }.first().size)
        vm.onCaretChange("anime ", 6)
        assertTrue(vm.suggestions.filter { it.isEmpty() }.first().isEmpty())
        job.cancel()
    }

    @Test
    fun rapidTypingEndsWithTheLastQuerysCategories() = runTest {
        val store = FakeDatasetStore()
        val vpnCategories = listOf(
            CategoryHit(
                section = Section("vpn", "VPN", "", 1, 10),
                heading = null,
                path = emptyList(),
            )
        )
        val engine = FakeSearchEngine(store) { q ->
            when (q) {
                "vpn" -> vpnCategories
                else -> listOf(
                    CategoryHit(
                        section = Section(q, "Cat $q", "", 1, 10),
                        heading = null,
                        path = emptyList(),
                    )
                )
            }
        }
        val vm = SearchViewModel("", store, engine, FakeSettingsRepository())
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect { }
        }

        vm.onQueryChange("v")
        vm.onQueryChange("vp")
        vm.onQueryChange("vpn")

        vm.uiState.filter { it.query == "vpn" && it.hasSearched && it.categories.isNotEmpty() }.first()
        testScheduler.advanceUntilIdle()

        assertEquals("vpn", vm.uiState.value.query)
        assertTrue(vm.uiState.value.hasSearched)
        assertTrue(vm.uiState.value.categories.isNotEmpty())
        assertEquals(vpnCategories, vm.uiState.value.categories)

        job.cancel()
    }
}
