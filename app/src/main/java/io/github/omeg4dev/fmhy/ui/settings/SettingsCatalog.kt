package io.github.omeg4dev.fmhy.ui.settings

import io.github.omeg4dev.fmhy.SettingsPage

data class SettingEntry(
    val id: String,              // "<page>.<snake_name>", e.g. "browser.javascript"; unique, stable
    val page: SettingsPage,
    val title: String,           // exactly the row title shown
    val description: String,     // one plain sentence, see 2.2
    val keywords: List<String> = emptyList(), // common words people type: "popup", "dark", "wifi", "cache" …
    val advanced: Boolean = false,
)

object SettingsCatalog {
    // Appearance
    val appearanceTheme = SettingEntry(
        id = "appearance.theme",
        page = SettingsPage.APPEARANCE,
        title = "Theme",
        description = "Choose light, dark, or follow system theme",
        keywords = listOf("theme", "dark", "light", "mode", "system"),
    )
    val appearancePureBlack = SettingEntry(
        id = "appearance.pure_black",
        page = SettingsPage.APPEARANCE,
        title = "Pure black",
        description = "Pitch black background in dark mode to save OLED battery",
        keywords = listOf("pure", "black", "oled", "amoled", "dark"),
    )
    val appearanceAccentColor = SettingEntry(
        id = "appearance.accent_color",
        page = SettingsPage.APPEARANCE,
        title = "Accent colour",
        description = "Pick an accent color palette or use Material You dynamic colors",
        keywords = listOf("accent", "colour", "color", "palette", "material you"),
    )
    val appearanceBottomBarStyle = SettingEntry(
        id = "appearance.bottom_bar_style",
        page = SettingsPage.APPEARANCE,
        title = "Bottom bar style",
        description = "Choose the style and layout of the bottom navigation bar",
        keywords = listOf("bottom", "bar", "navigation", "nav", "style"),
    )
    val appearanceTextSize = SettingEntry(
        id = "appearance.text_size",
        page = SettingsPage.APPEARANCE,
        title = "Text size",
        description = "Scale text size up or down across the app",
        keywords = listOf("text", "font", "size", "scale", "zoom"),
    )
    val appearanceAppIcon = SettingEntry(
        id = "appearance.app_icon",
        page = SettingsPage.APPEARANCE,
        title = "App icon",
        description = "Choose an alternative app launcher icon",
        keywords = listOf("app", "icon", "launcher", "logo"),
    )

    // Home
    val homePersonalHome = SettingEntry(
        id = "home.personal_home",
        page = SettingsPage.HOME,
        title = "Personal Home",
        description = "Pin categories and hide sections you do not use",
        keywords = listOf("personal", "home", "pin", "hide", "sections"),
    )
    val homeRandomPick = SettingEntry(
        id = "home.random_pick",
        page = SettingsPage.HOME,
        title = "Show random pick",
        description = "Show a random recommended site on the Browse tab",
        keywords = listOf("random", "pick", "recommendation", "browse", "suggest"),
    )
    val homeSectionOrder = SettingEntry(
        id = "home.section_order",
        page = SettingsPage.HOME,
        title = "Section order",
        description = "Sort sections alphabetically or in website order",
        keywords = listOf("section", "order", "sort", "alphabetical", "layout"),
    )
    val homePinnedSections = SettingEntry(
        id = "home.pinned_sections",
        page = SettingsPage.HOME,
        title = "Pinned sections",
        description = "Manage categories and sections pinned to your home tab",
        keywords = listOf("pinned", "pins", "favorite", "sections", "home"),
    )
    val homeHiddenSections = SettingEntry(
        id = "home.hidden_sections",
        page = SettingsPage.HOME,
        title = "Hidden sections",
        description = "Choose sections to hide from the browse list",
        keywords = listOf("hidden", "hide", "sections", "filter"),
    )

