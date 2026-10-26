package io.github.omeg4dev.fmhy.browser

import kotlin.math.pow
import kotlin.math.roundToInt

object ZoomMath {
    /** Returns the multiplicative factor to transition from [fromLevel] to [toLevel], clamping both. */
    fun zoomFactor(fromLevel: Float, toLevel: Float): Float {
        val from = ZoomLogic.clamp(fromLevel)
        val to = ZoomLogic.clamp(toLevel)
        return if (from > 0f) to / from else 1f
    }

    /** Multiplicative factor per step so that applying it [steps] times yields [totalFactor]. */
    fun stepFactor(totalFactor: Float, steps: Int): Float {
        if (steps <= 0 || totalFactor <= 0f) return 1f
        return totalFactor.toDouble().pow(1.0 / steps).toFloat()
    }

    /**
     * Computes the new scroll position so that content under [tapPos] stays at [tapPos]
     * after scaling by [factor].
     */
    fun targetScroll(scrollBefore: Int, tapPos: Float, factor: Float): Int {
        return ((scrollBefore + tapPos) * factor - tapPos).roundToInt()
    }

    /** Difference between [targetScroll] and [currentScroll]. */
    fun scrollDelta(targetScroll: Int, currentScroll: Int): Int = targetScroll - currentScroll

    /** True when the raw zoom level has fallen below normal page width by more than tolerance. */
    fun isZoomOutBelowNormal(level: Float): Boolean = level < (ZoomLogic.MIN - 0.02f)

    /** True when the raw zoom level has exceeded maximum limit by more than tolerance. */
    fun isZoomInAboveMax(level: Float): Boolean = level > (ZoomLogic.MAX + 0.02f)
}
