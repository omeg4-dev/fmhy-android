package io.github.omeg4dev.fmhy.browser

/** State holder and remember function for [BrowserScreenScope]. */

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.browser.download.DownloadEvent
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.data.BookmarksRepository
import io.github.omeg4dev.fmhy.data.CheckPrefs
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.ProxyMode
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ToolbarPosition
import io.github.omeg4dev.fmhy.data.UpdatePrefs
import io.github.omeg4dev.fmhy.data.search.SearchEngine as DatasetSearchEngine
import io.github.omeg4dev.fmhy.ui.common.BookmarksState
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.SavedInfo
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.work.VpnPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun rememberBrowserScreenScope(
    tabManager: TabManager,
    adblockService: AdblockService,
    settingsRepository: SettingsRepository,
    bookmarksRepository: BookmarksRepository,
    datasetSearchEngine: DatasetSearchEngine,
    datasetStore: DatasetStore,
    browserHooks: BrowserHooks,
    isAppDark: Boolean,
    isInPip: Boolean = false,
    onFinishActivity: () -> Unit,
    modifier: Modifier = Modifier,
): BrowserScreenScope {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    val tabsRef = tabManager.tabs.collectAsStateWithLifecycle()
    val tabs by tabsRef
    val currentTabIdRef = tabManager.currentTabId.collectAsStateWithLifecycle()
    val currentTabId by currentTabIdRef
    val webViewVersionRef = tabManager.webViewVersion.collectAsStateWithLifecycle()
    val webViewVersion by webViewVersionRef
    val adblockStatusRef = adblockService.status.collectAsStateWithLifecycle()
    val adblockStatus by adblockStatusRef

    val browserPrefsRef = settingsRepository.browserPrefs.collectAsStateWithLifecycle(initialValue = tabManager.currentBrowserPrefs)
    val browserPrefs by browserPrefsRef
    val blockingPrefsRef = settingsRepository.blockingPrefs.collectAsStateWithLifecycle(initialValue = adblockService.currentBlockingPrefs)
    val blockingPrefs by blockingPrefsRef
    val protectionPrefsRef = settingsRepository.protectionPrefs.collectAsStateWithLifecycle(initialValue = tabManager.currentProtectionPrefs)
    val protectionPrefs by protectionPrefsRef
    val checkPrefsRef = settingsRepository.checkPrefs.collectAsStateWithLifecycle(initialValue = CheckPrefs())
    val checkPrefs by checkPrefsRef

    val currentTab = remember(tabs, currentTabId) {
        tabs.firstOrNull { it.id == currentTabId }
    }
    val isBlankTab = currentTab == null || currentTab.url == "about:blank" || currentTab.url.isEmpty()

    // UI state
    val isEditModeRef = remember { mutableStateOf(false) }
    var isEditMode by isEditModeRef
    val showShieldSheetRef = remember { mutableStateOf(false) }
    var showShieldSheet by showShieldSheetRef
    val showConnectionSheetRef = remember { mutableStateOf(false) }
    var showConnectionSheet by showConnectionSheetRef
    val showBlockedSheetRef = remember { mutableStateOf(false) }
    var showBlockedSheet by showBlockedSheetRef
    val showTabsSheetRef = remember { mutableStateOf(false) }
    var showTabsSheet by showTabsSheetRef
    val showDownloadsSheetRef = remember { mutableStateOf(false) }
    var showDownloadsSheet by showDownloadsSheetRef
    val showVideoSheetRef = remember { mutableStateOf(false) }
    var showVideoSheet by showVideoSheetRef
    if (showVideoSheet && currentTab?.videoSources.isNullOrEmpty()) {
        showVideoSheet = false
    }
    val showTabsRequestedRef = tabManager.showTabsRequested.collectAsStateWithLifecycle()
    val showTabsRequested by showTabsRequestedRef
    val showDownloadsRequestedRef = tabManager.showDownloadsRequested.collectAsStateWithLifecycle()
    val showDownloadsRequested by showDownloadsRequestedRef

    // Find in page state
    val isFindModeRef = remember { mutableStateOf(false) }
    var isFindMode by isFindModeRef
    val findQueryRef = remember { mutableStateOf("") }
    var findQuery by findQueryRef
    val findActiveIndexRef = remember { mutableIntStateOf(0) }
    var findActiveIndex by findActiveIndexRef
    val findTotalMatchesRef = remember { mutableIntStateOf(0) }
    var findTotalMatches by findTotalMatchesRef

    // Dialogs state
    val pendingHideElementRuleRef = remember { mutableStateOf<String?>(null) }
    var pendingHideElementRule by pendingHideElementRuleRef
    val pendingExternalAppRef = remember { mutableStateOf<Pair<Intent, String>?>(null) }
    var pendingExternalApp by pendingExternalAppRef
    val pendingLinkMenuRef = remember { mutableStateOf<LinkMenuRequest?>(null) }
    var pendingLinkMenu by pendingLinkMenuRef

    // Page capture state
    val showPictureChoiceRef = remember { mutableStateOf(false) }
    var showPictureChoice by showPictureChoiceRef
    val captureJobRef = remember { mutableStateOf<Job?>(null) }
    var captureJob by captureJobRef
    val captureProgressRef = remember { mutableStateOf(0 to 0) }
    var captureProgress by captureProgressRef
    val isCapturing = captureJob != null
    val isCapturingFullPageRef = remember { mutableStateOf(false) }
    var isCapturingFullPage by isCapturingFullPageRef
    val capturedTabIdRef = remember { mutableStateOf<Long?>(null) }
    var capturedTabId by capturedTabIdRef
    val capturedTabUrlRef = remember { mutableStateOf<String?>(null) }
    var capturedTabUrl by capturedTabUrlRef

    val savedIdsRef = bookmarksRepository.savedIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val savedIds by savedIdsRef
    val isSaved = currentTab?.entryId != null && savedIds.contains(currentTab.entryId)

    val hooks = browserHooks as? DefaultBrowserHooks
    val snackbarHostState = remember { SnackbarHostState() }
    val view = LocalView.current
    val blockedStates = remember { mutableStateMapOf<Long, BlockedBarState>() }
    fun stateFor(tabId: Long): BlockedBarState = blockedStates.getOrPut(tabId) { BlockedBarState() }
    val pulseKeyRef = remember { mutableIntStateOf(0) }
    var pulseKey by pulseKeyRef
    val blockedBarHeightPxRef = remember { mutableIntStateOf(0) }
    var blockedBarHeightPx by blockedBarHeightPxRef
    val currentBlockedState = currentTabId?.let { stateFor(it) }
    val isBlockedBarVisible = currentBlockedState != null && currentBlockedState.visible && !isInPip && hooks?.customView == null

    val onStartPictureCapture: (Boolean) -> Unit = { fullPage ->
        val tab = currentTab
        val webView = tab?.let { tabManager.getTabRecord(it.id)?.webView }
        if (tab == null || webView == null) {
            scope.launch {
                snackbarHostState.showSnackbar("Couldn't save the picture")
            }
        } else {
            isCapturingFullPage = fullPage
            capturedTabId = tab.id
            capturedTabUrl = tab.url.substringBefore('#')
            captureProgress = 0 to 0

            captureJob = scope.launch {
                try {
                    val result = if (fullPage) {
                        captureFullPage(webView) { d, t ->
                            captureProgress = d to t
                        }
                    } else {
                        CaptureResult.Done(captureVisible(webView), truncated = false)
                    }

                    val bitmap = when (result) {
                        is CaptureResult.Done -> result.bitmap
                        is CaptureResult.InnerScroll -> result.bitmap
                    }

                    val uri = try {
                        val fileName = pictureFileName(tab.url, System.currentTimeMillis())
                        PictureStore.save(context, bitmap, fileName)
                    } finally {
                        bitmap.recycle()
                    }

                    val message = when {
                        result is CaptureResult.InnerScroll -> "This page scrolls inside a frame: saved the visible area"
                        result is CaptureResult.Done && result.truncated -> "Page too long: saved the top part"
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> "Picture saved to Pictures/FMHY"
                        else -> "Picture saved"
                    }

                    // Close the progress dialog first: a snackbar with an action waits until it is dismissed.
                    captureJob = null
                    isCapturingFullPage = false
                    scope.launch {
                        val action = snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = "View",
                            duration = SnackbarDuration.Long,
                        )
                        if (action == SnackbarResult.ActionPerformed) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "image/png")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                snackbarHostState.showSnackbar("No app to open pictures")
                            } catch (_: Throwable) {
                                snackbarHostState.showSnackbar("Couldn't open picture")
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    snackbarHostState.showSnackbar("Couldn't save the picture")
                } finally {
                    captureJob = null
                    isCapturingFullPage = false
                    capturedTabId = null
                    capturedTabUrl = null
                }
            }
        }
    }
    val customView = hooks?.customView

    val container = remember(context) { (context.applicationContext as FmhyApp).container }
    val vpnStateRef = container.vpnMonitor.state.collectAsStateWithLifecycle()
    val vpnState by vpnStateRef
    val connectionVpn = VpnPolicy.markFor(vpnState, protectionPrefs.showVpnState)
    val orbotProxy = protectionPrefs.proxyMode == ProxyMode.ORBOT
    val downloadController = container.downloads
    val downloadSummaryRef = downloadController.activeSummary.collectAsStateWithLifecycle()
    val downloadSummary by downloadSummaryRef
    val updatePrefsRef = settingsRepository.updatePrefs.collectAsStateWithLifecycle(initialValue = UpdatePrefs())
    val updatePrefs by updatePrefsRef

    val showKeyPromptDialogRef = remember { mutableStateOf(false) }
    var showKeyPromptDialog by showKeyPromptDialogRef
    val redirectWarningEventRef = remember { mutableStateOf<DownloadEvent.RedirectWarning?>(null) }
    var redirectWarningEvent by redirectWarningEventRef

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore result */ }
    val notificationPermissionRequestedThisSessionRef = remember { mutableStateOf(false) }
    var notificationPermissionRequestedThisSession by notificationPermissionRequestedThisSessionRef

    val pendingDownload = hooks?.pendingDownload
    val offerAcceptedRef = remember(pendingDownload) { mutableStateOf(false) }
    var offerAccepted by offerAcceptedRef

    val bookmarkListRef = bookmarksRepository.bookmarks.collectAsStateWithLifecycle(initialValue = emptyList())
    val bookmarkList by bookmarkListRef
    val folderListRef = bookmarksRepository.folders.collectAsStateWithLifecycle(initialValue = emptyList())
    val folderList by folderListRef
    val bookmarksState = remember(savedIds, bookmarkList, folderList) {
        val folderMap = folderList.associate { it.id to it.name }
        val byId = bookmarkList.associateBy { it.entryId }
        BookmarksState(
            savedIds = savedIds,
            toggle = { entry -> scope.launch { bookmarksRepository.toggle(entry, entry.headingPath) } },
            folders = folderList,
            savedInfo = { id ->
                byId[id]?.let { SavedInfo(it.folderId, it.folderId?.let { f -> folderMap[f] }, it.note) }
            },
            setNote = { id, note -> scope.launch { bookmarksRepository.setNote(id, note) } },
            moveToFolder = { id, folder -> scope.launch { bookmarksRepository.moveToFolder(id, folder) } },
            createFolder = { name, onCreated -> scope.launch { onCreated(bookmarksRepository.createFolder(name)) } },
        )
    }

    // Toolbar overlay measurement and animation
    val totalBarHeightPxRef = remember { mutableFloatStateOf(0f) }
    var totalBarHeightPx by totalBarHeightPxRef
    val toolbarTranslation = remember { Animatable(0f) }
    val snapJobRef = remember { mutableStateOf<Job?>(null) }
    var snapJob by snapJobRef
    val bars = remember { BarVisibilityState() }
    val barTracker = remember(density) {
        BarScrollTracker(hideThresholdPx = with(density) { 48.dp.toPx() }, showThresholdPx = with(density) { 32.dp.toPx() })
    }
    val isTopBar = browserPrefs.toolbarPosition == ToolbarPosition.TOP
    val applyBars: () -> Unit = {
        val hiddenValue = if (browserPrefs.toolbarPosition == ToolbarPosition.TOP) -totalBarHeightPx else totalBarHeightPx
        val target = if (bars.hidden) hiddenValue else 0f
        if (toolbarTranslation.targetValue != target) {
            barsLog("target ${toolbarTranslation.targetValue} -> $target pointerDown=${barTracker.pointerDown}")
            snapJob?.cancel()
            snapJob = scope.launch { toolbarTranslation.animateTo(target, Motion.standard()) }
        }
    }
    val applyBarsStateRef = rememberUpdatedState(applyBars)
    val applyBarsState by applyBarsStateRef
    val loadingStateRef = rememberUpdatedState(currentTab?.isLoading == true)
    val loadingState by loadingStateRef

    // Ensure toolbar is fully shown at top, during loading, find, edit, or navigation
    val shouldForceToolbarShown = isBlankTab || currentTab?.isLoading == true || isFindMode || isEditMode ||
        currentTab?.rescueState != null
    // Keyed on the URL without its fragment: in-page hash changes must not pop the bars back up.
    val navKey = currentTab?.url?.substringBefore('#')

    // Blocked notices
    val latestTabIdRef = rememberUpdatedState(currentTabId)
    val latestTabId by latestTabIdRef

    val prevTabIdRef = remember { mutableStateOf(currentTabId) }
    var prevTabId by prevTabIdRef
    val prevNavKeyRef = remember { mutableStateOf(navKey) }
    var prevNavKey by prevNavKeyRef

    val currentTabIds = remember(tabs) { tabs.map { it.id }.toSet() }

    // Tab notice
    val tabNoticeJobRef = remember { mutableStateOf<Job?>(null) }
    var tabNoticeJob by tabNoticeJobRef

    // Close activity if no tabs remain
    // Only once restore is done: the tab list is empty before that, which used to close the browser right after it opened.
    val restoredRef = tabManager.restored.collectAsStateWithLifecycle()
    val restored by restoredRef

    // Leave-to-FMHY: close empty tabs first, then switch to FMHY (or finish if no tabs remain).
    val leaveToFmhy: () -> Unit = {
        val left = tabManager.closeEmptyTabsOnLeave()
        bringFmhyToFront(context)
        if (left == 0) onFinishActivity()
    }

    // System Back Handling
    val currentBackAction = backAction(
        fullscreen = hooks?.customView != null,
        findMode = isFindMode,
        editMode = isEditMode,
        hasTab = currentTab != null,
        canGoBack = currentTab != null && tabManager.canGoBack(currentTab.id),
        isBlankTab = currentTab != null && (currentTab.url == "about:blank" || currentTab.url.isEmpty()),
        tabCount = tabs.size,
    )
    val predictiveBackEnabled = usePredictiveBack(currentBackAction, Build.VERSION.SDK_INT)

    val backScale = remember { Animatable(1f) }
    val backAlpha = remember { Animatable(1f) }
    val backRadius = remember { Animatable(0f) }
    val backTranslateX = remember { Animatable(0f) }

    return BrowserScreenScope(
        tabManager = tabManager,
        adblockService = adblockService,
        settingsRepository = settingsRepository,
        bookmarksRepository = bookmarksRepository,
        datasetSearchEngine = datasetSearchEngine,
        datasetStore = datasetStore,
        browserHooks = browserHooks,
        isAppDark = isAppDark,
        isInPip = isInPip,
        onFinishActivity = onFinishActivity,
        modifier = modifier,
        context = context,
        density = density,
        scope = scope,
        haptics = haptics,
        tabsRef = tabsRef,
        currentTabIdRef = currentTabIdRef,
        webViewVersionRef = webViewVersionRef,
        adblockStatusRef = adblockStatusRef,
        browserPrefsRef = browserPrefsRef,
        blockingPrefsRef = blockingPrefsRef,
        protectionPrefsRef = protectionPrefsRef,
        checkPrefsRef = checkPrefsRef,
        currentTab = currentTab,
        isBlankTab = isBlankTab,
        isEditModeRef = isEditModeRef,
        showShieldSheetRef = showShieldSheetRef,
        showConnectionSheetRef = showConnectionSheetRef,
        showBlockedSheetRef = showBlockedSheetRef,
        showTabsSheetRef = showTabsSheetRef,
        showDownloadsSheetRef = showDownloadsSheetRef,
        showVideoSheetRef = showVideoSheetRef,
        showTabsRequestedRef = showTabsRequestedRef,
        showDownloadsRequestedRef = showDownloadsRequestedRef,
        isFindModeRef = isFindModeRef,
        findQueryRef = findQueryRef,
        findActiveIndexRef = findActiveIndexRef,
        findTotalMatchesRef = findTotalMatchesRef,
        pendingHideElementRuleRef = pendingHideElementRuleRef,
        pendingExternalAppRef = pendingExternalAppRef,
        pendingLinkMenuRef = pendingLinkMenuRef,
        showPictureChoiceRef = showPictureChoiceRef,
        captureJobRef = captureJobRef,
        captureProgressRef = captureProgressRef,
        isCapturing = isCapturing,
        isCapturingFullPageRef = isCapturingFullPageRef,
        capturedTabIdRef = capturedTabIdRef,
        capturedTabUrlRef = capturedTabUrlRef,
        savedIdsRef = savedIdsRef,
        isSaved = isSaved,
        hooks = hooks,
        snackbarHostState = snackbarHostState,
        view = view,
        blockedStates = blockedStates,
        pulseKeyRef = pulseKeyRef,
        blockedBarHeightPxRef = blockedBarHeightPxRef,
        currentBlockedState = currentBlockedState,
        isBlockedBarVisible = isBlockedBarVisible,
        onStartPictureCapture = onStartPictureCapture,
        customView = customView,
        container = container,
        vpnStateRef = vpnStateRef,
        connectionVpn = connectionVpn,
        orbotProxy = orbotProxy,
        downloadController = downloadController,
        downloadSummaryRef = downloadSummaryRef,
        updatePrefsRef = updatePrefsRef,
        showKeyPromptDialogRef = showKeyPromptDialogRef,
        redirectWarningEventRef = redirectWarningEventRef,
        notificationPermissionLauncher = notificationPermissionLauncher,
        notificationPermissionRequestedThisSessionRef = notificationPermissionRequestedThisSessionRef,
        pendingDownload = pendingDownload,
        offerAcceptedRef = offerAcceptedRef,
        bookmarkListRef = bookmarkListRef,
        folderListRef = folderListRef,
        bookmarksState = bookmarksState,
        totalBarHeightPxRef = totalBarHeightPxRef,
        toolbarTranslation = toolbarTranslation,
        snapJobRef = snapJobRef,
        bars = bars,
        barTracker = barTracker,
        isTopBar = isTopBar,
        applyBars = applyBars,
        applyBarsStateRef = applyBarsStateRef,
        loadingStateRef = loadingStateRef,
        shouldForceToolbarShown = shouldForceToolbarShown,
        navKey = navKey,
        latestTabIdRef = latestTabIdRef,
        prevTabIdRef = prevTabIdRef,
        prevNavKeyRef = prevNavKeyRef,
        currentTabIds = currentTabIds,
        tabNoticeJobRef = tabNoticeJobRef,
        restoredRef = restoredRef,
        leaveToFmhy = leaveToFmhy,
        currentBackAction = currentBackAction,
        predictiveBackEnabled = predictiveBackEnabled,
        backScale = backScale,
        backAlpha = backAlpha,
        backRadius = backRadius,
        backTranslateX = backTranslateX,
    )
}
