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

    @Test
    fun claimOrdersRowsByCompositionAcrossGroups() {
        var t = 0L
        val tracker = EntranceTracker(now = { t })
        // two groups of 6 rows compose in one frame: ordinals keep counting instead of restarting per group
        val ordinals = (0 until 12).map { tracker.claim("list").first }
        assertEquals((0 until 12).toList(), ordinals)
        // delays never decrease down the list, even past the stagger cap
        val delays = ordinals.map { staggerDelayOrNull(it.coerceAtMost(Motion.STAGGER_MAX_ITEMS - 1), 0)!! }
        assertEquals(delays.sorted(), delays)
    }

    @Test
    fun claimRestartsAfterGapAndKeysAreIndependent() {
        var t = 0L
        val tracker = EntranceTracker(now = { t })
        assertEquals(0, tracker.claim("a").first)
        assertEquals(1, tracker.claim("a").first)
        assertEquals(0, tracker.claim("b").first)
        t += 2000
        assertEquals(0, tracker.claim("a").first)
    }
}
