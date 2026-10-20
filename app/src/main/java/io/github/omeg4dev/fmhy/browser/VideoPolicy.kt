package io.github.omeg4dev.fmhy.browser

import android.content.pm.ActivityInfo
import io.github.omeg4dev.fmhy.data.FullscreenOrientation

/** A width:height ratio kept as plain ints so it is testable without android.util.Rational. */
data class Ratio(val num: Int, val den: Int) {
    val value: Float get() = num.toFloat() / den.toFloat()
}

/** Android only accepts picture-in-picture ratios between 1:2.39 and 2.39:1. */
private const val MAX_PIP_RATIO = 2.39f
private val PIP_DEFAULT = Ratio(16, 9)
private val PIP_WIDEST = Ratio(239, 100)
private val PIP_TALLEST = Ratio(100, 239)

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/** The picture-in-picture ratio for a video of [width] x [height]; 16:9 when unknown. */
fun pipRatio(width: Int, height: Int): Ratio {
    if (width <= 0 || height <= 0) return PIP_DEFAULT
    val ratio = width.toFloat() / height.toFloat()
    return when {
        ratio > MAX_PIP_RATIO -> PIP_WIDEST
        ratio < 1f / MAX_PIP_RATIO -> PIP_TALLEST
        else -> {
            val g = gcd(width, height)
            Ratio(width / g, height / g)
        }
    }
}

/**
 * The requestedOrientation to use while a video is fullscreen. AUTO uses landscape for videos at
 * least as wide as tall (or of unknown shape) and portrait for taller ones.
 */
fun fullscreenRequestedOrientation(mode: FullscreenOrientation, videoAspect: Float?): Int = when (mode) {
    FullscreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    FullscreenOrientation.FOLLOW_PHONE -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    FullscreenOrientation.AUTO ->
        if (videoAspect == null || videoAspect >= 1f) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
}
