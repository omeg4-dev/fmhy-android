package io.github.omeg4dev.fmhy.browser

/** Overlays, toolbars, and bars for [BrowserScreen]. */

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.omeg4dev.fmhy.data.ToolbarPosition
import io.github.omeg4dev.fmhy.data.user.UserBookmark
import io.github.omeg4dev.fmhy.ui.common.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun BrowserScreenScope.BrowserScreenBars(boxScope: BoxScope) {
    with(boxScope) {
                // Toolbar overlay in Box (moving it never moves the page)
                if (!isInPip && customView == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(if (browserPrefs.toolbarPosition == ToolbarPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter)
                            .graphicsLayer {
                                translationY = toolbarTranslation.value
                            }
                            .zIndex(5f),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (!isTopBar && isBlockedBarVisible && currentBlockedState != null) {
                                BlockedBar(
                                    state = currentBlockedState,
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .onSizeChanged { blockedBarHeightPx = it.height },
                                 )
                            }
                            BrowserToolbar(
                                tab = currentTab,
                                tabs = tabs,
                                tabCount = tabs.size,
                                tabPulseKey = pulseKey,
                                toolbarPosition = browserPrefs.toolbarPosition,
                                modifier = Modifier.onSizeChanged { totalBarHeightPx = it.height.toFloat() },
                                isFindMode = isFindMode,
                                findQuery = findQuery,
                                findActiveIndex = findActiveIndex,
                                findTotalMatches = findTotalMatches,
                                showBlockedCount = blockingPrefs.showBlockedCount,
                                isSaved = isSaved,
                                connectionVpn = connectionVpn,
                                onConnectionClick = { showConnectionSheet = true },
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
                                onSavePictureClick = {
                                    showPictureChoice = true
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
                                onShareClick = {
                                    currentTab?.takeIf { it.hasWebAddress() }?.let { tab ->
                                        if (!shareWebAddress(context, tab.url, tab.title)) {
                                            scope.launch { snackbarHostState.showSnackbar("Couldn't share this page") }
                                        }
                                    }
                                },
                                onOpenInBrowserClick = {
                                    currentTab?.takeIf { it.hasWebAddress() }?.let { tab ->
                                        val message = when (openInExternalBrowser(context, tab.url)) {
                                            ExternalOpenResult.OPENED -> null
                                            ExternalOpenResult.NONE_FOUND -> "No other browser found"
                                            ExternalOpenResult.FAILED -> "Couldn't open another browser"
                                        }
                                        if (message != null) scope.launch { snackbarHostState.showSnackbar(message) }
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
                        onProxySettings = {
                            tabManager.openProxySettings()
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
}
