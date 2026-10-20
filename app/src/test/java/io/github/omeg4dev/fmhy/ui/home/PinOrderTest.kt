package io.github.omeg4dev.fmhy.ui.home

import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.user.Pin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinOrderTest {

    private val audioSection = Section(
        id = "audio",
        title = "Audio",
        description = "Audio sites",
        ord = 1,
        entryCount = 100,
    )
    private val videoSection = Section(
        id = "video",
        title = "Video",
        description = "Video sites",
        ord = 2,
        entryCount = 200,
    )
    private val gamingSection = Section(
        id = "gaming",
        title = "Gaming",
        description = "Gaming sites",
        ord = 3,
        entryCount = 300,
    )

    private val sections = listOf(audioSection, videoSection, gamingSection)

    @Test
    fun testPinsResolveInCorrectOrder() {
        val rawPins = listOf(
            Pin(sectionId = "gaming", headingId = "", title = "Gaming", ord = 2),
            Pin(sectionId = "audio", headingId = "audio-tools", title = "Audio Tools", ord = 0),
            Pin(sectionId = "video", headingId = "", title = "Video", ord = 1),
        )

        val resolved = resolvePins(rawPins, sections)

        assertEquals(3, resolved.size)
        // First pin: audio-tools (ord 0)
        assertEquals("audio", resolved[0].sectionId)
        assertEquals("audio-tools", resolved[0].headingId)
        assertEquals("Audio › Audio Tools", resolved[0].label)
        assertEquals("audio-tools", resolved[0].anchor)

        // Second pin: video (ord 1)
        assertEquals("video", resolved[1].sectionId)
        assertEquals("", resolved[1].headingId)
        assertEquals("Video", resolved[1].label)
        assertNull(resolved[1].anchor)

        // Third pin: gaming (ord 2)
        assertEquals("gaming", resolved[2].sectionId)
        assertEquals("", resolved[2].headingId)
        assertEquals("Gaming", resolved[2].label)
        assertNull(resolved[2].anchor)
    }

    @Test
    fun testPinsResolveFallbackWhenSectionNotFound() {
        val rawPins = listOf(
            Pin(sectionId = "unknown-sec", headingId = "sub", title = "Sub", ord = 0),
            Pin(sectionId = "unknown-sec-2", headingId = "", title = "My Section", ord = 1),
        )

        val resolved = resolvePins(rawPins, sections)

        assertEquals(2, resolved.size)
        assertEquals("unknown-sec › Sub", resolved[0].label)
        assertEquals("unknown-sec-2", resolved[1].label)
    }

    @Test
    fun testHomeFilteringWhenPersonalHomeOff() {
        val hidden = setOf("video")
        val filtered = filterSections(
            sections = sections,
            personalHome = false,
            hiddenSections = hidden,
            showHiddenSession = false,
        )

        assertEquals(3, filtered.size)
        assertEquals(listOf("audio", "video", "gaming"), filtered.map { it.id })
    }

    @Test
    fun testHomeFilteringWhenPersonalHomeOn() {
        val hidden = setOf("video")
        val filtered = filterSections(
            sections = sections,
            personalHome = true,
            hiddenSections = hidden,
            showHiddenSession = false,
        )

        assertEquals(2, filtered.size)
        assertEquals(listOf("audio", "gaming"), filtered.map { it.id })
    }

    @Test
    fun testHomeFilteringWhenShowHiddenSessionOn() {
        val hidden = setOf("video")
        val filtered = filterSections(
            sections = sections,
            personalHome = true,
            hiddenSections = hidden,
            showHiddenSession = true,
        )

        assertEquals(3, filtered.size)
        assertEquals(listOf("audio", "video", "gaming"), filtered.map { it.id })
    }
}
