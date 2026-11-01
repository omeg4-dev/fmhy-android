package io.github.omeg4dev.fmhy.ui.common

import io.github.omeg4dev.fmhy.data.model.Section
import org.junit.Assert.assertEquals
import org.junit.Test

class SectionNamesTest {

    private val expectedMappings = listOf(
        "privacy" to "Adblocking / Privacy",
        "ai" to "Artificial Intelligence",
        "video" to "Streaming",
        "audio" to "Listening",
        "gaming" to "Gaming",
        "reading" to "Reading",
        "downloading" to "Downloading",
        "torrenting" to "Torrenting",
        "educational" to "Educational",
        "mobile" to "Android / iOS",
        "linux-macos" to "Linux / macOS",
        "non-english" to "Non-English",
        "misc" to "Miscellaneous",
        "gaming-tools" to "Gaming Tools",
        "developer-tools" to "Developer Tools",
        "image-tools" to "Image Tools",
        "system-tools" to "System Tools",
        "file-tools" to "File Tools",
        "video-tools" to "Video Tools",
        "text-tools" to "Text Tools",
        "internet-tools" to "Internet Tools",
        "social-media-tools" to "Social Media Tools",
        "storage" to "Storage",
    )

    @Test
    fun mapHasExactly23Keys() {
        assertEquals(23, SECTION_DISPLAY_NAMES.size)
        assertEquals(23, expectedMappings.size)
    }

    @Test
    fun tableDrivenMappedNames() {
        for ((id, expected) in expectedMappings) {
            val actual = sectionDisplayTitle(id, "Wiki fallback for $id")
            assertEquals("Mapping for $id", expected, actual)

            val section = Section(id = id, title = "Wiki fallback for $id", description = "", ord = 0, entryCount = 0)
            assertEquals("Section.displayTitle for $id", expected, section.displayTitle)
        }
    }

    @Test
    fun unknownIdFallsBackToWikiTitle() {
        val unknownId = "unknown-id"
        val fallback = "Unknown Category Wiki Title"
        assertEquals(fallback, sectionDisplayTitle(unknownId, fallback))

        val section = Section(id = unknownId, title = fallback, description = "", ord = 0, entryCount = 0)
        assertEquals(fallback, section.displayTitle)
    }

    @Test
    fun settingOffShowsWikiTitles() {
        try {
            SectionNamePrefs.websiteNames = false
            assertEquals("Movies / TV / Anime", sectionDisplayTitle("video", "Movies / TV / Anime"))
            val section = Section(id = "audio", title = "Music / Podcasts / Radio", description = "", ord = 0, entryCount = 0)
            assertEquals("Music / Podcasts / Radio", section.displayTitle)
            SectionNamePrefs.websiteNames = true
            assertEquals("Streaming", sectionDisplayTitle("video", "Movies / TV / Anime"))
            assertEquals("Listening", section.displayTitle)
        } finally {
            SectionNamePrefs.websiteNames = true
        }
    }
}
