package io.github.omeg4dev.fmhy.ui.vpn

import io.github.omeg4dev.fmhy.work.VpnState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionSafetyTest {
    @Test fun https_withVpn_isProtected() {
        assertEquals(ConnectionLevel.PROTECTED, ConnectionSafety.levelOf("https://example.com/a", VpnState.ACTIVE))
    }

    @Test fun https_withoutVpn_isEncrypted() {
        assertEquals(ConnectionLevel.ENCRYPTED, ConnectionSafety.levelOf("HTTPS://example.com", VpnState.INACTIVE))
    }

    @Test fun http_isInsecure_withOrWithoutVpn() {
        assertEquals(ConnectionLevel.INSECURE, ConnectionSafety.levelOf("http://example.com", VpnState.ACTIVE))
        assertEquals(ConnectionLevel.INSECURE, ConnectionSafety.levelOf("http://example.com", VpnState.INACTIVE))
    }

    @Test fun https_withOrbot_isOrbot_withOrWithoutVpn() {
        VpnState.entries.forEach {
            assertEquals(ConnectionLevel.ORBOT, ConnectionSafety.levelOf("https://example.com", it, orbot = true))
        }
        assertEquals("Protected by Orbot", ConnectionSafety.title(ConnectionLevel.ORBOT))
    }

    @Test fun http_isInsecure_withOrbot() {
        assertEquals(
            ConnectionLevel.INSECURE,
            ConnectionSafety.levelOf("http://example.com", VpnState.INACTIVE, orbot = true),
        )
    }

    @Test fun nonWebPages_haveNoLevel_withOrbot() {
        assertNull(ConnectionSafety.levelOf("about:blank", VpnState.ACTIVE, orbot = true))
    }

    @Test fun orbotFailure_hasNoLevel() {
        assertNull(
            ConnectionSafety.levelOf("https://example.com", VpnState.INACTIVE, orbot = true, proxyFailed = true),
        )
        assertEquals(
            ConnectionLevel.ENCRYPTED,
            ConnectionSafety.levelOf("https://example.com", VpnState.INACTIVE, proxyFailed = true),
        )
    }

    @Test fun nonWebPages_haveNoLevel() {
        listOf("", "about:blank", "file:///sdcard/a.html", "content://x/y", "chrome-error://chromewebdata/")
            .forEach { assertNull(it, ConnectionSafety.levelOf(it, VpnState.ACTIVE)) }
    }

    @Test fun schemeChecks_andTexts() {
        assertTrue(ConnectionSafety.isHttps("https://a.b"))
        assertFalse(ConnectionSafety.isHttps("http://a.b"))
        assertEquals("HTTPS, encrypted", ConnectionSafety.connectionText("https://a.b"))
        assertEquals("HTTP, not encrypted", ConnectionSafety.connectionText("http://a.b"))
        val titles = ConnectionLevel.entries.map { ConnectionSafety.title(it) }
        assertEquals(titles.size, titles.toSet().size)
        ConnectionLevel.entries.forEach { assertNotEquals("", ConnectionSafety.detail(it)) }
    }
}
