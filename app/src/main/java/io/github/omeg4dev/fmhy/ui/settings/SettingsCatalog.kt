package io.github.omeg4dev.fmhy.ui.settings

import io.github.omeg4dev.fmhy.BuildConfig
import io.github.omeg4dev.fmhy.SettingsPage

data class SettingEntry(
    val id: String,              // "<prefix>.<snake_name>"; prefix = page where the setting was first added. Never renamed, even when the row moves (deep links and focus use it).
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
        description = "Pick an accent palette or Material You colours",
        keywords = listOf("accent", "colour", "color", "palette", "material you"),
    )
    val appearanceTextSize = SettingEntry(
        id = "appearance.text_size",
        page = SettingsPage.APPEARANCE,
        title = "Text size",
        description = "Make text bigger or smaller across the app",
        keywords = listOf("text", "font", "size", "scale", "zoom"),
    )
    val appearanceBottomBarStyle = SettingEntry(
        id = "appearance.bottom_bar_style",
        page = SettingsPage.APPEARANCE,
        title = "Bottom bar style",
        description = "Style and layout of the bottom navigation bar",
        keywords = listOf("bottom", "bar", "navigation", "nav", "style"),
    )
    val appearanceAppIcon = SettingEntry(
        id = "appearance.app_icon",
        page = SettingsPage.APPEARANCE,
        title = "App icon",
        description = "Choose an alternative app launcher icon",
        keywords = listOf("app", "icon", "launcher", "logo"),
    )

    // Home
    val homeBlocks = SettingEntry(
        id = "home.blocks",
        page = SettingsPage.HOME,
        title = "Build your Home",
        description = "Choose which blocks Home shows and in what order",
        keywords = listOf(
            "blocks",
            "reorder",
            "personal home",
            "random pick",
            "favourites",
            "favorites",
            "suggestions",
            "pinned",
            "search",
            "new since last visit",
            "pinned categories",
            "new in pinned categories",
            "sections list",
        ),
    )
    val homeSectionOrder = SettingEntry(
        id = "home.section_order",
        page = SettingsPage.HOME,
        title = "Section order",
        description = "Sort sections alphabetically or in website order",
        keywords = listOf("section", "order", "sort", "alphabetical", "layout"),
    )
    val homeNsfwLink = SettingEntry(
        id = "home.nsfw_link",
        page = SettingsPage.HOME,
        title = "Show NSFW link",
        description = "Add FMHY's NSFW section to Home (18+)",
        keywords = listOf("nsfw", "adult", "18"),
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
        description = "Mark sites that fmhy.net shows as new (added in the last 7 days)",
        keywords = listOf("new", "badges", "recent", "added"),
    )
    val listsSearchTapTarget = SettingEntry(
        id = "lists.search_tap_target",
        page = SettingsPage.LISTS,
        title = "Tap a search result opens",
        description = "The site itself, or its section in Browse",
        keywords = listOf("tap", "search", "result", "section", "open", "site", "gesture", "jump", "browse"),
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
    val listsCategoryNames = SettingEntry(
        id = "lists.category_names",
        page = SettingsPage.LISTS,
        title = "Category names from the FMHY website",
        description = "On: Streaming, Gaming. Off: older names like Movies / TV",
        keywords = listOf("category", "names", "titles", "wiki", "website", "streaming", "listening", "movies"),
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
        description = "Built-in browser, custom tab, or another browser app",
        keywords = listOf("open", "links", "in-app", "custom tab", "external", "browser"),
    )
    val aboutOpenFmhyLinks = SettingEntry(
        id = "about.open_fmhy_links",
        page = SettingsPage.BROWSER,
        title = "Open fmhy.net links in the app",
        description = "Let Android open fmhy.net links in this app",
        keywords = listOf("links", "intent", "default", "open", "url", "about"),
    )
    val browserSearchEngine = SettingEntry(
        id = "browser.search_engine",
        page = SettingsPage.BROWSER,
        title = "Search engine",
        description = "Search engine for words typed in the address bar",
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
        description = "Address bar icon showing if the site is listed on FMHY",
        keywords = listOf("fmhy", "status", "icon", "address bar", "badge"),
    )
    val browserDeadSiteRescue = SettingEntry(
        id = "browser.dead_site_rescue",
        page = SettingsPage.BROWSER,
        title = "Dead-site rescue",
        description = "Offer mirrors and alternative links when a webpage fails to load",
        keywords = listOf("dead", "site", "rescue", "mirror", "wayback", "archive"),
    )
    val browserRestoreTabs = SettingEntry(
        id = "browser.restore_tabs",
        page = SettingsPage.BROWSER,
        title = "Restore tabs on start",
        description = "Reopen your open tabs when relaunching the browser",
        keywords = listOf("restore", "tabs", "reopen", "session", "start"),
    )
    val browserMaxTabs = SettingEntry(
        id = "browser.max_tabs",
        page = SettingsPage.BROWSER,
        title = "Maximum tabs",
        description = "Limit how many open tabs the browser keeps in memory at once",
        keywords = listOf("max", "tabs", "limit", "memory", "count"),
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
        description = "Let websites run JavaScript (most sites need it)",
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
        description = "Screen rotation when a video goes fullscreen",
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
        title = "Auto picture-in-picture",
        description = "Shrink the video to picture-in-picture when you leave the app",
        keywords = listOf("pip", "auto", "shrink", "leave", "background", "start automatically"),
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
    val protectionAskBeforeDownloads = SettingEntry(
        id = "protection.ask_before_downloads",
        page = SettingsPage.DOWNLOADS,
        title = "Ask before downloads",
        description = "Confirm before a file is saved",
        keywords = listOf("download", "prompt", "confirm", "ask", "save", "protection"),
    )
    val downloadsVideoDownloadButton = SettingEntry(
        id = "downloads.video_download_button",
        page = SettingsPage.DOWNLOADS,
        title = "Video download button",
        description = "Download button in the address bar when a video is found",
        keywords = listOf("download", "button", "save", "video", "media"),
    )
    val downloadsShowFmhyStatus = SettingEntry(
        id = "downloads.show_fmhy_status",
        page = SettingsPage.DOWNLOADS,
        title = "Show FMHY status in downloads",
        description = "Show which FMHY section lists the site a file came from",
        keywords = listOf("fmhy", "status", "source", "listing", "downloads"),
    )
    val downloadsVirusTotal = SettingEntry(
        id = "downloads.virustotal",
        page = SettingsPage.DOWNLOADS,
        title = "VirusTotal",
        description = "Check files and links with VirusTotal's antivirus engines",
        keywords = listOf("virustotal", "scan", "antivirus", "security", "malware"),
    )
    val downloadsVirusTotalKey = SettingEntry(
        id = "downloads.virustotal_key",
        page = SettingsPage.DOWNLOADS,
        title = "VirusTotal key",
        description = "Your free VirusTotal API key for automatic checks",
        keywords = listOf("virustotal", "key", "api", "token", "credentials"),
    )
    val downloadsHybridAnalysis = SettingEntry(
        id = "downloads.hybrid_analysis",
        page = SettingsPage.DOWNLOADS,
        title = "Hybrid Analysis",
        description = "Check files in the Falcon Sandbox on Hybrid Analysis",
        keywords = listOf("hybrid", "analysis", "sandbox", "security", "malware"),
    )
    val downloadsHybridAnalysisKey = SettingEntry(
        id = "downloads.hybrid_analysis_key",
        page = SettingsPage.DOWNLOADS,
        title = "Hybrid Analysis key",
        description = "Your free Hybrid Analysis API key for automatic checks",
        keywords = listOf("hybrid", "analysis", "key", "api", "token"),
    )
    val downloadsCheckRiskyFiles = SettingEntry(
        id = "downloads.check_risky_files",
        page = SettingsPage.DOWNLOADS,
        title = "Check risky files automatically",
        description = "Send hashes of APKs, EXEs and scripts to the checks you turned on",
        keywords = listOf("check", "risky", "apk", "exe", "automatic", "scan"),
    )
    val downloadsHoldUntilChecked = SettingEntry(
        id = "downloads.hold_until_checked",
        page = SettingsPage.DOWNLOADS,
        title = "Hold new files until checked",
        description = "Hide new downloads from other apps until you approve them",
        keywords = listOf("hold", "quarantine", "isolate", "pending", "downloads"),
    )
    val protectionWarnRiskyDownloads = SettingEntry(
        id = "protection.warn_risky_downloads",
        page = SettingsPage.DOWNLOADS,
        title = "Warn about APK and EXE downloads",
        description = "Extra warning for app and program files",
        keywords = listOf("apk", "exe", "executable", "warning", "installers", "protection"),
    )
    val protectionForgetVtChecks = SettingEntry(
        id = "protection.forget_vt_checks",
        page = SettingsPage.DOWNLOADS,
        title = "Forget checked sites",
        description = "Clears your VirusTotal results and green badges",
        keywords = listOf("virustotal", "checked", "forget", "badge", "safe", "protection"),
        advanced = false,
    )

    // Ad blocking
    val adBlockingBlockAdsAndTrackers = SettingEntry(
        id = "ad_blocking.block_ads_and_trackers",
        page = SettingsPage.AD_BLOCKING,
        title = "Block ads and trackers",
        description = "Block ads, trackers and malware domains on web pages",
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
        description = "Hide empty ad spaces and placeholders on pages",
        keywords = listOf("hide", "cosmetic", "elements", "collapse", "clean"),
    )
    val adBlockingFixBrokenPages = SettingEntry(
        id = "ad_blocking.fix_broken_pages",
        page = SettingsPage.AD_BLOCKING,
        title = "Fix broken pages",
        description = "Run uBlock Origin fix scripts on pages that break",
        keywords = listOf("fix", "scriptlets", "broken", "compatibility", "ublock"),
    )
    val adBlockingUpdateListsAutomatically = SettingEntry(
        id = "ad_blocking.update_lists_automatically",
        page = SettingsPage.AD_BLOCKING,
        title = "Update lists automatically",
        description = "Download updated filter lists in the background",
        keywords = listOf("auto", "update", "lists", "periodic", "refresh"),
    )
    val adBlockingListStatus = SettingEntry(
        id = "ad_blocking.list_status",
        page = SettingsPage.AD_BLOCKING,
        title = "List status",
        description = "Active rules, last update, and an Update now button",
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
        description = "Show a warning before opening known scam or malware sites",
        keywords = listOf("unsafe", "malware", "phishing", "warning", "security"),
    )
    val protectionWarnPotentiallyUnsafeSites = SettingEntry(
        id = "protection.warn_potentially_unsafe_sites",
        page = SettingsPage.PROTECTION,
        title = "Warn before potentially unsafe sites",
        description = "Also warn about sites that look suspicious",
        keywords = listOf("potential", "suspicious", "warning", "risky", "safety"),
    )
    val protectionGoogleSafeBrowsing = SettingEntry(
        id = "protection.google_safe_browsing",
        page = SettingsPage.PROTECTION,
        title = "Google Safe Browsing",
        description = "Also check sites against Google's list of dangerous sites",
        keywords = listOf("google", "safe", "browsing", "phishing", "malware"),
    )
    val protectionCriticalNoteWarning = SettingEntry(
        id = "protection.critical_note_warning",
        page = SettingsPage.PROTECTION,
        title = "Warn before opening sites with a critical note",
        description = "Show a warning before opening sites that have a critical note",
        keywords = listOf("critical", "warning", "note", "dialog"),
    )
    val protectionResetHiddenWarnings = SettingEntry(
        id = "protection.reset_hidden_warnings",
        page = SettingsPage.PROTECTION,
        title = "Reset hidden warnings",
        description = "Show critical note warnings again for all sites",
        keywords = listOf("critical", "warning", "note", "reset", "hidden"),
    )
    val protectionBlockPopups = SettingEntry(
        id = "protection.block_popups",
        page = SettingsPage.PROTECTION,
        title = "Block pop-ups",
        description = "Stop sites from opening pop-up windows",
        keywords = listOf("popup", "popups", "window", "block", "tabs"),
    )
    val protectionBlockRedirects = SettingEntry(
        id = "protection.block_redirects",
        page = SettingsPage.PROTECTION,
        title = "Block redirects without a tap",
        description = "Stop pages from sending you elsewhere unless you tapped a link",
        keywords = listOf("redirect", "redirects", "tap", "forward", "navigate"),
    )
    val protectionBlockAppLinks = SettingEntry(
        id = "protection.block_app_links",
        page = SettingsPage.PROTECTION,
        title = "Block links that open apps",
        description = "Ask before a page opens another app",
        keywords = listOf("apps", "intent", "external", "open", "links"),
    )

    // VPN & proxy
    val protectionShowVpnState = SettingEntry(
        id = "protection.show_vpn_state",
        page = SettingsPage.CONNECTION,
        title = "Show VPN and HTTPS ring",
        description = "Ring on the site icon shows HTTPS, VPN and Orbot. Tap for details",
        keywords = listOf("vpn", "ring", "https", "secure", "lock", "protected", "favicon", "protection"),
    )
    val protectionBlockWithoutVpn = SettingEntry(
        id = "protection.block_without_vpn",
        page = SettingsPage.CONNECTION,
        title = "Disable browsing without VPN",
        description = "Show a \"No VPN\" page while no VPN is on. Needs the VPN ring",
        keywords = listOf("vpn", "block", "disable", "browsing", "without", "kill", "switch", "protection"),
    )
    val protectionShowVpnHomeIcon = SettingEntry(
        id = "protection.show_vpn_home_icon",
        page = SettingsPage.CONNECTION,
        title = "Show VPN icon on Home",
        description = "Lock icon on Home: green with a VPN, purple with Orbot, else amber",
        keywords = listOf("vpn", "lock", "home", "icon", "protected", "protection"),
    )
    val protectionShowIpCountry = SettingEntry(
        id = "protection.show_ip_country",
        page = SettingsPage.CONNECTION,
        title = "Show my IP and country",
        description = "Show your IP and country in connection details (via Mullvad)",
        keywords = listOf("ip", "country", "location", "mullvad", "vpn", "protection"),
    )
    val protectionProxy = SettingEntry(
        id = "protection.proxy",
        page = SettingsPage.CONNECTION,
        title = "Proxy",
        description = "Send all app and tab traffic through Orbot or a SOCKS5/HTTP proxy",
        keywords = listOf("proxy", "tor", "orbot", "socks", "socks5", "http", "protection"),
    )
    val protectionPrivateDns = SettingEntry(
        id = "protection.private_dns",
        page = SettingsPage.CONNECTION,
        title = "Private DNS (system)",
        description = "Android's Private DNS state; it also covers the in-app browser",
        keywords = listOf("dns", "doh", "dot", "private", "tls", "resolver", "provider", "adguard", "cloudflare", "protection"),
    )

    // Privacy & history
    val privacyBlockThirdPartyCookies = SettingEntry(
        id = "privacy.block_third_party_cookies",
        page = SettingsPage.PRIVACY,
        title = "Block third-party cookies",
        description = "Stop other sites from setting tracking cookies",
        keywords = listOf("cookie", "cookies", "third-party", "tracking", "privacy"),
    )
    val privacyHideCookieBanners = SettingEntry(
        id = "privacy.hide_cookie_banners",
        page = SettingsPage.PRIVACY,
        title = "Hide cookie banners",
        description = "Hide and reject cookie consent pop-ups",
        keywords = listOf("cookie", "cookies", "banners", "consent", "gdpr", "notices"),
    )
    val privacySendGpc = SettingEntry(
        id = "privacy.send_gpc",
        page = SettingsPage.PRIVACY,
        title = "Ask sites not to track me",
        description = "Send the Global Privacy Control signal (do not sell or share)",
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
        description = "Delete cookies and site storage when you close the app",
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
        description = "Alert when saved sites change, move or are removed",
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
        description = "How often to check followed sections and saved sites",
        keywords = listOf("interval", "frequency", "schedule", "period", "background"),
        advanced = true,
    )
    val notificationsDetailedChangeHistory = SettingEntry(
        id = "notifications.detailed_change_history",
        page = SettingsPage.NOTIFICATIONS,
        title = "Detailed change history",
        description = "Get exact dates and changes from fmhy-tracker",
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

    // App updates
    val appUpdatesCheckNow = SettingEntry(
        id = "app_updates.check_now",
        page = SettingsPage.APP_UPDATES,
        title = "Check for updates",
        description = "Look for a new version on GitHub now",
        keywords = listOf("check", "update", "upgrade", "version", "now"),
    )
    val appUpdatesAutoCheck = SettingEntry(
        id = "app_updates.auto_check",
        page = SettingsPage.APP_UPDATES,
        title = "Automatic checks",
        description = "Look for new app versions in the background",
        keywords = listOf("auto", "check", "update", "periodic", "background"),
    )
    val appUpdatesNotify = SettingEntry(
        id = "app_updates.notify",
        page = SettingsPage.APP_UPDATES,
        title = "Update notifications",
        description = "Notify me when a new app version is available",
        keywords = listOf("notify", "notification", "alert", "update", "push"),
    )
    val appUpdatesInterval = SettingEntry(
        id = "app_updates.interval",
        page = SettingsPage.APP_UPDATES,
        title = "Check frequency",
        description = "How often to look for new versions; the app also checks at start",
        keywords = listOf("frequency", "interval", "hours", "daily", "weekly", "check", "background"),
    )
    val appUpdatesSkippedVersion = SettingEntry(
        id = "app_updates.skipped_version",
        page = SettingsPage.APP_UPDATES,
        title = "Skipped version",
        description = "A version you skipped; you won't be asked about it again",
        keywords = listOf("skipped", "ignore", "version", "update"),
    )

    // Data & backup
    val syncSyncNow = SettingEntry(
        id = "sync.sync_now",
        page = SettingsPage.DATA,
        title = "Sync now",
        description = "Get the latest FMHY site list now",
        keywords = listOf("sync", "now", "fetch", "refresh", "download"),
    )
    val syncSyncOnLaunch = SettingEntry(
        id = "sync.sync_on_launch",
        page = SettingsPage.DATA,
        title = "Sync on launch",
        description = "Check for new FMHY data every time the app opens",
        keywords = listOf("sync", "launch", "start", "open", "automatic"),
    )
    val syncLastSynced = SettingEntry(
        id = "sync.last_synced",
        page = SettingsPage.DATA,
        title = "Last synced",
        description = "When the FMHY data was last updated",
        keywords = listOf("last", "synced", "updated", "time", "status", "sync"),
    )
    val dataUsageDownloadWifiOnly = SettingEntry(
        id = "data_usage.download_wifi_only",
        page = SettingsPage.DATA,
        title = "Download only on Wi-Fi",
        description = "Download files and media only on Wi-Fi",
        keywords = listOf("download", "wifi", "cellular", "mobile data", "metered", "data usage"),
    )
    val dataUsageBrowseWifiOnly = SettingEntry(
        id = "data_usage.browse_wifi_only",
        page = SettingsPage.DATA,
        title = "Browse only on Wi-Fi",
        description = "Load web pages only on Wi-Fi to save mobile data",
        keywords = listOf("browse", "wifi", "cellular", "mobile data", "network", "data usage"),
    )
    val dataUsageAppDataWifiOnly = SettingEntry(
        id = "data_usage.app_data_wifi_only",
        page = SettingsPage.DATA,
        title = "App data and updates only on Wi-Fi",
        description = "FMHY data, filter lists and app updates only on Wi-Fi",
        keywords = listOf("sync", "updates", "wifi", "cellular", "mobile data", "data usage"),
    )
    val backupExportBackup = SettingEntry(
        id = "backup.export_backup",
        page = SettingsPage.DATA,
        title = "Export backup",
        description = "Save bookmarks, followed sections and settings to a JSON file",
        keywords = listOf("export", "backup", "save", "json", "exporting", "settings", "backup & restore"),
    )
    val backupImportBackup = SettingEntry(
        id = "backup.import_backup",
        page = SettingsPage.DATA,
        title = "Import backup",
        description = "Restore bookmarks, folders and settings from a JSON backup",
        keywords = listOf("import", "backup", "restore", "json", "recover", "settings", "backup & restore"),
    )

    // FMHY X account
    val fmhyxAccountToken = SettingEntry(
        id = "fmhyx_account.account_token",
        page = SettingsPage.FMHYX_ACCOUNT,
        title = "Account token",
        description = "Sign in to sync saved sites and folders with FMHY X",
        keywords = listOf("fmhy x", "fmhyx", "account", "sync", "token", "saved", "sign in"),
    )
    val fmhyxAccountSyncNow = SettingEntry(
        id = "fmhyx_account.sync_now",
        page = SettingsPage.FMHYX_ACCOUNT,
        title = "Sync now",
        description = "Sync your saved sites and folders with FMHY X now",
        keywords = listOf("fmhy x", "fmhyx", "account", "sync", "saved", "now"),
    )
    val fmhyxAccountCopyToken = SettingEntry(
        id = "fmhyx_account.copy_token",
        page = SettingsPage.FMHYX_ACCOUNT,
        title = "Copy token",
        description = "Copy your FMHY X account token to the clipboard",
        keywords = listOf("fmhy x", "fmhyx", "account", "token", "copy"),
    )
    val fmhyxAccountSignOut = SettingEntry(
        id = "fmhyx_account.sign_out",
        page = SettingsPage.FMHYX_ACCOUNT,
        title = "Sign out",
        description = "Stop syncing; saved sites stay on this phone",
        keywords = listOf("fmhy x", "fmhyx", "account", "sign out", "logout", "saved"),
    )
    val fmhyxAccountDeleteAccount = SettingEntry(
        id = "fmhyx_account.delete_account",
        page = SettingsPage.FMHYX_ACCOUNT,
        title = "Delete account",
        description = "Delete your synced data from x.fmhy.bid",
        keywords = listOf("fmhy x", "fmhyx", "account", "delete", "remove"),
    )

    // Report a problem
    val reportReportProblem = SettingEntry(
        id = "report.report_problem",
        page = SettingsPage.REPORT,
        title = "Report a problem",
        description = "Tell us what went wrong or what is missing",
        keywords = listOf("report", "problem", "feedback", "bug", "issue", "support"),
    )
    val reportOfferToReportCrashes = SettingEntry(
        id = "report.offer_to_report_crashes",
        page = SettingsPage.REPORT,
        title = "Offer to report crashes",
        description = "After a crash, offer to send an error report",
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
    val aboutGithubRepo = SettingEntry(
        id = "about.github_repo",
        page = SettingsPage.ABOUT,
        title = "GitHub repo",
        description = "Source code, releases and issues",
        keywords = listOf("github", "repo", "source", "code", "issues", "releases"),
    )
    val aboutRunSetupAgain = SettingEntry(
        id = "about.run_setup_again",
        page = SettingsPage.ABOUT,
        title = "Run setup again",
        description = "Show the first-start setup again",
        keywords = listOf("setup", "onboarding", "walkthrough", "restart", "first-run"),
    )

    val all: List<SettingEntry> = listOf(
        // Appearance
        appearanceTheme,
        appearancePureBlack,
        appearanceAccentColor,
        appearanceTextSize,
        appearanceBottomBarStyle,
        appearanceAppIcon,
        // Home
        homeBlocks,
        homeSectionOrder,
        homeNsfwLink,
        homePinnedSections,
        homeHiddenSections,
        // Lists & gestures
        listsWebsiteStyle,
        listsSectionLayout,
        listsCategoryNames,
        listsStarredOnly,
        listsShowIndexes,
        listsNewBadges,
        listsSearchTapTarget,
        listsSwapTapAndHold,
        listsSwipeToBookmark,
        listsSwipeBetweenScreens,
        listsHapticFeedback,
        listsIconLegend,
        // Browser
        browserOpenLinksWith,
        aboutOpenFmhyLinks,
        browserSearchEngine,
        browserAddressBar,
        browserSiteBar,
        browserDeadSiteRescue,
        browserRestoreTabs,
        browserMaxTabs,
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
        protectionAskBeforeDownloads,
        downloadsVideoDownloadButton,
        downloadsShowFmhyStatus,
        downloadsVirusTotal,
        downloadsVirusTotalKey,
        downloadsHybridAnalysis,
        downloadsHybridAnalysisKey,
        downloadsCheckRiskyFiles,
        downloadsHoldUntilChecked,
        protectionWarnRiskyDownloads,
        protectionForgetVtChecks,
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
        protectionCriticalNoteWarning,
        protectionResetHiddenWarnings,
        protectionBlockPopups,
        protectionBlockRedirects,
        protectionBlockAppLinks,
        // VPN & proxy
        protectionShowVpnState,
        protectionBlockWithoutVpn,
        protectionShowVpnHomeIcon,
        protectionShowIpCountry,
        protectionProxy,
        protectionPrivateDns,
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
        // App updates
        appUpdatesCheckNow,
        appUpdatesAutoCheck,
        appUpdatesNotify,
        appUpdatesInterval,
        appUpdatesSkippedVersion,
        // Data & backup
        syncSyncNow,
        syncSyncOnLaunch,
        syncLastSynced,
        dataUsageDownloadWifiOnly,
        dataUsageBrowseWifiOnly,
        dataUsageAppDataWifiOnly,
        backupExportBackup,
        backupImportBackup,
        // FMHY X account
        fmhyxAccountToken,
        fmhyxAccountSyncNow,
        fmhyxAccountCopyToken,
        fmhyxAccountSignOut,
        fmhyxAccountDeleteAccount,
        // Report a problem
        reportReportProblem,
        reportOfferToReportCrashes,
        // About
        aboutAboutFmhy,
        aboutVersion,
        aboutGithubRepo,
        aboutRunSetupAgain,
    ).filter { entry ->
        (BuildConfig.NSFW_LINK_ENABLED || entry != homeNsfwLink) &&
            (BuildConfig.FMHYX_ENABLED || entry.page != SettingsPage.FMHYX_ACCOUNT)
    }

    fun byId(id: String): SettingEntry? = all.firstOrNull { it.id == id }
}
