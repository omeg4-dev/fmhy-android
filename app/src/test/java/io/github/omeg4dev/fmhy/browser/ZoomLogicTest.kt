package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomLogicTest {
    private fun tracker() = BarScrollTracker(hideThresholdPx = 100f, showThresholdPx = 60f).apply { onPointerDown() }

    @Test fun clampEnforcesMinAndMax() {
        assertEquals(1.0f, ZoomLogic.clamp(0.5f), 0.0001f)
        assertEquals(5.0f, ZoomLogic.clamp(6.0f), 0.0001f)
        assertEquals(2.5f, ZoomLogic.clamp(2.5f), 0.0001f)
    }

    @Test fun stepUpMovesToNextQuarterStrictlyAboveAndClamps() {
        assertEquals(1.25f, ZoomLogic.stepUp(1.0f), 0.0001f)
        assertEquals(1.25f, ZoomLogic.stepUp(1.1f), 0.0001f)
        assertEquals(1.5f, ZoomLogic.stepUp(1.25f), 0.0001f)
        assertEquals(5.0f, ZoomLogic.stepUp(4.9f), 0.0001f)
        assertEquals(5.0f, ZoomLogic.stepUp(5.0f), 0.0001f)
    }

    @Test fun stepDownMovesToNextQuarterStrictlyBelowAndClamps() {
        assertEquals(1.25f, ZoomLogic.stepDown(1.3f), 0.0001f)
        assertEquals(1.0f, ZoomLogic.stepDown(1.25f), 0.0001f)
        assertEquals(1.0f, ZoomLogic.stepDown(1.0f), 0.0001f)
    }

    @Test fun labelFormatsPercentageWithSpace() {
        assertEquals("150 %", ZoomLogic.label(1.5f))
        assertEquals("100 %", ZoomLogic.label(1.0f))
        assertEquals("500 %", ZoomLogic.label(5.0f))
        assertEquals("100 %", ZoomLogic.label(0.8f))
        assertEquals("500 %", ZoomLogic.label(5.5f))
    }

    @Test fun showResetToleratesSmallDeltaAroundOne() {
        assertFalse(ZoomLogic.showReset(1.0f))
        assertFalse(ZoomLogic.showReset(1.004f))
        assertTrue(ZoomLogic.showReset(1.006f))
        assertTrue(ZoomLogic.showReset(0.994f))
        assertTrue(ZoomLogic.showReset(1.5f))
    }

    @Test fun softLimitRubberBandsBeyondLimits() {
        assertEquals(2.5f, ZoomLogic.softLimit(2.5f), 0.0001f)
        assertEquals(1.0f, ZoomLogic.softLimit(1.0f), 0.0001f)
        assertEquals(5.0f, ZoomLogic.softLimit(5.0f), 0.0001f)
        assertEquals(0.95f, ZoomLogic.softLimit(0.8f), 0.0001f)
        assertEquals(5.1f, ZoomLogic.softLimit(5.4f), 0.0001f)
        assertEquals(5.15f, ZoomLogic.softLimit(9.0f), 0.0001f)
        assertEquals(0.85f, ZoomLogic.softLimit(0.0f), 0.0001f)
    }

    @Test fun doubleTapTargetTogglesBetweenZoomAndReset() {
        assertEquals(2.5f, ZoomLogic.doubleTapTarget(1.0f), 0.0001f)
        assertEquals(2.5f, ZoomLogic.doubleTapTarget(1.49f), 0.0001f)
        assertEquals(1.0f, ZoomLogic.doubleTapTarget(1.5f), 0.0001f)
        assertEquals(1.0f, ZoomLogic.doubleTapTarget(2.5f), 0.0001f)
        assertEquals(1.0f, ZoomLogic.doubleTapTarget(5.0f), 0.0001f)
    }

    @Test fun ignoresBarDetectsMultiTouch() {
        assertTrue(ZoomLogic.ignoresBar(2))
        assertFalse(ZoomLogic.ignoresBar(1))
        assertTrue(ZoomLogic.ignoresBar(3))
        assertFalse(ZoomLogic.ignoresBar(0))
    }

    @Test fun trackerMultiTouchReturnsNullAndNeverHides() {
        val tr = tracker()
        tr.setMultiTouch(true)
        repeat(50) { assertNull(tr.onScroll(20)) }
    }

    @Test fun trackerResumesNormalScrollAfterMultiTouchFalse() {
        val tr = tracker()
        tr.setMultiTouch(true)
        repeat(20) { assertNull(tr.onScroll(20)) }
        tr.setMultiTouch(false)
        repeat(4) { assertNull(tr.onScroll(20)) }
        assertEquals(true, tr.onScroll(20))
    }

    @Test fun onMultiTouchResetsHalfFilledAccumulator() {
        val tr = tracker()
        repeat(4) { assertNull(tr.onScroll(20)) }
        tr.onMultiTouch()
        repeat(4) { assertNull(tr.onScroll(20)) }
        assertEquals(true, tr.onScroll(20))
    }
}
