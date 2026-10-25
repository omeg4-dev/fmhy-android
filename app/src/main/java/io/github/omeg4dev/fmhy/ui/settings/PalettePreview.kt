package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import io.github.omeg4dev.fmhy.theme.calloutColorsForSeed
import io.github.omeg4dev.fmhy.theme.materialYouCalloutColors
import io.github.omeg4dev.fmhy.theme.paletteColorScheme

data class PaletteDots(
    val accent: Color,
    val selected: Color,
    val tip: Color,
)

fun paletteDots(seedArgb: Int, dark: Boolean): PaletteDots {
    val scheme = paletteColorScheme(seedArgb, dark)
    val callout = calloutColorsForSeed(seedArgb, dark)
    return PaletteDots(
        accent = scheme.primary,
        selected = scheme.primaryContainer,
        tip = callout.tip.accent,
    )
}

fun materialYouDots(scheme: ColorScheme, dark: Boolean): PaletteDots {
    val callout = materialYouCalloutColors(scheme.primary, scheme.surface, dark)
    return PaletteDots(
        accent = scheme.primary,
        selected = scheme.primaryContainer,
        tip = callout.tip.accent,
    )
}
