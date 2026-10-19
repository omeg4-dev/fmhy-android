package io.github.omeg4dev.fmhy.data

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ColorSource { FMHY, MATERIAL_YOU }   // later: CUSTOM
enum class SectionLayout { CONTINUOUS, CATEGORIES }

data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.FMHY,
    val pureBlack: Boolean = false,
    val accentSeed: Int = 0xFF7C5CFF.toInt(),   // FMHY violet; ARGB
    val sectionLayout: SectionLayout = SectionLayout.CONTINUOUS,
)
