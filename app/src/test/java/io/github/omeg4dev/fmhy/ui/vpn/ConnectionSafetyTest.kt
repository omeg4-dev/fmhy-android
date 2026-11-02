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
