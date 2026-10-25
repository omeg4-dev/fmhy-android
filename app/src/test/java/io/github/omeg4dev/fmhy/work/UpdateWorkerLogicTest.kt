package io.github.omeg4dev.fmhy.work

import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Tier
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
            sectionTitles = mapOf("video" to "Movies / TV"),
        )

        assertNotNull(result)
        assertEquals("Movies / TV: 1 new", result!!.title)
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
            sectionTitles = mapOf("video" to "Video"),
        )
        assertNotNull(singleResult)
        assertEquals("Video: 1 new", singleResult!!.title)
        assertEquals("Site A", singleResult.text)
        assertEquals("following", singleResult.openFilter)

        // Plural (3 sites in single section)
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
            sectionTitles = mapOf("video" to "Video"),
        )
        assertNotNull(pluralResult)
        assertEquals("Video: 2 new, 1 starred", pluralResult!!.title)
        assertTrue(pluralResult.text.contains("Site A"))
        assertTrue(pluralResult.text.contains("Site B"))
        assertTrue(pluralResult.text.contains("Site C"))
        assertEquals("following", pluralResult.openFilter)

        // Multiple sections
        val multiSectionChanges = listOf(
            makeChange(1, nowSec - 100, ChangeKind.ADDED, "Site A", "video"),
            makeChange(2, nowSec - 200, ChangeKind.REMOVED, "Site B", "audio"),
        )
        val multiResult = UpdateWorkerLogic.evaluate(
            changes = multiSectionChanges,
            follows = listOf(makeFollow("video"), makeFollow("audio")),
            bookmarks = emptyList(),
            lastTs = nowSec - 1000,
            nowSec = nowSec,
        )
        assertNotNull(multiResult)
        assertEquals("1 new, 1 removed in followed sections", multiResult!!.title)
    }

    @Test
    fun testMixedKindsFollowedChanges() {
        val follows = listOf(makeFollow("video"))
        val changes = listOf(
            makeChange(1, nowSec - 100, ChangeKind.ADDED, "Site A", "video"),
            makeChange(2, nowSec - 200, ChangeKind.ADDED, "Site B", "video"),
            makeChange(3, nowSec - 300, ChangeKind.ADDED, "Site C", "video"),
            makeChange(4, nowSec - 400, ChangeKind.RENAMED, "Site D", "video"),
        )
        val result = UpdateWorkerLogic.evaluate(
            changes = changes,
            follows = follows,
            bookmarks = emptyList(),
            lastTs = nowSec - 1000,
            nowSec = nowSec,
            sectionTitles = mapOf("video" to "Movies / TV"),
        )
        assertNotNull(result)
        assertEquals("Movies / TV: 3 new, 1 renamed", result!!.title)
    }

    @Test
    fun testSavedChangesSingleVsPlural() {
        val bookmarks = listOf(
            makeBookmark("e1", "Foo Site", "https://foo.com"),
            makeBookmark("e2", "Bar Site", "https://bar.com"),
        )

        // Single saved starred
        val savedStarred = makeChange(1, nowSec - 100, ChangeKind.STARRED, "Foo Site", "audio", url = "https://foo.com", entryId = "e1")
        val singleStarredResult = UpdateWorkerLogic.evaluate(
            changes = listOf(savedStarred),
            follows = emptyList(),
            bookmarks = bookmarks,
            lastTs = nowSec - 1000,
            nowSec = nowSec,
            watchSaved = true,
        )
        assertNotNull(singleStarredResult)
        assertEquals("1 saved site was starred", singleStarredResult!!.title)
        assertEquals("saved", singleStarredResult.openFilter)

        // Single saved url changed
        val savedUrlChanged = makeChange(3, nowSec - 100, ChangeKind.URL_CHANGED, "Foo Site", "audio", url = "https://foo.com", entryId = "e1")
        val singleUrlResult = UpdateWorkerLogic.evaluate(
            changes = listOf(savedUrlChanged),
            follows = emptyList(),
            bookmarks = bookmarks,
            lastTs = nowSec - 1000,
            nowSec = nowSec,
            watchSaved = true,
        )
        assertNotNull(singleUrlResult)
        assertEquals("1 saved site got a new link", singleUrlResult!!.title)
        assertEquals("saved", singleUrlResult.openFilter)

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

    private fun makeEntry(id: String, sectionId: String, headingId: String?): Entry {
        return Entry(
            id = id,
            num = 1,
            sectionId = sectionId,
            headingId = headingId,
            ord = 1,
            name = id,
            url = "https://example.com/$id",
            originalUrl = null,
            description = "",
            tags = emptyList(),
            headingPath = "",
            tier = Tier.NORMAL,
            isInlineMore = false,
            isBase64 = false,
            mirrors = emptyList(),
            extraLinks = emptyList(),
            firstSeen = nowSec,
            lastChanged = nowSec,
            starredAt = null,
        )
    }

    private fun makeChangeWithHeading(
        id: Long,
        ts: Long,
        kind: ChangeKind,
        name: String,
        sectionId: String,
        headingId: String?,
    ): ChangeItem {
        return ChangeItem(
            id = id,
            ts = ts,
            kind = kind,
            entryId = "e_$id",
            sectionId = sectionId,
            name = name,
            url = "https://example.com",
            detail = "",
            entry = makeEntry("e_$id", sectionId, headingId),
        )
    }

    @Test
    fun testSectionFollowedWithExcludedSubsectionFiltersOutExcludedItem() {
        val follows = listOf(
            makeFollow("video", "", "Video"),
            Follow(sectionId = "video", headingId = "movies", title = "Movies", createdAt = nowSec, excluded = true),
        )
        val changeMovies = makeChangeWithHeading(1, nowSec - 100, ChangeKind.ADDED, "Movie Site", "video", "movies")
        val changeShows = makeChangeWithHeading(2, nowSec - 100, ChangeKind.ADDED, "Show Site", "video", "shows")

        val filtered = UpdateWorkerLogic.filterFollowedChanges(
            changes = listOf(changeMovies, changeShows),
            follows = follows,
        )

        assertEquals(1, filtered.size)
        assertEquals("Show Site", filtered[0].name)
    }

    @Test
    fun testExcludedSubsectionDescendantsFilteredViaHierarchy() {
        val follows = listOf(
            makeFollow("video", "", "Video"),
            Follow(sectionId = "video", headingId = "parent-cat", title = "Parent Cat", createdAt = nowSec, excluded = true),
        )
        val hierarchy = mapOf(
            "parent-cat" to setOf("parent-cat", "child-sub1", "child-sub2")
        )

        val changeChild1 = makeChangeWithHeading(1, nowSec - 100, ChangeKind.ADDED, "Child 1 Site", "video", "child-sub1")
        val changeChild2 = makeChangeWithHeading(2, nowSec - 100, ChangeKind.ADDED, "Child 2 Site", "video", "child-sub2")
        val changeOther = makeChangeWithHeading(3, nowSec - 100, ChangeKind.ADDED, "Other Site", "video", "other-cat")

        val filtered = UpdateWorkerLogic.filterFollowedChanges(
            changes = listOf(changeChild1, changeChild2, changeOther),
            follows = follows,
            hierarchy = hierarchy,
        )

        assertEquals(1, filtered.size)
        assertEquals("Other Site", filtered[0].name)
    }

    @Test
    fun testExcludedSubsectionWithoutSectionFollowIgnored() {
        // Section not followed, only an excluded row exists -> neither counted as follow nor kept
        val follows = listOf(
            Follow(sectionId = "video", headingId = "movies", title = "Movies", createdAt = nowSec, excluded = true),
        )
        val changeMovies = makeChangeWithHeading(1, nowSec - 100, ChangeKind.ADDED, "Movie Site", "video", "movies")

        val filtered = UpdateWorkerLogic.filterFollowedChanges(
            changes = listOf(changeMovies),
            follows = follows,
        )

        assertTrue(filtered.isEmpty())
    }
}
