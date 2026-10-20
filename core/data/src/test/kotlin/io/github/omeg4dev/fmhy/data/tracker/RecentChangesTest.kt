package io.github.omeg4dev.fmhy.data.tracker

import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.DatasetStoreTest
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecentChangesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private suspend fun populateTestData(store: DatasetStore) {
        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('video', 'Video', '', 'video.md', 1), ('audio', 'Audio', '', 'audio.md', 2);")
            conn.execSQL("INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES ('video/root', 'video', NULL, 1, 'Root', 'root', 10), ('video/sub', 'video', 'video/root', 2, 'Sub', 'sub', 20), ('audio/root', 'audio', NULL, 1, 'AudioRoot', 'audioroot', 30);")
            conn.execSQL("""
                INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, kind, line, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) VALUES
                (1, 'e-root', 'video', 'video/root', 11, 'Entry Root', 'https://root.com', NULL, '', '', '', 0, 0, 0, 0, 0, '[]', '[]', 500, 500, NULL),
                (2, 'e-sub', 'video', 'video/sub', 21, 'Entry Sub', 'https://sub.com', NULL, '', '', '', 1, 0, 0, 0, 0, '[{"label":"m1","url":"https://m1.com"}]', '[{"label":"gh","url":"https://gh.com"}]', 600, 600, NULL),
                (3, 'e-audio', 'audio', 'audio/root', 31, 'Entry Audio', 'https://audio.com', NULL, '', '', '', 0, 0, 0, 0, 0, '[]', '[]', 700, 700, NULL),
                (4, 'e-index', 'video', 'video/root', 12, 'Index Entry', 'https://index.com', NULL, '', '', '', 0, 1, 0, 0, 0, '[]', '[]', 800, 800, NULL);
            """.trimIndent())
        }
    }

    @Test
    fun testDedupeAcrossOwnSyncAndTrackerRows() = runTest {
        val dbFile = tempFolder.newFile("test_dedupe.db")
        val store = DatasetStoreTest.createSampleDb(dbFile)
        populateTestData(store)

        // Insert own-sync row (ts = 2000) and tracker row (ts = 1500) for same normalized URL
        store.withConnection { conn ->
            conn.execSQL("INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (1, 2000, 'added', 'e-root', 'video', 'Entry Root', 'https://root.com/', '', '')")
            conn.execSQL("INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (2, 1500, 'added', 'e-root', 'video', 'Entry Root', 'https://www.root.com', '', 'commit123')")
        }

        val changes = store.recentChanges(sinceTs = 1000, kinds = setOf(ChangeKind.ADDED))
        assertEquals(1, changes.size)
        // Earliest ts (1500) must be preserved
        assertEquals(1500L, changes[0].ts)
        assertEquals("e-root", changes[0].entryId)
        assertNotNull(changes[0].entry)
    }

    @Test
    fun testRecentChangesFilters() = runTest {
        val dbFile = tempFolder.newFile("test_filters.db")
        val store = DatasetStoreTest.createSampleDb(dbFile)
        populateTestData(store)

        store.withConnection { conn ->
            conn.execSQL("INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (1, 1000, 'added', 'e-root', 'video', 'Entry Root', 'https://root.com', '', '')")
            conn.execSQL("INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (2, 2000, 'starred', 'e-sub', 'video', 'Entry Sub', 'https://sub.com', '', '')")
            conn.execSQL("INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (3, 3000, 'added', 'e-audio', 'audio', 'Entry Audio', 'https://audio.com', '', '')")
        }

        // Filter by kind
        val starredOnly = store.recentChanges(sinceTs = 0, kinds = setOf(ChangeKind.STARRED))
        assertEquals(1, starredOnly.size)
        assertEquals("e-sub", starredOnly[0].entryId)

        // Filter by section
        val audioOnly = store.recentChanges(sinceTs = 0, kinds = emptySet(), sectionIds = setOf("audio"))
        assertEquals(1, audioOnly.size)
        assertEquals("e-audio", audioOnly[0].entryId)

        // Filter by headingIds: 'video/root' should match both 'video/root' (e-root) and descendant 'video/sub' (e-sub)
        val headingMatches = store.recentChanges(sinceTs = 0, kinds = emptySet(), headingIds = setOf("video/root"))
        assertEquals(2, headingMatches.size)
        // Newest first
        assertEquals("e-sub", headingMatches[0].entryId)
        assertEquals("e-root", headingMatches[1].entryId)

        // Filter by child heading 'video/sub' only
        val subOnly = store.recentChanges(sinceTs = 0, kinds = emptySet(), headingIds = setOf("video/sub"))
        assertEquals(1, subOnly.size)
        assertEquals("e-sub", subOnly[0].entryId)
    }

    @Test
    fun testEntryHistoryOldestFirst() = runTest {
        val dbFile = tempFolder.newFile("test_history.db")
        val store = DatasetStoreTest.createSampleDb(dbFile)
        populateTestData(store)

        store.withConnection { conn ->
            conn.execSQL("INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (1, 3000, 'edited', 'e-root', 'video', 'Entry Root', 'https://root.com', 'edit details', 'sha3')")
            conn.execSQL("INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (2, 1000, 'added', 'e-root', 'video', 'Entry Root', 'https://root.com', '', 'sha1')")
            conn.execSQL("INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (3, 2000, 'starred', 'e-root', 'video', 'Entry Root', 'https://root.com', '', 'sha2')")
        }

        val history = store.entryHistory("e-root")
        assertEquals(3, history.size)
        // Oldest first
        assertEquals(1000L, history[0].ts)
        assertEquals(ChangeKind.ADDED, history[0].kind)
        assertEquals(2000L, history[1].ts)
        assertEquals(ChangeKind.STARRED, history[1].kind)
        assertEquals(3000L, history[2].ts)
        assertEquals(ChangeKind.EDITED, history[2].kind)
        assertEquals("edit details", history[2].detail)
        assertEquals("e-root", history[0].entry?.id)
    }

    @Test
    fun testAddedAt() = runTest {
        val dbFile = tempFolder.newFile("test_added_at.db")
        val store = DatasetStoreTest.createSampleDb(dbFile)
        populateTestData(store)

        store.withConnection { conn ->
            // e-root has an added change at 400 (earlier than first_seen 500)
            conn.execSQL("INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (1, 400, 'added', 'e-root', 'video', 'Entry Root', 'https://root.com', '', 'sha1')")
            // e-root also has a later change at 450
            conn.execSQL("INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (2, 450, 'added', 'e-root', 'video', 'Entry Root', 'https://root.com', '', 'sha2')")
        }

        val addedMap = store.addedAt(listOf("e-root", "e-sub", "e-audio", "unknown"))
        // e-root gets earliest added ts = 400
        assertEquals(400L, addedMap["e-root"])
        // e-sub has no changes, falls back to first_seen = 600
        assertEquals(600L, addedMap["e-sub"])
        // e-audio has no changes, falls back to first_seen = 700
        assertEquals(700L, addedMap["e-audio"])
    }

    @Test
    fun testLinkCount() = runTest {
        val dbFile = tempFolder.newFile("test_link_count.db")
        val store = DatasetStoreTest.createSampleDb(dbFile)
        populateTestData(store)

        // In test data:
        // e-root: kind = 0, 0 mirrors, 0 extra = 1 link
        // e-sub: kind = 0, 1 mirror, 1 extra = 3 links
        // e-audio: kind = 0, 0 mirrors, 0 extra = 1 link
        // e-index: kind = 1 (ignored)
        // Total expected: 1 + 3 + 1 = 5 links
        val count = store.linkCount()
        assertEquals(5, count)
    }
}
