package io.github.omeg4dev.fmhy.browser

/**
 * Accumulates page scroll in one direction and decides when the browser bars should hide or show.
 * Positive `dy` = page scrolled down (content moves up). The accumulator resets on direction change
 * and after each reported change, so slow wiggles never flip the bars.
 */
class BarScrollTracker(
    private val hideThresholdPx: Float,
    private val showThresholdPx: Float,
) {
    private var acc = 0f

    /** Returns true = hide, false = show, null = no change yet. */
    fun onScroll(dy: Int): Boolean? {
        if (dy == 0) return null
        if ((dy > 0) != (acc > 0f) && acc != 0f) acc = 0f
        acc += dy
        return when {
            acc >= hideThresholdPx -> { acc = 0f; true }
            acc <= -showThresholdPx -> { acc = 0f; false }
            else -> null
        }
    }

    fun reset() { acc = 0f }
}

/** Mutable bar state shared by the touch/scroll listeners (main thread only). */
class BarVisibilityState {
    var pointerDown = false
    var hidden = false
}
