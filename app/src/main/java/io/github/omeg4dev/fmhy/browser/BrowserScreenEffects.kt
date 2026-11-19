package io.github.omeg4dev.fmhy.browser

/** Side effects and back navigation handlers for [BrowserScreen]. */

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.tween
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.omeg4dev.fmhy.browser.adblock.SelectorPicker
import io.github.omeg4dev.fmhy.browser.download.DownloadEvent
import io.github.omeg4dev.fmhy.ui.common.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun BrowserScreenScope.BrowserScreenEffects() {
    LaunchedEffect(currentTabId) {
        isEditMode = false
    }
    LaunchedEffect(showTabsRequested) {
        if (showTabsRequested) {
            showTabsSheet = true
            tabManager.consumeShowTabs()
        }
    }
    LaunchedEffect(showDownloadsRequested) {
        if (showDownloadsRequested) {
            showDownloadsSheet = true
            tabManager.consumeShowDownloads()
        }
    }

    LaunchedEffect(currentTab?.id, currentTab?.url?.substringBefore('#')) {
        // Fragment-only changes (scroll-spy pages rewrite #hash while scrolling) are not a navigation.
        if (captureJob != null && (currentTab?.id != capturedTabId || currentTab?.url?.substringBefore('#') != capturedTabUrl)) {
            captureJob?.cancel()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            captureJob?.cancel()
        }
    }

    LaunchedEffect(downloadController) {
        downloadController.notices.collect { notice ->
            snackbarHostState.showSnackbar(notice)
        }
    }

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

    // Ensure toolbar is fully shown at top, during loading, find, edit, or navigation
    // Keyed on the URL without its fragment: in-page hash changes must not pop the bars back up.
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

    // Blocked notices
    LaunchedEffect(hooks) {
        hooks?.blockedFlow?.collect { event ->
            val state = stateFor(event.tabId)
            val appeared = state.add(event.entry)
            if (appeared && event.tabId == latestTabId) {
                val feedbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
                view.performHapticFeedback(feedbackConstant)
            }
        }
    }

    LaunchedEffect(hooks) {
        hooks?.blockedClearFlow?.collect { tabId ->
            blockedStates[tabId]?.clear()
        }
    }

    LaunchedEffect(currentTabId, navKey) {
        if (prevTabId != currentTabId) {
            prevTabId?.let { blockedStates[it]?.clear() }
            prevTabId = currentTabId
            prevNavKey = navKey
        } else if (prevNavKey != navKey) {
            currentTabId?.let { blockedStates[it]?.clear() }
            prevNavKey = navKey
        }
    }

    LaunchedEffect(currentTabIds) {
        val removed = blockedStates.keys - currentTabIds
        for (id in removed) {
            blockedStates.remove(id)
        }
    }

    // Tab notice
    LaunchedEffect(hooks) {
        hooks?.tabNoticeFlow?.collect { notice ->
            tabNoticeJob?.cancel()
            snackbarHostState.currentSnackbarData?.dismiss()
            tabNoticeJob = scope.launch {
                val (msg, action, onAct) = when (notice) {
                    is TabNotice.Single -> Triple(
                        "Opened in a new tab",
                        "Switch",
                        { tabManager.selectTab(notice.tabId) }
                    )
                    is TabNotice.Merged -> Triple(
                        "${notice.count} tabs opened in the background",
                        "Show tabs",
                        { tabManager.requestShowTabs() }
                    )
                }
                withTimeoutOrNull(4000L) {
                    val result = snackbarHostState.showSnackbar(
                        message = msg,
                        actionLabel = action,
                        duration = SnackbarDuration.Indefinite,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onAct()
                    }
                }
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        }
    }

    // Tab counter pulse
    LaunchedEffect(hooks) {
        hooks?.tabPulseFlow?.collect {
            pulseKey++
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
    LaunchedEffect(tabs, restored) {
        if (shouldFinishWhenEmpty(restored, tabs.size, currentTabId, tabManager.linkQueue.hasPendingLinks())) {
            onFinishActivity()
        }
    }

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

    BackHandler(enabled = isCapturing) {
        captureJob?.cancel()
    }
}
