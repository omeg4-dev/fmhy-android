package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedirectPolicyTest {

    @Test
    fun testFirstLoadIsNeverBlocked() {
        val decision = RedirectPolicy.evaluate(
            targetUrl = "https://example.com/start",
            currentUrl = "",
            isForMainFrame = true,
            hasGesture = false,
            isRedirect = false,
            isFirstLoad = true,
            redirectGuardEnabled = true,
            blockAppLinksEnabled = true,
        )
        assertEquals(RedirectDecision.Allow, decision)
    }

    @Test
    fun testFirstLoadWithAboutBlankIsNeverBlocked() {
        val decision = RedirectPolicy.evaluate(
            targetUrl = "https://example.com/start",
            currentUrl = "about:blank",
            isForMainFrame = true,
            hasGesture = false,
            isRedirect = false,
            isFirstLoad = false,
            redirectGuardEnabled = true,
            blockAppLinksEnabled = true,
        )
        assertEquals(RedirectDecision.Allow, decision)
    }

    @Test
    fun testSameSiteIsAlwaysAllowed() {
        val decision = RedirectPolicy.evaluate(
            targetUrl = "https://sub.example.com/page",
            currentUrl = "https://example.com/home",
            isForMainFrame = true,
            hasGesture = false,
            isRedirect = false,
            isFirstLoad = false,
            redirectGuardEnabled = true,
            blockAppLinksEnabled = true,
        )
        assertEquals(RedirectDecision.Allow, decision)
    }

    @Test
    fun testUserGestureAllowsCrossSite() {
        val decision = RedirectPolicy.evaluate(
            targetUrl = "https://other.org/destination",
            currentUrl = "https://example.com/home",
            isForMainFrame = true,
            hasGesture = true,
            isRedirect = false,
            isFirstLoad = false,
            redirectGuardEnabled = true,
            blockAppLinksEnabled = true,
        )
        assertEquals(RedirectDecision.Allow, decision)
    }

    @Test
    fun testServerSideRedirectIsAllowed() {
        val decision = RedirectPolicy.evaluate(
            targetUrl = "https://redirected.org/destination",
            currentUrl = "https://example.com/home",
            isForMainFrame = true,
            hasGesture = false,
            isRedirect = true,
            isFirstLoad = false,
            redirectGuardEnabled = true,
            blockAppLinksEnabled = true,
        )
        assertEquals(RedirectDecision.Allow, decision)
    }

    @Test
    fun testCrossSiteNonGestureBlockedWhenRedirectGuardOn() {
        val decision = RedirectPolicy.evaluate(
            targetUrl = "https://malicious.org/ad",
            currentUrl = "https://example.com/home",
            isForMainFrame = true,
            hasGesture = false,
            isRedirect = false,
            isFirstLoad = false,
            redirectGuardEnabled = true,
            blockAppLinksEnabled = true,
        )
        assertTrue(decision is RedirectDecision.BlockRedirect)
        val blocked = decision as RedirectDecision.BlockRedirect
        assertEquals("malicious.org", blocked.host)
        assertEquals("https://malicious.org/ad", blocked.targetUrl)
    }

    @Test
    fun testCrossSiteNonGestureAllowedWhenRedirectGuardOff() {
        val decision = RedirectPolicy.evaluate(
            targetUrl = "https://malicious.org/ad",
            currentUrl = "https://example.com/home",
            isForMainFrame = true,
            hasGesture = false,
            isRedirect = false,
            isFirstLoad = false,
            redirectGuardEnabled = false,
            blockAppLinksEnabled = true,
        )
        assertEquals(RedirectDecision.Allow, decision)
    }

    @Test
    fun testAllowedOnceBypassesGuard() {
        val decision = RedirectPolicy.evaluate(
            targetUrl = "https://malicious.org/ad",
            currentUrl = "https://example.com/home",
            isForMainFrame = true,
            hasGesture = false,
            isRedirect = false,
            isFirstLoad = false,
            redirectGuardEnabled = true,
            blockAppLinksEnabled = true,
            isAllowedOnce = true,
        )
        assertEquals(RedirectDecision.Allow, decision)
    }

    @Test
    fun testJavascriptSchemeNeverOpened() {
        val decision = RedirectPolicy.evaluate(
            targetUrl = "javascript:alert(1)",
            currentUrl = "https://example.com/",
            isForMainFrame = true,
            hasGesture = true,
            isRedirect = false,
            isFirstLoad = false,
            redirectGuardEnabled = true,
            blockAppLinksEnabled = true,
        )
        assertEquals(RedirectDecision.BlockJavascript, decision)
    }

    @Test
    fun testAppLinkBlockedWhenBlockAppLinksOn() {
        val intentUrl = "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;S.browser_fallback_url=https%3A%2F%2Fzxing.org;end"
        val decision = RedirectPolicy.evaluate(
            targetUrl = intentUrl,
            currentUrl = "https://example.com/",
            isForMainFrame = true,
            hasGesture = true,
            isRedirect = false,
            isFirstLoad = false,
            redirectGuardEnabled = true,
            blockAppLinksEnabled = true,
        )
        assertTrue(decision is RedirectDecision.BlockAppLink)
        val appLink = decision as RedirectDecision.BlockAppLink
        assertEquals(intentUrl, appLink.url)
        assertEquals("https://zxing.org", appLink.fallbackUrl)
    }

    @Test
    fun testAppLinkAskedWhenBlockAppLinksOff() {
        val marketUrl = "market://details?id=com.example.app"
        val decision = RedirectPolicy.evaluate(
            targetUrl = marketUrl,
            currentUrl = "https://example.com/",
            isForMainFrame = true,
            hasGesture = true,
            isRedirect = false,
            isFirstLoad = false,
            redirectGuardEnabled = true,
            blockAppLinksEnabled = false,
        )
        assertTrue(decision is RedirectDecision.AskAppLink)
        val appLink = decision as RedirectDecision.AskAppLink
        assertEquals(marketUrl, appLink.url)
        assertEquals(null, appLink.fallbackUrl)
    }
}
