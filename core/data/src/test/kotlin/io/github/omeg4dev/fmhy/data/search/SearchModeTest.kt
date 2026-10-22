package io.github.omeg4dev.fmhy.data.search

import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.DatasetStoreTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SearchModeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun insert(conn: androidx.sqlite.SQLiteConnection, num: Int, id: String, section: String, name: String, url: String, desc: String, path: String, tags: String = "") {
        conn.execSQL(
            "INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) " +
                "VALUES ($num, '$id', '$section', NULL, $num, '$name', '$url', NULL, '$desc', '$tags', '$path', 0, 0, 0, '[]', '[]', 1000, 1000, NULL);"
        )
    }

    private suspend fun store(name: String): DatasetStore {
        val store = DatasetStoreTest.createSampleDb(tempFolder.newFile(name))
        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('video', 'Movies / TV / Anime', '', 'video.md', 1);")
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('videopiracyguide', 'Video Piracy Guide', '', 'vpg.md', 2);")
            insert(conn, 1, "e_stremio", "video", "Stremio", "https://www.stremio.com/app", "Open source media center", "Video > Streaming")
            insert(conn, 2, "e_cdn", "video", "Foo Player", "https://zzzcdn.example/some/long/path/app", "A player", "Video > Players")
            insert(conn, 3, "e_sub", "video", "Opensub", "https://opensub.example", "Fetches files", "Video > Subtitles Hub")
            insert(conn, 4, "e_tag", "videopiracyguide", "Plain Thing", "https://plain.example", "Nothing here", "Guide", tags = "qwertytag|other")
            insert(conn, 5, "e_gh", "video", "Gh Tool", "https://github.com/x/y", "tool", "Video")
            insert(conn, 6, "e_gh2", "video", "Gh Two", "https://github.com/z", "tool", "Video")
        }
        return store
    }

    private fun SearchResults.ids() = groups.flatMap { it.hits }.map { it.entry.id }

    @Test
    fun typoFindsStremioOnlyInFuzzy() = runTest {
        val engine = SearchEngine(store("typo.db"))
        assertTrue("e_stremio" in engine.search("stremo", mode = MatchMode.FUZZY).ids())
        assertFalse("e_stremio" in engine.search("stremo", mode = MatchMode.EXACT).ids())
    }

    @Test
    fun prefixFindsStremioOnlyInFuzzy() = runTest {
        val engine = SearchEngine(store("prefix.db"))
        assertTrue("e_stremio" in engine.search("strem", mode = MatchMode.FUZZY).ids())
        assertFalse("e_stremio" in engine.search("strem", mode = MatchMode.EXACT).ids())
    }

    @Test
    fun exactWordFindsStremioInBoth() = runTest {
        val engine = SearchEngine(store("word.db"))
        assertTrue("e_stremio" in engine.search("stremio", mode = MatchMode.FUZZY).ids())
        assertTrue("e_stremio" in engine.search("stremio", mode = MatchMode.EXACT).ids())
        assertTrue("e_stremio" in engine.search("\"media center\"", mode = MatchMode.EXACT).ids())
    }

    @Test
    fun substringMatchesAreAddedAfterWordHitsInFuzzy() = runTest {
        val engine = SearchEngine(store("tri.db"))
        val ids = engine.search("remio").ids() // substring of Stremio, never a prefix
        assertEquals(listOf("e_stremio"), ids)
        assertTrue(engine.search("remio", mode = MatchMode.EXACT).ids().isEmpty())
        val hit = engine.search("remio").groups[0].hits[0]
        assertTrue(hit.nameHl.contains('\u0002'))
    }

    @Test
    fun hitsCarryRealHighlights() = runTest {
        val engine = SearchEngine(store("hl.db"))
        val hit = engine.search("stremio").groups.flatMap { it.hits }.first { it.entry.id == "e_stremio" }
        assertEquals("\u0002Stremio\u0003", hit.nameHl)
        assertFalse(hit.descriptionHl.contains('\u0002'))
        assertNull(hit.extraLabel)
    }

    @Test
    fun matchOnlyInHiddenColumnsGetsAnExtraLine() = runTest {
        val engine = SearchEngine(store("extra.db"))
        val link = engine.search("zzzcdn").groups.flatMap { it.hits }.single()
        assertEquals("link", link.extraLabel)
        assertTrue(link.extraHl!!.contains("\u0002zzzcdn\u0003"))
        assertFalse(link.extraHl!!.startsWith("https://"))

        val cat = engine.search("subtitles").groups.flatMap { it.hits }.single()
        assertEquals("category", cat.extraLabel)
        assertEquals("Video › \u0002Subtitles\u0003 Hub", cat.extraHl)

        val tag = engine.search("qwertytag").groups.flatMap { it.hits }.single()
        assertEquals("tags", tag.extraLabel)
    }

    @Test
    fun inFilterPrefersTheExactSectionId() = runTest {
        val engine = SearchEngine(store("in.db"))
        val onlyVideo = engine.search("in:video").groups.map { it.section.id }
        assertEquals(listOf("video"), onlyVideo)
        val both = engine.search("in:vid").groups.map { it.section.id }.toSet()
        assertEquals(setOf("video", "videopiracyguide"), both)
    }

    @Test
    fun hostSuggestions() = runTest {
        val engine = SearchEngine(store("hosts.db"))
        assertEquals("github.com", engine.suggestHosts("").first()) // most used host first
        assertEquals(listOf("stremio.com"), engine.suggestHosts("strem"))
        assertEquals(listOf("zzzcdn.example"), engine.suggestHosts("zzz"))
        assertTrue(engine.suggestHosts("git", limit = 8).contains("github.com"))
    }

    @Test
    fun categoriesAreComputedUpToFifty() = runTest {
        val engine = SearchEngine(store("cat.db"))
        val res = engine.search("video")
        assertNotNull(res.categories)
    }
}
