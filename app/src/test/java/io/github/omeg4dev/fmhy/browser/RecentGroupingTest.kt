package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.user.HistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentGroupingTest {

    @Test
    fun testSiteKey() {
        assertEquals("example.com", siteKey("https://www.Example.com/a?b"))
        assertEquals("m.example.com", siteKey("http://m.example.com"))
        assertEquals("example.org", siteKey("http://example.org:8080/path"))
        assertNull(siteKey("about:blank"))
        assertNull(siteKey(""))
        assertNull(siteKey("   "))
        assertNull(siteKey("data:text/html,x"))
        assertNull(siteKey("javascript:alert(1)"))
        assertNull(siteKey("file:///android_asset/test.html"))
        assertNull(siteKey("not a url"))
        assertNull(siteKey("http://"))
        assertNull(siteKey("https:///path"))
        assertNull(siteKey("http://www."))
    }

    @Test
    fun testSameHostTwiceMergesWithBothIdsAndNewerData() {
        val history = listOf(
            HistoryItem(id = 1, url = "https://example.com/old", entryId = null, title = "Old Title", ts = 1000L),
            HistoryItem(id = 2, url = "https://example.com/new", entryId = null, title = "New Title", ts = 2000L),
        )
        val grouped = groupRecent(history, limit = 5)
        assertEquals(1, grouped.size)
        val item = grouped[0]
        assertEquals("example.com", item.key)
        assertEquals("example.com", item.host)
        assertEquals("New Title", item.title)
        assertEquals("https://example.com/new", item.url)
        assertEquals(2000L, item.lastVisit)
        assertEquals(listOf(1L, 2L), item.historyIds)
    }

    @Test
    fun testWwwAndNonWwwMerge() {
        val history = listOf(
            HistoryItem(id = 10, url = "https://www.example.com/1", entryId = null, title = "WWW Page", ts = 3000L),
            HistoryItem(id = 11, url = "https://example.com/2", entryId = null, title = "Non-WWW Page", ts = 4000L),
        )
        val grouped = groupRecent(history, limit = 5)
        assertEquals(1, grouped.size)
        val item = grouped[0]
        assertEquals("example.com", item.key)
        assertEquals("example.com", item.host)
        assertEquals("Non-WWW Page", item.title)
        assertEquals("https://example.com/2", item.url)
        assertEquals(4000L, item.lastVisit)
        assertEquals(listOf(10L, 11L), item.historyIds)
    }

    @Test
    fun testUnsortedInputIsSortedNewestFirst() {
        val history = listOf(
            HistoryItem(id = 1, url = "https://site-old.org", entryId = null, title = "Old Site", ts = 500L),
            HistoryItem(id = 2, url = "https://site-new.org", entryId = null, title = "New Site", ts = 5000L),
            HistoryItem(id = 3, url = "https://site-mid.org", entryId = null, title = "Mid Site", ts = 2500L),
        )
        val grouped = groupRecent(history, limit = 5)
        assertEquals(3, grouped.size)
        assertEquals("site-new.org", grouped[0].key)
        assertEquals("site-mid.org", grouped[1].key)
        assertEquals("site-old.org", grouped[2].key)
    }

    @Test
    fun testLimitHonoredEvenWhenOneSiteHas50Rows() {
        val history = mutableListOf<HistoryItem>()
        // Site A has 50 rows
        for (i in 1..50) {
            history.add(
                HistoryItem(id = i.toLong(), url = "https://popular.com/page$i", entryId = null, title = "Pop $i", ts = 1000L + i)
            )
        }
        // Site B, C, D have rows
        history.add(HistoryItem(id = 51, url = "https://second.com", entryId = null, title = "Second", ts = 900L))
        history.add(HistoryItem(id = 52, url = "https://third.com", entryId = null, title = "Third", ts = 800L))
        history.add(HistoryItem(id = 53, url = "https://fourth.com", entryId = null, title = "Fourth", ts = 700L))

        val grouped = groupRecent(history, limit = 3)
        assertEquals(3, grouped.size)
        assertEquals("popular.com", grouped[0].key)
        assertEquals(50, grouped[0].historyIds.size)
        assertEquals("second.com", grouped[1].key)
        assertEquals("third.com", grouped[2].key)
    }

    @Test
    fun testBlankAboutDataRowsSkipped() {
        val history = listOf(
            HistoryItem(id = 1, url = "about:blank", entryId = null, title = "Blank", ts = 5000L),
            HistoryItem(id = 2, url = "data:text/html,<h1>Hi</h1>", entryId = null, title = "Data", ts = 4000L),
            HistoryItem(id = 3, url = "", entryId = null, title = "Empty", ts = 3000L),
            HistoryItem(id = 4, url = "javascript:void(0)", entryId = null, title = "JS", ts = 2000L),
            HistoryItem(id = 5, url = "https://valid.com", entryId = null, title = "Valid", ts = 1000L),
        )
        val grouped = groupRecent(history, limit = 5)
        assertEquals(1, grouped.size)
        assertEquals("valid.com", grouped[0].key)
    }

    @Test
    fun testBlankTitleFallsBackToHost() {
        val history = listOf(
            HistoryItem(id = 1, url = "https://notitle.org/abc", entryId = null, title = "", ts = 1000L),
            HistoryItem(id = 2, url = "https://blanktitle.com", entryId = null, title = "   ", ts = 1200L),
        )
        val grouped = groupRecent(history, limit = 5)
        assertEquals(2, grouped.size)
        assertEquals("blanktitle.com", grouped[0].title)
        assertEquals("notitle.org", grouped[1].title)
    }

    @Test
    fun testTiesBrokenByHigherId() {
        val history = listOf(
            HistoryItem(id = 10, url = "https://site-lower-id.com", entryId = null, title = "Lower", ts = 1000L),
            HistoryItem(id = 20, url = "https://site-higher-id.com", entryId = null, title = "Higher", ts = 1000L),
        )
        val grouped = groupRecent(history, limit = 5)
        assertEquals(2, grouped.size)
        assertEquals("site-higher-id.com", grouped[0].key)
        assertEquals("site-lower-id.com", grouped[1].key)
    }

    @Test
    fun testTiesBrokenWithinSameSiteByHigherId() {
        val history = listOf(
            HistoryItem(id = 10, url = "https://example.com/first", entryId = null, title = "First", ts = 1000L),
            HistoryItem(id = 20, url = "https://example.com/second", entryId = null, title = "Second", ts = 1000L),
        )
        val grouped = groupRecent(history, limit = 5)
        assertEquals(1, grouped.size)
        assertEquals("Second", grouped[0].title)
        assertEquals("https://example.com/second", grouped[0].url)
    }
}
