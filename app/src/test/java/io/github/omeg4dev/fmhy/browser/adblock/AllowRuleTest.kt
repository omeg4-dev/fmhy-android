package io.github.omeg4dev.fmhy.browser.adblock

import io.github.omeg4dev.fmhy.data.adblock.FilterEngineBuilder
import io.github.omeg4dev.fmhy.data.adblock.FilterRequest
import io.github.omeg4dev.fmhy.data.adblock.MatchResult
import io.github.omeg4dev.fmhy.data.adblock.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowRuleTest {

    @Test
    fun testForHost() {
        assertEquals("@@||cdn.tracker.net^\$domain=example.com", AllowRule.forHost("cdn.tracker.net", "example.com"))
        assertEquals("@@||cdn.tracker.net^\$domain=example.com", AllowRule.forHost("CDN.TRACKER.NET", "EXAMPLE.COM"))
    }

    @Test
    fun testAppend() {
        val r = "@@||cdn.tracker.net^\$domain=example.com"
        assertEquals(r, AllowRule.append("", r))

        val appended = AllowRule.append("||a.com^\n", r)
        assertEquals("||a.com^\n$r", appended)

        val appendedTwice = AllowRule.append(appended, r)
        assertEquals(appended, appendedTwice)
    }

    @Test
    fun testPageSite() {
        assertEquals("qwen.ai", AllowRule.pageSite("https://chat.qwen.ai/c/1"))
        assertNull(AllowRule.pageSite("about:blank"))
    }

    @Test
    fun testEngineRoundTrip() {
        val rule = AllowRule.forHost("cdn.tracker.net", "example.com")
        val engine = FilterEngineBuilder().apply {
            addCustomRules("||tracker.net^\n$rule")
        }.build()

        val allowedReq = FilterRequest("https://cdn.tracker.net/x.js", "https://example.com/", ResourceType.SCRIPT)
        val allowedResult = engine.match(allowedReq)
        assertTrue("Expected match on example.com not to be Block, but got $allowedResult", allowedResult !is MatchResult.Block)

        val blockedReq = FilterRequest("https://cdn.tracker.net/x.js", "https://other.org/", ResourceType.SCRIPT)
        val blockedResult = engine.match(blockedReq)
        assertTrue("Expected match on other.org to be Block, but got $blockedResult", blockedResult is MatchResult.Block)
    }

    @Test
    fun testBypassKey() {
        assertEquals("example.com cdn.tracker.net", AllowRule.bypassKey("EXAMPLE.COM", "CDN.TRACKER.NET"))
    }

    @Test
    fun testAllowedHostsForOnlyReturnsLinesForThatSiteAndIgnoresOtherRules() {
        val rules = """
            ||ads.com^
            ! comment line
            @@||cdn.example.org^${'$'}domain=targetsite.com
            @@||static.example.org^${'$'}domain=targetsite.com
            @@||other.org^${'$'}domain=othersite.com
            @@||sub.cdn.example.org^${'$'}domain=targetsite.com
            invalid@@||rule
        """.trimIndent()

        val allowed = AllowRule.allowedHostsFor(rules, "targetsite.com")
        assertEquals(
            listOf("cdn.example.org", "static.example.org", "sub.cdn.example.org"),
            allowed
        )

        val allowedUpper = AllowRule.allowedHostsFor(rules, "TARGETSITE.COM")
        assertEquals(
            listOf("cdn.example.org", "static.example.org", "sub.cdn.example.org"),
            allowedUpper
        )

        assertEquals(listOf("other.org"), AllowRule.allowedHostsFor(rules, "othersite.com"))
        assertEquals(emptyList<String>(), AllowRule.allowedHostsFor(rules, "nonexistent.com"))
        assertEquals(emptyList<String>(), AllowRule.allowedHostsFor(rules, ""))
    }

    @Test
    fun testRemoveDropsExactlyOneLineAndKeepsOthers() {
        val rules = """
            ||ads.com^
            @@||a.com^${'$'}domain=site.com
            @@||b.com^${'$'}domain=site.com
            ||tracker.net^
        """.trimIndent()

        val removed = AllowRule.remove(rules, "a.com", "site.com")
        val expected = """
            ||ads.com^
            @@||b.com^${'$'}domain=site.com
            ||tracker.net^
        """.trimIndent()
        assertEquals(expected, removed)

        assertEquals(expected, AllowRule.remove(expected, "nonexistent.com", "site.com"))
    }
}
