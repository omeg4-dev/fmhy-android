package io.github.omeg4dev.fmhy.ui.search

import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.SectionOrder
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.search.Query
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
        private val _openExternal = MutableStateFlow(false)
        private val _recents = MutableStateFlow<List<String>>(listOf("stremio", "torrent"))

        override val lastVisitTs: Flow<Long> = _lastVisit.asStateFlow()
        override suspend fun setLastVisitTs(ts: Long) { _lastVisit.value = ts }

        override val openInExternalBrowser: Flow<Boolean> = _openExternal.asStateFlow()
        override suspend fun setOpenInExternalBrowser(open: Boolean) { _openExternal.value = open }

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
    }

    private class FakeSearchEngine(store: DatasetStore) : SearchEngine(store) {
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
        val queries = MutableStateFlow<List<String>>(emptyList())

        override suspend fun search(query: String, sectionFilter: Set<String>, limit: Int): SearchResults {
            lastQuery = query
            queries.value = queries.value + query
            val group = SearchGroup(
                section = Section("video", "Video", "", 1, 10),
                hits = listOf(SearchHit(sampleEntry, sampleEntry.name, sampleEntry.description))
            )
            return SearchResults(groups = listOf(group), total = 1, tookMs = 5L)
        }

        override suspend fun search(query: Query, sectionFilter: Set<String>, limit: Int): SearchResults {
            return search(query.raw, sectionFilter, limit)
        }
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

        // Ensure visible query in state remains what user typed
        assertEquals("stremio", viewModel.uiState.value.query)
    }
}
