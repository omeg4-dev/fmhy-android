package io.github.omeg4dev.fmhy.ui.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.browser.adblock.AdblockStatus
import io.github.omeg4dev.fmhy.data.adblock.ListState
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
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.user.Follow
import io.github.omeg4dev.fmhy.data.user.ImportResult
import io.github.omeg4dev.fmhy.data.user.Pin
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.ui.common.FollowTree
import io.github.omeg4dev.fmhy.ui.shell.JobRowUi
import io.github.omeg4dev.fmhy.ui.shell.SYNC_CANCELLED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

    val jobs: StateFlow<List<JobRowUi>> = runCatching { container.statusNotices.jobs }
        .getOrElse { MutableStateFlow(emptyList()) }

    private val _isUpdating = MutableStateFlow(false)
    private val _updateStatusMessage = MutableStateFlow<String?>(null)

    private val _previewEntry = MutableStateFlow(DEFAULT_PREVIEW_ENTRY)
    val previewEntry: StateFlow<Entry> = _previewEntry.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val candidates = store.findLookalikeCandidates("uBlock Origin")
                val hit = candidates.firstOrNull { it.name.contains("uBlock Origin", ignoreCase = true) && it.tier != Tier.NORMAL }
                    ?: candidates.firstOrNull { it.name.contains("uBlock Origin", ignoreCase = true) }
                    ?: candidates.firstOrNull { it.tier != Tier.NORMAL }
                    ?: candidates.firstOrNull()
                if (hit != null) {
                    _previewEntry.value = hit
                }
            } catch (_: Exception) {
                // Keep default stub entry
            }
        }
    }

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

    val pins: StateFlow<List<Pin>> = container.userStore.changes
        .map {
            try { container.userStore.pins() } catch (_: Exception) { emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hiddenSections: StateFlow<Set<String>> = container.userStore.changes
        .map {
            try { container.userStore.hiddenSections() } catch (_: Exception) { emptySet() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val follows: StateFlow<List<Follow>> = container.userStore.changes
        .map {
            try { container.userStore.follows() } catch (_: Exception) { emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val followedSectionsCount: StateFlow<Int> = combine(
        container.userStore.changes,
        store.changes,
    ) { _, _ ->
        try {
            val follows = container.userStore.follows()
            val tree = FollowTree(follows)
            if (tree.isEmpty) {
                0
            } else {
                val sections = try { store.sections() } catch (_: Exception) { emptyList() }
                val headings = try { store.allHeadings() } catch (_: Exception) { emptyList() }
                val headingsBySec = headings.groupBy { it.sectionId }
                val subMap = sections.associate { sec ->
                    val secHeadings = headingsBySec[sec.id].orEmpty()
                    val hasLevel1 = secHeadings.any { it.level == 1 }
                    val topLevel = when {
                        hasLevel1 -> 1
                        secHeadings.any { it.level == 2 } -> 2
                        else -> 0
                    }
                    sec.id to (if (topLevel == 0) emptyList() else secHeadings.filter { it.level == topLevel }.map { it.id })
                }
                if (subMap.isEmpty()) {
                    follows.filter { !it.excluded }.map { it.sectionId }.distinct().size
                } else {
                    tree.followedCount(subMap)
                }
            }
        } catch (_: Exception) {
            0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val savedCount: StateFlow<Int> = container.userStore.changes
        .map {
            try { container.userStore.bookmarks().size } catch (_: Exception) { 0 }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val adblockStatus: StateFlow<AdblockStatus> = runCatching {
        container.adblockService.status
    }.getOrElse {
        MutableStateFlow(AdblockStatus())
    }

    val listStates: StateFlow<Map<String, ListState>> = runCatching {
        container.adblockService.listStates
    }.getOrElse {
        MutableStateFlow(emptyMap())
    }

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()

    val allSections: StateFlow<List<Section>> = flow {
        val list = try { store.sections(SectionOrder.WEBSITE) } catch (_: Exception) { emptyList() }
        emit(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun movePinUp(index: Int) {
        val current = pins.value.toMutableList()
        if (index > 0 && index < current.size) {
            val temp = current[index]
            current[index] = current[index - 1]
            current[index - 1] = temp
            viewModelScope.launch {
                container.userStore.reorderPins(current.map { it.sectionId to it.headingId })
            }
        }
    }

    fun movePinDown(index: Int) {
        val current = pins.value.toMutableList()
        if (index >= 0 && index < current.size - 1) {
            val temp = current[index]
            current[index] = current[index + 1]
            current[index + 1] = temp
            viewModelScope.launch {
                container.userStore.reorderPins(current.map { it.sectionId to it.headingId })
            }
        }
    }

    fun removePin(sectionId: String, headingId: String) {
        viewModelScope.launch {
            container.userStore.unpin(sectionId, headingId)
        }
    }

    fun setSectionHidden(sectionId: String, hidden: Boolean) {
        viewModelScope.launch {
            container.userStore.setSectionHidden(sectionId, hidden)
        }
    }

    fun unfollow(sectionId: String, headingId: String) {
        viewModelScope.launch {
            try {
                container.userStore.unfollow(sectionId, headingId)
            } catch (_: Exception) {}
        }
    }

    fun updateAdblockNow() {
        container.startFilterDownload()
    }

    fun checkForUpdates(onResult: (String) -> Unit) {
        if (_isCheckingUpdates.value) return
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            try {
                val outcome = io.github.omeg4dev.fmhy.work.UpdateChecker.run(container.context, container)
                onResult(outcome.summary)
            } catch (e: Exception) {
                onResult("Check failed: ${e.message}")
            } finally {
                _isCheckingUpdates.value = false
            }
        }
    }

    fun clearBrowsingData(
        clearCookiesAndStorage: Boolean,
        clearCache: Boolean,
        clearHistory: Boolean,
        context: Context,
        onFinished: () -> Unit,
    ) {
        viewModelScope.launch {
            if (clearCookiesAndStorage) {
                try {
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    android.webkit.WebStorage.getInstance().deleteAllData()
                    runCatching { android.webkit.WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword() }
                    @Suppress("DEPRECATION")
                    runCatching { android.webkit.WebViewDatabase.getInstance(context).clearFormData() }
                } catch (_: Exception) {}
            }
            if (clearCache) {
                try {
                    android.webkit.WebView(context).clearCache(true)
                } catch (_: Exception) {}
            }
            if (clearHistory) {
                try {
                    container.userStore.clearHistory()
                } catch (_: Exception) {}
            }
            onFinished()
        }
    }

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

    /** Forgets every per-site playback speed. Calls [done] with the number removed. */
    fun clearSiteSpeeds(done: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val n = try { container.userStore.deleteKvPrefix("video_speed:") } catch (_: Exception) { 0 }
            done(n)
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

    fun setWebsiteStyle(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWebsiteStyle(enabled)
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
                    is SyncResult.Failed -> if (result.reason == SYNC_CANCELLED) "Update cancelled" else "Update failed: ${result.reason}"
                }
                _updateStatusMessage.value = msg
            } catch (e: Exception) {
                _updateStatusMessage.value = "Error: ${e.message}"
            } finally {
                _isUpdating.value = false
            }
        }
    }

    fun formatRelativeTime(seconds: Long): String {
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

val DEFAULT_PREVIEW_ENTRY = Entry(
    id = "ublock-origin-preview",
    num = 1,
    sectionId = "adblocking",
    headingId = null,
    ord = 1,
    name = "uBlock Origin",
    url = "https://ublockorigin.com",
    originalUrl = null,
    description = "Ad blocker / element blocker",
    tags = emptyList(),
    headingPath = "Adblocking",
    tier = Tier.STAR,
    kind = EntryKind.NORMAL,
    line = 1,
    isInlineMore = false,
    isBase64 = false,
    mirrors = emptyList(),
    extraLinks = emptyList(),
    firstSeen = 0L,
    lastChanged = 0L,
    starredAt = null,
)
