package io.github.omeg4dev.fmhy.ui.section

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.data.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionStructureStarredTest {

    private fun createHeading(id: String): Heading {
        return Heading(
            id = id,
            sectionId = "test-sec",
            parentId = null,
            level = 1,
            title = id,
            anchor = id,
            ord = 0,
        )
    }

    private fun createEntry(id: String, tier: Tier): Entry {
        return Entry(
            id = id,
            num = 1,
            sectionId = "test-sec",
            headingId = null,
            ord = 0,
            name = id,
            url = "https://example.com/$id",
            originalUrl = null,
            description = "desc",
            tags = emptyList(),
            headingPath = "",
            tier = tier,
            kind = EntryKind.NORMAL,
            isInlineMore = false,
            isBase64 = false,
            mirrors = emptyList(),
            extraLinks = emptyList(),
            firstSeen = 0L,
            lastChanged = 0L,
            starredAt = null,
        )
    }

    @Test
    fun testStarredOnlyKeepsTopAndStarAndDropsNormal() {
        val heading = SectionItem.HeadingItem(createHeading("h1"))
        val entryTop = SectionItem.EntryItem(createEntry("top-entry", Tier.TOP))
        val entryStar = SectionItem.EntryItem(createEntry("star-entry", Tier.STAR))
        val entryNormal = SectionItem.EntryItem(createEntry("normal-entry", Tier.NORMAL))

        val items = listOf(heading, entryTop, entryStar, entryNormal)
        val filtered = filterSectionItems(items, starredOnly = true, showIndexes = true)

        assertEquals(3, filtered.size)
        assertEquals(heading, filtered[0])
        assertEquals("top-entry", (filtered[1] as SectionItem.EntryItem).entry.id)
        assertEquals("star-entry", (filtered[2] as SectionItem.EntryItem).entry.id)
    }

    @Test
    fun testStarredOnlyDropsHeadingIfOnlyNormalEntriesExist() {
        val heading = SectionItem.HeadingItem(createHeading("h1"))
        val entryNormal = SectionItem.EntryItem(createEntry("normal-entry", Tier.NORMAL))

        val items = listOf(heading, entryNormal)
        val filtered = filterSectionItems(items, starredOnly = true, showIndexes = true)

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun testStarredOnlyInMoreGroup() {
        val heading = SectionItem.HeadingItem(createHeading("h1"))
        val moreGroup = SectionItem.MoreGroup(
            listOf(
                createEntry("top-in-more", Tier.TOP),
                createEntry("star-in-more", Tier.STAR),
                createEntry("normal-in-more", Tier.NORMAL),
            )
        )

        val items = listOf(heading, moreGroup)
        val filtered = filterSectionItems(items, starredOnly = true, showIndexes = true)

        assertEquals(2, filtered.size)
        assertEquals(heading, filtered[0])
        val remainingMoreGroup = filtered[1] as SectionItem.MoreGroup
        val remainingIds = remainingMoreGroup.entries.map { it.id }
        assertEquals(listOf("top-in-more", "star-in-more"), remainingIds)
    }
}
