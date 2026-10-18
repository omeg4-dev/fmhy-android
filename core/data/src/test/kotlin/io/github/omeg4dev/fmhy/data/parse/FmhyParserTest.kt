package io.github.omeg4dev.fmhy.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FmhyParserTest {

    private val emptyB64 = Base64Map("<html></html>")
    private val testSection = SectionInfo(
        id = "test",
        title = "Test Section",
        description = "Test Desc",
        file = "test.md",
        ord = 0
    )

    private fun parseLine(line: String): ParsedSection {
        val markdown = "# Heading\n$line\n"
        return FmhyParser.parseSection(testSection, markdown, emptyB64)
    }

    @Test
    fun testFixture1PopcornMovies() {
        val line = "* 🌟 **[PopcornMovies](https://popcornmovies.ac/)** or [BingeBox](https://bingebox.ac/) - Movies / TV / Anime / Auto-Next"
        val parsed = parseLine(line)
        val entries = parsed.entries
        assertEquals(2, entries.size)
        assertEquals("PopcornMovies", entries[0].name)
        assertEquals("https://popcornmovies.ac/", entries[0].url)
        assertEquals(2, entries[0].tier)
        assertEquals("BingeBox", entries[1].name)
        assertEquals("https://bingebox.ac/", entries[1].url)
        assertEquals(0, entries[1].tier)

        assertEquals("Movies|TV|Anime|Auto-Next", entries[0].tags)
        assertEquals("Movies|TV|Anime|Auto-Next", entries[1].tags)
        assertEquals("Movies / TV / Anime / Auto-Next", entries[0].description)
        assertEquals("Movies / TV / Anime / Auto-Next", entries[1].description)
    }

    @Test
    fun testFixture27Movies() {
        val line = "* 🌟 **[7Movies](https://7movies.ac/)**, [2](https://7movies.ws/) - Movies / TV / Anime / Auto-Next / [Discord](https://discord.gg/JxgRaYMVHF) / [Telegram](https://t.me/+0XhCnzzvCt9hMTNl)"
        val parsed = parseLine(line)
        val entries = parsed.entries
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals("7Movies", e.name)
        assertEquals(2, e.tier)
        assertEquals(1, e.mirrors.size)
        assertEquals(LinkRow("2", "https://7movies.ws/"), e.mirrors[0])
        val extraLabels = e.extraLinks.map { it.label }.toSet()
        assertTrue(extraLabels.contains("Discord"))
        assertTrue(extraLabels.contains("Telegram"))
        assertEquals("Movies|TV|Anime|Auto-Next", e.tags)
    }

    @Test
    fun testFixture3Cascii() {
        val line = "* [Cascii](https://cascii.app/) / [GitHub](https://github.com/casparwylie/cascii-core), [Bob Editor](https://ivanceras.github.io/bob-editor/), [ASCII Flow](https://asciiflow.com/) or [tree](https://tree.nathanfriend.com/) - Create ASCII Diagrams"
        val parsed = parseLine(line)
        val entries = parsed.entries
        assertEquals(4, entries.size)
        val names = entries.map { it.name }
        assertTrue(names.contains("Cascii"))
        assertTrue(names.contains("Bob Editor"))
        assertTrue(names.contains("ASCII Flow"))
        assertTrue(names.contains("tree"))

        val cascii = entries.first { it.name == "Cascii" }
        assertTrue(cascii.extraLinks.any { it.label == "GitHub" })
        for (e in entries) {
            assertEquals("Create ASCII Diagrams", e.description)
        }
    }

    @Test
    fun testFixture4Kroki() {
        val line = "* ⭐ **[Kroki](https://kroki.io/#try)** - Text to Diagrams / [CLI](https://github.com/yuzutech/kroki-cli)"
        val parsed = parseLine(line)
        val entries = parsed.entries
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals("Kroki", e.name)
        assertEquals(1, e.tier)
        assertEquals("Text to Diagrams", e.tags)
        assertTrue(e.extraLinks.any { it.label == "CLI" })
    }

    @Test
    fun testFixture5DgmDrawdb() {
        val line = "[DGM](https://app.dgmjs.dev/), [DrawDB](https://www.drawdb.app/) / [Discord](https://discord.gg/BrjZgNrmR6), [Flourish](https://flourish.studio/)"
        val parsed = parseLine(line)
        val entries = parsed.entries
        assertEquals(3, entries.size)
        val names = entries.map { it.name }
        assertTrue(names.contains("DGM"))
        assertTrue(names.contains("DrawDB"))
        assertTrue(names.contains("Flourish"))
        for (e in entries) {
            assertTrue(e.isInlineMore)
        }
        val drawdb = entries.first { it.name == "DrawDB" }
        assertTrue(drawdb.extraLinks.any { it.label == "Discord" })
    }

    @Test
    fun testFixture6SteamunlockedUnparsed() {
        val line = "* SteamUnlocked - [IGG / NosTeam uploads](https://i.ibb.co/VgW2ymY/YUnRNpN.png) + very slow file host"
        val parsed = parseLine(line)
        assertEquals(0, parsed.entries.size)
        assertEquals(1, parsed.unparsedLines)
    }

    @Test
    fun testFixture7Note() {
        val line = "* **Note** - Repacks are compressed versions of games."
        val parsed = parseLine(line)
        assertEquals(0, parsed.entries.size)
        assertEquals(1, parsed.notes.size)
        assertTrue(parsed.notes[0].textMd.contains("Repacks"))
    }

    @Test
    fun testRealDisblock() {
        val line = "* [Disblock Origin](https://codeberg.org/AllPurposeMat/Disblock-Origin) or [Discord Adblock](https://codeberg.org/ridge/Discord-AdBlock) - Hide Discord Nitro / Boost Ads"
        val parsed = parseLine(line)
        assertEquals(2, parsed.entries.size)
        val names = parsed.entries.map { it.name }
        assertTrue(names.contains("Disblock Origin"))
        assertTrue(names.contains("Discord Adblock"))
        for (e in parsed.entries) {
            assertTrue(e.description.contains("Hide Discord Nitro"))
        }
    }

    @Test
    fun testRealHblock() {
        val line = "* [hBlock](https://hblock.molinero.dev/) - Blocklist Collection / [GitHub](https://github.com/hectorm/hblock)"
        val parsed = parseLine(line)
        assertEquals(1, parsed.entries.size)
        val e = parsed.entries[0]
        assertEquals("hBlock", e.name)
        assertEquals("Blocklist Collection", e.description)
        assertTrue(e.extraLinks.any { it.label == "GitHub" })
    }

    @Test
    fun testRealPopupBlockers() {
        val line = "* [Popup Blocker (strict)](https://github.com/schomery/popup-blocker), [Popupblocker All](https://addons.mozilla.org/firefox/addon/popupblockerall/) / [2](https://chromewebstore.google.com/detail/popupblocker/doklmegfghlnanggfgligimnnikiekde) or [PopupBlocker](https://github.com/AdguardTeam/PopupBlocker) (userscript) - Popup Blockers / [Note](https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes/popup-blockers.md)"
        val parsed = parseLine(line)
        assertTrue(parsed.entries.size >= 2)
        val names = parsed.entries.map { it.name }
        assertTrue(names.contains("Popup Blocker (strict)"))
    }

    @Test
    fun testHeadingsParsed() {
        val content = "# Main Heading\n## Sub Heading\n* [Example](https://example.com) - Test site\n"
        val parsed = FmhyParser.parseSection(testSection, content, emptyB64)
        assertEquals(2, parsed.headings.size)
        assertEquals(1, parsed.headings[0].level)
        assertEquals(2, parsed.headings[1].level)
        assertEquals(1, parsed.entries.size)
    }

    @Test
    fun testFrontMatterSkipped() {
        val content = "---\ntitle: Test\n---\n# Heading\n* [Site](https://example.com) - A site\n"
        val parsed = FmhyParser.parseSection(testSection, content, emptyB64)
        assertEquals(1, parsed.entries.size)
    }

    @Test
    fun testHtmlLinesSkipped() {
        val content = "# Heading\n<script>alert('hi')</script>\n* [Site](https://example.com) - Test\n"
        val parsed = FmhyParser.parseSection(testSection, content, emptyB64)
        assertEquals(1, parsed.entries.size)
    }

    @Test
    fun testFenceSkipped() {
        val content = "# Heading\n:::\nsome code\n:::\n* [Site](https://example.com) - Test\n"
        val parsed = FmhyParser.parseSection(testSection, content, emptyB64)
        assertEquals(1, parsed.entries.size)
    }

    @Test
    fun testBlockquoteNote() {
        val content = "# Heading\n> This is a note\n* [Site](https://example.com) - Test\n"
        val parsed = FmhyParser.parseSection(testSection, content, emptyB64)
        assertEquals(1, parsed.notes.size)
        assertTrue(parsed.notes[0].textMd.contains("This is a note"))
    }

    @Test
    fun testHeadingPath() {
        val content = "# Level 1\n## Level 2\n* [Site](https://example.com) - Test\n"
        val parsed = FmhyParser.parseSection(testSection, content, emptyB64)
        assertEquals(1, parsed.entries.size)
        assertTrue(parsed.entries[0].headingPath.contains("Test Section"))
        assertTrue(parsed.entries[0].headingPath.contains("Level 1"))
        assertTrue(parsed.entries[0].headingPath.contains("Level 2"))
    }
}
