package io.github.omeg4dev.fmhy.browser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsViewTest {

    private fun createItem(
        id: Long = 1L,
        fileName: String = "test.zip",
        sourceHost: String = "example.com",
        status: DownloadStatus = DownloadStatus.FINISHED,
        totalBytes: Long = 1000L,
        downloadedBytes: Long = 1000L,
        createdAt: Long = 100L,
    ): DownloadItem {
        return DownloadItem(
            id = id,
            url = "https://$sourceHost/$fileName",
            fileName = fileName,
            sourceHost = sourceHost,
            status = status,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            createdAt = createdAt,
        )
    }

    @Test
    fun testFilterAllMatchesAllStatuses() {
        for (status in DownloadStatus.entries) {
            val item = createItem(status = status)
            assertTrue("Expected ALL to match status $status", DownloadsView.matchesFilter(item, DownloadFilter.ALL))
        }
    }

    @Test
    fun testFilterActiveMatchesOnlyActiveStatuses() {
        val activeStatuses = setOf(
            DownloadStatus.WAITING,
            DownloadStatus.WAITING_WIFI,
            DownloadStatus.RUNNING,
            DownloadStatus.PAUSED,
            DownloadStatus.CHECKING,
            DownloadStatus.AWAITING_DECISION,
        )
        for (status in DownloadStatus.entries) {
            val item = createItem(status = status)
            val matched = DownloadsView.matchesFilter(item, DownloadFilter.ACTIVE)
            if (status in activeStatuses) {
                assertTrue("Expected ACTIVE to match $status", matched)
            } else {
                assertFalse("Expected ACTIVE not to match $status", matched)
            }
        }
    }

    @Test
    fun testFilterDoneMatchesFinishedOnly() {
        for (status in DownloadStatus.entries) {
            val item = createItem(status = status)
            val matched = DownloadsView.matchesFilter(item, DownloadFilter.DONE)
            if (status == DownloadStatus.FINISHED) {
                assertTrue("Expected DONE to match FINISHED", matched)
            } else {
                assertFalse("Expected DONE not to match $status", matched)
            }
        }
    }

    @Test
    fun testFilterFailedMatchesFailedOnly() {
        for (status in DownloadStatus.entries) {
            val item = createItem(status = status)
            val matched = DownloadsView.matchesFilter(item, DownloadFilter.FAILED)
            if (status == DownloadStatus.FAILED) {
                assertTrue("Expected FAILED to match FAILED", matched)
            } else {
                assertFalse("Expected FAILED not to match $status", matched)
            }
        }
    }

    @Test
    fun testCountsWithAllKeysIncludingZero() {
        val emptyList = emptyList<DownloadItem>()
        val emptyCounts = DownloadsView.counts(emptyList)
        assertEquals(4, emptyCounts.size)
        assertEquals(0, emptyCounts[DownloadFilter.ALL])
        assertEquals(0, emptyCounts[DownloadFilter.ACTIVE])
        assertEquals(0, emptyCounts[DownloadFilter.DONE])
        assertEquals(0, emptyCounts[DownloadFilter.FAILED])

        val items = listOf(
            createItem(id = 1, status = DownloadStatus.RUNNING),
            createItem(id = 2, status = DownloadStatus.WAITING),
            createItem(id = 3, status = DownloadStatus.FINISHED),
            createItem(id = 4, status = DownloadStatus.FAILED),
        )
        val counts = DownloadsView.counts(items)
        assertEquals(4, counts[DownloadFilter.ALL])
        assertEquals(2, counts[DownloadFilter.ACTIVE])
        assertEquals(1, counts[DownloadFilter.DONE])
        assertEquals(1, counts[DownloadFilter.FAILED])
    }

    @Test
    fun testSearchByFileNameCaseInsensitive() {
        val items = listOf(
            createItem(id = 1, fileName = "Document.PDF", createdAt = 200L),
            createItem(id = 2, fileName = "image.png", createdAt = 150L),
            createItem(id = 3, fileName = "archive.doc.zip", createdAt = 100L),
        )
        val result = DownloadsView.apply(items, DownloadFilter.ALL, "doc", DownloadSort.NEWEST)
        assertEquals(listOf(1L, 3L), result.map { it.id })
    }

    @Test
    fun testSearchBySourceHost() {
        val items = listOf(
            createItem(id = 1, fileName = "file1.zip", sourceHost = "github.com", createdAt = 200L),
            createItem(id = 2, fileName = "file2.zip", sourceHost = "gitlab.com", createdAt = 100L),
            createItem(id = 3, fileName = "file3.zip", sourceHost = "example.com", createdAt = 50L),
        )
        val result = DownloadsView.apply(items, DownloadFilter.ALL, "git", DownloadSort.NEWEST)
        assertEquals(listOf(1L, 2L), result.map { it.id })
    }

    @Test
    fun testSearchTrimmedAndBlank() {
        val items = listOf(
            createItem(id = 1, fileName = "alpha.txt"),
            createItem(id = 2, fileName = "beta.txt"),
        )
        // Blank query matches all
        val blankResult = DownloadsView.apply(items, DownloadFilter.ALL, "   ", DownloadSort.NEWEST)
        assertEquals(2, blankResult.size)

        // Trimmed query works
        val trimmedResult = DownloadsView.apply(items, DownloadFilter.ALL, "  alpha  ", DownloadSort.NEWEST)
        assertEquals(1, trimmedResult.size)
        assertEquals("alpha.txt", trimmedResult.first().fileName)
    }

    @Test
    fun testSearchNoMatchReturnsEmpty() {
        val items = listOf(
            createItem(id = 1, fileName = "photo.jpg", sourceHost = "photos.google.com"),
        )
        val result = DownloadsView.apply(items, DownloadFilter.ALL, "nonexistent", DownloadSort.NEWEST)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testSortNewestWithTies() {
        val items = listOf(
            createItem(id = 10, createdAt = 1000L),
            createItem(id = 20, createdAt = 2000L),
            createItem(id = 15, createdAt = 2000L), // tie at 2000L, id 20 > id 15
            createItem(id = 5, createdAt = 500L),
        )
        val result = DownloadsView.apply(items, DownloadFilter.ALL, "", DownloadSort.NEWEST)
        assertEquals(listOf(20L, 15L, 10L, 5L), result.map { it.id })
    }

    @Test
    fun testSortNameCaseInsensitiveWithTies() {
        val items = listOf(
            createItem(id = 1, fileName = "charlie.zip"),
            createItem(id = 2, fileName = "Alpha.zip"),
            createItem(id = 5, fileName = "alpha.zip"), // tie on case-insensitive name, id 5 > id 2
            createItem(id = 3, fileName = "bravo.zip"),
        )
        val result = DownloadsView.apply(items, DownloadFilter.ALL, "", DownloadSort.NAME)
        assertEquals(listOf(5L, 2L, 3L, 1L), result.map { it.id })
    }

    @Test
    fun testSortSizeWithFallbackAndTies() {
        val items = listOf(
            createItem(id = 1, totalBytes = 500L, downloadedBytes = 500L),
            createItem(id = 2, totalBytes = -1L, downloadedBytes = 1000L), // totalBytes <= 0 falls back to downloadedBytes
            createItem(id = 3, totalBytes = 1000L, downloadedBytes = 200L), // 1000L, ties with item 2 (size 1000L)
            createItem(id = 4, totalBytes = 100L, downloadedBytes = 100L),
        )
        // Item 3 and 2 both have size 1000L -> tie break by id descending -> id 3, then id 2
        val result = DownloadsView.apply(items, DownloadFilter.ALL, "", DownloadSort.SIZE)
        assertEquals(listOf(3L, 2L, 1L, 4L), result.map { it.id })
    }

    @Test
    fun testFilterSearchAndSortCombined() {
        val items = listOf(
            createItem(id = 1, fileName = "android-app.apk", sourceHost = "fdroid.org", status = DownloadStatus.FINISHED, totalBytes = 200L, createdAt = 10L),
            createItem(id = 2, fileName = "android-tool.zip", sourceHost = "github.com", status = DownloadStatus.RUNNING, totalBytes = 500L, createdAt = 20L),
            createItem(id = 3, fileName = "ios-tool.zip", sourceHost = "github.com", status = DownloadStatus.RUNNING, totalBytes = 400L, createdAt = 30L),
            createItem(id = 4, fileName = "android-lib.aar", sourceHost = "maven.org", status = DownloadStatus.RUNNING, totalBytes = 100L, createdAt = 40L),
            createItem(id = 5, fileName = "other.txt", sourceHost = "github.com", status = DownloadStatus.FAILED, totalBytes = 50L, createdAt = 50L),
        )
        // Filter: ACTIVE (items 2, 3, 4)
        // Search: "android" (items 2, 4)
        // Sort: SIZE descending -> item 2 (500L), then item 4 (100L)
        val result = DownloadsView.apply(items, DownloadFilter.ACTIVE, "android", DownloadSort.SIZE)
        assertEquals(listOf(2L, 4L), result.map { it.id })
    }

    @Test
    fun test150ItemsDoesNotCrashAndKeepsOrder() {
        val items = (1..150).map { i ->
            createItem(
                id = i.toLong(),
                fileName = "file_$i.bin",
                sourceHost = if (i % 2 == 0) "even.com" else "odd.com",
                status = if (i % 3 == 0) DownloadStatus.FINISHED else DownloadStatus.RUNNING,
                totalBytes = i * 1000L,
                createdAt = i * 10L,
            )
        }
        val result = DownloadsView.apply(items, DownloadFilter.ALL, "", DownloadSort.NEWEST)
        assertEquals(150, result.size)
        // NEWEST: createdAt descending
        for (i in 0 until 149) {
            assertTrue(result[i].createdAt >= result[i + 1].createdAt)
        }
    }
}
