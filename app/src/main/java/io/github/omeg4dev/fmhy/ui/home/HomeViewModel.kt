package io.github.omeg4dev.fmhy.ui.home

import androidx.compose.runtime.Immutable
import io.github.omeg4dev.fmhy.data.parse.Sections
import io.github.omeg4dev.fmhy.data.SectionOrder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.ui.section.buildSectionStructure
import io.github.omeg4dev.fmhy.data.sync.SyncResult
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.di.SyncStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.omeg4dev.fmhy.data.HomePrefs
import io.github.omeg4dev.fmhy.data.user.Pin
import io.github.omeg4dev.fmhy.data.user.UserStore

@Immutable
data class SyncBannerUiState(
    val text: String,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
)

@Immutable
data class ResolvedPin(
    val sectionId: String,
    val headingId: String = "",
    val label: String,
    val anchor: String? = null,
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
    val syncBanner: SyncBannerUiState? = null,
    val personalHome: Boolean = false,
    val pins: ImmutableList<ResolvedPin> = persistentListOf(),
    val hiddenSectionsCount: Int = 0,
    val showingHiddenSections: Boolean = false,
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
            )
        } else {
            val label = "$sectionTitle › ${pin.title}"
            ResolvedPin(
                sectionId = pin.sectionId,
                headingId = pin.headingId,
                label = label,
                anchor = pin.headingId,
            )
        }
    }
}

internal fun filterSections(
    sections: List<Section>,
    personalHome: Boolean,
    hiddenSections: Set<String>,
    showHiddenSession: Boolean,
): List<Section> {
    if (!personalHome || showHiddenSession || hiddenSections.isEmpty()) {
        return sections
    }
    return sections.filter { it.id !in hiddenSections }
}

class HomeViewModel(
    private val store: DatasetStore,
    private val settingsRepository: SettingsRepository,
    private val container: AppContainer,
    private val userStore: UserStore = container.userStore,
) : ViewModel() {

    private val _randomPick = MutableStateFlow<Entry?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _syncBanner = MutableStateFlow<SyncBannerUiState?>(null)
    private val _showHiddenSections = MutableStateFlow(false)

    fun toggleShowHiddenSections() {
        _showHiddenSections.value = !_showHiddenSections.value
    }

    private var syncingJob: Job? = null
    private var autoHideJob: Job? = null

    /** Site counts for the Home rows that point at a category inside another section (Audio Tools, …). */
    private suspend fun anchorCounts(): Map<String, Int> = ANCHOR_TARGETS.mapValues { (_, target) ->
        val (sectionId, title) = target
        buildSectionStructure(store.sectionItems(sectionId)).categories
            .firstOrNull { CrossLinks.splitLinkTitle(it.heading.title).first.equals(title, ignoreCase = true) }
            ?.entryCount ?: 0
    }

    init {
        viewModelScope.launch {
            container.syncStatus.collect { status ->
                handleSyncStatus(status)
            }
        }
    }

    private fun handleSyncStatus(status: SyncStatus) {
        when (status) {
            is SyncStatus.Idle -> {
                // Do nothing
            }
            is SyncStatus.Syncing -> {
                syncingJob?.cancel()
                autoHideJob?.cancel()
                syncingJob = viewModelScope.launch {
                    delay(600)
                    _syncBanner.value = SyncBannerUiState(
                        text = "Syncing…",
                        isLoading = true,
                    )
                }
            }
            is SyncStatus.Finished -> {
                syncingJob?.cancel()
                autoHideJob?.cancel()

                if (container.isSyncBannerConsumed) {
                    _syncBanner.value = null
                    return
                }

                when (val result = status.result) {
                    is SyncResult.Skipped -> {
                        _syncBanner.value = null
                    }
                    is SyncResult.UpToDate -> {
                        _syncBanner.value = SyncBannerUiState(
                            text = "Synced in background · up to date",
                            isLoading = false,
                            isError = false,
                        )
                        startAutoHide()
                    }
                    is SyncResult.Updated -> {
                        val text = when {
                            result.added > 0 && result.removed > 0 ->
                                "Synced in background · ${result.added} new · ${result.removed} removed"
                            result.added > 0 ->
                                "Synced in background · ${result.added} new"
                            result.removed > 0 ->
                                "Synced in background · ${result.removed} removed"
                            else ->
                                "Synced in background · updated"
                        }
                        _syncBanner.value = SyncBannerUiState(
                            text = text,
                            isLoading = false,
                            isError = false,
                        )
                        startAutoHide()
                    }
                    is SyncResult.Failed -> {
                        _syncBanner.value = SyncBannerUiState(
                            text = "Offline — showing saved data",
                            isLoading = false,
                            isError = true,
                        )
                        startAutoHide()
                    }
                }
            }
        }
    }

    private fun startAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = viewModelScope.launch {
            delay(4000)
            _syncBanner.value = null
            container.markSyncBannerConsumed()
        }
    }

    fun dismissBanner() {
        autoHideJob?.cancel()
        syncingJob?.cancel()
        _syncBanner.value = null
        container.markSyncBannerConsumed()
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
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            container.isInitialized,
            store.changes,
            settingsRepository.lastVisitTs,
            settingsRepository.appearance,
        ) { initialized, version, lastVisitTs, appearance ->
            HomeCoreData(initialized, version, lastVisitTs, appearance)
        },
        combine(
            settingsRepository.homePrefs,
            userStore.changes,
            _showHiddenSections,
        ) { homePrefs, userChanges, showHidden ->
            HomeUserData(homePrefs, userChanges, showHidden)
        },
        _randomPick,
        _isRefreshing,
        _syncBanner,
    ) { (initialized, version, lastVisitTs, appearance), (homePrefs, _, showHidden), randomPick, refreshing, banner ->
        if (!initialized) {
            HomeUiState(isLoading = true, isReady = false, syncBanner = banner)
        } else if (!store.isAvailable) {
            HomeUiState(isLoading = refreshing, isReady = true, isEmptyState = true, syncBanner = banner)
        } else {
            val personalHome = homePrefs.personalHome
            val allSections = store.sections(appearance.sectionOrder)
            val hiddenSections = if (personalHome) {
                try { userStore.hiddenSections() } catch (_: Exception) { emptySet() }
            } else emptySet()

            val rawPins = if (personalHome) {
                try { userStore.pins() } catch (_: Exception) { emptyList() }
            } else emptyList()

            val resolvedPins = resolvePins(rawPins, allSections).toImmutableList()
            val visibleSections = filterSections(allSections, personalHome, hiddenSections, showHidden).toImmutableList()

            val newCount = if (lastVisitTs > 0L) store.countAddedSince(lastVisitTs) else 0
            val pick = if (appearance.showRandomPick) (randomPick ?: store.randomTopPick()) else null
            val isEmpty = visibleSections.isEmpty() && version == 0L

            HomeUiState(
                isLoading = refreshing,
                isReady = true,
                datasetVersion = version,
                sections = visibleSections,
                sectionGroups = groupSections(visibleSections, appearance.sectionOrder, anchorCounts()).toImmutableList(),
                newSinceLastVisitCount = newCount,
                randomPick = pick,
                isEmptyState = isEmpty,
                syncBanner = banner,
                personalHome = personalHome,
                pins = resolvedPins,
                hiddenSectionsCount = hiddenSections.size,
                showingHiddenSections = showHidden,
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
