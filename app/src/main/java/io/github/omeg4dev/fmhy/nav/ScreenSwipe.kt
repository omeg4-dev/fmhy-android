package io.github.omeg4dev.fmhy.nav

import kotlin.math.abs
import kotlin.math.max

/**
 * Pure logic for screen swiping between bottom-bar tabs.
 *
 * Threshold = max(0.25 * widthPx, minPx).
 * Effective drag = if (rtl) -dragPx else dragPx.
 * Drag <= -threshold -> next tab in [Tab.entries].
 * Drag >= threshold -> previous tab in [Tab.entries].
 * Null at first/last page (no wrap), under threshold, or if disabled.
 */
fun screenSwipeTarget(
    dragPx: Float,
    widthPx: Float,
    minPx: Float,
    current: Tab,
    rtl: Boolean,
    enabled: Boolean,
): Tab? {
    if (!enabled) return null
    val threshold = max(0.25f * widthPx, minPx)
    val effectiveDrag = if (rtl) -dragPx else dragPx
    val currentIndex = current.ordinal
    return when {
        effectiveDrag <= -threshold -> {
            if (currentIndex + 1 < Tab.entries.size) Tab.entries[currentIndex + 1] else null
        }
        effectiveDrag >= threshold -> {
            if (currentIndex - 1 >= 0) Tab.entries[currentIndex - 1] else null
        }
        else -> null
    }
}

/**
 * Returns true if horizontal drag is dominant (|dx| > 2 * |dy|).
 */
fun isHorizontalIntent(dx: Float, dy: Float): Boolean = abs(dx) > 2 * abs(dy)
