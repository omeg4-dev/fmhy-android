package io.github.omeg4dev.fmhy.data.search

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.DatasetStoreTest
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SearchEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testQueryParserOperators() {
        // Plain words
        val q1 = QueryParser.parse("hello world")
        assertEquals(listOf("hello", "world"), q1.words)

        // Phrases
        val q2 = QueryParser.parse("\"free movies\" test")
        assertEquals(listOf("free movies"), q2.phrases)
        assertEquals(listOf("test"), q2.words)

        // Excludes
        val q3 = QueryParser.parse("movie -android -\"bad quality\"")
        assertEquals(listOf("movie"), q3.words)
        assertEquals(listOf("android", "bad quality"), q3.excludes)

        // in:<section> repeatable
        val q4 = QueryParser.parse("search in:video in:audio in:\"reading books\"")
        assertEquals(listOf("search"), q4.words)
        assertEquals(listOf("video", "audio", "reading books"), q4.inSections)

        // is:starred, is:top, is:b64
        val q5 = QueryParser.parse("item is:starred is:top is:b64")
        assertEquals(listOf("item"), q5.words)
        assertTrue(q5.isStarred)
        assertTrue(q5.isTop)
        assertTrue(q5.isB64)

        // site:<host>
        val q6 = QueryParser.parse("docs site:github.com site:\"www.fmhy.net\"")
        assertEquals(listOf("docs"), q6.words)
        assertEquals("fmhy.net", q6.site)

        // Injection input foo" OR " doesn't throw
        val q7 = QueryParser.parse("foo\" OR \"")
        assertNotNull(q7)
    }

    @Test
    fun testSearchEngineRankingAndFilters() = runTest {
        val dbFile = tempFolder.newFile("search_test.db")
        val store = DatasetStoreTest.createSampleDb(dbFile)

        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('video', 'Movies / TV / Anime', 'Watch online', 'video.md', 1);")
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('tools', 'Tools / Utilities', 'Helpful software', 'tools.md', 2);")

            // Three specific entries for the ranking test:
            // "Stremio" (normal tier, exact name match)
            // "Stremio Addons" (normal tier, prefix name match)
            // "Movie Web" (tier 2 top pick, description mentions stremio)
            conn.execSQL("""
                INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at)
                VALUES (1, 'e_stremio', 'video', NULL, 1, 'Stremio', 'https://stremio.com', NULL, 'Open source media center', '', 'Video', 0, 0, 0, '[]', '[]', 1000, 1000, NULL);
            """.trimIndent())

            conn.execSQL("""
                INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at)
                VALUES (2, 'e_stremio_addons', 'video', NULL, 2, 'Stremio Addons', 'https://addons.stremio.com', NULL, 'Community add-ons for streaming', '', 'Video', 0, 0, 0, '[]', '[]', 1000, 1000, NULL);
            """.trimIndent())

            conn.execSQL("""
                INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at)
                VALUES (3, 'e_movieweb', 'video', NULL, 3, 'Movie Web', 'https://movieweb.app', NULL, 'Web streaming alternative to stremio', '', 'Video', 2, 0, 0, '[]', '[]', 1000, 1000, 1000);
            """.trimIndent())

            // Base64 entry
            conn.execSQL("""
                INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at)
                VALUES (4, 'e_b64', 'tools', NULL, 1, 'Secret Decoder', 'https://decoded.com', 'https://rentry.co/FMHYB64#abc', 'Base64 link', '', 'Tools', 0, 0, 1, '[]', '[]', 1000, 1000, NULL);
            """.trimIndent())
        }

        val searchEngine = SearchEngine(store)

        // 1. Query "stremio" ranks "Stremio" first!
        val resultsStremio = searchEngine.search("stremio")
        println("resultsStremio total=${resultsStremio.total}")
        for (g in resultsStremio.groups) {
            println("Group ${g.section.id}:")
            for (h in g.hits) {
                println("  ${h.entry.id}: ${h.entry.name}")
            }
        }
        assertTrue(resultsStremio.total >= 3)
        val allHits = resultsStremio.groups.flatMap { it.hits }
        assertEquals("e_stremio", allHits[0].entry.id)
        assertEquals("Stremio", allHits[0].entry.name)

        // 2. in:video filter
        val resultsInVideo = searchEngine.search("stremio in:video")
        assertTrue(resultsInVideo.groups.all { it.section.id == "video" })

        // 3. is:b64 filter
        val resultsB64 = searchEngine.search("is:b64")
        assertEquals(1, resultsB64.total)
        assertEquals("e_b64", resultsB64.groups[0].hits[0].entry.id)

        // 4. Trigram fallback finds "Stremio" for "remi"
        val resultsTrigram = searchEngine.search("remi")
        assertTrue(resultsTrigram.total > 0)
        val trigramHitIds = resultsTrigram.groups.flatMap { it.hits }.map { it.entry.id }
        assertTrue("Trigram fallback should find Stremio for 'remi'", trigramHitIds.contains("e_stremio"))

        // 5. Injection input foo" OR " doesn't throw
        val resultsInjection = searchEngine.search("foo\" OR \"")
        assertNotNull(resultsInjection)

        store.close()
    }

    @Test
    fun testSearchPerformance30kRows() = runTest {
        val dbFile = tempFolder.newFile("perf_30k.db")
        val conn = BundledSQLiteDriver().open(dbFile.absolutePath)
        DatasetStoreTest.executeSqlScript(conn, DatasetStoreTest.loadSchemaSql())
        conn.execSQL("INSERT INTO meta (key, value) VALUES ('schema', '1'), ('version', '1');")
        conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('sec1', 'Main Section', 'Desc', 'sec1.md', 1);")

        conn.execSQL("BEGIN TRANSACTION;")
        val insertEntrySql = """
            INSERT INTO entries (
                num, id, section_id, heading_id, ord, name, url, original_url,
                description, tags, heading_path, tier, is_inline_more, is_base64,
                mirrors, extra_links, first_seen, last_changed, starred_at
            ) VALUES (?, ?, 'sec1', NULL, ?, ?, ?, NULL, ?, '', 'Main Section', ?, 0, 0, '[]', '[]', 1000, 1000, NULL);
        """.trimIndent()

        val sampleWords = listOf("stream", "video", "download", "torrent", "music", "books", "anime", "manga", "games", "movies")
        conn.prepare(insertEntrySql).use { stmt ->
            for (i in 1..30_000) {
                val w1 = sampleWords[i % sampleWords.size]
                val w2 = sampleWords[(i / 10) % sampleWords.size]
                val name = "Site $w1 $w2 $i"
                val url = "https://site$i.org"
                val desc = "Fast free $w1 service with $w2 support and high quality links"
                val tier = if (i % 50 == 0) 2 else if (i % 10 == 0) 1 else 0

                stmt.bindLong(1, i.toLong())
                stmt.bindText(2, "entry_$i")
                stmt.bindLong(3, i.toLong())
                stmt.bindText(4, name)
                stmt.bindText(5, url)
                stmt.bindText(6, desc)
                stmt.bindLong(7, tier.toLong())
                stmt.step()
                stmt.reset()
            }
        }
        conn.execSQL("COMMIT;")
        conn.close()

        val store = DatasetStore(dbFile)
        val searchEngine = SearchEngine(store)

        // Warm up
        searchEngine.search("stream download")

        // Measure typical search
        val startNano = System.nanoTime()
        val results = searchEngine.search("torrent anime")
        val elapsedMs = (System.nanoTime() - startNano) / 1_000_000

        println("Search on 30k rows took: ${elapsedMs}ms, hits: ${results.total}")
        assertTrue(results.total > 0)
        assertTrue("Search should take < 50 ms (was ${elapsedMs} ms)", elapsedMs < 50)

        store.close()
    }
}
