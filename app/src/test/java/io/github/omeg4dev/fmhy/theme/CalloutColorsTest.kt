package io.github.omeg4dev.fmhy.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalloutColorsTest {

    @Test
    fun testTipContainerDiffersFromAccentSelectedAndSurfaceContainers() {
        // Covering every palette x {light, dark, pure black}
        val modes = listOf(
            Triple("light", false, false),
            Triple("dark", true, false),
            Triple("pureBlack", true, true),
        )

        for (palette in ACCENT_PALETTES) {
            for ((modeName, dark, isPureBlack) in modes) {
                val callout = calloutColorsForSeed(palette.seed, dark)
                val baseScheme = paletteColorScheme(palette.seed, dark)
                val scheme = if (isPureBlack) baseScheme.toPureBlack() else baseScheme
                val paletteColors = palette.colors(dark)

                val tipContainer = callout.tip.container
                val tipAccent = callout.tip.accent
                val paletteAccent = paletteColors.accent
                val paletteSelected = paletteColors.selected

                // 1a. Contrast against palette accent >= 1.15
                val accentContrast = contrastRatio(tipContainer, paletteAccent)
                assertTrue(
                    "TIP container vs palette accent contrast was $accentContrast, expected >= 1.15 for ${palette.name} ($modeName)",
                    accentContrast >= 1.15,
                )

                // 1b. Contrast against selected >= 1.15
                val selectedContrast = contrastRatio(tipContainer, paletteSelected)
                assertTrue(
                    "TIP container vs palette selected contrast was $selectedContrast, expected >= 1.15 for ${palette.name} ($modeName)",
                    selectedContrast >= 1.15,
                )

                // 1c. Contrast against every surfaceContainer* >= 1.15
                val surfaceContainers = listOf(
                    "surfaceContainerLowest" to scheme.surfaceContainerLowest,
                    "surfaceContainerLow" to scheme.surfaceContainerLow,
                    "surfaceContainer" to scheme.surfaceContainer,
                    "surfaceContainerHigh" to scheme.surfaceContainerHigh,
                    "surfaceContainerHighest" to scheme.surfaceContainerHighest,
                )
                for ((scName, scColor) in surfaceContainers) {
                    val scContrast = contrastRatio(tipContainer, scColor)
                    assertTrue(
                        "TIP container vs $scName contrast was $scContrast, expected >= 1.15 for ${palette.name} ($modeName)",
                        scContrast >= 1.15,
                    )
                }

                // 1d. TIP accent hue >= 40° from palette accent hue
                val tipAccentHue = Hct.fromInt(tipAccent.toArgb()).hue
                val paletteAccentHue = Hct.fromInt(paletteAccent.toArgb()).hue
                val hueDist = circularHueDistance(tipAccentHue, paletteAccentHue)
                assertTrue(
                    "TIP accent hue ($tipAccentHue) vs palette accent hue ($paletteAccentHue) distance was $hueDist, expected >= 40 for ${palette.name} ($modeName)",
                    hueDist >= 40.0,
                )
            }
        }
    }

    @Test
    fun testCalloutStyleContrasts() {
        // TIP accent vs TIP container contrast >= 3.0; TIP link vs TIP container >= 4.5;
        // same for note/warning/danger link/accent.
        for (set in CalloutSet.entries) {
            for (dark in listOf(false, true)) {
                val colors = calloutColors(set, dark)
                val styles = listOf(
                    "tip" to colors.tip,
                    "note" to colors.note,
                    "warning" to colors.warning,
                    "danger" to colors.danger,
                )
                for ((styleName, style) in styles) {
                    val accentContrast = contrastRatio(style.accent, style.container)
                    assertTrue(
                        "$styleName accent vs container contrast was $accentContrast, expected >= 3.0 for set=$set, dark=$dark",
                        accentContrast >= 3.0,
                    )

                    val linkContrast = contrastRatio(style.link, style.container)
                    assertTrue(
                        "$styleName link vs container contrast was $linkContrast, expected >= 4.5 for set=$set, dark=$dark",
                        linkContrast >= 4.5,
                    )
                }
            }
        }
    }

    @Test
    fun testPresetCalloutSets() {
        // Teal, Green, Lime -> BLUE; the other 7 -> GREEN_TEAL.
        val bluePresets = setOf("Teal", "Green", "Lime")
        for (palette in ACCENT_PALETTES) {
            val expected = if (palette.name in bluePresets) CalloutSet.BLUE else CalloutSet.GREEN_TEAL
            assertEquals(
                "Palette ${palette.name} expected calloutSet $expected, got ${palette.calloutSet}",
                expected,
                palette.calloutSet,
            )
        }
    }

    @Test
    fun testWarningAndDangerAccentHues() {
        // warning accent hue in 30..65°, danger accent hue in 340..360 or 0..20
        for (set in CalloutSet.entries) {
            for (dark in listOf(false, true)) {
                val colors = calloutColors(set, dark)

                val warnHue = Hct.fromInt(colors.warning.accent.toArgb()).hue
                assertTrue(
                    "Warning accent hue was $warnHue, expected in 30..65° for set=$set, dark=$dark",
                    warnHue in 30.0..65.0,
                )

                val dangerHue = Hct.fromInt(colors.danger.accent.toArgb()).hue
                assertTrue(
                    "Danger accent hue was $dangerHue, expected in 340..360 or 0..20 for set=$set, dark=$dark",
                    dangerHue in 340.0..360.0 || dangerHue in 0.0..20.0,
                )
            }
        }
    }

    @Test
    fun testPickCalloutSetFurthestFromSeed() {
        // seed hue ~150 (green) -> BLUE; seed hue ~280 (violet) -> GREEN_TEAL; result always the farther one.
        val seedGreen = Hct.from(150.0, 50.0, 50.0).toInt()
        assertEquals(CalloutSet.BLUE, pickCalloutSet(seedGreen))

        val seedViolet = Hct.from(280.0, 50.0, 50.0).toInt()
        assertEquals(CalloutSet.GREEN_TEAL, pickCalloutSet(seedViolet))

        // Check full 360 circle: result is always the farther one
        val greenTipHue = Hct.fromInt(calloutColors(CalloutSet.GREEN_TEAL, false).tip.accent.toArgb()).hue
        val blueTipHue = Hct.fromInt(calloutColors(CalloutSet.BLUE, false).tip.accent.toArgb()).hue

        for (deg in 0 until 360 step 5) {
            val seed = Hct.from(deg.toDouble(), 50.0, 50.0).toInt()
            val chosen = pickCalloutSet(seed)
            val dGreen = circularHueDistance(deg.toDouble(), greenTipHue)
            val dBlue = circularHueDistance(deg.toDouble(), blueTipHue)
            val expected = if (dGreen >= dBlue) CalloutSet.GREEN_TEAL else CalloutSet.BLUE
            assertEquals("At hue $deg°, expected $expected but got $chosen", expected, chosen)
        }
    }

    @Test
    fun testMaterialYouCalloutColorsRequirements() {
        // TIP container vs the given surface contrast >= 1.15 and hue distance of TIP accent
        // from given primary >= 30° for primaries at hue 0,60,120,180,240,300 (chroma 60 tone 50 via Hct.from).
        val primaryHues = listOf(0.0, 60.0, 120.0, 180.0, 240.0, 300.0)

        for (h in primaryHues) {
            val primary = Color(Hct.from(h, 60.0, 50.0).toInt())

            for (dark in listOf(false, true)) {
                // Typical phone surfaces for Material You: light tone ~98, dark tone ~6
                val surface = if (dark) Color(Hct.from(h, 4.0, 6.0).toInt()) else Color(Hct.from(h, 4.0, 98.0).toInt())
                val callout = materialYouCalloutColors(primary, surface, dark)

                val containerContrast = contrastRatio(callout.tip.container, surface)
                assertTrue(
                    "TIP container vs surface contrast was $containerContrast, expected >= 1.15 for primary hue $h, dark=$dark",
                    containerContrast >= 1.15,
                )

                val tipAccentHue = Hct.fromInt(callout.tip.accent.toArgb()).hue
                val hueDist = circularHueDistance(tipAccentHue, h)
                assertTrue(
                    "TIP accent hue ($tipAccentHue) vs primary hue ($h) distance was $hueDist, expected >= 30 for dark=$dark",
                    hueDist >= 30.0,
                )
            }
        }
    }

    @Test
    fun testPaletteForMapping() {
        // paletteFor maps each of the 10 ACCENT_PRESETS seeds to the same-named palette; unknown seed -> null.
        for (preset in ACCENT_PRESETS) {
            val palette = paletteFor(preset.seed)
            assertNotNull("paletteFor(${preset.seed}) returned null for ${preset.name}", palette)
            assertEquals(preset.name, palette?.name)
            assertEquals(preset.seed, palette?.seed)
        }

        val unknownPalette = paletteFor(0x12345678)
        assertNull("Expected unknown seed to return null", unknownPalette)
    }
}
