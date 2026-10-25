package io.github.omeg4dev.fmhy.ui.common

import kotlin.math.max

/**
 * Returns the target subsection index after a horizontal swipe release, or null if no transition should occur.
 *
 * Total horizontal drag <= -threshold (end-to-start, RTL-aware: flip sign) -> next subsection;
 * >= threshold -> previous subsection; threshold = max(0.25 * widthPx, minPx).
 * Null at first/last page and under threshold.
 */
fun pageSwipeTarget(
    dragPx: Float,
    widthPx: Float,
    minPx: Float,
    current: Int,
    count: Int,
    rtl: Boolean,
): Int? {
    if (count < 2 || current !in 0 until count) return null
    val threshold = max(0.25f * widthPx, minPx)
    val effectiveDrag = if (rtl) -dragPx else dragPx
    return when {
        effectiveDrag <= -threshold -> if (current + 1 < count) current + 1 else null
        effectiveDrag >= threshold -> if (current - 1 >= 0) current - 1 else null
        else -> null
    }
}
