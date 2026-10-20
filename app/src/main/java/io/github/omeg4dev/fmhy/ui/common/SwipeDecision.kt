package io.github.omeg4dev.fmhy.ui.common

import kotlin.math.max
import kotlin.math.min

/** Distance the row must be dragged before releasing commits the action. */
fun swipeThresholdPx(rowWidthPx: Float, minPx: Float): Float = max(0.4f * rowWidthPx, minPx)

/**
 * Maps a raw drag distance (negative = end to start, the only allowed direction) to the visual row offset.
 * Positive values are blocked, past 60 % of the width the drag is rubber-banded, never beyond 75 %.
 */
fun clampSwipeOffset(rawPx: Float, rowWidthPx: Float): Float {
    if (rawPx >= 0f) return 0f
    val free = 0.6f * rowWidthPx
    val dist = -rawPx
    val shown = if (dist <= free) dist else free + (dist - free) * 0.2f
    return -min(shown, 0.75f * rowWidthPx)
}

enum class SwipeAction { NONE, SAVE, REMOVE }

/** Decision on release only; velocity is ignored on purpose so a flick cannot save by accident. */
fun swipeActionOnRelease(offsetPx: Float, thresholdPx: Float, wasSaved: Boolean): SwipeAction =
    if (offsetPx <= -thresholdPx) {
        if (wasSaved) SwipeAction.REMOVE else SwipeAction.SAVE
    } else {
        SwipeAction.NONE
    }
