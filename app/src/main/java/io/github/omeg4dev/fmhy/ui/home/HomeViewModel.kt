package io.github.omeg4dev.fmhy.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.di.AppContainer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val isReady: Boolean = false,
    val datasetVersion: Long = 0L,
    val sections: ImmutableList<Section> = persistentListOf(),
    val newSinceLastVisitCount: Int = 0,
    val randomPick: Entry? = null,
    val isEmptyState: Boolean = false,
)

class HomeViewModel(
    private val store: DatasetStore,
    private val settingsRepository: SettingsRepository,
    private val container: AppContainer,
) : ViewModel() {

    private val _randomPick = MutableStateFlow<Entry?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        container.isInitialized,
        store.changes,
        settingsRepository.lastVisitTs,
        _randomPick,
        _isRefreshing,
    ) { initialized, version, lastVisitTs, randomPick, refreshing ->
        if (!initialized) {
            HomeUiState(isLoading = true, isReady = false)
        } else if (!store.isAvailable) {
            HomeUiState(isLoading = refreshing, isReady = true, isEmptyState = true)
        } else {
            val sections = store.sections().toImmutableList()
            val newCount = if (lastVisitTs > 0L) store.countAddedSince(lastVisitTs) else 0
            val pick = randomPick ?: store.randomTopPick()
            val isEmpty = sections.isEmpty() && version == 0L

            HomeUiState(
                isLoading = refreshing,
                isReady = true,
                datasetVersion = version,
                sections = sections,
                newSinceLastVisitCount = newCount,
                randomPick = pick,
                isEmptyState = isEmpty,
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
                container.datasetSync.sync(force = true)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
