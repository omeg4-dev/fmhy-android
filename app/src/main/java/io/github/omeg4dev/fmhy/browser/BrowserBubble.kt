package io.github.omeg4dev.fmhy.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.BrowserButtonStyle
import io.github.omeg4dev.fmhy.data.BubbleSide
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Current "Browser button" setting; [BrowserSwitchChip] only shows for [BrowserButtonStyle.TOP_BAR]. */
val LocalBrowserButtonStyle = compositionLocalOf { BrowserButtonStyle.TOP_BAR }

private val BubbleSize = 52.dp
private val BubbleMargin = 8.dp
private val TopBarReserve = 64.dp
private val BottomNavReserve = 80.dp

/**
 * Draggable round bubble overlaid on all FMHY screens: tap opens the browser, hold opens a menu
 * (close a tab, close all tabs, hide the button). Render once at the root of MainActivity's content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserBubble(
    tabManager: TabManager,
    settingsRepository: SettingsRepository,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val prefs by settingsRepository.browserPrefs.collectAsState(initial = tabManager.currentBrowserPrefs)
    val count by tabManager.browserTabCount.collectAsState()
    val tabs by tabManager.tabs.collectAsState()
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    val visible = enabled && !imeVisible && BubbleLogic.isVisible(count, prefs.browserButton, prefs.browserBubbleHidden)

    var container by remember { mutableStateOf(IntSize.Zero) }
    val sizePx = with(density) { BubbleSize.toPx() }
    val marginPx = with(density) { BubbleMargin.toPx() }
    val minY = WindowInsets.statusBars.getTop(density) + with(density) { TopBarReserve.toPx() }
    val maxY = container.height - WindowInsets.navigationBars.getBottom(density) -
        with(density) { BottomNavReserve.toPx() } - sizePx

    val x = remember { Animatable(0f) }
    val y = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showCloseAll by remember { mutableStateOf(false) }
    var showHide by remember { mutableStateOf(false) }

    val prefsState = rememberUpdatedState(prefs)
    val rangeState = rememberUpdatedState(Pair(minY, maxY))
    val containerState = rememberUpdatedState(container)

    // Place at the persisted side/fraction whenever the layout or the prefs change (not while dragging).
    LaunchedEffect(container, prefs.bubbleSide, prefs.bubbleY, minY, maxY) {
        if (container.width > 0 && !dragging) {
            x.snapTo(BubbleLogic.edgeX(prefs.bubbleSide, sizePx, container.width.toFloat(), marginPx))
            y.snapTo(BubbleLogic.fractionToY(prefs.bubbleY, minY, maxY))
        }
    }
    // The menu closes itself when nothing is left to manage.
    LaunchedEffect(visible) { if (!visible) { showMenu = false; showCloseAll = false; showHide = false } }

    Box(modifier = modifier.fillMaxSize().onSizeChanged { container = it }) {
        AnimatedVisibility(
            visible = visible && container.width > 0,
            enter = fadeIn(Motion.enter()) + scaleIn(Motion.enter(), initialScale = 0.8f),
            exit = fadeOut(Motion.exit()) + scaleOut(Motion.exit(), targetScale = 0.8f),
            modifier = Modifier.offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) },
        ) {
            Box(
                modifier = Modifier
                    .size(BubbleSize)
                    .semantics { contentDescription = "Open browser, $count tabs" }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { bringBrowserToFront(context) },
                            onLongPress = {
                                haptics.longPress()
                                showMenu = true
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                scope.launch {
                                    val (lo, hi) = rangeState.value
                                    val w = containerState.value.width.toFloat()
                                    val side = BubbleLogic.nearestSide(x.value, sizePx, w)
                                    val frac = BubbleLogic.yToFraction(y.value, lo, hi)
                                    settingsRepository.updateBrowser { it.copy(bubbleSide = side, bubbleY = frac) }
                                    x.animateTo(BubbleLogic.edgeX(side, sizePx, w, marginPx), Motion.standard())
                                }
                            },
                            onDragCancel = { dragging = false },
                        ) { change, drag: Offset ->
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
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Tab, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .heightIn(min = 18.dp)
                        .widthIn18()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        text = BubbleLogic.badgeText(count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }

    if (showMenu) {
        ModalBottomSheet(onDismissRequest = { showMenu = false }) {
            BubbleMenu(
                tabs = tabs,
                totalCount = count,
                onCloseTab = { id ->
                    haptics.remove()
                    tabManager.closeTab(id)
                },
                onCloseAll = { showMenu = false; showCloseAll = true },
                onHide = { showMenu = false; showHide = true },
            )
        }
    }

    if (showCloseAll) {
        AlertDialog(
            onDismissRequest = { showCloseAll = false },
            title = { Text("Close all $count tabs?") },
            confirmButton = {
                TextButton(onClick = {
                    showCloseAll = false
                    haptics.remove()
                    tabManager.closeAllTabsIncludingRestorable()
                }) { Text("Close all") }
            },
            dismissButton = { TextButton(onClick = { showCloseAll = false }) { Text("Cancel") } },
        )
    }

    if (showHide) {
        AlertDialog(
            onDismissRequest = { showHide = false },
            title = { Text("Hide the browser button?") },
            text = { Text("It stays hidden until you turn it back on in Settings → Browsing → Browser button.") },
            confirmButton = {
                TextButton(onClick = {
                    showHide = false
                    scope.launch { settingsRepository.updateBrowser { it.copy(browserBubbleHidden = true) } }
                }) { Text("Hide") }
            },
            dismissButton = { TextButton(onClick = { showHide = false }) { Text("Cancel") } },
        )
    }
}

private fun Modifier.widthIn18(): Modifier = this.then(Modifier.widthIn(min = 18.dp))

@Composable
private fun BubbleMenu(
    tabs: List<TabState>,
    totalCount: Int,
    onCloseTab: (Long) -> Unit,
    onCloseAll: () -> Unit,
    onHide: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).animateContentSize()) {
        if (tabs.isNotEmpty()) {
            ListItem(
                headlineContent = { Text("Close a tab…") },
                leadingContent = { Icon(Icons.Filled.Close, contentDescription = null) },
                modifier = Modifier.clickable { expanded = !expanded },
            )
            if (expanded) {
                tabs.forEach { tab ->
                    ListItem(
                        headlineContent = {
                            Text(tabLabel(tab), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = {
                            val fav = tab.favicon
                            if (fav != null) {
                                Image(
                                    bitmap = fav.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).clip(CircleShape),
                                )
                            } else {
                                Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(24.dp))
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { onCloseTab(tab.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close ${tabLabel(tab)}")
                            }
                        },
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
        ListItem(
            headlineContent = { Text("Close all tabs") },
            supportingContent = { Text("$totalCount open") },
            leadingContent = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onCloseAll),
        )
        ListItem(
            headlineContent = { Text("Hide button") },
            leadingContent = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onHide),
        )
    }
}

private fun tabLabel(tab: TabState): String {
    if (tab.title.isNotBlank() && tab.title != "about:blank") return tab.title
    val host = try { android.net.Uri.parse(tab.url).host } catch (_: Exception) { null }
    return host?.takeIf { it.isNotBlank() } ?: "New tab"
}
