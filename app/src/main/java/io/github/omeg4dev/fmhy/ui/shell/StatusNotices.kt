package io.github.omeg4dev.fmhy.ui.shell

import androidx.compose.runtime.Immutable
import io.github.omeg4dev.fmhy.browser.adblock.AdblockStatus
import io.github.omeg4dev.fmhy.data.sync.SyncResult
import io.github.omeg4dev.fmhy.di.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
sealed interface AdblockSetupUiState {
    data class Downloading(val progress: Float?) : AdblockSetupUiState
    data object Ready : AdblockSetupUiState
    data object Failed : AdblockSetupUiState
}

@Immutable
data class SyncBannerUiState(
    val text: String,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
)

/** Banner for a finished sync; null when nothing should be shown (sync was skipped). */
fun syncBannerFor(result: SyncResult): SyncBannerUiState? = when (result) {
    is SyncResult.Skipped -> null
    is SyncResult.UpToDate -> SyncBannerUiState("Synced in background · up to date")
    is SyncResult.Updated -> SyncBannerUiState(
        when {
            result.added > 0 && result.removed > 0 ->
                "Synced in background · ${result.added} new · ${result.removed} removed"
            result.added > 0 -> "Synced in background · ${result.added} new"
            result.removed > 0 -> "Synced in background · ${result.removed} removed"
            else -> "Synced in background · updated"
        }
    )
    is SyncResult.Failed -> SyncBannerUiState("Offline — showing saved data", isError = true)
}

/** Next ad-block pill state from the current one, the setting and the service status. */
fun nextAdblockState(current: AdblockSetupUiState?, enabled: Boolean, status: AdblockStatus): AdblockSetupUiState? = when {
    !enabled -> null
    status.updating -> AdblockSetupUiState.Downloading(status.progress)
    current is AdblockSetupUiState.Downloading ->
        if (status.error != null) AdblockSetupUiState.Failed else AdblockSetupUiState.Ready
    status.error != null && !status.ready -> AdblockSetupUiState.Failed
    else -> current
}

/**
 * App-wide status pills (background sync, ad-block list download). Lives in the app container so
 * they are shown as an overlay on every screen instead of inside Home's list. Timing rules: the
 * "Syncing…" pill appears after [SYNC_SHOW_DELAY_MS] (fast syncs never flicker), result pills
 * disappear after [SYNC_RESULT_MS], "ready" after [ADBLOCK_READY_MS].
 */
class StatusNotices(
    private val scope: CoroutineScope,
    syncStatus: Flow<SyncStatus>,
    adblockInputs: Flow<Pair<Boolean, AdblockStatus>>?,
    private val isSyncBannerConsumed: () -> Boolean,
    private val markSyncBannerConsumed: () -> Unit,
    private val startFilterDownload: () -> Unit,
) {
    private val _sync = MutableStateFlow<SyncBannerUiState?>(null)
    val sync: StateFlow<SyncBannerUiState?> = _sync.asStateFlow()

    private val _adblock = MutableStateFlow<AdblockSetupUiState?>(null)
    val adblock: StateFlow<AdblockSetupUiState?> = _adblock.asStateFlow()

    private var syncingJob: Job? = null
    private var autoHideJob: Job? = null
    private var readyDismissJob: Job? = null

    init {
        scope.launch { syncStatus.collect(::handleSyncStatus) }
        if (adblockInputs != null) {
            scope.launch { adblockInputs.collect { (enabled, status) -> handleAdblock(enabled, status) } }
        }
    }

    private fun handleSyncStatus(status: SyncStatus) {
        when (status) {
            is SyncStatus.Idle -> Unit
            is SyncStatus.Syncing -> {
                syncingJob?.cancel()
                autoHideJob?.cancel()
                syncingJob = scope.launch {
                    delay(SYNC_SHOW_DELAY_MS)
                    _sync.value = SyncBannerUiState(text = "Syncing…", isLoading = true)
                }
            }
            is SyncStatus.Finished -> {
                syncingJob?.cancel()
                autoHideJob?.cancel()
                val banner = if (isSyncBannerConsumed()) null else syncBannerFor(status.result)
                _sync.value = banner
                if (banner != null) {
                    autoHideJob = scope.launch {
                        delay(SYNC_RESULT_MS)
                        _sync.value = null
                        markSyncBannerConsumed()
                    }
                }
            }
        }
    }

    fun dismissSync() {
        autoHideJob?.cancel()
        syncingJob?.cancel()
        _sync.value = null
        markSyncBannerConsumed()
    }

    private fun handleAdblock(enabled: Boolean, status: AdblockStatus) {
        val before = _adblock.value
        val next = nextAdblockState(before, enabled, status)
        _adblock.value = next
        if (next !is AdblockSetupUiState.Ready) readyDismissJob?.cancel()
        if (next is AdblockSetupUiState.Ready && before !is AdblockSetupUiState.Ready) {
            readyDismissJob?.cancel()
            readyDismissJob = scope.launch {
                delay(ADBLOCK_READY_MS)
                _adblock.value = null
            }
        }
    }

    /** Tap on the failed ad-block pill. */
    fun retryAdblock() {
        _adblock.value = AdblockSetupUiState.Downloading(null)
        startFilterDownload()
    }

    companion object {
        const val SYNC_SHOW_DELAY_MS = 600L
        const val SYNC_RESULT_MS = 4000L
        const val ADBLOCK_READY_MS = 2000L
    }
}
