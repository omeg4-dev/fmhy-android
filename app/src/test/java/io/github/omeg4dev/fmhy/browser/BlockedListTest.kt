package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedListTest {

    @Test
    fun testRowText() {
        assertEquals("Nothing blocked on this page", BlockedList.rowText(0))
        assertEquals("1 blocked on this page", BlockedList.rowText(1))
        assertEquals("2 blocked on this page", BlockedList.rowText(2))
        assertEquals("42 blocked on this page", BlockedList.rowText(42))
    }

    @Test
    fun testSorted() {
        val pageHost = "example.com"
        val hosts = listOf(
            BlockedHost("thirdparty.org", 50),
            BlockedHost("sub.example.com", 2),
            BlockedHost("example.com", 5),
            BlockedHost("another.com", 10),
            BlockedHost("b-other.com", 10),
            BlockedHost("a-other.com", 10),
            BlockedHost("api.example.com", 5),
        )

        val sorted = BlockedList.sorted(hosts, pageHost)

        assertEquals("api.example.com", sorted[0].host)
        assertEquals(5, sorted[0].count)
        assertEquals("example.com", sorted[1].host)
        assertEquals(5, sorted[1].count)
        assertEquals("sub.example.com", sorted[2].host)
        assertEquals(2, sorted[2].count)
        assertEquals("thirdparty.org", sorted[3].host)
        assertEquals(50, sorted[3].count)
        assertEquals("a-other.com", sorted[4].host)
    }

    @Test
    fun testSortedCaseInsensitiveTieBreak() {
        val hosts = listOf(
            BlockedHost("Z.com", 5),
            BlockedHost("a.com", 5),
            BlockedHost("B.com", 5),
        )
        val sorted = BlockedList.sorted(hosts, pageHost = null)
        assertEquals(listOf("a.com", "B.com", "Z.com"), sorted.map { it.host })
    }

    @Test
    fun testFilter() {
        val hosts = listOf(
            BlockedHost("tracker.ads.net", 3),
            BlockedHost("metrics.example.com", 2),
            BlockedHost("static.cdn.org", 1),
        )

        assertEquals(hosts, BlockedList.filter(hosts, ""))
        assertEquals(hosts, BlockedList.filter(hosts, "   "))

        val filteredAds = BlockedList.filter(hosts, "ADS")
        assertEquals(listOf(BlockedHost("tracker.ads.net", 3)), filteredAds)

        val filteredCom = BlockedList.filter(hosts, ".COM")
        assertEquals(listOf(BlockedHost("metrics.example.com", 2)), filteredCom)

        val none = BlockedList.filter(hosts, "nomatch")
        assertTrue(none.isEmpty())
    }

    @Test
    fun testShowSearch() {
        assertFalse(BlockedList.showSearch(0))
        assertFalse(BlockedList.showSearch(15))
        assertTrue(BlockedList.showSearch(16))
        assertTrue(BlockedList.showSearch(30))
    }

    @Test
    fun testMiddleEllipsis() {
        assertEquals("example.com", BlockedList.middleEllipsis("example.com", 32))
        assertEquals("short", BlockedList.middleEllipsis("short", 10))

        val longHost = "ads.verylongtrackerdomainnamehere.example.com"
        val result32 = BlockedList.middleEllipsis(longHost, 32)
        assertTrue(result32.length <= 32)
        assertTrue(result32.contains("…"))
        assertTrue(result32.endsWith(".example.com"))

        val hostWithTld = "verylongsubdomaintracker.co.uk"
        val result20 = BlockedList.middleEllipsis(hostWithTld, 20)
        assertTrue(result20.length <= 20)
        assertTrue(result20.endsWith("uk"))
        assertTrue(result20.contains("…"))

        val museumHost = "verylongsubdomaintracker.museum"
        val resultMuseum = BlockedList.middleEllipsis(museumHost, 18)
        assertTrue(resultMuseum.length <= 18)
        assertTrue(resultMuseum.endsWith(".museum"))
    }
}
