package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.sync.SyncResult
import io.github.omeg4dev.fmhy.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val datasetVersion: Long = 0L,
    val lastUpdatedText: String = "",
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
        val meta = try { store.meta() } catch (_: Exception) { emptyMap() }
        val createdTs = meta["created"]?.toLongOrNull() ?: 0L
        val lastUpdatedStr = if (createdTs > 0L) {
            formatRelativeTime(createdTs)
        } else {
            "Never"
        }

        SettingsUiState(
            datasetVersion = version,
            lastUpdatedText = lastUpdatedStr,
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
                val result = container.datasetSync.sync(force = true)
                val msg = when (result) {
                    is SyncResult.Skipped -> "Skipped (recent update check)"
                    is SyncResult.NotModified -> "Already up to date (not modified)"
                    is SyncResult.UpToDate -> "Already up to date"
                    is SyncResult.Updated -> "Updated to v${result.to} (+${result.added} new sites)"
                    is SyncResult.Replaced -> "Dataset replaced with v${result.to}"
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
