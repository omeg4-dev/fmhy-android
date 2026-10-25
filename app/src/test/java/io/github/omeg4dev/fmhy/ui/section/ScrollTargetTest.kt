package io.github.omeg4dev.fmhy.ui.section

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.data.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollTargetTest {

    private fun createHeading(
        id: String,
        level: Int = 1,
        title: String = id,
        anchor: String = id,
    ): Heading {
        return Heading(
            id = id,
            sectionId = "test-sec",
            parentId = null,
            level = level,
            title = title,
            anchor = anchor,
            ord = 0,
        )
    }

    private fun createEntry(
        id: String,
        name: String = id,
        tier: Tier = Tier.NORMAL,
        kind: EntryKind = EntryKind.NORMAL,
    ): Entry {
        return Entry(
            id = id,
            num = 1,
            sectionId = "test-sec",
            headingId = null,
            ord = 0,
            name = name,
            url = "https://example.com/$id",
            originalUrl = null,
            description = "desc",
            tags = emptyList(),
            headingPath = "",
            tier = tier,
            kind = kind,
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
    fun testEntryFirst() {
        val items = listOf(
            SectionItem.EntryItem(createEntry("e1")),
            SectionItem.HeadingItem(createHeading("h1")),
            SectionItem.EntryItem(createEntry("e2")),
        )
        val target = resolveScrollTarget(items, focusEntryId = "e1", focusHeadingId = null)
        assertEquals(ScrollTarget.Found(index = 0, isHeading = false, id = "e1"), target)
    }

    @Test
    fun testEntryMiddle() {
        val items = listOf(
            SectionItem.HeadingItem(createHeading("h1")),
            SectionItem.EntryItem(createEntry("e1")),
            SectionItem.HeadingItem(createHeading("h2")),
        )
        val target = resolveScrollTarget(items, focusEntryId = "e1", focusHeadingId = null)
        assertEquals(ScrollTarget.Found(index = 1, isHeading = false, id = "e1"), target)
    }

    @Test
    fun testEntryLast() {
        val items = listOf(
            SectionItem.HeadingItem(createHeading("h1")),
            SectionItem.EntryItem(createEntry("e1")),
            SectionItem.EntryItem(createEntry("e2")),
        )
        val target = resolveScrollTarget(items, focusEntryId = "e2", focusHeadingId = null)
        assertEquals(ScrollTarget.Found(index = 2, isHeading = false, id = "e2"), target)
    }

    @Test
    fun testEntryMatchedViaAlternatives() {
        val items = listOf(
            SectionItem.HeadingItem(createHeading("h1")),
            SectionItem.EntryItem(
                entry = createEntry("main_entry"),
                alternatives = listOf(createEntry("alt1"), createEntry("alt2")),
            ),
        )
        val target = resolveScrollTarget(items, focusEntryId = "alt2", focusHeadingId = null)
        assertEquals(ScrollTarget.Found(index = 1, isHeading = false, id = "alt2"), target)
    }

    @Test
    fun testEntryInMoreGroup() {
        val items = listOf(
            SectionItem.HeadingItem(createHeading("h1")),
            SectionItem.MoreGroup(
                entries = listOf(createEntry("more1"), createEntry("more2")),
            ),
        )
        val target = resolveScrollTarget(items, focusEntryId = "more2", focusHeadingId = null)
        assertEquals(ScrollTarget.Found(index = 1, isHeading = false, id = "more2"), target)
    }

    @Test
    fun testMissingEntryWithHeadingFallback() {
        val items = listOf(
            SectionItem.HeadingItem(createHeading(id = "head1", anchor = "head-1")),
            SectionItem.EntryItem(createEntry("e1")),
        )
        val target = resolveScrollTarget(items, focusEntryId = "missing", focusHeadingId = "head1")
        assertEquals(ScrollTarget.MissingEntry(fallbackHeadingIndex = 0, fallbackHeadingId = "head1"), target)
    }

    @Test
    fun testMissingEntryWithHeadingFallbackByAnchor() {
        val items = listOf(
            SectionItem.HeadingItem(createHeading(id = "head1", anchor = "head-1")),
            SectionItem.EntryItem(createEntry("e1")),
        )
        val target = resolveScrollTarget(items, focusEntryId = "missing", focusHeadingId = "head-1")
        assertEquals(ScrollTarget.MissingEntry(fallbackHeadingIndex = 0, fallbackHeadingId = "head1"), target)
    }

    @Test
    fun testMissingEntryWithoutFallback() {
        val items = listOf(
            SectionItem.HeadingItem(createHeading("head1")),
            SectionItem.EntryItem(createEntry("e1")),
        )
        val targetNullHeading = resolveScrollTarget(items, focusEntryId = "missing", focusHeadingId = null)
        assertEquals(ScrollTarget.MissingEntry(fallbackHeadingIndex = null, fallbackHeadingId = null), targetNullHeading)

        val targetUnknownHeading = resolveScrollTarget(items, focusEntryId = "missing", focusHeadingId = "unknown-heading")
        assertEquals(ScrollTarget.MissingEntry(fallbackHeadingIndex = null, fallbackHeadingId = null), targetUnknownHeading)
    }

    @Test
    fun testHeadingById() {
        val items = listOf(
            SectionItem.HeadingItem(createHeading("h1")),
            SectionItem.HeadingItem(createHeading("h2")),
        )
        val target = resolveScrollTarget(items, focusEntryId = null, focusHeadingId = "h2")
        assertEquals(ScrollTarget.Found(index = 1, isHeading = true, id = "h2"), target)
    }

    @Test
    fun testHeadingByAnchorCaseInsensitive() {
        val items = listOf(
            SectionItem.HeadingItem(createHeading(id = "h1", anchor = "my-Anchor-Slug")),
        )
        val target = resolveScrollTarget(items, focusEntryId = null, focusHeadingId = "MY-ANCHOR-SLUG")
        assertEquals(ScrollTarget.Found(index = 0, isHeading = true, id = "h1"), target)
    }

    @Test
    fun testHeadingByEndsWith() {
        val items = listOf(
            SectionItem.HeadingItem(createHeading(id = "section/category/subheading")),
        )
        val target = resolveScrollTarget(items, focusEntryId = null, focusHeadingId = "category/subheading")
        assertEquals(ScrollTarget.Found(index = 0, isHeading = true, id = "section/category/subheading"), target)
    }

    @Test
    fun testBothNullReturnsNone() {
        val items = listOf(
            SectionItem.HeadingItem(createHeading("h1")),
            SectionItem.EntryItem(createEntry("e1")),
        )
        val targetNull = resolveScrollTarget(items, focusEntryId = null, focusHeadingId = null)
        assertEquals(ScrollTarget.None, targetNull)

        val targetEmpty = resolveScrollTarget(items, focusEntryId = "", focusHeadingId = "")
        assertEquals(ScrollTarget.None, targetEmpty)
    }

    @Test
    fun testEmptyList() {
        val empty = emptyList<SectionItem>()

        val targetWithEntry = resolveScrollTarget(empty, focusEntryId = "e1", focusHeadingId = "h1")
        assertEquals(ScrollTarget.MissingEntry(fallbackHeadingIndex = null, fallbackHeadingId = null), targetWithEntry)

        val targetWithHeadingOnly = resolveScrollTarget(empty, focusEntryId = null, focusHeadingId = "h1")
        assertEquals(ScrollTarget.None, targetWithHeadingOnly)

        val targetBothNull = resolveScrollTarget(empty, focusEntryId = null, focusHeadingId = null)
        assertEquals(ScrollTarget.None, targetBothNull)
    }

    @Test
    fun testEntryPrecedenceOverHeading() {
        val items = listOf(
            SectionItem.HeadingItem(createHeading("h1")),
            SectionItem.EntryItem(createEntry("e1")),
        )
        val target = resolveScrollTarget(items, focusEntryId = "e1", focusHeadingId = "h1")
        assertEquals(ScrollTarget.Found(index = 1, isHeading = false, id = "e1"), target)
    }
}
