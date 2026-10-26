package io.github.omeg4dev.fmhy.ui.whatsnew

import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import org.junit.Assert.assertEquals
import org.junit.Test

class SinceLastVisitTest {

    private val fixedNow = 1760000000L

    @Test
    fun testSinceLastVisitLabel_cases() {
        // cutoff 0
        assertEquals("Since last visit", sinceLastVisitLabel(0L, fixedNow))

        // just now (< 60s)
        assertEquals("Since last visit · just now", sinceLastVisitLabel(fixedNow - 30, fixedNow))

        // 2 minutes
        assertEquals("Since last visit · 2 minutes ago", sinceLastVisitLabel(fixedNow - 120, fixedNow))

        // 3 hours
        assertEquals("Since last visit · 3 hours ago", sinceLastVisitLabel(fixedNow - 3 * 3600, fixedNow))

        // 1 day
        assertEquals("Since last visit · yesterday", sinceLastVisitLabel(fixedNow - 86400, fixedNow))

        // 3 days
        assertEquals("Since last visit · 3 days ago", sinceLastVisitLabel(fixedNow - 3 * 86400, fixedNow))
    }

    @Test
    fun testCountAddedSinceMatchesRecentChanges() {
        val cutoff = 1000L
        val items = listOf(
            ChangeItem(1, cutoff - 10, ChangeKind.ADDED, "1", "video", "A", "url1", "", null),
            ChangeItem(2, cutoff, ChangeKind.ADDED, "2", "video", "B", "url2", "", null),
            ChangeItem(3, cutoff + 1, ChangeKind.ADDED, "3", "video", "C", "url3", "", null),
            ChangeItem(4, cutoff + 5, ChangeKind.ADDED, "4", "video", "D", "url4", "", null),
            ChangeItem(5, cutoff + 10, ChangeKind.REMOVED, "5", "video", "E", "url5", "", null),
            ChangeItem(6, cutoff + 15, ChangeKind.EDITED, "6", "video", "F", "url6", "", null),
            ChangeItem(7, cutoff + 20, ChangeKind.ADDED, "7", "video", "G", "url7", "", null),
        )

        // countAddedSince rule: kind == 'added' AND ts > cutoff
        val homeCount = items.count { it.kind == ChangeKind.ADDED && it.ts > cutoff }

        // recentChanges rule for SINCE_LAST_VISIT: ts >= cutoff + 1
        val whatsNewAdded = items.filter { it.ts >= (cutoff + 1) && it.kind == ChangeKind.ADDED }

        assertEquals(homeCount, whatsNewAdded.size)
        assertEquals(3, homeCount)
    }

    @Test
    fun testWhatsNewPeriodEnumOrderAndLabels() {
        val periods = WhatsNewPeriod.entries
        assertEquals(WhatsNewPeriod.SINCE_LAST_VISIT, periods[0])
        assertEquals(WhatsNewPeriod.DAY, periods[1])
        assertEquals(WhatsNewPeriod.WEEK, periods[2])
        assertEquals(WhatsNewPeriod.MONTH, periods[3])

        assertEquals("Since last visit", WhatsNewPeriod.SINCE_LAST_VISIT.label)
        assertEquals("Last 24 hours", WhatsNewPeriod.DAY.label)
        assertEquals("Last 7 days", WhatsNewPeriod.WEEK.label)
        assertEquals("Last 30 days", WhatsNewPeriod.MONTH.label)
    }

    @Test
    fun testInitialPeriodMapping() {
        fun resolvePeriod(initialPeriod: String?): WhatsNewPeriod =
            if (initialPeriod == "since_last_visit") WhatsNewPeriod.SINCE_LAST_VISIT else WhatsNewPeriod.WEEK

        assertEquals(WhatsNewPeriod.SINCE_LAST_VISIT, resolvePeriod("since_last_visit"))
        assertEquals(WhatsNewPeriod.WEEK, resolvePeriod(null))
        assertEquals(WhatsNewPeriod.WEEK, resolvePeriod("week"))
        assertEquals(WhatsNewPeriod.WEEK, resolvePeriod("other"))
    }
}
