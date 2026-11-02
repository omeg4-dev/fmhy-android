package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.user.SitePrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteFixTest {
    private val host = "upfilesgo.com"

    @Test
    fun blockingAppliesByDefault() {
        assertTrue(SiteFix.adblockActive(globalOn = true, sitePrefs = null))
        assertTrue(SiteFix.adblockActive(globalOn = true, sitePrefs = SitePrefs(host = host)))
    }

    @Test
    fun fixThisSiteAllowsAllAds() {
        assertFalse(SiteFix.adblockActive(globalOn = true, sitePrefs = SitePrefs(host = host, fixSite = true)))
    }

    @Test
    fun fixWinsOverAnExplicitBlockingOverride() {
        val prefs = SitePrefs(host = host, adblock = true, fixSite = true)
        assertFalse(SiteFix.adblockActive(globalOn = true, sitePrefs = prefs))
    }

    @Test
    fun resetToDefaultsBringsBlockingBack() {
        val fixed = SitePrefs(host = host, fixSite = true)
        assertFalse(SiteFix.adblockActive(globalOn = true, sitePrefs = fixed))
        // "Reset to defaults" clears the row, and turning blocking on in the sheet clears fixSite.
        assertTrue(SiteFix.adblockActive(globalOn = true, sitePrefs = fixed.copy(fixSite = null)))
    }

    @Test
    fun globalSwitchAndSiteOffStillWin() {
        assertFalse(SiteFix.adblockActive(globalOn = false, sitePrefs = null))
        assertFalse(SiteFix.adblockActive(globalOn = true, sitePrefs = SitePrefs(host = host, adblock = false)))
    }
}
