package io.github.omeg4dev.fmhy.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.SelectorPicker
import io.github.omeg4dev.fmhy.data.BookmarksRepository
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ToolbarPosition
import io.github.omeg4dev.fmhy.data.search.SearchEngine as DatasetSearchEngine
import io.github.omeg4dev.fmhy.data.user.UserBookmark
import io.github.omeg4dev.fmhy.ui.common.BookmarksState
import io.github.omeg4dev.fmhy.ui.common.EmptyState
import io.github.omeg4dev.fmhy.ui.common.LocalBookmarks
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.SavedInfo
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
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

    val currentTab = remember(tabs, currentTabId) {
        tabs.firstOrNull { it.id == currentTabId }
    }

    // UI state
    var isEditMode by remember { mutableStateOf(false) }
    var showShieldSheet by remember { mutableStateOf(false) }
    var showTabsSheet by remember { mutableStateOf(false) }
    val showTabsRequested by tabManager.showTabsRequested.collectAsStateWithLifecycle()
    LaunchedEffect(showTabsRequested) {
        if (showTabsRequested) {
            showTabsSheet = true
            tabManager.consumeShowTabs()
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

    val savedIds by bookmarksRepository.savedIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val isSaved = currentTab?.entryId != null && savedIds.contains(currentTab.entryId)

    val hooks = browserHooks as? DefaultBrowserHooks
    val snackbarHostState = remember { SnackbarHostState() }
    val customView = hooks?.customView

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
    val applyBars: () -> Unit = {
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
    val shouldForceToolbarShown = currentTab?.isLoading == true || isFindMode || isEditMode ||
        currentTab?.rescueState != null
    // Keyed on the URL without its fragment: in-page hash changes must not pop the bars back up.
    val navKey = currentTab?.url?.substringBefore('#')
    LaunchedEffect(shouldForceToolbarShown, navKey) {
        bars.hidden = false
        barTracker.reset()
        applyBarsState()
    }
    // Toolbar position changed: reset to shown.
    LaunchedEffect(isTopBar) {
        bars.hidden = false
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
    }

    // Close activity if no tabs remain
    // Only once restore is done: the tab list is empty before that, which used to close the browser right after it opened.
    val restored by tabManager.restored.collectAsStateWithLifecycle()
    LaunchedEffect(tabs, restored) {
        if (shouldFinishWhenEmpty(restored, tabs.size, currentTabId, tabManager.linkQueue.hasPendingLinks())) {
            onFinishActivity()
        }
    }

    // System Back Handling
    BackHandler(enabled = true) {
        when {
            hooks?.customView != null -> hooks.onHideCustomView()
            isFindMode -> {
                isFindMode = false
                currentTab?.let { tabManager.getTabRecord(it.id)?.webView?.clearMatches() }
            }
            isEditMode -> {
                isEditMode = false
            }
            currentTab != null && tabManager.goBack(currentTab.id) -> {
                // Navigated back in WebView
            }
            // Back on an empty new tab returns to the tab you came from instead of leaving the browser.
            currentTab != null && tabs.size > 1 && (currentTab.url == "about:blank" || currentTab.url.isEmpty()) -> {
                tabManager.closeTab(currentTab.id)
            }
            else -> {
                onFinishActivity()
            }
        }
    }

    CompositionLocalProvider(LocalBookmarks provides bookmarksState) {
        Box(
            modifier = modifier
                .fillMaxSize()
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
                val isBlankTab = currentTab == null || currentTab.url == "about:blank" || currentTab.url.isEmpty()

                if (isBlankTab) {
                    // New tab page: full-screen with saved tiles, history, followed sections
                    BrowserEditMode(
                        initialText = "",
                        searchEngine = browserPrefs.searchEngine,
                        datasetSearchEngine = datasetSearchEngine,
                        userStore = tabManager.userStore,
                        toolbarPosition = browserPrefs.toolbarPosition,
                        autofocus = false,
                        onNavigate = { url ->
                            currentTab?.let { tabManager.loadUrl(it.id, url) }
                        },
                        onDismiss = {
                            if (tabs.size > 1) {
                                currentTab?.let { tabManager.closeTab(it.id) }
                            } else {
                                onFinishActivity()
                            }
                        },
                        onCloseActivity = onFinishActivity,
                    )
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
                            // The page moves with the top bar (View.translationY, no relayout) so the
                            // bar never covers the top of the page; a bottom bar simply overlays it.
                            LaunchedEffect(webView, browserPrefs.toolbarPosition) {
                                webView.setPadding(0, 0, 0, 0)
                                snapshotFlow { totalBarHeightPx + toolbarTranslation.value }.collect { visible ->
                                    val ty = if (browserPrefs.toolbarPosition == ToolbarPosition.TOP) visible.coerceAtLeast(0f) else 0f
                                    if (webView.translationY != ty) {
                                        barsLog("webView.translationY=$ty pointerDown=${barTracker.pointerDown}")
                                        webView.translationY = ty
                                    }
                                }
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
                                    val hiddenValue = if (isTopBar) -totalBarHeightPx else totalBarHeightPx
                                    val target = if (bars.hidden) hiddenValue else 0f
                                    if (!toolbarTranslation.isRunning && toolbarTranslation.value != target) {
                                        // A cancelled animation keeps its target, so applyBars would do nothing here.
                                        barsLog("idle resnap -> $target")
                                        snapJob?.cancel()
                                        snapJob = scope.launch { toolbarTranslation.animateTo(target, Motion.standard()) }
                                    }
                                }
                                var lastTy = webView.translationY
                                webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                                    // Screen-space delta: moving the WebView with a top bar shifts the finger's
                                    // position in the page and WebView scrolls by the same amount; that is no scroll.
                                    val ty = webView.translationY
                                    val dy = (scrollY - oldScrollY) - (ty - lastTy).roundToInt()
                                    lastTy = ty
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
                                            bars.hidden = hide
                                            applyBarsState()
                                        }
                                    }
                                    webView.removeCallbacks(idle)
                                    if (!barTracker.pointerDown) webView.postDelayed(idle, 200L)
                                }

                                // Touch listener for Pull-to-refresh at top of WebView
                                var startY = 0f
                                var isPulling = false
                                var hasCrossedThreshold = false
                                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

                                webView.setOnTouchListener { _, event ->
                                    when (event.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> {
                                            barTracker.onPointerDown()
                                            webView.removeCallbacks(idle)
                                            startY = event.rawY
                                            isPulling = false
                                            hasCrossedThreshold = false
                                            false
                                        }
                                        MotionEvent.ACTION_MOVE -> {
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
                                        MotionEvent.ACTION_UP -> {
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
                                onNewTabClick = { isPrivate ->
                                    tabManager.openTab(url = "about:blank", title = "New tab", isPrivate = isPrivate, select = true)
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
                            )
                        }
                    }
                }

                // Back-to-FMHY bubble: slides away with the toolbar, hidden while something else owns the screen.
                BackBubble(
                    settingsRepository = settingsRepository,
                    visible = BubbleLogic.backBubbleVisible(
                        hidden = browserPrefs.backBubbleHidden,
                        fullscreen = customView != null,
                        pip = isInPip,
                        ime = WindowInsets.ime.getBottom(density) > 0,
                        editMode = isEditMode,
                        findMode = isFindMode,
                    ),
                    toolbarPosition = browserPrefs.toolbarPosition,
                    barHeightPx = totalBarHeightPx,
                    barTranslation = { toolbarTranslation.value },
                    side = browserPrefs.backBubbleSide,
                    yFraction = browserPrefs.backBubbleY,
                    modifier = Modifier.zIndex(4f),
                )

                // Edit Mode overlay (when typing a URL over an existing page)
                if (isEditMode && currentTab != null && currentTab.url != "about:blank" && currentTab.url.isNotEmpty()) {
                    BrowserEditMode(
                        initialText = currentTab.url,
                        searchEngine = browserPrefs.searchEngine,
                        datasetSearchEngine = datasetSearchEngine,
                        userStore = tabManager.userStore,
                        toolbarPosition = browserPrefs.toolbarPosition,
                        autofocus = true,
                        onNavigate = { url ->
                            isEditMode = false
                            tabManager.loadUrl(currentTab.id, url)
                        },
                        onDismiss = { isEditMode = false },
                        onCloseActivity = onFinishActivity,
                        modifier = Modifier.zIndex(6f),
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
                    )
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
    if (hooks != null) {
        val pendingDownload = hooks.pendingDownloadDialog
        if (pendingDownload != null) {
            DownloadDialog(
                state = pendingDownload,
                onConfirm = {
                    hooks.pendingDownloadDialog = null
                    hooks.executeDownload(context, pendingDownload.url, pendingDownload.filename, pendingDownload.userAgent, pendingDownload.mimeType)
                },
                onCancel = { hooks.pendingDownloadDialog = null },
            )
        }

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
            },
            onHideElement = {
                showShieldSheet = false
                tabManager.startElementPicker(currentTab.id)
            },
            onAllowHost = { host ->
                tabManager.allowBlockedHost(currentTab.id, host)
            },
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
                tabManager.openTab(url = "about:blank", title = "New tab", isPrivate = isPrivate, select = true)
            },
            onCloseAll = {
                showTabsSheet = false
                tabManager.closeAllTabs()
                onFinishActivity()
            },
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

private fun barsLog(msg: String) {
    if (io.github.omeg4dev.fmhy.BuildConfig.DEBUG) android.util.Log.d("FmhyBars", msg)
}
