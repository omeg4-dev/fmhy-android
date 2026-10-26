package io.github.omeg4dev.fmhy.ui.whatsnew

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.user.Follow
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.di.AppContainer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class WhatsNewUiState(
    val period: WhatsNewPeriod = WhatsNewPeriod.WEEK,
    val types: Set<ChangeKind> = WhatsNewGrouping.DEFAULT_TYPES,
    val scope: WhatsNewScope = WhatsNewScope.All,
    val counts: Map<ChangeKind, Int> = emptyMap(),
    val totalCards: Int = 0,
    val dayGroups: ImmutableList<DayGroup> = persistentListOf(),
    val availableSections: ImmutableList<Section> = persistentListOf(),
    val hasFollows: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val oldestChangeTs: Long? = null,
    val query: String = "",
    val cutoffTs: Long = 0L,
)

class WhatsNewViewModel(
    private val initialFilter: String = "all",
    initialType: String? = null,
    initialPeriod: String? = null,
    private val container: AppContainer,
) : ViewModel() {

    companion object {
        /** `WhatsNewNavKey.type` for the feed with every kind (incl. edits). */
        const val TYPE_ALL = "ALL"
    }

    private val store: DatasetStore = container.datasetStore
    private val userStore: UserStore = container.userStore

    private val period = MutableStateFlow(
        if (initialPeriod == "since_last_visit") WhatsNewPeriod.SINCE_LAST_VISIT else WhatsNewPeriod.WEEK
    )
    private val types = MutableStateFlow(
        when {
            initialType == null -> WhatsNewGrouping.DEFAULT_TYPES
            initialType == TYPE_ALL -> emptySet()
            else -> ChangeKind.entries.firstOrNull {
                it.name.equals(initialType, ignoreCase = true) || it.value.equals(initialType, ignoreCase = true)
            }?.let { setOf(it) } ?: WhatsNewGrouping.DEFAULT_TYPES
        }
    )
    private val query = MutableStateFlow("")
    private val scope = MutableStateFlow<WhatsNewScope>(
        when (initialFilter) {
            "following" -> WhatsNewScope.Following
            "saved" -> WhatsNewScope.Saved
            else -> WhatsNewScope.All
        }
    )
    private val isRefreshing = MutableStateFlow(false)
    private val isLoading = MutableStateFlow(true)
    private var hasAutoSwitched = false

    private val rawChanges = MutableStateFlow<List<ChangeItem>>(emptyList())
    private val allSections = MutableStateFlow<List<Section>>(emptyList())
    private val followsList = MutableStateFlow<List<Follow>>(emptyList())
    private val savedUrlsAndIds = MutableStateFlow<Pair<Set<String>, Set<String>>>(emptySet<String>() to emptySet<String>())
    private val sectionHeadingHierarchy = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    init {
        loadData()
        viewModelScope.launch {
            container.visitTracker.visitCutoffTs.drop(1).collect {
                loadChanges()
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            loadSectionsAndFollows()
            loadChanges()
        }
    }

    private suspend fun loadSectionsAndFollows() {
        withContext(Dispatchers.IO) {
            try {
                if (store.isAvailable) {
                    val sec = store.sections()
                    allSections.value = sec

                    val allH = store.allHeadings()
                    val parentToChildren = allH.groupBy { it.parentId }
                    val hierarchy = mutableMapOf<String, Set<String>>()
                    for (h in allH) {
                        val descendants = mutableSetOf<String>()
                        val queue = ArrayDeque<String>()
                        queue.add(h.id)
                        while (queue.isNotEmpty()) {
                            val cur = queue.removeFirst()
                            if (descendants.add(cur)) {
                                parentToChildren[cur]?.forEach { queue.add(it.id) }
                            }
                        }
                        hierarchy[h.id] = descendants
                    }
                    sectionHeadingHierarchy.value = hierarchy
                }
            } catch (_: Exception) {}

            try {
                followsList.value = userStore.follows()
                val bookmarks = userStore.bookmarks()
                val eIds = bookmarks.map { it.entryId }.toSet()
                val normUrls = bookmarks.map { DatasetStore.normalizeEntryUrl(it.url) }.toSet()
                savedUrlsAndIds.value = eIds to normUrls
            } catch (_: Exception) {}
        }
    }

    private suspend fun loadChanges() {
        withContext(Dispatchers.IO) {
            try {
                if (store.isAvailable) {
                    val nowSec = System.currentTimeMillis() / 1000
                    val currentPeriod = period.value
                    val changes = if (currentPeriod == WhatsNewPeriod.SINCE_LAST_VISIT) {
                        val cutoff = container.visitTracker.visitCutoffTs.value
                        if (cutoff == 0L) {
                            emptyList()
                        } else {
                            store.recentChanges(
                                sinceTs = cutoff + 1L,
                                kinds = emptySet(),
                                limit = 5000,
                            )
                        }
                    } else {
                        val cutoffTs = nowSec - currentPeriod.days * 86400L
                        store.recentChanges(
                            sinceTs = cutoffTs,
                            kinds = emptySet(),
                            limit = 5000,
                        )
                    }

                    // Auto-switch: if WEEK has 0 cards on the very first load, switch to MONTH automatically once
                    if (currentPeriod == WhatsNewPeriod.WEEK && !hasAutoSwitched) {
                        val cards = WhatsNewGrouping.collapseChanges(changes)
                        if (cards.isEmpty()) {
                            hasAutoSwitched = true
                            period.value = WhatsNewPeriod.MONTH
                            val monthCutoff = nowSec - WhatsNewPeriod.MONTH.days * 86400L
                            val monthChanges = store.recentChanges(
                                sinceTs = monthCutoff,
                                kinds = emptySet(),
                                limit = 5000,
                            )
                            rawChanges.value = monthChanges
                            return@withContext
                        }
                    }

                    rawChanges.value = changes
                }
            } catch (_: Exception) {
            } finally {
                isLoading.value = false
            }
        }
    }

    private data class FilterState(
        val period: WhatsNewPeriod,
        val types: Set<ChangeKind>,
        val scope: WhatsNewScope,
        val cutoffTs: Long,
        val refreshing: Boolean,
        val loading: Boolean,
    )

    private val filterState = combine(
        period,
        types,
        scope,
        container.visitTracker.visitCutoffTs,
        combine(isRefreshing, isLoading) { r, l -> r to l },
    ) { p, t, s, cutoff, (refreshing, loading) ->
        FilterState(p, t, s, cutoff, refreshing, loading)
    }

    private val filterAndQuery = combine(filterState, query) { f, q -> f to q }

    val uiState: StateFlow<WhatsNewUiState> = combine(
        filterAndQuery,
        rawChanges,
        allSections,
        followsList,
        savedUrlsAndIds,
    ) { (filter, text), changes, sections, follows, (savedIds, savedUrls) ->
        val collapsedCards = WhatsNewGrouping.collapseChanges(changes)

        // Sections that have changes in current dataset/period
        val sectionsWithChanges = changes.map { it.sectionId }.toSet()
        val availableSections = sections.filter { it.id in sectionsWithChanges }.toImmutableList()

        // 1. Filter by scope
        val hierarchy = sectionHeadingHierarchy.value
        val afterScopeCards = WhatsNewGrouping.filterByScope(
            cards = collapsedCards,
            scope = filter.scope,
            follows = follows,
            savedIds = savedIds,
            savedUrls = savedUrls,
            hierarchy = hierarchy,
        )

        // 1b. Free-text search over name, host and section title
        val afterTextCards = WhatsNewGrouping.filterByText(
            afterScopeCards, text, sections.associate { it.id to it.title },
        )

        // 2. Counts per kind computed AFTER scope + text filter, BEFORE type filter
        val counts = WhatsNewGrouping.countsPerKind(afterTextCards)
        val totalCards = afterTextCards.size

        // 3. Filter by types
        val afterTypeCards = WhatsNewGrouping.filterByTypes(afterTextCards, filter.types)

        // 4. Group by day
        val dayGroups = WhatsNewGrouping.groupByDay(afterTypeCards).toImmutableList()
        val oldestTs = changes.minOfOrNull { it.ts }

        WhatsNewUiState(
            period = filter.period,
            types = filter.types,
            scope = filter.scope,
            counts = counts,
            totalCards = totalCards,
            dayGroups = dayGroups,
            availableSections = availableSections,
            hasFollows = follows.isNotEmpty(),
            isLoading = filter.loading,
            isRefreshing = filter.refreshing,
            oldestChangeTs = oldestTs,
            query = text,
            cutoffTs = filter.cutoffTs,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WhatsNewUiState(
            period = period.value,
            types = types.value,
            scope = scope.value,
            isLoading = true,
            cutoffTs = container.visitTracker.visitCutoffTs.value,
        )
    )

    fun setQuery(text: String) {
        query.value = text
    }

    fun setPeriod(newPeriod: WhatsNewPeriod) {
        if (period.value == newPeriod) return
        period.value = newPeriod
        viewModelScope.launch {
            isLoading.value = true
            loadChanges()
        }
    }

    fun toggleType(kind: ChangeKind) {
        val current = types.value
        types.value = if (kind in current) current - kind else current + kind
    }

    fun setTypes(newTypes: Set<ChangeKind>) {
        types.value = newTypes
    }

    fun clearTypes() {
        types.value = emptySet()
    }

    fun setScope(newScope: WhatsNewScope) {
        scope.value = newScope
    }

    fun toggleSectionScope(sectionId: String) {
        val current = scope.value
        val currentSet = if (current is WhatsNewScope.Sections) current.sections else emptySet()
        val newSet = if (sectionId in currentSet) currentSet - sectionId else currentSet + sectionId
        scope.value = if (newSet.isEmpty()) WhatsNewScope.All else WhatsNewScope.Sections(newSet)
    }

    fun resetFilters() {
        types.value = emptySet()
        scope.value = WhatsNewScope.All
        setPeriod(WhatsNewPeriod.MONTH)
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                container.sync(force = true)
                loadSectionsAndFollows()
                loadChanges()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    suspend fun getReplacements(card: ChangeCard): List<Entry> = withContext(Dispatchers.IO) {
        try {
            if (!store.isAvailable) return@withContext emptyList()
            val sectionEntries = store.entriesForSection(card.sectionId)
            val headingId = card.entry?.headingId
            val cardEntryId = card.entry?.id ?: card.key.substringBefore(':')

            val candidates = if (headingId != null) {
                sectionEntries.filter { it.headingId == headingId && it.id != cardEntryId }
            } else {
                sectionEntries.filter { it.id != cardEntryId }
            }

            candidates
                .sortedWith(
                    compareByDescending<Entry> { it.starredAt != null || it.tier >= Tier.STAR }
                        .thenBy { it.name.lowercase() }
                )
                .take(15)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getEntryHistory(entryId: String): List<ChangeItem> = withContext(Dispatchers.IO) {
        try {
            if (store.isAvailable) store.entryHistory(entryId) else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
