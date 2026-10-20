package io.github.omeg4dev.fmhy.ui.saved

import io.github.omeg4dev.fmhy.data.Bookmark
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareListFormatterTest {

    private fun sampleEntry(
        id: String,
        name: String,
        url: String,
        description: String = "",
        tier: Tier = Tier.NORMAL,
    ): Entry {
        return Entry(
            num = 1,
            id = id,
            sectionId = "video",
            headingId = "h1",
            ord = 1,
            name = name,
            url = url,
            originalUrl = null,
            description = description,
            tags = emptyList(),
            headingPath = "Video > Streaming",
            tier = tier,
            isInlineMore = false,
            isBase64 = false,
            mirrors = emptyList(),
            extraLinks = emptyList(),
            firstSeen = 1000L,
            lastChanged = 1000L,
            starredAt = null,
        )
    }

    private fun sampleBookmark(
        id: String,
        name: String,
        url: String,
        note: String = "",
    ): Bookmark {
        return Bookmark(
            entryId = id,
            name = name,
            url = url,
            sectionId = "video",
            sectionTitle = "Movies / TV",
            headingPath = "Video > Streaming",
            savedAt = 1000L,
            note = note,
        )
    }

    @Test
    fun testFormatMatchesSpec() {
        val b1 = sampleBookmark("e1", "Site One", "https://site1.com", note = "Private note")
        val e1 = sampleEntry("e1", "Site One", "https://site1.com", description = "Best movies site", tier = Tier.STAR)

        val b2 = sampleBookmark("e2", "Site Two", "https://site2.com")
        val e2 = sampleEntry("e2", "Site Two", "https://site2.com", description = "", tier = Tier.NORMAL)

        val items = listOf(
            SavedEntryItem(b1, e1),
            SavedEntryItem(b2, e2),
        )

        val output = ShareListFormatter.format("My Favorites", items)
        val expected = """
            # My Favorites
            * ⭐ [Site One](https://site1.com) - Best movies site
            * [Site Two](https://site2.com)

        """.trimIndent()

        assertEquals(expected, output)
    }

    @Test
    fun testTopPickIsStarredAndRemovedSiteHasNoDescription() {
        val b1 = sampleBookmark("e1", "Top Site", "https://top.com")
        val e1 = sampleEntry("e1", "Top Site", "https://top.com", description = "Great", tier = Tier.TOP)

        // Removed site (liveEntry == null)
        val b2 = sampleBookmark("e2", "Old Site", "https://old.com", note = "Some note")

        val items = listOf(
            SavedEntryItem(b1, e1),
            SavedEntryItem(b2, null),
        )

        val output = ShareListFormatter.format("Tools", items)
        val expected = """
            # Tools
            * ⭐ [Top Site](https://top.com) - Great
            * [Old Site](https://old.com)

        """.trimIndent()

        assertEquals(expected, output)
    }
}
