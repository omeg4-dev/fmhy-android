package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.SectionOrder
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.sync.SyncResult
import io.github.omeg4dev.fmhy.data.sync.SyncState
import io.github.omeg4dev.fmhy.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val entryCount: Int = 0,
    val lastSyncedText: String = "",
    val datasetVersion: Long = 0L,
    val openInExternalBrowser: Boolean = false,
    val isUpdating: Boolean = false,
    val updateStatusMessage: String? = null,
    val appearance: Appearance = Appearance(),
)

class SettingsViewModel(
    private val store: DatasetStore,
    private val settingsRepository: SettingsRepository,
    private val container: AppContainer,
) : ViewModel() {

    private val _isUpdating = MutableStateFlow(false)
    private val _updateStatusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            store.changes,
            settingsRepository.openInExternalBrowser,
            settingsRepository.appearance,
        ) { version, openExternal, appearance ->
            Triple(version, openExternal, appearance)
        },
        _isUpdating,
        _updateStatusMessage,
    ) { (version, openExternal, appearance), updating, statusMsg ->
        val count = try { store.entryCount() } catch (_: Exception) { 0 }
        val syncState = try { container.fmhySync.loadState() } catch (_: Exception) { SyncState() }
        val lastSyncedStr = when {
            syncState.lastSuccess > 0L -> {
                val host = if (syncState.source == "fallback") "jsDelivr" else "GitHub"
                "${formatRelativeTime(syncState.lastSuccess)} via $host"
            }
            syncState.lastCheck > 0L -> {
                val host = if (syncState.source == "fallback") "jsDelivr" else "GitHub"
                "${formatRelativeTime(syncState.lastCheck)} via $host"
            }
            else -> "Never"
        }

        SettingsUiState(
            entryCount = count,
            lastSyncedText = lastSyncedStr,
            datasetVersion = version,
            openInExternalBrowser = openExternal,
            isUpdating = updating,
            updateStatusMessage = statusMsg,
            appearance = appearance,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsUiState()
    )

    fun setOpenInExternalBrowser(open: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOpenInExternalBrowser(open)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setColorSource(source: ColorSource) {
        viewModelScope.launch {
            settingsRepository.setColorSource(source)
        }
    }

    fun setPureBlack(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPureBlack(enabled)
        }
    }

    fun setAccentSeed(argb: Int) {
        viewModelScope.launch {
            settingsRepository.setAccentSeed(argb)
        }
    }

    fun setSectionLayout(layout: SectionLayout) {
        viewModelScope.launch {
            settingsRepository.setSectionLayout(layout)
        }
    }

    fun setTextScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.setTextScale(scale)
        }
    }

    fun setSectionOrder(order: SectionOrder) {
        viewModelScope.launch {
            settingsRepository.setSectionOrder(order)
        }
    }

    fun setShowRandomPick(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowRandomPick(show)
        }
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

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHapticFeedback(enabled)
        }
    }

    fun updateNow() {
        viewModelScope.launch {
            _isUpdating.value = true
            _updateStatusMessage.value = "Checking for updates..."
            try {
                val result = container.sync(force = true)
                val msg = when (result) {
                    is SyncResult.UpToDate -> "Already up to date"
                    is SyncResult.Updated -> "Updated: ${result.added} added, ${result.removed} removed"
                    is SyncResult.Skipped -> "Skipped"
                    is SyncResult.Failed -> "Update failed: ${result.reason}"
                }
                _updateStatusMessage.value = msg
            } catch (e: Exception) {
                _updateStatusMessage.value = "Error: ${e.message}"
            } finally {
                _isUpdating.value = false
            }
        }
    }

    private fun formatRelativeTime(seconds: Long): String {
        val nowSec = System.currentTimeMillis() / 1000
        val diff = nowSec - seconds
        return when {
            diff < 60 -> "Just now"
            diff < 3600 -> ago(diff / 60, "minute")
            diff < 86400 -> ago(diff / 3600, "hour")
            diff < 604800 -> ago(diff / 86400, "day")
            else -> ago(diff / 604800, "week")
        }
    }

    private fun ago(n: Long, unit: String) = if (n == 1L) "1 $unit ago" else "$n ${unit}s ago"
}
