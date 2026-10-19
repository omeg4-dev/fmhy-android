package io.github.omeg4dev.fmhy.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Section
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

@Immutable
data class SyncBannerUiState(
    val text: String,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
)

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val isReady: Boolean = false,
    val datasetVersion: Long = 0L,
    val sections: ImmutableList<Section> = persistentListOf(),
    val newSinceLastVisitCount: Int = 0,
    val randomPick: Entry? = null,
    val isEmptyState: Boolean = false,
    val syncBanner: SyncBannerUiState? = null,
)

class HomeViewModel(
    private val store: DatasetStore,
    private val settingsRepository: SettingsRepository,
    private val container: AppContainer,
) : ViewModel() {

    private val _randomPick = MutableStateFlow<Entry?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _syncBanner = MutableStateFlow<SyncBannerUiState?>(null)

    private var syncingJob: Job? = null
    private var autoHideJob: Job? = null

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

    private data class HomeInitData(
        val initialized: Boolean,
        val version: Long,
        val lastVisitTs: Long,
        val appearance: Appearance,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            container.isInitialized,
            store.changes,
            settingsRepository.lastVisitTs,
            settingsRepository.appearance,
        ) { initialized, version, lastVisitTs, appearance ->
            HomeInitData(initialized, version, lastVisitTs, appearance)
        },
        _randomPick,
        _isRefreshing,
        _syncBanner,
    ) { (initialized, version, lastVisitTs, appearance), randomPick, refreshing, banner ->
        if (!initialized) {
            HomeUiState(isLoading = true, isReady = false, syncBanner = banner)
        } else if (!store.isAvailable) {
            HomeUiState(isLoading = refreshing, isReady = true, isEmptyState = true, syncBanner = banner)
        } else {
            val sections = store.sections(appearance.sectionOrder).toImmutableList()
            val newCount = if (lastVisitTs > 0L) store.countAddedSince(lastVisitTs) else 0
            val pick = if (appearance.showRandomPick) (randomPick ?: store.randomTopPick()) else null
            val isEmpty = sections.isEmpty() && version == 0L

            HomeUiState(
                isLoading = refreshing,
                isReady = true,
                datasetVersion = version,
                sections = sections,
                newSinceLastVisitCount = newCount,
                randomPick = pick,
                isEmptyState = isEmpty,
                syncBanner = banner,
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
