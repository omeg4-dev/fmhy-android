package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.work.VpnState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnGateTest {

    @Test
    fun `shouldBlock returns true when all conditions hold for http and https`() {
        assertTrue(
            VpnGate.shouldBlock(
                url = "https://fmhy.net",
                blockWithoutVpn = true,
                showVpnState = true,
                vpn = VpnState.INACTIVE,
                tabOverride = false,
            )
        )
        assertTrue(
            VpnGate.shouldBlock(
                url = "http://example.com/test",
                blockWithoutVpn = true,
                showVpnState = true,
                vpn = VpnState.INACTIVE,
                tabOverride = false,
            )
        )
        assertTrue(
            VpnGate.shouldBlock(
                url = "HTTPS://X.COM",
                blockWithoutVpn = true,
                showVpnState = true,
                vpn = VpnState.INACTIVE,
                tabOverride = false,
            )
        )
        assertTrue(
            VpnGate.shouldBlock(
                url = "HTTP://EXAMPLE.ORG/PATH",
                blockWithoutVpn = true,
                showVpnState = true,
                vpn = VpnState.INACTIVE,
                tabOverride = false,
            )
        )
    }

    @Test
    fun `shouldBlock returns false when blockWithoutVpn is false`() {
        assertFalse(
            VpnGate.shouldBlock(
                url = "https://fmhy.net",
                blockWithoutVpn = false,
                showVpnState = true,
                vpn = VpnState.INACTIVE,
                tabOverride = false,
            )
        )
    }

    @Test
    fun `shouldBlock returns false when showVpnState is false`() {
        assertFalse(
            VpnGate.shouldBlock(
                url = "https://fmhy.net",
                blockWithoutVpn = true,
                showVpnState = false,
                vpn = VpnState.INACTIVE,
                tabOverride = false,
            )
        )
    }

    @Test
    fun `shouldBlock returns false when vpn is ACTIVE`() {
        assertFalse(
            VpnGate.shouldBlock(
                url = "https://fmhy.net",
                blockWithoutVpn = true,
                showVpnState = true,
                vpn = VpnState.ACTIVE,
                tabOverride = false,
            )
        )
    }

    @Test
    fun `shouldBlock returns false when tabOverride is true`() {
        assertFalse(
            VpnGate.shouldBlock(
                url = "https://fmhy.net",
                blockWithoutVpn = true,
                showVpnState = true,
                vpn = VpnState.INACTIVE,
                tabOverride = true,
            )
        )
    }

    @Test
    fun `shouldBlock returns false for start screen urls`() {
        assertFalse(
            VpnGate.shouldBlock(
                url = "about:blank",
                blockWithoutVpn = true,
                showVpnState = true,
                vpn = VpnState.INACTIVE,
                tabOverride = false,
            )
        )
        assertFalse(
            VpnGate.shouldBlock(
                url = "",
                blockWithoutVpn = true,
                showVpnState = true,
                vpn = VpnState.INACTIVE,
                tabOverride = false,
            )
        )
        assertFalse(
            VpnGate.shouldBlock(
                url = "   ",
                blockWithoutVpn = true,
                showVpnState = true,
                vpn = VpnState.INACTIVE,
                tabOverride = false,
            )
        )
    }

    @Test
    fun `shouldBlock returns false for non-web urls`() {
        assertFalse(
            VpnGate.shouldBlock(
                url = "data:text/html,x",
                blockWithoutVpn = true,
                showVpnState = true,
                vpn = VpnState.INACTIVE,
                tabOverride = false,
            )
        )
        assertFalse(
            VpnGate.shouldBlock(
                url = "file:///x",
                blockWithoutVpn = true,
                showVpnState = true,
                vpn = VpnState.INACTIVE,
                tabOverride = false,
            )
        )
        assertFalse(
            VpnGate.shouldBlock(
                url = "javascript:void(0)",
                blockWithoutVpn = true,
                showVpnState = true,
                vpn = VpnState.INACTIVE,
                tabOverride = false,
            )
        )
    }
}