    // Lists & gestures
    val listsWebsiteStyle = SettingEntry(
        id = "lists.website_style",
        page = SettingsPage.LISTS,
        title = "Website style",
        description = "Lists look like fmhy.net with dense compact rows",
        keywords = listOf("website", "style", "dense", "compact", "fmhy", "list"),
    )
    val listsSectionLayout = SettingEntry(
        id = "lists.section_layout",
        page = SettingsPage.LISTS,
        title = "Section layout",
        description = "Choose between continuous scrolling and separate category pages",
        keywords = listOf("section", "layout", "continuous", "categories"),
    )
    val listsStarredOnly = SettingEntry(
        id = "lists.starred_only",
        page = SettingsPage.LISTS,
        title = "Starred only",
        description = "Show only starred sites recommended by FMHY",
        keywords = listOf("starred", "favorite", "filter", "recommended", "stars"),
    )
    val listsShowIndexes = SettingEntry(
        id = "lists.show_indexes",
        page = SettingsPage.LISTS,
        title = "Show indexes",
        description = "Include index sites that list other directories and sites",
        keywords = listOf("index", "indexes", "directories", "lists"),
    )
    val listsNewBadges = SettingEntry(
        id = "lists.new_badges",
        page = SettingsPage.LISTS,
        title = "NEW badges",
        description = "Show NEW badges on recently added sites for a set number of days",
        keywords = listOf("new", "badges", "recent", "added", "days"),
    )
    val listsSwapTapAndHold = SettingEntry(
        id = "lists.swap_tap_and_hold",
        page = SettingsPage.LISTS,
        title = "Swap tap and hold",
        description = "Tap opens the actions menu and hold opens the site link",
        keywords = listOf("tap", "hold", "swap", "gesture", "menu"),
    )
    val listsSwipeToBookmark = SettingEntry(
        id = "lists.swipe_to_bookmark",
        page = SettingsPage.LISTS,
        title = "Swipe to bookmark",
        description = "Swipe an entry sideways to save or remove it",
        keywords = listOf("swipe", "bookmark", "save", "gesture"),
    )
    val listsSwipeBetweenScreens = SettingEntry(
        id = "lists.swipe_between_screens",
        page = SettingsPage.LISTS,
        title = "Swipe between screens",
        description = "Swipe left or right to move between Browse, Search, and Saved",
        keywords = listOf("swipe", "screen", "tabs", "navigation", "gesture"),
    )
    val listsHapticFeedback = SettingEntry(
        id = "lists.haptic_feedback",
        page = SettingsPage.LISTS,
        title = "Haptic feedback",
        description = "Vibrate lightly on taps, toggles, and actions",
        keywords = listOf("haptic", "vibrate", "feedback", "touch"),
    )
    val listsIconLegend = SettingEntry(
        id = "lists.icon_legend",
        page = SettingsPage.LISTS,
        title = "Icon legend",
        description = "Show what every icon and indicator means",
        keywords = listOf("icon", "legend", "help", "guide", "symbols"),
    )

    // Browser
    val browserOpenLinksWith = SettingEntry(
        id = "browser.open_links_with",
        page = SettingsPage.BROWSER,
        title = "Open links with",
        description = "Choose whether links open in the built-in browser, custom tabs, or an external browser",
        keywords = listOf("open", "links", "in-app", "custom tab", "external", "browser"),
    )
    val browserSearchEngine = SettingEntry(
        id = "browser.search_engine",
        page = SettingsPage.BROWSER,
        title = "Search engine",
        description = "Default search provider for web queries entered in the address bar",
        keywords = listOf("search", "engine", "google", "duckduckgo", "bing", "brave"),
    )
    val browserAddressBar = SettingEntry(
        id = "browser.address_bar",
        page = SettingsPage.BROWSER,
        title = "Address bar",
        description = "Position the browser address bar at the top or bottom of the screen",
        keywords = listOf("address", "bar", "toolbar", "position", "top", "bottom"),
    )
    val browserSiteBar = SettingEntry(
        id = "browser.site_bar",
        page = SettingsPage.BROWSER,
        title = "FMHY status icon",
        description = "Show an icon in the address bar indicating if the current site is on FMHY",
        keywords = listOf("fmhy", "status", "icon", "address bar", "badge"),
    )
    val browserDeadSiteRescue = SettingEntry(
        id = "browser.dead_site_rescue",
        page = SettingsPage.BROWSER,
        title = "Dead-site rescue",
        description = "Offer mirrors and alternative links when a webpage fails to load",
        keywords = listOf("dead", "site", "rescue", "mirror", "wayback", "archive"),
    )
    val browserMaxTabs = SettingEntry(
        id = "browser.max_tabs",
        page = SettingsPage.BROWSER,
        title = "Maximum tabs",
        description = "Limit how many open tabs the browser keeps in memory at once",
        keywords = listOf("max", "tabs", "limit", "memory", "count"),
    )
    val browserRestoreTabs = SettingEntry(
        id = "browser.restore_tabs",
        page = SettingsPage.BROWSER,
        title = "Restore tabs on start",
        description = "Reopen your open tabs when relaunching the browser",
        keywords = listOf("restore", "tabs", "reopen", "session", "start"),
    )
    val browserForceDark = SettingEntry(
        id = "browser.force_dark",
        page = SettingsPage.BROWSER,
        title = "Dark pages in dark theme",
        description = "Automatically darken web pages when dark theme is enabled",
        keywords = listOf("dark", "night", "black", "web", "theme"),
    )
    val browserTextZoomFollowsApp = SettingEntry(
        id = "browser.text_zoom_follows_app",
        page = SettingsPage.BROWSER,
        title = "Text size follows app",
        description = "Apply your app text size scale to web pages in the browser",
        keywords = listOf("text", "zoom", "font", "size", "scale"),
    )
    val browserForceZoom = SettingEntry(
        id = "browser.force_zoom",
        page = SettingsPage.BROWSER,
        title = "Allow zoom on every site",
        description = "Override sites that try to block pinch-to-zoom",
        keywords = listOf("zoom", "pinch", "scale", "override", "magnify"),
    )
    val browserSiteSettings = SettingEntry(
        id = "browser.site_settings",
        page = SettingsPage.BROWSER,
        title = "Site settings",
        description = "Manage permissions, exceptions, and overrides for individual sites",
        keywords = listOf("site", "settings", "permissions", "exceptions", "overrides"),
    )
    val browserJavascript = SettingEntry(
        id = "browser.javascript",
        page = SettingsPage.BROWSER,
        title = "JavaScript",
        description = "Allow websites to run JavaScript scripts and dynamic code",
        keywords = listOf("javascript", "js", "script", "code"),
        advanced = true,
    )
    val browserDesktopDefault = SettingEntry(
        id = "browser.desktop_default",
        page = SettingsPage.BROWSER,
        title = "Desktop site by default",
        description = "Request desktop versions of web pages instead of mobile versions",
        keywords = listOf("desktop", "useragent", "request", "mobile", "pc"),
        advanced = true,
    )

