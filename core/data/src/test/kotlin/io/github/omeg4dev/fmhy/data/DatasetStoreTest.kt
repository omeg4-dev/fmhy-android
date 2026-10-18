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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatasetStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    companion object {
        fun loadSchemaSql(): String {
            val candidates = listOf(
                File("../../schema/dataset.sql"),
                File("schema/dataset.sql"),
                File("../schema/dataset.sql"),
            )
            val f = candidates.firstOrNull { it.exists() }
                ?: error("Cannot find dataset.sql in $candidates, cwd=${File(".").absolutePath}")
            return f.readText()
        }

        fun executeSqlScript(conn: SQLiteConnection, sql: String) {
            val statements = mutableListOf<String>()
            val current = StringBuilder()
            var inTrigger = false

            for (line in sql.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("--") || trimmed.isEmpty()) continue

                current.appendLine(line)
                val upper = trimmed.uppercase()
                if (upper.contains("BEGIN")) {
                    inTrigger = true
                }

                if (trimmed.endsWith(";")) {
                    if (inTrigger) {
                        if (upper == "END;" || upper.endsWith(" END;")) {
                            inTrigger = false
                            statements.add(current.toString().trim())
                            current.clear()
                        }
                    } else {
                        statements.add(current.toString().trim())
                        current.clear()
                    }
                }
            }
            if (current.isNotBlank()) {
                statements.add(current.toString().trim())
            }

            for (stmt in statements) {
                if (stmt.isNotBlank()) {
                    conn.execSQL(stmt)
                }
            }
        }

        fun createSampleDb(dbFile: File, version: Long = 1L): DatasetStore {
            val conn = BundledSQLiteDriver().open(dbFile.absolutePath)
            executeSqlScript(conn, loadSchemaSql())
            conn.execSQL("INSERT INTO meta (key, value) VALUES ('schema', '1'), ('version', '$version');")
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
}
