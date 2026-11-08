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
    fun entranceOrdersRowsByCompositionAcrossGroups() {
        val entrance = ListEntrance(now = { 0L })
        // two groups of 6 rows compose in one frame: ordinals keep counting instead of restarting per group
        val delays = (0 until 12).map { entrance.claim()!! }
        assertEquals(listOf(0, 40, 80, 120, 160, 200, 240, 280, 280, 280, 280, 280), delays)
    }

    @Test
    fun entranceStartsAtFirstClaim() {
        var t = 0L
        val entrance = ListEntrance(now = { t })
        t = 5_000L // the screen showed a skeleton for a while before the rows arrived
        assertEquals(0, entrance.claim())
        t += 50
        assertEquals(40, entrance.claim())
    }

    @Test
    fun rowsComposedAfterThePauseAppearAtOnce() {
        var t = 0L
        val entrance = ListEntrance(now = { t })
        assertEquals(0, entrance.claim())
        t += 10
        assertEquals(40, entrance.claim())
        t += 10_000 // user reads, then scrolls again: not a new entrance
        assertNull(entrance.claim())
        assertNull(entrance.claim())
    }

    @Test
    fun entranceStaysClosedOnceTheWindowPassed() {
        var t = 0L
        val entrance = ListEntrance(now = { t })
        assertEquals(0, entrance.claim())
        t += STAGGER_WINDOW_MS + 1
        assertNull(entrance.claim())
        t = 0L
        assertNull(entrance.claim())
    }

    @Test
    fun closedEntranceGivesNoDelay() {
        val entrance = ListEntrance(now = { 0L })
        assertEquals(0, entrance.claim())
        entrance.close() // programmatic jump: rows it brings into view must not fade
        assertNull(entrance.claim())
    }
}
