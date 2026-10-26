package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarScrollTrackerTest {
    private fun t() = BarScrollTracker(hideThresholdPx = 100f, showThresholdPx = 60f).apply { onPointerDown() }

    @Test fun smallWigglesNeverTrigger() {
        val tr = t()
        repeat(50) { assertNull(tr.onScroll(if (it % 2 == 0) 10 else -10)) }
    }

    @Test fun hidesAfterAccumulatedDownScroll() {
        val tr = t()
        repeat(9) { assertNull(tr.onScroll(10)) }
        assertEquals(true, tr.onScroll(10))
    }

    @Test fun showsAfterAccumulatedUpScroll() {
        val tr = t()
        repeat(5) { assertNull(tr.onScroll(-10)) }
        assertEquals(false, tr.onScroll(-10))
    }

    @Test fun directionChangeResetsAccumulator() {
        val tr = t()
        repeat(9) { tr.onScroll(10) }
        assertNull(tr.onScroll(-10))
        assertNull(tr.onScroll(10)) // accumulator restarted, not 90+10
    }

    @Test fun glideInDragDirectionStillHides() {
        val tr = t()
        repeat(3) { tr.onScroll(10) }
        tr.onPointerUp()
        repeat(6) { assertNull(tr.onScroll(10)) }
        assertEquals(true, tr.onScroll(10))
    }

    @Test fun glideAgainstDragDirectionIsIgnored() {
        val tr = t()
        repeat(10) { tr.onScroll(10) } // hidden
        tr.onPointerUp()
        // Page height change / bounce back while gliding: never flips the bar back.
        repeat(20) { assertNull(tr.onScroll(-30)) }
    }

    @Test fun liftingAloneChangesNothing() {
        val tr = t()
        repeat(5) { tr.onScroll(10) }
        tr.onPointerUp()
        tr.onScrollIdle()
        repeat(20) { assertNull(tr.onScroll(50)) }
        repeat(20) { assertNull(tr.onScroll(-50)) }
    }

    @Test fun tapStopsTheGlide() {
        val tr = t()
        repeat(3) { tr.onScroll(10) }
        tr.onPointerUp()
        tr.onPointerDown() // tap to stop the fling
        tr.onPointerUp()
        repeat(20) { assertNull(tr.onScroll(10)) }
    }

    @Test fun reactsWhileTheFingerIsDown() {
        val tr = t()
        repeat(9) { assertNull(tr.onScroll(10)) }
        assertEquals(true, tr.onScroll(10))
        repeat(5) { assertNull(tr.onScroll(-10)) }
        assertEquals(false, tr.onScroll(-10))
    }

    @Test fun pageJumpAgainstTheDragIsIgnored() {
        val tr = t()
        repeat(3) { assertNull(tr.onScroll(19)) }
        assertNull(tr.onScroll(-108))
        repeat(2) { assertNull(tr.onScroll(19)) }
        assertEquals(true, tr.onScroll(19).let { it ?: tr.onScroll(19) ?: tr.onScroll(19) })
    }

    @Test fun smallReverseStepStillCounts() {
        val tr = t()
        tr.onScroll(19)
        repeat(3) { tr.onScroll(-19) }
        assertEquals(false, tr.onScroll(-19))
    }
}
