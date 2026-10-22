package io.github.omeg4dev.fmhy.data.search

import io.github.omeg4dev.fmhy.data.model.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryEditTest {
    private val sections = listOf(
        Section("video", "Movies / TV / Anime", "", 1, 10),
        Section("videopiracyguide", "Video Piracy Guide", "", 2, 3),
        Section("tools", "Tools", "", 3, 5),
    )

    @Test
    fun tokensCarryRangesAndKinds() {
        val t = QueryParser.tokens("anime in:\"video games\" is:starred -foo \"a b\" site:www.GitHub.com is:nope")
        assertEquals(
            listOf(TokenKind.WORD, TokenKind.PARAM, TokenKind.PARAM, TokenKind.EXCLUDE, TokenKind.PHRASE, TokenKind.PARAM, TokenKind.WORD),
            t.map { it.kind },
        )
        assertEquals("video games", t[1].value)
        assertEquals("in:\"video games\"", t[1].text)
        assertEquals(6 until 22, t[1].start until t[1].end)
        assertEquals("github.com", t[5].value)
        assertEquals("is:nope", t[6].text) // unknown is: value stays a word, like the parser
    }

    @Test
    fun unclosedQuoteIsNotAPill() {
        val t = QueryParser.tokens("in:\"video ga")
        assertEquals(1, t.size)
        assertFalse(t[0].closed)
        assertFalse(t[0].isPill)
    }

    @Test
    fun parseAndTokensAgree() {
        val raw = "foo in:video in:\"x y\" -bar is:top is:b64 site:a.b \"p q\" baz"
        val q = QueryParser.parse(raw)
        val t = QueryParser.tokens(raw)
        assertEquals(q.inSections, t.filter { it.key == "in" }.map { it.value })
        assertEquals(q.excludes, t.filter { it.kind == TokenKind.EXCLUDE }.map { it.value })
        assertEquals(q.words, t.filter { it.kind == TokenKind.WORD }.map { it.value })
        assertEquals(q.phrases, t.filter { it.kind == TokenKind.PHRASE }.map { it.value })
    }

    @Test
    fun splitCommitsOnlyTokensFollowedBySpace() {
        val s = QueryEdit.split("anime in:video")
        assertTrue(s.pills.isEmpty())
        assertEquals("anime in:video", s.free)

        val c = QueryEdit.split("anime in:video ", cursor = 15)
        assertEquals(listOf("in:video"), c.pills.map { it.text })
        assertEquals("anime ", c.free)
        assertEquals(6, c.cursor)
    }

    @Test
    fun composeRoundTrips() {
        val raw = "is:starred -foo in:video anime"
        val s = QueryEdit.split(raw)
        assertEquals("is:starred -foo in:video anime", QueryEdit.compose(s.pills, s.free))
        assertEquals("anime", s.free)
        // pills move to the front
        assertEquals("in:video anime more", QueryEdit.canonical("anime in:video more"))
    }

    @Test
    fun toggleAddsAndRemovesPills() {
        var q = QueryEdit.toggleParam("stremio", "is", "starred")
        assertEquals("is:starred stremio", q)
        assertTrue(QueryEdit.hasParam(q, "is", "starred"))
        q = QueryEdit.toggleParam(q, "is", "b64")
        assertEquals("is:starred is:b64 stremio", q)
        q = QueryEdit.toggleParam(q, "is", "starred")
        assertEquals("is:b64 stremio", q)
        assertEquals("stremio", QueryEdit.removePill(q, "is:b64"))
        assertEquals("stremio", QueryEdit.dropLastPill(q))
    }

    @Test
    fun siteIsSingleValued() {
        val q = QueryEdit.addParam(QueryEdit.addParam("", "site", "a.com"), "site", "b.com")
        assertEquals("site:b.com ", q)
    }

    @Test
    fun sectionTogglesUseExactIdFirst() {
        // in:video must not also select videopiracyguide
        assertEquals(setOf("video"), QueryEdit.resolveSections("video", sections))
        assertEquals(setOf("video", "videopiracyguide"), QueryEdit.resolveSections("vid", sections))
        assertEquals(setOf("video"), QueryEdit.resolveSections("Movies", sections))
        var q = QueryEdit.toggleSection("x", "tools", sections)
        assertEquals("in:tools x", q)
        assertEquals(setOf("tools"), QueryEdit.selectedSections(q, sections))
        q = QueryEdit.toggleSection(q, "tools", sections)
        assertEquals("x", q)
        assertEquals("in:\"a b\" ", QueryEdit.addParam("", "in", "a b"))
    }

    @Test
    fun partialAtFindsTheTokenUnderTheCaret() {
        val free = "anime in:vi more"
        assertEquals("in:vi", QueryAssist.partialAt(free, 11)?.text)
        assertEquals("anime", QueryAssist.partialAt(free, 3)?.text)
        assertNull(QueryAssist.partialAt("anime ", 6))
    }

    @Test
    fun requestsAndReplacement() {
        assertEquals(AssistRequest(SuggestionKind.SECTION, "vi"), QueryAssist.request("in:vi"))
        assertEquals(AssistRequest(SuggestionKind.SECTION, "video g"), QueryAssist.request("in:\"video g"))
        assertEquals(AssistRequest(SuggestionKind.IS, ""), QueryAssist.request("is:"))
        assertEquals(AssistRequest(SuggestionKind.SITE, "git"), QueryAssist.request("site:git"))
        assertEquals(AssistRequest(SuggestionKind.OPERATOR, "si"), QueryAssist.request("si"))
        assertNull(QueryAssist.request("i"))
        assertNull(QueryAssist.request("inception"))
        assertNull(QueryAssist.request("foo:bar"))

        val sug = QueryAssist.sections("vid", sections).first()
        assertEquals("in:video", sug.insert)
        val free = "anime in:vid more"
        val partial = QueryAssist.partialAt(free, 12)!!
        val (text, caret) = QueryAssist.apply(free, partial, sug)
        assertEquals("anime in:video  more", text)
        assertEquals(15, caret)
        assertEquals(listOf("Starred", "Top picks", "Hidden links"), QueryAssist.isOptions("").map { it.label })
        assertEquals(listOf("top"), QueryAssist.isOptions("t").map { it.insert.removePrefix("is:") })
    }
}
