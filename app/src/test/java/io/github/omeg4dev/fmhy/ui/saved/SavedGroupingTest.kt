package io.github.omeg4dev.fmhy.ui.saved

import io.github.omeg4dev.fmhy.data.Bookmark
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.user.HistoryItem
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedGroupingTest {

    private val zone = ZoneId.of("UTC")

    private fun sampleBookmark(
        id: String,
        name: String,
        sectionId: String = "video",
        sectionTitle: String = "Video",
        headingPath: String = "Video > Streaming",
        savedAt: Long = 1000L,
        folderId: Long? = null,
        note: String = "",
    ): Bookmark = Bookmark(
        entryId = id,
        name = name,
        url = "https://example.com/$id",
        sectionId = sectionId,
        sectionTitle = sectionTitle,
        headingPath = headingPath,
        savedAt = savedAt,
        folderId = folderId,
        note = note,
    )

    private fun sampleEntry(
        id: String,
        name: String,
        sectionId: String = "video",
        headingId: String? = "h1",
        headingPath: String = "Video > Streaming",
        tier: Tier = Tier.NORMAL,
        ord: Int = 1,
    ): Entry = Entry(
        num = 1,
        id = id,
        sectionId = sectionId,
        headingId = headingId,
        ord = ord,
        name = name,
        url = "https://example.com/$id",
        originalUrl = null,
        description = "Desc",
        tags = emptyList(),
        headingPath = headingPath,
        tier = tier,
        isInlineMore = false,
        isBase64 = false,
        mirrors = emptyList(),
        extraLinks = emptyList(),
        firstSeen = 1000L,
        lastChanged = 1000L,
        starredAt = null,
    )

    @Test
    fun testAllGroupingBySection() {
        val b1 = sampleBookmark("b1", "Site 1", sectionTitle = "Audio", savedAt = 100L)
        val b2 = sampleBookmark("b2", "Site 2", sectionTitle = "Video", savedAt = 200L, folderId = 1L)
        val b3 = sampleBookmark("b3", "Site 3", sectionTitle = "Video", savedAt = 300L)

        val folderMap = mapOf(1L to "Favorites")
        val groups = SavedGrouping.groupBookmarks(
            bookmarks = listOf(b1, b2, b3),
            liveEntriesMap = emptyMap(),
            folderMap = folderMap,
        )

        // Video section had newest item at 300L, Audio at 100L -> Video first
        assertEquals(2, groups.size)
        assertEquals("Video", groups[0].sectionTitle)
        assertEquals(2, groups[0].items.size)
        assertEquals("Favorites", groups[0].items[0].folderName)

        assertEquals("Audio", groups[1].sectionTitle)
        assertEquals(1, groups[1].items.size)
    }

    @Test
    fun testFolderFilteringAndGrouping() {
        val b1 = sampleBookmark("b1", "Site 1", sectionTitle = "Video", folderId = 1L)
        val b2 = sampleBookmark("b2", "Site 2", sectionTitle = "Video", folderId = 2L)
        val b3 = sampleBookmark("b3", "Site 3", sectionTitle = "Audio", folderId = 1L)

        val bookmarks = listOf(b1, b2, b3)
        val folder1Bookmarks = bookmarks.filter { it.folderId == 1L }
        val groups = SavedGrouping.groupBookmarks(
            bookmarks = folder1Bookmarks,
            liveEntriesMap = emptyMap(),
        )

        assertEquals(2, groups.size)
        val allIds = groups.flatMap { it.items }.map { it.bookmark.entryId }.toSet()
        assertEquals(setOf("b1", "b3"), allIds)
    }

    @Test
    fun testHistoryGroupingByDay() {
        val fixedNow = LocalDate.of(2026, 10, 20).atStartOfDay(zone).toInstant().toEpochMilli() + 3600_000L // 2026-10-20 01:00 UTC
        val todayTs = fixedNow - 1800_000L // 2026-10-20 00:30 UTC
        val yesterdayTs = fixedNow - 86400_000L // 2026-10-19 01:00 UTC
        val olderTs = LocalDate.of(2026, 10, 18).atStartOfDay(zone).toInstant().toEpochMilli() // 2026-10-18 00:00 UTC

        val items = listOf(
            HistoryItem(id = 1, url = "https://example.com/page", entryId = null, title = "Example", ts = todayTs),
            HistoryItem(id = 2, url = "https://yt.com", entryId = "e_yt", title = "YouTube", ts = yesterdayTs),
            HistoryItem(id = 3, url = "https://old.com", entryId = null, title = "Old", ts = olderTs),
        )

        val ytEntry = sampleEntry("e_yt", "YouTube")
        val groups = SavedGrouping.groupHistory(
            items = items,
            liveEntriesMap = mapOf("e_yt" to ytEntry),
            nowMs = fixedNow,
            zoneId = zone,
        )

        assertEquals(3, groups.size)
        assertEquals("Today", groups[0].dayLabel)
        assertEquals("Yesterday", groups[1].dayLabel)
        assertEquals("18 Oct", groups[2].dayLabel)

        assertEquals(ytEntry, groups[1].items[0].liveEntry)
        assertEquals("yt.com", groups[1].items[0].host)
    }

    @Test
    fun testRemovedDateFormatting() {
        // 2026-10-18 12:00:00 UTC
        val tsSec = 1792324800L
        val formatted = SavedGrouping.formatRemovedDate(tsSec, zone)
        assertEquals("Removed on 18 Oct", formatted)
    }

    @Test
    fun testFindReplacementsExactHeadingPathStarredFirst() {
        val removed = sampleBookmark("rem1", "Old Streaming", headingPath = "Video > Streaming")

        val e1 = sampleEntry("e1", "Normal Streaming 1", headingPath = "Video > Streaming", tier = Tier.NORMAL, ord = 1)
        val e2 = sampleEntry("e2", "Starred Streaming", headingPath = "Video > Streaming", tier = Tier.STAR, ord = 5)
        val e3 = sampleEntry("e3", "Top Streaming", headingPath = "Video > Streaming", tier = Tier.TOP, ord = 10)
        val e4 = sampleEntry("e4", "Other Section Entry", headingPath = "Video > Download", tier = Tier.TOP, ord = 2)

        val replacements = SavedGrouping.findReplacements(
            bookmark = removed,
            sectionEntries = listOf(e1, e2, e3, e4),
        )

        assertEquals(3, replacements.size)
        // Starred/Top should be first
        assertTrue(replacements[0].tier == Tier.TOP || replacements[0].tier == Tier.STAR)
        assertTrue(replacements[1].tier == Tier.TOP || replacements[1].tier == Tier.STAR)
        assertEquals(Tier.NORMAL, replacements[2].tier)
    }

    @Test
    fun testFindReplacementsLeafCategoryFallback() {
        val removed = sampleBookmark("rem1", "Old Tool", headingPath = "Audio › Production › Tools")

        val e1 = sampleEntry("e1", "Tool A", headingPath = "Audio › Sound › Tools", tier = Tier.NORMAL)
        val e2 = sampleEntry("e2", "Synth", headingPath = "Audio › Instruments › Synths", tier = Tier.STAR)

        val replacements = SavedGrouping.findReplacements(
            bookmark = removed,
            sectionEntries = listOf(e1, e2),
            headings = listOf(Heading("h_tools", "audio", null, 2, "Tools", "tools", 10)),
        )

        assertEquals(1, replacements.size)
        assertEquals("e1", replacements[0].id)
    }
}
