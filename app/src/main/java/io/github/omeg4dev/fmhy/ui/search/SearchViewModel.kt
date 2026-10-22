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
import io.github.omeg4dev.fmhy.data.search.MatchMode
import io.github.omeg4dev.fmhy.data.search.QueryAssist
import io.github.omeg4dev.fmhy.data.search.QueryEdit
import io.github.omeg4dev.fmhy.data.search.QueryParser
import io.github.omeg4dev.fmhy.data.search.QuerySuggestion
import io.github.omeg4dev.fmhy.data.search.SuggestionKind
import io.github.omeg4dev.fmhy.data.search.TokenKind
import io.github.omeg4dev.fmhy.data.search.SearchEngine
import io.github.omeg4dev.fmhy.data.search.SearchGroup
import io.github.omeg4dev.fmhy.data.search.SearchResults
import io.github.omeg4dev.fmhy.ui.common.NewBadgeLogic
import io.github.omeg4dev.fmhy.data.safety.SafeGuard
import io.github.omeg4dev.fmhy.data.safety.Verdict
import io.github.omeg4dev.fmhy.ui.check.looksLikeUrl
import io.github.omeg4dev.fmhy.ui.whatsnew.WhatsNewGrouping
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SearchUiState(
    /** The raw query, in canonical form: pills (`in:x`, `is:starred`, ...) first, then the free text. */
    val query: String = "",
    val starredOnly: Boolean = false,
    val b64Only: Boolean = false,
    val selectedSectionFilters: Set<String> = emptySet(),
    /** Number of parameter pills in [query] (the "Filters · N" count). */
    val filterCount: Int = 0,
    val exact: Boolean = false,
    val whatsNewWeekCount: Int? = null,
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

    /** Canonical raw query: the single source of truth for text, pills and filters. */
    val query = MutableStateFlow(QueryEdit.canonical(initialQuery))
    private val allSections = MutableStateFlow<ImmutableList<Section>>(persistentListOf())
    private val searching = MutableStateFlow(false)
    private val whatsNewWeek = MutableStateFlow<Int?>(null)

    init {
        // Reload whenever the dataset changes (first launch without a bundled snapshot, a refresh).
        viewModelScope.launch(Dispatchers.IO) {
            store.changes.collect {
                if (store.isAvailable) {
                    try {
                        allSections.value = store.sections().toImmutableList()
                    } catch (_: Exception) {
                    }
                    launch { searchEngine.warmUp() }
                    whatsNewWeek.value = try {
                        val since = System.currentTimeMillis() / 1000 - 7 * 86400L
                        val cards = WhatsNewGrouping.collapseChanges(store.recentChanges(since, emptySet(), limit = 5000))
                        WhatsNewGrouping.filterByTypes(cards, WhatsNewGrouping.DEFAULT_TYPES).size
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
    }

    private val exactMode = settingsRepository.homePrefs.map { it.searchExact }.distinctUntilChanged()

    private data class SearchParams(val query: String, val exact: Boolean)

    private val searchParams = combine(query, exactMode) { q, exact -> SearchParams(q, exact) }
        .debounce(40L)
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

        val effectiveQuery = searchableQuery(trimmedQuery)

        val results = if (effectiveQuery.isBlank() || !store.isAvailable) {
            null
        } else {
            searchEngine.search(
                query = effectiveQuery,
                sectionFilter = emptySet(),
                limit = 200,
                mode = if (params.exact) MatchMode.EXACT else MatchMode.FUZZY,
            )
        }
        val suggestion = if (results != null && results.total == 0 && results.categories.isEmpty() && effectiveQuery.isNotBlank()) {
            try {
                searchEngine.suggest(QueryParser.parse(effectiveQuery))
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
        query,
        combine(searching, whatsNewWeek, exactMode) { s, w, e -> Triple(s, w, e) },
        allSections,
        combine(settingsRepository.recentSearches, settingsRepository.homePrefs) { r, h -> RecentsAndPrefs(r, h) },
        searchResults,
    ) { q, (isSearching, whatsNew, exact), sections, recentsAndPrefs, engineAndVerdict ->
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
            starredOnly = QueryEdit.hasParam(q, "is", "starred"),
            b64Only = QueryEdit.hasParam(q, "is", "b64"),
            selectedSectionFilters = QueryEdit.selectedSections(q, sections),
            filterCount = QueryEdit.pills(q).size,
            exact = exact,
            whatsNewWeekCount = whatsNew,
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
        SearchUiState(query = QueryEdit.canonical(initialQuery))
    )

    /** The full raw query (pills first). Compose state, updated synchronously on every keystroke: binding the
     *  field to the async [uiState] instead drops and reorders characters while typing fast. */
    var queryText by mutableStateOf(QueryEdit.canonical(initialQuery))
        private set

    /** Replaces the whole query; [raw] should already be canonical (see [QueryEdit.split]/[QueryEdit.compose]). */
    fun onQueryChange(raw: String) {
        queryText = raw
        query.value = raw
    }

    /** Sets a query that comes from outside the field (recent search, Try chip, "Did you mean"):
     *  every parameter counts as typed, so it comes back as a pill. */
    fun setQuery(raw: String) = onQueryChange(QueryEdit.canonical(raw))

    private fun edit(f: (String) -> String) = onQueryChange(f(queryText))

    fun toggleStarredOnly() = edit { QueryEdit.toggleParam(it, "is", "starred") }

    fun toggleB64Only() = edit { QueryEdit.toggleParam(it, "is", "b64") }

    fun toggleSectionFilter(sectionId: String) = edit { QueryEdit.toggleSection(it, sectionId, allSections.value) }

    fun removePill(text: String) = edit { QueryEdit.removePill(it, text) }

    fun clearAllFilters() = edit { raw ->
        val s = QueryEdit.split(QueryEdit.commitAll(raw))
        QueryEdit.compose(emptyList(), s.free)
    }

    fun setExact(exact: Boolean) {
        viewModelScope.launch { settingsRepository.updateHome { it.copy(searchExact = exact) } }
    }

    // --- suggestions under the bar -------------------------------------------------------------

    private val caret = MutableStateFlow("" to 0)

    /** Tells the VM what the field (the free text, without pills) shows and where the caret is. */
    fun onCaretChange(free: String, cursor: Int) {
        caret.value = free to cursor
    }

    val suggestions: StateFlow<List<QuerySuggestion>> = combine(caret, allSections) { c, sections -> c to sections }
        .mapLatest { (c, sections) ->
            val (free, cursor) = c
            val partial = QueryAssist.partialAt(free, cursor) ?: return@mapLatest emptyList()
            val req = QueryAssist.request(partial.text) ?: return@mapLatest emptyList()
            val list = when (req.kind) {
                SuggestionKind.OPERATOR -> QueryAssist.operators(req.prefix)
                SuggestionKind.SECTION -> QueryAssist.sections(req.prefix, sections)
                SuggestionKind.IS -> QueryAssist.isOptions(req.prefix)
                SuggestionKind.SITE -> QueryAssist.sites(req.prefix, try {
                    searchEngine.suggestHosts(req.prefix, 8)
                } catch (_: Exception) {
                    emptyList()
                })
            }
            // An operator that is already typed out in full has nothing left to offer.
            if (list.size == 1 && !list[0].commit && list[0].insert.equals(partial.text, ignoreCase = true)) emptyList() else list
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchSubmitted(submittedQuery: String) {
        val trimmed = submittedQuery.trim()
        if (trimmed.isNotEmpty()) {
            viewModelScope.launch {
                settingsRepository.addRecentSearch(trimmed)
            }
        }
    }

    fun selectRecentSearch(recent: String) {
        setQuery(recent)
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

/** The query handed to the engine: [raw] without half-typed parameters (`is:st`, a bare `in:`), which the
 *  parser would otherwise search for as plain words. */
internal fun searchableQuery(raw: String): String {
    val drop = QueryParser.tokens(raw).filter { t ->
        t.kind == TokenKind.WORD &&
            (t.key != null || t.text.startsWith("is:", ignoreCase = true) || t.text.startsWith("in:", ignoreCase = true) || t.text.startsWith("site:", ignoreCase = true))
    }
    if (drop.isEmpty()) return raw.trim()
    val sb = StringBuilder()
    var pos = 0
    for (t in drop) { sb.append(raw, pos, t.start); pos = t.end }
    sb.append(raw, pos, raw.length)
    return sb.toString().trim()
}
