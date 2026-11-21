package io.github.omeg4dev.fmhy.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatasetStoreCriticalTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTestDb(dbFile: File): DatasetStore {
        val conn = BundledSQLiteDriver().open(dbFile.absolutePath)
        DatasetStore.executeSqlScript(conn, DatasetStore.loadSchemaSql())
        conn.execSQL("INSERT INTO meta (key, value) VALUES ('schema', '$DATASET_SCHEMA'), ('version', '1');")

        conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('audio', 'Audio', 'Audio Desc', 'audio.md', 1);")

        conn.execSQL("""
            INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, line, first_seen, last_changed)
            VALUES (1, 'foo_entry', 'audio', null, 1, 'Foo', 'https://foo.example', 3, 100, 100);
        """.trimIndent())
        conn.execSQL("""
            INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, line, first_seen, last_changed)
            VALUES (2, 'bar_entry', 'audio', null, 2, 'Bar', 'https://bar.example', 4, 100, 100);
        """.trimIndent())
        conn.execSQL("""
            INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, line, first_seen, last_changed)
            VALUES (3, 'multi_entry', 'audio', null, 3, 'Multi', 'https://multi.example', 5, 100, 100);
        """.trimIndent())

        for (i in 0 until 550) {
            conn.execSQL("""
                INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, line, first_seen, last_changed)
                VALUES (${10 + i}, 'entry_$i', 'audio', null, ${10 + i}, 'Entry $i', 'https://e$i.example', 100, 100, 100);
            """.trimIndent())
        }

        conn.execSQL("INSERT INTO critical_notes (id, title, text_md, hash) VALUES ('bar-note', 'Bar Danger', 'Bar text', '0d09fd9ff28b');")
        conn.execSQL("INSERT INTO critical_notes (id, title, text_md, hash) VALUES ('foo-critical', 'Foo Warning', 'Foo text', '73ec7e5dfce0');")
        conn.execSQL("INSERT INTO critical_notes (id, title, text_md, hash) VALUES ('other-critical', 'Other', 'Other text', '1234567890ab');")

        conn.execSQL("INSERT INTO note_links (entry_id, note_id, section_id, line, ord) VALUES ('bar_entry', 'bar-note', 'audio', 4, 0);")
        conn.execSQL("INSERT INTO note_links (entry_id, note_id, section_id, line, ord) VALUES ('foo_entry', 'foo-critical', 'audio', 3, 0);")
        conn.execSQL("INSERT INTO note_links (entry_id, note_id, section_id, line, ord) VALUES ('multi_entry', 'other-critical', 'audio', 5, 1);")
        conn.execSQL("INSERT INTO note_links (entry_id, note_id, section_id, line, ord) VALUES ('multi_entry', 'bar-note', 'audio', 5, 0);")
        conn.execSQL("INSERT INTO note_links (entry_id, note_id, section_id, line, ord) VALUES ('multi_entry', 'noncritical-note', 'audio', 5, 2);")

        for (i in 0 until 550) {
            conn.execSQL("INSERT INTO note_links (entry_id, note_id, section_id, line, ord) VALUES ('entry_$i', 'bar-note', 'audio', 100, 0);")
        }

        conn.close()
        return DatasetStore(dbFile)
    }

    @Test
    fun testAllSixMethodsAndOrdOrderAndLargeCollectionAndGuards() = runTest {
        val dbFile = tempFolder.newFile("critical_test.db")
        val store = createTestDb(dbFile)

        // 1. criticalNotes()
        val allNotes = store.criticalNotes()
        assertEquals(3, allNotes.size)
        assertEquals("bar-note", allNotes[0].id)
        assertEquals("foo-critical", allNotes[1].id)
        assertEquals("other-critical", allNotes[2].id)

        // 2. criticalNote(id) - present and absent
        val fooNote = store.criticalNote("foo-critical")
        assertNotNull(fooNote)
        assertEquals("foo-critical", fooNote!!.id)
        assertEquals("Foo Warning", fooNote.title)
        assertEquals("Foo text", fooNote.textMd)
        assertEquals("73ec7e5dfce0", fooNote.hash)

        val absentNote = store.criticalNote("absent-note")
        assertNull(absentNote)

        // 3. isCriticalNote(id) - present and absent
        assertTrue(store.isCriticalNote("foo-critical"))
        assertTrue(store.isCriticalNote("bar-note"))
        assertFalse(store.isCriticalNote("absent-note"))
        assertFalse(store.isCriticalNote("noncritical-note"))

        // 4. criticalNotesForEntry(entryId) - ord order and filters out non-critical
        val multiNotes = store.criticalNotesForEntry("multi_entry")
        assertEquals(2, multiNotes.size)
        assertEquals("bar-note", multiNotes[0].id)        // ord 0
        assertEquals("other-critical", multiNotes[1].id)   // ord 1

        val absentEntryNotes = store.criticalNotesForEntry("absent_entry")
        assertTrue(absentEntryNotes.isEmpty())

        // 5. criticalNoteForEntry(entryId) - primary (lowest ord) note, and bold Bar entry guarded
        val multiPrimary = store.criticalNoteForEntry("multi_entry")
        assertNotNull(multiPrimary)
        assertEquals("bar-note", multiPrimary!!.id)

        val barPrimary = store.criticalNoteForEntry("bar_entry")
        assertNotNull(barPrimary)
        assertEquals("bar-note", barPrimary!!.id)

        val absentEntryPrimary = store.criticalNoteForEntry("absent_entry")
        assertNull(absentEntryPrimary)

        // 6. criticalNoteIdsForEntries(entryIds) - >500 ids chunking and lowest ord
        val entryIds = mutableListOf<String>()
        entryIds.add("bar_entry")
        entryIds.add("multi_entry")
        for (i in 0 until 550) {
            entryIds.add("entry_$i")
        }
        assertEquals(552, entryIds.size)

        val idsMap = store.criticalNoteIdsForEntries(entryIds)
        assertEquals(552, idsMap.size)
        assertEquals("bar-note", idsMap["bar_entry"])
        assertEquals("bar-note", idsMap["multi_entry"])
        assertEquals("bar-note", idsMap["entry_0"])
        assertEquals("bar-note", idsMap["entry_549"])

        // Empty input returns empty map
        val emptyMap = store.criticalNoteIdsForEntries(emptyList())
        assertTrue(emptyMap.isEmpty())

        // Absent ids omitted
        val absentMap = store.criticalNoteIdsForEntries(listOf("absent_1", "absent_2"))
        assertTrue(absentMap.isEmpty())

        // Internal helpers
        val linked = store.linkedNoteIdsImpl()
        assertEquals(setOf("bar-note", "foo-critical", "other-critical", "noncritical-note"), linked)

        val stored = store.storedCriticalNotesImpl()
        assertEquals(3, stored.size)
        assertEquals("Foo Warning", stored["foo-critical"]?.title)

        store.close()
    }
}
