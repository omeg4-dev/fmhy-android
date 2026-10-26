package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ZoomMathTest {

    @Test
    fun zoomFactorComputesRatioBetweenClampedLevels() {
        assertEquals(2.5f, ZoomMath.zoomFactor(1.0f, 2.5f), 0.0001f)
        assertEquals(0.5f, ZoomMath.zoomFactor(2.0f, 1.0f), 0.0001f)
        assertEquals(1.0f, ZoomMath.zoomFactor(2.5f, 2.5f), 0.0001f)
        // Clamps inputs outside 1f..5f
        assertEquals(2.0f, ZoomMath.zoomFactor(0.5f, 2.0f), 0.0001f)
        assertEquals(5.0f, ZoomMath.zoomFactor(1.0f, 6.0f), 0.0001f)
    }

    @Test
    fun stepFactorSplitsTotalFactorAcrossSteps() {
        assertEquals(2.0f, ZoomMath.stepFactor(8.0f, 3), 0.0001f)
        assertEquals(1.0f, ZoomMath.stepFactor(1.0f, 6), 0.0001f)
        assertEquals(0.5f, ZoomMath.stepFactor(0.125f, 3), 0.0001f)

        val total = 2.5f
        val steps = 6
        val step = ZoomMath.stepFactor(total, steps)
        val accumulated = step.toDouble().pow(steps.toDouble()).toFloat()
        assertEquals(total, accumulated, 0.001f)

        // Invalid inputs return 1f
        assertEquals(1.0f, ZoomMath.stepFactor(total, 0), 0.0001f)
        assertEquals(1.0f, ZoomMath.stepFactor(-1f, 3), 0.0001f)
    }

    @Test
    fun targetScrollAndScrollDeltaPreserveContentPosition() {
        // Tapped at 100 with scroll 0, zoomed by factor 2.0
        val target = ZoomMath.targetScroll(scrollBefore = 0, tapPos = 100f, factor = 2.0f)
        assertEquals(100, target)
        assertEquals(100, ZoomMath.scrollDelta(target, currentScroll = 0))

        // Tapped at 100 with scroll 50, zoomed by factor 2.0
        val target2 = ZoomMath.targetScroll(scrollBefore = 50, tapPos = 100f, factor = 2.0f)
        assertEquals(200, target2)
        assertEquals(150, ZoomMath.scrollDelta(target2, currentScroll = 50))

        // Zoom out: tap at 100 with scroll 200, factor 0.5
        val targetOut = ZoomMath.targetScroll(scrollBefore = 200, tapPos = 100f, factor = 0.5f)
        assertEquals(50, targetOut)
        assertEquals(-150, ZoomMath.scrollDelta(targetOut, currentScroll = 200))
    }

    @Test
    fun outOfBoundsTolerances() {
        assertTrue(ZoomMath.isZoomOutBelowNormal(0.97f))
        assertFalse(ZoomMath.isZoomOutBelowNormal(0.985f))
        assertFalse(ZoomMath.isZoomOutBelowNormal(1.0f))

        assertTrue(ZoomMath.isZoomInAboveMax(5.03f))
        assertFalse(ZoomMath.isZoomInAboveMax(5.01f))
        assertFalse(ZoomMath.isZoomInAboveMax(5.0f))
    }
}
