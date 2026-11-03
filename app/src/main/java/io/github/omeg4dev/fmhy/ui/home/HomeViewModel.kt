package io.github.omeg4dev.fmhy.ui.home

import androidx.compose.runtime.Immutable
import io.github.omeg4dev.fmhy.data.parse.Sections
import io.github.omeg4dev.fmhy.data.SectionOrder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.Bookmark
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.ui.posts.PostsReadState
import io.github.omeg4dev.fmhy.ui.section.buildSectionStructure
import io.github.omeg4dev.fmhy.di.AppContainer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.omeg4dev.fmhy.data.HomePrefs
import io.github.omeg4dev.fmhy.data.user.Follow
import io.github.omeg4dev.fmhy.data.user.Pin
import io.github.omeg4dev.fmhy.data.user.UserStore

@Immutable
data class ResolvedPin(
    val sectionId: String,
    val headingId: String = "",
    val label: String,
    val anchor: String? = null,
    val pin: Pin? = null,
    val title: String = label,
    val parentTitle: String? = null,
)

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val isReady: Boolean = false,
    val datasetVersion: Long = 0L,
    val sections: ImmutableList<Section> = persistentListOf(),
    val sectionGroups: ImmutableList<SectionGroup> = persistentListOf(),
    val newSinceLastVisitCount: Int = 0,
    val randomPick: Entry? = null,
    val isEmptyState: Boolean = false,
    val pins: ImmutableList<ResolvedPin> = persistentListOf(),
    val followedKeys: Set<Pair<String, String>> = emptySet(),
    val hiddenSectionsCount: Int = 0,
    val showingHiddenSections: Boolean = false,
    val unreadPosts: Int = 0,
    val blocks: ImmutableList<HomeBlock> = persistentListOf(),
    val newInPinned: ImmutableList<ChangeItem> = persistentListOf(),
    val favourites: ImmutableList<Bookmark> = persistentListOf(),
    val suggestions: ImmutableList<Entry> = persistentListOf(),
)

/** A titled block of sections on Home; [title] is null when the list is not grouped (A–Z). */
data class SectionGroup(val title: String?, val sections: ImmutableList<Section>)

val AUDIO_TOOLS_SECTION = Section(
    id = "audio-tools",
    title = "Audio Tools",
    description = "Audio editors, converters, players & more",
    ord = 0,
    entryCount = 0,
)

val EDUCATIONAL_TOOLS_SECTION = Section(
    id = "educational-tools",
    title = "Educational Tools",
    description = "Learning tools, calculators, typing & more",
    ord = 0,
    entryCount = 0,
)

val BEGINNERS_GUIDE_SECTION = Section(
    id = "beginners-guide",
    title = "Beginner's guide",
    description = "Essential setup, adblocking & safety",
    ord = 0,
    entryCount = 0,
)

val FAQ_SECTION = Section(
    id = "faq",
    title = "FAQ",
    description = "Frequently asked questions & help",
    ord = 0,
    entryCount = 0,
)

val BACKUPS_SECTION = Section(
    id = "backups",
    title = "Backups & mirrors",
    description = "Mirrors, archives & emergency links",
    ord = 0,
    entryCount = 0,
)

val POSTS_SECTION = Section(
    id = "news-and-posts",
    title = "News & posts",
    description = "Monthly updates & announcements",
    ord = 0,
    entryCount = 0,
)

val MORE_VIRTUAL_SECTIONS = listOf(
    BEGINNERS_GUIDE_SECTION,
    FAQ_SECTION,
    BACKUPS_SECTION,
    POSTS_SECTION,
)

/** Home row id → (section, level-1 category title) it opens. */
private val ANCHOR_TARGETS = mapOf(
    "audio-tools" to ("audio" to "Audio Tools"),
    "educational-tools" to ("educational" to "Educational Tools"),
)

