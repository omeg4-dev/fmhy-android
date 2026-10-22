package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import io.github.omeg4dev.fmhy.data.model.Entry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Swipe an entry row from end to start to save or remove it. The action is decided and shown when the drag
 * starts, only performed once the finger is released past the threshold and the row has sprung back.
 */
@Composable
fun SwipeToSave(
    entry: Entry,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val actions = rememberSaveActions()
    val isSaved = actions.isSaved(entry)
    val currentActions by rememberUpdatedState(actions)
    val currentEntry by rememberUpdatedState(entry)

    val a11yModifier = if (enabled) {
        Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(
                    label = if (isSaved) "Remove from Saved" else "Save",
                    action = {
                        actions.toggle(entry)
                        true
                    },
                )
            )
        }
    } else {
        Modifier
    }

    SwipeActionRow(
        enabled = enabled,
        gestureKey = entry.id,
        actionAtDragStart = {
            if (currentActions.isSaved(currentEntry)) SwipeAction.REMOVE else SwipeAction.SAVE
        },
        removeIcon = Icons.Outlined.BookmarkRemove,
        onCommit = { action ->
            when (action) {
                SwipeAction.SAVE -> currentActions.save(currentEntry)
                SwipeAction.REMOVE -> currentActions.unsave(currentEntry)
                SwipeAction.NONE -> Unit
            }
        },
        modifier = modifier.then(a11yModifier),
        content = content,
    )
}

/**
 * Shared end-to-start swipe row. [actionAtDragStart] is read once per gesture (SAVE or REMOVE) and the
 * background shows only that action for the whole gesture. [onCommit] runs once, right on release past the
 * threshold (the row springs back in parallel).
 */
@Composable
internal fun SwipeActionRow(
    enabled: Boolean,
    gestureKey: Any?,
    actionAtDragStart: () -> SwipeAction,
    removeIcon: ImageVector,
    onCommit: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var frozen by remember { mutableStateOf<SwipeAction?>(null) }
    var pastThreshold by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val minThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }

    val currentStart by rememberUpdatedState(actionAtDragStart)
    val currentCommit by rememberUpdatedState(onCommit)
    val currentHaptics by rememberUpdatedState(haptics)
    val currentRtl by rememberUpdatedState(rtl)
    val currentMin by rememberUpdatedState(minThresholdPx)

    val iconScale by animateFloatAsState(
        targetValue = if (pastThreshold) 1f else 0.8f,
        animationSpec = Motion.standard(Motion.SHORT),
        label = "swipeIconScale",
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (pastThreshold) 1f else 0.55f,
        animationSpec = Motion.standard(Motion.SHORT),
        label = "swipeBgAlpha",
    )

    val gestureModifier = if (enabled) {
        Modifier.pointerInput(gestureKey, enabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (settling) return@awaitEachGesture
                // Signed so that negative always means end to start, also in RTL.
                val sign = if (currentRtl) -1f else 1f
                var overshoot = 0f
                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    change.consume()
                    overshoot = over
                }
                if (drag == null) return@awaitEachGesture
                val action = currentStart()
                val width = widthPx
                val threshold = swipeThresholdPx(width, currentMin)
                frozen = action
                var raw = overshoot * sign
                var past = false
                fun update() {
                    val logical = clampSwipeOffset(raw, width)
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(logical * sign) }
                    val nowPast = logical <= -threshold
                    if (nowPast != past) {
                        past = nowPast
                        pastThreshold = nowPast
                        currentHaptics.tick()
                    }
                }
                update()
                val completed = horizontalDrag(drag.id) { change ->
                    raw += change.positionChange().x * sign
                    change.consume()
                    update()
                }
                val logicalEnd = clampSwipeOffset(raw, width)
                val decided = if (completed) {
                    swipeActionOnRelease(logicalEnd, threshold, action == SwipeAction.REMOVE)
                } else {
                    SwipeAction.NONE
                }
                settling = true
                // Commit on finger-up: the save / remove must not wait for the spring back. It happens
                // here, once, so a row scrolled out of composition mid-spring cannot lose or repeat it.
                if (decided != SwipeAction.NONE) currentCommit(decided)
                scope.launch {
                    try {
                        offset.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        )
                    } finally {
                        settling = false
                        pastThreshold = false
                        frozen = null
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .then(gestureModifier),
    ) {
        val shown = frozen
        if (shown != null) {
            val isSave = shown == SwipeAction.SAVE
            val container = if (isSave) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            val onContainer = if (isSave) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(bgAlpha)
                    .background(container)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (isSave) "Save" else "Remove",
                        style = MaterialTheme.typography.labelLarge,
                        color = onContainer,
                    )
                    Icon(
                        imageVector = if (isSave) Icons.Filled.BookmarkAdd else removeIcon,
                        contentDescription = null,
                        tint = onContainer,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().offset { IntOffset(offset.value.roundToInt(), 0) }) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                content()
            }
        }
    }
}
