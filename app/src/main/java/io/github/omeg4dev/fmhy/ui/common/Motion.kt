package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared motion tokens (Material 3 emphasized curves) so every screen moves the same way. */
object Motion {
    const val SHORT = 150
    const val MEDIUM = 250
    const val LONG = 400

    /** Delay between items of a staggered entrance; the stagger stops growing after [STAGGER_MAX_ITEMS]. */
    const val STAGGER_STEP = 40
    const val STAGGER_MAX_ITEMS = 8

    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> standard(durationMillis: Int = MEDIUM): FiniteAnimationSpec<T> =
        tween(durationMillis, easing = Emphasized)

    fun <T> enter(durationMillis: Int = MEDIUM, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis, easing = EmphasizedDecelerate)

    fun <T> exit(durationMillis: Int = SHORT): FiniteAnimationSpec<T> =
        tween(durationMillis, easing = EmphasizedAccelerate)
}

/**
 * Delay in ms for an item of a list's entrance stagger, or null when it must not animate: only the first
 * [Motion.STAGGER_MAX_ITEMS] items that compose within [STAGGER_WINDOW_MS] of the list's first item take part;
 * everything composed later (scrolling into view) appears at once instead of fading in over blank space.
 */
fun staggerDelayOrNull(index: Int, msSinceListStart: Long): Int? {
    if (index >= Motion.STAGGER_MAX_ITEMS || msSinceListStart > STAGGER_WINDOW_MS) return null
    return index.coerceAtLeast(0) * Motion.STAGGER_STEP
}

internal const val STAGGER_WINDOW_MS = 300L
private const val STAGGER_FADE_MS = 300

/**
 * Remembers when the items of each list (by `enterStagger` key) first composed, so [Modifier.enterStagger]
 * can tell a list's entrance from items scrolling into the lazy window. A key not seen for [gapMs] starts
 * a new entrance (screen opened again). Small LRU; thread-safe.
 */
internal class EntranceTracker(
    private val now: () -> Long,
    private val gapMs: Long = 1500L,
    private val capacity: Int = 32,
) {
    private class Mark(val start: Long, var last: Long)

    private val marks = object : LinkedHashMap<Any?, Mark>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any?, Mark>?) = size > capacity
    }

    /** Milliseconds since the first item of the entrance for [key] composed (0 for the first). */
    @Synchronized
    fun msSinceListStart(key: Any?): Long {
        val t = now()
        val m = marks[key]
        if (m == null || t - m.last >= gapMs) {
            marks[key] = Mark(t, t)
            return 0L
        }
        m.last = t
        return t - m.start
    }
}

private val entranceTracker = EntranceTracker(now = { android.os.SystemClock.uptimeMillis() })

/**
 * Fades and slides the element up into place when its list opens. [index] staggers siblings (capped),
 * [key] identifies the list and restarts the entrance when it changes (e.g. a new pager page). Items that
 * compose later than the entrance (scrolling) are shown immediately.
 */
fun Modifier.enterStagger(index: Int = 0, key: Any? = Unit, offset: Dp = 24.dp): Modifier = composed {
    val delayMs = remember(key) { staggerDelayOrNull(index, entranceTracker.msSinceListStart(key)) }
    entranceFade(key, delayMs, offset)
}

/**
 * One entrance of a lazy list, owned by the screen's composition (see [rememberListEntrance]). Rows claim a
 * position in the order they compose, so rows of different groups fade strictly top to bottom. The entrance
 * starts with the first claim and closes for good [STAGGER_WINDOW_MS] later: rows that compose afterwards
 * (scrolling, even after a pause) appear at once instead of fading in over blank space.
 */
class ListEntrance internal constructor(private val now: () -> Long) {
    constructor() : this({ android.os.SystemClock.uptimeMillis() })

    private var start = -1L
    private var next = 0
    private var closed = false

    /** Fade delay in ms for the next row, or null when it must appear at once. */
    @Synchronized
    fun claim(): Int? {
        if (closed) return null
        val t = now()
        if (start < 0) start = t
        val delay = staggerDelayOrNull(next.coerceAtMost(Motion.STAGGER_MAX_ITEMS - 1), t - start)
        if (delay == null) closed = true else next++
        return delay
    }

    /** Ends the entrance: call before a programmatic scroll so the rows it brings into view appear at once. */
    @Synchronized
    fun close() {
        closed = true
    }
}

/** A [ListEntrance] per list; a new [key] (new tab, new result set) starts a new entrance. */
@Composable
fun rememberListEntrance(key: Any?): ListEntrance = remember(key) { ListEntrance() }

/**
 * Entrance fade for the rows of a lazy list: the delay comes from the order the rows compose in ([entrance]),
 * not from a caller index. Rows past [Motion.STAGGER_MAX_ITEMS] share the last delay instead of appearing before
 * the rows above them. Rows that are already composed keep their state when the entrance changes.
 */
fun Modifier.enterStaggerOrdered(entrance: ListEntrance, offset: Dp = 24.dp): Modifier = composed {
    val delayMs = remember { entrance.claim() }
    entranceFade(Unit, delayMs, offset)
}

@Composable
private fun entranceFade(key: Any?, delayMs: Int?, offset: Dp): Modifier {
    if (delayMs == null) return Modifier
    val progress = remember(key) { Animatable(0f) }
    val offsetPx = with(LocalDensity.current) { offset.toPx() }
    LaunchedEffect(key) {
        progress.animateTo(1f, Motion.enter(STAGGER_FADE_MS, delayMs))
    }
    return Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}

/** Crossfades between states (loading → content, empty → list) with the shared timings. */
@Composable
fun <S> CrossfadeContent(
    targetState: S,
    modifier: Modifier = Modifier,
    contentKey: (S) -> Any? = { it },
    content: @Composable AnimatedContentScope.(S) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        contentKey = contentKey,
        transitionSpec = {
            fadeIn(Motion.enter(Motion.MEDIUM, Motion.SHORT / 2)) togetherWith fadeOut(Motion.exit())
        },
        label = "crossfade",
        content = content,
    )
}

/** Pulsing placeholder rows shown while a list loads, instead of a blank screen. */
@Composable
fun SkeletonRows(count: Int = 6, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(800, easing = Motion.Emphasized), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val color = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }) {
        repeat(count) { i ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(if (i % 2 == 0) 0.45f else 0.6f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(if (i % 3 == 0) 0.8f else 0.7f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color),
                )
            }
        }
    }
}

/** Empty screen with an icon, one line of text and (optionally) one button that leads somewhere. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp).enterStagger(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(36.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Shows "[message] · [actionLabel]"; returns true when the user tapped the action. */
suspend fun SnackbarHostState.showAction(message: String, actionLabel: String): Boolean =
    showSnackbar(message, actionLabel = actionLabel, withDismissAction = false, duration = SnackbarDuration.Short) ==
        SnackbarResult.ActionPerformed

/** Shows "[message] · Undo"; returns true when the user tapped Undo. */
suspend fun SnackbarHostState.showUndo(message: String): Boolean =
    showAction(message, "Undo")
