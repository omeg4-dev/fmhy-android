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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SearchUiState(
    val query: String = "",
    val selectedSectionFilters: Set<String> = emptySet(),
    val availableSections: ImmutableList<Section> = persistentListOf(),
    val groups: ImmutableList<SearchGroup> = persistentListOf(),
    val total: Int = 0,
    val tookMs: Long = 0L,
    val recentSearches: ImmutableList<String> = persistentListOf(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    initialQuery: String = "",
    private val store: DatasetStore,
    private val searchEngine: SearchEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val query = MutableStateFlow(initialQuery)
    val selectedSectionFilters = MutableStateFlow<Set<String>>(emptySet())
    private val allSections = MutableStateFlow<ImmutableList<Section>>(persistentListOf())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (store.isAvailable) allSections.value = store.sections().toImmutableList()
        }
    }

    private data class SearchParams(val query: String, val filters: Set<String>)

    private val searchParams = combine(query, selectedSectionFilters) { q, filters ->
        SearchParams(q, filters)
    }.debounce(40L)
     .distinctUntilChanged()

    private val searchResults = searchParams.mapLatest { params ->
        if ((params.query.isBlank() && params.filters.isEmpty()) || !store.isAvailable) {
            null
        } else {
            searchEngine.search(
                query = params.query,
                sectionFilter = params.filters,
                limit = 200
            )
        }
    }.flowOn(Dispatchers.IO)

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        selectedSectionFilters,
        allSections,
        settingsRepository.recentSearches,
        searchResults,
    ) { q, filters, sections, recents, results ->
        SearchUiState(
            query = q,
            selectedSectionFilters = filters,
            availableSections = sections,
            groups = (results?.groups ?: emptyList()).toImmutableList(),
            total = results?.total ?: 0,
            tookMs = results?.tookMs ?: 0L,
            recentSearches = recents.toImmutableList(),
            isSearching = false,
            hasSearched = results != null,
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

    fun toggleSectionFilter(sectionId: String) {
        val current = selectedSectionFilters.value
        selectedSectionFilters.value = if (current.contains(sectionId)) {
            current - sectionId
        } else {
            current + sectionId
        }
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
