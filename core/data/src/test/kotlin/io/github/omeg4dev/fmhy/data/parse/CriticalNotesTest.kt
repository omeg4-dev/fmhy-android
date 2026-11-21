package io.github.omeg4dev.fmhy.data.parse

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalNotesTest {

    private fun findFixtureDir(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "schema/fixtures/critical-notes").exists()) {
            val parent = dir.parentFile ?: error("Could not find schema/fixtures/critical-notes from ${System.getProperty("user.dir")}")
            dir = parent
        }
        return File(dir, "schema/fixtures/critical-notes")
    }

    @Test
    fun parseNote_critical_by_name() {
        val raw = "#### Foo Warning\nDo not use Foo.\n"
        val parsed = CriticalNotes.parseNote("foo-critical", raw)
        assertNotNull(parsed)
        assertEquals("foo-critical", parsed!!.id)
        assertEquals("Foo Warning", parsed.title)
        assertEquals("#### Foo Warning\nDo not use Foo.", parsed.textMd)
        assertEquals("73ec7e5dfce0", parsed.hash)
    }

    @Test
    fun parseNote_front_matter_true_makes_note_critical() {
        val raw = "---\ncritical: true\n---\n## Bar Danger\nBar steals data.\n"
        val parsed = CriticalNotes.parseNote("bar-note", raw)
        assertNotNull(parsed)
        assertEquals("bar-note", parsed!!.id)
        assertEquals("Bar Danger", parsed.title)
        assertEquals("## Bar Danger\nBar steals data.", parsed.textMd)
        assertEquals("0d09fd9ff28b", parsed.hash)
    }

    @Test
    fun parseNote_front_matter_false_wins_over_name() {
        val raw = "---\ncritical: false\n---\n# Baz\nOld warning withdrawn.\n"
        val parsed = CriticalNotes.parseNote("baz-critical", raw)
        assertNull(parsed)
    }

    @Test
    fun parseNote_crlf_hash_keeps_carriage_returns() {
        val raw = "# Crlf Warning\r\nline two\r\n"
        val parsed = CriticalNotes.parseNote("crlf-critical", raw)
        assertNotNull(parsed)
        assertEquals("crlf-critical", parsed!!.id)
        assertEquals("Crlf Warning", parsed.title)
        assertEquals("# Crlf Warning\r\nline two", parsed.textMd)
        assertEquals("4ce4dfa2880c", parsed.hash)
    }

    @Test
    fun parseNote_both_flags_true_wins() {
        val raw1 = "---\ncritical: false\ncritical: true\n---\n# Note\nBody text"
        val parsed1 = CriticalNotes.parseNote("my-note", raw1)
        assertNotNull(parsed1)
        assertEquals("my-note", parsed1!!.id)

        val raw2 = "---\ncritical: true\ncritical: false\n---\n# Note\nBody text"
        val parsed2 = CriticalNotes.parseNote("my-note", raw2)
        assertNotNull(parsed2)
        assertEquals("my-note", parsed2!!.id)
    }

    @Test
    fun jsTrim_uses_exact_js_whitespace_set() {
        assertEquals("hello", CriticalNotes.jsTrim("\uFEFFhello\uFEFF"))
        assertEquals("hello", CriticalNotes.jsTrim("\u00A0hello\u00A0"))
        assertEquals("hello", CriticalNotes.jsTrim("\u2028hello\u2028"))
        assertEquals("\u0085hello\u0085", CriticalNotes.jsTrim("\u0085hello\u0085"))
        assertEquals("\u180Ehello\u180E", CriticalNotes.jsTrim("\u180Ehello\u180E"))

        val mixed = "\uFEFF\u00A0\u2028\u0085\u180Ehello\u0085\u180E\u2028\u00A0\uFEFF"
        assertEquals("\u0085\u180Ehello\u0085\u180E", CriticalNotes.jsTrim(mixed))
    }

    @Test
    fun noteIdsInLine_ordered_distinct_and_requires_link_end() {
        val line = "[Warning](https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes/foo-critical.md) " +
            "and [Repeat](/.vitepress/notes/foo-critical) " +
            "and [Bar](.vitepress/notes/bar-note.md) " +
            "and [Bad1](.vitepress/notes/baz-note.extra) " +
            "and [Bad2](.vitepress/notes/baz-note#anchor)"

        val ids = CriticalNotes.noteIdsInLine(line)
        assertEquals(listOf("foo-critical", "bar-note"), ids)
    }

    @Test
    fun noteLinks_uses_entry_line_and_catches_bold_link() {
        val markdown = """
            # Audio
            ## Streaming
            * [Foo](https://foo.example) - Foo
            * [Bar](https://bar.example) - Bar music / **[Danger](https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes/bar-note.md)**
        """.trimIndent()

        val entry = EntryRow(
            id = "bar_entry",
            sectionId = "audio",
            headingId = "audio/streaming",
            ord = 1,
            name = "Bar",
            url = "https://bar.example",
            originalUrl = null,
            description = "Bar music",
            tags = "",
            headingPath = "Audio › Streaming",
            tier = 0,
            line = 4,
            isInlineMore = false,
            isBase64 = false,
            mirrors = emptyList(),
            extraLinks = emptyList(),
        )

        val links = CriticalNotes.noteLinks("audio", markdown, listOf(entry))
        assertEquals(1, links.size)
        val link = links[0]
        assertEquals("bar_entry", link.entryId)
        assertEquals("bar-note", link.noteId)
        assertEquals("audio", link.sectionId)
        assertEquals(4, link.line)
        assertEquals(0, link.ord)
    }

    private fun entryRow(id: String, line: Int, url: String) = EntryRow(
        id = id, sectionId = "s", headingId = null, ord = 1, name = id, url = url, originalUrl = null,
        description = "", tags = "", headingPath = "", tier = 0, line = line, isInlineMore = false,
        isBase64 = false, mirrors = emptyList(), extraLinks = emptyList(),
    )

    @Test
    fun noteLinks_guard_only_entries_before_the_note_link() {
        val n = "https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes"
        val line = "* [A](https://a.example/) / [Warning]($n/a-critical.md), [B](https://b.example/), " +
            "[C](https://c.example/) - Desc"
        val entries = listOf(
            entryRow("ea", 1, "https://a.example/"),
            entryRow("eb", 1, "https://b.example/"),
            entryRow("ec", 1, "https://missing.example/"),
        )
        val rows = CriticalNotes.noteLinks("s", line, entries)
        assertEquals(listOf("ea", "ec"), rows.map { it.entryId })

        val late = "* [A](https://a.example/) or [B](https://b.example/) - Desc / [Warning]($n/a-critical.md)"
        val rows2 = CriticalNotes.noteLinks("s", late, entries.take(2))
        assertEquals(listOf("ea", "eb"), rows2.map { it.entryId })
    }

    @Test
    fun fixture_rows_equal_expected_json() {
        val fixtureDir = findFixtureDir()
        val expectedFile = File(fixtureDir, "expected.json")
        assertTrue("expected.json must exist", expectedFile.exists())

        val expectedJson = Json.parseToJsonElement(expectedFile.readText(Charsets.UTF_8)).jsonObject
        val crlfObj = expectedJson["crlf_note_file"]!!.jsonObject
        val crlfId = crlfObj["id"]!!.jsonPrimitive.content
        val crlfRaw = crlfObj["bytes_utf8"]!!.jsonPrimitive.content

        val notesDir = File(fixtureDir, "docs/.vitepress/notes")
        assertTrue("fixture notes dir must exist", notesDir.exists())

        val noteFiles = notesDir.listFiles()?.toList().orEmpty()
        val rows = mutableListOf<CriticalNoteRow>()

        for (file in noteFiles) {
            val noteId = file.name.removeSuffix(".md")
            val raw = String(file.readBytes(), Charsets.UTF_8)
            val parsed = CriticalNotes.parseNote(noteId, raw)
            if (parsed != null) {
                rows.add(parsed)
            }
        }

        val crlfParsed = CriticalNotes.parseNote(crlfId, crlfRaw)
        assertNotNull(crlfParsed)
        rows.add(crlfParsed!!)

        rows.sortBy { it.id }

        val expectedCriticalNotes = expectedJson["critical_notes"]!!.jsonArray
        assertEquals(expectedCriticalNotes.size, rows.size)

        for (i in rows.indices) {
            val exp = expectedCriticalNotes[i].jsonObject
            val act = rows[i]
            assertEquals(exp["id"]!!.jsonPrimitive.content, act.id)
            assertEquals(exp["title"]!!.jsonPrimitive.content, act.title)
            assertEquals(exp["text_md"]!!.jsonPrimitive.content, act.textMd)
            assertEquals(exp["hash"]!!.jsonPrimitive.content, act.hash)
        }

        val hashes = rows.map { it.hash }.toSet()
        assertTrue(hashes.contains("73ec7e5dfce0"))
        assertTrue(hashes.contains("0d09fd9ff28b"))
        assertTrue(hashes.contains("4ce4dfa2880c"))
    }
}
