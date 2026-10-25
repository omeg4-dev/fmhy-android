package io.github.omeg4dev.fmhy.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import kotlin.math.abs
import kotlin.math.pow

data class CalloutStyle(
    val container: Color,
    val border: Color,
    val accent: Color,
    val link: Color,
)

data class CalloutColors(
    val tip: CalloutStyle,
    val note: CalloutStyle,
    val warning: CalloutStyle,
    val danger: CalloutStyle,
)

enum class CalloutSet { GREEN_TEAL, BLUE }

val LocalCalloutColors = staticCompositionLocalOf { calloutColors(CalloutSet.GREEN_TEAL, dark = false) }

// --- Hand-tuned fixed callout colours ---

private val NOTE_LIGHT = CalloutStyle(
    container = Color(0xFFECEFF1),
    border = Color(0xFFB0BEC5),
    accent = Color(0xFF37474F),
    link = Color(0xFF1E3A5F),
)

private val NOTE_DARK = CalloutStyle(
    container = Color(0xFF1E252B),
    border = Color(0xFF37474F),
    accent = Color(0xFF90A4AE),
    link = Color(0xFFB0BEC5),
)

private val WARNING_LIGHT = CalloutStyle(
    container = Color(0xFFFFF0D4),
    border = Color(0xFFFFCC80),
    accent = Color(0xFFA34900), // HCT hue ~48° (in 30..65°)
    link = Color(0xFF783300),
)

private val WARNING_DARK = CalloutStyle(
    container = Color(0xFF332000),
    border = Color(0xFF5C3A00),
    accent = Color(0xFFFFAE81), // HCT hue ~48° (in 30..65°)
    link = Color(0xFFFFCCA8),
)

private val DANGER_LIGHT = CalloutStyle(
    container = Color(0xFFFCE4E4),
    border = Color(0xFFF5A9A9),
    accent = Color(0xFFB4214A), // HCT hue ~10° (in 0..20°)
    link = Color(0xFF82002A),
)

private val DANGER_DARK = CalloutStyle(
    container = Color(0xFF3E1414),
    border = Color(0xFF6B2323),
    accent = Color(0xFFFF90A1), // HCT hue ~10° (in 0..20°)
    link = Color(0xFFFFB2BF),
)

private val TIP_GREEN_TEAL_LIGHT = CalloutStyle(
    container = Color(0xFFD7EDE1), // HCT tone ~92, hue ~170°, calm light callout
    border = Color(0xFF5DBFA8),
    accent = Color(0xFF006C50),    // HCT hue ~170° (in 165..175°)
    link = Color(0xFF004D38),
)

private val TIP_GREEN_TEAL_DARK = CalloutStyle(
    container = Color(0xFF1B4A41), // luminance ~0.055, distinct from dark surfaceContainer*
    border = Color(0xFF28685C),
    accent = Color(0xFF71DAB3),    // HCT hue ~170° (in 165..175°)
    link = Color(0xFF8CE3C4),
)

private val TIP_BLUE_LIGHT = CalloutStyle(
    container = Color(0xFFDBE9FB),
    border = Color(0xFF75A9F9),
    accent = Color(0xFF005E93),    // HCT hue ~248° (in 235..250°)
    link = Color(0xFF00446C),
)

private val TIP_BLUE_DARK = CalloutStyle(
    container = Color(0xFF224274), // luminance ~0.055, distinct from dark surfaceContainer*
    border = Color(0xFF305C9E),
    accent = Color(0xFF6DC3FF),    // HCT hue ~248° (in 235..250°)
    link = Color(0xFF99D4FF),
)

fun calloutColors(set: CalloutSet, dark: Boolean): CalloutColors = CalloutColors(
    tip = when (set) {
        CalloutSet.GREEN_TEAL -> if (dark) TIP_GREEN_TEAL_DARK else TIP_GREEN_TEAL_LIGHT
        CalloutSet.BLUE -> if (dark) TIP_BLUE_DARK else TIP_BLUE_LIGHT
    },
    note = if (dark) NOTE_DARK else NOTE_LIGHT,
    warning = if (dark) WARNING_DARK else WARNING_LIGHT,
    danger = if (dark) DANGER_DARK else DANGER_LIGHT,
)

fun pickCalloutSet(seedArgb: Int): CalloutSet {
    val seedHue = Hct.fromInt(seedArgb).hue
    val greenTipHue = Hct.fromInt(TIP_GREEN_TEAL_LIGHT.accent.toArgb()).hue
    val blueTipHue = Hct.fromInt(TIP_BLUE_LIGHT.accent.toArgb()).hue
    val distGreen = circularHueDistance(seedHue, greenTipHue)
    val distBlue = circularHueDistance(seedHue, blueTipHue)
    return if (distGreen >= distBlue) CalloutSet.GREEN_TEAL else CalloutSet.BLUE
}

fun materialYouCalloutColors(primary: Color, surface: Color, dark: Boolean): CalloutColors {
    val primaryHue = Hct.fromInt(primary.toArgb()).hue
    val greenSet = calloutColors(CalloutSet.GREEN_TEAL, dark)
    val nudgedGreenTip = nudgeCalloutStyle(greenSet.tip, primaryHue, maxNudgeDeg = 15.0)

    val c = contrastRatio(nudgedGreenTip.container, surface)
    val hueDist = circularHueDistance(Hct.fromInt(nudgedGreenTip.accent.toArgb()).hue, primaryHue)

    return if (c < 1.15 || hueDist < 40.0) {
        val blueSet = calloutColors(CalloutSet.BLUE, dark)
        val nudgedBlueTip = nudgeCalloutStyle(blueSet.tip, primaryHue, maxNudgeDeg = 15.0)
        blueSet.copy(tip = nudgedBlueTip)
    } else {
        greenSet.copy(tip = nudgedGreenTip)
    }
}

private fun nudgeCalloutStyle(style: CalloutStyle, targetHue: Double, maxNudgeDeg: Double): CalloutStyle {
    val accentHct = Hct.fromInt(style.accent.toArgb())
    val diff = (targetHue - accentHct.hue) % 360.0
    val shortestDiff = when {
        diff > 180.0 -> diff - 360.0
        diff < -180.0 -> diff + 360.0
        else -> diff
    }
    val nudge = shortestDiff.coerceIn(-maxNudgeDeg, maxNudgeDeg)
    if (abs(nudge) < 0.001) return style

    fun nudgeColor(c: Color): Color {
        val hct = Hct.fromInt(c.toArgb())
        val newHue = (hct.hue + nudge + 360.0) % 360.0
        return Color(Hct.from(newHue, hct.chroma, hct.tone).toInt())
    }

    return CalloutStyle(
        container = nudgeColor(style.container),
        border = nudgeColor(style.border),
        accent = nudgeColor(style.accent),
        link = nudgeColor(style.link),
    )
}

// --- Contrast and color math utilities ---

private fun srgbToLinear(c: Float): Double =
    if (c <= 0.04045f) c.toDouble() / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

internal fun relativeLuminance(color: Color): Double {
    val r = srgbToLinear(color.red)
    val g = srgbToLinear(color.green)
    val b = srgbToLinear(color.blue)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

internal fun contrastRatio(c1: Color, c2: Color): Double {
    val l1 = relativeLuminance(c1)
    val l2 = relativeLuminance(c2)
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}

internal fun circularHueDistance(h1: Double, h2: Double): Double {
    val diff = abs(h1 - h2) % 360.0
    return if (diff > 180.0) 360.0 - diff else diff
}
