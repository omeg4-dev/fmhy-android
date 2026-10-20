package io.github.omeg4dev.fmhy.ui.whatsnew

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.user.Follow
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.ui.common.NewBadgeLogic
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class WhatsNewUiState(
    val selectedTab: WhatsNewTab = WhatsNewTab.ADDED,
    val followingFilter: Boolean = false,
    val savedFilter: Boolean = false,
    val selectedSections: Set<String> = emptySet(),
    val availableSections: ImmutableList<Section> = persistentListOf(),
    val dayGroups: ImmutableList<DayGroup> = persistentListOf(),
    val hasFollows: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val oldestChangeTs: Long? = null,
    val newEntryIds: Set<String> = emptySet(),
)

class WhatsNewViewModel(
    private val initialFilter: String = "all",
    private val container: AppContainer,
) : ViewModel() {

    private val store: DatasetStore = container.datasetStore
    private val userStore: UserStore = container.userStore

    private val selectedTab = MutableStateFlow(WhatsNewTab.ADDED)
    private val followingFilter = MutableStateFlow(initialFilter == "following")
    private val savedFilter = MutableStateFlow(initialFilter == "saved")
    private val selectedSections = MutableStateFlow<Set<String>>(emptySet())
    private val isRefreshing = MutableStateFlow(false)
    private val refreshTrigger = MutableStateFlow(0)

    private val rawChanges = MutableStateFlow<List<ChangeItem>>(emptyList())
    private val allSections = MutableStateFlow<List<Section>>(emptyList())
    private val followsList = MutableStateFlow<List<Follow>>(emptyList())
    private val savedUrlsAndIds = MutableStateFlow<Pair<Set<String>, Set<String>>>(emptySet<String>() to emptySet<String>())
    private val sectionHeadingHierarchy = MutableStateFlow<Map<String, Set<String>>>(emptyMap()) // headingId -> all descendant headingIds

    init {
        loadData()
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
                    val cutoffTs = nowSec - 30 * 86400L
                    val changes = store.recentChanges(
                        sinceTs = cutoffTs,
                        kinds = emptySet(),
                        limit = 1000,
                    )
                    rawChanges.value = changes
                }
            } catch (_: Exception) {}
        }
    }

    private data class FilterState(
        val tab: WhatsNewTab,
        val following: Boolean,
        val saved: Boolean,
        val sections: Set<String>,
        val refreshing: Boolean,
    )

    private val filterState = combine(
        selectedTab,
        followingFilter,
        savedFilter,
        selectedSections,
        isRefreshing,
    ) { tab, following, saved, sections, refreshing ->
        FilterState(tab, following, saved, sections, refreshing)
    }

    val uiState: StateFlow<WhatsNewUiState> = combine(
        filterState,
        rawChanges,
        allSections,
        followsList,
        savedUrlsAndIds,
    ) { filter, changes, sections, follows, (savedIds, savedUrls) ->
        val tabChanges = WhatsNewGrouping.filterChanges(changes, filter.tab)

        // Sections that have changes in current dataset
        val sectionsWithChanges = changes.map { it.sectionId }.toSet()
        val availableSections = sections.filter { it.id in sectionsWithChanges }.toImmutableList()

        // Filter by following if active
        val hierarchy = sectionHeadingHierarchy.value
        val afterFollowing = if (filter.following && follows.isNotEmpty()) {
            val sectionFollows = follows.filter { it.headingId.isEmpty() }.map { it.sectionId }.toSet()
            val categoryFollowHeadingIds = follows.filter { it.headingId.isNotEmpty() }.flatMap { f ->
                hierarchy[f.headingId] ?: setOf(f.headingId)
            }.toSet()

            tabChanges.filter { item ->
                if (item.sectionId in sectionFollows) return@filter true
                val hId = item.entry?.headingId
                if (hId != null && hId in categoryFollowHeadingIds) return@filter true
                false
            }
        } else {
            tabChanges
        }

        // Filter by saved if active
        val afterSaved = if (filter.saved) {
            afterFollowing.filter { item ->
                item.entryId in savedIds || DatasetStore.normalizeEntryUrl(item.url) in savedUrls
            }
        } else {
            afterFollowing
        }

        // Filter by selected sections if any
        val afterSections = if (filter.sections.isNotEmpty()) {
            afterSaved.filter { it.sectionId in filter.sections }
        } else {
            afterSaved
        }

        val dayGroups = WhatsNewGrouping.groupByDay(afterSections).toImmutableList()
        val oldestTs = changes.minOfOrNull { it.ts }

        val visibleEntryIds = afterSections.mapNotNull { it.entry?.id }
        val newEntryIds = try {
            if (visibleEntryIds.isNotEmpty()) {
                val addedMap = store.addedAt(visibleEntryIds)
                val nowSec = System.currentTimeMillis() / 1000
                addedMap.filter { NewBadgeLogic.isNew(it.value, nowSec, 3) }.keys
            } else emptySet()
        } catch (_: Exception) {
            emptySet()
        }

        WhatsNewUiState(
            selectedTab = filter.tab,
            followingFilter = filter.following,
            savedFilter = filter.saved,
            selectedSections = filter.sections,
            availableSections = availableSections,
            dayGroups = dayGroups,
            hasFollows = follows.isNotEmpty(),
            isLoading = false,
            isRefreshing = filter.refreshing,
            oldestChangeTs = oldestTs,
            newEntryIds = newEntryIds,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WhatsNewUiState(
            selectedTab = selectedTab.value,
            followingFilter = followingFilter.value,
            savedFilter = savedFilter.value,
        )
    )

    fun selectTab(tab: WhatsNewTab) {
        selectedTab.value = tab
    }

    fun toggleFollowingFilter() {
        followingFilter.value = !followingFilter.value
    }

    fun clearSavedFilter() {
        savedFilter.value = false
    }

    fun toggleSectionFilter(sectionId: String) {
        val current = selectedSections.value
        selectedSections.value = if (sectionId in current) current - sectionId else current + sectionId
    }

    fun clearSectionFilters() {
        selectedSections.value = emptySet()
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

    suspend fun getReplacements(change: ChangeItem): List<Entry> = withContext(Dispatchers.IO) {
        try {
            if (!store.isAvailable) return@withContext emptyList()
            val sectionEntries = store.entriesForSection(change.sectionId)
            val headingId = change.entry?.headingId

            val candidates = if (headingId != null) {
                sectionEntries.filter { it.headingId == headingId && it.id != change.entryId }
            } else {
                // If entry has no headingId or was deleted, match section entries
                sectionEntries.filter { it.id != change.entryId }
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
