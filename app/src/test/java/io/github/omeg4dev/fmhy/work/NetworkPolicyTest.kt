package io.github.omeg4dev.fmhy.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyTest {

    @Test
    fun kindOf_meteredWifiAndEthernetMapping() {
        // Disconnected
        assertEquals(NetworkKind.NONE, NetworkPolicy.kindOf(isWifiOrEthernet = false, isConnected = false, isMetered = false))
        assertEquals(NetworkKind.NONE, NetworkPolicy.kindOf(isWifiOrEthernet = true, isConnected = false, isMetered = false))
        assertEquals(NetworkKind.NONE, NetworkPolicy.kindOf(isWifiOrEthernet = true, isConnected = false, isMetered = true))

        // Unmetered Wi-Fi or Ethernet -> WIFI
        assertEquals(NetworkKind.WIFI, NetworkPolicy.kindOf(isWifiOrEthernet = true, isConnected = true, isMetered = false))

        // Metered Wi-Fi or Ethernet -> MOBILE
        assertEquals(NetworkKind.MOBILE, NetworkPolicy.kindOf(isWifiOrEthernet = true, isConnected = true, isMetered = true))

        // Cellular (metered or unmetered) -> MOBILE
        assertEquals(NetworkKind.MOBILE, NetworkPolicy.kindOf(isWifiOrEthernet = false, isConnected = true, isMetered = true))
        assertEquals(NetworkKind.MOBILE, NetworkPolicy.kindOf(isWifiOrEthernet = false, isConnected = true, isMetered = false))
    }

    @Test
    fun allowed_switchOffAllowsAll() {
        assertTrue(NetworkPolicy.allowed(switchOn = false, kind = NetworkKind.WIFI, overrideThisTime = false))
        assertTrue(NetworkPolicy.allowed(switchOn = false, kind = NetworkKind.MOBILE, overrideThisTime = false))
        assertTrue(NetworkPolicy.allowed(switchOn = false, kind = NetworkKind.NONE, overrideThisTime = false))
    }

    @Test
    fun allowed_switchOnOnlyAllowsWifiWithoutOverride() {
        assertTrue(NetworkPolicy.allowed(switchOn = true, kind = NetworkKind.WIFI, overrideThisTime = false))
        assertFalse(NetworkPolicy.allowed(switchOn = true, kind = NetworkKind.MOBILE, overrideThisTime = false))
        assertFalse(NetworkPolicy.allowed(switchOn = true, kind = NetworkKind.NONE, overrideThisTime = false))
    }

    @Test
    fun allowed_overridePerCallAllowsMobileAndNone() {
        assertTrue(NetworkPolicy.allowed(switchOn = true, kind = NetworkKind.WIFI, overrideThisTime = true))
        assertTrue(NetworkPolicy.allowed(switchOn = true, kind = NetworkKind.MOBILE, overrideThisTime = true))
        assertTrue(NetworkPolicy.allowed(switchOn = true, kind = NetworkKind.NONE, overrideThisTime = true))
    }

    @Test
    fun needsAsk_trueOnMobileOnlyWhenManualAndSwitchOn() {
        // True only when manual && switchOn && kind == MOBILE
        assertTrue(NetworkPolicy.needsAsk(manual = true, switchOn = true, kind = NetworkKind.MOBILE))

        // False on Wi-Fi or None
        assertFalse(NetworkPolicy.needsAsk(manual = true, switchOn = true, kind = NetworkKind.WIFI))
        assertFalse(NetworkPolicy.needsAsk(manual = true, switchOn = true, kind = NetworkKind.NONE))

        // False when switch is off
        assertFalse(NetworkPolicy.needsAsk(manual = true, switchOn = false, kind = NetworkKind.MOBILE))
        assertFalse(NetworkPolicy.needsAsk(manual = true, switchOn = false, kind = NetworkKind.WIFI))

        // False when not manual
        assertFalse(NetworkPolicy.needsAsk(manual = false, switchOn = true, kind = NetworkKind.MOBILE))
        assertFalse(NetworkPolicy.needsAsk(manual = false, switchOn = false, kind = NetworkKind.MOBILE))
    }

    @Test
    fun firstWikiNeedsAsk_asksOnlyWhenNothingBuiltInOrLocal() {
        // Nothing built in, nothing local, switch on, mobile -> asks
        assertTrue(NetworkPolicy.firstWikiNeedsAsk(
            hasBuiltInWiki = false,
            hasLocalWiki = false,
            switchOn = true,
            kind = NetworkKind.MOBILE,
        ))

        // Has built-in wiki -> does not ask
        assertFalse(NetworkPolicy.firstWikiNeedsAsk(
            hasBuiltInWiki = true,
            hasLocalWiki = false,
            switchOn = true,
            kind = NetworkKind.MOBILE,
        ))

        // Has local wiki -> does not ask
        assertFalse(NetworkPolicy.firstWikiNeedsAsk(
            hasBuiltInWiki = false,
            hasLocalWiki = true,
            switchOn = true,
            kind = NetworkKind.MOBILE,
        ))

        // Both present -> does not ask
        assertFalse(NetworkPolicy.firstWikiNeedsAsk(
            hasBuiltInWiki = true,
            hasLocalWiki = true,
            switchOn = true,
            kind = NetworkKind.MOBILE,
        ))

        // Switch off -> does not ask
        assertFalse(NetworkPolicy.firstWikiNeedsAsk(
            hasBuiltInWiki = false,
            hasLocalWiki = false,
            switchOn = false,
            kind = NetworkKind.MOBILE,
        ))

        // On Wi-Fi or None -> does not ask
        assertFalse(NetworkPolicy.firstWikiNeedsAsk(
            hasBuiltInWiki = false,
            hasLocalWiki = false,
            switchOn = true,
            kind = NetworkKind.WIFI,
        ))
        assertFalse(NetworkPolicy.firstWikiNeedsAsk(
            hasBuiltInWiki = false,
            hasLocalWiki = false,
            switchOn = true,
            kind = NetworkKind.NONE,
        ))
    }
}
