package io.github.omeg4dev.fmhy.browser

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.browser.download.DownloadEvent
import io.github.omeg4dev.fmhy.browser.download.DownloadsSheet
import io.github.omeg4dev.fmhy.browser.download.KeyPromptDialog
import io.github.omeg4dev.fmhy.browser.download.RedirectWarningText
import io.github.omeg4dev.fmhy.browser.download.VideoSourcesSheet
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.AllowRule
import io.github.omeg4dev.fmhy.browser.adblock.SelectorPicker
import io.github.omeg4dev.fmhy.data.BookmarksRepository
import io.github.omeg4dev.fmhy.data.CheckPrefs
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ToolbarPosition
import io.github.omeg4dev.fmhy.data.UpdatePrefs
import io.github.omeg4dev.fmhy.data.search.SearchEngine as DatasetSearchEngine
import io.github.omeg4dev.fmhy.data.user.UserBookmark
import io.github.omeg4dev.fmhy.ui.common.BookmarksState
import io.github.omeg4dev.fmhy.ui.common.EmptyState
import io.github.omeg4dev.fmhy.ui.common.LocalBookmarks
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.SavedInfo
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
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
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    val tabs by tabManager.tabs.collectAsStateWithLifecycle()
    val currentTabId by tabManager.currentTabId.collectAsStateWithLifecycle()
    val webViewVersion by tabManager.webViewVersion.collectAsStateWithLifecycle()
    val adblockStatus by adblockService.status.collectAsStateWithLifecycle()

    val browserPrefs by settingsRepository.browserPrefs.collectAsStateWithLifecycle(initialValue = tabManager.currentBrowserPrefs)
    val blockingPrefs by settingsRepository.blockingPrefs.collectAsStateWithLifecycle(initialValue = adblockService.currentBlockingPrefs)
    val protectionPrefs by settingsRepository.protectionPrefs.collectAsStateWithLifecycle(initialValue = tabManager.currentProtectionPrefs)
    val checkPrefs by settingsRepository.checkPrefs.collectAsStateWithLifecycle(initialValue = CheckPrefs())

    val currentTab = remember(tabs, currentTabId) {
        tabs.firstOrNull { it.id == currentTabId }
    }
    val isBlankTab = currentTab == null || currentTab.url == "about:blank" || currentTab.url.isEmpty()

    // UI state
    var isEditMode by remember { mutableStateOf(false) }
    LaunchedEffect(currentTabId) {
        isEditMode = false
    }
    var showShieldSheet by remember { mutableStateOf(false) }
    var showBlockedSheet by remember { mutableStateOf(false) }
    var showTabsSheet by remember { mutableStateOf(false) }
    var showDownloadsSheet by remember { mutableStateOf(false) }
    var showVideoSheet by remember { mutableStateOf(false) }
    if (showVideoSheet && currentTab?.videoSources.isNullOrEmpty()) {
        showVideoSheet = false
    }
    val showTabsRequested by tabManager.showTabsRequested.collectAsStateWithLifecycle()
    LaunchedEffect(showTabsRequested) {
        if (showTabsRequested) {
            showTabsSheet = true
            tabManager.consumeShowTabs()
        }
    }
    val showDownloadsRequested by tabManager.showDownloadsRequested.collectAsStateWithLifecycle()
    LaunchedEffect(showDownloadsRequested) {
        if (showDownloadsRequested) {
            showDownloadsSheet = true
            tabManager.consumeShowDownloads()
        }
    }

    // Find in page state
    var isFindMode by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findActiveIndex by remember { mutableIntStateOf(0) }
    var findTotalMatches by remember { mutableIntStateOf(0) }

    // Dialogs state
    var pendingHideElementRule by remember { mutableStateOf<String?>(null) }
    var pendingExternalApp by remember { mutableStateOf<Pair<Intent, String>?>(null) }
    var pendingLinkMenu by remember { mutableStateOf<LinkMenuRequest?>(null) }

    val savedIds by bookmarksRepository.savedIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val isSaved = currentTab?.entryId != null && savedIds.contains(currentTab.entryId)

    val hooks = browserHooks as? DefaultBrowserHooks
    val snackbarHostState = remember { SnackbarHostState() }
    val customView = hooks?.customView

    val container = remember(context) { (context.applicationContext as FmhyApp).container }
    val downloadController = container.downloads
    val downloadSummary by downloadController.activeSummary.collectAsStateWithLifecycle()
    val updatePrefs by settingsRepository.updatePrefs.collectAsStateWithLifecycle(initialValue = UpdatePrefs())

    LaunchedEffect(downloadController) {
        downloadController.notices.collect { notice ->
            snackbarHostState.showSnackbar(notice)
        }
    }

    var showKeyPromptDialog by remember { mutableStateOf(false) }
    var redirectWarningEvent by remember { mutableStateOf<DownloadEvent.RedirectWarning?>(null) }
    LaunchedEffect(downloadController) {
        downloadController.events.collect { event ->
            when (event) {
                is DownloadEvent.KeyPromptNeeded -> {
                    downloadController.keyPromptShown()
                    showKeyPromptDialog = true
                }
                is DownloadEvent.RedirectWarning -> {
                    redirectWarningEvent = event
                }
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore result */ }
    var notificationPermissionRequestedThisSession by remember { mutableStateOf(false) }
    LaunchedEffect(hooks) {
        hooks?.downloadStarted?.collect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionRequestedThisSession) {
                notificationPermissionRequestedThisSession = true
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    val pendingDownload = hooks?.pendingDownload
    var offerAccepted by remember(pendingDownload) { mutableStateOf(false) }

    val bookmarkList by bookmarksRepository.bookmarks.collectAsStateWithLifecycle(initialValue = emptyList())
    val folderList by bookmarksRepository.folders.collectAsStateWithLifecycle(initialValue = emptyList())
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
    var totalBarHeightPx by remember { mutableFloatStateOf(0f) }
    val toolbarTranslation = remember { Animatable(0f) }
    var snapJob by remember { mutableStateOf<Job?>(null) }
    val bars = remember { BarVisibilityState() }
    val barTracker = remember(density) {
        BarScrollTracker(hideThresholdPx = with(density) { 48.dp.toPx() }, showThresholdPx = with(density) { 32.dp.toPx() })
    }
    val isTopBar = browserPrefs.toolbarPosition == ToolbarPosition.TOP
    var barTargetHidden by remember { mutableStateOf(false) }
    val applyBars: () -> Unit = {
        barTargetHidden = bars.hidden
        val hiddenValue = if (browserPrefs.toolbarPosition == ToolbarPosition.TOP) -totalBarHeightPx else totalBarHeightPx
        val target = if (bars.hidden) hiddenValue else 0f
        if (toolbarTranslation.targetValue != target) {
            barsLog("target ${toolbarTranslation.targetValue} -> $target pointerDown=${barTracker.pointerDown}")
            snapJob?.cancel()
            snapJob = scope.launch { toolbarTranslation.animateTo(target, Motion.standard()) }
        }
    }
    val applyBarsState by rememberUpdatedState(applyBars)
    val loadingState by rememberUpdatedState(currentTab?.isLoading == true)

    // Ensure toolbar is fully shown at top, during loading, find, edit, or navigation
    val shouldForceToolbarShown = isBlankTab || currentTab?.isLoading == true || isFindMode || isEditMode ||
        currentTab?.rescueState != null
    // Keyed on the URL without its fragment: in-page hash changes must not pop the bars back up.
    val navKey = currentTab?.url?.substringBefore('#')
    LaunchedEffect(shouldForceToolbarShown, navKey) {
        bars.hidden = false
        barTargetHidden = false
        barTracker.reset()
        applyBarsState()
    }
    // Toolbar position changed: reset to shown.
    LaunchedEffect(isTopBar) {
        bars.hidden = false
        barTargetHidden = false
        snapJob?.cancel()
        toolbarTranslation.snapTo(0f)
    }
    // Bar block grew/shrank (site bar appeared, find bar...) while hidden: re-snap so it all stays hidden.
    LaunchedEffect(totalBarHeightPx) {
        if (bars.hidden && totalBarHeightPx > 0f) {
            val hiddenValue = if (isTopBar) -totalBarHeightPx else totalBarHeightPx
            if (toolbarTranslation.targetValue != hiddenValue) {
                barsLog("resnap hidden -> $hiddenValue")
                snapJob?.cancel()
                toolbarTranslation.snapTo(hiddenValue)
            }
        }
    }

    // Snackbars emitted by shield hooks
    LaunchedEffect(hooks) {
        hooks?.snackbarFlow?.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = event.message,
                    actionLabel = event.actionLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) event.onAction?.invoke()
            }
        }
    }

    // Tab manager callbacks
    LaunchedEffect(tabManager) {
        tabManager.onPickerResultCallback = { tabId, selector ->
            val record = tabManager.getTabRecord(tabId)
            val host = try { Uri.parse(record?.url.orEmpty()).host.orEmpty() } catch (_: Exception) { "" }
            if (host.isNotEmpty() && selector.isNotEmpty()) {
                pendingHideElementRule = SelectorPicker.computeRule(host, selector)
            }
        }
        tabManager.onPickerCancelCallback = {}
        tabManager.onExternalAppCallback = { intent, appName ->
            pendingExternalApp = Pair(intent, appName)
        }
        tabManager.onLinkMenuRequestCallback = { req ->
            pendingLinkMenu = req
        }
    }

    // Close activity if no tabs remain
    // Only once restore is done: the tab list is empty before that, which used to close the browser right after it opened.
    val restored by tabManager.restored.collectAsStateWithLifecycle()
    LaunchedEffect(tabs, restored) {
        if (shouldFinishWhenEmpty(restored, tabs.size, currentTabId, tabManager.linkQueue.hasPendingLinks())) {
            onFinishActivity()
        }
    }

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

    if (Build.VERSION.SDK_INT >= 34) {
        PredictiveBackHandler(enabled = predictiveBackEnabled) { progress ->
            try {
                progress.collect { backEvent ->
                    val p = backEvent.progress
                    val edge = backEvent.swipeEdge
                    val sign = if (edge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                    val maxTranslatePx = with(density) { 24.dp.toPx() }
                    backScale.snapTo(1f - (0.1f * p))
                    backRadius.snapTo(28f * p)
                    backTranslateX.snapTo(maxTranslatePx * p * sign)
                    backAlpha.snapTo(1f)
                }
                coroutineScope {
                    launch { backScale.animateTo(0.85f, tween(200, easing = Motion.EmphasizedAccelerate)) }
                    launch { backAlpha.animateTo(0f, tween(200, easing = Motion.EmphasizedAccelerate)) }
                }
                leaveToFmhy()
                backScale.snapTo(1f)
                backAlpha.snapTo(1f)
                backRadius.snapTo(0f)
                backTranslateX.snapTo(0f)
            } catch (_: CancellationException) {
                coroutineScope {
                    launch { backScale.animateTo(1f, Motion.standard()) }
                    launch { backAlpha.animateTo(1f, Motion.standard()) }
                    launch { backRadius.animateTo(0f, Motion.standard()) }
                    launch { backTranslateX.animateTo(0f, Motion.standard()) }
                }
            }
        }
    }

    BackHandler(enabled = !predictiveBackEnabled) {
        when (currentBackAction) {
            BackAction.EXIT_FULLSCREEN -> hooks?.onHideCustomView()
            BackAction.CLOSE_FIND -> {
                isFindMode = false
                currentTab?.let { tabManager.getTabRecord(it.id)?.webView?.clearMatches() }
            }
            BackAction.CLOSE_EDIT -> {
                isEditMode = false
            }
            BackAction.WEB_BACK -> {
                currentTab?.let { tabManager.goBack(it.id) }
            }
            BackAction.CLOSE_BLANK_TAB -> {
                currentTab?.let { tabManager.closeTab(it.id) }
            }
            BackAction.RETURN_TO_FMHY -> {
                leaveToFmhy()
            }
        }
    }

    CompositionLocalProvider(LocalBookmarks provides bookmarksState) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = backScale.value
                    scaleY = backScale.value
                    alpha = backAlpha.value
                    translationX = backTranslateX.value
                    val r = backRadius.value
                    if (r > 0f) {
                        shape = RoundedCornerShape(r.dp)
                        clip = true
                    }
                }
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. Status bar background: solid surfaceContainer at all times (no transparent band)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .align(Alignment.TopCenter)
                    .zIndex(10f),
            )

            // 2. Navigation bar background if toolbar is at bottom
            if (browserPrefs.toolbarPosition == ToolbarPosition.BOTTOM) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .align(Alignment.BottomCenter)
                        .zIndex(10f),
                )
            }

            // 3. WebView container fills whole area between status bar and navigation bar and never resizes
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
            ) {
                if (isBlankTab) {
                    key(currentTab?.id) {
                        BrowserStartScreen(
                            isPrivate = currentTab?.isPrivate == true,
                            userStore = tabManager.userStore,
                            datasetStore = datasetStore,
                            toolbarPosition = browserPrefs.toolbarPosition,
                            contentPadding = if (browserPrefs.toolbarPosition == ToolbarPosition.TOP) {
                                PaddingValues(top = with(density) { totalBarHeightPx.toDp() })
                            } else {
                                PaddingValues(bottom = with(density) { totalBarHeightPx.toDp() })
                            },
                            onOpenAddressBar = { isEditMode = true },
                            onNavigate = { url, entryId ->
                                currentTab?.let { tabManager.loadUrl(it.id, url, entryId) }
                            },
                            onShowMessage = { scope.launch { snackbarHostState.showSnackbar(it) } },
                            onCloseActivity = onFinishActivity,
                        )
                    }
                } else if (currentTab.crashed) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            icon = Icons.Outlined.SentimentDissatisfied,
                            title = "This page stopped",
                            body = "It used too much memory or crashed.",
                            actionLabel = "Reload",
                            onAction = {
                                tabManager.reloadTab(currentTab.id)
                            },
                        )
                    }
                } else {
                    // Active web content with Pull-to-refresh
                    @Suppress("UNUSED_VARIABLE")
                    val version = webViewVersion
                    val record = tabManager.getTabRecord(currentTab.id)
                    val webView = record?.webView

                    val pullRefreshState = rememberPullToRefreshState()

                    LaunchedEffect(currentTab.isLoading) {
                        if (!currentTab.isLoading) {
                            pullRefreshState.animateToHidden()
                        }
                    }

                    PullToRefreshBox(
                        isRefreshing = currentTab.isLoading,
                        onRefresh = { tabManager.reloadTab(currentTab.id) },
                        state = pullRefreshState,
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                state = pullRefreshState,
                                isRefreshing = currentTab.isLoading,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = if (browserPrefs.toolbarPosition == ToolbarPosition.TOP) 64.dp else 16.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (webView != null) {
                            val tyHolder = remember(webView) { TyHolder(webView.translationY) }
                            var initializedForWebView by remember(webView, browserPrefs.toolbarPosition) { mutableStateOf(false) }

                            // BUG-15: The WebView translation changes only at the start of a bar animation (once per switch),
                            // never per frame, compensating scroll in the same frame so content stays fixed on screen.
                            val targetHidden = barTargetHidden || (isTopBar && toolbarTranslation.targetValue < 0f)
                            LaunchedEffect(webView, browserPrefs.toolbarPosition, targetHidden, totalBarHeightPx) {
                                webView.setPadding(0, 0, 0, 0)
                                if (browserPrefs.toolbarPosition != ToolbarPosition.TOP) {
                                    if (webView.translationY != 0f) {
                                        barsLog("webView.translationY=0.0 pointerDown=${barTracker.pointerDown}")
                                        webView.translationY = 0f
                                        tyHolder.lastTy = 0f
                                    }
                                    initializedForWebView = true
                                    return@LaunchedEffect
                                }

                                if (totalBarHeightPx <= 0f) {
                                    return@LaunchedEffect
                                }

                                val targetTy = pageOffsetFor(targetHidden, totalBarHeightPx)
                                if (!initializedForWebView) {
                                    // On first composition / WebView change / toolbar position change:
                                    // set translationY directly to the correct resting value without scroll compensation.
                                    initializedForWebView = true
                                    if (webView.translationY != targetTy) {
                                        barsLog("webView.translationY=$targetTy pointerDown=${barTracker.pointerDown}")
                                        webView.translationY = targetTy
                                        tyHolder.lastTy = targetTy
                                    }
                                    return@LaunchedEffect
                                }

                                tyHolder.pendingTy = targetTy
                                // A finger is down: the switch rides on the next MOVE event (see the touch listener).
                                if (!barTracker.pointerDown) applyPageOffsetAtRest(webView, tyHolder, ::barsLog)
                            }

                            DisposableEffect(webView) {
                                webView.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                                    findActiveIndex = activeMatchOrdinal
                                    findTotalMatches = numberOfMatches
                                }

                                // Scroll listener: bars follow the scroll while it happens, never on finger lift.
                                // Idle: the glide after a lift ended; make sure the bar rests fully shown/hidden.
                                val idle = Runnable {
                                    barTracker.onScrollIdle()
                                    applyPageOffsetAtRest(webView, tyHolder, ::barsLog)
                                    val hiddenValue = if (isTopBar) -totalBarHeightPx else totalBarHeightPx
                                    val target = if (bars.hidden) hiddenValue else 0f
                                    if (!toolbarTranslation.isRunning && toolbarTranslation.value != target) {
                                        // A cancelled animation keeps its target, so applyBars would do nothing here.
                                        barsLog("idle resnap -> $target")
                                        snapJob?.cancel()
                                        snapJob = scope.launch { toolbarTranslation.animateTo(target, Motion.standard()) }
                                    }
                                }
                                tyHolder.lastTy = webView.translationY
                                webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                                    // Screen-space delta: moving the WebView with a top bar shifts the finger's
                                    // position in the page and WebView scrolls by the same amount; that is no scroll.
                                    val ty = webView.translationY
                                    val dy = (scrollY - oldScrollY) - (ty - tyHolder.lastTy).roundToInt() - tyHolder.pendingScroll
                                    tyHolder.pendingScroll = 0
                                    tyHolder.lastTy = ty
                                    val isAtTop = !webView.canScrollVertically(-1) || scrollY <= 0

                                    if (isAtTop) {
                                        barTracker.reset()
                                        if (bars.hidden) {
                                            bars.hidden = false
                                            applyBarsState()
                                        }
                                    } else if (!isFindMode && !isEditMode && !loadingState && totalBarHeightPx > 0f) {
                                        val hide = barTracker.onScroll(dy)
                                        if (hide != null && hide != bars.hidden) {
                                            if (hide && isTopBar && !canHideTopBar(webView.scrollY, totalBarHeightPx)) {
                                                // Only allow hiding when webView.scrollY >= H (otherwise the compensation would clamp and the content would jump):
                                                // if it is smaller, ignore the hide request (leave bars.hidden = false).
                                            } else {
                                                bars.hidden = hide
                                                applyBarsState()
                                            }
                                        }
                                    }
                                    webView.removeCallbacks(idle)
                                    if (!barTracker.pointerDown) webView.postDelayed(idle, 200L)
                                }

                                // Touch listener for Pull-to-refresh at top of WebView and Pinch/Double-tap zoom
                                var startY = 0f
                                var isPulling = false
                                var hasCrossedThreshold = false
                                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

                                val gestureDetector = GestureDetector(
                                    context,
                                    object : GestureDetector.SimpleOnGestureListener() {
                                        override fun onDoubleTap(e: MotionEvent): Boolean {
                                            val record = currentTab?.let { tabManager.getTabRecord(it.id) }
                                            if (record != null) {
                                                if (record.hasMedia && record.isVideoPlaying) {
                                                    return false
                                                }
                                                if (customView != null) {
                                                    return false
                                                }
                                                val targetLevel = ZoomLogic.doubleTapTarget(record.zoomLevel)
                                                animateZoom(
                                                    webView = webView,
                                                    record = record,
                                                    targetLevel = targetLevel,
                                                    tapX = e.x,
                                                    tapY = e.y,
                                                    onFinish = {
                                                        tabManager.notifyTabChanged(record.id)
                                                    }
                                                )
                                            }
                                            return false
                                        }
                                    }
                                )

                                webView.setOnTouchListener { _, event ->
                                    if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                                        val pending = tyHolder.pendingTy
                                        if (pending != null && pending != webView.translationY) {
                                            // Move the page and tell the WebView the finger "moved" by the same amount in
                                            // this very event: it scrolls the content back in the same frame, no flick.
                                            val old = webView.translationY
                                            barsLog("webView.translationY=$pending (drag)")
                                            webView.translationY = pending
                                            tyHolder.lastTy = pending
                                            tyHolder.pendingScroll += (pending - old).roundToInt()
                                            event.offsetLocation(0f, old - pending)
                                        }
                                        tyHolder.pendingTy = null
                                    }
                                    gestureDetector.onTouchEvent(event)
                                    if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN || event.pointerCount >= 2) {
                                        barTracker.setMultiTouch(true)
                                    }
                                    when (event.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> {
                                            currentTab?.let { tabManager.getTabRecord(it.id)?.lastTouchAt = System.currentTimeMillis() }
                                            barTracker.onPointerDown()
                                            webView.removeCallbacks(idle)
                                            startY = event.rawY
                                            isPulling = false
                                            hasCrossedThreshold = false
                                            false
                                        }
                                        MotionEvent.ACTION_POINTER_DOWN -> {
                                            barTracker.setMultiTouch(true)
                                            if (isPulling) {
                                                isPulling = false
                                                scope.launch { pullRefreshState.animateToHidden() }
                                            }
                                            false
                                        }
                                        MotionEvent.ACTION_POINTER_UP -> {
                                            if (event.pointerCount <= 2) {
                                                barTracker.setMultiTouch(false)
                                            }
                                            false
                                        }
                                        MotionEvent.ACTION_MOVE -> {
                                            val ignoresBar = ZoomLogic.ignoresBar(event.pointerCount)
                                            if (ignoresBar) {
                                                barTracker.setMultiTouch(true)
                                                if (isPulling) {
                                                    isPulling = false
                                                    scope.launch { pullRefreshState.animateToHidden() }
                                                }
                                                false
                                            } else {
                                                val dy = event.rawY - startY
                                                val canScrollUp = webView.canScrollVertically(-1)
                                                if (dy > touchSlop && !canScrollUp && webView.scrollY <= 0) {
                                                    isPulling = true
                                                    val thresholdPx = with(density) { 80.dp.toPx() }
                                                    val fraction = (dy - touchSlop) / thresholdPx
                                                    scope.launch {
                                                        pullRefreshState.snapTo(fraction.coerceIn(0f, 2f))
                                                    }
                                                    if (fraction >= 1f && !hasCrossedThreshold) {
                                                        hasCrossedThreshold = true
                                                        haptics.tick()
                                                    } else if (fraction < 1f && hasCrossedThreshold) {
                                                        hasCrossedThreshold = false
                                                    }
                                                    true
                                                } else {
                                                    if (isPulling) {
                                                        isPulling = false
                                                        scope.launch { pullRefreshState.animateToHidden() }
                                                    }
                                                    false
                                                }
                                            }
                                        }
                                        MotionEvent.ACTION_UP -> {
                                            barTracker.setMultiTouch(false)
                                            barTracker.onPointerUp()
                                            webView.removeCallbacks(idle)
                                            webView.postDelayed(idle, 200L)
                                            if (isPulling) {
                                                isPulling = false
                                                if (pullRefreshState.distanceFraction >= 1f) {
                                                    tabManager.reloadTab(currentTab.id)
                                                }
                                                scope.launch {
                                                    if (pullRefreshState.distanceFraction >= 1f) {
                                                        pullRefreshState.animateToThreshold()
                                                    } else {
                                                        pullRefreshState.animateToHidden()
                                                    }
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        MotionEvent.ACTION_CANCEL -> {
                                            barTracker.setMultiTouch(false)
                                            barTracker.onPointerUp()
                                            webView.removeCallbacks(idle)
                                            webView.postDelayed(idle, 200L)
                                            if (isPulling) {
                                                isPulling = false
                                                scope.launch { pullRefreshState.animateToHidden() }
                                            }
                                            false
                                        }
                                        else -> false
                                    }
                                }

                                onDispose {
                                    webView.setOnScrollChangeListener(null)
                                    webView.removeCallbacks(idle)
                                    webView.setOnTouchListener(null)
                                }
                            }

                            androidx.compose.runtime.key(webView) {
                                AndroidView(
                                    factory = {
                                        val parent = webView.parent as? ViewGroup
                                        parent?.removeView(webView)
                                        webView
                                    },
                                    update = {},
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }

                // Toolbar overlay in Box (never resizes webview)
                if (!isInPip && customView == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(if (browserPrefs.toolbarPosition == ToolbarPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter)
                            .graphicsLayer {
                                translationY = toolbarTranslation.value
                            }
                            .onSizeChanged { totalBarHeightPx = it.height.toFloat() }
                            .zIndex(5f),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            BrowserToolbar(
                                tab = currentTab,
                                tabs = tabs,
                                tabCount = tabs.size,
                                toolbarPosition = browserPrefs.toolbarPosition,
                                isFindMode = isFindMode,
                                findQuery = findQuery,
                                findActiveIndex = findActiveIndex,
                                findTotalMatches = findTotalMatches,
                                showBlockedCount = blockingPrefs.showBlockedCount,
                                isSaved = isSaved,
                                onShieldClick = { showShieldSheet = true },
                                onAddressClick = { isEditMode = true },
                                onTabsClick = { showTabsSheet = true },
                                onReloadClick = { currentTab?.let { tabManager.reloadTab(it.id) } },
                                onStopClick = { currentTab?.let { tabManager.stopTab(it.id) } },
                                onBackClick = { currentTab?.let { tabManager.goBack(it.id) } },
                                onForwardClick = { currentTab?.let { tabManager.goForward(it.id) } },
                                onBackToFmhyClick = { leaveToFmhy() },
                                onNewTabClick = { isPrivate ->
                                    tabManager.openNewTabPage(isPrivate = isPrivate)
                                },
                                onFindInPageClick = {
                                    isFindMode = true
                                    findQuery = ""
                                },
                                onFindQueryChange = { query ->
                                    findQuery = query
                                    currentTab?.let { t ->
                                        val record = tabManager.getTabRecord(t.id)
                                        record?.webView?.findAllAsync(query)
                                    }
                                },
                                onFindNext = {
                                    currentTab?.let { t ->
                                        tabManager.getTabRecord(t.id)?.webView?.findNext(true)
                                    }
                                },
                                onFindPrevious = {
                                    currentTab?.let { t ->
                                        tabManager.getTabRecord(t.id)?.webView?.findNext(false)
                                    }
                                },
                                onFindClose = {
                                    isFindMode = false
                                    currentTab?.let { t ->
                                        tabManager.getTabRecord(t.id)?.webView?.clearMatches()
                                    }
                                },
                                onToggleDesktop = {
                                    currentTab?.let { tabManager.toggleDesktopSite(it.id) }
                                },
                                onSetZoom = { targetLevel ->
                                    currentTab?.let { tab ->
                                        val record = tabManager.getTabRecord(tab.id)
                                        val wb = record?.webView
                                        if (record != null && wb != null) {
                                            animateZoom(
                                                webView = wb,
                                                record = record,
                                                targetLevel = targetLevel,
                                                onFinish = {
                                                    tabManager.notifyTabChanged(tab.id)
                                                }
                                            )
                                        }
                                    }
                                },
                                onSetPlaybackSpeed = { speed ->
                                    currentTab?.let { tabManager.setPlaybackSpeed(it.id, speed) }
                                },
                                onToggleBookmark = {
                                    val tab = currentTab
                                    val entryId = tab?.entryId
                                    if (entryId != null) {
                                        scope.launch {
                                            if (isSaved) {
                                                bookmarksRepository.remove(entryId)
                                            } else {
                                                tabManager.userStore.upsertBookmark(
                                                    UserBookmark(
                                                        entryId = entryId,
                                                        name = tab.title.ifEmpty { tab.url },
                                                        url = tab.url,
                                                        sectionId = "",
                                                        sectionTitle = "",
                                                        headingPath = "",
                                                        savedAt = System.currentTimeMillis(),
                                                    )
                                                )
                                            }
                                        }
                                    }
                                },
                                onSelectTab = { tabId ->
                                    tabManager.selectTab(tabId)
                                },
                                statusIcon = { currentTab?.let { browserHooks.siteStatusIcon(it) } },
                                onCopyLink = {
                                    currentTab?.url?.let { url ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Link copied")
                                        }
                                    }
                                },
                                onReportProblem = {
                                    val app = context.applicationContext as? io.github.omeg4dev.fmhy.FmhyApp
                                    currentTab?.let { tab ->
                                        app?.container?.openReport(
                                            io.github.omeg4dev.fmhy.report.ReportRequest(
                                                defaultArea = io.github.omeg4dev.fmhy.report.ReportArea.PAGE_DOES_NOT_LOAD,
                                                pageUrl = tab.url,
                                                isPrivateTab = tab.isPrivate,
                                                crash = null,
                                            )
                                        )
                                    }
                                },
                                onDownloadsClick = { showDownloadsSheet = true },
                                downloadSummary = downloadSummary,
                                onDownloadsRingClick = { showDownloadsSheet = true },
                                showVideoButton = browserPrefs.videoDownloadButton && (currentTab?.videoSources?.isNotEmpty() == true),
                                onVideoClick = { showVideoSheet = true },
                            )
                        }
                    }
                }


                // Edit Mode overlay (when typing a URL over an existing page or from start screen)
                if (isEditMode && currentTab != null) {
                    BrowserEditMode(
                        initialText = if (isBlankTab) "" else currentTab.url,
                        searchEngine = browserPrefs.searchEngine,
                        datasetSearchEngine = datasetSearchEngine,
                        toolbarPosition = browserPrefs.toolbarPosition,
                        autofocus = true,
                        onNavigate = { url ->
                            isEditMode = false
                            tabManager.loadUrl(currentTab.id, url)
                        },
                        onDismiss = { isEditMode = false },
                        modifier = Modifier
                            .zIndex(6f)
                            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
                    )
                }

                // Dead-site rescue overlay: under the bar (which stays shown), fades in and out once.
                val rescue = currentTab?.rescueState?.takeIf { !isBlankTab }
                var lastRescue by remember { mutableStateOf(rescue) }
                if (rescue != null) lastRescue = rescue
                val barHeightDp = with(density) { totalBarHeightPx.toDp() }
                AnimatedVisibility(
                    visible = rescue != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .zIndex(4f)
                        .padding(
                            top = if (isTopBar) barHeightDp else 0.dp,
                            bottom = if (isTopBar) 0.dp else barHeightDp,
                        ),
                ) {
                    val shown = lastRescue ?: return@AnimatedVisibility
                    val tabId = currentTab?.id
                    RescueOverlay(
                        state = shown,
                        onRetry = {
                            val record = tabId?.let { tabManager.getTabRecord(it) }
                            if (hooks != null && record != null) {
                                hooks.retryRescue(record)
                            } else if (tabId != null) {
                                tabManager.setRescueState(tabId, null)
                                tabManager.loadUrl(tabId, shown.failingUrl)
                            }
                        },
                        onReloadWithoutBlocking = {
                            val record = tabId?.let { tabManager.getTabRecord(it) }
                            if (hooks != null && record != null) {
                                hooks.reloadWithoutBlocking(record)
                            } else if (tabId != null) {
                                tabManager.setRescueState(tabId, null)
                                tabManager.loadUrl(tabId, shown.failingUrl)
                            }
                        },
                        onOpenUrl = { url ->
                            if (tabId != null) {
                                tabManager.setRescueState(tabId, null)
                                tabManager.loadUrl(tabId, url)
                            }
                        },
                        onReport = {
                            val app = context.applicationContext as? io.github.omeg4dev.fmhy.FmhyApp
                            app?.container?.openReport(
                                io.github.omeg4dev.fmhy.report.ReportRequest(
                                    defaultArea = io.github.omeg4dev.fmhy.report.ReportArea.PAGE_DOES_NOT_LOAD,
                                    pageUrl = shown.failingUrl,
                                    isPrivateTab = currentTab?.isPrivate == true,
                                    crash = null,
                                )
                            )
                        },
                    )
                }

                // Zoom level pill indicator
                var showZoomPill by remember { mutableStateOf(false) }
                var lastSeenTabId by remember { mutableStateOf<Long?>(null) }
                var lastSeenZoomLevel by remember { mutableStateOf(1f) }

                val currentTabId = currentTab?.id
                val currentZoomLevel = currentTab?.zoomLevel ?: 1f

                LaunchedEffect(currentTabId, currentZoomLevel) {
                    if (currentTabId == null) {
                        showZoomPill = false
                        return@LaunchedEffect
                    }
                    if (currentTabId != lastSeenTabId) {
                        lastSeenTabId = currentTabId
                        lastSeenZoomLevel = currentZoomLevel
                        showZoomPill = false
                    } else if (kotlin.math.abs(currentZoomLevel - lastSeenZoomLevel) > 0.005f) {
                        lastSeenZoomLevel = currentZoomLevel
                        showZoomPill = true
                        delay(1500L)
                        showZoomPill = false
                    }
                }

                AnimatedVisibility(
                    visible = showZoomPill && currentTab != null && !isBlankTab && customView == null,
                    enter = fadeIn(Motion.enter()),
                    exit = fadeOut(Motion.exit()),
                    modifier = Modifier
                        .align(if (isTopBar) Alignment.TopCenter else Alignment.BottomCenter)
                        .padding(
                            top = if (isTopBar) barHeightDp + 72.dp else 0.dp,
                            bottom = if (isTopBar) 0.dp else barHeightDp + 72.dp,
                        )
                        .zIndex(4f),
                ) {
                    val tab = currentTab ?: return@AnimatedVisibility
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .clickable {
                                showZoomPill = false
                                val record = tabManager.getTabRecord(tab.id)
                                val wb = record?.webView
                                if (record != null && wb != null && record.zoomLevel != 1f) {
                                    animateZoom(
                                        webView = wb,
                                        record = record,
                                        targetLevel = 1.0f,
                                        onFinish = {
                                            tabManager.notifyTabChanged(tab.id)
                                        }
                                    )
                                }
                            },
                    ) {
                        Text(
                            text = ZoomLogic.label(tab.zoomLevel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                // Unsafe / potentially unsafe interstitial
                val interstitial = currentTab?.interstitialState
                if (interstitial != null && !isBlankTab) {
                    InterstitialOverlay(
                        state = interstitial,
                        onGoBack = {
                            tabManager.setInterstitialState(currentTab.id, null)
                            if (!tabManager.goBack(currentTab.id)) {
                                if (tabs.size > 1) tabManager.closeTab(currentTab.id) else tabManager.loadUrl(currentTab.id, "about:blank")
                            }
                        },
                        onContinue = { tabManager.continueAnyway(currentTab.id) },
                        onReport = {
                            val app = context.applicationContext as? io.github.omeg4dev.fmhy.FmhyApp
                            app?.container?.openReport(
                                io.github.omeg4dev.fmhy.report.ReportRequest(
                                    defaultArea = io.github.omeg4dev.fmhy.report.ReportArea.OTHER,
                                    pageUrl = interstitial.url,
                                    isPrivateTab = currentTab.isPrivate,
                                    crash = null,
                                )
                            )
                        },
                        modifier = Modifier.zIndex(7f),
                    )
                }

                // Download interstitial overlay
                if (pendingDownload?.interstitial != null && !offerAccepted) {
                    InterstitialOverlay(
                        state = pendingDownload.interstitial,
                        onGoBack = { hooks?.pendingDownload = null },
                        onContinue = { offerAccepted = true },
                        onReport = {
                            val app = context.applicationContext as? io.github.omeg4dev.fmhy.FmhyApp
                            app?.container?.openReport(
                                io.github.omeg4dev.fmhy.report.ReportRequest(
                                    defaultArea = io.github.omeg4dev.fmhy.report.ReportArea.OTHER,
                                    pageUrl = pendingDownload.url,
                                    isPrivateTab = pendingDownload.isPrivate,
                                    crash = null,
                                )
                            )
                        },
                        modifier = Modifier.zIndex(7f),
                    )
                }
            }

            // Full-screen video (WebChromeClient custom view)
            if (customView != null) {
                AndroidView(
                    factory = { ctx ->
                        (customView.parent as? ViewGroup)?.removeView(customView)
                        DoubleTapSeekLayout(ctx) { seconds ->
                            haptics.tick()
                            tabManager.seekActiveVideo(seconds)
                        }.also { it.setContent(customView) }
                    },
                    update = { it.seekEnabled = browserPrefs.doubleTapSeek },
                    onRelease = { it.removeAllViews() },
                    modifier = Modifier.fillMaxSize().background(Color.Black).zIndex(20f),
                )
            }

            // SnackbarHost
            if (!isInPip) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (browserPrefs.toolbarPosition == ToolbarPosition.BOTTOM) 68.dp else 16.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .zIndex(15f),
                )
            }
        }
    }

    // Download confirmation
    if (hooks != null && pendingDownload != null) {
        val showDialogOrConfirm = pendingDownload.interstitial == null || offerAccepted
        if (showDialogOrConfirm) {
            if (pendingDownload.needsConfirmation) {
                DownloadDialog(
                    offer = pendingDownload,
                    onConfirm = { allowMobile ->
                        hooks.confirmDownload(pendingDownload, allowMobile)
                    },
                    onCancel = { hooks.pendingDownload = null },
                    checkServices = checkPrefs.enabledServices,
                    readyServices = checkPrefs.readyServices,
                    onCheckLink = { url -> container.checks.checkLink(url, checkPrefs.readyServices) },
                    onOpenUrl = { tabManager.openTab(it, isPrivate = currentTab?.isPrivate == true) },
                    onShowInSection = { entryId ->
                        scope.launch {
                            val entry = datasetStore.entry(entryId)
                            if (entry != null) {
                                showEntryInApp(context, entry)
                            }
                        }
                    },
                )
            } else {
                LaunchedEffect(pendingDownload, offerAccepted) {
                    hooks.confirmDownload(pendingDownload, allowMobile = false)
                }
            }
        }
    }

    if (hooks != null) {
        // "Open in another app?" from the redirect/app-link guard (call site 1)
        val appDialog = hooks.pendingExternalAppDialog
        if (appDialog != null) {
            OpenInAnotherAppDialog(
                appName = appDialog.appName,
                onConfirm = {
                    hooks.pendingExternalAppDialog = null
                    try {
                        appDialog.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(appDialog.intent)
                    } catch (_: Exception) {
                        appDialog.fallbackUrl?.let { u -> currentTab?.let { tabManager.loadUrl(it.id, u) } }
                    }
                },
                onDismiss = { hooks.pendingExternalAppDialog = null },
            )
        }
    }

    // Shield Sheet
    if (showShieldSheet && currentTab != null) {
        ShieldSheet(
            tab = currentTab,
            adblockStatus = adblockStatus,
            blockingPrefs = blockingPrefs,
            protectionPrefs = protectionPrefs,
            browserPrefs = browserPrefs,
            isAppDark = isAppDark,
            onDismiss = { showShieldSheet = false },
            onUpdateSitePref = { update ->
                val host = try { Uri.parse(currentTab.url).host.orEmpty() } catch (_: Exception) { "" }
                if (host.isNotEmpty()) {
                    tabManager.updateSitePrefs(currentTab.id, host, update)
                }
            },
            onResetDefaults = {
                val host = try { Uri.parse(currentTab.url).host.orEmpty() } catch (_: Exception) { "" }
                if (host.isNotEmpty()) {
                    tabManager.clearSitePrefs(currentTab.id, host)
                }
                val site = AllowRule.pageSite(currentTab.url)
                if (site != null) {
                    val allowed = AllowRule.allowedHostsFor(blockingPrefs.customRules, site)
                    for (h in allowed) {
                        tabManager.unallowBlockedHost(currentTab.id, h)
                    }
                }
            },
            onHideElement = {
                showShieldSheet = false
                tabManager.startElementPicker(currentTab.id)
            },
            onOpenBlockedList = {
                showShieldSheet = false
                showBlockedSheet = true
            },
        )
    }

    // Blocked Sheet
    if (showBlockedSheet && currentTab != null) {
        val site = currentTab.url.let { AllowRule.pageSite(it) }.orEmpty()
        BlockedSheet(
            tab = currentTab,
            allowedHosts = AllowRule.allowedHostsFor(blockingPrefs.customRules, site),
            onAllow = { host -> tabManager.allowBlockedHost(currentTab.id, host) },
            onUnallow = { host -> tabManager.unallowBlockedHost(currentTab.id, host) },
            onReload = { tabManager.reloadTab(currentTab.id) },
            onDismiss = { showBlockedSheet = false },
        )
    }

    // Tabs Sheet
    if (showTabsSheet) {
        TabsSheet(
            tabs = tabs,
            currentTabId = currentTabId,
            tabManager = tabManager,
            tabsView = browserPrefs.tabsView,
            onTabsViewChange = { v -> scope.launch { settingsRepository.updateBrowser { it.copy(tabsView = v) } } },
            onDismiss = { showTabsSheet = false },
            onSelectTab = { tabId ->
                showTabsSheet = false
                tabManager.selectTab(tabId)
            },
            onCloseTab = { tabId ->
                tabManager.closeTab(tabId)
            },
            onNewTab = { isPrivate ->
                showTabsSheet = false
                tabManager.openNewTabPage(isPrivate = isPrivate)
            },
            onCloseAll = {
                showTabsSheet = false
                tabManager.closeAllTabs()
                onFinishActivity()
            },
        )
    }

    // Downloads Sheet
    if (showDownloadsSheet) {
        DownloadsSheet(
            controller = downloadController,
            isPrivateTab = currentTab?.isPrivate == true,
            onPasteLink = { hooks?.offerPastedLink(context, it, currentTab?.isPrivate == true) },
            onDismiss = { showDownloadsSheet = false },
            onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
            installExplained = updatePrefs.installExplained,
            onInstallExplained = { scope.launch { settingsRepository.updateUpdatePrefs { it.copy(installExplained = true) } } },
            checkPrefs = checkPrefs,
            checks = container.checks,
            onOpenUrl = { tabManager.openTab(it, isPrivate = currentTab?.isPrivate == true) },
            onOpenCheckSettings = { tabManager.openFileCheckSettings() },
            onShowInSection = { entryId ->
                scope.launch {
                    val entry = datasetStore.entry(entryId)
                    if (entry != null) {
                        showEntryInApp(context, entry)
                    }
                }
            },
        )
    }

    redirectWarningEvent?.let { warn ->
        AlertDialog(
            onDismissRequest = { redirectWarningEvent = null },
            title = { Text("Download redirected") },
            text = { Text(RedirectWarningText.sentence(warn.fromHost, warn.flag)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = warn.id
                        redirectWarningEvent = null
                        downloadController.continueAfterRedirectWarning(id)
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val id = warn.id
                        redirectWarningEvent = null
                        downloadController.cancel(id)
                    }
                ) {
                    Text("Cancel download")
                }
            },
        )
    }

    if (showKeyPromptDialog) {
        KeyPromptDialog(
            onSetUp = {
                showKeyPromptDialog = false
                tabManager.openFileCheckSettings()
            },
            onNotNow = {
                showKeyPromptDialog = false
            },
        )
    }

    // Video Sources Sheet
    if (showVideoSheet) {
        val sources = currentTab?.videoSources.orEmpty()
        VideoSourcesSheet(
            sources = sources,
            onPick = { source ->
                showVideoSheet = false
                val id = currentTab?.id ?: return@VideoSourcesSheet
                val record = tabManager.getTabRecord(id) ?: return@VideoSourcesSheet
                val userAgent = record.webView?.settings?.userAgentString.orEmpty()
                browserHooks.onDownloadStart(
                    context = context,
                    record = record,
                    url = source.url,
                    userAgent = userAgent,
                    contentDisposition = "",
                    mimeType = source.mimeType,
                    contentLength = -1L,
                )
            },
            onDismiss = { showVideoSheet = false },
        )
    }

    // Link Menu Sheet
    pendingLinkMenu?.let { req ->
        LinkMenuSheet(
            request = req,
            onLookupVerdict = { url ->
                val verdict = hooks?.checkVerdict(url)
                verdict?.let { SiteBarModel.from(it, isRedirectedFromEntry = false) }
            },
            onAction = { action ->
                pendingLinkMenu = null
                when (action) {
                    LinkMenuAction.OPEN_NEW_TAB -> {
                        val opener = tabManager.getTabRecord(req.tabId)
                        val isPrivate = tabPrivacyFor(opener?.isPrivate == true, action)
                        tabManager.openTab(url = req.url, isPrivate = isPrivate, select = true)
                    }
                    LinkMenuAction.OPEN_BACKGROUND -> {
                        val opener = tabManager.getTabRecord(req.tabId)
                        val isPrivate = tabPrivacyFor(opener?.isPrivate == true, action)
                        val newId = tabManager.openTab(url = req.url, isPrivate = isPrivate, select = false)
                        val event = BrowserSnackbarEvent(
                            message = "Opened in background",
                            actionLabel = "Switch",
                            onAction = { tabManager.selectTab(newId) },
                        )
                        if (hooks?.snackbarFlow?.tryEmit(event) != true) {
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = event.message,
                                    actionLabel = event.actionLabel,
                                    duration = SnackbarDuration.Long,
                                )
                                if (result == SnackbarResult.ActionPerformed) event.onAction?.invoke()
                            }
                        }
                    }
                    LinkMenuAction.OPEN_PRIVATE -> {
                        tabManager.openTab(url = req.url, isPrivate = true, select = true)
                    }
                    LinkMenuAction.COPY_LINK -> {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("Link", req.url))
                        val event = BrowserSnackbarEvent(message = "Link copied")
                        if (hooks?.snackbarFlow?.tryEmit(event) != true) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = event.message,
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    }
                    LinkMenuAction.SHARE_LINK -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, req.url)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share link"))
                    }
                    LinkMenuAction.OPEN_IMAGE_NEW_TAB -> {
                        val opener = tabManager.getTabRecord(req.tabId)
                        val isPrivate = tabPrivacyFor(opener?.isPrivate == true, action)
                        tabManager.openTab(url = req.url, isPrivate = isPrivate, select = true)
                    }
                    LinkMenuAction.COPY_IMAGE_LINK -> {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("Image link", req.url))
                        val event = BrowserSnackbarEvent(message = "Link copied")
                        if (hooks?.snackbarFlow?.tryEmit(event) != true) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = event.message,
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    }
                }
            },
            onDismiss = { pendingLinkMenu = null },
        )
    }

    // Hide Element Confirmation Dialog
    val ruleToHide = pendingHideElementRule
    if (ruleToHide != null) {
        AlertDialog(
            onDismissRequest = { pendingHideElementRule = null },
            title = { Text("Hide element on this site") },
            text = {
                Column {
                    Text("Add cosmetic filter rule:")
                    Text(
                        text = ruleToHide,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingHideElementRule = null
                        scope.launch {
                            settingsRepository.updateBlocking { bp ->
                                val existing = bp.customRules.trim()
                                val newRules = if (existing.isEmpty()) ruleToHide else "$existing\n$ruleToHide"
                                bp.copy(customRules = newRules)
                            }
                            currentTab?.let { tabManager.reloadTab(it.id) }
                        }
                    }
                ) {
                    Text("Hide")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingHideElementRule = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Open External App Dialog (call site 2)
    val externalApp = pendingExternalApp
    if (externalApp != null) {
        OpenInAnotherAppDialog(
            appName = externalApp.second,
            onConfirm = {
                val intent = externalApp.first
                pendingExternalApp = null
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) {}
            },
            onDismiss = { pendingExternalApp = null },
        )
    }
}

/** [pendingScroll] is the scrollBy this screen applied itself; the scroll listener must not count it as a user scroll. */
private class TyHolder(var lastTy: Float, var pendingScroll: Int = 0, var pendingTy: Float? = null)

/** No finger down: switch the page offset and keep the content in place with one scrollBy. */
private fun applyPageOffsetAtRest(webView: WebView, holder: TyHolder, log: (String) -> Unit) {
    val target = holder.pendingTy ?: return
    holder.pendingTy = null
    val old = webView.translationY
    if (old == target) return
    val compensation = compensationFor(oldTy = old, newTy = target)
    log("webView.translationY=$target (rest)")
    webView.translationY = target
    holder.lastTy = target
    if (compensation != 0) {
        holder.pendingScroll += compensation
        webView.scrollBy(0, compensation)
    }
}

private fun barsLog(msg: String) {
    if (io.github.omeg4dev.fmhy.BuildConfig.DEBUG) android.util.Log.d("FmhyBars", msg)
}

private fun animateZoom(
    webView: WebView,
    record: TabRecord,
    targetLevel: Float,
    tapX: Float? = null,
    tapY: Float? = null,
    steps: Int = 6,
    durationMs: Long = 180L,
    onFinish: () -> Unit = {}
) {
    val currentLevel = ZoomLogic.clamp(record.zoomLevel)
    val totalFactor = ZoomMath.zoomFactor(currentLevel, targetLevel)
    if (kotlin.math.abs(totalFactor - 1f) < 0.005f) return

    val factorPerStep = ZoomMath.stepFactor(totalFactor, steps)
    val stepDelayMs = (durationMs / steps).coerceAtLeast(16L)
    record.isProgrammaticZoom = true

    var step = 0
    val runnable = object : Runnable {
        override fun run() {
            if (record.webView !== webView) {
                record.isProgrammaticZoom = false
                return
            }
            if (step < steps) {
                val scrollBeforeX = webView.scrollX
                val scrollBeforeY = webView.scrollY
                webView.zoomBy(factorPerStep)
                if (tapX != null && tapY != null) {
                    val targetX = ZoomMath.targetScroll(scrollBeforeX, tapX, factorPerStep)
                    val targetY = ZoomMath.targetScroll(scrollBeforeY, tapY, factorPerStep)
                    val dx = ZoomMath.scrollDelta(targetX, webView.scrollX)
                    val dy = ZoomMath.scrollDelta(targetY, webView.scrollY)
                    if (dx != 0 || dy != 0) {
                        webView.scrollBy(dx, dy)
                    }
                }
                step++
                if (step < steps) {
                    webView.postDelayed(this, stepDelayMs)
                } else {
                    record.isProgrammaticZoom = false
                    record.zoomLevel = ZoomLogic.clamp(targetLevel)
                    onFinish()
                }
            } else {
                record.isProgrammaticZoom = false
                onFinish()
            }
        }
    }
    webView.post(runnable)
}
