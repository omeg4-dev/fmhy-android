package io.github.omeg4dev.fmhy.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteMarkersTest {

    @Test
    fun websiteBadgeMarkers_allCombinations_valid() {
        val booleans = listOf(false, true)
        val altCounts = listOf(0, 3)

        var testedCombos = 0
        for (isNew in booleans) {
            for (isBase64 in booleans) {
                for (isSaved in booleans) {
                    for (isCrossLink in booleans) {
                        for (count in altCounts) {
                            testedCombos++
                            val markers = websiteBadgeMarkers(
                                isNew = isNew,
                                alternativesCount = count,
                                isBase64 = isBase64,
                                isSaved = isSaved,
                                isCrossLink = isCrossLink,
                            )

                            // Every alternateText non-blank
                            for (m in markers) {
                                assertTrue(
                                    "alternateText should not be blank for ${m.id}",
                                    m.alternateText.isNotBlank()
                                )
                            }

                            // IDs unique
                            val ids = markers.map { it.id }
                            assertEquals(
                                "Marker IDs must be unique: $ids",
                                ids.distinct().size,
                                ids.size
                            )

                            // Crosslink text == "Link to another FMHY section"
                            if (isCrossLink) {
                                val crosslinkMarker = markers.find { it.id == "badge:crosslink" }
                                assertEquals(
                                    "Link to another FMHY section",
                                    crosslinkMarker?.alternateText
                                )
                            } else {
                                assertTrue(
                                    "badge:crosslink should not be present",
                                    markers.none { it.id == "badge:crosslink" }
                                )
                            }
                        }
                    }
                }
            }
        }
        assertEquals(32, testedCombos)
    }

    @Test
    fun websiteBadgeMarkers_allEnabled_orderAndValues() {
        val markers = websiteBadgeMarkers(
            isNew = true,
            alternativesCount = 3,
            isBase64 = true,
            isSaved = true,
            isCrossLink = true,
        )
        assertEquals(
            listOf(
                WebsiteMarker("badge:new", "NEW"),
                WebsiteMarker("badge:alternatives", "+3"),
                WebsiteMarker("badge:base64", "Hidden link, decoded"),
                WebsiteMarker("badge:saved", "Saved"),
                WebsiteMarker("badge:crosslink", "Link to another FMHY section"),
            ),
            markers
        )
    }

    @Test
    fun websiteBadgeMarkers_allDisabled_empty() {
        val markers = websiteBadgeMarkers(
            isNew = false,
            alternativesCount = 0,
            isBase64 = false,
            isSaved = false,
            isCrossLink = false,
        )
        assertTrue(markers.isEmpty())
    }

    @Test
    fun inlineAltText_fallbackWhenBlank() {
        assertEquals("Link", inlineAltText("", "Link"))
        assertEquals("Link", inlineAltText("   ", "Link"))
        assertEquals("Link", inlineAltText(null, "Link"))
        assertEquals("x", inlineAltText("x", "Link"))
    }
}
