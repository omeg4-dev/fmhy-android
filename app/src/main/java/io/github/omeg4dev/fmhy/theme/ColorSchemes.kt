package io.github.omeg4dev.fmhy.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeFidelity

data class AccentPreset(val name: String, val seed: Int)

val ACCENT_PRESETS: List<AccentPreset> = ACCENT_PALETTES.map { AccentPreset(it.name, it.seed) }

fun seedColorScheme(seedArgb: Int, dark: Boolean): ColorScheme {
    val scheme = SchemeFidelity(
        Hct.fromInt(seedArgb),
        dark,
        0.0,
        ColorSpec.SpecVersion.SPEC_2021,
        DynamicScheme.Platform.PHONE,
    )
    val mdc = MaterialDynamicColors()
    return ColorScheme(
        primary = Color(mdc.primary().getArgb(scheme)),
        onPrimary = Color(mdc.onPrimary().getArgb(scheme)),
        primaryContainer = Color(mdc.primaryContainer().getArgb(scheme)),
        onPrimaryContainer = Color(mdc.onPrimaryContainer().getArgb(scheme)),
        inversePrimary = Color(mdc.inversePrimary().getArgb(scheme)),
        secondary = Color(mdc.secondary().getArgb(scheme)),
        onSecondary = Color(mdc.onSecondary().getArgb(scheme)),
        secondaryContainer = Color(mdc.secondaryContainer().getArgb(scheme)),
        onSecondaryContainer = Color(mdc.onSecondaryContainer().getArgb(scheme)),
        tertiary = Color(mdc.tertiary().getArgb(scheme)),
        onTertiary = Color(mdc.onTertiary().getArgb(scheme)),
        tertiaryContainer = Color(mdc.tertiaryContainer().getArgb(scheme)),
        onTertiaryContainer = Color(mdc.onTertiaryContainer().getArgb(scheme)),
        background = Color(mdc.background().getArgb(scheme)),
        onBackground = Color(mdc.onBackground().getArgb(scheme)),
        surface = Color(mdc.surface().getArgb(scheme)),
        onSurface = Color(mdc.onSurface().getArgb(scheme)),
        surfaceVariant = Color(mdc.surfaceVariant().getArgb(scheme)),
        onSurfaceVariant = Color(mdc.onSurfaceVariant().getArgb(scheme)),
        surfaceTint = Color(mdc.surfaceTint().getArgb(scheme)),
        inverseSurface = Color(mdc.inverseSurface().getArgb(scheme)),
        inverseOnSurface = Color(mdc.inverseOnSurface().getArgb(scheme)),
        error = Color(mdc.error().getArgb(scheme)),
        onError = Color(mdc.onError().getArgb(scheme)),
        errorContainer = Color(mdc.errorContainer().getArgb(scheme)),
        onErrorContainer = Color(mdc.onErrorContainer().getArgb(scheme)),
        outline = Color(mdc.outline().getArgb(scheme)),
        outlineVariant = Color(mdc.outlineVariant().getArgb(scheme)),
        scrim = Color(mdc.scrim().getArgb(scheme)),
        surfaceBright = Color(mdc.surfaceBright().getArgb(scheme)),
        surfaceDim = Color(mdc.surfaceDim().getArgb(scheme)),
        surfaceContainer = Color(mdc.surfaceContainer().getArgb(scheme)),
        surfaceContainerHigh = Color(mdc.surfaceContainerHigh().getArgb(scheme)),
        surfaceContainerHighest = Color(mdc.surfaceContainerHighest().getArgb(scheme)),
        surfaceContainerLow = Color(mdc.surfaceContainerLow().getArgb(scheme)),
        surfaceContainerLowest = Color(mdc.surfaceContainerLowest().getArgb(scheme)),
        primaryFixed = Color(mdc.primaryFixed().getArgb(scheme)),
        primaryFixedDim = Color(mdc.primaryFixedDim().getArgb(scheme)),
        onPrimaryFixed = Color(mdc.onPrimaryFixed().getArgb(scheme)),
        onPrimaryFixedVariant = Color(mdc.onPrimaryFixedVariant().getArgb(scheme)),
        secondaryFixed = Color(mdc.secondaryFixed().getArgb(scheme)),
        secondaryFixedDim = Color(mdc.secondaryFixedDim().getArgb(scheme)),
        onSecondaryFixed = Color(mdc.onSecondaryFixed().getArgb(scheme)),
        onSecondaryFixedVariant = Color(mdc.onSecondaryFixedVariant().getArgb(scheme)),
        tertiaryFixed = Color(mdc.tertiaryFixed().getArgb(scheme)),
        tertiaryFixedDim = Color(mdc.tertiaryFixedDim().getArgb(scheme)),
        onTertiaryFixed = Color(mdc.onTertiaryFixed().getArgb(scheme)),
        onTertiaryFixedVariant = Color(mdc.onTertiaryFixedVariant().getArgb(scheme)),
    )
}

fun ColorScheme.toPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF121214),
    surfaceContainerHigh = Color(0xFF1A1A1D),
    surfaceContainerHighest = Color(0xFF232327),
)

fun ColorScheme.withLightCardTiers(): ColorScheme {
    val scLow = lerp(surface, onSurface, 0.03f)
    val sc = lerp(surface, onSurface, 0.05f)
    val scHigh = lerp(surface, onSurface, 0.08f)
    val scHighest = lerp(surface, onSurface, 0.11f)

    val outVariant = if (contrastRatio(outlineVariant, surface) < 1.6) {
        lerp(surface, onSurface, 0.22f)
    } else {
        outlineVariant
    }

    var out = outline
    var f = 0.0f
    while (contrastRatio(out, surface) < 3.0 && f <= 1.0f) {
        f += 0.05f
        out = lerp(surface, onSurface, f)
    }

    return copy(
        surfaceContainerLowest = surface,
        surfaceContainerLow = scLow,
        surfaceContainer = sc,
        surfaceContainerHigh = scHigh,
        surfaceContainerHighest = scHighest,
        outlineVariant = outVariant,
        outline = out,
    )
}

fun ColorScheme.withVividMaterialYou(context: Context, dark: Boolean): ColorScheme {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val resId = if (dark) android.R.color.system_accent1_300 else android.R.color.system_accent1_500
    val vividContainer = Color(context.getColor(resId))
    if (contrastRatio(vividContainer, surface) < 3.0) return this

    val onContainerCandidates = listOf(onSurface, surface)
    val onContainer = onContainerCandidates.maxByOrNull { contrastRatio(it, vividContainer) } ?: onSurface

    return copy(
        primaryContainer = vividContainer,
        onPrimaryContainer = onContainer,
        secondaryContainer = vividContainer,
        onSecondaryContainer = onContainer,
    )
}
