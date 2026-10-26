package io.github.omeg4dev.fmhy.data

import io.github.omeg4dev.fmhy.browser.download.ScanService
import io.github.omeg4dev.fmhy.data.adblock.FilterListCatalog

// Preference groups added in 0.5. The older prefs stay in [Appearance]. Defaults here are the
// defaults for new installs; DataStore keys live in DefaultSettingsRepository.

enum class OpenLinksWith { IN_APP, CUSTOM_TAB, EXTERNAL }
enum class ToolbarPosition { TOP, BOTTOM }

/** Where FMHY shows the switch to the browser: a draggable bubble, or the chip in the Home/Saved top bars. */
enum class BrowserButtonStyle { FLOATING, TOP_BAR }

enum class BubbleSide { LEFT, RIGHT }

/** Layout of the browser's tab overview. */
enum class TabsView { LIST, GRID }

enum class AutoplayMode(val label: String) {
    BLOCK("Never"),
    ALLOW_MUTED("Muted only"),
    ALLOW("Always"),
}

enum class FullscreenOrientation(val label: String) {
    /** Landscape for wide videos, portrait for tall ones. */
    AUTO("Auto"),
    LANDSCAPE("Landscape"),
    FOLLOW_PHONE("Like phone"),
}

/**
 * Picks the fullscreen orientation: the new setting when stored, otherwise the pre-0.5.2 boolean
 * "rotate for fullscreen video" (true = AUTO, false = FOLLOW_PHONE), otherwise the default.
 */
fun resolveFullscreenOrientation(stored: String?, legacyRotate: Boolean?): FullscreenOrientation {
    val parsed = stored?.let { name -> FullscreenOrientation.entries.firstOrNull { it.name == name } }
    if (parsed != null) return parsed
    return when (legacyRotate) {
        true -> FullscreenOrientation.AUTO
        false -> FullscreenOrientation.FOLLOW_PHONE
        null -> BrowserPrefs().fullscreenOrientation
    }
}
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
    /** Search: whole words only. Off = fuzzy (prefix + typo-tolerant trigram matches). */
    val searchExact: Boolean = false,
    val swipeToBookmark: Boolean = false,
    val swapTapHold: Boolean = false,
    val swipeBetweenScreens: Boolean = true,
)

data class BrowserPrefs(
    val openLinksWith: OpenLinksWith = OpenLinksWith.IN_APP,
    val siteBar: Boolean = true,
    val deadSiteRescue: Boolean = true,
    val maxTabs: Int = 10,
    val restoreTabs: Boolean = false,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.TOP,
    val browserButton: BrowserButtonStyle = BrowserButtonStyle.FLOATING,
    /** Set from the bubble's hold menu; Settings -> Browsing -> Browser button shows it again. */
    val browserBubbleHidden: Boolean = false,
    val bubbleSide: BubbleSide = BubbleSide.RIGHT,
    /** 0..1 position inside the allowed vertical range. */
    val bubbleY: Float = 0.75f,
    val tabsView: TabsView = TabsView.LIST,
    // Page
    val javascript: Boolean = true,
    /** Darken pages when the app is in dark theme. */
    val forceDark: Boolean = true,
    val desktopByDefault: Boolean = false,
    val textZoomFollowsApp: Boolean = true,
    /** Zoom pages even when they ask to block it. */
    val forceZoom: Boolean = true,
    // Video
    val fullscreenOrientation: FullscreenOrientation = FullscreenOrientation.AUTO,
    val pictureInPicture: Boolean = true,
    /** Enter picture-in-picture on its own when leaving the app while a video plays. */
    val autoPip: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** Keep audio/video playing (with a media notification) when leaving the app or locking the screen. */
    val backgroundPlayback: Boolean = false,
    val autoplay: AutoplayMode = AutoplayMode.ALLOW_MUTED,
    /** Double-tap the left or right third of a fullscreen video to seek 10 s. */
    val doubleTapSeek: Boolean = true,
    /** Playback speed applied to media on pages without a remembered per-site speed. */
    val defaultSpeed: Float = 1f,
    val videoDownloadButton: Boolean = true,
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
)

data class DataUsagePrefs(
    val downloadWifiOnly: Boolean = false,
    val browseWifiOnly: Boolean = false,
    val appDataWifiOnly: Boolean = false,
)

data class UpdatePrefs(
    val autoCheck: Boolean = true,
    val lastCheckMs: Long = 0,
    val laterUntilMs: Long = 0,
    val skippedVersion: String? = null,
    val installExplained: Boolean = false,
    val knownAvailable: String? = null,
)

data class DownloadPrefs(
    val folderUri: String? = null,
    val folderFallbackNotified: Boolean = false,
)

data class DataPrefs(
    val syncOnLaunch: Boolean = true,
    val keepHistory: Boolean = true,
)

data class CheckPrefs(
    val virusTotalOn: Boolean = false,
    val virusTotalKey: String = "",
    val hybridOn: Boolean = false,
    val hybridKey: String = "",
    val holdUntilChecked: Boolean = false,
    val autoCheckRisky: Boolean = true,
    /** The one-time "set up a key" prompt for automatic checks was shown. */
    val keyPromptShown: Boolean = false,
    /** The one-time explanation of what checks send was shown. */
    val explainShown: Boolean = false,
    val fmhyDownloadStatus: Boolean = true,
) {
    /** Services that are on AND have a non-blank key, VirusTotal first. */
    val readyServices: List<ScanService>
        get() = buildList {
            if (virusTotalOn && virusTotalKey.isNotBlank()) add(ScanService.VIRUSTOTAL)
            if (hybridOn && hybridKey.isNotBlank()) add(ScanService.HYBRID_ANALYSIS)
        }

    /** Services that are on (with or without key). */
    val enabledServices: List<ScanService>
        get() = buildList {
            if (virusTotalOn) add(ScanService.VIRUSTOTAL)
            if (hybridOn) add(ScanService.HYBRID_ANALYSIS)
        }
}

