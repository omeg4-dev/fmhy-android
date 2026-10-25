package io.github.omeg4dev.fmhy.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.ThemeMode

import io.github.omeg4dev.fmhy.ui.common.LocalHapticsEnabled
import io.github.omeg4dev.fmhy.ui.common.SilentHapticFeedback

val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun FMHYTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val dark = when (appearance.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val baseScheme = when {
        appearance.colorSource == ColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context).withLightCardTiers()
        }
        else -> remember(appearance.accentSeed, dark) {
            paletteColorScheme(appearance.accentSeed, dark)
        }
    }

    val colorScheme = if (dark && appearance.pureBlack) {
        baseScheme.toPureBlack()
    } else {
        baseScheme
    }

    val calloutColors = remember(appearance.colorSource, appearance.accentSeed, colorScheme, dark) {
        if (appearance.colorSource == ColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            materialYouCalloutColors(colorScheme.primary, colorScheme.surface, dark)
        } else {
            calloutColorsForSeed(appearance.accentSeed, dark)
        }
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides dark,
        LocalHapticsEnabled provides appearance.hapticFeedback,
        LocalHapticFeedback provides SilentHapticFeedback,
        LocalCalloutColors provides calloutColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
        ) {
            val d = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(d.density, d.fontScale * appearance.textScale),
                content = content,
            )
        }
    }
}