    // Video
    val videoFullscreenOrientation = SettingEntry(
        id = "video.fullscreen_orientation",
        page = SettingsPage.VIDEO,
        title = "Fullscreen orientation",
        description = "Control screen rotation when entering fullscreen video playback",
        keywords = listOf("fullscreen", "orientation", "landscape", "rotation", "rotate"),
    )
    val videoDoubleTapSeek = SettingEntry(
        id = "video.double_tap_seek",
        page = SettingsPage.VIDEO,
        title = "Double-tap to seek",
        description = "Double-tap left or right side of fullscreen video to skip 10 seconds",
        keywords = listOf("seek", "skip", "double", "tap", "gesture", "video"),
    )
    val videoKeepScreenOn = SettingEntry(
        id = "video.keep_screen_on",
        page = SettingsPage.VIDEO,
        title = "Keep screen on while playing",
        description = "Prevent the screen from turning off while a video is playing",
        keywords = listOf("screen", "awake", "timeout", "display", "sleep"),
    )
    val videoPictureInPicture = SettingEntry(
        id = "video.picture_in_picture",
        page = SettingsPage.VIDEO,
        title = "Picture-in-picture",
        description = "Allow videos to play in a floating overlay window",
        keywords = listOf("pip", "picture", "window", "floating", "overlay"),
    )
    val videoAutoPip = SettingEntry(
        id = "video.auto_pip",
        page = SettingsPage.VIDEO,
        title = "Start automatically",
        description = "Automatically shrink video to picture-in-picture when leaving the app",
        keywords = listOf("pip", "auto", "shrink", "leave", "background"),
    )
    val videoAutoplay = SettingEntry(
        id = "video.autoplay",
        page = SettingsPage.VIDEO,
        title = "Autoplay",
        description = "Choose whether web videos can start playing without a tap",
        keywords = listOf("autoplay", "play", "automatic", "media"),
    )
    val videoBackgroundPlayback = SettingEntry(
        id = "video.background_playback",
        page = SettingsPage.VIDEO,
        title = "Play in background",
        description = "Keep audio playing when you leave the app or lock the screen",
        keywords = listOf("background", "audio", "lock", "screen", "play"),
    )
    val videoDefaultSpeed = SettingEntry(
        id = "video.default_speed",
        page = SettingsPage.VIDEO,
        title = "Default speed",
        description = "Default playback speed for newly opened videos",
        keywords = listOf("speed", "rate", "fast", "slow", "playback"),
    )
    val videoForgetSiteSpeeds = SettingEntry(
        id = "video.forget_site_speeds",
        page = SettingsPage.VIDEO,
        title = "Forget per-site speeds",
        description = "Clear custom playback speeds remembered for specific websites",
        keywords = listOf("speed", "reset", "site", "playback", "clear"),
        advanced = true,
    )

