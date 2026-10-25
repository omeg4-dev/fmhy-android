package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.adblock.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedHostsTest {

    @Test
    fun emptyRequestsReturnsEmptyList() {
        val result = BlockedHosts.group(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun groupingCountsPerHostAndCaseFolding() {
        val requests = listOf(
            BlockedRequest(host = "Tracker.Com", url = "https://Tracker.Com/a", type = ResourceType.SCRIPT, rule = "||tracker.com^"),
            BlockedRequest(host = "tracker.com", url = "https://tracker.com/b", type = ResourceType.IMAGE, rule = "||tracker.com^"),
            BlockedRequest(host = "TRACKER.COM", url = "https://TRACKER.COM/c", type = ResourceType.XHR, rule = "||tracker.com^"),
            BlockedRequest(host = "ads.net", url = "https://ads.net/banner", type = ResourceType.IMAGE, rule = "||ads.net^"),
        )
        val result = BlockedHosts.group(requests)
        assertEquals(2, result.size)
        assertEquals(BlockedHost("tracker.com", 3), result[0])
        assertEquals(BlockedHost("ads.net", 1), result[1])
    }

    @Test
    fun orderByCountDescThenHostAsc() {
        val requests = listOf(
            BlockedRequest(host = "b.com", url = "https://b.com/1", type = ResourceType.SCRIPT, rule = "rule"),
            BlockedRequest(host = "b.com", url = "https://b.com/2", type = ResourceType.SCRIPT, rule = "rule"),
            BlockedRequest(host = "a.com", url = "https://a.com/1", type = ResourceType.SCRIPT, rule = "rule"),
            BlockedRequest(host = "a.com", url = "https://a.com/2", type = ResourceType.SCRIPT, rule = "rule"),
            BlockedRequest(host = "c.com", url = "https://c.com/1", type = ResourceType.SCRIPT, rule = "rule"),
            BlockedRequest(host = "c.com", url = "https://c.com/2", type = ResourceType.SCRIPT, rule = "rule"),
            BlockedRequest(host = "c.com", url = "https://c.com/3", type = ResourceType.SCRIPT, rule = "rule"),
            BlockedRequest(host = "z.com", url = "https://z.com/1", type = ResourceType.SCRIPT, rule = "rule"),
        )
        val result = BlockedHosts.group(requests)
        // c.com has count 3
        // a.com and b.com have count 2 (tie-broken by host asc: a.com before b.com)
        // z.com has count 1
        assertEquals(
            listOf(
                BlockedHost("c.com", 3),
                BlockedHost("a.com", 2),
                BlockedHost("b.com", 2),
                BlockedHost("z.com", 1),
            ),
            result
        )
    }
}
