package io.github.omeg4dev.fmhy.data.newbadge

import java.time.LocalDate
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewBadgeParserTest {

    @Test
    fun parseRealAudioHtml_matchesExpectedTsvExactly() {
        val htmlStream = checkNotNull(javaClass.getResourceAsStream("/newbadge/fmhy-net-audio.html")) {
            "fmhy-net-audio.html not found"
        }
        val tsvStream = checkNotNull(javaClass.getResourceAsStream("/newbadge/fmhy-net-audio.expected.tsv")) {
            "fmhy-net-audio.expected.tsv not found"
        }

        val html = htmlStream.bufferedReader().use { it.readText() }
        val expectedLines = tsvStream.bufferedReader().use { it.readLines() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val parts = line.split("\t")
                assertEquals(2, parts.size)
                parts[0] to LocalDate.parse(parts[1])
            }

        val result = NewBadgeParser.parse(html, "https://fmhy.net/")

        assertEquals(17, expectedLines.size)
        assertEquals(expectedLines.size, result.marks.size)
        assertEquals(0, result.skipped)
        assertTrue(result.sawBadge)

        for (i in expectedLines.indices) {
            val expected = expectedLines[i]
            val actual = result.marks[i]
            assertEquals("Mismatch at line $i URL", expected.first, actual.url)
            assertEquals("Mismatch at line $i Date", expected.second, actual.date)
        }
    }

    @Test
    fun parseSyntheticHtml_trapsAndValidMarks() {
        val stream = checkNotNull(javaClass.getResourceAsStream("/newbadge/fmhy-net-synthetic.html")) {
            "fmhy-net-synthetic.html not found"
        }
        val html = stream.bufferedReader().use { it.readText() }

        val result = NewBadgeParser.parse(html, "https://fmhy.net/")

        assertTrue(result.sawBadge)
        // Traps 4, 5, 6, 7, 8 are skipped
        assertEquals(5, result.skipped)

        val expectedMarks = listOf(
            Mark("https://example.com/item1", LocalDate.parse("2026-11-20")),
            Mark("https://example.com/item2", LocalDate.parse("2026-11-19")),
            Mark("https://example.com/item3?foo=1&bar=2\"test\"", LocalDate.parse("2026-11-18")),
            Mark("https://fmhy.net/relative/item4", LocalDate.parse("2026-11-17")),
            Mark("https://rentry.co/FMHYB64#wavernrs", LocalDate.parse("2026-11-16")),
            Mark("https://example.com/first", LocalDate.parse("2026-11-15")),
            Mark("https://example.com/unclosed", LocalDate.parse("2026-11-14")),
        )

        assertEquals(expectedMarks.size, result.marks.size)
        for (i in expectedMarks.indices) {
            assertEquals("Mismatch at mark $i", expectedMarks[i], result.marks[i])
        }
    }

    @Test
    fun performance_5mbSyntheticStringInUnder2s() {
        val chunk = """
            <li class="item"><a href="https://example.com/foo">Link</a> - some text</li>
            <li class="new-item"><a href="https://example.com/bar">Bar</a></li>
            <li class="new"><a href="https://example.com/item1?a=1&amp;b=2">⁠Item 1</a><button type="button" class="new-badge" aria-label="New, added 2026-11-15">New</button></li>
            <li class="starred new"><a href="/relative/path">Relative</a><button class="new-badge" aria-label="New, added 2026-11-16">New</button></li>
            <li class="new"><a href="javascript:void(0)">Bad</a><button class="new-badge" aria-label="New, added 2026-11-17">New</button></li>
        """.trimIndent()

        val sb = java.lang.StringBuilder()
        while (sb.length < 5 * 1024 * 1024) {
            sb.append(chunk).append("\n")
        }
        val bigHtml = sb.toString()

        val elapsed = measureTimeMillis {
            val result = NewBadgeParser.parse(bigHtml, "https://fmhy.net/")
            assertTrue(result.marks.isNotEmpty())
        }

        assertTrue("Expected 5MB parse < 2000ms, took ${elapsed}ms", elapsed < 2000L)
    }

    @Test
    fun emptyHtml_returnsEmptyResult() {
        val result = NewBadgeParser.parse("", "https://fmhy.net/")
        assertTrue(result.marks.isEmpty())
        assertEquals(0, result.skipped)
        assertFalse(result.sawBadge)
    }

    @Test
    fun htmlWithoutNewBadge_sawBadgeFalse() {
        val html = "<ul><li><a href='https://example.com'>Link</a></li></ul>"
        val result = NewBadgeParser.parse(html, "https://fmhy.net/")
        assertTrue(result.marks.isEmpty())
        assertEquals(0, result.skipped)
        assertFalse(result.sawBadge)
    }

    @Test
    fun sawBadgeTrueEvenIfNoMarks() {
        val html = "<div><button class='new-badge'>New</button></div>"
        val result = NewBadgeParser.parse(html, "https://fmhy.net/")
        assertTrue(result.marks.isEmpty())
        assertEquals(0, result.skipped)
        assertTrue(result.sawBadge)
    }

    @Test
    fun malformedDate_skipped() {
        val html = """<li class="new"><a href="https://example.com">Link</a><button aria-label="New, added 2026-99-99">New</button></li>"""
        val result = NewBadgeParser.parse(html, "https://fmhy.net/")
        assertTrue(result.marks.isEmpty())
        assertEquals(1, result.skipped)
        assertTrue(result.sawBadge)
    }

    @Test
    fun nonHttpScheme_skipped() {
        val html = """<li class="new"><a href="ftp://example.com/file">FTP</a><button aria-label="New, added 2026-11-20">New</button></li>"""
        val result = NewBadgeParser.parse(html, "https://fmhy.net/")
        assertTrue(result.marks.isEmpty())
        assertEquals(1, result.skipped)
        assertTrue(result.sawBadge)
    }
}
