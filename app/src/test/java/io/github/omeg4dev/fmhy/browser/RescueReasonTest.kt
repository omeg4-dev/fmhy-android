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
        assertEquals("Server not found", RescueReason.fromErrorCode(RescueReason.ERROR_HOST_LOOKUP))
        assertEquals("Connection failed", RescueReason.fromErrorCode(RescueReason.ERROR_CONNECT))
        assertEquals("Connection timed out", RescueReason.fromErrorCode(RescueReason.ERROR_TIMEOUT))
        assertEquals("Secure connection failed", RescueReason.fromErrorCode(RescueReason.ERROR_FAILED_SSL_HANDSHAKE))
        assertEquals("Server error 522", RescueReason.fromHttpStatus(522))
        assertEquals("Server error 500", RescueReason.fromHttpStatus(500))
    }
}
