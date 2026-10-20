package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.AppIcon
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.BlockingPrefs
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.DataPrefs
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.HomePrefs
import io.github.omeg4dev.fmhy.data.NotificationPrefs
import io.github.omeg4dev.fmhy.data.PrivacyPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.SectionOrder
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.sync.SyncResult
import io.github.omeg4dev.fmhy.data.sync.SyncState
import io.github.omeg4dev.fmhy.data.user.ImportResult
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
            settingsRepository.appearance,
        ) { version, appearance ->
            Pair(version, appearance)
        },
        _isUpdating,
        _updateStatusMessage,
    ) { (version, appearance), updating, statusMsg ->
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
            isUpdating = updating,
            updateStatusMessage = statusMsg,
            appearance = appearance,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsUiState()
    )

    val homePrefs: StateFlow<HomePrefs> = settingsRepository.homePrefs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        HomePrefs()
    )

    val browserPrefs: StateFlow<BrowserPrefs> = settingsRepository.browserPrefs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BrowserPrefs()
    )

    val blockingPrefs: StateFlow<BlockingPrefs> = settingsRepository.blockingPrefs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BlockingPrefs()
    )

    val protectionPrefs: StateFlow<ProtectionPrefs> = settingsRepository.protectionPrefs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ProtectionPrefs()
    )

    val privacyPrefs: StateFlow<PrivacyPrefs> = settingsRepository.privacyPrefs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PrivacyPrefs()
    )

    val notificationPrefs: StateFlow<NotificationPrefs> = settingsRepository.notificationPrefs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        NotificationPrefs()
    )

    val dataPrefs: StateFlow<DataPrefs> = settingsRepository.dataPrefs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DataPrefs()
    )

    fun updateHome(f: (HomePrefs) -> HomePrefs) {
        viewModelScope.launch {
            settingsRepository.updateHome(f)
        }
    }

    fun updateBrowser(f: (BrowserPrefs) -> BrowserPrefs) {
        viewModelScope.launch {
            settingsRepository.updateBrowser(f)
        }
    }

    fun updateBlocking(f: (BlockingPrefs) -> BlockingPrefs) {
        viewModelScope.launch {
            settingsRepository.updateBlocking(f)
        }
    }

    fun updateProtection(f: (ProtectionPrefs) -> ProtectionPrefs) {
        viewModelScope.launch {
            settingsRepository.updateProtection(f)
        }
    }

    fun updatePrivacy(f: (PrivacyPrefs) -> PrivacyPrefs) {
        viewModelScope.launch {
            settingsRepository.updatePrivacy(f)
        }
    }

    fun updateNotifications(f: (NotificationPrefs) -> NotificationPrefs) {
        viewModelScope.launch {
            settingsRepository.updateNotifications(f)
        }
    }

    fun updateData(f: (DataPrefs) -> DataPrefs) {
        viewModelScope.launch {
            settingsRepository.updateData(f)
        }
    }

    fun setAppIcon(icon: AppIcon) {
        viewModelScope.launch {
            settingsRepository.setAppIcon(icon)
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

    suspend fun exportBackup(includeHistory: Boolean): String {
        return container.userStore.exportJson(includeHistory)
    }

    suspend fun importBackup(json: String): ImportResult {
        return container.userStore.importJson(json)
    }

    suspend fun clearHistory() {
        container.userStore.clearHistory()
    }

    fun setOnboardingVersion(v: Int) {
        viewModelScope.launch {
            settingsRepository.setOnboardingVersion(v)
        }
    }
}
