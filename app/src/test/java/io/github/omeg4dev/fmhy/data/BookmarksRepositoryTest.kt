package io.github.omeg4dev.fmhy.data

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.user.UserStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun createEntry(id: String, name: String, sectionId: String = "sec"): Entry {
        return Entry(
            num = 1,
            id = id,
            sectionId = sectionId,
            headingId = null,
            ord = 1,
            name = name,
            url = "https://example.com/$id",
            originalUrl = null,
            description = "Description for $name",
            tags = emptyList(),
            headingPath = "Heading > $name",
            tier = Tier.NORMAL,
            isInlineMore = false,
            isBase64 = false,
            mirrors = emptyList(),
            extraLinks = emptyList(),
            firstSeen = 1000L,
            lastChanged = 1000L,
            starredAt = null,
        )
    }

    @Test
    fun testToggleAddsThenRemoves() = runTest {
        val userStore = UserStore(tmp.newFile("user1.db"))
        val repository = BookmarksRepository(userStore = userStore, clock = { 1000L })
        val entry = createEntry("e1", "Entry 1")

        // Initially empty
        assertTrue(repository.bookmarks.first().isEmpty())
        assertTrue(repository.savedIds.first().isEmpty())

        // Toggle to add
        repository.toggle(entry, "Section 1")
        val addedBookmarks = repository.bookmarks.first()
        assertEquals(1, addedBookmarks.size)
        assertEquals("e1", addedBookmarks[0].entryId)
        assertEquals("Entry 1", addedBookmarks[0].name)
        assertEquals("Section 1", addedBookmarks[0].sectionTitle)
        assertEquals(setOf("e1"), repository.savedIds.first())

        // Toggle to remove
        repository.toggle(entry, "Section 1")
        assertTrue(repository.bookmarks.first().isEmpty())
        assertTrue(repository.savedIds.first().isEmpty())

        userStore.close()
    }

    @Test
    fun testNewestFirst() = runTest {
        val userStore = UserStore(tmp.newFile("user2.db"))
        var currentTime = 1000L
        val repository = BookmarksRepository(userStore = userStore, clock = { currentTime })

        val entry1 = createEntry("e1", "Entry 1")
        val entry2 = createEntry("e2", "Entry 2")

        currentTime = 1000L
        repository.toggle(entry1, "Section 1")

        currentTime = 2000L
        repository.toggle(entry2, "Section 2")

        val bookmarks = repository.bookmarks.first()
        assertEquals(2, bookmarks.size)
        assertEquals("e2", bookmarks[0].entryId)
        assertEquals(2000L, bookmarks[0].savedAt)
        assertEquals("e1", bookmarks[1].entryId)
        assertEquals(1000L, bookmarks[1].savedAt)

        userStore.close()
    }

    @Test
    fun testSurvivesNewRepositoryInstanceOnSameFile() = runTest {
        val dbFile = tmp.newFile("user3.db")
        val store1 = UserStore(dbFile)
        val repo1 = BookmarksRepository(userStore = store1, clock = { 1234L })
        val entry = createEntry("e1", "Persistent Entry")

        repo1.toggle(entry, "Section P")
        assertEquals(1, repo1.bookmarks.first().size)
        store1.close()

        // New repository instance on the same file
        val store2 = UserStore(dbFile)
        val repo2 = BookmarksRepository(userStore = store2)

        val restored = repo2.bookmarks.first()
        assertEquals(1, restored.size)
        assertEquals("e1", restored[0].entryId)
        assertEquals("Persistent Entry", restored[0].name)
        assertEquals("Section P", restored[0].sectionTitle)
        assertEquals(setOf("e1"), repo2.savedIds.first())

        store2.close()
    }

    @Test
    fun testBackupRoundTripThroughRepository() = runTest {
        val db1 = tmp.newFile("user_export.db")
        val store1 = UserStore(db1)
        val repo1 = BookmarksRepository(userStore = store1, clock = { 1000L })

        val folder = repo1.createFolder("My Folder")
        val entry = createEntry("e1", "Site A")
        repo1.toggle(entry, "Section A")
        repo1.moveToFolder("e1", folder.id)
        repo1.setNote("e1", "Great site")

        val backupJson = repo1.exportJson(includeHistory = false)
        assertTrue(backupJson.contains("fmhy-backup"))
        assertTrue(backupJson.contains("My Folder"))
        assertTrue(backupJson.contains("Great site"))

        store1.close()

        val db2 = tmp.newFile("user_import.db")
        val store2 = UserStore(db2)
        val repo2 = BookmarksRepository(userStore = store2, clock = { 2000L })

        val result = repo2.importJson(backupJson)
        assertEquals(1, result.bookmarks)
        assertEquals(1, result.folders)

        val restoredBookmarks = repo2.bookmarks.first()
        assertEquals(1, restoredBookmarks.size)
        assertEquals("e1", restoredBookmarks[0].entryId)
        assertEquals("Great site", restoredBookmarks[0].note)

        val restoredFolders = repo2.folders.first()
        assertEquals(1, restoredFolders.size)
        assertEquals("My Folder", restoredFolders[0].name)
        assertEquals(restoredFolders[0].id, restoredBookmarks[0].folderId)

        store2.close()
    }
}
