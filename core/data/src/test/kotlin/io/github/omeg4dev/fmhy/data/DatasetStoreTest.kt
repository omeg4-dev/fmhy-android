package io.github.omeg4dev.fmhy.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.data.model.Tier
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatasetStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    companion object {
        fun loadSchemaSql(): String = DatasetStore.loadSchemaSql()

        fun executeSqlScript(conn: SQLiteConnection, sql: String) = DatasetStore.executeSqlScript(conn, sql)

        fun createSampleDb(dbFile: File, version: Long = 1L): DatasetStore {
            val conn = BundledSQLiteDriver().open(dbFile.absolutePath)
            executeSqlScript(conn, loadSchemaSql())
            conn.execSQL("INSERT INTO meta (key, value) VALUES ('schema', '$DATASET_SCHEMA'), ('version', '$version');")
            conn.close()
            return DatasetStore(dbFile)
        }
    }

    @Test
    fun testSectionItemsMergeAndInlineMoreGrouping() = runTest {
        val dbFile = tempFolder.newFile("sample.db")
        val store = createSampleDb(dbFile)

        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('video', 'Video', '', 'video.md', 1);")
            conn.execSQL("INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES ('video/movies', 'video', NULL, 1, 'Movies', 'movies', 10);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (1, 'e1', 'video', 'video/movies', 11, 'Entry 1', 'https://e1.com', NULL, 'Normal entry', '', 'Video > Movies', 0, 0, 0, '[]', '[]', 1000, 1000, NULL);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (2, 'm1', 'video', 'video/movies', 12, 'More 1', 'https://m1.com', NULL, 'More 1', '', 'Video > Movies', 0, 1, 0, '[]', '[]', 1000, 1000, NULL);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (3, 'm2', 'video', 'video/movies', 13, 'More 2', 'https://m2.com', NULL, 'More 2', '', 'Video > Movies', 0, 1, 0, '[]', '[]', 1000, 1000, NULL);")
            conn.execSQL("INSERT INTO notes (id, section_id, heading_id, ord, text_md) VALUES ('n1', 'video', 'video/movies', 14, '**Note** test note');")
            conn.execSQL("INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES ('video/tv', 'video', NULL, 1, 'TV', 'tv', 20);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (4, 'm3', 'video', 'video/tv', 21, 'More 3', 'https://m3.com', NULL, 'More 3 under TV', '', 'Video > TV', 0, 1, 0, '[]', '[]', 1000, 1000, NULL);")
        }

        val items = store.sectionItems("video")
        // Expected:
        // 0: Heading(Movies)
        // 1: Entry(Entry 1)
        // 2: MoreGroup([m1, m2])
        // 3: Note(n1)
        // 4: Heading(TV)
        // 5: MoreGroup([m3])
        assertEquals(6, items.size)
        assertTrue(items[0] is SectionItem.HeadingItem)
        assertEquals("video/movies", (items[0] as SectionItem.HeadingItem).heading.id)

        assertTrue(items[1] is SectionItem.EntryItem)
        assertEquals("e1", (items[1] as SectionItem.EntryItem).entry.id)

        assertTrue(items[2] is SectionItem.MoreGroup)
        val more1 = (items[2] as SectionItem.MoreGroup).entries
        assertEquals(2, more1.size)
        assertEquals("m1", more1[0].id)
        assertEquals("m2", more1[1].id)

        assertTrue(items[3] is SectionItem.NoteItem)
        assertEquals("n1", (items[3] as SectionItem.NoteItem).note.id)

        assertTrue(items[4] is SectionItem.HeadingItem)
        assertEquals("video/tv", (items[4] as SectionItem.HeadingItem).heading.id)

        assertTrue(items[5] is SectionItem.MoreGroup)
        val more2 = (items[5] as SectionItem.MoreGroup).entries
        assertEquals(1, more2.size)
        assertEquals("m3", more2[0].id)

        store.close()
    }

    @Test
    fun testUnsafeReason() = runTest {
        val dbFile = tempFolder.newFile("sample_unsafe.db")
        val store = createSampleDb(dbFile)

        store.withConnection { conn ->
            conn.execSQL("INSERT INTO unsafe_domains (domain, reason) VALUES ('example.com', 'Malware site');")
            conn.execSQL("INSERT INTO unsafe_domains (domain, reason) VALUES ('phishing.org', 'Phishing credential harvester');")
        }

        // Exact domain
        assertEquals("Malware site", store.unsafeReason("https://example.com/download"))
        // Subdomain
        assertEquals("Malware site", store.unsafeReason("https://sub.example.com/login"))
        assertEquals("Malware site", store.unsafeReason("https://deep.sub.example.com/"))
        // www. prefix
        assertEquals("Malware site", store.unsafeReason("https://www.example.com/test"))
        assertEquals("Malware site", store.unsafeReason("http://www.sub.example.com/test"))
        // Look-alike evilexample.com vs example.com (MUST be safe -> null)
        assertNull(store.unsafeReason("https://evilexample.com/test"))
        assertNull(store.unsafeReason("https://notexample.com/login"))
        assertNull(store.unsafeReason("https://example.com.evil.org/"))

        // Unrelated safe site
        assertNull(store.unsafeReason("https://google.com"))

        store.close()
    }

    @Test
    fun testSectionsAndHeadingsAndEntryQueries() = runTest {
        val dbFile = tempFolder.newFile("sample_queries.db")
        val store = createSampleDb(dbFile)

        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('tools', 'Tools', 'Useful tools', 'tools.md', 1);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (1, 't1', 'tools', NULL, 1, 'Top Tool', 'https://top.tool', NULL, 'Desc', '', 'Tools', 2, 0, 0, '[]', '[]', 1000, 1000, 1000);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (2, 't2', 'tools', NULL, 2, 'Normal Tool', 'https://norm.tool', NULL, 'Desc', '', 'Tools', 0, 0, 0, '[]', '[]', 1100, 1100, NULL);")
            conn.execSQL("INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (1, 1000, 'added', 't1', 'tools', 'Top Tool', 'https://top.tool', '', 'abc');")
            conn.execSQL("INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (2, 1100, 'added', 't2', 'tools', 'Normal Tool', 'https://norm.tool', '', 'abc');")
        }

        val sections = store.sections()
        assertEquals(1, sections.size)
        assertEquals("tools", sections[0].id)
        assertEquals(2, sections[0].entryCount)

        val topPick = store.randomTopPick()
        assertNotNull(topPick)
        assertEquals("t1", topPick?.id)
        assertEquals(Tier.TOP, topPick?.tier)

        val e1 = store.entry("t1")
        assertNotNull(e1)
        assertEquals("Top Tool", e1?.name)

        val entries = store.entries(listOf("t1", "t2", "nonexistent"))
        assertEquals(2, entries.size)

        val countAdded = store.countAddedSince(1050)
        assertEquals(1, countAdded)

        val allChanges = store.changes()
        assertEquals(2, allChanges.size)

        store.close()
    }
    // The parser numbers headings and entries/notes with separate counters (FmhyParser:
    // headingOrd vs ordCounter), so items must be placed under their heading, not merged by ord.
    @Test
    fun testSectionItemsPlaceItemsUnderTheirHeadingWithSeparateOrdCounters() = runTest {
        val dbFile = tempFolder.newFile("separate-ords.db")
        val store = createSampleDb(dbFile)

        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('privacy', 'Privacy', '', 'privacy.md', 1);")
            conn.execSQL("INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES ('privacy/adblocking', 'privacy', NULL, 1, 'Adblocking', 'adblocking', 0);")
            conn.execSQL("INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES ('privacy/filters', 'privacy', 'privacy/adblocking', 2, 'Filters', 'filters', 1);")
            conn.execSQL("INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES ('privacy/antivirus', 'privacy', NULL, 1, 'Antivirus', 'antivirus', 2);")
            conn.execSQL("INSERT INTO notes (id, section_id, heading_id, ord, text_md) VALUES ('n0', 'privacy', 'privacy/adblocking', 0, 'note');")
            listOf(
                Triple(1, "ublock", "privacy/adblocking"),
                Triple(2, "adguard", "privacy/adblocking"),
                Triple(3, "easylist", "privacy/filters"),
                Triple(4, "malwarebytes", "privacy/antivirus"),
                Triple(5, "virustotal", "privacy/antivirus"),
            ).forEach { (ord, id, heading) ->
                conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES ($ord, '$id', 'privacy', '$heading', $ord, '$id', 'https://$id.example', NULL, '', '', '', 0, 0, 0, '[]', '[]', 1000, 1000, NULL);")
            }
        }

        val order = store.sectionItems("privacy").map {
            when (it) {
                is SectionItem.HeadingItem -> "H:" + it.heading.id.substringAfter('/')
                is SectionItem.EntryItem -> it.entry.id
                is SectionItem.NoteItem -> "N:" + it.note.id
                is SectionItem.MoreGroup -> "M:" + it.entries.joinToString(",") { e -> e.id }
            }
        }
        assertEquals(
            listOf("H:adblocking", "N:n0", "ublock", "adguard", "H:filters", "easylist",
                "H:antivirus", "malwarebytes", "virustotal"),
            order,
        )
        store.close()
    }

    @Test
    fun testBundledSnapshotEveryItemFollowsItsOwnHeading() = runTest {
        val snapshot = File("../../app/src/main/assets/dataset/snapshot.gzbin")
        assumeTrue("bundled snapshot not built", snapshot.exists())
        val dbFile = tempFolder.newFile("snapshot.db")
        java.util.zip.GZIPInputStream(snapshot.inputStream()).use { input ->
            dbFile.outputStream().use { input.copyTo(it) }
        }
        val store = DatasetStore(dbFile)
        var checked = 0
        for (section in store.sections()) {
            var current: String? = null
            for (item in store.sectionItems(section.id)) {
                val headingId = when (item) {
                    is SectionItem.HeadingItem -> { current = item.heading.id; continue }
                    is SectionItem.EntryItem -> item.entry.headingId
                    is SectionItem.NoteItem -> item.note.headingId
                    is SectionItem.MoreGroup -> item.entries.first().headingId
                }
                if (headingId != null) {
                    assertEquals("${section.id}: item under the wrong heading", headingId, current)
                }
                checked++
            }
        }
        assertTrue("checked only $checked items", checked > 15_000)
        store.close()
    }

    @Test
    fun testSchemaCurrentCheck() = runTest {
        val dbCurrent = tempFolder.newFile("current_schema.db")
        val storeCurrent = createSampleDb(dbCurrent)
        assertTrue(storeCurrent.isSchemaCurrent())
        assertEquals(DATASET_SCHEMA, storeCurrent.schemaVersion())
        storeCurrent.close()

        val dbOld = tempFolder.newFile("old_schema.db")
        val conn = BundledSQLiteDriver().open(dbOld.absolutePath)
        executeSqlScript(conn, loadSchemaSql())
        conn.execSQL("INSERT INTO meta (key, value) VALUES ('schema', '1'), ('version', '1');")
        conn.close()

        val storeOld = DatasetStore(dbOld)
        assertEquals(false, storeOld.isSchemaCurrent())
        assertEquals(1, storeOld.schemaVersion())
        storeOld.close()

        val nonExistent = DatasetStore(File(tempFolder.root, "does_not_exist.db"))
        assertEquals(false, nonExistent.isSchemaCurrent())
        assertNull(nonExistent.schemaVersion())
    }

    @Test
    fun testRandomTopPickDrawsFromTier1And2() = runTest {
        val dbFile = tempFolder.newFile("sample_pick.db")
        val store = createSampleDb(dbFile)

        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('s1', 'S1', '', 's1.md', 1);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, kind, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (1, 'star1', 's1', NULL, 1, 'Star 1', 'https://star1.com', NULL, '', '', '', 1, 0, 0, 0, '[]', '[]', 1000, 1000, 1000);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, kind, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (2, 'norm1', 's1', NULL, 2, 'Norm 1', 'https://norm1.com', NULL, '', '', '', 0, 0, 0, 0, '[]', '[]', 1000, 1000, NULL);")
        }

        val pick = store.randomTopPick()
        assertNotNull(pick)
        assertEquals("star1", pick?.id)
        assertEquals(Tier.STAR, pick?.tier)
        store.close()
    }

    @Test
    fun testRandomTopPickIsASafeStar() = runTest {
        val dbFile = tempFolder.newFile("sample_safe_pick.db")
        val store = createSampleDb(dbFile)
        val cols = "num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, kind, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at"

        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('s1', 'S1', '', 's1.md', 1);")
            conn.execSQL("INSERT INTO unsafe_domains (domain, reason) VALUES ('bad.example', 'malware');")
            // starred but: unsafe host, an index, a section link, a base64 link — none may be picked
            conn.execSQL("INSERT INTO entries ($cols) VALUES (1, 'unsafe', 's1', NULL, 1, 'U', 'https://www.bad.example/x', NULL, '', '', '', 1, 0, 0, 0, '[]', '[]', 1, 1, 1);")
            conn.execSQL("INSERT INTO entries ($cols) VALUES (2, 'index', 's1', NULL, 2, 'I', 'https://i.example', NULL, '', '', '', 1, 1, 0, 0, '[]', '[]', 1, 1, 1);")
            conn.execSQL("INSERT INTO entries ($cols) VALUES (3, 'xlink', 's1', NULL, 3, 'X', 'https://fmhy.net/ai', NULL, '', '', '', 1, 2, 0, 0, '[]', '[]', 1, 1, 1);")
            conn.execSQL("INSERT INTO entries ($cols) VALUES (4, 'b64', 's1', NULL, 4, 'B', 'https://pastebin.com/abcd', 'https://rentry.co/FMHYB64#b', '', '', '', 1, 0, 0, 1, '[]', '[]', 1, 1, 1);")
            conn.execSQL("INSERT INTO entries ($cols) VALUES (5, 'good', 's1', NULL, 5, 'G', 'https://good.example', NULL, '', '', '', 1, 0, 0, 0, '[]', '[]', 1, 1, 1);")
        }

        repeat(10) { assertEquals("good", store.randomTopPick()?.id) }
        store.close()
    }

    @Test
    fun testSectionOrderingWebsiteAndAlphabetical() = runTest {
        val dbFile = tempFolder.newFile("sample_order.db")
        val store = createSampleDb(dbFile)

        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('video', 'Video', '', 'video.md', 1);")
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('ai', 'Artificial Intelligence', '', 'ai.md', 2);")
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('privacy', 'Privacy', '', 'privacy.md', 3);")
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('zzz', 'Zzz', '', 'zzz.md', 4);")
        }

        val websiteOrder = store.sections(SectionOrder.WEBSITE).map { it.id }
        assertEquals(listOf("privacy", "ai", "video", "zzz"), websiteOrder)

        val alphaOrder = store.sections(SectionOrder.ALPHABETICAL).map { it.id }
        assertEquals(listOf("ai", "privacy", "video", "zzz"), alphaOrder)

        store.close()
    }

    @Test
    fun testSectionItemsLineGrouping() = runTest {
        val dbFile = tempFolder.newFile("sample_line_grouping.db")
        val store = createSampleDb(dbFile)

        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('adblock', 'Adblocking', '', 'adblock.md', 1);")
            conn.execSQL("INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES ('adblock/tools', 'adblock', NULL, 1, 'Tools', 'tools', 1);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, kind, line, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (1, 'e1', 'adblock', 'adblock/tools', 1, 'uBlock Origin', 'https://ubo.org', NULL, '', '', '', 1, 0, 2, 0, 0, '[]', '[]', 1000, 1000, 1000);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, kind, line, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (2, 'e2', 'adblock', 'adblock/tools', 2, 'AdGuard', 'https://adguard.com', NULL, '', '', '', 1, 0, 2, 0, 0, '[]', '[]', 1000, 1000, 1000);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, kind, line, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (3, 'e3', 'adblock', 'adblock/tools', 3, 'uBO Lite', 'https://ubol.org', NULL, '', '', '', 1, 0, 2, 0, 0, '[]', '[]', 1000, 1000, 1000);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, kind, line, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (4, 'e4', 'adblock', 'adblock/tools', 4, 'Report Issues', 'https://uassets.org', NULL, '', '', '', 1, 0, 3, 0, 0, '[]', '[{\"label\":\"uAssets\",\"url\":\"https://uassets.org\"}]', 1000, 1000, 1000);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, kind, line, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES (5, 'e5', 'adblock', 'adblock/tools', 5, 'SponsorBlock', 'https://sponsor.org', NULL, '', '', '', 1, 0, 4, 0, 0, '[]', '[]', 1000, 1000, 1000);")
        }

        val items = store.sectionItems("adblock")
        assertEquals(4, items.size)
        assertTrue(items[0] is SectionItem.HeadingItem)

        assertTrue(items[1] is SectionItem.EntryItem)
        val uBlockItem = items[1] as SectionItem.EntryItem
        assertEquals("e1", uBlockItem.entry.id)
        assertEquals("uBlock Origin", uBlockItem.entry.name)
        assertEquals(Tier.STAR, uBlockItem.entry.tier)
        assertEquals(2, uBlockItem.alternatives.size)
        assertEquals("e2", uBlockItem.alternatives[0].id)
        assertEquals("AdGuard", uBlockItem.alternatives[0].name)
        assertEquals(Tier.STAR, uBlockItem.alternatives[0].tier)
        assertEquals("e3", uBlockItem.alternatives[1].id)
        assertEquals("uBO Lite", uBlockItem.alternatives[1].name)
        assertEquals(Tier.STAR, uBlockItem.alternatives[1].tier)

        assertTrue(items[2] is SectionItem.EntryItem)
        val reportItem = items[2] as SectionItem.EntryItem
        assertEquals("e4", reportItem.entry.id)
        assertEquals("Report Issues", reportItem.entry.name)
        assertTrue(reportItem.alternatives.isEmpty())

        assertTrue(items[3] is SectionItem.EntryItem)
        val sponsorItem = items[3] as SectionItem.EntryItem
        assertEquals("e5", sponsorItem.entry.id)
        assertEquals("SponsorBlock", sponsorItem.entry.name)
        assertTrue(sponsorItem.alternatives.isEmpty())

        store.close()
    }

    @Test
    fun testInitDatabaseAppearsOnlyWhenComplete() = runTest {
        val dbFile = File(tempFolder.root, "fresh/fmhy.db")
        DatasetStore.initDatabase(dbFile)

        assertTrue(dbFile.exists())
        assertTrue(!File(dbFile.parentFile, "fmhy.db.init").exists())
        val store = DatasetStore(dbFile)
        val tables = store.withConnection { conn ->
            conn.prepare("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name IN ('meta', 'sections', 'entries')")
                .use { it.step(); it.getLong(0) }
        }
        assertEquals(3L, tables)
        store.close()
    }
}
