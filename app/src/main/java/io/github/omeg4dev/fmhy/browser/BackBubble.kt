package io.github.omeg4dev.fmhy.browser

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.BubbleSide
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ToolbarPosition
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val BackBubbleSize = 40.dp
private val BackBubbleMargin = 8.dp

/**
 * Small draggable "back to FMHY" bubble over the browser. Tap returns to FMHY, hold opens a menu to hide it,
 * a drag moves it (snaps to the nearest side, position persisted). It slides away with the toolbar on scroll.
 * Position is [BrowserPrefs.backBubbleSide] / [BrowserPrefs.backBubbleY] (0..1 of the allowed vertical range).
 */
@Composable
fun BackBubble(
    settingsRepository: SettingsRepository,
    visible: Boolean,
    toolbarPosition: ToolbarPosition,
    /** Height of the toolbar block in px (0 until measured). */
    barHeightPx: Float,
    /** Current toolbar translation in px; read inside a graphics layer so scrolling doesn't recompose. */
    barTranslation: () -> Float,
    side: BubbleSide,
    yFraction: Float,
    /** Called when the user taps the bubble to return to FMHY; handles empty-tab cleanup. */
    onBackToFmhy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current

    var container by remember { mutableStateOf(IntSize.Zero) }
    val sizePx = with(density) { BackBubbleSize.toPx() }
    val marginPx = with(density) { BackBubbleMargin.toPx() }
    val isTop = toolbarPosition == ToolbarPosition.TOP
    val minY = BubbleLogic.backBubbleMinY(
        WindowInsets.statusBars.getTop(density).toFloat(), if (isTop) barHeightPx else 0f, marginPx,
    )
    val maxY = BubbleLogic.backBubbleMaxY(
        container.height.toFloat(), WindowInsets.navigationBars.getBottom(density).toFloat(),
        if (isTop) 0f else barHeightPx, sizePx, marginPx,
    )

    val x = remember { Animatable(0f) }
    val y = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showHide by remember { mutableStateOf(false) }
    // Set when a hold fired: the rest of that touch must not move the bubble.
    var held by remember { mutableStateOf(false) }

    val rangeState = rememberUpdatedState(Pair(minY, maxY))
    val containerState = rememberUpdatedState(container)

    LaunchedEffect(container, side, yFraction, minY, maxY) {
        if (container.width > 0 && !dragging) {
            x.snapTo(BubbleLogic.edgeX(side, sizePx, container.width.toFloat(), marginPx))
            y.snapTo(BubbleLogic.fractionToY(yFraction, minY, maxY))
        }
    }
    LaunchedEffect(visible) { if (!visible) { showMenu = false; showHide = false } }

    Box(modifier = modifier.fillMaxSize().onSizeChanged { container = it }) {
        if (visible && container.width > 0) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
                    .graphicsLayer {
                        val f = BubbleLogic.barHiddenFraction(barTranslation(), barHeightPx)
                        alpha = 1f - f
                        // Bottom bar: follow it down. Top bar: slide out sideways to the bubble's own edge.
                        if (isTop) {
                            val dir = if (side == BubbleSide.LEFT) -1f else 1f
                            translationX = dir * f * (sizePx + marginPx)
                        } else {
                            translationY = barTranslation()
                        }
                    }
                    .size(BackBubbleSize)
                    // It sits at the screen edge: keep the system back gesture from stealing the drag.
                    .systemGestureExclusion()
                    .semantics { contentDescription = "Back to FMHY" }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { held = false },
                            onTap = { onBackToFmhy() },
                            onLongPress = {
                                held = true
                                haptics.longPress()
                                showMenu = true
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { if (!held) dragging = true },
                            onDragEnd = {
                                if (dragging) {
                                    dragging = false
                                    scope.launch {
                                        val (lo, hi) = rangeState.value
                                        val w = containerState.value.width.toFloat()
                                        val s = BubbleLogic.nearestSide(x.value, sizePx, w)
                                        val frac = BubbleLogic.yToFraction(y.value, lo, hi)
                                        settingsRepository.updateBrowser { it.copy(backBubbleSide = s, backBubbleY = frac) }
                                        x.animateTo(BubbleLogic.edgeX(s, sizePx, w, marginPx), Motion.standard())
                                    }
                                }
                            },
                            onDragCancel = { dragging = false },
                        ) { change, drag: Offset ->
                            if (held) return@detectDragGestures
                            change.consume()
                            val (lo, hi) = rangeState.value
                            val w = containerState.value.width.toFloat()
                            scope.launch {
                                x.snapTo((x.value + drag.x).coerceIn(0f, (w - sizePx).coerceAtLeast(0f)))
                                y.snapTo(BubbleLogic.clampY(y.value + drag.y, lo, hi))
                            }
                        }
                    },
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Hide back-to-FMHY button") },
                        leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                        onClick = { showMenu = false; showHide = true },
                    )
                }
            }
        }
    }

    if (showHide) {
        AlertDialog(
            onDismissRequest = { showHide = false },
            title = { Text("Hide the back-to-FMHY button?") },
            text = { Text("Turn it back on in Settings → Browsing → Back-to-FMHY button. The FMHY logo in the address bar and \"Back to FMHY\" in the menu still return to FMHY.") },
            confirmButton = {
                TextButton(onClick = {
                    showHide = false
                    scope.launch { settingsRepository.updateBrowser { it.copy(backBubbleHidden = true) } }
                }) { Text("Hide") }
            },
            dismissButton = { TextButton(onClick = { showHide = false }) { Text("Cancel") } },
        )
    }
}
