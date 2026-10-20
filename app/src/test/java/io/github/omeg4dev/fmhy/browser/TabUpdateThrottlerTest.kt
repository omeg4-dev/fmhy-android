package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class TabUpdateThrottlerTest {

    private class FakeScheduler {
        var currentTime = 0L
        val pendingTasks = mutableListOf<Pair<Long, () -> Unit>>()

        fun postDelayed(delayMs: Long, action: () -> Unit) {
            pendingTasks.add((currentTime + delayMs) to action)
        }

        fun advanceTimeBy(ms: Long) {
            currentTime += ms
            val toRun = pendingTasks.filter { it.first <= currentTime }
            pendingTasks.removeAll(toRun)
            toRun.forEach { it.second.invoke() }
        }
    }

    @Test
    fun emitImmediate_callsEmitDirectly() {
        var emissions = 0
        val scheduler = FakeScheduler()
        val throttler = TabUpdateThrottler(
            intervalMs = 100L,
            timeProvider = { scheduler.currentTime },
            postDelayed = { delay, action -> scheduler.postDelayed(delay, action) },
            emit = { emissions++ }
        )

        throttler.emitImmediate()
        assertEquals(1, emissions)
    }

    @Test
    fun requestThrottled_conflatesRapidRequests() {
        var emissions = 0
        val scheduler = FakeScheduler()
        val throttler = TabUpdateThrottler(
            intervalMs = 100L,
            timeProvider = { scheduler.currentTime },
            postDelayed = { delay, action -> scheduler.postDelayed(delay, action) },
            emit = { emissions++ }
        )

        // First throttled request at t=0
        throttler.requestThrottled()
        // scheduler has a task scheduled for t=0 (delay=0)
        scheduler.advanceTimeBy(0)
        assertEquals(1, emissions)

        // Send 10 rapid requests between t=10 and t=50
        for (i in 1..10) {
            scheduler.currentTime += 5
            throttler.requestThrottled()
        }

        // Only 1 emission so far
        assertEquals(1, emissions)

        // Advance to t=100
        scheduler.advanceTimeBy(50)
        // Now at t=105 >= 100 ms from first emit, the second emission should occur
        assertEquals(2, emissions)

        // No more pending emissions
        scheduler.advanceTimeBy(100)
        assertEquals(2, emissions)
    }

    @Test
    fun emitImmediate_bypassesThrottle() {
        var emissions = 0
        val scheduler = FakeScheduler()
        val throttler = TabUpdateThrottler(
            intervalMs = 100L,
            timeProvider = { scheduler.currentTime },
            postDelayed = { delay, action -> scheduler.postDelayed(delay, action) },
            emit = { emissions++ }
        )

        throttler.requestThrottled()
        scheduler.advanceTimeBy(0)
        assertEquals(1, emissions)

        // Call immediate at t=10, should fire immediately regardless of interval
        scheduler.currentTime = 10
        throttler.emitImmediate()
        assertEquals(2, emissions)
    }
}
