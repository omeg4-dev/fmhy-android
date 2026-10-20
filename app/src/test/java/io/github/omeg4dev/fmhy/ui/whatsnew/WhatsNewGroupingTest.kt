package io.github.omeg4dev.fmhy.ui.whatsnew

import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewGroupingTest {

    @Test
    fun testTabKindsMapping() {
        val addedKinds = WhatsNewGrouping.tabKinds(WhatsNewTab.ADDED)
        assertEquals(setOf(ChangeKind.ADDED), addedKinds)

        val updatedKinds = WhatsNewGrouping.tabKinds(WhatsNewTab.UPDATED)
        assertEquals(
            setOf(
                ChangeKind.EDITED,
                ChangeKind.STARRED,
                ChangeKind.UNSTARRED,
                ChangeKind.URL_CHANGED,
                ChangeKind.RENAMED,
                ChangeKind.MOVED,
            ),
            updatedKinds
        )

        val removedKinds = WhatsNewGrouping.tabKinds(WhatsNewTab.REMOVED)
        assertEquals(setOf(ChangeKind.REMOVED), removedKinds)
    }

    @Test
    fun testDayGrouping() {
        val nowSec = 1760800000L // arbitrary fixed point

        val itemToday1 = makeChangeItem(1, nowSec - 3600, ChangeKind.ADDED, "Site A")
        val itemToday2 = makeChangeItem(2, nowSec - 7200, ChangeKind.ADDED, "Site B")
        val itemYesterday = makeChangeItem(3, nowSec - 90000, ChangeKind.ADDED, "Site C")
        val itemOlder = makeChangeItem(4, nowSec - 300000, ChangeKind.ADDED, "Site D")

        val groups = WhatsNewGrouping.groupByDay(
            listOf(itemToday1, itemToday2, itemYesterday, itemOlder),
            nowSec = nowSec
        )

        assertEquals(3, groups.size)
        assertEquals("Today", groups[0].dayHeader)
        assertEquals(listOf(itemToday1, itemToday2), groups[0].items)

        assertEquals("Yesterday", groups[1].dayHeader)
        assertEquals(listOf(itemYesterday), groups[1].items)

        // Older header should not be Today or Yesterday
        assertTrue(groups[2].dayHeader != "Today" && groups[2].dayHeader != "Yesterday")
        assertEquals(listOf(itemOlder), groups[2].items)
    }

    @Test
    fun testUpdateLineFormatting() {
        val starred = makeChangeItem(1, 1000, ChangeKind.STARRED, "StarSite", "https://star.com")
        assertEquals("★ Starred", WhatsNewGrouping.updateLine(starred))

        val unstarred = makeChangeItem(2, 1000, ChangeKind.UNSTARRED, "UnstarSite", "https://unstar.com")
        assertEquals("Unstarred", WhatsNewGrouping.updateLine(unstarred))

        val urlChanged = makeChangeItem(3, 1000, ChangeKind.URL_CHANGED, "ChangeSite", "https://newhost.org/sub")
        assertEquals("New link: newhost.org", WhatsNewGrouping.updateLine(urlChanged))

        val edited = makeChangeItem(4, 1000, ChangeKind.EDITED, "EditSite", "https://edit.com")
        assertEquals("Description changed", WhatsNewGrouping.updateLine(edited))

        val added = makeChangeItem(5, 1000, ChangeKind.ADDED, "AddSite", "https://add.com")
        assertNull(WhatsNewGrouping.updateLine(added))
    }

    private fun makeChangeItem(
        id: Long,
        ts: Long,
        kind: ChangeKind,
        name: String,
        url: String = "https://example.com"
    ): ChangeItem {
        return ChangeItem(
            id = id,
            ts = ts,
            kind = kind,
            entryId = "entry_$id",
            sectionId = "video",
            name = name,
            url = url,
            detail = "",
            entry = null,
        )
    }
}