/** Groups like fmhy.net's sidebar: Wiki, Tools, then everything else. */
internal fun groupSections(
    sections: List<Section>,
    order: SectionOrder,
    anchorCounts: Map<String, Int> = emptyMap(),
): List<SectionGroup> {
    val anchors = listOf(AUDIO_TOOLS_SECTION, EDUCATIONAL_TOOLS_SECTION)
        .map { it.copy(entryCount = anchorCounts[it.id] ?: 0) }
    if (order != SectionOrder.WEBSITE) {
        val allSections = (sections + anchors + MORE_VIRTUAL_SECTIONS)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        return listOf(SectionGroup(null, allSections.toImmutableList()))
    }
    val toolsStart = Sections.SIDEBAR_ORDER.indexOf("system-tools")
    val toolsEnd = Sections.SIDEBAR_ORDER.indexOf("developer-tools")
    val byGroup = sections.groupBy {
        val idx = Sections.SIDEBAR_ORDER.indexOf(it.id)
        when {
            idx in 0 until toolsStart -> "Wiki"
            idx in toolsStart..toolsEnd -> "Tools"
            else -> "More"
        }
    }.toMutableMap()

    val toolsList = byGroup["Tools"]?.toMutableList() ?: mutableListOf()
    val videoToolsIdx = toolsList.indexOfFirst { it.id == "video-tools" }
    val devToolsIdx = toolsList.indexOfFirst { it.id == "developer-tools" }
    val insertIdx = when {
        videoToolsIdx != -1 -> videoToolsIdx + 1
        devToolsIdx != -1 -> devToolsIdx
        else -> toolsList.size
    }
    toolsList.addAll(insertIdx, anchors)
    byGroup["Tools"] = toolsList

    val moreList = byGroup["More"]?.toMutableList() ?: mutableListOf()
    moreList.addAll(MORE_VIRTUAL_SECTIONS)
    byGroup["More"] = moreList

    return listOf("Wiki", "Tools", "More").mapNotNull { title ->
        byGroup[title]?.let { SectionGroup(title, it.toImmutableList()) }
    }
}

internal fun resolvePins(
    pins: List<Pin>,
    sections: List<Section>,
): List<ResolvedPin> {
    val sectionMap = sections.associateBy { it.id }
    return pins.sortedBy { it.ord }.map { pin ->
        val sectionTitle = sectionMap[pin.sectionId]?.title ?: pin.sectionId
        if (pin.headingId.isEmpty()) {
            ResolvedPin(
                sectionId = pin.sectionId,
                headingId = "",
                label = sectionTitle,
                anchor = null,
                pin = pin,
                title = sectionTitle,
                parentTitle = null,
            )
        } else {
            val label = "$sectionTitle › ${pin.title}"
            ResolvedPin(
                sectionId = pin.sectionId,
                headingId = pin.headingId,
                label = label,
                anchor = pin.headingId,
                pin = pin,
                title = pin.title,
                parentTitle = sectionTitle,
            )
        }
    }
}

internal fun isPinFollowed(pin: ResolvedPin, follows: List<Follow>): Boolean {
    val sectionFollowed = follows.any { it.sectionId == pin.sectionId && it.headingId.isEmpty() && !it.excluded }
    if (pin.headingId.isEmpty()) {
        return sectionFollowed
    }
    val subFollow = follows.firstOrNull { it.sectionId == pin.sectionId && it.headingId == pin.headingId }
    return if (sectionFollowed) {
        subFollow?.excluded != true
    } else {
        subFollow != null && !subFollow.excluded
    }
}

internal fun filterSections(
    sections: List<Section>,
    hiddenSections: Set<String>,
    showHiddenSession: Boolean,
): List<Section> {
    if (showHiddenSession || hiddenSections.isEmpty()) {
        return sections
    }
    return sections.filter { it.id !in hiddenSections }
}

internal fun mergeNewInPinned(
    perPin: List<List<ChangeItem>>,
    limit: Int = 5,
): List<ChangeItem> {
    if (limit <= 0) return emptyList()
    return perPin.flatten()
        .sortedByDescending { it.ts }
        .distinctBy { it.url }
        .take(limit)
}

internal fun latestFavourites(
    list: List<Bookmark>,
    limit: Int = 5,
): List<Bookmark> {
    if (limit <= 0) return emptyList()
    return list.sortedByDescending { it.savedAt }.take(limit)
}

internal fun buildSuggestions(
    randomPick: Entry?,
    perSection: List<List<Entry>>,
    fillPicks: List<Entry>,
    exclude: Set<String>,
): List<Entry> {
    val result = mutableListOf<Entry>()
    val seenIds = exclude.toMutableSet()

    if (randomPick != null && seenIds.add(randomPick.id)) {
        result.add(randomPick)
    }

    var addedFromSections = 0
    val maxLen = perSection.maxOfOrNull { it.size } ?: 0
    for (col in 0 until maxLen) {
        for (secList in perSection) {
            if (addedFromSections >= 4 || result.size >= 5) break
            if (col < secList.size) {
                val entry = secList[col]
                if (seenIds.add(entry.id)) {
                    result.add(entry)
                    addedFromSections++
                }
            }
        }
        if (addedFromSections >= 4 || result.size >= 5) break
    }

    for (pick in fillPicks) {
        if (result.size >= 5) break
        if (seenIds.add(pick.id)) {
            result.add(pick)
        }
    }

    return result
}

