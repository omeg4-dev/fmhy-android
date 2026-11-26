package io.github.omeg4dev.fmhy.ui.section

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.BuildConfig
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.data.newbadge.NewBadgeIndex
import io.github.omeg4dev.fmhy.data.nsfw.NSFW_SECTION
import io.github.omeg4dev.fmhy.data.nsfw.NSFW_SECTION_ID
import io.github.omeg4dev.fmhy.data.nsfw.NsfwLoadResult
import io.github.omeg4dev.fmhy.data.nsfw.NsfwSource
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.work.NetworkKind
import io.github.omeg4dev.fmhy.work.NetworkMonitor
import io.github.omeg4dev.fmhy.work.NetworkPolicy
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NsfwScreenStatus {
    NONE,
    DISABLED,
    WAITING_FOR_WIFI,
    FAILED,
}

@Immutable
data class SectionUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val section: Section? = null,
    val items: ImmutableList<SectionItem> = persistentListOf(),
    val headings: ImmutableList<Heading> = persistentListOf(),
    val structure: SectionStructure = SectionStructure(emptyList(), emptyList(), IntArray(0)),
    val layout: SectionLayout = SectionLayout.CONTINUOUS,
    val starredOnly: Boolean = false,
    val showIndexes: Boolean = true,
    val newBadges: Map<String, LocalDate> = emptyMap(),
    val nsfwStatus: NsfwScreenStatus = NsfwScreenStatus.NONE,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SectionViewModel(
    val sectionId: String,
    val anchor: String?,
    private val store: DatasetStore,
    private val settingsRepository: SettingsRepository,
    private val userStore: UserStore? = null,
    newBadgeIndex: StateFlow<NewBadgeIndex> = MutableStateFlow(NewBadgeIndex.EMPTY),
    today: () -> LocalDate = { LocalDate.now() },
    private val nsfwSource: NsfwSource? = null,
    private val networkMonitor: NetworkMonitor? = null,
    private val nsfwEnabledInBuild: Boolean = BuildConfig.NSFW_LINK_ENABLED,
) : ViewModel() {

    private data class NsfwLoadedState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val status: NsfwScreenStatus = NsfwScreenStatus.NONE,
        val data: NsfwLoadResult.Success? = null,
    )

    private data class ReloadRequest(
        val seq: Int,
        val force: Boolean,
    )

    private val isNsfw: Boolean = sectionId == NSFW_SECTION_ID
    private val _refreshTrigger = MutableStateFlow(0)
    private val _nsfwState = MutableStateFlow(NsfwLoadedState())
    private val _mobileOverride = MutableStateFlow(false)
    private val _reloadRequest = MutableStateFlow(ReloadRequest(seq = 0, force = false))
    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()
    private var lastAttemptWasForce: Boolean = false

    init {
        if (!isNsfw) {
            viewModelScope.launch {
                runCatching {
                    if (userStore != null && store.sections().any { it.id == sectionId }) {
                        userStore.recordSectionOpen(sectionId)
                    }
                }
            }
        } else {
            observeAndLoadNsfw()
        }
    }

    private fun observeAndLoadNsfw() {
        viewModelScope.launch {
            settingsRepository.homePrefs
                .map { nsfwEnabledInBuild && it.showNsfwLink }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    if (!enabled || nsfwSource == null) {
                        _nsfwState.value = NsfwLoadedState(
                            isLoading = false,
                            isRefreshing = false,
                            status = NsfwScreenStatus.DISABLED,
                            data = null,
                        )
                        return@collectLatest
                    }
                    var initialLoaded = false
                    var lastHandledSeq = _reloadRequest.value.seq
                    val wifiOnlyFlow = settingsRepository.dataUsagePrefs
                        .map { it.appDataWifiOnly }
                        .distinctUntilChanged()
                    val networkKindFlow = networkMonitor?.kind ?: flowOf(NetworkKind.WIFI)
                    val wifiAllowedFlow = combine(wifiOnlyFlow, networkKindFlow, _mobileOverride) { wifiOnly, kind, override ->
                        NetworkPolicy.allowed(wifiOnly, kind, overrideThisTime = override)
                    }.distinctUntilChanged()

                    combine(_reloadRequest, wifiAllowedFlow) { req, wifiAllowed ->
                        req to wifiAllowed
                    }.collect { (req, wifiAllowed) ->
                        val hasNewRequest = req.seq != lastHandledSeq
                        val recoveredFromWifiBlock =
                            initialLoaded && _nsfwState.value.status == NsfwScreenStatus.WAITING_FOR_WIFI && wifiAllowed
                        if (!initialLoaded || hasNewRequest || recoveredFromWifiBlock) {
                            initialLoaded = true
                            lastHandledSeq = req.seq
                            val force = if (hasNewRequest) req.force else false
                            executeNsfwLoad(forceRefresh = force, allowNetwork = wifiAllowed)
                        }
                    }
                }
        }
    }

    @Suppress("RedundantSuspendModifier")
    private suspend fun executeNsfwLoad(forceRefresh: Boolean, allowNetwork: Boolean) {
        val source = nsfwSource ?: return
        lastAttemptWasForce = forceRefresh
        val currentData = _nsfwState.value.data
        if (currentData != null) {
            _nsfwState.update { it.copy(isRefreshing = true) }
        } else {
            _nsfwState.update {
                it.copy(isLoading = true, isRefreshing = false, status = NsfwScreenStatus.NONE)
            }
        }

        when (val result = source.load(forceRefresh = forceRefresh, allowNetwork = allowNetwork)) {
            is NsfwLoadResult.Success -> {
                _nsfwState.value = NsfwLoadedState(
                    isLoading = false,
                    isRefreshing = false,
                    status = NsfwScreenStatus.NONE,
                    data = result,
                )
            }
            NsfwLoadResult.BlockedByWifi -> {
                if (currentData != null) {
                    _nsfwState.value = _nsfwState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        status = NsfwScreenStatus.NONE,
                    )
                    _snackbarEvents.tryEmit("Waiting for Wi-Fi")
                } else {
                    _nsfwState.value = NsfwLoadedState(
                        isLoading = false,
                        isRefreshing = false,
                        status = NsfwScreenStatus.WAITING_FOR_WIFI,
                        data = null,
                    )
                }
            }
            is NsfwLoadResult.Failed -> {
                if (currentData != null) {
                    _nsfwState.value = _nsfwState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        status = NsfwScreenStatus.NONE,
                    )
                    _snackbarEvents.tryEmit("Couldn't refresh the NSFW list")
                } else {
                    _nsfwState.value = NsfwLoadedState(
                        isLoading = false,
                        isRefreshing = false,
                        status = NsfwScreenStatus.FAILED,
                        data = null,
                    )
                }
            }
        }
    }

    val uiState: StateFlow<SectionUiState> = if (isNsfw) {
        combine(_nsfwState, settingsRepository.appearance) { nsfwState, appearance ->
            if (nsfwState.isLoading) {
                return@combine SectionUiState(
                    isLoading = true,
                    isRefreshing = false,
                    section = NSFW_SECTION,
                    layout = appearance.sectionLayout,
                    starredOnly = appearance.starredOnly,
                    showIndexes = appearance.showIndexes,
                )
            }
            val loaded = nsfwState.data
            if (nsfwState.status != NsfwScreenStatus.NONE || loaded == null) {
                return@combine SectionUiState(
                    isLoading = false,
                    isRefreshing = nsfwState.isRefreshing,
                    section = NSFW_SECTION,
                    layout = appearance.sectionLayout,
                    starredOnly = appearance.starredOnly,
                    showIndexes = appearance.showIndexes,
                    nsfwStatus = nsfwState.status,
                )
            }
            buildFilteredState(
                section = NSFW_SECTION,
                rawItems = loaded.items,
                allHeadings = loaded.headings,
                layout = appearance.sectionLayout,
                starredOnly = appearance.starredOnly,
                showIndexes = appearance.showIndexes,
                newBadges = emptyMap(),
                isRefreshing = nsfwState.isRefreshing,
                nsfwStatus = NsfwScreenStatus.NONE,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SectionUiState(isLoading = true, section = NSFW_SECTION),
        )
    } else {
        combine(
            store.changes,
            _refreshTrigger,
            settingsRepository.appearance,
            settingsRepository.homePrefs,
            newBadgeIndex,
        ) { _, _, appearance, homePrefs, index ->
            if (!store.isAvailable) {
                return@combine SectionUiState(
                    isLoading = false,
                    layout = appearance.sectionLayout,
                    starredOnly = appearance.starredOnly,
                    showIndexes = appearance.showIndexes,
                )
            }
            withContext(Dispatchers.Default) {
                val sections = store.sections()
                val currentSection = sections.firstOrNull { it.id == sectionId }
                val rawItems = store.sectionItems(sectionId)
                val allHeadings = store.headings(sectionId)
                val baseState = buildFilteredState(
                    section = currentSection,
                    rawItems = rawItems,
                    allHeadings = allHeadings,
                    layout = appearance.sectionLayout,
                    starredOnly = appearance.starredOnly,
                    showIndexes = appearance.showIndexes,
                    newBadges = emptyMap(),
                )
                val newBadges = if (homePrefs.newBadges) {
                    val todayDate = today()
                    val map = mutableMapOf<String, LocalDate>()
                    for (item in baseState.items) {
                        if (item is SectionItem.EntryItem) {
                            index.activeDate(item.entry, todayDate)?.let { date ->
                                map[item.entry.id] = date
                            }
                        }
                    }
                    map
                } else {
                    emptyMap()
                }
                baseState.copy(newBadges = newBadges)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SectionUiState(isLoading = true)
        )
    }

    private fun buildFilteredState(
        section: Section?,
        rawItems: List<SectionItem>,
        allHeadings: List<Heading>,
        layout: SectionLayout,
        starredOnly: Boolean,
        showIndexes: Boolean,
        newBadges: Map<String, LocalDate>,
        isRefreshing: Boolean = false,
        nsfwStatus: NsfwScreenStatus = NsfwScreenStatus.NONE,
    ): SectionUiState {
        val filteredItems = filterSectionItems(
            items = rawItems,
            starredOnly = starredOnly,
            showIndexes = showIndexes,
        ).toImmutableList()
        val keptHeadingIds = filteredItems.filterIsInstance<SectionItem.HeadingItem>()
            .mapTo(HashSet()) { it.heading.id }
        val sectionHeadings = allHeadings.filter { it.id in keptHeadingIds }.toImmutableList()
        val structure = buildSectionStructure(filteredItems)
        return SectionUiState(
            isLoading = false,
            isRefreshing = isRefreshing,
            section = section,
            items = filteredItems,
            headings = sectionHeadings,
            structure = structure,
            layout = layout,
            starredOnly = starredOnly,
            showIndexes = showIndexes,
            newBadges = newBadges,
            nsfwStatus = nsfwStatus,
        )
    }

    fun setStarredOnly(starredOnly: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStarredOnly(starredOnly)
        }
    }

    fun setShowIndexes(showIndexes: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowIndexes(showIndexes)
        }
    }

    fun refresh() {
        if (isNsfw) {
            if (_nsfwState.value.data != null) {
                _nsfwState.update { it.copy(isRefreshing = true) }
            }
            _reloadRequest.update { ReloadRequest(it.seq + 1, force = true) }
        } else {
            _refreshTrigger.value++
        }
    }

    fun retry() {
        if (isNsfw) {
            _reloadRequest.update { ReloadRequest(it.seq + 1, force = false) }
        } else {
            refresh()
        }
    }

    fun useMobileDataThisTime() {
        if (isNsfw) {
            _mobileOverride.value = true
            _reloadRequest.update { ReloadRequest(it.seq + 1, force = lastAttemptWasForce) }
        }
    }
}
