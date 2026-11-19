package io.github.omeg4dev.fmhy.browser

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.data.BookmarksRepository
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ToolbarPosition
import io.github.omeg4dev.fmhy.data.search.SearchEngine as DatasetSearchEngine
import io.github.omeg4dev.fmhy.ui.common.AppHaptics
import io.github.omeg4dev.fmhy.ui.common.LocalBookmarks

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
    val s = rememberBrowserScreenScope(
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
    )
    with(s) {
        BrowserScreenEffects()
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
                if (!isInPip) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .align(Alignment.TopCenter)
                            .zIndex(10f),
                    )
                }

                // 2. Navigation bar background if toolbar is at bottom
                if (browserPrefs.toolbarPosition == ToolbarPosition.BOTTOM && !isInPip) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .align(Alignment.BottomCenter)
                            .zIndex(10f),
                    )
                }

                // 3. WebView container fills whole area between status bar and navigation bar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        ),
                ) {
                    BrowserScreenPage(this)
                    BrowserScreenBars(this)
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

                if (isTopBar && isBlockedBarVisible && currentBlockedState != null) {
                    BlockedBar(
                        state = currentBlockedState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 12.dp)
                            .onSizeChanged { blockedBarHeightPx = it.height }
                            .zIndex(4f),
                    )
                }

                // SnackbarHost
                if (!isInPip) {
                    val blockedBarPadding = if (isBlockedBarVisible) {
                        with(density) { blockedBarHeightPx.toDp() } + 8.dp
                    } else {
                        0.dp
                    }
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = if (browserPrefs.toolbarPosition == ToolbarPosition.BOTTOM) {
                                    with(density) { totalBarHeightPx.toDp() } + blockedBarPadding + 8.dp
                                } else {
                                    16.dp + blockedBarPadding
                                }
                            )
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .zIndex(15f),
                    )
                }
            }
        }
        BrowserScreenSheets()
        BrowserScreenDialogs()
    }
}

internal fun barsLog(msg: String) {
    if (io.github.omeg4dev.fmhy.BuildConfig.DEBUG) android.util.Log.d("FmhyBars", msg)
}

internal fun animateZoom(
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
