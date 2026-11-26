package io.github.omeg4dev.fmhy.data.nsfw

import io.github.omeg4dev.fmhy.data.model.SectionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NsfwSectionBuilderTest {

    @Test
    fun buildsAlternativesAndMoreGroupAndHeadingsMatchingDatasetStoreRules() {
        val md = """
            ## Streaming
            > Always check links
            * ⭐ **[PrimarySite](https://primary.example)** or **[AltSite](https://alt.example)** - Watch stuff
            [InlineOne](https://inline1.example) - First more
            [InlineTwo](https://inline2.example) - Second more

            ### Subcategory
            * [SubSite](https://sub.example) - Sub entry
        """.trimIndent()

        val result = NsfwSectionBuilder.build(md)

        assertEquals(2, result.headings.size)
        assertEquals("Streaming", result.headings[0].title)
        assertEquals("Subcategory", result.headings[1].title)

        // Expected items:
        // 0: Heading(Streaming)
        // 1: Note(Always check links)
        // 2: EntryItem(PrimarySite, alternatives=[AltSite])
        // 3: MoreGroup([InlineOne, InlineTwo])
        // 4: Heading(Subcategory)
        // 5: EntryItem(SubSite)
        assertEquals(6, result.items.size)
        assertTrue(result.items[0] is SectionItem.HeadingItem)
        assertTrue(result.items[1] is SectionItem.NoteItem)
        assertTrue(result.items[2] is SectionItem.EntryItem)
        val entryItem = result.items[2] as SectionItem.EntryItem
        assertEquals("PrimarySite", entryItem.entry.name)
        assertEquals(listOf("AltSite"), entryItem.alternatives.map { it.name })

        assertTrue(result.items[3] is SectionItem.MoreGroup)
        val more = result.items[3] as SectionItem.MoreGroup
        assertEquals(listOf("InlineOne", "InlineTwo"), more.entries.map { it.name })

        assertTrue(result.items[4] is SectionItem.HeadingItem)
        assertTrue(result.items[5] is SectionItem.EntryItem)
    }
}