    // Downloads
    val downloadsDownloadFolder = SettingEntry(
        id = "downloads.download_folder",
        page = SettingsPage.DOWNLOADS,
        title = "Download folder",
        description = "Choose where newly downloaded files are saved on your device",
        keywords = listOf("download", "folder", "directory", "storage", "path"),
    )
    val downloadsVideoDownloadButton = SettingEntry(
        id = "downloads.video_download_button",
        page = SettingsPage.DOWNLOADS,
        title = "Video download button",
        description = "Show a download button in the address bar when a downloadable video is detected",
        keywords = listOf("download", "button", "save", "video", "media"),
    )
    val downloadsVirusTotal = SettingEntry(
        id = "downloads.virustotal",
        page = SettingsPage.DOWNLOADS,
        title = "VirusTotal",
        description = "Scan downloaded files and links against multiple antivirus engines using VirusTotal",
        keywords = listOf("virustotal", "scan", "antivirus", "security", "malware"),
    )
    val downloadsVirusTotalKey = SettingEntry(
        id = "downloads.virustotal_key",
        page = SettingsPage.DOWNLOADS,
        title = "VirusTotal key",
        description = "Add your personal free VirusTotal API key to check files automatically",
        keywords = listOf("virustotal", "key", "api", "token", "credentials"),
    )
    val downloadsHybridAnalysis = SettingEntry(
        id = "downloads.hybrid_analysis",
        page = SettingsPage.DOWNLOADS,
        title = "Hybrid Analysis",
        description = "Scan downloaded files with the Falcon Sandbox on Hybrid Analysis",
        keywords = listOf("hybrid", "analysis", "sandbox", "security", "malware"),
    )
    val downloadsHybridAnalysisKey = SettingEntry(
        id = "downloads.hybrid_analysis_key",
        page = SettingsPage.DOWNLOADS,
        title = "Hybrid Analysis key",
        description = "Add your personal free Hybrid Analysis API key to check files automatically",
        keywords = listOf("hybrid", "analysis", "key", "api", "token"),
    )
    val downloadsHoldUntilChecked = SettingEntry(
        id = "downloads.hold_until_checked",
        page = SettingsPage.DOWNLOADS,
        title = "Hold new files until checked",
        description = "Keep new downloads hidden from other apps until you review and approve them",
        keywords = listOf("hold", "quarantine", "isolate", "pending", "downloads"),
    )
    val downloadsCheckRiskyFiles = SettingEntry(
        id = "downloads.check_risky_files",
        page = SettingsPage.DOWNLOADS,
        title = "Check risky files automatically",
        description = "Automatically send fingerprints of APKs, executables, and scripts to enabled security services",
        keywords = listOf("check", "risky", "apk", "exe", "automatic", "scan"),
    )
    val downloadsShowFmhyStatus = SettingEntry(
        id = "downloads.show_fmhy_status",
        page = SettingsPage.DOWNLOADS,
        title = "Show FMHY status in downloads",
        description = "Show where the downloaded file source site is listed in FMHY directories",
        keywords = listOf("fmhy", "status", "source", "listing", "downloads"),
    )

    // Ad blocking
    val adBlockingBlockAdsAndTrackers = SettingEntry(
        id = "ad_blocking.block_ads_and_trackers",
        page = SettingsPage.AD_BLOCKING,
        title = "Block ads and trackers",
        description = "Block advertisements, behavioral trackers, and malicious domains across web pages",
        keywords = listOf("ad", "ads", "adblock", "tracker", "privacy", "blocking"),
    )
    val adBlockingBlockingLevel = SettingEntry(
        id = "ad_blocking.blocking_level",
        page = SettingsPage.AD_BLOCKING,
        title = "Blocking level",
        description = "Choose between Balanced, Strict, or Custom rule combinations",
        keywords = listOf("strict", "balanced", "custom", "level", "mode"),
    )
    val adBlockingShowBlockedCount = SettingEntry(
        id = "ad_blocking.show_blocked_count",
        page = SettingsPage.AD_BLOCKING,
        title = "Show blocked count",
        description = "Display the number of blocked requests in the browser menu",
        keywords = listOf("count", "blocked", "badge", "statistics", "counter"),
    )
    val adBlockingHidePageElements = SettingEntry(
        id = "ad_blocking.hide_page_elements",
        page = SettingsPage.AD_BLOCKING,
        title = "Hide page elements",
        description = "Hide empty ad spaces and cosmetic placeholders on web pages",
        keywords = listOf("hide", "cosmetic", "elements", "collapse", "clean"),
    )
    val adBlockingFixBrokenPages = SettingEntry(
        id = "ad_blocking.fix_broken_pages",
        page = SettingsPage.AD_BLOCKING,
        title = "Fix broken pages",
        description = "Inject uBlock Origin compatibility scriptlets to fix pages that break with ad blocking",
        keywords = listOf("fix", "scriptlets", "broken", "compatibility", "ublock"),
    )
    val adBlockingUpdateListsAutomatically = SettingEntry(
        id = "ad_blocking.update_lists_automatically",
        page = SettingsPage.AD_BLOCKING,
        title = "Update lists automatically",
        description = "Periodically check for and download updated filter lists in the background",
        keywords = listOf("auto", "update", "lists", "periodic", "refresh"),
    )
    val adBlockingListStatus = SettingEntry(
        id = "ad_blocking.list_status",
        page = SettingsPage.AD_BLOCKING,
        title = "List status",
        description = "View active rule count, last update time, and manually refresh filter lists",
        keywords = listOf("status", "rules", "active", "updated", "refresh", "now"),
    )
    val adBlockingFilterLists = SettingEntry(
        id = "ad_blocking.filter_lists",
        page = SettingsPage.AD_BLOCKING,
        title = "Filter lists",
        description = "Toggle individual ad, privacy, annoyance, and malware filter lists",
        keywords = listOf("filter", "lists", "easylist", "easyprivacy", "ublock", "subscriptions"),
        advanced = true,
    )
    val adBlockingCustomRules = SettingEntry(
        id = "ad_blocking.custom_rules",
        page = SettingsPage.AD_BLOCKING,
        title = "Custom rules",
        description = "Add your own custom filtering rules using uBlock Origin syntax",
        keywords = listOf("custom", "rules", "ubo", "user", "syntax", "filters"),
        advanced = true,
    )
    val adBlockingAllowedSites = SettingEntry(
        id = "ad_blocking.allowed_sites",
        page = SettingsPage.AD_BLOCKING,
        title = "Allowed sites",
        description = "Disable ad and tracker blocking on specific websites you trust",
        keywords = listOf("allow", "whitelist", "exceptions", "trusted", "domains"),
        advanced = true,
    )

