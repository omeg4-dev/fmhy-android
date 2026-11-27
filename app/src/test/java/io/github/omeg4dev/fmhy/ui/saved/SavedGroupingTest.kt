package io.github.omeg4dev.fmhy.ui.saved

import io.github.omeg4dev.fmhy.data.Bookmark
import io.github.omeg4dev.fmhy.data.SavedSort
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.user.HistoryItem
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun testBuildItemsPopulatesLiveEntryFolderAndRemovedDate() {
        val b1 = sampleBookmark("b1", "Live Site", folderId = 1L)
        val b2 = sampleBookmark("b2", "Removed Site", folderId = null)
        val e1 = sampleEntry("b1", "Live Site Updated")

        val items = SavedGrouping.buildItems(
            bookmarks = listOf(b1, b2),
            liveEntriesMap = mapOf("b1" to e1),
            folderMap = mapOf(1L to "Favorites"),
            removedDates = mapOf("b2" to "Removed on 18 Oct"),
        )

        assertEquals(2, items.size)
        assertEquals(e1, items[0].liveEntry)
        assertEquals("Favorites", items[0].folderName)
        assertNull(items[0].removedDateText)

        assertNull(items[1].liveEntry)
        assertNull(items[1].folderName)
        assertEquals("Removed on 18 Oct", items[1].removedDateText)
    }

    @Test
    fun testFilterAndSortFolderScopeAndQueryFields() {
        val titleOf: (Bookmark) -> String = { bm ->
            if (bm.sectionId == "video") "Streaming" else bm.sectionTitle
        }

        val liveRename = SavedEntryItem(
            bookmark = sampleBookmark("e1", "Old Bookmark Name", sectionId = "audio", sectionTitle = "Audio", headingPath = "Audio > Music", folderId = 1L),
            liveEntry = sampleEntry("e1", "Renamed Live Name", sectionId = "audio", headingPath = "Audio > Podcasts"),
        )
        val bmNameOnly = SavedEntryItem(
            bookmark = sampleBookmark("e2", "UniqueBookmarkTitle", sectionId = "audio", sectionTitle = "Audio", headingPath = "Audio > Radio", folderId = 1L),
            liveEntry = sampleEntry("e2", "Radio App", sectionId = "audio", headingPath = "Audio > Radio"),
        )
        val displayTitleMatch = SavedEntryItem(
            bookmark = sampleBookmark("e3", "Movie Hub", sectionId = "video", sectionTitle = "Movies / TV", headingPath = "Video > Movies", folderId = 2L),
            liveEntry = sampleEntry("e3", "Movie Hub", sectionId = "video", headingPath = "Video > Movies"),
        )
        val sectionTitleMatch = SavedEntryItem(
            bookmark = sampleBookmark("e4", "Clip Site", sectionId = "video", sectionTitle = "SpecialWikiSection", headingPath = "Video > Clips", folderId = 1L),
            liveEntry = sampleEntry("e4", "Clip Site", sectionId = "video", headingPath = "Video > Clips"),
        )
        val noteAndRemovedMatch = SavedEntryItem(
            bookmark = sampleBookmark("e5", "Gone Site", sectionId = "misc", sectionTitle = "Misc", headingPath = "Misc > ArchivedPath", folderId = 1L, note = "PrivateWatchNote"),
            liveEntry = null,
        )

        val all = listOf(liveRename, bmNameOnly, displayTitleMatch, sectionTitleMatch, noteAndRemovedMatch)

        // Blank / whitespace query matches all in All scope
        assertEquals(5, SavedGrouping.filterAndSort(all, null, "   ", SavedSort.RECENT, titleOf).size)

        // Folder scope
        val folder1 = SavedGrouping.filterAndSort(all, 1L, "", SavedSort.RECENT, titleOf)
        assertEquals(setOf("e1", "e2", "e4", "e5"), folder1.map { it.bookmark.entryId }.toSet())

        // Each searched field (case-insensitive, trimmed)
        assertEquals(listOf("e1"), SavedGrouping.filterAndSort(all, null, " renamed live ", SavedSort.RECENT, titleOf).map { it.bookmark.entryId })
        assertEquals(listOf("e2"), SavedGrouping.filterAndSort(all, null, "uniquebookmarktitle", SavedSort.RECENT, titleOf).map { it.bookmark.entryId })
        assertEquals(setOf("e3", "e4"), SavedGrouping.filterAndSort(all, null, "STREAMING", SavedSort.RECENT, titleOf).map { it.bookmark.entryId }.toSet())
        assertEquals(listOf("e4"), SavedGrouping.filterAndSort(all, null, "specialwikisection", SavedSort.RECENT, titleOf).map { it.bookmark.entryId })
        assertEquals(listOf("e1"), SavedGrouping.filterAndSort(all, null, "podcasts", SavedSort.RECENT, titleOf).map { it.bookmark.entryId })
        assertEquals(listOf("e5"), SavedGrouping.filterAndSort(all, null, "archivedpath", SavedSort.RECENT, titleOf).map { it.bookmark.entryId })
        assertEquals(listOf("e5"), SavedGrouping.filterAndSort(all, null, "privatewatchnote", SavedSort.RECENT, titleOf).map { it.bookmark.entryId })

        // No match
        assertEquals(emptyList<SavedEntryItem>(), SavedGrouping.filterAndSort(all, null, "nonexistent_xyz", SavedSort.RECENT, titleOf))
    }

    @Test
    fun testFilterAndSortOrdersAndTieBreakersIncludingRemoved() {
        val titleOf: (Bookmark) -> String = { it.sectionTitle }

        val aOld = SavedEntryItem(
            bookmark = sampleBookmark("id_b", "alpha", sectionTitle = "Video", savedAt = 100L),
            liveEntry = sampleEntry("id_b", "alpha"),
        )
        val aSameTimeEarlierId = SavedEntryItem(
            bookmark = sampleBookmark("id_a", "Alpha", sectionTitle = "Audio", savedAt = 100L),
            liveEntry = null, // removed item uses bookmark.name
        )
        val bNewer = SavedEntryItem(
            bookmark = sampleBookmark("id_c", "OldBeta", sectionTitle = "Audio", savedAt = 300L),
            liveEntry = sampleEntry("id_c", "Beta"), // liveEntry name overrides bookmark.name
        )
        val cMid = SavedEntryItem(
            bookmark = sampleBookmark("id_d", "Charlie", sectionTitle = "Games", savedAt = 200L),
            liveEntry = null,
        )

        val items = listOf(aOld, aSameTimeEarlierId, bNewer, cMid)

        // RECENT: savedAt desc, then name (case-insensitive), then entryId
        val recent = SavedGrouping.filterAndSort(items, null, "", SavedSort.RECENT, titleOf)
        assertEquals(listOf("id_c", "id_d", "id_a", "id_b"), recent.map { it.bookmark.entryId })

        // A_Z: name (case-insensitive), then savedAt desc, then entryId
        val az = SavedGrouping.filterAndSort(items, null, "", SavedSort.A_Z, titleOf)
        assertEquals(listOf("id_a", "id_b", "id_c", "id_d"), az.map { it.bookmark.entryId })

        // SECTION: titleOf (case-insensitive), then name, then entryId
        val section = SavedGrouping.filterAndSort(items, null, "", SavedSort.SECTION, titleOf)
        assertEquals(listOf("id_a", "id_c", "id_d", "id_b"), section.map { it.bookmark.entryId })
    }

    @Test
    fun testGroupSortedOrderAndPreservedItemOrder() {
        val titleOf: (Bookmark) -> String = { it.sectionTitle }

        val audioOld = SavedEntryItem(sampleBookmark("a1", "Zeta", sectionTitle = "Audio", savedAt = 100L), null)
        val audioNew = SavedEntryItem(sampleBookmark("a2", "Alpha", sectionTitle = "Audio", savedAt = 200L), null)
        val videoNew = SavedEntryItem(sampleBookmark("v1", "Movie", sectionTitle = "Video", savedAt = 500L), null)
        val gamesTie = SavedEntryItem(sampleBookmark("g1", "Game", sectionTitle = "Games", savedAt = 200L), null)

        // Input order inside Audio: audioOld then audioNew (must be preserved by groupSorted)
        val input = listOf(audioOld, audioNew, videoNew, gamesTie)

        val recentGroups = SavedGrouping.groupSorted(input, SavedSort.RECENT, titleOf)
        // Video (500) first; Audio (200) ties with Games (200) -> Audio before Games alphabetically
        assertEquals(listOf("Video", "Audio", "Games"), recentGroups.map { it.sectionTitle })
        assertEquals(listOf("a1", "a2"), recentGroups[1].items.map { it.bookmark.entryId })

        val azGroups = SavedGrouping.groupSorted(input, SavedSort.A_Z, titleOf)
        assertEquals(listOf("Audio", "Games", "Video"), azGroups.map { it.sectionTitle })
        assertEquals(listOf("a1", "a2"), azGroups[0].items.map { it.bookmark.entryId })

        val sectionGroups = SavedGrouping.groupSorted(input, SavedSort.SECTION, titleOf)
        assertEquals(listOf("Audio", "Games", "Video"), sectionGroups.map { it.sectionTitle })
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
