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
import io.github.omeg4dev.fmhy.data.search.CategoryHit
import io.github.omeg4dev.fmhy.data.search.SearchEngine
import io.github.omeg4dev.fmhy.data.search.SearchGroup
import io.github.omeg4dev.fmhy.data.search.SearchResults
import io.github.omeg4dev.fmhy.ui.common.NewBadgeLogic
import io.github.omeg4dev.fmhy.data.safety.SafeGuard
import io.github.omeg4dev.fmhy.data.safety.Verdict
import io.github.omeg4dev.fmhy.ui.check.looksLikeUrl
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    val categories: ImmutableList<CategoryHit> = persistentListOf(),
    val total: Int = 0,
    val tookMs: Long = 0L,
    val recentSearches: ImmutableList<String> = persistentListOf(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val verdict: Verdict? = null,
    val suggestion: String? = null,
    val newEntryIds: Set<String> = emptySet(),
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
    private val searching = MutableStateFlow(false)

    init {
        // Reload whenever the dataset changes (first launch without a bundled snapshot, a refresh).
        viewModelScope.launch(Dispatchers.IO) {
            store.changes.collect {
                if (store.isAvailable) {
                    try {
                        allSections.value = store.sections().toImmutableList()
                    } catch (_: Exception) {
                    }
                }
            }
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
        val suggestion: String? = null,
    )

    private val searchResults = searchParams.mapLatest { params -> coroutineScope {
        // Only show the skeleton for searches that take longer than 150 ms; fast ones never flash it.
        val timer = launch { delay(150L); searching.value = true }
        try {
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
        val suggestion = if (results != null && results.total == 0 && results.categories.isEmpty() && effectiveQuery.isNotBlank()) {
            try {
                searchEngine.suggest(io.github.omeg4dev.fmhy.data.search.QueryParser.parse(effectiveQuery))
            } catch (_: Exception) {
                null
            }
        } else null
        if (allSections.value.isEmpty() && store.isAvailable) {
            // Sections could not be loaded earlier (dataset arrived late): retry on search.
            try {
                allSections.value = store.sections().toImmutableList()
            } catch (_: Exception) {
            }
        }
        SearchEngineAndVerdict(results, verdict, suggestion)
        } finally {
            timer.cancel()
            searching.value = false
        }
    } }.flowOn(Dispatchers.IO)

    private data class RecentsAndPrefs(
        val recents: List<String>,
        val homePrefs: io.github.omeg4dev.fmhy.data.HomePrefs,
    )

    val uiState: StateFlow<SearchUiState> = combine(
        combine(query, starredOnly, b64Only) { q, starred, b64 -> Triple(q, starred, b64) },
        combine(selectedSectionFilters, searching) { f, s -> f to s },
        allSections,
        combine(settingsRepository.recentSearches, settingsRepository.homePrefs) { r, h -> RecentsAndPrefs(r, h) },
        searchResults,
    ) { (q, starred, b64), (filters, isSearching), sections, recentsAndPrefs, engineAndVerdict ->
        val results = engineAndVerdict.results
        val groups: ImmutableList<SearchGroup> = (results?.groups ?: emptyList()).toImmutableList()
        val badgeDays = recentsAndPrefs.homePrefs.newBadgeDays
        val allVisibleIds = groups.flatMap { g -> g.hits.map { it.entry.id } }
        val newEntryIds = if (badgeDays > 0 && allVisibleIds.isNotEmpty()) {
            val addedMap = try {
                store.addedAt(allVisibleIds)
            } catch (_: Exception) {
                emptyMap()
            }
            val nowSec = System.currentTimeMillis() / 1000
            addedMap.filter { NewBadgeLogic.isNew(it.value, nowSec, badgeDays) }.keys
        } else emptySet()
        SearchUiState(
            query = q,
            starredOnly = starred,
            b64Only = b64,
            selectedSectionFilters = filters,
            availableSections = sections,
            groups = groups,
            categories = (results?.categories ?: emptyList()).toImmutableList(),
            total = results?.total ?: 0,
            tookMs = results?.tookMs ?: 0L,
            recentSearches = recentsAndPrefs.recents.toImmutableList(),
            isSearching = isSearching,
            hasSearched = results != null || engineAndVerdict.verdict != null,
            verdict = engineAndVerdict.verdict,
            suggestion = engineAndVerdict.suggestion,
            newEntryIds = newEntryIds,
        )
    }.flowOn(Dispatchers.IO).stateIn(
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
