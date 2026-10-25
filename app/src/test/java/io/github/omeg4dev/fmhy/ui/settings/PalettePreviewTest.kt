package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.github.omeg4dev.fmhy.theme.ACCENT_PRESETS
import io.github.omeg4dev.fmhy.theme.calloutColorsForSeed
import io.github.omeg4dev.fmhy.theme.paletteColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PalettePreviewTest {

    @Test
    fun testPaletteDotsMatchesSchemeAndCalloutsAndPairwiseDifferent() {
        val darkModes = listOf(false, true)

        for (preset in ACCENT_PRESETS) {
            for (dark in darkModes) {
                val dots = paletteDots(preset.seed, dark)
                val expectedScheme = paletteColorScheme(preset.seed, dark)
                val expectedCallout = calloutColorsForSeed(preset.seed, dark)

                assertEquals(
                    "accent must equal primary for ${preset.name} (dark=$dark)",
                    expectedScheme.primary,
                    dots.accent,
                )
                assertEquals(
                    "selected must equal primaryContainer for ${preset.name} (dark=$dark)",
                    expectedScheme.primaryContainer,
                    dots.selected,
                )
                assertEquals(
                    "tip must equal callout tip accent for ${preset.name} (dark=$dark)",
                    expectedCallout.tip.accent,
                    dots.tip,
                )

                assertNotEquals(
                    "accent and selected must differ for ${preset.name} (dark=$dark)",
                    dots.accent,
                    dots.selected,
                )
                assertNotEquals(
                    "accent and tip must differ for ${preset.name} (dark=$dark)",
                    dots.accent,
                    dots.tip,
                )
                assertNotEquals(
                    "selected and tip must differ for ${preset.name} (dark=$dark)",
                    dots.selected,
                    dots.tip,
                )
            }
        }
    }

    @Test
    fun testMaterialYouDotsTipDiffersFromPrimary() {
        val primary = Color(0xFF6750A4)
        val scheme = lightColorScheme(primary = primary)
        val dots = materialYouDots(scheme, dark = false)

        assertEquals("accent must equal primary", primary, dots.accent)
        assertEquals("selected must equal primaryContainer", scheme.primaryContainer, dots.selected)
        assertNotEquals("tip must not equal primary", primary, dots.tip)
    }
}
