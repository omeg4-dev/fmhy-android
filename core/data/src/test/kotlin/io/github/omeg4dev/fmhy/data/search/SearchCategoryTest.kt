package io.github.omeg4dev.fmhy.data.search

import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.DatasetStoreTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SearchCategoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private suspend fun store(name: String): DatasetStore {
        val store = DatasetStoreTest.createSampleDb(tempFolder.newFile(name))
        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('gaming', 'Gaming', '', 'gaming.md', 1);")
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('privacy', 'Privacy Tools', '', 'privacy.md', 2);")
            conn.execSQL("INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES ('gaming/emulators', 'gaming', NULL, 1, 'Emulators', 'emulators', 1);")
            conn.execSQL("INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES ('gaming/retro', 'gaming', 'gaming/emulators', 2, 'Retro Emulator Cores', 'retro', 2);")
            conn.execSQL("INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES ('gaming/cafe', 'gaming', NULL, 1, 'Café Games', 'cafe', 3);")
            conn.execSQL("INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES ('gaming/blank', 'gaming', NULL, 1, '  ', 'blank', 4);")
            conn.execSQL("INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES ('privacy/adblocking', 'privacy', NULL, 1, 'Adblocking', 'adblocking', 1);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (1, 'e1', 'privacy', 'privacy/adblocking', 1, 'uBlock Origin', 'https://ublockorigin.com', NULL, 'Adblocker', '', 'Privacy', 0, 0, 0, '[]', '[]', 1000, 1000, NULL);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (2, 'e2', 'gaming', 'gaming/emulators', 2, 'Emulator Zone', 'https://emuzone.example', NULL, 'Spiel', '', 'Gaming', 0, 0, 0, '[]', '[]', 1000, 1000, NULL);")
        }
        return store
    }

    @Test
    fun emulatFindsEmulatorsHeading() = runTest {
        val engine = SearchEngine(store("a.db"))
        val hits = engine.searchCategories(QueryParser.parse("emulat"))
        println("searchCategories(emulat) = " + hits.map { "${it.heading?.title ?: it.section.title} [${it.path.joinToString(" > ")}]" })
        assertEquals("Emulators", hits[0].heading?.title)
        assertEquals(listOf("Gaming"), hits[0].path)
        assertEquals(listOf("Gaming", "Emulators"), hits[1].path)
    }

    @Test
    fun exactBeatsPrefix() = runTest {
        val engine = SearchEngine(store("b.db"))
        val hits = engine.searchCategories(QueryParser.parse("gaming"))
        assertNull(hits[0].heading)
        assertEquals("Gaming", hits[0].section.title)
        val exact = engine.searchCategories(QueryParser.parse("emulators"))
        assertEquals("Emulators", exact[0].heading?.title)
    }

    @Test
    fun diacriticsAndCase() = runTest {
        val engine = SearchEngine(store("c.db"))
        assertEquals("Café Games", engine.searchCategories(QueryParser.parse("CAFE"))[0].heading?.title)
        assertEquals("Café Games", engine.searchCategories(QueryParser.parse("café"))[0].heading?.title)
    }

    @Test
    fun operatorsIgnoredAndOnlyOperatorsEmpty() = runTest {
        val engine = SearchEngine(store("d.db"))
        val hits = engine.searchCategories(QueryParser.parse("emulat in:gaming is:starred"))
        assertEquals("Emulators", hits[0].heading?.title)
        assertTrue(engine.searchCategories(QueryParser.parse("is:starred")).isEmpty())
        assertTrue(engine.searchCategories(QueryParser.parse("in:gaming")).isEmpty())
    }

    @Test
    fun blankTitlesSkippedAndSearchFillsCategories() = runTest {
        val engine = SearchEngine(store("e.db"))
        assertTrue(engine.searchCategories(QueryParser.parse("blank")).isEmpty())
        val res = engine.search("emulat")
        assertTrue(res.categories.isNotEmpty())
        assertTrue(engine.search("is:starred").categories.isEmpty())
    }

    @Test
    fun didYouMean() = runTest {
        val engine = SearchEngine(store("f.db"))
        val u = engine.suggest(QueryParser.parse("ublok"))
        val e = engine.suggest(QueryParser.parse("emulatr"))
        println("suggest(ublok) = $u, suggest(emulatr) = $e")
        assertEquals("ublock", u)
        assertEquals("emulator", e)
        assertNull(engine.suggest(QueryParser.parse("xqzv")))
        assertEquals("ublock in:adblock", engine.suggest(QueryParser.parse("ublok in:adblock")))
        assertNull(engine.suggest(QueryParser.parse("ublock")))
        assertNull(engine.suggest(QueryParser.parse("is:starred")))
    }

    @Test
    fun trigramFallbackHonoursExcludes() = runTest {
        val store = store("g.db")
        store.withConnection { conn ->
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (3, 'e3', 'gaming', NULL, 3, 'Zonetool', 'https://zonetool.example', NULL, 'plain helper', '', 'Gaming', 0, 0, 0, '[]', '[]', 1000, 1000, NULL);")
        }
        val engine = SearchEngine(store)
        // "mulat" has no word-prefix hit, so only the trigram fallback can find "Emulator Zone".
        val plain = engine.search("mulat").groups.flatMap { it.hits }.map { it.entry.id }
        assertTrue("fallback should find e2", "e2" in plain)
        val excluded = engine.search("mulat -spiel").groups.flatMap { it.hits }.map { it.entry.id }
        assertTrue("excluded word in description must hide e2", "e2" !in excluded)
        val excludedUrl = engine.search("mulat -emuzone").groups.flatMap { it.hits }.map { it.entry.id }
        assertTrue("excluded word in url must hide e2", "e2" !in excludedUrl)
    }
}
