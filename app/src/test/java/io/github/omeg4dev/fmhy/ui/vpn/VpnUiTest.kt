package io.github.omeg4dev.fmhy.ui.vpn

import io.github.omeg4dev.fmhy.work.IpInfo
import io.github.omeg4dev.fmhy.work.IpResult
import io.github.omeg4dev.fmhy.work.VpnState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnUiTest {

    @Test
    fun testIpLineOf() {
        assertEquals(IpLine.Loading, ipLineOf(null))
        assertEquals(IpLine.Hidden, ipLineOf(IpResult.Disabled))
        assertEquals(IpLine.Text("Unavailable"), ipLineOf(IpResult.Unavailable))
        assertEquals(
            IpLine.Text("1.2.3.4 · Germany"),
            ipLineOf(IpResult.Value(IpInfo("1.2.3.4", "Germany")))
        )
    }

    @Test
    fun testVpnStateText() {
        assertEquals("VPN active", vpnStateText(VpnState.ACTIVE))
        assertEquals("No VPN detected", vpnStateText(VpnState.INACTIVE))
    }

    @Test
    fun testVpnStateHint() {
        assertEquals(
            "Your traffic goes through a VPN. This cannot prove your real IP is hidden.",
            vpnStateHint(VpnState.ACTIVE)
        )
        assertEquals("Sites see your own IP address.", vpnStateHint(VpnState.INACTIVE))
    }

    @Test
    fun orbotOverridesVpnStateTexts() {
        VpnState.entries.forEach {
            assertEquals("Orbot proxy on", vpnStateText(it, orbot = true))
            assertEquals(
                "Your traffic goes through Orbot (Tor). This cannot prove your real IP is hidden.",
                vpnStateHint(it, orbot = true),
            )
            assertEquals(vpnIcon(VpnState.ACTIVE), vpnIcon(it, orbot = true))
        }
    }

    @Test
    fun orbotColourIsPurpleInBothThemes() {
        listOf(false, true).forEach { dark ->
            val c = orbotColor(dark)
            assertTrue("blue above red", c.blue > c.red)
            assertTrue("red above green", c.red > c.green)
        }
    }

    @Test
    fun testVpnIcon() {
        assertNotEquals(vpnIcon(VpnState.ACTIVE), vpnIcon(VpnState.INACTIVE))
    }
}
