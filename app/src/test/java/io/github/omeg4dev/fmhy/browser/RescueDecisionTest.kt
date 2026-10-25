package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.browser.RescueDecision.Action
import io.github.omeg4dev.fmhy.browser.RescueDecision.PageProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RescueDecisionTest {
    private val url = "https://virgil.samidy.com/games"

    private fun decide(
        mainFrame: Boolean = true,
        failing: String = url,
        current: List<String> = listOf(url),
        contentShown: Boolean = false,
        retries: Int = 0,
    ) = RescueDecision.decide(mainFrame, failing, current, contentShown, retries)

    @Test fun sideRequestsNeverShow() {
        assertEquals(Action.IGNORE, decide(mainFrame = false, retries = 5))
    }

    @Test fun otherAddressIsIgnored() {
        assertEquals(Action.IGNORE, decide(failing = "https://virgil.samidy.com/software", retries = 5))
    }

    @Test fun redirectTargetCountsViaSourceUrl() {
        val target = "https://mirror.example.org/"
        assertEquals(Action.RETRY, decide(failing = target, current = listOf(url, target)))
    }

    @Test fun loadedPageIsNeverReplaced() {
        assertEquals(Action.IGNORE, decide(contentShown = true, retries = 5))
    }

    @Test fun retriesQuietlyTwiceThenShows() {
        assertEquals(Action.RETRY, decide(retries = 0))
        assertEquals(Action.RETRY, decide(retries = 1))
        assertEquals(Action.SHOW, decide(retries = 2))
        assertEquals(Action.SHOW, decide(retries = 3))
    }

    @Test fun retryDelaysAreShortAndGrowing() {
        assertEquals(1_000L, RescueDecision.retryDelayMs(0))
        assertEquals(2_000L, RescueDecision.retryDelayMs(1))
    }

    @Test fun sameAddressNormalises() {
        assertTrue(RescueDecision.sameAddress("https://example.org/a#top", "https://example.org/a"))
        assertTrue(RescueDecision.sameAddress("http://Example.org/a/", "https://www.example.org/a"))
        assertTrue(RescueDecision.sameAddress("https://example.org", "https://example.org/"))
        assertTrue(RescueDecision.sameAddress("https://example.org/?q=1", "https://example.org?q=1"))
    }

    @Test fun differentAddresses() {
        assertFalse(RescueDecision.sameAddress("https://example.org/a", "https://example.org/b"))
        assertFalse(RescueDecision.sameAddress("https://example.org/?q=1", "https://example.org/?q=2"))
        assertFalse(RescueDecision.sameAddress("https://example.org/", "https://example.net/"))
        assertFalse(RescueDecision.sameAddress(null, "https://example.org/"))
        assertFalse(RescueDecision.sameAddress("", ""))
    }

    @Test fun probeParsing() {
        assertEquals(PageProbe(123, 4, 5), RescueDecision.parseProbe("\"123,4,5\""))
        assertNull(RescueDecision.parseProbe(null))
        assertNull(RescueDecision.parseProbe("null"))
        assertNull(RescueDecision.parseProbe("\"1,2\""))
    }

    @Test fun errorPagesAreNotContent() {
        // nginx "503 Service Temporarily Unavailable"
        assertFalse(RescueDecision.looksLikeContent(PageProbe(45, 0, 0)))
        // Cloudflare 521 page
        assertFalse(RescueDecision.looksLikeContent(PageProbe(700, 4, 1)))
        assertFalse(RescueDecision.looksLikeContent(null))
    }

    @Test fun realPagesAreContent() {
        assertTrue(RescueDecision.looksLikeContent(PageProbe(5000, 3, 0)))
        assertTrue(RescueDecision.looksLikeContent(PageProbe(300, 40, 0)))
        assertTrue(RescueDecision.looksLikeContent(PageProbe(100, 2, 12)))
    }

    @Test
    fun testIsBlank() {
        assertTrue(RescueDecision.isBlank(PageProbe(0, 0, 0)))
        assertFalse(RescueDecision.isBlank(PageProbe(1, 0, 0)))
        assertFalse(RescueDecision.isBlank(PageProbe(0, 0, 1)))
        assertFalse(RescueDecision.isBlank(null))
    }

    @Test
    fun testBlankKind() {
        assertEquals(LoadFailureKind.BLANK, RescueDecision.blankKind(0))
        assertEquals(LoadFailureKind.BLOCKED, RescueDecision.blankKind(3))
    }

    @Test
    fun testIsWebPage() {
        assertFalse(RescueDecision.isWebPage("about:blank"))
        assertTrue(RescueDecision.isWebPage("HTTPS://x.y"))
    }

    @Test
    fun testParseFlag() {
        assertTrue(RescueDecision.parseFlag("\"1\""))
        assertFalse(RescueDecision.parseFlag("\"0\""))
        assertFalse(RescueDecision.parseFlag(null))
    }
}