    // Protection
    val protectionWarnUnsafeSites = SettingEntry(
        id = "protection.warn_unsafe_sites",
        page = SettingsPage.PROTECTION,
        title = "Warn before unsafe sites",
        description = "Display a warning before navigating to known malicious or phishing sites",
        keywords = listOf("unsafe", "malware", "phishing", "warning", "security"),
    )
    val protectionWarnPotentiallyUnsafeSites = SettingEntry(
        id = "protection.warn_potentially_unsafe_sites",
        page = SettingsPage.PROTECTION,
        title = "Warn before potentially unsafe sites",
        description = "Warn when opening sites reported for questionable behavior or unverified safety",
        keywords = listOf("potential", "suspicious", "warning", "risky", "safety"),
    )
    val protectionGoogleSafeBrowsing = SettingEntry(
        id = "protection.google_safe_browsing",
        page = SettingsPage.PROTECTION,
        title = "Google Safe Browsing",
        description = "Check web addresses against Google Safe Browsing threat databases",
        keywords = listOf("google", "safe", "browsing", "phishing", "malware"),
    )
    val protectionBlockPopups = SettingEntry(
        id = "protection.block_popups",
        page = SettingsPage.PROTECTION,
        title = "Block pop-ups",
        description = "Prevent websites from opening automatic pop-up windows and tabs",
        keywords = listOf("popup", "popups", "window", "block", "tabs"),
    )
    val protectionBlockRedirects = SettingEntry(
        id = "protection.block_redirects",
        page = SettingsPage.PROTECTION,
        title = "Block redirects without a tap",
        description = "Prevent pages from redirecting you to other sites unless you tapped a link",
        keywords = listOf("redirect", "redirects", "tap", "forward", "navigate"),
    )
    val protectionBlockAppLinks = SettingEntry(
        id = "protection.block_app_links",
        page = SettingsPage.PROTECTION,
        title = "Block links that open apps",
        description = "Ask for confirmation before allowing a webpage to open external installed apps",
        keywords = listOf("apps", "intent", "external", "open", "links"),
    )
    val protectionAskBeforeDownloads = SettingEntry(
        id = "protection.ask_before_downloads",
        page = SettingsPage.PROTECTION,
        title = "Ask before downloads",
        description = "Prompt for confirmation before starting any file download",
        keywords = listOf("download", "prompt", "confirm", "ask", "save"),
    )
    val protectionWarnRiskyDownloads = SettingEntry(
        id = "protection.warn_risky_downloads",
        page = SettingsPage.PROTECTION,
        title = "Warn about APK and EXE downloads",
        description = "Show an extra warning when downloading executable files, installers, and Android packages",
        keywords = listOf("apk", "exe", "executable", "warning", "installers"),
    )

    // Privacy & history
    val privacyBlockThirdPartyCookies = SettingEntry(
        id = "privacy.block_third_party_cookies",
        page = SettingsPage.PRIVACY,
        title = "Block third-party cookies",
        description = "Prevent external domains from setting tracking cookies in your browser session",
        keywords = listOf("cookie", "cookies", "third-party", "tracking", "privacy"),
    )
    val privacyHideCookieBanners = SettingEntry(
        id = "privacy.hide_cookie_banners",
        page = SettingsPage.PRIVACY,
        title = "Hide cookie banners",
        description = "Automatically suppress and reject annoying cookie consent banners on websites",
        keywords = listOf("cookie", "cookies", "banners", "consent", "gdpr", "notices"),
    )
    val privacySendGpc = SettingEntry(
        id = "privacy.send_gpc",
        page = SettingsPage.PRIVACY,
        title = "Ask sites not to track me",
        description = "Send Global Privacy Control signal requesting sites do not sell or share data",
        keywords = listOf("track", "gpc", "dnt", "privacy", "signal"),
    )
    val privacyPrivateTabsByDefault = SettingEntry(
        id = "privacy.private_tabs_by_default",
        page = SettingsPage.PRIVACY,
        title = "Open new tabs as private",
        description = "Always open newly created browser tabs in incognito private mode",
        keywords = listOf("private", "incognito", "tabs", "history", "isolated"),
    )
    val privacyClearDataOnExit = SettingEntry(
        id = "privacy.clear_data_on_exit",
        page = SettingsPage.PRIVACY,
        title = "Clear site data on exit",
        description = "Automatically remove cookies and site storage whenever you close the app",
        keywords = listOf("clear", "exit", "close", "cleanup", "storage", "cookies"),
    )
    val privacyKeepHistory = SettingEntry(
        id = "privacy.keep_history",
        page = SettingsPage.PRIVACY,
        title = "Keep history of opened sites",
        description = "Record links you open in your browsing history for easy reference",
        keywords = listOf("history", "record", "visited", "log", "sites"),
    )
    val privacyClearHistory = SettingEntry(
        id = "privacy.clear_history",
        page = SettingsPage.PRIVACY,
        title = "Clear history",
        description = "Permanently delete all recorded history of opened websites",
        keywords = listOf("delete", "clear", "remove", "history", "privacy"),
    )
    val privacyClearBrowsingData = SettingEntry(
        id = "privacy.clear_browsing_data",
        page = SettingsPage.PRIVACY,
        title = "Clear browsing data",
        description = "Remove cookies, cached web files, and browsing history",
        keywords = listOf("clear", "cache", "cookies", "data", "delete", "storage"),
    )

