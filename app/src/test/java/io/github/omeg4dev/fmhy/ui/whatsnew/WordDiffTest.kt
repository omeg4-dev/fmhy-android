package io.github.omeg4dev.fmhy.ui.whatsnew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordDiffTest {

    @Test
    fun testDescriptionOfRealLines() {
        // Line 1: with star, bold, first link, separator, and extra link
        val line1 = "* ⭐ **[PopcornMovies](https://popcornmovies.ac/)** - Movies / TV / Anime / [Discord](https://discord.gg/abc)"
        assertEquals("Movies / TV / Anime / Discord", WordDiff.descriptionOf(line1))

        // Line 2: glowing star, dash bullet, multiple tags
        val line2 = "- 🌟 **[7Movies](https://7movies.ac/)** - Fast streaming / No ads"
        assertEquals("Fast streaming / No ads", WordDiff.descriptionOf(line2))

        // Line 3: plain link without stars
        val line3 = "* [DirectDL](https://directdl.net) - High speed mirrors / [Forum](https://forum.net)"
        assertEquals("High speed mirrors / Forum", WordDiff.descriptionOf(line3))

        // Line 4: simple description without extra links
        val line4 = "* **[ToolSite](https://tools.org)** - A collection of web utilities"
        assertEquals("A collection of web utilities", WordDiff.descriptionOf(line4))
    }

    @Test
    fun testWordDiffInsert() {
        val old = "quick fox jumps"
        val new = "quick brown fox jumps"
        val diff = WordDiff.wordDiff(old, new)

        val expected = listOf(
            DiffSegment("quick", DiffType.SAME),
            DiffSegment("brown", DiffType.ADDED),
            DiffSegment("fox jumps", DiffType.SAME),
        )
        assertEquals(expected, diff)
    }

    @Test
    fun testWordDiffDelete() {
        val old = "quick brown fox jumps"
        val new = "quick jumps"
        val diff = WordDiff.wordDiff(old, new)

        val expected = listOf(
            DiffSegment("quick", DiffType.SAME),
            DiffSegment("brown fox", DiffType.REMOVED),
            DiffSegment("jumps", DiffType.SAME),
        )
        assertEquals(expected, diff)
    }

    @Test
    fun testWordDiffReplace() {
        val old = "the red car"
        val new = "the blue car"
        val diff = WordDiff.wordDiff(old, new)

        val expected = listOf(
            DiffSegment("the", DiffType.SAME),
            DiffSegment("red", DiffType.REMOVED),
            DiffSegment("blue", DiffType.ADDED),
            DiffSegment("car", DiffType.SAME),
        )
        assertEquals(expected, diff)
    }

    @Test
    fun testWordDiffTokenCap() {
        val oldTokens = (1..405).joinToString(" ") { "old$it" }
        val newTokens = (1..10).joinToString(" ") { "new$it" }
        val diff = WordDiff.wordDiff(oldTokens, newTokens)

        assertEquals(2, diff.size)
        assertEquals(DiffType.REMOVED, diff[0].type)
        assertEquals(oldTokens, diff[0].text)
        assertEquals(DiffType.ADDED, diff[1].type)
        assertEquals(newTokens, diff[1].text)
    }

    @Test
    fun testCompactDiffContextAndGaps() {
        // 10 unchanged words, 1 changed word, 10 unchanged words
        val prefix = (1..10).joinToString(" ") { "pre$it" }
        val suffix = (1..10).joinToString(" ") { "post$it" }
        val old = "$prefix targetOld $suffix"
        val new = "$prefix targetNew $suffix"

        val fullDiff = WordDiff.wordDiff(old, new)
        val compacted = WordDiff.compactDiff(fullDiff, context = 4)

        // Should start with gap "…"
        assertEquals(DiffSegment("…", DiffType.SAME), compacted.first())

        // Context before: last 4 pre tokens: pre7 pre8 pre9 pre10
        assertEquals(DiffSegment("pre7 pre8 pre9 pre10", DiffType.SAME), compacted[1])
        assertEquals(DiffSegment("targetOld", DiffType.REMOVED), compacted[2])
        assertEquals(DiffSegment("targetNew", DiffType.ADDED), compacted[3])

        // Context after: first 4 post tokens: post1 post2 post3 post4
        assertEquals(DiffSegment("post1 post2 post3 post4", DiffType.SAME), compacted[4])

        // Should end with gap "…"
        assertEquals(DiffSegment("…", DiffType.SAME), compacted.last())
    }

    @Test
    fun testCompactDiffNoChangeReturnsOriginal() {
        val diff = listOf(DiffSegment("all same tokens here", DiffType.SAME))
        val compacted = WordDiff.compactDiff(diff, context = 4)
        assertEquals(diff, compacted)
    }

    @Test
    fun plainLineKeepsAllNamesAndDropsMarkup() {
        val line = "* ⭐ **[Alfred](https://www.alfredapp.com/)**, [Raycast](https://www.raycast.com/) / [FMHY Search](https://x.y/z) - Keystroke Launchers"
        assertEquals("Alfred, Raycast / FMHY Search - Keystroke Launchers", WordDiff.plainLine(line))
    }
}
