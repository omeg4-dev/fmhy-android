package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupPolicyTest {

    @Test
    fun testSameSiteDetection() {
        assertTrue(PopupPolicy.isSameSite("https://example.com/page", "https://sub.example.com/other"))
        assertTrue(PopupPolicy.isSameSite("https://a.b.example.co.uk", "https://c.example.co.uk"))
        assertFalse(PopupPolicy.isSameSite("https://example.com", "https://other.com"))
    }

    @Test
    fun testBlockerOffAllowsAll() {
        val decision = PopupPolicy.evaluate(
            targetUrl = "https://ad.com/popup",
            currentUrl = "https://example.com/",
            isUserGesture = false,
            popupBlockerEnabled = false,
            popupsSitePref = null,
        )
        assertEquals(PopupDecision.Allow(isBackground = false), decision)
    }

    @Test
    fun testSiteOverrideAllowsPopupsEvenIfGlobalBlockerOn() {
        val decision = PopupPolicy.evaluate(
            targetUrl = "https://ad.com/popup",
            currentUrl = "https://example.com/",
            isUserGesture = false,
            popupBlockerEnabled = true,
            popupsSitePref = true,
        )
        assertEquals(PopupDecision.Allow(isBackground = false), decision)
    }

    @Test
    fun testWithoutGestureIsBlockedWhenBlockerOn() {
        val decision = PopupPolicy.evaluate(
            targetUrl = "https://example.com/same-site-popup",
            currentUrl = "https://example.com/",
            isUserGesture = false,
            popupBlockerEnabled = true,
            popupsSitePref = null,
        )
        assertTrue(decision is PopupDecision.Block)
    }

    @Test
    fun testWithGestureSameSiteAllowedInBackgroundTab() {
        val decision = PopupPolicy.evaluate(
            targetUrl = "https://sub.example.com/popup",
            currentUrl = "https://example.com/",
            isUserGesture = true,
            popupBlockerEnabled = true,
            popupsSitePref = null,
        )
        assertEquals(PopupDecision.Allow(isBackground = true), decision)
    }

    @Test
    fun testWithGestureCrossSiteAllowedIfNotBlockedByEngine() {
        val decision = PopupPolicy.evaluate(
            targetUrl = "https://partner.com/page",
            currentUrl = "https://example.com/",
            isUserGesture = true,
            popupBlockerEnabled = true,
            popupsSitePref = null,
            isBlockedByEngine = { _, _ -> false }
        )
        assertEquals(PopupDecision.Allow(isBackground = true), decision)
    }

    @Test
    fun testWithGestureCrossSiteBlockedIfBlockedByEngine() {
        val decision = PopupPolicy.evaluate(
            targetUrl = "https://adserver.com/banner",
            currentUrl = "https://example.com/",
            isUserGesture = true,
            popupBlockerEnabled = true,
            popupsSitePref = null,
            isBlockedByEngine = { _, _ -> true }
        )
        assertTrue(decision is PopupDecision.Block)
    }
}
