package io.github.omeg4dev.fmhy.ui.section

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Note
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.data.model.Tier
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionStructureTest {

    private fun createHeading(id: String, level: Int, title: String = id, anchor: String = id): Heading {
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

    private fun createNote(id: String, text: String = "Note text"): Note {
        return Note(
            id = id,
            sectionId = "test-sec",
            headingId = null,
            ord = 0,
            textMd = text,
        )
    }

    @Test
    fun testOverviewSplit() {
        val note1 = SectionItem.NoteItem(createNote("n1"))
        val entry1 = SectionItem.EntryItem(createEntry("e1"))
        val h1 = SectionItem.HeadingItem(createHeading("h1", level = 1, title = "Category 1"))
        val entry2 = SectionItem.EntryItem(createEntry("e2"))
        val h2 = SectionItem.HeadingItem(createHeading("h2", level = 1, title = "Category 2"))
        val entry3 = SectionItem.EntryItem(createEntry("e3"))

        val items = listOf(note1, entry1, h1, entry2, h2, entry3)
        val structure = buildSectionStructure(items)

        assertEquals(listOf(note1, entry1), structure.overview)
        assertEquals(2, structure.categories.size)

        val cat1 = structure.categories[0]
        assertEquals("h1", cat1.heading.id)
        assertEquals(listOf(entry2), cat1.items)

        val cat2 = structure.categories[1]
        assertEquals("h2", cat2.heading.id)
        assertEquals(listOf(entry3), cat2.items)
    }

    @Test
    fun testEntryCountsIncludingMoreGroup() {
        val h1 = SectionItem.HeadingItem(createHeading("h1", level = 1, title = "Cat 1"))
        val e1 = SectionItem.EntryItem(createEntry("e1"))
        val e2 = SectionItem.EntryItem(createEntry("e2"))
        val mg = SectionItem.MoreGroup(listOf(createEntry("m1"), createEntry("m2"), createEntry("m3")))
        val note = SectionItem.NoteItem(createNote("n1"))
        val subHeading = SectionItem.HeadingItem(createHeading("sub1", level = 2, title = "Sub 1"))
        val e3 = SectionItem.EntryItem(createEntry("e3"))

        val items = listOf(h1, e1, e2, mg, note, subHeading, e3)
        val structure = buildSectionStructure(items)

        assertEquals(1, structure.categories.size)
        val cat = structure.categories[0]

        // 2 individual entries + 3 in MoreGroup + 1 after subHeading = 6 entries total
        assertEquals(6, cat.entryCount)
        assertEquals(listOf(subHeading.heading), cat.subHeadings)
        assertEquals(listOf(e1, e2, mg, note, subHeading, e3), cat.items)
    }

    @Test
    fun testCategoryIndexOfItemMapping() {
        // Indices:
        // 0: note (overview) -> -1
        // 1: entry (overview) -> -1
        // 2: h1 (cat 0 heading) -> 0
        // 3: entry -> 0
        // 4: note -> 0
        // 5: h2 (cat 1 heading) -> 1
        // 6: entry -> 1
        val n0 = SectionItem.NoteItem(createNote("n0"))
        val e0 = SectionItem.EntryItem(createEntry("e0"))
        val h1 = SectionItem.HeadingItem(createHeading("h1", level = 1))
        val e1 = SectionItem.EntryItem(createEntry("e1"))
        val n1 = SectionItem.NoteItem(createNote("n1"))
        val h2 = SectionItem.HeadingItem(createHeading("h2", level = 1))
        val e2 = SectionItem.EntryItem(createEntry("e2"))

        val items = listOf(n0, e0, h1, e1, n1, h2, e2)
        val structure = buildSectionStructure(items)

        val expected = intArrayOf(-1, -1, 0, 0, 0, 1, 1)
        assertArrayEquals(expected, structure.categoryIndexOfItem)
    }

    @Test
    fun testNoLevel1Fallback() {
        // Section has no level-1 headings, but has level-2 headings.
        // Level-2 headings should be treated as categories.
        val n0 = SectionItem.NoteItem(createNote("n0"))
        val hSub1 = SectionItem.HeadingItem(createHeading("sub1", level = 2, title = "Sub Category 1"))
        val e1 = SectionItem.EntryItem(createEntry("e1"))
        val hSubSub = SectionItem.HeadingItem(createHeading("subsub", level = 3, title = "Sub Sub Heading"))
        val e2 = SectionItem.EntryItem(createEntry("e2"))
        val hSub2 = SectionItem.HeadingItem(createHeading("sub2", level = 2, title = "Sub Category 2"))
        val e3 = SectionItem.EntryItem(createEntry("e3"))

        val items = listOf(n0, hSub1, e1, hSubSub, e2, hSub2, e3)
        val structure = buildSectionStructure(items)

        assertEquals(listOf(n0), structure.overview)
        assertEquals(2, structure.categories.size)

        val cat1 = structure.categories[0]
        assertEquals("sub1", cat1.heading.id)
        assertEquals(listOf(e1, hSubSub, e2), cat1.items)
        assertEquals(2, cat1.entryCount)
        assertEquals(listOf(hSubSub.heading), cat1.subHeadings)

        val cat2 = structure.categories[1]
        assertEquals("sub2", cat2.heading.id)
        assertEquals(listOf(e3), cat2.items)
        assertEquals(1, cat2.entryCount)
        assertTrue(cat2.subHeadings.isEmpty())

        val expectedIndices = intArrayOf(-1, 0, 0, 0, 0, 1, 1)
        assertArrayEquals(expectedIndices, structure.categoryIndexOfItem)
    }

    @Test
    fun testEmptyItems() {
        val structure = buildSectionStructure(emptyList())
        assertTrue(structure.overview.isEmpty())
        assertTrue(structure.categories.isEmpty())
        assertEquals(0, structure.categoryIndexOfItem.size)
    }

    @Test
    fun testNoHeadingsAtAll() {
        val e1 = SectionItem.EntryItem(createEntry("e1"))
        val n1 = SectionItem.NoteItem(createNote("n1"))
        val items = listOf(e1, n1)

        val structure = buildSectionStructure(items)
        assertEquals(items, structure.overview)
        assertTrue(structure.categories.isEmpty())
        assertArrayEquals(intArrayOf(-1, -1), structure.categoryIndexOfItem)
    }

    @Test
    fun testFilterSectionItems_noFilterReturnsSame() {
        val h1 = SectionItem.HeadingItem(createHeading("h1", level = 1))
        val e1 = SectionItem.EntryItem(createEntry("e1", tier = Tier.NORMAL, kind = EntryKind.NORMAL))
        val e2 = SectionItem.EntryItem(createEntry("e2", tier = Tier.STAR, kind = EntryKind.INDEX))
        val items = listOf(h1, e1, e2)

        val filtered = filterSectionItems(items, starredOnly = false, showIndexes = true)
        assertEquals(items, filtered)
    }

    @Test
    fun testFilterSectionItems_starredOnly_dropsNormalAndEmptyHeadings() {
        // Cat 1 has Sub 1 (only normal) and Sub 2 (has star)
        val h1 = SectionItem.HeadingItem(createHeading("h1", level = 1, title = "Cat 1"))
        val hSub1 = SectionItem.HeadingItem(createHeading("sub1", level = 2, title = "Sub 1"))
        val e1 = SectionItem.EntryItem(createEntry("e1", tier = Tier.NORMAL))
        val hSub2 = SectionItem.HeadingItem(createHeading("sub2", level = 2, title = "Sub 2"))
        val e2 = SectionItem.EntryItem(createEntry("e2", tier = Tier.STAR))

        // Cat 2 has only normal entry
        val h2 = SectionItem.HeadingItem(createHeading("h2", level = 1, title = "Cat 2"))
        val e3 = SectionItem.EntryItem(createEntry("e3", tier = Tier.NORMAL))

        val items = listOf(h1, hSub1, e1, hSub2, e2, h2, e3)
        val filtered = filterSectionItems(items, starredOnly = true, showIndexes = true)

        // Sub 1 dropped (e1 dropped, no entries before sub2), Cat 2 dropped (e3 dropped, no entries before end)
        assertEquals(listOf(h1, hSub2, e2), filtered)
    }

    @Test
    fun testFilterSectionItems_showIndexes_dropsIndexEntries() {
        val h1 = SectionItem.HeadingItem(createHeading("h1", level = 1))
        val eIndexStar = SectionItem.EntryItem(createEntry("e1", tier = Tier.STAR, kind = EntryKind.INDEX))
        val eNormal = SectionItem.EntryItem(createEntry("e2", tier = Tier.NORMAL, kind = EntryKind.NORMAL))

        val items = listOf(h1, eIndexStar, eNormal)

        // Drop indexes while starredOnly = false -> eIndexStar dropped, eNormal kept, h1 kept
        val filteredNoIndex = filterSectionItems(items, starredOnly = false, showIndexes = false)
        assertEquals(listOf(h1, eNormal), filteredNoIndex)

        // Drop indexes AND starredOnly = true -> both entries dropped, h1 dropped (empty subtree)
        val filteredBoth = filterSectionItems(items, starredOnly = true, showIndexes = false)
        assertTrue(filteredBoth.isEmpty())
    }

    @Test
    fun testFilterSectionItems_moreGroup_partialAndFullFiltering() {
        val h1 = SectionItem.HeadingItem(createHeading("h1", level = 1))
        val mg1 = SectionItem.MoreGroup(
            listOf(
                createEntry("m1", tier = Tier.STAR),
                createEntry("m2", tier = Tier.NORMAL),
            )
        )
        val h2 = SectionItem.HeadingItem(createHeading("h2", level = 1))
        val mg2 = SectionItem.MoreGroup(
            listOf(
                createEntry("m3", tier = Tier.NORMAL),
                createEntry("m4", tier = Tier.NORMAL),
            )
        )

        val items = listOf(h1, mg1, h2, mg2)
        val filtered = filterSectionItems(items, starredOnly = true, showIndexes = true)

        // mg1 filtered to only [m1]; mg2 dropped entirely; h2 dropped because no remaining entries
        assertEquals(2, filtered.size)
        assertEquals(h1, filtered[0])
        val filteredMg = filtered[1] as SectionItem.MoreGroup
        assertEquals(1, filteredMg.entries.size)
        assertEquals("m1", filteredMg.entries[0].id)
    }

    @Test
    fun testFilterSectionItems_nestedHeadingsAndNotes() {
        val noteOverview = SectionItem.NoteItem(createNote("n0", "Overview Note"))
        val h1 = SectionItem.HeadingItem(createHeading("h1", level = 1))
        val note1 = SectionItem.NoteItem(createNote("n1", "Cat 1 Note"))
        val hSub1 = SectionItem.HeadingItem(createHeading("sub1", level = 2))
        val note2 = SectionItem.NoteItem(createNote("n2", "Sub 1 Note"))
        val hSubSub = SectionItem.HeadingItem(createHeading("subsub", level = 3))
        val e1 = SectionItem.EntryItem(createEntry("e1", tier = Tier.NORMAL))

        val items = listOf(noteOverview, h1, note1, hSub1, note2, hSubSub, e1)
        val filtered = filterSectionItems(items, starredOnly = true, showIndexes = true)

        // e1 dropped -> all subheadings and their notes dropped, overview note preserved
        assertEquals(listOf(noteOverview), filtered)
    }

    @Test
    fun testFindCategoryHeadingId() {
        val h1 = SectionItem.HeadingItem(createHeading("h1", level = 1))
        val sub1 = SectionItem.HeadingItem(createHeading("sub1", level = 2))
        val e1 = SectionItem.EntryItem(createEntry("e1"))
        val h2 = SectionItem.HeadingItem(createHeading("h2", level = 1))
        val e2 = SectionItem.EntryItem(createEntry("e2"))
        val n0 = SectionItem.NoteItem(createNote("n0"))

        val items = listOf(n0, h1, sub1, e1, h2, e2)
        val structure = buildSectionStructure(items)

        assertEquals("h1", findCategoryHeadingId(structure, "h1"))
        assertEquals("h1", findCategoryHeadingId(structure, "sub1"))
        assertEquals("h2", findCategoryHeadingId(structure, "h2"))
        assertNull(findCategoryHeadingId(structure, "nonexistent"))
    }

    @Test
    fun testEntryItemAlternativesCount() {
        val h1 = SectionItem.HeadingItem(createHeading("h1", level = 1))
        val e1 = SectionItem.EntryItem(
            entry = createEntry("e1"),
            alternatives = listOf(createEntry("e2"), createEntry("e3"))
        )
        val structure = buildSectionStructure(listOf(h1, e1))
        assertEquals(1, structure.categories.size)
        assertEquals(3, structure.categories[0].entryCount)
    }

    @Test
    fun testFilterSectionItemsAlternatives() {
        val h1 = SectionItem.HeadingItem(createHeading("h1", level = 1))
        val ePrimaryIndex = SectionItem.EntryItem(
            entry = createEntry("e1", kind = EntryKind.INDEX),
            alternatives = listOf(createEntry("e2", kind = EntryKind.NORMAL), createEntry("e3", kind = EntryKind.INDEX))
        )
        val filtered = filterSectionItems(listOf(h1, ePrimaryIndex), starredOnly = false, showIndexes = false)
        assertEquals(2, filtered.size)
        assertEquals(h1, filtered[0])
        val promoted = filtered[1] as SectionItem.EntryItem
        assertEquals("e2", promoted.entry.id)
        assertTrue(promoted.alternatives.isEmpty())
    }
}

