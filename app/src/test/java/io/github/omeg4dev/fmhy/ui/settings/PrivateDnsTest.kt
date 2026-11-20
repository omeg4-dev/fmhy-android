package io.github.omeg4dev.fmhy.ui.settings

import io.github.omeg4dev.fmhy.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateDnsTest {

    @Test
    fun privateDnsStateOfMapping() {
        assertEquals(PrivateDnsState.Off, privateDnsStateOf("off", null))
        assertEquals(PrivateDnsState.Off, privateDnsStateOf("OFF", "x"))
        assertEquals(PrivateDnsState.Automatic, privateDnsStateOf("opportunistic", null))
        assertEquals(PrivateDnsState.Provider("dns.google"), privateDnsStateOf("hostname", " dns.google "))
        assertEquals(PrivateDnsState.Unknown, privateDnsStateOf("hostname", ""))
        assertEquals(PrivateDnsState.Unknown, privateDnsStateOf("hostname", null))
        assertEquals(PrivateDnsState.Unknown, privateDnsStateOf(null, null))
        assertEquals(PrivateDnsState.Unknown, privateDnsStateOf("weird", null))
    }

    @Test
    fun privateDnsSummaryOutputs() {
        assertEquals("Off", privateDnsSummary(PrivateDnsState.Off))
        assertEquals("Automatic", privateDnsSummary(PrivateDnsState.Automatic))
        assertEquals("dns.google", privateDnsSummary(PrivateDnsState.Provider("dns.google")))
        assertEquals("Open the system setting to see it", privateDnsSummary(PrivateDnsState.Unknown))
    }

    @Test
    fun catalogEntryContainsExpectedKeywordsAndIsRegistered() {
        val entry = SettingsCatalog.protectionPrivateDns
        assertEquals("protection.private_dns", entry.id)
        assertEquals(SettingsPage.PROTECTION, entry.page)
        assertTrue("protectionPrivateDns must be in SettingsCatalog.all", entry in SettingsCatalog.all)
        assertTrue("Keywords must contain 'dns'", "dns" in entry.keywords)
        assertTrue("Keywords must contain 'doh'", "doh" in entry.keywords)
        assertTrue("Keywords must contain 'dot'", "dot" in entry.keywords)
    }
}