    // Notifications
    val notificationsEnabled = SettingEntry(
        id = "notifications.enabled",
        page = SettingsPage.NOTIFICATIONS,
        title = "Notifications",
        description = "Allow notifications for updates to followed sections and saved items",
        keywords = listOf("notifications", "notification", "alerts", "push", "enable", "messages"),
    )
    val notificationsNotifyFollowed = SettingEntry(
        id = "notifications.notify_followed",
        page = SettingsPage.NOTIFICATIONS,
        title = "New sites in followed sections",
        description = "Receive an alert when new sites are added to sections you follow",
        keywords = listOf("followed", "sections", "new", "sites", "alerts"),
    )
    val notificationsWatchSaved = SettingEntry(
        id = "notifications.watch_saved",
        page = SettingsPage.NOTIFICATIONS,
        title = "Changes to saved sites",
        description = "Receive an alert when bookmarked sites are updated, moved, or removed",
        keywords = listOf("saved", "bookmarks", "changes", "updates", "alerts"),
    )
    val notificationsNotifyNewPosts = SettingEntry(
        id = "notifications.notify_new_posts",
        page = SettingsPage.NOTIFICATIONS,
        title = "New monthly posts",
        description = "Receive an alert when FMHY publishes its monthly update post",
        keywords = listOf("monthly", "posts", "news", "announcements", "fmhy"),
    )
    val notificationsCheckEvery = SettingEntry(
        id = "notifications.check_every",
        page = SettingsPage.NOTIFICATIONS,
        title = "Check every",
        description = "Set how frequently the background worker checks for site and section updates",
        keywords = listOf("interval", "frequency", "schedule", "period", "background"),
        advanced = true,
    )
    val notificationsDetailedChangeHistory = SettingEntry(
        id = "notifications.detailed_change_history",
        page = SettingsPage.NOTIFICATIONS,
        title = "Detailed change history",
        description = "Fetch exact dates and diffs from fmhy-tracker instead of basic section checks",
        keywords = listOf("tracker", "history", "diff", "detailed", "changes"),
        advanced = true,
    )
    val notificationsCheckNow = SettingEntry(
        id = "notifications.check_now",
        page = SettingsPage.NOTIFICATIONS,
        title = "Check for updates now",
        description = "Check for new sites, edits, and removals right now",
        keywords = listOf("check", "now", "refresh", "manual", "fetch"),
        advanced = true,
    )

    // Sync
    val syncSyncNow = SettingEntry(
        id = "sync.sync_now",
        page = SettingsPage.SYNC,
        title = "Sync now",
        description = "Fetch the latest FMHY directory data immediately",
        keywords = listOf("sync", "now", "fetch", "refresh", "download"),
    )
    val syncSyncOnLaunch = SettingEntry(
        id = "sync.sync_on_launch",
        page = SettingsPage.SYNC,
        title = "Sync on launch",
        description = "Automatically check for new directory data every time the app opens",
        keywords = listOf("sync", "launch", "start", "open", "automatic"),
    )
    val syncLastSynced = SettingEntry(
        id = "sync.last_synced",
        page = SettingsPage.SYNC,
        title = "Last synced",
        description = "Timestamp of the most recent successful directory synchronization",
        keywords = listOf("last", "synced", "updated", "time", "status"),
    )

