package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.browser.RescueDecision.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreRetryTest {
    private val testUrl = "https://example.org/page"
    private val currentUrls = listOf(testUrl)

    @Test
    fun lookupFailureInsideWindowWithRetriesDoneAtOrAbove2ReturnsRetry() {
        val action = RescueDecision.decideWithNetworkGrace(
            isForMainFrame = true,
            failingUrl = testUrl,
            currentUrls = currentUrls,
            contentShown = false,
            retriesDone = 2,
            inGraceWindow = true,
            networkReady = true,
            graceElapsedMs = 1_000L,
            isLookupFailure = true,
        )
        assertEquals(Action.RETRY, action)

        val actionMoreRetries = RescueDecision.decideWithNetworkGrace(
            isForMainFrame = true,
            failingUrl = testUrl,
            currentUrls = currentUrls,
            contentShown = false,
            retriesDone = 5,
            inGraceWindow = true,
            networkReady = false,
            graceElapsedMs = 3_000L,
            isLookupFailure = true,
        )
        assertEquals(Action.RETRY, actionMoreRetries)
    }

    @Test
    fun sameFailureAfterDeadlineReturnsShow() {
        val actionAtDeadline = RescueDecision.decideWithNetworkGrace(
            isForMainFrame = true,
            failingUrl = testUrl,
            currentUrls = currentUrls,
            contentShown = false,
            retriesDone = 2,
            inGraceWindow = true,
            networkReady = true,
            graceElapsedMs = RescueDecision.GRACE_MS,
            isLookupFailure = true,
        )
        assertEquals(Action.SHOW, actionAtDeadline)

        val actionPastDeadline = RescueDecision.decideWithNetworkGrace(
            isForMainFrame = true,
            failingUrl = testUrl,
            currentUrls = currentUrls,
            contentShown = false,
            retriesDone = 2,
            inGraceWindow = true,
            networkReady = true,
            graceElapsedMs = RescueDecision.GRACE_MS + 1_000L,
            isLookupFailure = true,
        )
        assertEquals(Action.SHOW, actionPastDeadline)
    }

    @Test
    fun nonLookupFailureInsideWindowFollowsOldBehavior() {
        // Under old behavior: 0 and 1 retries retry silently, 2 shows rescue
        val retry0 = RescueDecision.decideWithNetworkGrace(
            isForMainFrame = true,
            failingUrl = testUrl,
            currentUrls = currentUrls,
            contentShown = false,
            retriesDone = 0,
            inGraceWindow = true,
            networkReady = true,
            graceElapsedMs = 1_000L,
            isLookupFailure = false,
        )
        assertEquals(Action.RETRY, retry0)

        val retry1 = RescueDecision.decideWithNetworkGrace(
            isForMainFrame = true,
            failingUrl = testUrl,
            currentUrls = currentUrls,
            contentShown = false,
            retriesDone = 1,
            inGraceWindow = true,
            networkReady = true,
            graceElapsedMs = 1_000L,
            isLookupFailure = false,
        )
        assertEquals(Action.RETRY, retry1)

        val show2 = RescueDecision.decideWithNetworkGrace(
            isForMainFrame = true,
            failingUrl = testUrl,
            currentUrls = currentUrls,
            contentShown = false,
            retriesDone = 2,
            inGraceWindow = true,
            networkReady = true,
            graceElapsedMs = 1_000L,
            isLookupFailure = false,
        )
        assertEquals(Action.SHOW, show2)
    }

    @Test
    fun contentShownReturnsIgnore() {
        val action = RescueDecision.decideWithNetworkGrace(
            isForMainFrame = true,
            failingUrl = testUrl,
            currentUrls = currentUrls,
            contentShown = true,
            retriesDone = 0,
            inGraceWindow = true,
            networkReady = true,
            graceElapsedMs = 1_000L,
            isLookupFailure = true,
        )
        assertEquals(Action.IGNORE, action)

        val actionWithRetries = RescueDecision.decideWithNetworkGrace(
            isForMainFrame = true,
            failingUrl = testUrl,
            currentUrls = currentUrls,
            contentShown = true,
            retriesDone = 3,
            inGraceWindow = true,
            networkReady = true,
            graceElapsedMs = 1_000L,
            isLookupFailure = true,
        )
        assertEquals(Action.IGNORE, actionWithRetries)
    }

    @Test
    fun notInWindowFollowsOldBehavior() {
        val retry0 = RescueDecision.decideWithNetworkGrace(
            isForMainFrame = true,
            failingUrl = testUrl,
            currentUrls = currentUrls,
            contentShown = false,
            retriesDone = 0,
            inGraceWindow = false,
            networkReady = true,
            graceElapsedMs = 0L,
            isLookupFailure = true,
        )
        assertEquals(Action.RETRY, retry0)

        val retry1 = RescueDecision.decideWithNetworkGrace(
            isForMainFrame = true,
            failingUrl = testUrl,
            currentUrls = currentUrls,
            contentShown = false,
            retriesDone = 1,
            inGraceWindow = false,
            networkReady = true,
            graceElapsedMs = 0L,
            isLookupFailure = true,
        )
        assertEquals(Action.RETRY, retry1)

        val show2 = RescueDecision.decideWithNetworkGrace(
            isForMainFrame = true,
            failingUrl = testUrl,
            currentUrls = currentUrls,
            contentShown = false,
            retriesDone = 2,
            inGraceWindow = false,
            networkReady = true,
            graceElapsedMs = 0L,
            isLookupFailure = true,
        )
        assertEquals(Action.SHOW, show2)

        val show3 = RescueDecision.decideWithNetworkGrace(
            isForMainFrame = true,
            failingUrl = testUrl,
            currentUrls = currentUrls,
            contentShown = false,
            retriesDone = 3,
            inGraceWindow = false,
            networkReady = true,
            graceElapsedMs = 0L,
            isLookupFailure = true,
        )
        assertEquals(Action.SHOW, show3)
    }

    @Test
    fun delayNeverExceedsDeadline() {
        // Network ready: 500ms intervals, clamped by remaining grace time
        assertEquals(500L, RescueDecision.graceRetryDelayMs(networkReady = true, elapsed = 0L))
        assertEquals(500L, RescueDecision.graceRetryDelayMs(networkReady = true, elapsed = 5_000L))
        assertEquals(500L, RescueDecision.graceRetryDelayMs(networkReady = true, elapsed = 5_500L))
        assertEquals(200L, RescueDecision.graceRetryDelayMs(networkReady = true, elapsed = 5_800L))
        assertEquals(50L, RescueDecision.graceRetryDelayMs(networkReady = true, elapsed = 5_950L))
        assertEquals(0L, RescueDecision.graceRetryDelayMs(networkReady = true, elapsed = 6_000L))
        assertEquals(0L, RescueDecision.graceRetryDelayMs(networkReady = true, elapsed = 6_500L))

        // Network not ready: 1000ms intervals, clamped by remaining grace time
        assertEquals(1_000L, RescueDecision.graceRetryDelayMs(networkReady = false, elapsed = 0L))
        assertEquals(1_000L, RescueDecision.graceRetryDelayMs(networkReady = false, elapsed = 4_500L))
        assertEquals(1_000L, RescueDecision.graceRetryDelayMs(networkReady = false, elapsed = 5_000L))
        assertEquals(500L, RescueDecision.graceRetryDelayMs(networkReady = false, elapsed = 5_500L))
        assertEquals(200L, RescueDecision.graceRetryDelayMs(networkReady = false, elapsed = 5_800L))
        assertEquals(0L, RescueDecision.graceRetryDelayMs(networkReady = false, elapsed = 6_000L))
        assertEquals(0L, RescueDecision.graceRetryDelayMs(networkReady = false, elapsed = 7_000L))

        // Invariant check across range: elapsed + delay never exceeds deadline
        for (elapsed in 0L..10_000L step 50L) {
            for (networkReady in listOf(true, false)) {
                val delay = RescueDecision.graceRetryDelayMs(networkReady, elapsed)
                assertTrue("delay should be non-negative", delay >= 0L)
                assertTrue(
                    "delay should not exceed deadline: elapsed=$elapsed, delay=$delay",
                    elapsed + delay <= maxOf(elapsed, RescueDecision.GRACE_MS)
                )
            }
        }
    }

    @Test
    fun failedLoadIsLookupFailureDetection() {
        val hostLookup = FailedLoad(
            url = testUrl,
            reason = "The site's address could not be found",
            isHttp = false,
            errorCode = RescueReason.ERROR_HOST_LOOKUP,
        )
        assertTrue(hostLookup.isLookupFailure())

        val connectError = FailedLoad(
            url = testUrl,
            reason = "Could not connect to the site",
            isHttp = false,
            errorCode = RescueReason.ERROR_CONNECT,
        )
        assertTrue(connectError.isLookupFailure())

        val offlineError = FailedLoad(
            url = testUrl,
            reason = "Offline",
            isHttp = false,
            kind = LoadFailureKind.OFFLINE,
        )
        assertTrue(offlineError.isLookupFailure())

        val timeoutError = FailedLoad(
            url = testUrl,
            reason = "The site did not answer",
            isHttp = false,
            errorCode = RescueReason.ERROR_TIMEOUT,
        )
        assertTrue(timeoutError.isLookupFailure())

        val http500 = FailedLoad(
            url = testUrl,
            reason = "The site's server had an error (error 500)",
            isHttp = true,
            kind = LoadFailureKind.SERVER,
            errorCode = 500,
        )
        assertFalse(http500.isLookupFailure())

        val http403 = FailedLoad(
            url = testUrl,
            reason = "The site refused access (error 403)",
            isHttp = true,
            kind = LoadFailureKind.REFUSED,
            errorCode = 403,
        )
        assertFalse(http403.isLookupFailure())

        val sslError = FailedLoad(
            url = testUrl,
            reason = "A secure connection to the site failed",
            isHttp = false,
            errorCode = RescueReason.ERROR_FAILED_SSL_HANDSHAKE,
        )
        assertFalse(sslError.isLookupFailure())
    }

    @Test
    fun decideWithFailedLoadOverload() {
        val lookupFailed = FailedLoad(
            url = testUrl,
            reason = "The site's address could not be found",
            isHttp = false,
            errorCode = RescueReason.ERROR_HOST_LOOKUP,
        )
        val action = RescueDecision.decideWithNetworkGrace(
            isForMainFrame = true,
            failingUrl = testUrl,
            currentUrls = currentUrls,
            contentShown = false,
            retriesDone = 2,
            inGraceWindow = true,
            networkReady = true,
            graceElapsedMs = 500L,
            failed = lookupFailed,
        )
        assertEquals(Action.RETRY, action)
    }
}
