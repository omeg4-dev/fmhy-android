package io.github.omeg4dev.fmhy.ui.common

import io.github.omeg4dev.fmhy.data.model.Link
import org.junit.Assert.assertEquals
import org.junit.Test

class WebsiteStyleFormatTest {

    private val isSocial: (String) -> Boolean = { it == "GitHub" || it == "Discord" }

    @Test
    fun caseA_nameOnly() {
        val segments = websiteSegments(
            name = "Test Site",
            description = "",
            mirrors = emptyList(),
            extraLinks = emptyList(),
            isSocial = isSocial,
        )
        assertEquals(listOf(WebSegment.Name("Test Site")), segments)
    }

    @Test
    fun caseB_namePlusDescription() {
        val segments = websiteSegments(
            name = "Test Site",
            description = "A great site",
            mirrors = emptyList(),
            extraLinks = emptyList(),
            isSocial = isSocial,
        )
        assertEquals(
            listOf(
                WebSegment.Name("Test Site"),
                WebSegment.Separator(" - "),
                WebSegment.Description("A great site"),
            ),
            segments
        )
    }

    @Test
    fun caseC_descriptionPlusTwoOtherLinks() {
        val segments = websiteSegments(
            name = "Test Site",
            description = "A great site",
            mirrors = emptyList(),
            extraLinks = listOf(
                Link("Link 1", "https://example.com/1"),
                Link("Link 2", "https://example.com/2"),
            ),
            isSocial = isSocial,
        )
        assertEquals(
            listOf(
                WebSegment.Name("Test Site"),
                WebSegment.Separator(" - "),
                WebSegment.Description("A great site"),
                WebSegment.Separator(" / "),
                WebSegment.Link("Link 1", "https://example.com/1"),
                WebSegment.Separator(" / "),
                WebSegment.Link("Link 2", "https://example.com/2"),
            ),
            segments
        )
    }

    @Test
    fun caseD_noDescriptionTwoOtherLinks() {
        val segments = websiteSegments(
            name = "Test Site",
            description = "",
            mirrors = emptyList(),
            extraLinks = listOf(
                Link("Link 1", "https://example.com/1"),
                Link("Link 2", "https://example.com/2"),
            ),
            isSocial = isSocial,
        )
        assertEquals(
            listOf(
                WebSegment.Name("Test Site"),
                WebSegment.Separator(" - "),
                WebSegment.Link("Link 1", "https://example.com/1"),
                WebSegment.Separator(" / "),
                WebSegment.Link("Link 2", "https://example.com/2"),
            ),
            segments
        )
    }

    @Test
    fun caseE_onlySocialLinks() {
        val segments = websiteSegments(
            name = "Test Site",
            description = "",
            mirrors = emptyList(),
            extraLinks = listOf(
                Link("GitHub", "https://github.com/test"),
                Link("Discord", "https://discord.gg/test"),
            ),
            isSocial = isSocial,
        )
        assertEquals(
            listOf(
                WebSegment.Name("Test Site"),
                WebSegment.Separator(" "),
                WebSegment.Social(0, "GitHub", "https://github.com/test"),
                WebSegment.Separator(" "),
                WebSegment.Social(1, "Discord", "https://discord.gg/test"),
            ),
            segments
        )
    }

    @Test
    fun caseF_mirrors() {
        val segments = websiteSegments(
            name = "Test Site",
            description = "A great site",
            mirrors = listOf(
                Link("2", "https://mirror1.com"),
                Link("3", "https://mirror2.com"),
            ),
            extraLinks = emptyList(),
            isSocial = isSocial,
        )
        assertEquals(
            listOf(
                WebSegment.Name("Test Site"),
                WebSegment.Separator(", "),
                WebSegment.Mirror("2", "https://mirror1.com"),
                WebSegment.Separator(", "),
                WebSegment.Mirror("3", "https://mirror2.com"),
                WebSegment.Separator(" - "),
                WebSegment.Description("A great site"),
            ),
            segments
        )
    }

    @Test
    fun caseG_mixedExtraLinks() {
        val segments = websiteSegments(
            name = "Test Site",
            description = "A great site",
            mirrors = emptyList(),
            extraLinks = listOf(
                Link("GitHub", "https://github.com/test"),
                Link("Mirror2", "https://example.com/mirror2"),
                Link("Discord", "https://discord.gg/test"),
            ),
            isSocial = isSocial,
        )
        assertEquals(
            listOf(
                WebSegment.Name("Test Site"),
                WebSegment.Separator(" - "),
                WebSegment.Description("A great site"),
                WebSegment.Separator(" / "),
                WebSegment.Link("Mirror2", "https://example.com/mirror2"),
                WebSegment.Separator(" "),
                WebSegment.Social(0, "GitHub", "https://github.com/test"),
                WebSegment.Separator(" "),
                WebSegment.Social(2, "Discord", "https://discord.gg/test"),
            ),
            segments
        )
    }
}
