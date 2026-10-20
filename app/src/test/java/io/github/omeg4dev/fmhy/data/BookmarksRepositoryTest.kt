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
}
