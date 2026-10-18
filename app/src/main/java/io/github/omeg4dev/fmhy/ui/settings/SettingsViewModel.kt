package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
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
)

class SettingsViewModel(
    private val store: DatasetStore,
    private val settingsRepository: SettingsRepository,
    private val container: AppContainer,
) : ViewModel() {

    private val _isUpdating = MutableStateFlow(false)
    private val _updateStatusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        store.changes,
        settingsRepository.openInExternalBrowser,
        _isUpdating,
        _updateStatusMessage,
    ) { version, openExternal, updating, statusMsg ->
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
            diff < 3600 -> "${diff / 60} minutes ago"
            diff < 86400 -> "${diff / 3600} hours ago"
            diff < 604800 -> "${diff / 86400} days ago"
            else -> "${diff / 604800} weeks ago"
        }
    }
}
