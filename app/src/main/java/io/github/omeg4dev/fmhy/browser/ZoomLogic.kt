package io.github.omeg4dev.fmhy.browser

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

object ZoomLogic {
    const val MIN = 1.0f
    const val MAX = 5.0f
    const val STEP = 0.25f

    fun clamp(level: Float): Float = level.coerceIn(MIN, MAX)

    fun stepUp(level: Float): Float {
        val eps = 1e-4f
        val next = (floor((level + eps) / STEP) + 1) * STEP
        return clamp(next)
    }

    fun stepDown(level: Float): Float {
        val eps = 1e-4f
        val next = (ceil((level - eps) / STEP) - 1) * STEP
        return clamp(next)
    }

    fun label(level: Float): String = "${(clamp(level) * 100).roundToInt()} %"

    fun showReset(level: Float): Boolean = abs(level - 1f) >= 0.005f

    fun softLimit(raw: Float): Float = when {
        raw < MIN -> {
            val overshoot = MIN - raw
            val damped = (overshoot * 0.25f).coerceAtMost(0.15f)
            MIN - damped
        }
        raw > MAX -> {
            val overshoot = raw - MAX
            val damped = (overshoot * 0.25f).coerceAtMost(0.15f)
            MAX + damped
        }
        else -> raw
    }

    fun doubleTapTarget(level: Float): Float = if (level < 1.5f) 2.5f else 1.0f

    fun ignoresBar(pointerCount: Int): Boolean = pointerCount > 1
}
