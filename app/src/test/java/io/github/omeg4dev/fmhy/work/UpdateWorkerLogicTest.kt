package io.github.omeg4dev.fmhy.work

import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.user.Follow
import io.github.omeg4dev.fmhy.data.user.UserBookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateWorkerLogicTest {

    private val nowSec = 1760800000L

    private fun makeFollow(sectionId: String, headingId: String = "", title: String = sectionId): Follow {
        return Follow(
            sectionId = sectionId,
            headingId = headingId,
            title = title,
            createdAt = nowSec,
        )
    }

    private fun makeBookmark(entryId: String, name: String, url: String): UserBookmark {
        return UserBookmark(
            entryId = entryId,
            name = name,
            url = url,
            sectionId = "video",
            sectionTitle = "Video",
            headingPath = "Video › Streaming",
            savedAt = nowSec,
        )
    }

    @Test
    fun testFirstRunNoSpam() {
        val oldChange = makeChange(1, nowSec - 200000L, ChangeKind.ADDED, "Old Site", "video")
        val recentChange = makeChange(2, nowSec - 3600L, ChangeKind.ADDED, "Recent Site", "video")

        val follows = listOf(makeFollow("video"))
        val bookmarks = emptyList<UserBookmark>()

        // When lastTs is null (first run), should only evaluate changes within the last 1 day (now - 86400)
        val result = UpdateWorkerLogic.evaluate(
            changes = listOf(oldChange, recentChange),
            follows = follows,
            bookmarks = bookmarks,
            lastTs = null,
            nowSec = nowSec,
        )

        assertNotNull(result)
        assertEquals("1 new site in followed categories", result!!.title)
        assertEquals("Recent Site", result.text)
        assertEquals(1, result.changeCount)
    }

    @Test
    fun testFirstRunNoSpamReturnsNullWhenNoRecentChanges() {
        val oldChange1 = makeChange(1, nowSec - 150000L, ChangeKind.ADDED, "Old Site 1", "video")
        val oldChange2 = makeChange(2, nowSec - 100000L, ChangeKind.ADDED, "Old Site 2", "video")

        val follows = listOf(makeFollow("video"))

        val result = UpdateWorkerLogic.evaluate(
            changes = listOf(oldChange1, oldChange2),
            follows = follows,
            bookmarks = emptyList(),
            lastTs = null,
            nowSec = nowSec,
        )

        assertNull(result)
    }

    @Test
    fun testSingleVsPluralFollowedChanges() {
        val follows = listOf(makeFollow("video"))

        // Single
        val singleChange = makeChange(1, nowSec - 100, ChangeKind.ADDED, "Site A", "video")
        val singleResult = UpdateWorkerLogic.evaluate(
            changes = listOf(singleChange),
            follows = follows,
            bookmarks = emptyList(),
            lastTs = nowSec - 1000,
            nowSec = nowSec,
        )
        assertNotNull(singleResult)
        assertEquals("1 new site in followed categories", singleResult!!.title)
        assertEquals("Site A", singleResult.text)
        assertEquals("following", singleResult.openFilter)

        // Plural (3 sites)
        val changes = listOf(
            makeChange(1, nowSec - 100, ChangeKind.ADDED, "Site A", "video"),
            makeChange(2, nowSec - 200, ChangeKind.ADDED, "Site B", "video"),
            makeChange(3, nowSec - 300, ChangeKind.STARRED, "Site C", "video"),
        )
        val pluralResult = UpdateWorkerLogic.evaluate(
            changes = changes,
            follows = follows,
            bookmarks = emptyList(),
            lastTs = nowSec - 1000,
            nowSec = nowSec,
        )
        assertNotNull(pluralResult)
        assertEquals("3 new sites in followed categories", pluralResult!!.title)
        assertTrue(pluralResult.text.contains("Site A"))
        assertTrue(pluralResult.text.contains("Site B"))
        assertTrue(pluralResult.text.contains("Site C"))
        assertEquals("following", pluralResult.openFilter)
    }

    @Test
    fun testSavedChangesSingleVsPlural() {
        val bookmarks = listOf(
            makeBookmark("e1", "Foo Site", "https://foo.com"),
            makeBookmark("e2", "Bar Site", "https://bar.com"),
        )

        // Single saved starred
        val savedStarred = makeChange(1, nowSec - 100, ChangeKind.STARRED, "Foo Site", "audio", url = "https://foo.com", entryId = "e1")
        val singleResult = UpdateWorkerLogic.evaluate(
            changes = listOf(savedStarred),
            follows = emptyList(),
            bookmarks = bookmarks,
            lastTs = nowSec - 1000,
            nowSec = nowSec,
            watchSaved = true,
        )
        assertNotNull(singleResult)
        assertEquals("Your saved site Foo Site was starred", singleResult!!.title)
        assertEquals("saved", singleResult.openFilter)

        // Plural saved changes
        val savedRemoved = makeChange(2, nowSec - 200, ChangeKind.REMOVED, "Bar Site", "audio", url = "https://bar.com", entryId = "e2")
        val pluralResult = UpdateWorkerLogic.evaluate(
            changes = listOf(savedStarred, savedRemoved),
            follows = emptyList(),
            bookmarks = bookmarks,
            lastTs = nowSec - 1000,
            nowSec = nowSec,
            watchSaved = true,
        )
        assertNotNull(pluralResult)
        assertEquals("FMHY: 2 saved sites changed", pluralResult!!.title)
        assertEquals("saved", pluralResult.openFilter)
    }

    @Test
    fun testWatchSavedDisabledIgnoresSaved() {
        val bookmarks = listOf(
            makeBookmark("e1", "Foo Site", "https://foo.com"),
        )
        val savedStarred = makeChange(1, nowSec - 100, ChangeKind.STARRED, "Foo Site", "audio", url = "https://foo.com", entryId = "e1")
        val result = UpdateWorkerLogic.evaluate(
            changes = listOf(savedStarred),
            follows = emptyList(),
            bookmarks = bookmarks,
            lastTs = nowSec - 1000,
            nowSec = nowSec,
            watchSaved = false,
        )
        assertNull(result)
    }

    private fun makeChange(
        id: Long,
        ts: Long,
        kind: ChangeKind,
        name: String,
        sectionId: String,
        url: String = "https://example.com",
        entryId: String = "e_$id",
    ): ChangeItem {
        return ChangeItem(
            id = id,
            ts = ts,
            kind = kind,
            entryId = entryId,
            sectionId = sectionId,
            name = name,
            url = url,
            detail = "",
            entry = null,
        )
    }
}
