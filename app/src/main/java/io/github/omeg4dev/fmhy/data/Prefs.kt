package io.github.omeg4dev.fmhy.data

import io.github.omeg4dev.fmhy.data.adblock.FilterListCatalog

// Preference groups added in 0.5. The older prefs stay in [Appearance]. Defaults here are the
// defaults for new installs; DataStore keys live in DefaultSettingsRepository.

enum class OpenLinksWith { IN_APP, CUSTOM_TAB, EXTERNAL }
enum class ToolbarPosition { TOP, BOTTOM }
enum class AppIcon(val label: String) {
    DEFAULT("Default"),
    UWU("UwU"),
    MONOCHROME("Purple"),
    LIGHT("Light"),
    BLUE("Blue"),
    GREEN("Green"),
    RED("Red"),
    BLACK("Black"),
}
enum class UpdateInterval(val hours: Long) { H12(12), DAILY(24), WEEKLY(168) }

enum class SearchEngine(val label: String, val queryUrl: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?q="),
    BRAVE("Brave Search", "https://search.brave.com/search?q="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
}

data class HomePrefs(
    val personalHome: Boolean = false,
    /** 0 = NEW badges off. */
    val newBadgeDays: Int = 7,
)

data class BrowserPrefs(
    val openLinksWith: OpenLinksWith = OpenLinksWith.IN_APP,
    val siteBar: Boolean = true,
    val deadSiteRescue: Boolean = true,
    val maxTabs: Int = 10,
    val restoreTabs: Boolean = false,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.TOP,
    // Page
    val javascript: Boolean = true,
    /** Darken pages when the app is in dark theme. */
    val forceDark: Boolean = true,
    val desktopByDefault: Boolean = false,
    val textZoomFollowsApp: Boolean = true,
    // Video
    val videoAutoRotate: Boolean = true,
    val pictureInPicture: Boolean = true,
    val keepScreenOn: Boolean = true,
)

data class BlockingPrefs(
    val adblock: Boolean = true,
    val enabledLists: Set<String> = FilterListCatalog.balancedIds,
    /** One rule per line, uBO syntax. */
    val customRules: String = "",
    /** Hosts (eTLD+1 or full host) where blocking is off. */
    val allowlist: Set<String> = emptySet(),
    val showBlockedCount: Boolean = true,
    val elementHiding: Boolean = true,
    val scriptlets: Boolean = true,
    val autoUpdateLists: Boolean = true,
)

data class ProtectionPrefs(
    val unsafeWarning: Boolean = true,
    val potentiallyUnsafeWarning: Boolean = true,
    val popupBlocker: Boolean = true,
    val redirectGuard: Boolean = true,
    /** intent:, market: and other app-launch links: blocked when true, otherwise asked. */
    val blockAppLinks: Boolean = true,
    val askBeforeDownload: Boolean = true,
    val warnRiskyDownloads: Boolean = true,
    val safeBrowsing: Boolean = true,
)

data class PrivacyPrefs(
    val blockThirdPartyCookies: Boolean = true,
    /** Mirrors whether [FilterListCatalog.COOKIE_LIST_ID] is in [BlockingPrefs.enabledLists]. */
    val hideCookieBanners: Boolean = false,
    val clearDataOnExit: Boolean = false,
    val privateTabsByDefault: Boolean = false,
    val sendGpc: Boolean = true,
)

data class NotificationPrefs(
    val useTracker: Boolean = true,
    /** Master switch; needs POST_NOTIFICATIONS on Android 13+. */
    val enabled: Boolean = false,
    val notifyFollowed: Boolean = true,
    val watchSaved: Boolean = true,
    val notifyNewPosts: Boolean = true,
    val interval: UpdateInterval = UpdateInterval.DAILY,
    val wifiOnly: Boolean = false,
)

data class DataPrefs(
    val syncOnLaunch: Boolean = true,
    val keepHistory: Boolean = true,
)
