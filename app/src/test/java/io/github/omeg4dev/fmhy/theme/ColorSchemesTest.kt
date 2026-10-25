package io.github.omeg4dev.fmhy.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ColorSchemesTest {

    private fun srgbToLinear(c: Float): Double {
        return if (c <= 0.04045f) {
            c.toDouble() / 12.92
        } else {
            ((c + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun relativeLuminance(color: Color): Double {
        val r = srgbToLinear(color.red)
        val g = srgbToLinear(color.green)
        val b = srgbToLinear(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(c1: Color, c2: Color): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private val sharedSeeds = (ACCENT_PRESETS.map { it.seed } + listOf(
        0xFF7C5CFF.toInt(),
        0xFF00897B.toInt(),
        0xFFE53935.toInt(),
    )).distinct()

    @Test
    fun testWcagContrastRequirements() {
        for (seed in sharedSeeds) {
            for (dark in listOf(false, true)) {
                val scheme = paletteColorScheme(seed, dark)

                val onSurfaceContrast = contrast(scheme.onSurface, scheme.surface)
                assertTrue(
                    "contrast(onSurface, surface) was $onSurfaceContrast, expected >= 7.0 for seed $seed, dark=$dark",
                    onSurfaceContrast >= 7.0,
                )

                val onPrimaryContrast = contrast(scheme.onPrimary, scheme.primary)
                assertTrue(
                    "contrast(onPrimary, primary) was $onPrimaryContrast, expected >= 4.5 for seed $seed, dark=$dark",
                    onPrimaryContrast >= 4.5,
                )

                val onSurfaceVariantContrast = contrast(scheme.onSurfaceVariant, scheme.surfaceContainer)
                assertTrue(
                    "contrast(onSurfaceVariant, surfaceContainer) was $onSurfaceVariantContrast, expected >= 4.5 for seed $seed, dark=$dark",
                    onSurfaceVariantContrast >= 4.5,
                )
            }
        }
    }

    @Test
    fun testSurfaceLuminanceRequirements() {
        for (seed in sharedSeeds) {
            val lightScheme = paletteColorScheme(seed, dark = false)
            val lightLuminance = relativeLuminance(lightScheme.surface)
            assertTrue(
                "light surface luminance was $lightLuminance, expected > 0.8 for seed $seed",
                lightLuminance > 0.8,
            )

            val darkScheme = paletteColorScheme(seed, dark = true)
            val darkLuminance = relativeLuminance(darkScheme.surface)
            assertTrue(
                "dark surface luminance was $darkLuminance, expected < 0.05 for seed $seed",
                darkLuminance < 0.05,
            )
        }
    }

    @Test
    fun testToPureBlackModifiesSurfaceLeavesPrimaryUnchanged() {
        for (seed in sharedSeeds) {
            val darkScheme = paletteColorScheme(seed, dark = true)
            val pureBlackScheme = darkScheme.toPureBlack()

            assertEquals(Color.Black, pureBlackScheme.surface)
            assertEquals(Color.Black, pureBlackScheme.background)
            assertEquals(Color.Black, pureBlackScheme.surfaceContainerLowest)
            assertEquals(Color.Black, pureBlackScheme.surfaceDim)
            assertEquals(Color(0xFF0B0B0D), pureBlackScheme.surfaceContainerLow)
            assertEquals(Color(0xFF121214), pureBlackScheme.surfaceContainer)
            assertEquals(Color(0xFF1A1A1D), pureBlackScheme.surfaceContainerHigh)
            assertEquals(Color(0xFF232327), pureBlackScheme.surfaceContainerHighest)
            assertEquals(darkScheme.primary, pureBlackScheme.primary)
        }
    }

    @Test
    fun testColourfulnessSliderChromaDifference() {
        val seedMuted = Hct.from(0.0, 20.0, 50.0).toInt()
        val seedSaturated = Hct.from(0.0, 100.0, 50.0).toInt()
        val schemeMuted = seedColorScheme(seedMuted, dark = false)
        val schemeSaturated = seedColorScheme(seedSaturated, dark = false)

        val chromaMuted = Hct.fromInt(schemeMuted.primary.toArgb()).chroma
        val chromaSaturated = Hct.fromInt(schemeSaturated.primary.toArgb()).chroma
        val diff = chromaSaturated - chromaMuted

        assertTrue("Expected chroma difference >= 25, got $diff (muted: $chromaMuted, saturated: $chromaSaturated)", diff >= 25.0)
    }
}
