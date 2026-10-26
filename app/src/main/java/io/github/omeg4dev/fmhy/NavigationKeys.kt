package io.github.omeg4dev.fmhy

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeNavKey : NavKey

@Serializable
data class SectionNavKey(val sectionId: String, val anchor: String? = null, val focusEntryId: String? = null) : NavKey

@Serializable
data class CategoryNavKey(
    val sectionId: String,
    val categoryHeadingId: String,
    val focusHeadingId: String? = null,
    val focusEntryId: String? = null,
) : NavKey

@Serializable
data class SearchNavKey(val initialQuery: String = "") : NavKey

@Serializable
data object SettingsNavKey : NavKey

@Serializable
enum class SettingsPage {
    APPEARANCE,
    HOME,
    LISTS,
    BROWSER,
    VIDEO,
    DOWNLOADS,
    AD_BLOCKING,
    PROTECTION,
    PRIVACY,
    NOTIFICATIONS,
    APP_UPDATES,
    SYNC,
    DATA_USAGE,
    BACKUP,
    REPORT,
    ABOUT,
}

@Serializable
data class SettingsPageNavKey(
    val page: SettingsPage,
    val focusSetting: String? = null,
) : NavKey

@Serializable
data object SavedNavKey : NavKey

@Serializable
/**
 * [filter] "all" / "following" / "saved". With filter "all" and no [type] it opens the hub;
 * [type] is a ChangeKind name (or "ALL") and opens the feed on that kind.
 */
data class WhatsNewNavKey(val filter: String = "all", val type: String? = null, val period: String? = null) : NavKey

@Serializable
data object LegendNavKey : NavKey

@Serializable
data class CheckLinkNavKey(val initial: String = "") : NavKey

@Serializable
data object SiteSettingsNavKey : NavKey

@Serializable
data class ReaderNavKey(val path: String, val title: String) : NavKey

@Serializable
data object PostsNavKey : NavKey

@Serializable
data object FollowingNavKey : NavKey

// Also aliases matching the brief names:
typealias Home = HomeNavKey
typealias Section = SectionNavKey
typealias Search = SearchNavKey
typealias Settings = SettingsNavKey
typealias Saved = SavedNavKey
typealias Legend = LegendNavKey
typealias WhatsNew = WhatsNewNavKey
typealias CheckLink = CheckLinkNavKey
typealias Reader = ReaderNavKey
typealias Posts = PostsNavKey
typealias Following = FollowingNavKey
