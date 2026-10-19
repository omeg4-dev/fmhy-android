package io.github.omeg4dev.fmhy

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeNavKey : NavKey

@Serializable
data class SectionNavKey(val sectionId: String, val anchor: String? = null) : NavKey

@Serializable
data class CategoryNavKey(val sectionId: String, val categoryHeadingId: String, val focusHeadingId: String? = null) : NavKey

@Serializable
data class SearchNavKey(val initialQuery: String = "") : NavKey

@Serializable
data object SettingsNavKey : NavKey

@Serializable
data object SavedNavKey : NavKey

@Serializable
data object LegendNavKey : NavKey

// Also aliases matching the brief names:
typealias Home = HomeNavKey
typealias Section = SectionNavKey
typealias Search = SearchNavKey
typealias Settings = SettingsNavKey
typealias Saved = SavedNavKey
typealias Legend = LegendNavKey
