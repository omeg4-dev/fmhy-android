package io.github.omeg4dev.fmhy.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnterStaggerTest {
    @Test
    fun entranceItemsGetStepDelay() {
        assertEquals(0, staggerDelayOrNull(0, 0))
        assertEquals(40, staggerDelayOrNull(1, 10))
        assertEquals(280, staggerDelayOrNull(7, 300))
    }

    @Test
    fun laterOrDeepItemsDoNotAnimate() {
        assertNull(staggerDelayOrNull(0, 301))
        assertNull(staggerDelayOrNull(8, 0))
        assertNull(staggerDelayOrNull(40, 0))
    }

    @Test
    fun trackerStartsOnFirstComposeAndRestartsAfterGap() {
        var t = 1000L
        val tracker = EntranceTracker(now = { t })
        assertEquals(0L, tracker.msSinceListStart("a"))
        t += 50
        assertEquals(50L, tracker.msSinceListStart("a"))
        t += 400
        assertEquals(450L, tracker.msSinceListStart("a")) // scrolling: past the window
        assertEquals(0L, tracker.msSinceListStart("b"))   // other key independent
        t += 2000
        assertEquals(0L, tracker.msSinceListStart("a"))   // screen opened again
    }

    @Test
    fun trackerEvictsOldKeys() {
        var t = 0L
        val tracker = EntranceTracker(now = { t }, capacity = 2)
        tracker.msSinceListStart("a"); t += 10
        tracker.msSinceListStart("b"); t += 10
        tracker.msSinceListStart("c"); t += 10
        assertEquals(0L, tracker.msSinceListStart("a")) // evicted, starts fresh
    }
}