internal fun shortAge(nowSec: Long, tsSec: Long): String {
    val diffSec = (nowSec - tsSec).coerceAtLeast(0L)
    return when {
        diffSec < 60 -> "just now"
        diffSec < 3600 -> "${diffSec / 60}m"
        diffSec < 86400 -> "${diffSec / 3600}h"
        else -> "${diffSec / 86400}d"
    }
}

class HomeViewModel(
    private val store: DatasetStore,
    private val settingsRepository: SettingsRepository,
    private val container: AppContainer,
    private val userStore: UserStore = container.userStore,
) : ViewModel() {

    private val _randomPick = MutableStateFlow<Entry?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _showHiddenSections = MutableStateFlow(false)

    fun toggleShowHiddenSections() {
        _showHiddenSections.value = !_showHiddenSections.value
    }

    private var currentPick: Entry? = null
    private var currentPickVersion = -1L

    /**
     * The random pick is chosen once per process and only changes via [refreshRandomPick]; unrelated
     * upstream emissions (prefs, user data) must not re-roll it. After a dataset change it is re-rolled
     * only if its entry is gone.
     */
    private suspend fun stablePick(explicit: Entry?, version: Long): Entry? {
        if (explicit != null) {
            currentPick = explicit
            currentPickVersion = version
            return explicit
        }
        val cur = currentPick
        if (cur != null) {
            if (version == currentPickVersion || store.entry(cur.id) != null) {
                currentPickVersion = version
                return cur
            }
        }
        return store.randomTopPick().also {
            currentPick = it
            currentPickVersion = version
        }
    }

    private var cachedSuggestions: List<Entry> = emptyList()
    private var cachedSuggestionsVersion = -1L

    /**
     * Suggestions are stable per process and recomputed only when the dataset version changes
     * or when the cache is empty — not on every user/prefs emission.
     */
    private suspend fun stableSuggestions(version: Long, randomPickBlockEntry: Entry?): List<Entry> {
        if (cachedSuggestionsVersion == version && cachedSuggestions.isNotEmpty()) {
            return cachedSuggestions
        }
        val computed = try {
            val mainPick = store.randomTopPick()
            val topSectionIds = userStore.topOpenedSections(4)
            val perSection = topSectionIds.map { sectionId ->
                store.randomTopEntries(sectionId, 2)
            }
            val fillPicks = (1..4).mapNotNull { store.randomTopPick() }
            val exclude = if (randomPickBlockEntry != null) setOf(randomPickBlockEntry.id) else emptySet()
            buildSuggestions(mainPick, perSection, fillPicks, exclude)
        } catch (_: Throwable) {
            emptyList()
        }
        cachedSuggestions = computed
        cachedSuggestionsVersion = version
        return computed
    }

    /** Site counts for the Home rows that point at a category inside another section (Audio Tools, …). */
    private suspend fun anchorCounts(): Map<String, Int> = ANCHOR_TARGETS.mapValues { (_, target) ->
        val (sectionId, title) = target
        buildSectionStructure(store.sectionItems(sectionId)).categories
            .firstOrNull { CrossLinks.splitLinkTitle(it.heading.title).first.equals(title, ignoreCase = true) }
            ?.entryCount ?: 0
    }

    private data class HomeCoreData(
        val initialized: Boolean,
        val version: Long,
        val lastVisitTs: Long,
        val appearance: Appearance,
    )

    private data class HomeUserData(
        val homePrefs: HomePrefs,
        val userChanges: Long,
        val showHidden: Boolean,
        val bookmarks: List<Bookmark>,
    )

    private data class HomeUiExtras(
        val randomPick: Entry?,
        val refreshing: Boolean,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            container.isInitialized,
            store.changes,
            container.visitTracker.visitCutoffTs,
            settingsRepository.appearance,
        ) { initialized, version, lastVisitTs, appearance ->
            HomeCoreData(initialized, version, lastVisitTs, appearance)
        },
        combine(
            settingsRepository.homePrefs,
            userStore.changes,
            _showHiddenSections,
            container.bookmarks.bookmarks,
        ) { homePrefs, userChanges, showHidden, bookmarks ->
            HomeUserData(homePrefs, userChanges, showHidden, bookmarks)
        },
        combine(
            _randomPick,
            _isRefreshing,
        ) { randomPick, refreshing ->
            HomeUiExtras(randomPick, refreshing)
        }
    ) { (initialized, version, lastVisitTs, appearance), (homePrefs, _, showHidden, bookmarks), (randomPick, refreshing) ->
        if (!initialized) {
            HomeUiState(isLoading = true, isReady = false)
        } else if (!store.isAvailable) {
            HomeUiState(isLoading = refreshing, isReady = true, isEmptyState = true)
        } else {
            val allSections = store.sections(appearance.sectionOrder)
            val hiddenSections = try { userStore.hiddenSections() } catch (_: Exception) { emptySet() }
            val rawPins = try { userStore.pins() } catch (_: Exception) { emptyList() }
            val rawFollows = try { userStore.follows() } catch (_: Exception) { emptyList() }

            val resolvedPins = resolvePins(rawPins, allSections).toImmutableList()
            val followedKeys = resolvedPins
                .filter { isPinFollowed(it, rawFollows) }
                .map { it.sectionId to it.headingId }
                .toSet()
            val visibleSections = filterSections(allSections, hiddenSections, showHidden).toImmutableList()

            val newCount = if (homePrefs.blocks.isEnabled(HomeBlock.NEW_SINCE) && lastVisitTs > 0L) {
                store.countAddedSince(lastVisitTs)
            } else {
                0
            }
            val pick = if (homePrefs.blocks.isEnabled(HomeBlock.RANDOM_PICK)) {
                stablePick(randomPick, version)
            } else {
                null
            }

            val newInPinnedList = if (homePrefs.blocks.isEnabled(HomeBlock.NEW_IN_PINNED) && lastVisitTs > 0L) {
                val perPinChanges = resolvedPins.take(20).map { pin ->
                    try {
                        if (pin.headingId.isEmpty()) {
                            store.recentChanges(
                                sinceTs = lastVisitTs,
                                kinds = setOf(ChangeKind.ADDED),
                                sectionIds = setOf(pin.sectionId),
                                limit = 5,
                            )
                        } else {
                            store.recentChanges(
                                sinceTs = lastVisitTs,
                                kinds = setOf(ChangeKind.ADDED),
                                headingIds = setOf(pin.headingId),
                                limit = 5,
                            )
                        }
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }
                mergeNewInPinned(perPinChanges, limit = 5)
            } else {
                emptyList()
            }

            val favouritesList = if (homePrefs.blocks.isEnabled(HomeBlock.FAVOURITES)) {
                latestFavourites(bookmarks, limit = 5)
            } else {
                emptyList()
            }

            val suggestionsList = if (homePrefs.blocks.isEnabled(HomeBlock.SUGGESTIONS)) {
                stableSuggestions(version, pick)
            } else {
                emptyList()
            }

            val isEmpty = visibleSections.isEmpty() && version == 0L
            val unreadPosts = try {
                PostsReadState.unreadCountFromCache(userStore, container.context.filesDir)
            } catch (_: Throwable) { 0 }

            HomeUiState(
                isLoading = refreshing,
                isReady = true,
                datasetVersion = version,
                sections = visibleSections,
                sectionGroups = groupSections(visibleSections, appearance.sectionOrder, anchorCounts()).toImmutableList(),
                newSinceLastVisitCount = newCount,
                randomPick = pick,
                isEmptyState = isEmpty,
                pins = resolvedPins,
                followedKeys = followedKeys,
                hiddenSectionsCount = hiddenSections.size,
                showingHiddenSections = showHidden,
                unreadPosts = unreadPosts,
                blocks = homePrefs.blocks.enabledBlocks().toImmutableList(),
                newInPinned = newInPinnedList.toImmutableList(),
                favourites = favouritesList.toImmutableList(),
                suggestions = suggestionsList.toImmutableList(),
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        HomeUiState(isLoading = true, isReady = false)
    )

    fun refreshRandomPick() {
        viewModelScope.launch {
            _randomPick.value = store.randomTopPick()
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                container.sync(force = true)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
