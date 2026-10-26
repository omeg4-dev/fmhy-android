package io.github.omeg4dev.fmhy.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.pow

class PaletteVividnessTest {

    private fun srgbToLinear(c: Float): Double =
        if (c <= 0.04045f) c.toDouble() / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun oklchChroma(color: Color): Double {
        val r = srgbToLinear(color.red)
        val g = srgbToLinear(color.green)
        val b = srgbToLinear(color.blue)

        // Linear sRGB to LMS
        val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
        val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
        val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b

        val l_ = Math.cbrt(l)
        val m_ = Math.cbrt(m)
        val s_ = Math.cbrt(s)

        // LMS to OKLab a and b
        val a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
        val bLab = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_

        // OKLCH Chroma
        return hypot(a, bLab)
    }

    /**
     * Allowed exceptions for impossible cases.
     * Amber at selected tone ~48-49 is physically gamut-limited in sRGB: yellow/amber cannot achieve high chroma
     * at mid tones (max OKLCH chroma ~0.116 vs seed primaryContainer chroma ~0.1705 at tone 78).
     */
    private val exceptions: Set<String> = setOf(
        "Amber:light:primaryContainer",
        "Amber:dark:primaryContainer",
    )

    @Test
    fun testPaletteVividnessAgainstSeedColorScheme() {
        println("%-10s | %-5s | %-16s | %-10s | %-10s | %s".format("Palette", "Mode", "Kind", "Old Chroma", "New Chroma", "Status"))
        println("-".repeat(70))

        val failures = mutableListOf<String>()

        for (palette in ACCENT_PALETTES) {
            for (dark in listOf(false, true)) {
                val modeStr = if (dark) "dark" else "light"
                val seedScheme = seedColorScheme(palette.seed, dark)
                val palScheme = paletteColorScheme(palette.seed, dark)

                val checks = listOf(
                    "primary" to (seedScheme.primary to palScheme.primary),
                    "primaryContainer" to (seedScheme.primaryContainer to palScheme.primaryContainer),
                )

                for ((kind, colors) in checks) {
                    val oldChroma = oklchChroma(colors.first)
                    val newChroma = oklchChroma(colors.second)
                    val key = "${palette.name}:$modeStr:$kind"
                    val isException = key in exceptions

                    val passed = newChroma >= oldChroma * 0.95
                    val status = when {
                        passed -> "PASS"
                        isException -> "EXCEPTION"
                        else -> "FAIL"
                    }

                    println("%-10s | %-5s | %-16s | %10.4f | %10.4f | %s".format(
                        palette.name, modeStr, kind, oldChroma, newChroma, status
                    ))

                    if (!passed && !isException) {
                        failures.add("$key: new chroma $newChroma < old chroma $oldChroma * 0.95 (${oldChroma * 0.95})")
                    }
                }
            }
        }

        assertTrue(
            "Vividness check failed for:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }
}
