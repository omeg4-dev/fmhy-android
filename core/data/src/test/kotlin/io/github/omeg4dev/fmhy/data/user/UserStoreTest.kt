package io.github.omeg4dev.fmhy.data.user

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createStore(
        dbFile: File = tempFolder.newFile("${java.util.UUID.randomUUID()}.db"),
        clock: () -> Long = System::currentTimeMillis,
    ): UserStore {
        return UserStore(dbFile, clock = clock)
    }

    private fun sampleBookmark(
        id: String,
        savedAt: Long = 1000L,
        note: String = "",
        folderId: Long? = null,
    ): UserBookmark {
        return UserBookmark(
            entryId = id,
            name = "Name $id",
            url = "https://example.com/$id",
            sectionId = "sec",
            sectionTitle = "Section",
            headingPath = "Sec > Head",
            savedAt = savedAt,
            note = note,
            folderId = folderId,
        )
    }

    @Test
    fun testBookmarkCrudAndOrdering() = runTest {
        val store = createStore()

        // Empty initially
        assertTrue(store.bookmarks().isEmpty())
        assertNull(store.bookmark("b1"))

        // Upsert 2 bookmarks
        store.upsertBookmark(sampleBookmark("b1", savedAt = 1000L))
        store.upsertBookmark(sampleBookmark("b2", savedAt = 2000L))

        // Newest first
        val list = store.bookmarks()
        assertEquals(2, list.size)
        assertEquals("b2", list[0].entryId)
        assertEquals("b1", list[1].entryId)

        // Single lookup
        val b1 = store.bookmark("b1")
        assertNotNull(b1)
        assertEquals("Name b1", b1?.name)

        // Set note
        store.setNote("b1", "My favorite site")
        assertEquals("My favorite site", store.bookmark("b1")?.note)

        // Move to folder
        val folder = store.createFolder("Tools")
        store.moveToFolder("b1", folder.id)
        assertEquals(folder.id, store.bookmark("b1")?.folderId)

        store.moveToFolder("b1", null)
        assertNull(store.bookmark("b1")?.folderId)

        // Insert if absent
        val inserted = store.insertBookmarksIfAbsent(
            listOf(
                sampleBookmark("b1", savedAt = 3000L), // already present
                sampleBookmark("b3", savedAt = 1500L), // new
            )
        )
        assertEquals(1, inserted)
        assertEquals(3, store.bookmarks().size)

        // Remove bookmark
        store.removeBookmark("b1")
        assertNull(store.bookmark("b1"))
        assertEquals(2, store.bookmarks().size)

        store.close()
    }

    @Test
    fun testFoldersCrudAndReorder() = runTest {
        val store = createStore()

        assertTrue(store.folders().isEmpty())

        val f1 = store.createFolder("Folder A")
        val f2 = store.createFolder("Folder B")
        assertEquals("Folder A", f1.name)
        assertEquals(0, f1.ord)
        assertEquals("Folder B", f2.name)
        assertEquals(1, f2.ord)

        // Rename
        store.renameFolder(f1.id, "Folder A Renamed")
        val foldersAfterRename = store.folders()
        assertEquals("Folder A Renamed", foldersAfterRename.first { it.id == f1.id }.name)

        // Reorder
        store.reorderFolders(listOf(f2.id, f1.id))
        val reordered = store.folders()
        assertEquals(f2.id, reordered[0].id)
        assertEquals(0, reordered[0].ord)
        assertEquals(f1.id, reordered[1].id)
        assertEquals(1, reordered[1].ord)

        store.close()
    }

    @Test
    fun testFolderDeleteLeavesBookmarksWithNullFolderId() = runTest {
        val store = createStore()

        val folder = store.createFolder("Media")
        val b = sampleBookmark("b1", folderId = folder.id)
        store.upsertBookmark(b)

        assertEquals(folder.id, store.bookmark("b1")?.folderId)

        // Delete folder
        store.deleteFolder(folder.id)
        assertTrue(store.folders().isEmpty())

        // Bookmark must still exist with folderId = null
        val bAfter = store.bookmark("b1")
        assertNotNull(bAfter)
        assertNull(bAfter?.folderId)

        store.close()
    }

    @Test
    fun testHistoryCap500And30MinDedupe() = runTest {
        var currentTime = 100_000L
        val store = createStore(clock = { currentTime })

        // 1. 30-min dedupe test
        // Add URL 1 at t=100,000
        store.addHistory("https://site.com/page", "e1", "Initial Title")
        var hist = store.history()
        assertEquals(1, hist.size)
        assertEquals("Initial Title", hist[0].title)
        assertEquals(100_000L, hist[0].ts)

        // Revisit same URL within 20 minutes (20 * 60 * 1000 = 1,200,000 ms)
        currentTime += 20 * 60 * 1000L
        store.addHistory("https://site.com/page", "e1", "Updated Title")
        hist = store.history()
        assertEquals("Same URL within 30 min should update row, not insert new one", 1, hist.size)
        assertEquals("Updated Title", hist[0].title)
        assertEquals(currentTime, hist[0].ts)

        // Revisit same URL after another 40 minutes (> 30 min)
        currentTime += 40 * 60 * 1000L
        store.addHistory("https://site.com/page", "e1", "New Session Title")
        hist = store.history()
        assertEquals("Same URL after > 30 min should insert new row", 2, hist.size)
        assertEquals("New Session Title", hist[0].title)

        // 2. Cap 500 test
        store.clearHistory()
        assertTrue(store.history().isEmpty())

        for (i in 1..550) {
            currentTime += 1000L
            store.addHistory("https://site$i.com", "e$i", "Title $i")
        }

        val capped = store.history(limit = 1000)
        assertEquals(500, capped.size)
        // Newest should be site550
        assertEquals("https://site550.com", capped[0].url)
        // Oldest kept should be site51
        assertEquals("https://site51.com", capped[499].url)

        store.close()
    }

    @Test
    fun testFollowsAndPinsAndHiddenSections() = runTest {
        val store = createStore()

        // Follows
        assertTrue(store.follows().isEmpty())
        store.follow("video", "movies", "Movies")
        store.follow("audio", "", "Audio Section")
        val follows = store.follows()
        assertEquals(2, follows.size)

        store.unfollow("video", "movies")
        val followsAfter = store.follows()
        assertEquals(1, followsAfter.size)
        assertEquals("audio", followsAfter[0].sectionId)

        // Pins
        assertTrue(store.pins().isEmpty())
        store.pin("video", "movies", "Movies")
        store.pin("audio", "", "Audio")
        val pins = store.pins()
        assertEquals(2, pins.size)
        assertEquals(0, pins[0].ord)
        assertEquals(1, pins[1].ord)

        store.reorderPins(listOf("audio" to "", "video" to "movies"))
        val reorderedPins = store.pins()
        assertEquals("audio", reorderedPins[0].sectionId)
        assertEquals("video", reorderedPins[1].sectionId)

        store.unpin("video", "movies")
        assertEquals(1, store.pins().size)

        // Hidden sections
        assertTrue(store.hiddenSections().isEmpty())
        store.setSectionHidden("nsfw", true)
        store.setSectionHidden("games", true)
        assertEquals(setOf("nsfw", "games"), store.hiddenSections())

        store.setSectionHidden("nsfw", false)
        assertEquals(setOf("games"), store.hiddenSections())

        store.close()
    }

    @Test
    fun testSitePrefsCrudAndAllNullDeletesRow() = runTest {
        val store = createStore()

        assertTrue(store.allSitePrefs().isEmpty())
        assertNull(store.sitePrefs("example.com"))

        val p = SitePrefs(host = "example.com", adblock = true, popups = false, javascript = true)
        store.setSitePrefs(p)

        val retrieved = store.sitePrefs("example.com")
        assertNotNull(retrieved)
        assertEquals(true, retrieved?.adblock)
        assertEquals(false, retrieved?.popups)
        assertEquals(true, retrieved?.javascript)
        assertNull(retrieved?.desktop)
        assertNull(retrieved?.dark)

        // All-null deletes row
        store.setSitePrefs(SitePrefs(host = "example.com"))
        assertNull(store.sitePrefs("example.com"))
        assertTrue(store.allSitePrefs().isEmpty())

        // Clear site prefs
        store.setSitePrefs(SitePrefs(host = "other.org", adblock = true))
        assertEquals(1, store.allSitePrefs().size)
        store.clearSitePrefs("other.org")
        assertTrue(store.allSitePrefs().isEmpty())

        store.close()
    }

    @Test
    fun testKvStore() = runTest {
        val store = createStore()

        assertNull(store.get("key1"))
        store.put("key1", "val1")
        assertEquals("val1", store.get("key1"))

        store.put("key1", "val2")
        assertEquals("val2", store.get("key1"))

        store.close()
    }

    @Test
    fun testExportAndImportIntoFreshStoreYieldsIdenticalData() = runTest {
        val store1 = createStore()

        val f1 = store1.createFolder("Streaming")
        val f2 = store1.createFolder("Audio")
        val b1 = sampleBookmark("b1", savedAt = 1000L, note = "Note 1", folderId = f1.id)
        val b2 = sampleBookmark("b2", savedAt = 2000L, note = "Note 2", folderId = null)
        store1.upsertBookmark(b1)
        store1.upsertBookmark(b2)

        store1.follow("video", "movies", "Movies")
        store1.pin("video", "tv", "TV Shows")
        store1.setSectionHidden("nsfw", true)
        store1.addHistory("https://stream.example", "b1", "Stream")

        val json = store1.exportJson(includeHistory = true)

        val store2 = createStore()
        val result = store2.importJson(json)

        assertEquals(2, result.folders)
        assertEquals(2, result.bookmarks)
        assertEquals(1, result.follows)
        assertEquals(1, result.pins)

        // Verify folders
        val folders2 = store2.folders()
        assertEquals(2, folders2.size)
        val sFolder = folders2.first { it.name == "Streaming" }
        assertEquals("Streaming", sFolder.name)

        // Verify bookmarks and folder mapping
        val bookmarks2 = store2.bookmarks()
        assertEquals(2, bookmarks2.size)
        val b1Restored = bookmarks2.first { it.entryId == "b1" }
        assertEquals(sFolder.id, b1Restored.folderId)
        assertEquals("Note 1", b1Restored.note)

        val b2Restored = bookmarks2.first { it.entryId == "b2" }
        assertNull(b2Restored.folderId)
        assertEquals("Note 2", b2Restored.note)

        // Verify follows, pins, hidden, history
        assertEquals(store1.follows(), store2.follows())
        assertEquals(store1.pins(), store2.pins())
        assertEquals(store1.hiddenSections(), store2.hiddenSections())
        assertEquals(store1.history(), store2.history())

        store1.close()
        store2.close()
    }

    @Test
    fun testImportMergeRules() = runTest {
        val store = createStore()

        // Existing bookmark b1: older savedAt, has non-empty note
        store.upsertBookmark(sampleBookmark("b1", savedAt = 1000L, note = "Original Note"))
        // Existing bookmark b2: newer savedAt, empty note
        store.upsertBookmark(sampleBookmark("b2", savedAt = 3000L, note = ""))

        val folder = store.createFolder("ExistingFolder")

        // Backup has:
        // b1 with newer savedAt (2000L) and empty note -> should update savedAt/metadata, keep "Original Note"
        // b2 with older savedAt (1500L) and non-empty note -> should keep existing savedAt (3000L), take non-empty note
        val backupJson = """
        {
            "format": "fmhy-backup",
            "version": 1,
            "exportedAt": 5000,
            "folders": [
                {"name": "ExistingFolder", "ord": 0, "createdAt": 100},
                {"name": "NewFolder", "ord": 1, "createdAt": 200}
            ],
            "bookmarks": [
                {
                    "entryId": "b1",
                    "name": "Updated Name b1",
                    "url": "https://example.com/b1-new",
                    "sectionId": "sec",
                    "sectionTitle": "Sec",
                    "headingPath": "H",
                    "savedAt": 2000,
                    "note": "",
                    "folder": "ExistingFolder"
                },
                {
                    "entryId": "b2",
                    "name": "Old Backup Name b2",
                    "url": "https://example.com/b2-old",
                    "sectionId": "sec",
                    "sectionTitle": "Sec",
                    "headingPath": "H",
                    "savedAt": 1500,
                    "note": "Imported Note from Backup",
                    "folder": null
                }
            ],
            "follows": [],
            "pins": [],
            "hidden": []
        }
        """.trimIndent()

        store.importJson(backupJson)

        val b1 = store.bookmark("b1")!!
        assertEquals("Updated Name b1", b1.name)
        assertEquals(2000L, b1.savedAt)
        assertEquals("Original Note", b1.note)
        assertEquals(folder.id, b1.folderId)

        val b2 = store.bookmark("b2")!!
        assertEquals("Name b2", b2.name)
        assertEquals(3000L, b2.savedAt)
        assertEquals("Imported Note from Backup", b2.note)

        // ExistingFolder should not be duplicated
        val folders = store.folders()
        assertEquals(2, folders.size)
        assertEquals(setOf("ExistingFolder", "NewFolder"), folders.map { it.name }.toSet())

        store.close()
    }

    @Test
    fun testRejectsWrongFormatOrNewerVersion() = runTest {
        val store = createStore()

        val wrongFormat = """{"format":"other-app","version":1,"exportedAt":1000}"""
        var caught1 = false
        try {
            store.importJson(wrongFormat)
        } catch (ex: IllegalArgumentException) {
            caught1 = true
            assertTrue(ex.message!!.contains("Not an FMHY backup"))
        }
        assertTrue("Expected IllegalArgumentException for wrong format", caught1)

        val newerVersion = """{"format":"fmhy-backup","version":99,"exportedAt":1000}"""
        var caught2 = false
        try {
            store.importJson(newerVersion)
        } catch (ex: IllegalArgumentException) {
            caught2 = true
            assertTrue(ex.message!!.contains("Backup is from a newer app version"))
        }
        assertTrue("Expected IllegalArgumentException for newer version", caught2)

        store.close()
    }

    @Test
    fun testMigrationMechanismFakeV0ToV1() = runTest {
        val dbFile = tempFolder.newFile("legacy_v0.db")

        // Create a raw SQLite DB with user_version = 0 and an old table
        val rawConn = BundledSQLiteDriver().open(dbFile.absolutePath)
        rawConn.execSQL("PRAGMA user_version = 0;")
        rawConn.execSQL("CREATE TABLE legacy_data (data TEXT NOT NULL);")
        rawConn.execSQL("INSERT INTO legacy_data (data) VALUES ('preserve_me');")
        rawConn.close()

        var migrationCalled = false
        val customMigration = mapOf<Int, (androidx.sqlite.SQLiteConnection) -> Unit>(
            0 to { conn ->
                migrationCalled = true
                conn.execSQL("ALTER TABLE legacy_data ADD COLUMN note TEXT DEFAULT 'migrated';")
                UserStore.executeSqlScript(conn, UserStore.loadSchemaSql())
            }
        )

        // Open UserStore with targetVersion = 1
        val store = UserStore(dbFile, targetVersion = 1, customMigrations = customMigration)
        assertTrue(migrationCalled)

        // Verify legacy data was preserved and NOT deleted
        store.withConnection { conn ->
            conn.prepare("SELECT data, note FROM legacy_data").use { stmt ->
                assertTrue(stmt.step())
                assertEquals("preserve_me", stmt.getText(0))
                assertEquals("migrated", stmt.getText(1))
            }
            conn.prepare("PRAGMA user_version;").use { stmt ->
                assertTrue(stmt.step())
                assertEquals(1L, stmt.getLong(0))
            }
        }
        store.close()

        // Re-open: migration should not run again
        var reRunCalled = false
        val reStore = UserStore(
            dbFile,
            targetVersion = 1,
            customMigrations = mapOf(0 to { _ -> reRunCalled = true })
        )
        assertFalse(reRunCalled)
        reStore.close()
    }

    @Test
    fun testReopenPersistence() = runTest {
        val dbFile = tempFolder.newFile("persistent.db")
        val store1 = UserStore(dbFile)

        store1.put("k", "v")
        store1.upsertBookmark(sampleBookmark("b1", note = "Persistent Note"))
        store1.createFolder("Persistent Folder")
        store1.follow("sec", "head", "Follow Title")
        store1.pin("sec", "head", "Pin Title")
        store1.setSitePrefs(SitePrefs(host = "test.org", dark = true))
        store1.close()

        // New store on same dbFile
        val store2 = UserStore(dbFile)
        assertEquals("v", store2.get("k"))
        assertEquals("Persistent Note", store2.bookmark("b1")?.note)
        assertEquals(1, store2.folders().size)
        assertEquals("Persistent Folder", store2.folders()[0].name)
        assertEquals(1, store2.follows().size)
        assertEquals(1, store2.pins().size)
        assertEquals(true, store2.sitePrefs("test.org")?.dark)

        store2.close()
    }

    @Test
    fun testChangesBumpsOnWrites() = runTest {
        val store = createStore()

        var currentChange = store.changes.value
        assertEquals(0L, currentChange)

        store.upsertBookmark(sampleBookmark("b1"))
        assertTrue(store.changes.value > currentChange)
        currentChange = store.changes.value

        store.setNote("b1", "new note")
        assertTrue(store.changes.value > currentChange)
        currentChange = store.changes.value

        store.createFolder("F1")
        assertTrue(store.changes.value > currentChange)
        currentChange = store.changes.value

        store.addHistory("https://url.com", null, "Title")
        assertTrue(store.changes.value > currentChange)
        currentChange = store.changes.value

        store.follow("s", "h", "T")
        assertTrue(store.changes.value > currentChange)
        currentChange = store.changes.value

        store.pin("s", "h", "T")
        assertTrue(store.changes.value > currentChange)
        currentChange = store.changes.value

        store.setSectionHidden("s", true)
        assertTrue(store.changes.value > currentChange)
        currentChange = store.changes.value

        store.setSitePrefs(SitePrefs(host = "x.com", adblock = true))
        assertTrue(store.changes.value > currentChange)
        currentChange = store.changes.value

        store.put("key", "val")
        assertTrue(store.changes.value > currentChange)

        store.close()
    }
}
