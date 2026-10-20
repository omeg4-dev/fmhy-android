package io.github.omeg4dev.fmhy.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeDecisionTest {

    private val width = 1000f
    private val min = 120f
    private val threshold = swipeThresholdPx(width, min)

    @Test
    fun thresholdIsFortyPercentOfWidth() {
        assertEquals(400f, threshold, 0.001f)
    }

    @Test
    fun thresholdOnNarrowRowUsesMin() {
        assertEquals(120f, swipeThresholdPx(200f, 120f), 0.001f)
    }

    @Test
    fun shortSwipeDoesNothing() {
        val off = clampSwipeOffset(-100f, width)
        assertEquals(SwipeAction.NONE, swipeActionOnRelease(off, threshold, false))
        assertEquals(SwipeAction.NONE, swipeActionOnRelease(off, threshold, true))
    }

    @Test
    fun startToEndSwipeIsClampedAndDoesNothing() {
        val off = clampSwipeOffset(700f, width)
        assertEquals(0f, off, 0f)
        assertEquals(SwipeAction.NONE, swipeActionOnRelease(off, threshold, false))
    }

    @Test
    fun draggedPastThresholdThenBackDoesNothing() {
        assertEquals(SwipeAction.SAVE, swipeActionOnRelease(clampSwipeOffset(-500f, width), threshold, false))
        val back = clampSwipeOffset(-300f, width)
        assertEquals(SwipeAction.NONE, swipeActionOnRelease(back, threshold, false))
    }

    @Test
    fun releasePastThresholdSavesOrRemoves() {
        val off = clampSwipeOffset(-450f, width)
        assertEquals(SwipeAction.SAVE, swipeActionOnRelease(off, threshold, false))
        assertEquals(SwipeAction.REMOVE, swipeActionOnRelease(off, threshold, true))
    }

    @Test
    fun rubberBandClamps() {
        assertEquals(-600f, clampSwipeOffset(-600f, width), 0.001f)
        val banded = clampSwipeOffset(-800f, width)
        assertEquals(-640f, banded, 0.001f)
        assertEquals(-750f, clampSwipeOffset(-5000f, width), 0.001f)
        assertTrue(banded > -750f)
    }
}
