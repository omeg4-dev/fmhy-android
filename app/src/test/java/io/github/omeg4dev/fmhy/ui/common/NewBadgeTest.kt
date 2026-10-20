package io.github.omeg4dev.fmhy.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewBadgeTest {

    private val nowSec = 1700000000L
    private val oneDaySec = 86400L

    @Test
    fun testDaysZeroThresholdAlwaysOff() {
        // Even if added 1 second ago, newBadgeDays = 0 means badges are off
        assertFalse(NewBadgeLogic.isNew(addedAtSec = nowSec - 1, nowSec = nowSec, newBadgeDays = 0))
        assertFalse(NewBadgeLogic.isNew(addedAtSec = nowSec - 3600, nowSec = nowSec, newBadgeDays = 0))
        assertFalse(NewBadgeLogic.isNew(addedAtSec = null, nowSec = nowSec, newBadgeDays = 0))
    }

    @Test
    fun testDaysThreeThreshold() {
        // 2 days ago: inside 3-day threshold -> true
        assertTrue(NewBadgeLogic.isNew(addedAtSec = nowSec - (2 * oneDaySec), nowSec = nowSec, newBadgeDays = 3))
        // Exactly on boundary 3 days ago -> true (>= cutoff)
        assertTrue(NewBadgeLogic.isNew(addedAtSec = nowSec - (3 * oneDaySec), nowSec = nowSec, newBadgeDays = 3))
        // 4 days ago: outside 3-day threshold -> false
        assertFalse(NewBadgeLogic.isNew(addedAtSec = nowSec - (4 * oneDaySec), nowSec = nowSec, newBadgeDays = 3))
    }

    @Test
    fun testDaysSevenThreshold() {
        // 5 days ago: inside 7-day threshold -> true
        assertTrue(NewBadgeLogic.isNew(addedAtSec = nowSec - (5 * oneDaySec), nowSec = nowSec, newBadgeDays = 7))
        // Exactly on boundary 7 days ago -> true
        assertTrue(NewBadgeLogic.isNew(addedAtSec = nowSec - (7 * oneDaySec), nowSec = nowSec, newBadgeDays = 7))
        // 8 days ago: outside 7-day threshold -> false
        assertFalse(NewBadgeLogic.isNew(addedAtSec = nowSec - (8 * oneDaySec), nowSec = nowSec, newBadgeDays = 7))
    }

    @Test
    fun testNullOrNegativeTimestamp() {
        assertFalse(NewBadgeLogic.isNew(addedAtSec = null, nowSec = nowSec, newBadgeDays = 7))
        assertFalse(NewBadgeLogic.isNew(addedAtSec = 0L, nowSec = nowSec, newBadgeDays = 7))
        assertFalse(NewBadgeLogic.isNew(addedAtSec = -100L, nowSec = nowSec, newBadgeDays = 7))
    }
}
