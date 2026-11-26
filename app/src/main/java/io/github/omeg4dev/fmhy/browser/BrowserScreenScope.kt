package io.github.omeg4dev.fmhy.browser

/** Scope holding state and dependencies for [BrowserScreen]. */

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.AdblockStatus
import io.github.omeg4dev.fmhy.browser.download.ActiveSummary
import io.github.omeg4dev.fmhy.browser.download.DownloadController
import io.github.omeg4dev.fmhy.browser.download.DownloadEvent
import io.github.omeg4dev.fmhy.browser.download.DownloadOffer
import io.github.omeg4dev.fmhy.data.Bookmark
import io.github.omeg4dev.fmhy.data.BookmarksRepository
import io.github.omeg4dev.fmhy.data.BlockingPrefs
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.CheckPrefs
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.UpdatePrefs
import io.github.omeg4dev.fmhy.data.search.SearchEngine as DatasetSearchEngine
import io.github.omeg4dev.fmhy.data.user.Folder
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.ui.common.AppHaptics
import io.github.omeg4dev.fmhy.ui.common.BookmarksState
import io.github.omeg4dev.fmhy.work.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

@Stable
internal data class BrowserScreenScope(
    val tabManager: TabManager,
    val adblockService: AdblockService,
    val settingsRepository: SettingsRepository,
    val bookmarksRepository: BookmarksRepository,
    val datasetSearchEngine: DatasetSearchEngine,
    val datasetStore: DatasetStore,
    val browserHooks: BrowserHooks,
    val isAppDark: Boolean,
    val isInPip: Boolean,
    val onFinishActivity: () -> Unit,
    val modifier: Modifier,
    val context: Context,
    val density: Density,
    val scope: CoroutineScope,
    val haptics: AppHaptics,
    val tabsRef: State<List<TabState>>,
    val currentTabIdRef: State<Long?>,
    val webViewVersionRef: State<Int>,
    val adblockStatusRef: State<AdblockStatus>,
    val browserPrefsRef: State<BrowserPrefs>,
    val blockingPrefsRef: State<BlockingPrefs>,
    val protectionPrefsRef: State<ProtectionPrefs>,
    val checkPrefsRef: State<CheckPrefs>,
    val currentTab: TabState?,
    val isBlankTab: Boolean,
    val isEditModeRef: MutableState<Boolean>,
    val showShieldSheetRef: MutableState<Boolean>,
    val showConnectionSheetRef: MutableState<Boolean>,
    val showBlockedSheetRef: MutableState<Boolean>,
    val showTabsSheetRef: MutableState<Boolean>,
    val showDownloadsSheetRef: MutableState<Boolean>,
    val showVideoSheetRef: MutableState<Boolean>,
    val showTabsRequestedRef: State<Boolean>,
    val showDownloadsRequestedRef: State<Boolean>,
    val isFindModeRef: MutableState<Boolean>,
    val findQueryRef: MutableState<String>,
    val findActiveIndexRef: MutableIntState,
    val findTotalMatchesRef: MutableIntState,
    val pendingHideElementRuleRef: MutableState<String?>,
    val pendingExternalAppRef: MutableState<Pair<Intent, String>?>,
    val pendingLinkMenuRef: MutableState<LinkMenuRequest?>,
    val showPictureChoiceRef: MutableState<Boolean>,
    val captureJobRef: MutableState<Job?>,
    val captureProgressRef: MutableState<Pair<Int, Int>>,
    val isCapturing: Boolean,
    val isCapturingFullPageRef: MutableState<Boolean>,
    val capturedTabIdRef: MutableState<Long?>,
    val capturedTabUrlRef: MutableState<String?>,
    val savedIdsRef: State<Set<String>>,
    val isSaved: Boolean,
    val hooks: DefaultBrowserHooks?,
    val snackbarHostState: SnackbarHostState,
    val view: View,
    val blockedStates: SnapshotStateMap<Long, BlockedBarState>,
    val pulseKeyRef: MutableIntState,
    val blockedBarHeightPxRef: MutableIntState,
    val currentBlockedState: BlockedBarState?,
    val isBlockedBarVisible: Boolean,
    val onStartPictureCapture: (Boolean) -> Unit,
    val customView: View?,
    val container: AppContainer,
    val vpnStateRef: State<VpnState>,
    val connectionVpn: VpnState?,
    val orbotProxy: Boolean,
    val downloadController: DownloadController,
    val downloadSummaryRef: State<ActiveSummary?>,
    val updatePrefsRef: State<UpdatePrefs>,
    val showKeyPromptDialogRef: MutableState<Boolean>,
    val redirectWarningEventRef: MutableState<DownloadEvent.RedirectWarning?>,
    val notificationPermissionLauncher: ActivityResultLauncher<String>,
    val notificationPermissionRequestedThisSessionRef: MutableState<Boolean>,
    val pendingDownload: DownloadOffer?,
    val offerAcceptedRef: MutableState<Boolean>,
    val bookmarkListRef: State<List<Bookmark>>,
    val folderListRef: State<List<Folder>>,
    val bookmarksState: BookmarksState,
    val totalBarHeightPxRef: MutableFloatState,
    val toolbarTranslation: Animatable<Float, AnimationVector1D>,
    val snapJobRef: MutableState<Job?>,
    val bars: BarVisibilityState,
    val barTracker: BarScrollTracker,
    val isTopBar: Boolean,
    val applyBars: () -> Unit,
    val applyBarsStateRef: State<() -> Unit>,
    val loadingStateRef: State<Boolean>,
    val shouldForceToolbarShown: Boolean,
    val navKey: String?,
    val latestTabIdRef: State<Long?>,
    val prevTabIdRef: MutableState<Long?>,
    val prevNavKeyRef: MutableState<String?>,
    val currentTabIds: Set<Long>,
    val tabNoticeJobRef: MutableState<Job?>,
    val restoredRef: State<Boolean>,
    val leaveToFmhy: () -> Unit,
    val currentBackAction: BackAction,
    val predictiveBackEnabled: Boolean,
    val backScale: Animatable<Float, AnimationVector1D>,
    val backAlpha: Animatable<Float, AnimationVector1D>,
    val backRadius: Animatable<Float, AnimationVector1D>,
    val backTranslateX: Animatable<Float, AnimationVector1D>,
) {
    val tabs: List<TabState> by tabsRef
    val currentTabId: Long? by currentTabIdRef
    val webViewVersion: Int by webViewVersionRef
    val adblockStatus: AdblockStatus by adblockStatusRef
    val browserPrefs: BrowserPrefs by browserPrefsRef
    val blockingPrefs: BlockingPrefs by blockingPrefsRef
    val protectionPrefs: ProtectionPrefs by protectionPrefsRef
    val checkPrefs: CheckPrefs by checkPrefsRef
    var isEditMode: Boolean by isEditModeRef
    var showShieldSheet: Boolean by showShieldSheetRef
    var showConnectionSheet: Boolean by showConnectionSheetRef
    var showBlockedSheet: Boolean by showBlockedSheetRef
    var showTabsSheet: Boolean by showTabsSheetRef
    var showDownloadsSheet: Boolean by showDownloadsSheetRef
    var showVideoSheet: Boolean by showVideoSheetRef
    val showTabsRequested: Boolean by showTabsRequestedRef
    val showDownloadsRequested: Boolean by showDownloadsRequestedRef
    var isFindMode: Boolean by isFindModeRef
    var findQuery: String by findQueryRef
    var findActiveIndex: Int by findActiveIndexRef
    var findTotalMatches: Int by findTotalMatchesRef
    var pendingHideElementRule: String? by pendingHideElementRuleRef
    var pendingExternalApp: Pair<Intent, String>? by pendingExternalAppRef
    var pendingLinkMenu: LinkMenuRequest? by pendingLinkMenuRef
    var showPictureChoice: Boolean by showPictureChoiceRef
    var captureJob: Job? by captureJobRef
    var captureProgress: Pair<Int, Int> by captureProgressRef
    var isCapturingFullPage: Boolean by isCapturingFullPageRef
    var capturedTabId: Long? by capturedTabIdRef
    var capturedTabUrl: String? by capturedTabUrlRef
    val savedIds: Set<String> by savedIdsRef
    var pulseKey: Int by pulseKeyRef
    var blockedBarHeightPx: Int by blockedBarHeightPxRef
    val vpnState: VpnState by vpnStateRef
    val downloadSummary: ActiveSummary? by downloadSummaryRef
    val updatePrefs: UpdatePrefs by updatePrefsRef
    var showKeyPromptDialog: Boolean by showKeyPromptDialogRef
    var redirectWarningEvent: DownloadEvent.RedirectWarning? by redirectWarningEventRef
    var notificationPermissionRequestedThisSession: Boolean by notificationPermissionRequestedThisSessionRef
    var offerAccepted: Boolean by offerAcceptedRef
    val bookmarkList: List<Bookmark> by bookmarkListRef
    val folderList: List<Folder> by folderListRef
    var totalBarHeightPx: Float by totalBarHeightPxRef
    var snapJob: Job? by snapJobRef
    val applyBarsState: () -> Unit by applyBarsStateRef
    val loadingState: Boolean by loadingStateRef
    val latestTabId: Long? by latestTabIdRef
    var prevTabId: Long? by prevTabIdRef
    var prevNavKey: String? by prevNavKeyRef
    var tabNoticeJob: Job? by tabNoticeJobRef
    val restored: Boolean by restoredRef

    fun stateFor(tabId: Long): BlockedBarState = blockedStates.getOrPut(tabId) { BlockedBarState() }
}
