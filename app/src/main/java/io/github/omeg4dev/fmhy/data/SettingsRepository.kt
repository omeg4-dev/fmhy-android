package io.github.omeg4dev.fmhy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.omeg4dev.fmhy.data.adblock.FilterListCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fmhy_settings")

interface SettingsRepository {
    val lastVisitTs: Flow<Long>
    suspend fun setLastVisitTs(ts: Long)

    val recentSearches: Flow<List<String>>
    suspend fun addRecentSearch(query: String)
    suspend fun removeRecentSearch(query: String)
    suspend fun clearRecentSearches()

    val appearance: Flow<Appearance>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setColorSource(source: ColorSource)
    suspend fun setPureBlack(enabled: Boolean)
    suspend fun setAccentSeed(argb: Int)
    suspend fun setSectionLayout(layout: SectionLayout)
    suspend fun setTextScale(scale: Float)
    suspend fun setSectionOrder(order: SectionOrder)
    suspend fun setShowRandomPick(show: Boolean)
    suspend fun setStarredOnly(starredOnly: Boolean)
    suspend fun setShowIndexes(showIndexes: Boolean)
    suspend fun setHapticFeedback(enabled: Boolean)
    suspend fun setWebsiteStyle(enabled: Boolean)
    suspend fun setAppIcon(icon: AppIcon)

    val homePrefs: Flow<HomePrefs>
    suspend fun updateHome(f: (HomePrefs) -> HomePrefs)

    val browserPrefs: Flow<BrowserPrefs>
    suspend fun updateBrowser(f: (BrowserPrefs) -> BrowserPrefs)

    val blockingPrefs: Flow<BlockingPrefs>
    suspend fun updateBlocking(f: (BlockingPrefs) -> BlockingPrefs)

    val protectionPrefs: Flow<ProtectionPrefs>
    suspend fun updateProtection(f: (ProtectionPrefs) -> ProtectionPrefs)

    val privacyPrefs: Flow<PrivacyPrefs>
    suspend fun updatePrivacy(f: (PrivacyPrefs) -> PrivacyPrefs)

    val notificationPrefs: Flow<NotificationPrefs>
    suspend fun updateNotifications(f: (NotificationPrefs) -> NotificationPrefs)

    val dataUsagePrefs: Flow<DataUsagePrefs>
        get() = kotlinx.coroutines.flow.flowOf(DataUsagePrefs())
    suspend fun updateDataUsage(f: (DataUsagePrefs) -> DataUsagePrefs) {}

    val updatePrefs: Flow<UpdatePrefs>
        get() = kotlinx.coroutines.flow.flowOf(UpdatePrefs())
    suspend fun updateUpdatePrefs(f: (UpdatePrefs) -> UpdatePrefs) {}

    val downloadPrefs: Flow<DownloadPrefs>
        get() = kotlinx.coroutines.flow.flowOf(DownloadPrefs())
    suspend fun updateDownloadPrefs(f: (DownloadPrefs) -> DownloadPrefs) {}

    val checkPrefs: Flow<CheckPrefs>
        get() = kotlinx.coroutines.flow.flowOf(CheckPrefs())
    suspend fun updateCheckPrefs(f: (CheckPrefs) -> CheckPrefs) {}

    val followNotifyPromptShown: Flow<Boolean>
        get() = kotlinx.coroutines.flow.flowOf(false)
    suspend fun setFollowNotifyPromptShown(shown: Boolean) {}

    val gestureHintShown: Flow<Boolean>
        get() = kotlinx.coroutines.flow.flowOf(false)
    suspend fun setGestureHintShown(shown: Boolean) {}

    val reportOfferCrashes: Flow<Boolean>
        get() = kotlinx.coroutines.flow.flowOf(true)
    suspend fun setReportOfferCrashes(enabled: Boolean) {}

    val dataPrefs: Flow<DataPrefs>
    suspend fun updateData(f: (DataPrefs) -> DataPrefs)

    val onboardingVersion: Flow<Int>
    suspend fun setOnboardingVersion(v: Int)
}

class DefaultSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    constructor(context: Context) : this(context.dataStore)

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val KEY_LAST_VISIT_TS = longPreferencesKey("last_visit_ts")
        private val KEY_OPEN_EXTERNAL_BROWSER = booleanPreferencesKey("open_in_external_browser")
        private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_COLOR_SOURCE = stringPreferencesKey("color_source")
        private val KEY_SECTION_LAYOUT = stringPreferencesKey("section_layout")
        private val KEY_PURE_BLACK = booleanPreferencesKey("pure_black")
        private val KEY_ACCENT_SEED = intPreferencesKey("accent_seed")
        private val KEY_TEXT_SCALE = floatPreferencesKey("text_scale")
        private val KEY_SECTION_ORDER = stringPreferencesKey("section_order")
        private val KEY_SHOW_RANDOM_PICK = booleanPreferencesKey("show_random_pick")
        private val KEY_STARRED_ONLY = booleanPreferencesKey("starred_only")
        private val KEY_SHOW_INDEXES = booleanPreferencesKey("show_indexes")
        private val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        private val KEY_WEBSITE_STYLE = booleanPreferencesKey("website_style")
        private val KEY_APP_ICON = stringPreferencesKey("app_icon")
        private val KEY_ONBOARDING_VERSION = intPreferencesKey("onboarding_version")

        // Home
        private val KEY_HOME_PERSONAL_HOME = booleanPreferencesKey("home_personal_home")
        private val KEY_HOME_NEW_BADGE_DAYS = intPreferencesKey("home_new_badge_days")
        private val KEY_HOME_SEARCH_EXACT = booleanPreferencesKey("home_search_exact")
        private val KEY_HOME_SWIPE_TO_BOOKMARK = booleanPreferencesKey("home_swipe_to_bookmark")
        private val KEY_HOME_SWAP_TAP_HOLD = booleanPreferencesKey("home_swap_tap_hold")

        // Browser
        private val KEY_BROWSER_OPEN_LINKS_WITH = stringPreferencesKey("browser_open_links_with")
        private val KEY_BROWSER_SITE_BAR = booleanPreferencesKey("browser_site_bar")
        private val KEY_BROWSER_DEAD_SITE_RESCUE = booleanPreferencesKey("browser_dead_site_rescue")
        private val KEY_BROWSER_MAX_TABS = intPreferencesKey("browser_max_tabs")
        private val KEY_BROWSER_RESTORE_TABS = booleanPreferencesKey("browser_restore_tabs")
        private val KEY_BROWSER_SEARCH_ENGINE = stringPreferencesKey("browser_search_engine")
        private val KEY_BROWSER_TOOLBAR_POSITION = stringPreferencesKey("browser_toolbar_position")
        private val KEY_BROWSER_BUTTON = stringPreferencesKey("browser_button_style")
        private val KEY_BROWSER_BUBBLE_HIDDEN = booleanPreferencesKey("browser_bubble_hidden")
        private val KEY_BROWSER_BUBBLE_SIDE = stringPreferencesKey("browser_bubble_side")
        private val KEY_BROWSER_BUBBLE_Y = floatPreferencesKey("browser_bubble_y")
        private val KEY_BROWSER_TABS_VIEW = stringPreferencesKey("browser_tabs_view")
        private val KEY_BROWSER_JAVASCRIPT = booleanPreferencesKey("browser_javascript")
        private val KEY_BROWSER_FORCE_DARK = booleanPreferencesKey("browser_force_dark")
        private val KEY_BROWSER_DESKTOP_BY_DEFAULT = booleanPreferencesKey("browser_desktop_by_default")
        private val KEY_BROWSER_TEXT_ZOOM_FOLLOWS_APP = booleanPreferencesKey("browser_text_zoom_follows_app")
        private val KEY_BROWSER_FORCE_ZOOM = booleanPreferencesKey("browser_force_zoom")
        private val KEY_BROWSER_FULLSCREEN_ORIENTATION = stringPreferencesKey("browser_fullscreen_orientation")
        /** Pre-0.5.2 boolean; only read, as the fallback for [KEY_BROWSER_FULLSCREEN_ORIENTATION]. */
        private val KEY_BROWSER_VIDEO_AUTO_ROTATE = booleanPreferencesKey("browser_video_auto_rotate")
        private val KEY_BROWSER_PICTURE_IN_PICTURE = booleanPreferencesKey("browser_picture_in_picture")
        private val KEY_BROWSER_AUTO_PIP = booleanPreferencesKey("browser_auto_pip")
        private val KEY_BROWSER_DEFAULT_SPEED = floatPreferencesKey("browser_default_speed")
        private val KEY_BROWSER_DOUBLE_TAP_SEEK = booleanPreferencesKey("browser_double_tap_seek")
        private val KEY_BROWSER_AUTOPLAY = stringPreferencesKey("browser_autoplay")
        private val KEY_BROWSER_BACKGROUND_PLAYBACK = booleanPreferencesKey("browser_background_playback")
        private val KEY_BROWSER_KEEP_SCREEN_ON = booleanPreferencesKey("browser_keep_screen_on")
        private val KEY_BROWSER_VIDEO_DOWNLOAD_BUTTON = booleanPreferencesKey("browser_video_download_button")

        // Blocking
        private val KEY_BLOCKING_ADBLOCK = booleanPreferencesKey("blocking_adblock")
        private val KEY_BLOCKING_ENABLED_LISTS = stringSetPreferencesKey("blocking_enabled_lists")
        private val KEY_BLOCKING_CUSTOM_RULES = stringPreferencesKey("blocking_custom_rules")
        private val KEY_BLOCKING_ALLOWLIST = stringSetPreferencesKey("blocking_allowlist")
        private val KEY_BLOCKING_SHOW_BLOCKED_COUNT = booleanPreferencesKey("blocking_show_blocked_count")
        private val KEY_BLOCKING_ELEMENT_HIDING = booleanPreferencesKey("blocking_element_hiding")
        private val KEY_BLOCKING_SCRIPTLETS = booleanPreferencesKey("blocking_scriptlets")
        private val KEY_BLOCKING_AUTO_UPDATE_LISTS = booleanPreferencesKey("blocking_auto_update_lists")

        // Protection
        private val KEY_PROTECTION_UNSAFE_WARNING = booleanPreferencesKey("protection_unsafe_warning")
        private val KEY_PROTECTION_POTENTIALLY_UNSAFE_WARNING = booleanPreferencesKey("protection_potentially_unsafe_warning")
        private val KEY_PROTECTION_POPUP_BLOCKER = booleanPreferencesKey("protection_popup_blocker")
        private val KEY_PROTECTION_REDIRECT_GUARD = booleanPreferencesKey("protection_redirect_guard")
        private val KEY_PROTECTION_BLOCK_APP_LINKS = booleanPreferencesKey("protection_block_app_links")
        private val KEY_PROTECTION_ASK_BEFORE_DOWNLOAD = booleanPreferencesKey("protection_ask_before_download")
        private val KEY_PROTECTION_WARN_RISKY_DOWNLOADS = booleanPreferencesKey("protection_warn_risky_downloads")
        private val KEY_PROTECTION_SAFE_BROWSING = booleanPreferencesKey("protection_safe_browsing")

        // Privacy
        private val KEY_PRIVACY_BLOCK_THIRD_PARTY_COOKIES = booleanPreferencesKey("privacy_block_third_party_cookies")
        private val KEY_PRIVACY_HIDE_COOKIE_BANNERS = booleanPreferencesKey("privacy_hide_cookie_banners")
        private val KEY_PRIVACY_CLEAR_DATA_ON_EXIT = booleanPreferencesKey("privacy_clear_data_on_exit")
        private val KEY_PRIVACY_PRIVATE_TABS_BY_DEFAULT = booleanPreferencesKey("privacy_private_tabs_by_default")
        private val KEY_PRIVACY_SEND_GPC = booleanPreferencesKey("privacy_send_gpc")

        // Notifications
        private val KEY_NOTIFICATIONS_USE_TRACKER = booleanPreferencesKey("notifications_use_tracker")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_NOTIFICATIONS_NOTIFY_FOLLOWED = booleanPreferencesKey("notifications_notify_followed")
        private val KEY_NOTIFICATIONS_WATCH_SAVED = booleanPreferencesKey("notifications_watch_saved")
        private val KEY_NOTIFICATIONS_NOTIFY_NEW_POSTS = booleanPreferencesKey("notifications_notify_new_posts")
        private val KEY_NOTIFICATIONS_INTERVAL = stringPreferencesKey("notifications_interval")
        private val KEY_NOTIFICATIONS_WIFI_ONLY = booleanPreferencesKey("notifications_wifi_only")
        private val KEY_FOLLOW_NOTIFY_PROMPT_SHOWN = booleanPreferencesKey("follow_notify_prompt_shown")
        private val KEY_GESTURE_HINT_SHOWN = booleanPreferencesKey("gesture_hint_shown")

        // Data usage
        private val KEY_DATA_USAGE_DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("data_usage_download_wifi_only")
        private val KEY_DATA_USAGE_BROWSE_WIFI_ONLY = booleanPreferencesKey("data_usage_browse_wifi_only")
        private val KEY_DATA_USAGE_APP_DATA_WIFI_ONLY = booleanPreferencesKey("data_usage_app_data_wifi_only")

        // Data
        private val KEY_DATA_SYNC_ON_LAUNCH = booleanPreferencesKey("data_sync_on_launch")
        private val KEY_DATA_KEEP_HISTORY = booleanPreferencesKey("data_keep_history")

        // Problem reports
        private val KEY_REPORT_OFFER_CRASHES = booleanPreferencesKey("report_offer_crashes")

        // In-app update
        private val KEY_UPDATE_AUTO_CHECK = booleanPreferencesKey("update_auto_check")
        private val KEY_UPDATE_LAST_CHECK_MS = longPreferencesKey("update_last_check_ms")
        private val KEY_UPDATE_LATER_UNTIL_MS = longPreferencesKey("update_later_until_ms")
        private val KEY_UPDATE_SKIPPED_VERSION = stringPreferencesKey("update_skipped_version")
        private val KEY_UPDATE_INSTALL_EXPLAINED = booleanPreferencesKey("update_install_explained")
        private val KEY_UPDATE_KNOWN_AVAILABLE = stringPreferencesKey("update_known_available")

        // Downloads
        private val KEY_DOWNLOAD_FOLDER_URI = stringPreferencesKey("download_folder_uri")
        private val KEY_DOWNLOAD_FOLDER_FALLBACK_NOTIFIED = booleanPreferencesKey("download_folder_fallback_notified")

        // Malware checks
        private val KEY_CHECK_VT_ON = booleanPreferencesKey("check_vt_on")
        private val KEY_CHECK_VT_KEY = stringPreferencesKey("check_vt_key")
        private val KEY_CHECK_HA_ON = booleanPreferencesKey("check_ha_on")
        private val KEY_CHECK_HA_KEY = stringPreferencesKey("check_ha_key")
        private val KEY_CHECK_HOLD = booleanPreferencesKey("check_hold")
        private val KEY_CHECK_AUTO_RISKY = booleanPreferencesKey("check_auto_risky")
        private val KEY_CHECK_KEY_PROMPT_SHOWN = booleanPreferencesKey("check_key_prompt_shown")
        private val KEY_CHECK_EXPLAIN_SHOWN = booleanPreferencesKey("check_explain_shown")
        private val KEY_CHECK_FMHY_DOWNLOAD_STATUS = booleanPreferencesKey("check_fmhy_download_status")
    }

    override val lastVisitTs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_VISIT_TS] ?: 0L
    }

    override suspend fun setLastVisitTs(ts: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_VISIT_TS] = ts
        }
    }

    override val recentSearches: Flow<List<String>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_RECENT_SEARCHES] ?: return@map emptyList()
        try {
            json.decodeFromString<List<String>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        dataStore.edit { prefs ->
            val existing = try {
                val raw = prefs[KEY_RECENT_SEARCHES] ?: "[]"
                json.decodeFromString<List<String>>(raw).toMutableList()
            } catch (_: Exception) {
                mutableListOf()
            }
            existing.remove(trimmed)
            existing.add(0, trimmed)
            val updated = existing.take(10)
            prefs[KEY_RECENT_SEARCHES] = json.encodeToString(updated)
        }
    }

    override suspend fun removeRecentSearch(query: String) {
        dataStore.edit { prefs ->
            val existing = try {
                val raw = prefs[KEY_RECENT_SEARCHES] ?: "[]"
                json.decodeFromString<List<String>>(raw).toMutableList()
            } catch (_: Exception) {
                mutableListOf()
            }
            existing.remove(query)
            prefs[KEY_RECENT_SEARCHES] = json.encodeToString(existing)
        }
    }

    override suspend fun clearRecentSearches() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_RECENT_SEARCHES)
        }
    }

    override val appearance: Flow<Appearance> = dataStore.data.map { prefs ->
        val themeMode = try {
            prefs[KEY_THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
        val colorSource = try {
            prefs[KEY_COLOR_SOURCE]?.let { ColorSource.valueOf(it) } ?: ColorSource.FMHY
        } catch (_: IllegalArgumentException) {
            ColorSource.FMHY
        }
        val sectionLayout = try {
            prefs[KEY_SECTION_LAYOUT]?.let { SectionLayout.valueOf(it) } ?: SectionLayout.CONTINUOUS
        } catch (_: IllegalArgumentException) {
            SectionLayout.CONTINUOUS
        }
        val pureBlack = prefs[KEY_PURE_BLACK] ?: false
        val accentSeed = prefs[KEY_ACCENT_SEED] ?: 0xFF7C5CFF.toInt()
        val textScale = (prefs[KEY_TEXT_SCALE] ?: 1.0f).coerceIn(0.85f, 1.30f)
        val sectionOrder = try {
            prefs[KEY_SECTION_ORDER]?.let { SectionOrder.valueOf(it) } ?: SectionOrder.WEBSITE
        } catch (_: IllegalArgumentException) {
            SectionOrder.WEBSITE
        }
        val showRandomPick = prefs[KEY_SHOW_RANDOM_PICK] ?: true
        val starredOnly = prefs[KEY_STARRED_ONLY] ?: false
        val showIndexes = prefs[KEY_SHOW_INDEXES] ?: true
        val hapticFeedback = prefs[KEY_HAPTIC_FEEDBACK] ?: true
        val websiteStyle = prefs[KEY_WEBSITE_STYLE] ?: false
        val appIcon = try {
            prefs[KEY_APP_ICON]?.let { AppIcon.valueOf(it) } ?: AppIcon.DEFAULT
        } catch (_: IllegalArgumentException) {
            AppIcon.DEFAULT
        }

        Appearance(
            themeMode = themeMode,
            colorSource = colorSource,
            pureBlack = pureBlack,
            accentSeed = accentSeed,
            sectionLayout = sectionLayout,
            textScale = textScale,
            sectionOrder = sectionOrder,
            showRandomPick = showRandomPick,
            starredOnly = starredOnly,
            showIndexes = showIndexes,
            hapticFeedback = hapticFeedback,
            websiteStyle = websiteStyle,
            appIcon = appIcon,
        )
    }.distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    override suspend fun setColorSource(source: ColorSource) {
        dataStore.edit { prefs ->
            prefs[KEY_COLOR_SOURCE] = source.name
        }
    }

    override suspend fun setPureBlack(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_PURE_BLACK] = enabled
        }
    }

    override suspend fun setAccentSeed(argb: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCENT_SEED] = argb
        }
    }

    override suspend fun setSectionLayout(layout: SectionLayout) {
        dataStore.edit { prefs ->
            prefs[KEY_SECTION_LAYOUT] = layout.name
        }
    }

    override suspend fun setTextScale(scale: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_TEXT_SCALE] = scale.coerceIn(0.85f, 1.30f)
        }
    }

    override suspend fun setSectionOrder(order: SectionOrder) {
        dataStore.edit { prefs ->
            prefs[KEY_SECTION_ORDER] = order.name
        }
    }

    override suspend fun setShowRandomPick(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_RANDOM_PICK] = show
        }
    }

    override suspend fun setStarredOnly(starredOnly: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_STARRED_ONLY] = starredOnly
        }
    }

    override suspend fun setShowIndexes(showIndexes: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_INDEXES] = showIndexes
        }
    }

    override suspend fun setHapticFeedback(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HAPTIC_FEEDBACK] = enabled
        }
    }

    override suspend fun setWebsiteStyle(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_WEBSITE_STYLE] = enabled
        }
    }

    override suspend fun setAppIcon(icon: AppIcon) {
        dataStore.edit { prefs ->
            prefs[KEY_APP_ICON] = icon.name
        }
    }

    // --- HomePrefs ---
    private fun readHomePrefs(prefs: Preferences): HomePrefs {
        val default = HomePrefs()
        return HomePrefs(
            personalHome = prefs[KEY_HOME_PERSONAL_HOME] ?: default.personalHome,
            newBadgeDays = prefs[KEY_HOME_NEW_BADGE_DAYS] ?: default.newBadgeDays,
            searchExact = prefs[KEY_HOME_SEARCH_EXACT] ?: default.searchExact,
            swipeToBookmark = prefs[KEY_HOME_SWIPE_TO_BOOKMARK] ?: default.swipeToBookmark,
            swapTapHold = prefs[KEY_HOME_SWAP_TAP_HOLD] ?: default.swapTapHold,
        )
    }

    private fun writeHomePrefs(prefs: MutablePreferences, p: HomePrefs) {
        prefs[KEY_HOME_PERSONAL_HOME] = p.personalHome
        prefs[KEY_HOME_SEARCH_EXACT] = p.searchExact
        prefs[KEY_HOME_NEW_BADGE_DAYS] = p.newBadgeDays
        prefs[KEY_HOME_SWIPE_TO_BOOKMARK] = p.swipeToBookmark
        prefs[KEY_HOME_SWAP_TAP_HOLD] = p.swapTapHold
    }

    override val homePrefs: Flow<HomePrefs> = dataStore.data.map { prefs ->
        readHomePrefs(prefs)
    }.distinctUntilChanged()

    override suspend fun updateHome(f: (HomePrefs) -> HomePrefs) {
        dataStore.edit { prefs ->
            val current = readHomePrefs(prefs)
            val updated = f(current)
            writeHomePrefs(prefs, updated)
        }
    }

    // --- BrowserPrefs ---
    private fun readBrowserPrefs(prefs: Preferences): BrowserPrefs {
        val default = BrowserPrefs()
        val openLinksWith = if (prefs.contains(KEY_BROWSER_OPEN_LINKS_WITH)) {
            prefs[KEY_BROWSER_OPEN_LINKS_WITH]?.let { name ->
                try { OpenLinksWith.valueOf(name) } catch (_: IllegalArgumentException) { default.openLinksWith }
            } ?: default.openLinksWith
        } else {
            if (prefs[KEY_OPEN_EXTERNAL_BROWSER] == true) OpenLinksWith.EXTERNAL else default.openLinksWith
        }
        val searchEngine = prefs[KEY_BROWSER_SEARCH_ENGINE]?.let { name ->
            try { SearchEngine.valueOf(name) } catch (_: IllegalArgumentException) { default.searchEngine }
        } ?: default.searchEngine
        val toolbarPosition = prefs[KEY_BROWSER_TOOLBAR_POSITION]?.let { name ->
            try { ToolbarPosition.valueOf(name) } catch (_: IllegalArgumentException) { default.toolbarPosition }
        } ?: default.toolbarPosition
        val browserButton = prefs[KEY_BROWSER_BUTTON]?.let { name ->
            try { BrowserButtonStyle.valueOf(name) } catch (_: IllegalArgumentException) { default.browserButton }
        } ?: default.browserButton
        val bubbleSide = prefs[KEY_BROWSER_BUBBLE_SIDE]?.let { name ->
            try { BubbleSide.valueOf(name) } catch (_: IllegalArgumentException) { default.bubbleSide }
        } ?: default.bubbleSide
        val tabsView = prefs[KEY_BROWSER_TABS_VIEW]?.let { name ->
            try { TabsView.valueOf(name) } catch (_: IllegalArgumentException) { default.tabsView }
        } ?: default.tabsView

        return BrowserPrefs(
            openLinksWith = openLinksWith,
            siteBar = prefs[KEY_BROWSER_SITE_BAR] ?: default.siteBar,
            deadSiteRescue = prefs[KEY_BROWSER_DEAD_SITE_RESCUE] ?: default.deadSiteRescue,
            maxTabs = prefs[KEY_BROWSER_MAX_TABS] ?: default.maxTabs,
            restoreTabs = prefs[KEY_BROWSER_RESTORE_TABS] ?: default.restoreTabs,
            searchEngine = searchEngine,
            toolbarPosition = toolbarPosition,
            browserButton = browserButton,
            browserBubbleHidden = prefs[KEY_BROWSER_BUBBLE_HIDDEN] ?: default.browserBubbleHidden,
            bubbleSide = bubbleSide,
            bubbleY = (prefs[KEY_BROWSER_BUBBLE_Y] ?: default.bubbleY).coerceIn(0f, 1f),
            tabsView = tabsView,
            javascript = prefs[KEY_BROWSER_JAVASCRIPT] ?: default.javascript,
            forceDark = prefs[KEY_BROWSER_FORCE_DARK] ?: default.forceDark,
            desktopByDefault = prefs[KEY_BROWSER_DESKTOP_BY_DEFAULT] ?: default.desktopByDefault,
            textZoomFollowsApp = prefs[KEY_BROWSER_TEXT_ZOOM_FOLLOWS_APP] ?: default.textZoomFollowsApp,
            forceZoom = prefs[KEY_BROWSER_FORCE_ZOOM] ?: default.forceZoom,
            fullscreenOrientation = resolveFullscreenOrientation(
                prefs[KEY_BROWSER_FULLSCREEN_ORIENTATION],
                prefs[KEY_BROWSER_VIDEO_AUTO_ROTATE],
            ),
            pictureInPicture = prefs[KEY_BROWSER_PICTURE_IN_PICTURE] ?: default.pictureInPicture,
            autoPip = prefs[KEY_BROWSER_AUTO_PIP] ?: default.autoPip,
            keepScreenOn = prefs[KEY_BROWSER_KEEP_SCREEN_ON] ?: default.keepScreenOn,
            backgroundPlayback = prefs[KEY_BROWSER_BACKGROUND_PLAYBACK] ?: default.backgroundPlayback,
            autoplay = prefs[KEY_BROWSER_AUTOPLAY]?.let { name -> AutoplayMode.entries.firstOrNull { it.name == name } } ?: default.autoplay,
            doubleTapSeek = prefs[KEY_BROWSER_DOUBLE_TAP_SEEK] ?: default.doubleTapSeek,
            defaultSpeed = prefs[KEY_BROWSER_DEFAULT_SPEED] ?: default.defaultSpeed,
            videoDownloadButton = prefs[KEY_BROWSER_VIDEO_DOWNLOAD_BUTTON] ?: default.videoDownloadButton,
        )
    }

    private fun writeBrowserPrefs(prefs: MutablePreferences, p: BrowserPrefs) {
        prefs[KEY_BROWSER_OPEN_LINKS_WITH] = p.openLinksWith.name
        prefs[KEY_BROWSER_SITE_BAR] = p.siteBar
        prefs[KEY_BROWSER_DEAD_SITE_RESCUE] = p.deadSiteRescue
        prefs[KEY_BROWSER_MAX_TABS] = p.maxTabs
        prefs[KEY_BROWSER_RESTORE_TABS] = p.restoreTabs
        prefs[KEY_BROWSER_SEARCH_ENGINE] = p.searchEngine.name
        prefs[KEY_BROWSER_TOOLBAR_POSITION] = p.toolbarPosition.name
        prefs[KEY_BROWSER_BUTTON] = p.browserButton.name
        prefs[KEY_BROWSER_BUBBLE_HIDDEN] = p.browserBubbleHidden
        prefs[KEY_BROWSER_BUBBLE_SIDE] = p.bubbleSide.name
        prefs[KEY_BROWSER_BUBBLE_Y] = p.bubbleY
        prefs[KEY_BROWSER_TABS_VIEW] = p.tabsView.name
        prefs[KEY_BROWSER_JAVASCRIPT] = p.javascript
        prefs[KEY_BROWSER_FORCE_DARK] = p.forceDark
        prefs[KEY_BROWSER_DESKTOP_BY_DEFAULT] = p.desktopByDefault
        prefs[KEY_BROWSER_TEXT_ZOOM_FOLLOWS_APP] = p.textZoomFollowsApp
        prefs[KEY_BROWSER_FORCE_ZOOM] = p.forceZoom
        prefs[KEY_BROWSER_FULLSCREEN_ORIENTATION] = p.fullscreenOrientation.name
        prefs[KEY_BROWSER_PICTURE_IN_PICTURE] = p.pictureInPicture
        prefs[KEY_BROWSER_AUTO_PIP] = p.autoPip
        prefs[KEY_BROWSER_KEEP_SCREEN_ON] = p.keepScreenOn
        prefs[KEY_BROWSER_BACKGROUND_PLAYBACK] = p.backgroundPlayback
        prefs[KEY_BROWSER_AUTOPLAY] = p.autoplay.name
        prefs[KEY_BROWSER_DOUBLE_TAP_SEEK] = p.doubleTapSeek
        prefs[KEY_BROWSER_DEFAULT_SPEED] = p.defaultSpeed
        prefs[KEY_BROWSER_VIDEO_DOWNLOAD_BUTTON] = p.videoDownloadButton
    }

    override val browserPrefs: Flow<BrowserPrefs> = dataStore.data.map { prefs ->
        readBrowserPrefs(prefs)
    }.distinctUntilChanged()

    override suspend fun updateBrowser(f: (BrowserPrefs) -> BrowserPrefs) {
        dataStore.edit { prefs ->
            val current = readBrowserPrefs(prefs)
            val updated = f(current)
            writeBrowserPrefs(prefs, updated)
        }
    }

    // --- BlockingPrefs ---
    private fun readBlockingPrefs(prefs: Preferences): BlockingPrefs {
        val default = BlockingPrefs()
        return BlockingPrefs(
            adblock = prefs[KEY_BLOCKING_ADBLOCK] ?: default.adblock,
            enabledLists = prefs[KEY_BLOCKING_ENABLED_LISTS] ?: default.enabledLists,
            customRules = prefs[KEY_BLOCKING_CUSTOM_RULES] ?: default.customRules,
            allowlist = prefs[KEY_BLOCKING_ALLOWLIST] ?: default.allowlist,
            showBlockedCount = prefs[KEY_BLOCKING_SHOW_BLOCKED_COUNT] ?: default.showBlockedCount,
            elementHiding = prefs[KEY_BLOCKING_ELEMENT_HIDING] ?: default.elementHiding,
            scriptlets = prefs[KEY_BLOCKING_SCRIPTLETS] ?: default.scriptlets,
            autoUpdateLists = prefs[KEY_BLOCKING_AUTO_UPDATE_LISTS] ?: default.autoUpdateLists,
        )
    }

    private fun writeBlockingPrefs(prefs: MutablePreferences, p: BlockingPrefs) {
        prefs[KEY_BLOCKING_ADBLOCK] = p.adblock
        prefs[KEY_BLOCKING_ENABLED_LISTS] = p.enabledLists
        prefs[KEY_BLOCKING_CUSTOM_RULES] = p.customRules
        prefs[KEY_BLOCKING_ALLOWLIST] = p.allowlist
        prefs[KEY_BLOCKING_SHOW_BLOCKED_COUNT] = p.showBlockedCount
        prefs[KEY_BLOCKING_ELEMENT_HIDING] = p.elementHiding
        prefs[KEY_BLOCKING_SCRIPTLETS] = p.scriptlets
        prefs[KEY_BLOCKING_AUTO_UPDATE_LISTS] = p.autoUpdateLists
    }

    override val blockingPrefs: Flow<BlockingPrefs> = dataStore.data.map { prefs ->
        readBlockingPrefs(prefs)
    }.distinctUntilChanged()

    override suspend fun updateBlocking(f: (BlockingPrefs) -> BlockingPrefs) {
        dataStore.edit { prefs ->
            val current = readBlockingPrefs(prefs)
            val updated = f(current)
            writeBlockingPrefs(prefs, updated)
            val hadCookie = current.enabledLists.contains(FilterListCatalog.COOKIE_LIST_ID)
            val hasCookie = updated.enabledLists.contains(FilterListCatalog.COOKIE_LIST_ID)
            if (hasCookie != hadCookie) {
                prefs[KEY_PRIVACY_HIDE_COOKIE_BANNERS] = hasCookie
            }
        }
    }

    // --- ProtectionPrefs ---
    private fun readProtectionPrefs(prefs: Preferences): ProtectionPrefs {
        val default = ProtectionPrefs()
        return ProtectionPrefs(
            unsafeWarning = prefs[KEY_PROTECTION_UNSAFE_WARNING] ?: default.unsafeWarning,
            potentiallyUnsafeWarning = prefs[KEY_PROTECTION_POTENTIALLY_UNSAFE_WARNING] ?: default.potentiallyUnsafeWarning,
            popupBlocker = prefs[KEY_PROTECTION_POPUP_BLOCKER] ?: default.popupBlocker,
            redirectGuard = prefs[KEY_PROTECTION_REDIRECT_GUARD] ?: default.redirectGuard,
            blockAppLinks = prefs[KEY_PROTECTION_BLOCK_APP_LINKS] ?: default.blockAppLinks,
            askBeforeDownload = prefs[KEY_PROTECTION_ASK_BEFORE_DOWNLOAD] ?: default.askBeforeDownload,
            warnRiskyDownloads = prefs[KEY_PROTECTION_WARN_RISKY_DOWNLOADS] ?: default.warnRiskyDownloads,
            safeBrowsing = prefs[KEY_PROTECTION_SAFE_BROWSING] ?: default.safeBrowsing,
        )
    }

    private fun writeProtectionPrefs(prefs: MutablePreferences, p: ProtectionPrefs) {
        prefs[KEY_PROTECTION_UNSAFE_WARNING] = p.unsafeWarning
        prefs[KEY_PROTECTION_POTENTIALLY_UNSAFE_WARNING] = p.potentiallyUnsafeWarning
        prefs[KEY_PROTECTION_POPUP_BLOCKER] = p.popupBlocker
        prefs[KEY_PROTECTION_REDIRECT_GUARD] = p.redirectGuard
        prefs[KEY_PROTECTION_BLOCK_APP_LINKS] = p.blockAppLinks
        prefs[KEY_PROTECTION_ASK_BEFORE_DOWNLOAD] = p.askBeforeDownload
        prefs[KEY_PROTECTION_WARN_RISKY_DOWNLOADS] = p.warnRiskyDownloads
        prefs[KEY_PROTECTION_SAFE_BROWSING] = p.safeBrowsing
    }

    override val protectionPrefs: Flow<ProtectionPrefs> = dataStore.data.map { prefs ->
        readProtectionPrefs(prefs)
    }.distinctUntilChanged()

    override suspend fun updateProtection(f: (ProtectionPrefs) -> ProtectionPrefs) {
        dataStore.edit { prefs ->
            val current = readProtectionPrefs(prefs)
            val updated = f(current)
            writeProtectionPrefs(prefs, updated)
        }
    }

    // --- PrivacyPrefs ---
    private fun readPrivacyPrefs(prefs: Preferences): PrivacyPrefs {
        val default = PrivacyPrefs()
        return PrivacyPrefs(
            blockThirdPartyCookies = prefs[KEY_PRIVACY_BLOCK_THIRD_PARTY_COOKIES] ?: default.blockThirdPartyCookies,
            hideCookieBanners = prefs[KEY_PRIVACY_HIDE_COOKIE_BANNERS] ?: default.hideCookieBanners,
            clearDataOnExit = prefs[KEY_PRIVACY_CLEAR_DATA_ON_EXIT] ?: default.clearDataOnExit,
            privateTabsByDefault = prefs[KEY_PRIVACY_PRIVATE_TABS_BY_DEFAULT] ?: default.privateTabsByDefault,
            sendGpc = prefs[KEY_PRIVACY_SEND_GPC] ?: default.sendGpc,
        )
    }

    private fun writePrivacyPrefs(prefs: MutablePreferences, p: PrivacyPrefs) {
        prefs[KEY_PRIVACY_BLOCK_THIRD_PARTY_COOKIES] = p.blockThirdPartyCookies
        prefs[KEY_PRIVACY_HIDE_COOKIE_BANNERS] = p.hideCookieBanners
        prefs[KEY_PRIVACY_CLEAR_DATA_ON_EXIT] = p.clearDataOnExit
        prefs[KEY_PRIVACY_PRIVATE_TABS_BY_DEFAULT] = p.privateTabsByDefault
        prefs[KEY_PRIVACY_SEND_GPC] = p.sendGpc
    }

    override val privacyPrefs: Flow<PrivacyPrefs> = dataStore.data.map { prefs ->
        readPrivacyPrefs(prefs)
    }.distinctUntilChanged()

    override suspend fun updatePrivacy(f: (PrivacyPrefs) -> PrivacyPrefs) {
        dataStore.edit { prefs ->
            val current = readPrivacyPrefs(prefs)
            val updated = f(current)
            writePrivacyPrefs(prefs, updated)
            if (updated.hideCookieBanners != current.hideCookieBanners) {
                val currentLists = prefs[KEY_BLOCKING_ENABLED_LISTS] ?: FilterListCatalog.balancedIds
                val newLists = if (updated.hideCookieBanners) {
                    currentLists + FilterListCatalog.COOKIE_LIST_ID
                } else {
                    currentLists - FilterListCatalog.COOKIE_LIST_ID
                }
                prefs[KEY_BLOCKING_ENABLED_LISTS] = newLists
            }
        }
    }

    // --- NotificationPrefs ---
    private fun readNotificationPrefs(prefs: Preferences): NotificationPrefs {
        val default = NotificationPrefs()
        val interval = prefs[KEY_NOTIFICATIONS_INTERVAL]?.let { name ->
            try { UpdateInterval.valueOf(name) } catch (_: IllegalArgumentException) { default.interval }
        } ?: default.interval

        return NotificationPrefs(
            useTracker = prefs[KEY_NOTIFICATIONS_USE_TRACKER] ?: default.useTracker,
            enabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: default.enabled,
            notifyFollowed = prefs[KEY_NOTIFICATIONS_NOTIFY_FOLLOWED] ?: default.notifyFollowed,
            watchSaved = prefs[KEY_NOTIFICATIONS_WATCH_SAVED] ?: default.watchSaved,
            notifyNewPosts = prefs[KEY_NOTIFICATIONS_NOTIFY_NEW_POSTS] ?: default.notifyNewPosts,
            interval = interval,
        )
    }

    private fun writeNotificationPrefs(prefs: MutablePreferences, p: NotificationPrefs) {
        prefs[KEY_NOTIFICATIONS_USE_TRACKER] = p.useTracker
        prefs[KEY_NOTIFICATIONS_ENABLED] = p.enabled
        prefs[KEY_NOTIFICATIONS_NOTIFY_FOLLOWED] = p.notifyFollowed
        prefs[KEY_NOTIFICATIONS_WATCH_SAVED] = p.watchSaved
        prefs[KEY_NOTIFICATIONS_NOTIFY_NEW_POSTS] = p.notifyNewPosts
        prefs[KEY_NOTIFICATIONS_INTERVAL] = p.interval.name
    }

    override val notificationPrefs: Flow<NotificationPrefs> = dataStore.data.map { prefs ->
        readNotificationPrefs(prefs)
    }.distinctUntilChanged()

    override suspend fun updateNotifications(f: (NotificationPrefs) -> NotificationPrefs) {
        dataStore.edit { prefs ->
            val current = readNotificationPrefs(prefs)
            val updated = f(current)
            writeNotificationPrefs(prefs, updated)
        }
    }

    // --- DataUsagePrefs ---
    private fun readDataUsagePrefs(prefs: Preferences): DataUsagePrefs {
        val default = DataUsagePrefs()
        val appDataWifiOnly = prefs[KEY_DATA_USAGE_APP_DATA_WIFI_ONLY]
            ?: prefs[KEY_NOTIFICATIONS_WIFI_ONLY]
            ?: default.appDataWifiOnly
        return DataUsagePrefs(
            downloadWifiOnly = prefs[KEY_DATA_USAGE_DOWNLOAD_WIFI_ONLY] ?: default.downloadWifiOnly,
            browseWifiOnly = prefs[KEY_DATA_USAGE_BROWSE_WIFI_ONLY] ?: default.browseWifiOnly,
            appDataWifiOnly = appDataWifiOnly,
        )
    }

    private fun writeDataUsagePrefs(prefs: MutablePreferences, p: DataUsagePrefs) {
        prefs[KEY_DATA_USAGE_DOWNLOAD_WIFI_ONLY] = p.downloadWifiOnly
        prefs[KEY_DATA_USAGE_BROWSE_WIFI_ONLY] = p.browseWifiOnly
        prefs[KEY_DATA_USAGE_APP_DATA_WIFI_ONLY] = p.appDataWifiOnly
    }

    override val dataUsagePrefs: Flow<DataUsagePrefs> = dataStore.data.map { prefs ->
        readDataUsagePrefs(prefs)
    }.distinctUntilChanged()

    override suspend fun updateDataUsage(f: (DataUsagePrefs) -> DataUsagePrefs) {
        dataStore.edit { prefs ->
            val current = readDataUsagePrefs(prefs)
            val updated = f(current)
            writeDataUsagePrefs(prefs, updated)
        }
    }

    // --- UpdatePrefs ---
    private fun readUpdatePrefs(prefs: Preferences): UpdatePrefs {
        val default = UpdatePrefs()
        return UpdatePrefs(
            autoCheck = prefs[KEY_UPDATE_AUTO_CHECK] ?: default.autoCheck,
            lastCheckMs = prefs[KEY_UPDATE_LAST_CHECK_MS] ?: default.lastCheckMs,
            laterUntilMs = prefs[KEY_UPDATE_LATER_UNTIL_MS] ?: default.laterUntilMs,
            skippedVersion = prefs[KEY_UPDATE_SKIPPED_VERSION] ?: default.skippedVersion,
            installExplained = prefs[KEY_UPDATE_INSTALL_EXPLAINED] ?: default.installExplained,
            knownAvailable = prefs[KEY_UPDATE_KNOWN_AVAILABLE] ?: default.knownAvailable,
        )
    }

    private fun writeUpdatePrefs(prefs: MutablePreferences, p: UpdatePrefs) {
        prefs[KEY_UPDATE_AUTO_CHECK] = p.autoCheck
        prefs[KEY_UPDATE_LAST_CHECK_MS] = p.lastCheckMs
        prefs[KEY_UPDATE_LATER_UNTIL_MS] = p.laterUntilMs
        if (p.skippedVersion != null) {
            prefs[KEY_UPDATE_SKIPPED_VERSION] = p.skippedVersion
        } else {
            prefs.remove(KEY_UPDATE_SKIPPED_VERSION)
        }
        prefs[KEY_UPDATE_INSTALL_EXPLAINED] = p.installExplained
        if (p.knownAvailable != null) {
            prefs[KEY_UPDATE_KNOWN_AVAILABLE] = p.knownAvailable
        } else {
            prefs.remove(KEY_UPDATE_KNOWN_AVAILABLE)
        }
    }

    override val updatePrefs: Flow<UpdatePrefs> = dataStore.data.map { prefs ->
        readUpdatePrefs(prefs)
    }.distinctUntilChanged()

    override suspend fun updateUpdatePrefs(f: (UpdatePrefs) -> UpdatePrefs) {
        dataStore.edit { prefs ->
            val current = readUpdatePrefs(prefs)
            val updated = f(current)
            writeUpdatePrefs(prefs, updated)
        }
    }

    // --- DownloadPrefs ---
    private fun readDownloadPrefs(prefs: Preferences): DownloadPrefs {
        val default = DownloadPrefs()
        return DownloadPrefs(
            folderUri = prefs[KEY_DOWNLOAD_FOLDER_URI] ?: default.folderUri,
            folderFallbackNotified = prefs[KEY_DOWNLOAD_FOLDER_FALLBACK_NOTIFIED] ?: default.folderFallbackNotified,
        )
    }

    private fun writeDownloadPrefs(prefs: MutablePreferences, p: DownloadPrefs) {
        if (p.folderUri != null) {
            prefs[KEY_DOWNLOAD_FOLDER_URI] = p.folderUri
        } else {
            prefs.remove(KEY_DOWNLOAD_FOLDER_URI)
        }
        prefs[KEY_DOWNLOAD_FOLDER_FALLBACK_NOTIFIED] = p.folderFallbackNotified
    }

    override val downloadPrefs: Flow<DownloadPrefs> = dataStore.data.map { prefs ->
        readDownloadPrefs(prefs)
    }.distinctUntilChanged()

    override suspend fun updateDownloadPrefs(f: (DownloadPrefs) -> DownloadPrefs) {
        dataStore.edit { prefs ->
            val current = readDownloadPrefs(prefs)
            val updated = f(current)
            writeDownloadPrefs(prefs, updated)
        }
    }

    // --- CheckPrefs ---
    private fun readCheckPrefs(prefs: Preferences): CheckPrefs {
        val default = CheckPrefs()
        return CheckPrefs(
            virusTotalOn = prefs[KEY_CHECK_VT_ON] ?: default.virusTotalOn,
            virusTotalKey = prefs[KEY_CHECK_VT_KEY] ?: default.virusTotalKey,
            hybridOn = prefs[KEY_CHECK_HA_ON] ?: default.hybridOn,
            hybridKey = prefs[KEY_CHECK_HA_KEY] ?: default.hybridKey,
            holdUntilChecked = prefs[KEY_CHECK_HOLD] ?: default.holdUntilChecked,
            autoCheckRisky = prefs[KEY_CHECK_AUTO_RISKY] ?: default.autoCheckRisky,
            keyPromptShown = prefs[KEY_CHECK_KEY_PROMPT_SHOWN] ?: default.keyPromptShown,
            explainShown = prefs[KEY_CHECK_EXPLAIN_SHOWN] ?: default.explainShown,
            fmhyDownloadStatus = prefs[KEY_CHECK_FMHY_DOWNLOAD_STATUS] ?: default.fmhyDownloadStatus,
        )
    }

    private fun writeCheckPrefs(prefs: MutablePreferences, p: CheckPrefs) {
        prefs[KEY_CHECK_VT_ON] = p.virusTotalOn
        prefs[KEY_CHECK_VT_KEY] = p.virusTotalKey.trim()
        prefs[KEY_CHECK_HA_ON] = p.hybridOn
        prefs[KEY_CHECK_HA_KEY] = p.hybridKey.trim()
        prefs[KEY_CHECK_HOLD] = p.holdUntilChecked
        prefs[KEY_CHECK_AUTO_RISKY] = p.autoCheckRisky
        prefs[KEY_CHECK_KEY_PROMPT_SHOWN] = p.keyPromptShown
        prefs[KEY_CHECK_EXPLAIN_SHOWN] = p.explainShown
        prefs[KEY_CHECK_FMHY_DOWNLOAD_STATUS] = p.fmhyDownloadStatus
    }

    override val checkPrefs: Flow<CheckPrefs> = dataStore.data.map { prefs ->
        readCheckPrefs(prefs)
    }.distinctUntilChanged()

    override suspend fun updateCheckPrefs(f: (CheckPrefs) -> CheckPrefs) {
        dataStore.edit { prefs ->
            val current = readCheckPrefs(prefs)
            val updated = f(current)
            writeCheckPrefs(prefs, updated)
        }
    }

    override val followNotifyPromptShown: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FOLLOW_NOTIFY_PROMPT_SHOWN] ?: false
    }.distinctUntilChanged()

    override suspend fun setFollowNotifyPromptShown(shown: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_FOLLOW_NOTIFY_PROMPT_SHOWN] = shown
        }
    }

    override val gestureHintShown: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_GESTURE_HINT_SHOWN] ?: false
    }.distinctUntilChanged()

    override suspend fun setGestureHintShown(shown: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_GESTURE_HINT_SHOWN] = shown
        }
    }

    override val reportOfferCrashes: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_REPORT_OFFER_CRASHES] ?: true
    }.distinctUntilChanged()

    override suspend fun setReportOfferCrashes(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_REPORT_OFFER_CRASHES] = enabled
        }
    }

    // --- DataPrefs ---
    private fun readDataPrefs(prefs: Preferences): DataPrefs {
        val default = DataPrefs()
        return DataPrefs(
            syncOnLaunch = prefs[KEY_DATA_SYNC_ON_LAUNCH] ?: default.syncOnLaunch,
            keepHistory = prefs[KEY_DATA_KEEP_HISTORY] ?: default.keepHistory,
        )
    }

    private fun writeDataPrefs(prefs: MutablePreferences, p: DataPrefs) {
        prefs[KEY_DATA_SYNC_ON_LAUNCH] = p.syncOnLaunch
        prefs[KEY_DATA_KEEP_HISTORY] = p.keepHistory
    }

    override val dataPrefs: Flow<DataPrefs> = dataStore.data.map { prefs ->
        readDataPrefs(prefs)
    }.distinctUntilChanged()

    override suspend fun updateData(f: (DataPrefs) -> DataPrefs) {
        dataStore.edit { prefs ->
            val current = readDataPrefs(prefs)
            val updated = f(current)
            writeDataPrefs(prefs, updated)
        }
    }

    // --- Onboarding ---
    override val onboardingVersion: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_VERSION] ?: 0
    }.distinctUntilChanged()

    override suspend fun setOnboardingVersion(v: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_VERSION] = v
        }
    }
}