    // Data usage
    val dataUsageDownloadWifiOnly = SettingEntry(
        id = "data_usage.download_wifi_only",
        page = SettingsPage.DATA_USAGE,
        title = "Download only on Wi-Fi",
        description = "Only download files and media when connected to an unmetered Wi-Fi network",
        keywords = listOf("download", "wifi", "cellular", "mobile data", "metered"),
    )
    val dataUsageBrowseWifiOnly = SettingEntry(
        id = "data_usage.browse_wifi_only",
        page = SettingsPage.DATA_USAGE,
        title = "Browse only on Wi-Fi",
        description = "Restrict in-app web browsing to Wi-Fi connections to save mobile data",
        keywords = listOf("browse", "wifi", "cellular", "mobile data", "network"),
    )
    val dataUsageAppDataWifiOnly = SettingEntry(
        id = "data_usage.app_data_wifi_only",
        page = SettingsPage.DATA_USAGE,
        title = "App data and updates only on Wi-Fi",
        description = "Restrict database sync, adblock lists, and app updates to Wi-Fi connections",
        keywords = listOf("sync", "updates", "wifi", "cellular", "mobile data"),
    )

    // Backup & restore
    val backupExportBackup = SettingEntry(
        id = "backup.export_backup",
        page = SettingsPage.BACKUP,
        title = "Export backup",
        description = "Save your bookmarks, folders, and followed sections to a JSON file",
        keywords = listOf("export", "backup", "save", "json", "exporting"),
    )
    val backupImportBackup = SettingEntry(
        id = "backup.import_backup",
        page = SettingsPage.BACKUP,
        title = "Import backup",
        description = "Restore bookmarks, folders, and preferences from a previously saved JSON backup",
        keywords = listOf("import", "backup", "restore", "json", "recover"),
    )

    // Report a problem
    val reportReportProblem = SettingEntry(
        id = "report.report_problem",
        page = SettingsPage.REPORT,
        title = "Report a problem",
        description = "Submit feedback, report a broken site, or report an app issue",
        keywords = listOf("report", "problem", "feedback", "bug", "issue", "support"),
    )
    val reportOfferToReportCrashes = SettingEntry(
        id = "report.offer_to_report_crashes",
        page = SettingsPage.REPORT,
        title = "Offer to report crashes",
        description = "Show a prompt after an unexpected app crash allowing you to send an error report",
        keywords = listOf("crash", "report", "crashes", "errors", "bug"),
    )

    // About
    val aboutAboutFmhy = SettingEntry(
        id = "about.about_fmhy",
        page = SettingsPage.ABOUT,
        title = "About FMHY",
        description = "Unofficial open-source client for FreeMediaHeckYeah",
        keywords = listOf("about", "client", "license", "gpl", "info"),
    )
    val aboutVersion = SettingEntry(
        id = "about.version",
        page = SettingsPage.ABOUT,
        title = "Version",
        description = "Current installed version of the app",
        keywords = listOf("version", "build", "release", "app"),
    )
    val aboutRunSetupAgain = SettingEntry(
        id = "about.run_setup_again",
        page = SettingsPage.ABOUT,
        title = "Run setup again",
        description = "Restart the first-run onboarding setup walkthrough",
        keywords = listOf("setup", "onboarding", "walkthrough", "restart", "first-run"),
    )
    val aboutOpenFmhyLinks = SettingEntry(
        id = "about.open_fmhy_links",
        page = SettingsPage.ABOUT,
        title = "Open fmhy.net links in the app",
        description = "Configure Android system to open fmhy.net links directly in this app",
        keywords = listOf("links", "intent", "default", "open", "url"),
    )
    val aboutGithubRepo = SettingEntry(
        id = "about.github_repo",
        page = SettingsPage.ABOUT,
        title = "GitHub repo",
        description = "Source code, releases and issues",
        keywords = listOf("github", "repo", "source", "code", "issues", "releases"),
    )

    // App updates
    val appUpdatesAutoCheck = SettingEntry(
        id = "app_updates.auto_check",
        page = SettingsPage.APP_UPDATES,
        title = "Automatic checks",
        description = "Check the project release page for new versions automatically",
        keywords = listOf("auto", "check", "update", "periodic", "background"),
    )
    val appUpdatesCheckNow = SettingEntry(
        id = "app_updates.check_now",
        page = SettingsPage.APP_UPDATES,
        title = "Check for updates",
        description = "Check GitHub releases right now for newer available versions",
        keywords = listOf("check", "update", "upgrade", "version", "now"),
    )
    val appUpdatesSkippedVersion = SettingEntry(
        id = "app_updates.skipped_version",
        page = SettingsPage.APP_UPDATES,
        title = "Skipped version",
        description = "Update version that was skipped and will not prompt to install",
        keywords = listOf("skipped", "ignore", "version", "update"),
    )

