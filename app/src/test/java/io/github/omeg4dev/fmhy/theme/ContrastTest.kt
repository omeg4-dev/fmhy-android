package io.github.omeg4dev.fmhy.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {

    private val testSeeds = (ACCENT_PALETTES.map { it.seed } + 0xFF7C5CFF.toInt()).distinct()

    @Test
    fun testPaletteContrastRequirements() {
        for (seed in testSeeds) {
            val palette = paletteFor(seed)
            val paletteName = palette?.name ?: "Seed_${seed.toUInt().toString(16)}"
            val scheme = paletteColorScheme(seed, dark = false)

            fun assertContrast(pairName: String, c1: Color, c2: Color, expected: Double) {
                val ratio = contrastRatio(c1, c2)
                assertTrue(
                    "Palette $paletteName, pair $pairName: contrast was $ratio, expected >= $expected",
                    ratio >= expected,
                )
            }

            // onSurface/surface >= 7
            assertContrast("onSurface vs surface", scheme.onSurface, scheme.surface, 7.0)

            // onSurfaceVariant/surfaceContainer >= 4.5
            assertContrast("onSurfaceVariant vs surfaceContainer", scheme.onSurfaceVariant, scheme.surfaceContainer, 4.5)

            // onPrimary/primary >= 4.5
            assertContrast("onPrimary vs primary", scheme.onPrimary, scheme.primary, 4.5)

            // onPrimaryContainer/primaryContainer >= 4.5
            assertContrast("onPrimaryContainer vs primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, 4.5)

            // onSecondaryContainer/secondaryContainer >= 4.5
            assertContrast("onSecondaryContainer vs secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, 4.5)

            // primary vs surface >= 3
            assertContrast("primary vs surface", scheme.primary, scheme.surface, 3.0)

            // primaryContainer vs surface >= 3
            assertContrast("primaryContainer vs surface", scheme.primaryContainer, scheme.surface, 3.0)

            // outline vs surface >= 3
            assertContrast("outline vs surface", scheme.outline, scheme.surface, 3.0)

            // surfaceContainer vs surface >= 1.04
            assertContrast("surfaceContainer vs surface", scheme.surfaceContainer, scheme.surface, 1.04)

            // surfaceContainerHigh vs surface >= 1.07
            assertContrast("surfaceContainerHigh vs surface", scheme.surfaceContainerHigh, scheme.surface, 1.07)
        }
    }

    @Test
    fun testCalloutContrastRequirements() {
        for (set in CalloutSet.entries) {
            val colors = calloutColors(set, dark = false)
            val styles = listOf(
                "tip" to colors.tip,
                "note" to colors.note,
                "warning" to colors.warning,
                "danger" to colors.danger,
            )
            for ((typeName, style) in styles) {
                val accentContrast = contrastRatio(style.accent, style.container)
                assertTrue(
                    "Callout set $set, type $typeName, pair accent vs container: contrast was $accentContrast, expected >= 4.5",
                    accentContrast >= 4.5,
                )

                val linkContrast = contrastRatio(style.link, style.container)
                assertTrue(
                    "Callout set $set, type $typeName, pair link vs container: contrast was $linkContrast, expected >= 4.5",
                    linkContrast >= 4.5,
                )
            }
        }
    }
}
