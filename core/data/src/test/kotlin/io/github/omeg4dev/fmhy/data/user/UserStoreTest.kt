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
    fun testDeleteHistory() = runTest {
        var currentTime = 100_000L
        val store = createStore(clock = { currentTime })

        // Add 3 history rows (2 hosts: host A has 2 rows, host B has 1 row)
        store.addHistory("https://host-a.com/page1", "e1", "Host A Page 1")
        currentTime += 40 * 60 * 1000L
        store.addHistory("https://host-a.com/page2", "e2", "Host A Page 2")
        currentTime += 40 * 60 * 1000L
        store.addHistory("https://host-b.com/page1", "e3", "Host B Page 1")

        val initialHist = store.history()
        assertEquals(3, initialHist.size)

        // deleteHistory(emptyList()) leaves rows intact
        store.deleteHistory(emptyList())
        assertEquals(3, store.history().size)

        // deleteHistory(ids of one host)
        val hostAIds = initialHist.filter { it.url.contains("host-a.com") }.map { it.id }
        assertEquals(2, hostAIds.size)
        store.deleteHistory(hostAIds)

        // history() returns only the other host's row
        val remaining = store.history()
        assertEquals(1, remaining.size)
        assertEquals("https://host-b.com/page1", remaining[0].url)

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

    @Test
    fun testUnsaveAndRestoreKeepsNoteAndFolder() = runTest {
        val store = createStore()
        val folder = store.createFolder("Work Tools")
        val bookmark = sampleBookmark("b1", savedAt = 12345L, note = "Important site", folderId = folder.id)
        store.upsertBookmark(bookmark)

        val before = store.bookmark("b1")
        assertNotNull(before)
        assertEquals("Important site", before?.note)
        assertEquals(folder.id, before?.folderId)

        // Snapshot and remove
        val snapshot = store.removeBookmarkWithSnapshot("b1")
        assertNotNull(snapshot)
        assertEquals("b1", snapshot?.entryId)
        assertEquals("Important site", snapshot?.note)
        assertEquals(folder.id, snapshot?.folderId)
        assertNull(store.bookmark("b1"))

        // Restore
        store.restoreBookmark(snapshot!!)
        val restored = store.bookmark("b1")
        assertNotNull(restored)
        assertEquals("Important site", restored?.note)
        assertEquals(folder.id, restored?.folderId)
        assertEquals(12345L, restored?.savedAt)

        store.close()
    }

    @Test
    fun testFolderDeleteAndRestoreKeepsFolderAndBookmarkAssignments() = runTest {
        val store = createStore()
        val f1 = store.createFolder("Folder 1")
        val f2 = store.createFolder("Folder 2")
        val b1 = sampleBookmark("b1", folderId = f1.id)
        val b2 = sampleBookmark("b2", folderId = f1.id)
        val b3 = sampleBookmark("b3", folderId = f2.id)
        store.upsertBookmark(b1)
        store.upsertBookmark(b2)
        store.upsertBookmark(b3)

        // Delete f1 with snapshot
        val snapshot = store.deleteFolderWithSnapshot(f1.id)
        assertNotNull(snapshot)
        assertEquals(f1.id, snapshot?.folder?.id)
        assertEquals(setOf("b1", "b2"), snapshot?.bookmarkEntryIds?.toSet())

        // Folder is deleted, and its bookmarks have folderId = null (due to ON DELETE SET NULL)
        assertNull(store.folders().firstOrNull { it.id == f1.id })
        assertNull(store.bookmark("b1")?.folderId)
        assertNull(store.bookmark("b2")?.folderId)
        assertEquals(f2.id, store.bookmark("b3")?.folderId)

        // Restore f1
        store.restoreFolder(snapshot!!)
        val restoredFolder = store.folders().firstOrNull { it.id == f1.id }
        assertNotNull(restoredFolder)
        assertEquals("Folder 1", restoredFolder?.name)
        assertEquals(f1.ord, restoredFolder?.ord)
        assertEquals(f1.id, store.bookmark("b1")?.folderId)
        assertEquals(f1.id, store.bookmark("b2")?.folderId)
        assertEquals(f2.id, store.bookmark("b3")?.folderId)

        store.close()
    }

    @Test
    fun testHistoryClearAndRestore() = runTest {
        val store = createStore()
        store.addHistory("https://site1.com", "e1", "Site 1")
        store.addHistory("https://site2.com", "e2", "Site 2")
        val historyBefore = store.history()
        assertEquals(2, historyBefore.size)

        // Clear with snapshot
        val snapshot = store.clearHistoryWithSnapshot()
        assertEquals(2, snapshot.size)
        assertTrue(store.history().isEmpty())

        // Restore
        store.restoreHistory(snapshot)
        val restoredHistory = store.history()
        assertEquals(2, restoredHistory.size)
        assertEquals(historyBefore.map { it.url }, restoredHistory.map { it.url })
        assertEquals(historyBefore.map { it.title }, restoredHistory.map { it.title })
        assertEquals(historyBefore.map { it.id }, restoredHistory.map { it.id })

        store.close()
    }

    @Test
    fun testPinAndFollowRestore() = runTest {
        val store = createStore()
        store.pin("sec1", "", "Section 1")
        store.pin("sec2", "cat1", "Cat 1")
        val pinsBefore = store.pins()
        assertEquals(2, pinsBefore.size)
        val pinToUnpin = pinsBefore[0]

        // Unpin
        store.unpin(pinToUnpin.sectionId, pinToUnpin.headingId)
        assertEquals(1, store.pins().size)

        // Restore pin at its old position
        store.restorePin(pinToUnpin)
        val pinsAfter = store.pins()
        assertEquals(2, pinsAfter.size)
        assertEquals(pinToUnpin.sectionId, pinsAfter[0].sectionId)
        assertEquals(pinToUnpin.ord, pinsAfter[0].ord)

        // Follow restore
        store.follow("sec1", "cat1", "Cat 1")
        val followsBefore = store.follows()
        val followToUnfollow = followsBefore[0]
        store.unfollow(followToUnfollow.sectionId, followToUnfollow.headingId)
        assertTrue(store.follows().isEmpty())

        store.restoreFollow(followToUnfollow)
        val followsAfter = store.follows()
        assertEquals(1, followsAfter.size)
        assertEquals(followToUnfollow.sectionId, followsAfter[0].sectionId)
        assertEquals(followToUnfollow.title, followsAfter[0].title)

        store.close()
    }

    @Test
    fun testFollowSectionRemovesSubRows() = runTest {
        val store = createStore()
        store.follow("video", "movies", "Movies")
        store.follow("video", "tv", "TV Shows")
        store.follow("audio", "music", "Music")
        assertEquals(3, store.follows().size)

        // Follow whole video section
        store.followSection("video", "All Video")
        val follows = store.follows()
        // movies and tv should be deleted; video section follow created; audio follow preserved
        assertEquals(2, follows.size)
        val videoFollow = follows.firstOrNull { it.sectionId == "video" }
        assertNotNull(videoFollow)
        assertEquals("", videoFollow!!.headingId)
        assertEquals("All Video", videoFollow.title)
        assertFalse(videoFollow.excluded)

        val audioFollow = follows.firstOrNull { it.sectionId == "audio" }
        assertNotNull(audioFollow)
        assertEquals("music", audioFollow!!.headingId)

        store.close()
    }

    @Test
    fun testSetSubsectionFollowedUnderFollowedSection() = runTest {
        val store = createStore()
        store.followSection("video", "All Video")
        assertEquals(1, store.follows().size)

        // Switching off a subsection under a followed section creates an exclusion row
        store.setSubsectionFollowed("video", "movies", "Movies", followed = false)
        val followsAfterExclude = store.follows()
        assertEquals(2, followsAfterExclude.size)
        val exclusion = followsAfterExclude.firstOrNull { it.headingId == "movies" }
        assertNotNull(exclusion)
        assertEquals("video", exclusion!!.sectionId)
        assertTrue(exclusion.excluded)

        // Switching subsection back on under a followed section deletes the exclusion row
        store.setSubsectionFollowed("video", "movies", "Movies", followed = true)
        val followsAfterReInclude = store.follows()
        assertEquals(1, followsAfterReInclude.size)
        assertEquals("", followsAfterReInclude[0].headingId)
        assertFalse(followsAfterReInclude[0].excluded)

        store.close()
    }

    @Test
    fun testSetSubsectionFollowedWithoutSectionFollow() = runTest {
        val store = createStore()
        // No whole-section follow for "video"
        // Following subsection creates plain row with excluded=0
        store.setSubsectionFollowed("video", "movies", "Movies", followed = true)
        val follows = store.follows()
        assertEquals(1, follows.size)
        assertEquals("video", follows[0].sectionId)
        assertEquals("movies", follows[0].headingId)
        assertFalse(follows[0].excluded)

        // Unfollowing subsection deletes the row
        store.setSubsectionFollowed("video", "movies", "Movies", followed = false)
        assertTrue(store.follows().isEmpty())

        store.close()
    }

    @Test
    fun testUnfollowSectionDeletesEverything() = runTest {
        val store = createStore()
        store.followSection("video", "All Video")
        store.setSubsectionFollowed("video", "movies", "Movies", followed = false) // exclusion row
        store.follow("audio", "", "Audio") // another section
        assertEquals(3, store.follows().size)

        store.unfollowSection("video")
        val follows = store.follows()
        assertEquals(1, follows.size)
        assertEquals("audio", follows[0].sectionId)

        store.close()
    }

    @Test
    fun testMigrationFromOldSchemaWithoutExcludedColumn() = runTest {
        val dbFile = tempFolder.newFile("legacy_v1.db")
        val rawConn = BundledSQLiteDriver().open(dbFile.absolutePath)
        rawConn.execSQL("PRAGMA user_version = 1;")
        rawConn.execSQL("""
            CREATE TABLE follows (
                section_id TEXT NOT NULL,
                heading_id TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (section_id, heading_id)
            ) WITHOUT ROWID;
        """.trimIndent())
        rawConn.execSQL("INSERT INTO follows (section_id, heading_id, title, created_at) VALUES ('video', '', 'Video Section', 1000);")
        rawConn.execSQL("INSERT INTO follows (section_id, heading_id, title, created_at) VALUES ('audio', 'podcasts', 'Podcasts', 2000);")
        rawConn.close()

        // Opening with default targetVersion (= 3) will run migration 1 -> 2
        val store = UserStore(dbFile)
        val follows = store.follows()
        assertEquals(2, follows.size)
        val video = follows.first { it.sectionId == "video" }
        assertEquals("", video.headingId)
        assertEquals("Video Section", video.title)
        assertEquals(1000L, video.createdAt)
        assertFalse(video.excluded)

        val podcasts = follows.first { it.headingId == "podcasts" }
        assertEquals("audio", podcasts.sectionId)
        assertEquals("Podcasts", podcasts.title)
        assertEquals(2000L, podcasts.createdAt)
        assertFalse(podcasts.excluded)

        // Check user_version in DB
        store.withConnection { conn ->
            conn.prepare("PRAGMA user_version;").use { stmt ->
                assertTrue(stmt.step())
                assertEquals(3L, stmt.getLong(0))
            }
        }
        store.close()
    }

    @Test
    fun testExportImportRoundTripKeepsExcluded() = runTest {
        val store1 = createStore()
        store1.followSection("video", "All Video")
        store1.setSubsectionFollowed("video", "movies", "Movies", followed = false) // excluded = true
        store1.follow("audio", "music", "Music") // excluded = false

        val json = store1.exportJson(includeHistory = false)
        assertTrue(json.contains("\"excluded\":true") || json.contains("\"excluded\": true"))

        val store2 = createStore()
        val result = store2.importJson(json)
        assertEquals(3, result.follows)

        val importedFollows = store2.follows()
        assertEquals(3, importedFollows.size)

        val videoSection = importedFollows.first { it.sectionId == "video" && it.headingId == "" }
        assertFalse(videoSection.excluded)

        val moviesExclusion = importedFollows.first { it.sectionId == "video" && it.headingId == "movies" }
        assertTrue(moviesExclusion.excluded)

        val musicFollow = importedFollows.first { it.sectionId == "audio" && it.headingId == "music" }
        assertFalse(musicFollow.excluded)

        store1.close()
        store2.close()
    }

    @Test
    fun testImportOldBackupWithoutExcludedField() = runTest {
        val store = createStore()
        val oldBackupJson = """
            {
                "format": "fmhy-backup",
                "version": 1,
                "exportedAt": 1000,
                "follows": [
                    {"sectionId": "video", "headingId": "movies", "title": "Movies", "createdAt": 1000}
                ]
            }
        """.trimIndent()
        val result = store.importJson(oldBackupJson)
        assertEquals(1, result.follows)

        val follows = store.follows()
        assertEquals(1, follows.size)
        assertEquals("movies", follows[0].headingId)
        assertFalse(follows[0].excluded)

        store.close()
    }

    @Test
    fun testInsertDownloadsAndOrder() = runTest {
        val store = createStore()
        val r1 = DownloadRecord(
            url = "https://example.com/file1.apk",
            fileName = "file1.apk",
            mimeType = "application/vnd.android.package-archive",
            sourceHost = "example.com",
            referer = "https://example.com",
            userAgent = "CustomUA",
            totalBytes = 1000L,
            downloadedBytes = 500L,
            status = "RUNNING",
            resumable = true,
            restartNote = false,
            allowMobile = true,
            error = null,
            fileUri = "content://downloads/1",
            folderLabel = "Downloads",
            etag = "\"etag1\"",
            lastModified = "Wed, 21 Oct 2025 07:28:00 GMT",
            createdAt = 1000L,
            finishedAt = null,
        )
        val id1 = store.insertDownload(r1)
        assertTrue(id1 > 0)

        val list1 = store.downloads()
        assertEquals(1, list1.size)
        assertEquals(r1.copy(id = id1), list1[0])

        val r2 = DownloadRecord(
            url = "https://example.com/file2.zip",
            fileName = "file2.zip",
            mimeType = "application/zip",
            sourceHost = "example.com",
            referer = null,
            userAgent = null,
            totalBytes = 2000L,
            downloadedBytes = 2000L,
            status = "FINISHED",
            resumable = false,
            restartNote = true,
            allowMobile = false,
            error = "some error",
            fileUri = "content://downloads/2",
            folderLabel = "Other",
            etag = null,
            lastModified = null,
            createdAt = 2000L,
            finishedAt = 2500L,
        )
        val id2 = store.insertDownload(r2)
        assertTrue(id2 > id1)

        val list2 = store.downloads()
        assertEquals(2, list2.size)
        assertEquals(id2, list2[0].id)
        assertEquals(r2.copy(id = id2), list2[0])
        assertEquals(id1, list2[1].id)
        assertEquals(r1.copy(id = id1), list2[1])

        store.close()
    }

    @Test
    fun testUpdateDownload() = runTest {
        val store = createStore()
        val original = DownloadRecord(
            url = "https://example.com/test.bin",
            fileName = "original.bin",
            mimeType = "application/octet-stream",
            sourceHost = "example.com",
            referer = "https://example.com/ref",
            userAgent = "UA",
            totalBytes = 5000L,
            downloadedBytes = 100L,
            status = "RUNNING",
            resumable = true,
            restartNote = false,
            allowMobile = false,
            error = null,
            fileUri = null,
            folderLabel = "Downloads",
            etag = null,
            lastModified = null,
            createdAt = 1000L,
            finishedAt = null,
        )
        val id = store.insertDownload(original)

        val updated = original.copy(
            id = id,
            url = "https://example.com/CHANGED_URL_IGNORED",
            createdAt = 999999L,
            fileName = "renamed.bin",
            mimeType = "application/pdf",
            sourceHost = "other.com",
            referer = "https://other.com/ref",
            userAgent = "NewUA",
            totalBytes = 6000L,
            downloadedBytes = 5000L,
            status = "FINISHED",
            resumable = false,
            restartNote = true,
            allowMobile = true,
            error = "no error",
            fileUri = "content://new/uri",
            folderLabel = "Docs",
            etag = "\"etag2\"",
            lastModified = "new date",
            finishedAt = 5000L,
        )
        store.updateDownload(updated)

        val records = store.downloads()
        assertEquals(1, records.size)
        val r = records[0]
        assertEquals(id, r.id)
        assertEquals("https://example.com/test.bin", r.url)
        assertEquals(1000L, r.createdAt)
        assertEquals("renamed.bin", r.fileName)
        assertEquals("application/pdf", r.mimeType)
        assertEquals("other.com", r.sourceHost)
        assertEquals("https://other.com/ref", r.referer)
        assertEquals("NewUA", r.userAgent)
        assertEquals(6000L, r.totalBytes)
        assertEquals(5000L, r.downloadedBytes)
        assertEquals("FINISHED", r.status)
        assertFalse(r.resumable)
        assertTrue(r.restartNote)
        assertTrue(r.allowMobile)
        assertEquals("no error", r.error)
        assertEquals("content://new/uri", r.fileUri)
        assertEquals("Docs", r.folderLabel)
        assertEquals("\"etag2\"", r.etag)
        assertEquals("new date", r.lastModified)
        assertEquals(5000L, r.finishedAt)

        store.close()
    }

    @Test
    fun testDeleteDownloads() = runTest {
        val store = createStore()
        val id1 = store.insertDownload(DownloadRecord(url = "https://a.com/1", fileName = "1", status = "FINISHED", createdAt = 1))
        val id2 = store.insertDownload(DownloadRecord(url = "https://a.com/2", fileName = "2", status = "FINISHED", createdAt = 2))
        val id3 = store.insertDownload(DownloadRecord(url = "https://a.com/3", fileName = "3", status = "RUNNING", createdAt = 3))
        val id4 = store.insertDownload(DownloadRecord(url = "https://a.com/4", fileName = "4", status = "FAILED", createdAt = 4))

        val deletedCount = store.deleteDownloadsWithStatus("FINISHED")
        assertEquals(2, deletedCount)

        val remaining = store.downloads()
        assertEquals(2, remaining.size)
        assertEquals(setOf(id3, id4), remaining.map { it.id }.toSet())

        store.deleteDownload(id3)
        val remainingAfterSingle = store.downloads()
        assertEquals(1, remainingAfterSingle.size)
        assertEquals(id4, remainingAfterSingle[0].id)

        store.close()
    }

    @Test
    fun testMigration2To3() = runTest {
        val dbFile = tempFolder.newFile("legacy_v2.db")
        val rawConn = BundledSQLiteDriver().open(dbFile.absolutePath)
        rawConn.execSQL("PRAGMA user_version = 2;")
        rawConn.execSQL("""
            CREATE TABLE follows (
                section_id TEXT NOT NULL,
                heading_id TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                excluded INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (section_id, heading_id)
            ) WITHOUT ROWID;
        """.trimIndent())
        rawConn.execSQL("INSERT INTO follows (section_id, heading_id, title, created_at, excluded) VALUES ('video', '', 'Video', 1000, 0);")
        rawConn.close()

        val store = UserStore(dbFile)
        val downloadId = store.insertDownload(
            DownloadRecord(
                url = "https://example.com/file.apk",
                fileName = "file.apk",
                status = "RUNNING",
                createdAt = 12345L,
            )
        )
        assertTrue(downloadId > 0)
        val dl = store.downloads()
        assertEquals(1, dl.size)
        assertEquals("file.apk", dl[0].fileName)

        store.withConnection { conn ->
            conn.prepare("PRAGMA user_version;").use { stmt ->
                assertTrue(stmt.step())
                assertEquals(3L, stmt.getLong(0))
            }
        }
        store.close()
    }

    @Test
    fun testDownloadsDoNotChangeChangesFlow() = runTest {
        val store = createStore()
        val initialChanges = store.changes.value

        val id = store.insertDownload(
            DownloadRecord(
                url = "https://example.com/test",
                fileName = "test",
                status = "RUNNING",
                createdAt = 1000L,
            )
        )
        assertEquals(initialChanges, store.changes.value)

        store.updateDownload(
            DownloadRecord(
                id = id,
                url = "https://example.com/test",
                fileName = "test2",
                status = "FINISHED",
                createdAt = 1000L,
            )
        )
        assertEquals(initialChanges, store.changes.value)

        store.downloads()
        assertEquals(initialChanges, store.changes.value)

        store.deleteDownload(id)
        assertEquals(initialChanges, store.changes.value)

        store.deleteDownloadsWithStatus("FINISHED")
        assertEquals(initialChanges, store.changes.value)

        store.close()
    }
}
