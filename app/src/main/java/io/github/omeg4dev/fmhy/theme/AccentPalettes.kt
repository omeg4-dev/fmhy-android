package io.github.omeg4dev.fmhy.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.hct.Hct

data class PaletteColors(
    val accent: Color,
    val onAccent: Color,
    val selected: Color,
    val onSelected: Color,          // selected bubbles/options/bottom-bar item container: clearly stronger than surfaceContainer*
    val accentContainer: Color,
    val onAccentContainer: Color,
)

data class AccentPalette(
    val name: String,
    val seed: Int,
    val calloutSet: CalloutSet,
    val light: PaletteColors,
    val dark: PaletteColors,
)

private fun buildLightAccent(h: Double, c: Double): Color {
    val initialHct = Hct.from(h, c, 40.0)
    if (initialHct.chroma >= 0.95 * c) {
        return Color(initialHct.toInt())
    }
    val baseSurface = Color(Hct.from(h, 4.0, 98.0).toInt())
    for (t in 41..50) {
        val candidate = Hct.from(h, c, t.toDouble())
        val col = Color(candidate.toInt())
        if (contrastRatio(Color.White, col) >= 4.5 && contrastRatio(col, baseSurface) >= 3.0) {
            if (candidate.chroma >= 0.95 * c) {
                return col
            }
        }
    }
    return Color(initialHct.toInt())
}

private fun buildPaletteColors(seedArgb: Int, dark: Boolean): PaletteColors {
    val hct = Hct.fromInt(seedArgb)
    val h = hct.hue
    val c = hct.chroma
    val selectedChroma = maxOf(c * 1.05, 24.0)

    var selectedTone = 48.0
    val candHct = Hct.from(h, selectedChroma, 49.5)
    val candColor = Color(candHct.toInt())
    if (contrastRatio(Color.White, candColor) >= 4.5 && candHct.chroma > Hct.from(h, selectedChroma, 48.0).chroma) {
        selectedTone = 49.5
    }

    return if (dark) {
        PaletteColors(
            accent = Color(Hct.from(h, c, 80.0).toInt()),
            onAccent = Color(Hct.from(h, (c * 0.8).coerceIn(20.0, 40.0), 10.0).toInt()),
            selected = Color(Hct.from(h, selectedChroma, selectedTone).toInt()),
            onSelected = Color.White,
            accentContainer = Color(Hct.from(h, (c * 0.65).coerceIn(14.0, 36.0), 30.0).toInt()),
            onAccentContainer = Color(Hct.from(h, 15.0, 90.0).toInt()),
        )
    } else {
        PaletteColors(
            accent = buildLightAccent(h, c),
            onAccent = Color.White,
            selected = Color(Hct.from(h, selectedChroma, selectedTone).toInt()),
            onSelected = Color.White,
            accentContainer = Color(Hct.from(h, (c * 0.65).coerceIn(12.0, 36.0), 90.0).toInt()),
            onAccentContainer = Color(Hct.from(h, 30.0, 10.0).toInt()),
        )
    }
}

private fun createAccentPalette(name: String, seed: Int, calloutSet: CalloutSet): AccentPalette =
    AccentPalette(
        name = name,
        seed = seed,
        calloutSet = calloutSet,
        light = buildPaletteColors(seed, dark = false),
        dark = buildPaletteColors(seed, dark = true),
    )

val ACCENT_PALETTES: List<AccentPalette> = listOf(
    createAccentPalette("Violet", 0xFF7C5CFF.toInt(), CalloutSet.GREEN_TEAL),
    createAccentPalette("Blue", 0xFF1E88E5.toInt(), CalloutSet.GREEN_TEAL),
    createAccentPalette("Teal", 0xFF00897B.toInt(), CalloutSet.BLUE),
    createAccentPalette("Green", 0xFF43A047.toInt(), CalloutSet.BLUE),
    createAccentPalette("Lime", 0xFF7CB342.toInt(), CalloutSet.BLUE),
    createAccentPalette("Amber", 0xFFFFB300.toInt(), CalloutSet.GREEN_TEAL),
    createAccentPalette("Orange", 0xFFF4511E.toInt(), CalloutSet.GREEN_TEAL),
    createAccentPalette("Red", 0xFFE53935.toInt(), CalloutSet.GREEN_TEAL),
    createAccentPalette("Pink", 0xFFD81B60.toInt(), CalloutSet.GREEN_TEAL),
    createAccentPalette("Grey", 0xFF78909C.toInt(), CalloutSet.GREEN_TEAL),
)

fun paletteFor(seedArgb: Int): AccentPalette? = ACCENT_PALETTES.firstOrNull { it.seed == seedArgb }

fun AccentPalette.colors(dark: Boolean): PaletteColors = if (dark) this.dark else this.light

fun paletteColorScheme(seedArgb: Int, dark: Boolean): ColorScheme {
    val base = seedColorScheme(seedArgb, dark)
    val palette = paletteFor(seedArgb) ?: return if (dark) base else base.withLightCardTiers()
    val colors = palette.colors(dark)

    val scheme = base.copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.selected,
        onPrimaryContainer = colors.onSelected,
        secondaryContainer = colors.selected,
        onSecondaryContainer = colors.onSelected,
        secondary = colors.accent,
        onSecondary = colors.onAccent,
        tertiary = colors.accent,
        onTertiary = colors.onAccent,
        tertiaryContainer = colors.accentContainer,
        onTertiaryContainer = colors.onAccentContainer,
        secondaryFixed = colors.accentContainer,
        secondaryFixedDim = colors.selected,
        onSecondaryFixed = colors.onAccentContainer,
        onSecondaryFixedVariant = colors.onSelected,
        tertiaryFixed = colors.accentContainer,
        tertiaryFixedDim = colors.selected,
        onTertiaryFixed = colors.onAccentContainer,
        onTertiaryFixedVariant = colors.onSelected,
        surfaceTint = colors.accent,
        inversePrimary = if (dark) palette.light.accent else palette.dark.accent,
    )
    return if (dark) scheme else scheme.withLightCardTiers()
}

fun calloutColorsForSeed(seedArgb: Int, dark: Boolean): CalloutColors {
    val palette = paletteFor(seedArgb)
    val set = palette?.calloutSet ?: pickCalloutSet(seedArgb)
    return calloutColors(set, dark)
}
