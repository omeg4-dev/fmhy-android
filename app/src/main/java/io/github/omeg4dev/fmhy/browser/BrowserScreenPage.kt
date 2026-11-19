package io.github.omeg4dev.fmhy.browser

/** Page content area (start screen, crash state, or active web view) for [BrowserScreen]. */

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.omeg4dev.fmhy.data.ToolbarPosition
import io.github.omeg4dev.fmhy.ui.common.EmptyState
import io.github.omeg4dev.fmhy.ui.common.Motion
import kotlinx.coroutines.launch

@Composable
internal fun BrowserScreenScope.BrowserScreenPage(boxScope: BoxScope) {
    with(boxScope) {
        // Local copies keep the smart cast on currentTab.
        val currentTab = currentTab
        val isBlankTab = currentTab == null || isBlankTab
        if (isBlankTab) {
                    key(currentTab?.id) {
                        BrowserStartScreen(
                            isPrivate = currentTab?.isPrivate == true,
                            userStore = tabManager.userStore,
                            datasetStore = datasetStore,
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
                    val currentIsCapturing by rememberUpdatedState(isCapturing)

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
                                    .padding(top = 16.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        // BUG-17: the page never moves or resizes while it scrolls. A top bar stays put and the page
                        // sits below it; a bottom bar hides over the page. Moving the WebView under a finger flickered.
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = if (isTopBar && !isInPip) with(density) { totalBarHeightPx.toDp() } else 0.dp),
                    ) {
                        if (webView != null) {
                            DisposableEffect(webView, isTopBar) {
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
                                webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                                    val dy = scrollY - oldScrollY
                                    val isAtTop = !webView.canScrollVertically(-1) || scrollY <= 0

                                    if (isAtTop) {
                                        if (!currentIsCapturing) {
                                            barTracker.reset()
                                            if (bars.hidden) {
                                                bars.hidden = false
                                                applyBarsState()
                                            }
                                        }
                                    } else if (!currentIsCapturing && !isTopBar && !isFindMode && !isEditMode && !loadingState && totalBarHeightPx > 0f) {
                                        val hide = barTracker.onScroll(dy)
                                        if (hide != null && hide != bars.hidden) {
                                            bars.hidden = hide
                                            applyBarsState()
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
    }
}
