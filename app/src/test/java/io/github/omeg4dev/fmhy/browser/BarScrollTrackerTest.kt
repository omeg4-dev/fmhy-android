package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarScrollTrackerTest {
    private fun t() = BarScrollTracker(hideThresholdPx = 100f, showThresholdPx = 60f)

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
}
