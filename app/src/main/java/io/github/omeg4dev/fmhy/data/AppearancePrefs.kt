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
    val textScale: Float = 1.0f,
    val sectionOrder: SectionOrder = SectionOrder.WEBSITE,
    val showRandomPick: Boolean = true,
    val starredOnly: Boolean = false,
    val showIndexes: Boolean = true,
    val hapticFeedback: Boolean = true,
    val websiteStyle: Boolean = false,
    val appIcon: AppIcon = AppIcon.DEFAULT,
)
