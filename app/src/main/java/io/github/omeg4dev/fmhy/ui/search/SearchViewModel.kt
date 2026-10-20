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
import io.github.omeg4dev.fmhy.data.safety.SafeGuard
import io.github.omeg4dev.fmhy.data.safety.Verdict
import io.github.omeg4dev.fmhy.ui.check.looksLikeUrl
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
    val verdict: Verdict? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    initialQuery: String = "",
    private val store: DatasetStore,
    private val searchEngine: SearchEngine,
    private val settingsRepository: SettingsRepository,
    private val safeGuard: SafeGuard = SafeGuard(store),
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

    private data class SearchEngineAndVerdict(
        val results: SearchResults?,
        val verdict: Verdict?,
    )

    private val searchResults = searchParams.mapLatest { params ->
        val trimmedQuery = params.query.trim()
        val verdict = if (looksLikeUrl(trimmedQuery) && store.isAvailable) {
            safeGuard.check(trimmedQuery)
        } else {
            null
        }

        val effectiveQuery = buildString {
            append(trimmedQuery)
            if (params.starredOnly) {
                if (isNotEmpty()) append(" ")
                append("is:starred")
            }
            if (params.b64Only) {
                if (isNotEmpty()) append(" ")
                append("is:b64")
            }
        }

        val results = if ((effectiveQuery.isBlank() && params.filters.isEmpty()) || !store.isAvailable) {
            null
        } else {
            searchEngine.search(
                query = effectiveQuery,
                sectionFilter = params.filters,
                limit = 200
            )
        }
        SearchEngineAndVerdict(results, verdict)
    }.flowOn(Dispatchers.IO)

    val uiState: StateFlow<SearchUiState> = combine(
        combine(query, starredOnly, b64Only) { q, starred, b64 -> Triple(q, starred, b64) },
        selectedSectionFilters,
        allSections,
        settingsRepository.recentSearches,
        searchResults,
    ) { (q, starred, b64), filters, sections, recents, engineAndVerdict ->
        val results = engineAndVerdict.results
        SearchUiState(
            query = q,
            starredOnly = starred,
            b64Only = b64,
            selectedSectionFilters = filters,
            availableSections = sections,
            groups = (results?.groups ?: emptyList()).toImmutableList(),
            total = results?.total ?: 0,
            tookMs = results?.tookMs ?: 0L,
            recentSearches = recents.toImmutableList(),
            isSearching = false,
            hasSearched = results != null || engineAndVerdict.verdict != null,
            verdict = engineAndVerdict.verdict,
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
