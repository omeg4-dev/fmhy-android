package io.github.omeg4dev.fmhy.ui.search

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.search.SearchEngine
import io.github.omeg4dev.fmhy.data.search.SearchGroup
import io.github.omeg4dev.fmhy.data.search.SearchResults
import io.github.omeg4dev.fmhy.ui.common.NewBadgeLogic
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SearchUiState(
    val query: String = "",
    val starredOnly: Boolean = false,
    val b64Only: Boolean = false,
    val selectedSectionFilters: Set<String> = emptySet(),
    val availableSections: ImmutableList<Section> = persistentListOf(),
    val groups: ImmutableList<SearchGroup> = persistentListOf(),
    val total: Int = 0,
    val tookMs: Long = 0L,
    val recentSearches: ImmutableList<String> = persistentListOf(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val newEntryIds: Set<String> = emptySet(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    initialQuery: String = "",
    private val store: DatasetStore,
    private val searchEngine: SearchEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val query = MutableStateFlow(initialQuery)
    val starredOnly = MutableStateFlow(false)
    val b64Only = MutableStateFlow(false)
    val selectedSectionFilters = MutableStateFlow<Set<String>>(emptySet())
    private val allSections = MutableStateFlow<ImmutableList<Section>>(persistentListOf())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (store.isAvailable) allSections.value = store.sections().toImmutableList()
        }
    }

    private data class SearchParams(
        val query: String,
        val filters: Set<String>,
        val starredOnly: Boolean,
        val b64Only: Boolean,
    )

    private val searchParams = combine(query, selectedSectionFilters, starredOnly, b64Only) { q, filters, starred, b64 ->
        SearchParams(q, filters, starred, b64)
    }.debounce(40L)
     .distinctUntilChanged()

    private val searchResults = searchParams.mapLatest { params ->
        val effectiveQuery = buildString {
            append(params.query.trim())
            if (params.starredOnly) {
                if (isNotEmpty()) append(" ")
                append("is:starred")
            }
            if (params.b64Only) {
                if (isNotEmpty()) append(" ")
                append("is:b64")
            }
        }

        if ((effectiveQuery.isBlank() && params.filters.isEmpty()) || !store.isAvailable) {
            null
        } else {
            searchEngine.search(
                query = effectiveQuery,
                sectionFilter = params.filters,
                limit = 200
            )
        }
    }.flowOn(Dispatchers.IO)

    private data class FilterParams(
        val query: String,
        val starredOnly: Boolean,
        val b64Only: Boolean,
        val selectedSectionFilters: Set<String>,
        val homePrefs: io.github.omeg4dev.fmhy.data.HomePrefs,
    )

    private val filterParams = combine(
        query,
        starredOnly,
        b64Only,
        selectedSectionFilters,
        settingsRepository.homePrefs,
    ) { q, starred, b64, filters, homePrefs ->
        FilterParams(q, starred, b64, filters, homePrefs)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        filterParams,
        allSections,
        settingsRepository.recentSearches,
        searchResults,
    ) { params: FilterParams, sections: List<Section>, recents: List<String>, results: SearchResults? ->
        val groups: ImmutableList<SearchGroup> = (results?.groups ?: emptyList()).toImmutableList()
        val allVisibleIds = groups.flatMap { g -> g.hits.map { it.entry.id } }
        val newEntryIds = if (params.homePrefs.newBadgeDays > 0 && allVisibleIds.isNotEmpty()) {
            val addedMap = try {
                store.addedAt(allVisibleIds)
            } catch (_: Exception) {
                emptyMap()
            }
            val nowSec = System.currentTimeMillis() / 1000
            addedMap.filter { NewBadgeLogic.isNew(it.value, nowSec, params.homePrefs.newBadgeDays) }.keys
        } else emptySet()

        SearchUiState(
            query = params.query,
            starredOnly = params.starredOnly,
            b64Only = params.b64Only,
            selectedSectionFilters = params.selectedSectionFilters,
            availableSections = sections.toImmutableList(),
            groups = groups,
            total = results?.total ?: 0,
            tookMs = results?.tookMs ?: 0L,
            recentSearches = recents.toImmutableList(),
            isSearching = false,
            hasSearched = results != null,
            newEntryIds = newEntryIds,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SearchUiState(query = initialQuery)
    )

    /** What the text field shows. Compose state, updated synchronously on every keystroke: binding the
     *  field to the async [uiState] instead drops and reorders characters while typing fast. */
    var queryText by mutableStateOf(initialQuery)
        private set

    fun onQueryChange(newQuery: String) {
        queryText = newQuery
        query.value = newQuery
    }

    fun toggleStarredOnly() {
        starredOnly.value = !starredOnly.value
    }

    fun toggleB64Only() {
        b64Only.value = !b64Only.value
    }

    fun toggleSectionFilter(sectionId: String) {
        val current = selectedSectionFilters.value
        selectedSectionFilters.value = if (current.contains(sectionId)) {
            current - sectionId
        } else {
            current + sectionId
        }
    }

    fun clearAllFilters() {
        selectedSectionFilters.value = emptySet()
        starredOnly.value = false
        b64Only.value = false
    }

    fun onSearchSubmitted(submittedQuery: String) {
        val trimmed = submittedQuery.trim()
        if (trimmed.isNotEmpty()) {
            viewModelScope.launch {
                settingsRepository.addRecentSearch(trimmed)
            }
        }
    }

    fun selectRecentSearch(recent: String) {
        onQueryChange(recent)
        onSearchSubmitted(recent)
    }

    fun removeRecentSearch(recent: String) {
        viewModelScope.launch {
            settingsRepository.removeRecentSearch(recent)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            settingsRepository.clearRecentSearches()
        }
    }
}
