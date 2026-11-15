package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A value written by Site settings must mean the same to PopupPolicy and the Shield sheet. */
class SitePopupPrefTest {

    private fun policyBlocks(stored: Boolean?): Boolean = PopupPolicy.evaluate(
        targetUrl = "https://ad.com/popup",
        currentUrl = "https://example.com/",
        isUserGesture = false,
        popupBlockerEnabled = true,
        popupsSitePref = stored,
    ) is PopupDecision.Block

    @Test
    fun switchOnStoresBlockedAndPolicyBlocks() {
        val stored = SitePopupPref.toStored(blockPopups = true)
        assertTrue(policyBlocks(stored))
        assertTrue(SitePopupPref.blockSwitch(stored, globalBlocker = false))
        assertEquals("pop-ups blocked", SitePopupPref.label(stored))
    }

    @Test
    fun switchOffStoresAllowedAndPolicyAllows() {
        val stored = SitePopupPref.toStored(blockPopups = false)
        assertTrue(!policyBlocks(stored))
        assertTrue(!SitePopupPref.blockSwitch(stored, globalBlocker = true))
        assertEquals("pop-ups allowed", SitePopupPref.label(stored))
    }

    @Test
    fun noOverrideFollowsGlobalBlocker() {
        assertTrue(SitePopupPref.blockSwitch(null, globalBlocker = true))
        assertTrue(!SitePopupPref.blockSwitch(null, globalBlocker = false))
        assertTrue(policyBlocks(null))
    }
}
