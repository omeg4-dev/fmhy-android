package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RescueReasonTest {

    @Test
    fun testWhichErrorsTrigger() {
        // Errors that must trigger dead site rescue
        assertTrue(RescueReason.shouldTrigger(RescueReason.ERROR_HOST_LOOKUP))
        assertTrue(RescueReason.shouldTrigger(RescueReason.ERROR_CONNECT))
        assertTrue(RescueReason.shouldTrigger(RescueReason.ERROR_TIMEOUT))
        assertTrue(RescueReason.shouldTrigger(RescueReason.ERROR_FAILED_SSL_HANDSHAKE))
        assertTrue(RescueReason.shouldTrigger(RescueReason.ERROR_UNKNOWN))

        // Errors that must NOT trigger
        assertFalse(RescueReason.shouldTrigger(RescueReason.ERROR_UNSUPPORTED_SCHEME))
        assertFalse(RescueReason.shouldTrigger(RescueReason.ERROR_UNKNOWN, description = "net::ERR_ABORTED"))
        assertFalse(RescueReason.shouldTrigger(RescueReason.ERROR_CONNECT, description = "Operation cancelled by user"))
    }

    @Test
    fun testHttpStatusTrigger() {
        // 500-599 trigger
        assertTrue(RescueReason.shouldTriggerHttp(500))
        assertTrue(RescueReason.shouldTriggerHttp(502))
        assertTrue(RescueReason.shouldTriggerHttp(503))
        assertTrue(RescueReason.shouldTriggerHttp(504))
        // Cloudflare error range 520-527
        assertTrue(RescueReason.shouldTriggerHttp(520))
        assertTrue(RescueReason.shouldTriggerHttp(521))
        assertTrue(RescueReason.shouldTriggerHttp(522))
        assertTrue(RescueReason.shouldTriggerHttp(527))
        assertTrue(RescueReason.shouldTriggerHttp(599))

        // 4xx or 2xx/3xx do not trigger
        assertFalse(RescueReason.shouldTriggerHttp(200))
        assertFalse(RescueReason.shouldTriggerHttp(301))
        assertFalse(RescueReason.shouldTriggerHttp(404))
        assertFalse(RescueReason.shouldTriggerHttp(403))
    }

    @Test
    fun testErrorMessages() {
        assertEquals("The site's address could not be found", RescueReason.fromErrorCode(RescueReason.ERROR_HOST_LOOKUP))
        assertEquals("Could not connect to the site", RescueReason.fromErrorCode(RescueReason.ERROR_CONNECT))
        assertEquals("The site did not answer", RescueReason.fromErrorCode(RescueReason.ERROR_TIMEOUT))
        assertEquals("A secure connection to the site failed", RescueReason.fromErrorCode(RescueReason.ERROR_FAILED_SSL_HANDSHAKE))
        assertEquals("The page could not be loaded", RescueReason.fromErrorCode(RescueReason.ERROR_FILE))
        assertEquals("The site did not answer in time (error 522)", RescueReason.fromHttpStatus(522))
        assertEquals("The site is overloaded or down for maintenance (error 503)", RescueReason.fromHttpStatus(503))
        assertEquals("The site's server had an error (error 500)", RescueReason.fromHttpStatus(500))
    }

    @Test
    fun testKindForError() {
        assertEquals(LoadFailureKind.OFFLINE, RescueReason.kindForError(RescueReason.ERROR_HOST_LOOKUP, online = false))
        assertEquals(LoadFailureKind.TIMEOUT, RescueReason.kindForError(RescueReason.ERROR_TIMEOUT, online = true))
        assertEquals(LoadFailureKind.UNKNOWN, RescueReason.kindForError(RescueReason.ERROR_CONNECT, online = true))
    }

    @Test
    fun testKindForHttp() {
        assertEquals(LoadFailureKind.REFUSED, RescueReason.kindForHttp(403))
        assertEquals(LoadFailureKind.REFUSED, RescueReason.kindForHttp(429))
        assertEquals(LoadFailureKind.REFUSED, RescueReason.kindForHttp(451))
        assertEquals(LoadFailureKind.SERVER, RescueReason.kindForHttp(503))
        assertEquals(LoadFailureKind.UNKNOWN, RescueReason.kindForHttp(404))
    }

    @Test
    fun testShouldTriggerRefused() {
        assertTrue(RescueReason.shouldTriggerRefused(403))
        assertTrue(RescueReason.shouldTriggerRefused(429))
        assertTrue(RescueReason.shouldTriggerRefused(451))
        assertFalse(RescueReason.shouldTriggerRefused(404))
        assertFalse(RescueReason.shouldTriggerRefused(503))
    }

    @Test
    fun testTitlesAndAdvice() {
        for (kind in LoadFailureKind.values()) {
            assertTrue(RescueReason.title(kind).isNotBlank())
            assertTrue(RescueReason.advice(kind).isNotBlank())
        }

        assertEquals("FMHY's filters blocked part of this page", RescueReason.title(LoadFailureKind.BLOCKED))
        assertEquals("Reload without blocking, or allow the blocked sites in the shield panel.", RescueReason.advice(LoadFailureKind.BLOCKED))

        assertEquals("No internet connection", RescueReason.title(LoadFailureKind.OFFLINE))
        assertEquals("Check Wi-Fi or mobile data, then retry.", RescueReason.advice(LoadFailureKind.OFFLINE))

        assertEquals("The site refused to show this page", RescueReason.title(LoadFailureKind.REFUSED))
        assertEquals("Try desktop site, a private tab, or a mirror.", RescueReason.advice(LoadFailureKind.REFUSED))

        assertEquals("The site took too long to answer", RescueReason.title(LoadFailureKind.TIMEOUT))
        assertEquals("Retry, or try a mirror.", RescueReason.advice(LoadFailureKind.TIMEOUT))

        assertEquals("The page stayed blank", RescueReason.title(LoadFailureKind.BLANK))
        assertEquals("Reload without blocking, or try desktop site.", RescueReason.advice(LoadFailureKind.BLANK))

        assertEquals("This site isn't loading", RescueReason.title(LoadFailureKind.SERVER))
        assertEquals("The site may be down. Retry later or try a mirror.", RescueReason.advice(LoadFailureKind.SERVER))

        assertEquals("This site isn't loading", RescueReason.title(LoadFailureKind.UNKNOWN))
        assertEquals("Retry, or try a mirror.", RescueReason.advice(LoadFailureKind.UNKNOWN))
    }

    @Test
    fun testHttpStatusMessagesForNewCodes() {
        assertEquals("The site refused access (error 403)", RescueReason.fromHttpStatus(403))
        assertEquals("Too many requests to the site (error 429)", RescueReason.fromHttpStatus(429))
        assertEquals("The site is not available here (error 451)", RescueReason.fromHttpStatus(451))
    }
}