    val all: List<SettingEntry> = listOf(
        // Appearance
        appearanceTheme,
        appearancePureBlack,
        appearanceAccentColor,
        appearanceBottomBarStyle,
        appearanceTextSize,
        appearanceAppIcon,
        // Home
        homePersonalHome,
        homeRandomPick,
        homeSectionOrder,
        homePinnedSections,
        homeHiddenSections,
        // Lists & gestures
        listsWebsiteStyle,
        listsSectionLayout,
        listsStarredOnly,
        listsShowIndexes,
        listsNewBadges,
        listsSwapTapAndHold,
        listsSwipeToBookmark,
        listsSwipeBetweenScreens,
        listsHapticFeedback,
        listsIconLegend,
        // Browser
        browserOpenLinksWith,
        browserSearchEngine,
        browserAddressBar,
        browserSiteBar,
        browserDeadSiteRescue,
        browserMaxTabs,
        browserRestoreTabs,
        browserForceDark,
        browserTextZoomFollowsApp,
        browserForceZoom,
        browserSiteSettings,
        browserJavascript,
        browserDesktopDefault,
        // Video
        videoFullscreenOrientation,
        videoDoubleTapSeek,
        videoKeepScreenOn,
        videoPictureInPicture,
        videoAutoPip,
        videoAutoplay,
        videoBackgroundPlayback,
        videoDefaultSpeed,
        videoForgetSiteSpeeds,
        // Downloads
        downloadsDownloadFolder,
        downloadsVideoDownloadButton,
        downloadsVirusTotal,
        downloadsVirusTotalKey,
        downloadsHybridAnalysis,
        downloadsHybridAnalysisKey,
        downloadsHoldUntilChecked,
        downloadsCheckRiskyFiles,
        downloadsShowFmhyStatus,
        // Ad blocking
        adBlockingBlockAdsAndTrackers,
        adBlockingBlockingLevel,
        adBlockingShowBlockedCount,
        adBlockingHidePageElements,
        adBlockingFixBrokenPages,
        adBlockingUpdateListsAutomatically,
        adBlockingListStatus,
        adBlockingFilterLists,
        adBlockingCustomRules,
        adBlockingAllowedSites,
        // Protection
        protectionWarnUnsafeSites,
        protectionWarnPotentiallyUnsafeSites,
        protectionGoogleSafeBrowsing,
        protectionBlockPopups,
        protectionBlockRedirects,
        protectionBlockAppLinks,
        protectionAskBeforeDownloads,
        protectionWarnRiskyDownloads,
        // Privacy & history
        privacyBlockThirdPartyCookies,
        privacyHideCookieBanners,
        privacySendGpc,
        privacyPrivateTabsByDefault,
        privacyClearDataOnExit,
        privacyKeepHistory,
        privacyClearHistory,
        privacyClearBrowsingData,
        // Notifications
        notificationsEnabled,
        notificationsNotifyFollowed,
        notificationsWatchSaved,
        notificationsNotifyNewPosts,
        notificationsCheckEvery,
        notificationsDetailedChangeHistory,
        notificationsCheckNow,
        // Sync
        syncSyncNow,
        syncSyncOnLaunch,
        syncLastSynced,
        // Data usage
        dataUsageDownloadWifiOnly,
        dataUsageBrowseWifiOnly,
        dataUsageAppDataWifiOnly,
        // Backup & restore
        backupExportBackup,
        backupImportBackup,
        // Report a problem
        reportReportProblem,
        reportOfferToReportCrashes,
        // About
        aboutAboutFmhy,
        aboutVersion,
        aboutRunSetupAgain,
        aboutOpenFmhyLinks,
        aboutGithubRepo,
        // App updates
        appUpdatesAutoCheck,
        appUpdatesCheckNow,
        appUpdatesSkippedVersion,
    )

    fun byId(id: String): SettingEntry? = all.firstOrNull { it.id == id }
}

fun SettingsPage.heading(): String = when (this) {
    SettingsPage.APPEARANCE, SettingsPage.HOME, SettingsPage.LISTS -> "Look & feel"
    SettingsPage.BROWSER, SettingsPage.VIDEO, SettingsPage.DOWNLOADS -> "Browsing"
    SettingsPage.AD_BLOCKING, SettingsPage.PROTECTION, SettingsPage.PRIVACY -> "Safety & privacy"
    SettingsPage.NOTIFICATIONS, SettingsPage.APP_UPDATES -> "Updates"
    SettingsPage.SYNC, SettingsPage.DATA_USAGE, SettingsPage.BACKUP -> "Your data"
    SettingsPage.REPORT, SettingsPage.ABOUT -> "Help & about"
}

fun SettingsPage.label(): String = when (this) {
    SettingsPage.APPEARANCE -> "Appearance"
    SettingsPage.HOME -> "Home"
    SettingsPage.LISTS -> "Lists & gestures"
    SettingsPage.BROWSER -> "Browser"
    SettingsPage.VIDEO -> "Video"
    SettingsPage.DOWNLOADS -> "Downloads"
    SettingsPage.AD_BLOCKING -> "Ad blocking"
    SettingsPage.PROTECTION -> "Protection"
    SettingsPage.PRIVACY -> "Privacy & history"
    SettingsPage.NOTIFICATIONS -> "Notifications"
    SettingsPage.APP_UPDATES -> "App updates"
    SettingsPage.SYNC -> "Sync"
    SettingsPage.DATA_USAGE -> "Data usage"
    SettingsPage.BACKUP -> "Backup & restore"
    SettingsPage.REPORT -> "Report a problem"
    SettingsPage.ABOUT -> "About"
}
