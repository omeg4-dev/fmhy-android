package io.github.omeg4dev.fmhy.ui.whatsnew

import io.github.omeg4dev.fmhy.data.model.ChangeKind
import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsNewTextFilterTest {
    private fun card(name: String, url: String, section: String, oldName: String? = null, oldUrl: String? = null) = ChangeCard(
        key = "$url:1", ts = 1, primaryKind = ChangeKind.ADDED, kinds = setOf(ChangeKind.ADDED),
        name = name, url = url, sectionId = section, entry = null,
        oldName = oldName, oldUrl = oldUrl, oldSectionId = null, oldLine = null,
    )

    private val cards = listOf(
        card("uBlock Origin", "https://ublockorigin.com/", "privacy"),
        card("Zoro", "https://zoro.example/watch", "video", oldName = "Aniwatch", oldUrl = "https://aniwatch.to/x"),
    )
    private val titles = mapOf("privacy" to "Privacy", "video" to "Video Streaming")

    @Test fun blankQueryKeepsAll() = assertEquals(2, WhatsNewGrouping.filterByText(cards, "  ", titles).size)

    @Test fun matchesNameCaseInsensitive() =
        assertEquals(listOf("uBlock Origin"), WhatsNewGrouping.filterByText(cards, "UBLOCK", titles).map { it.name })

    @Test fun matchesOldNameAndOldHost() {
        assertEquals(1, WhatsNewGrouping.filterByText(cards, "aniwatch", titles).size)
        assertEquals(1, WhatsNewGrouping.filterByText(cards, "aniwatch.to", titles).size)
    }

    @Test fun matchesSectionTitleAndAllWords() {
        assertEquals(1, WhatsNewGrouping.filterByText(cards, "streaming", titles).size)
        assertEquals(1, WhatsNewGrouping.filterByText(cards, "zoro streaming", titles).size)
        assertEquals(0, WhatsNewGrouping.filterByText(cards, "zoro privacy", titles).size)
    }

    @Test fun urlPathIsNotSearched() = assertEquals(0, WhatsNewGrouping.filterByText(cards, "watch", titles).size)
}
