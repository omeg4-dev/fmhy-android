package io.github.omeg4dev.fmhy.browser

import kotlin.math.abs

/**
 * Accumulates page scroll in one direction and decides when the browser bars should hide or show.
 * Positive `dy` = page scrolled down (content moves up). The accumulator resets on direction change
 * and after each reported change, so slow wiggles never flip the bars.
 *
 * The bars react while the finger drags. After it lifts, the page may glide on: only changes in the
 * direction of the drag count then, until the scroll comes to rest. Scrolls without a finger (page
 * height changes, scripted scrolls) change nothing.
 */
class BarScrollTracker(
    private val hideThresholdPx: Float,
    private val showThresholdPx: Float,
) {
    private var acc = 0f
    /** +1 = last drag scrolled down, -1 = up, 0 = no drag. */
    private var dragDir = 0
    private var gliding = false
    var pointerDown = false
        private set
    var multiTouch = false
        private set

    fun setMultiTouch(active: Boolean) {
        multiTouch = active
        if (active) onMultiTouch()
    }

    fun onMultiTouch() {
        acc = 0f
        dragDir = 0
    }

    fun onPointerDown() {
        pointerDown = true
        gliding = false
        dragDir = 0
        acc = 0f
    }

    fun onPointerUp() {
        pointerDown = false
        gliding = dragDir != 0
    }

    /** The page stopped moving after the finger lifted. */
    fun onScrollIdle() {
        if (pointerDown) return
        gliding = false
        acc = 0f
    }

    /** Returns true = hide, false = show, null = no change. */
    fun onScroll(dy: Int): Boolean? {
        if (multiTouch) return null
        if (dy == 0) return null
        val dir = if (dy > 0) 1 else -1
        // A finger cannot reverse a drag by a big step in one event: the page moved itself (sticky header
        // collapsing, content above loading), so it says nothing about the finger.
        if (pointerDown && dragDir != 0 && dir != dragDir && abs(dy) > PAGE_JUMP_PX) return null
        if (pointerDown) {
            dragDir = dir
        } else if (!gliding || dir != dragDir) {
            acc = 0f
            return null
        }
        if ((dy > 0) != (acc > 0f) && acc != 0f) acc = 0f
        acc += dy
        return when {
            acc >= hideThresholdPx -> { acc = 0f; true }
            acc <= -showThresholdPx -> { acc = 0f; false }
            else -> null
        }
    }

    fun reset() { acc = 0f }

    private companion object {
        const val PAGE_JUMP_PX = 80
    }
}

/** Mutable bar state shared by the touch/scroll listeners (main thread only). */
class BarVisibilityState {
    var hidden = false
}
