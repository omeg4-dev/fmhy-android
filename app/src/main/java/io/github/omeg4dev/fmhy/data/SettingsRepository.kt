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
        private val KEY_APP_ICON = stringPreferencesKey("app_icon")
        private val KEY_ONBOARDING_VERSION = intPreferencesKey("onboarding_version")

        // Home
        private val KEY_HOME_PERSONAL_HOME = booleanPreferencesKey("home_personal_home")
        private val KEY_HOME_NEW_BADGE_DAYS = intPreferencesKey("home_new_badge_days")

        // Browser
        private val KEY_BROWSER_OPEN_LINKS_WITH = stringPreferencesKey("browser_open_links_with")
        private val KEY_BROWSER_SITE_BAR = booleanPreferencesKey("browser_site_bar")
        private val KEY_BROWSER_DEAD_SITE_RESCUE = booleanPreferencesKey("browser_dead_site_rescue")
        private val KEY_BROWSER_MAX_TABS = intPreferencesKey("browser_max_tabs")
        private val KEY_BROWSER_RESTORE_TABS = booleanPreferencesKey("browser_restore_tabs")
        private val KEY_BROWSER_SEARCH_ENGINE = stringPreferencesKey("browser_search_engine")
        private val KEY_BROWSER_TOOLBAR_POSITION = stringPreferencesKey("browser_toolbar_position")
        private val KEY_BROWSER_JAVASCRIPT = booleanPreferencesKey("browser_javascript")
        private val KEY_BROWSER_FORCE_DARK = booleanPreferencesKey("browser_force_dark")
        private val KEY_BROWSER_DESKTOP_BY_DEFAULT = booleanPreferencesKey("browser_desktop_by_default")
        private val KEY_BROWSER_TEXT_ZOOM_FOLLOWS_APP = booleanPreferencesKey("browser_text_zoom_follows_app")
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

        // Data
        private val KEY_DATA_SYNC_ON_LAUNCH = booleanPreferencesKey("data_sync_on_launch")
        private val KEY_DATA_KEEP_HISTORY = booleanPreferencesKey("data_keep_history")
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
        )
    }

    private fun writeHomePrefs(prefs: MutablePreferences, p: HomePrefs) {
        prefs[KEY_HOME_PERSONAL_HOME] = p.personalHome
        prefs[KEY_HOME_NEW_BADGE_DAYS] = p.newBadgeDays
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

        return BrowserPrefs(
            openLinksWith = openLinksWith,
            siteBar = prefs[KEY_BROWSER_SITE_BAR] ?: default.siteBar,
            deadSiteRescue = prefs[KEY_BROWSER_DEAD_SITE_RESCUE] ?: default.deadSiteRescue,
            maxTabs = prefs[KEY_BROWSER_MAX_TABS] ?: default.maxTabs,
            restoreTabs = prefs[KEY_BROWSER_RESTORE_TABS] ?: default.restoreTabs,
            searchEngine = searchEngine,
            toolbarPosition = toolbarPosition,
            javascript = prefs[KEY_BROWSER_JAVASCRIPT] ?: default.javascript,
            forceDark = prefs[KEY_BROWSER_FORCE_DARK] ?: default.forceDark,
            desktopByDefault = prefs[KEY_BROWSER_DESKTOP_BY_DEFAULT] ?: default.desktopByDefault,
            textZoomFollowsApp = prefs[KEY_BROWSER_TEXT_ZOOM_FOLLOWS_APP] ?: default.textZoomFollowsApp,
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
        prefs[KEY_BROWSER_JAVASCRIPT] = p.javascript
        prefs[KEY_BROWSER_FORCE_DARK] = p.forceDark
        prefs[KEY_BROWSER_DESKTOP_BY_DEFAULT] = p.desktopByDefault
        prefs[KEY_BROWSER_TEXT_ZOOM_FOLLOWS_APP] = p.textZoomFollowsApp
        prefs[KEY_BROWSER_FULLSCREEN_ORIENTATION] = p.fullscreenOrientation.name
        prefs[KEY_BROWSER_PICTURE_IN_PICTURE] = p.pictureInPicture
        prefs[KEY_BROWSER_AUTO_PIP] = p.autoPip
        prefs[KEY_BROWSER_KEEP_SCREEN_ON] = p.keepScreenOn
        prefs[KEY_BROWSER_BACKGROUND_PLAYBACK] = p.backgroundPlayback
        prefs[KEY_BROWSER_AUTOPLAY] = p.autoplay.name
        prefs[KEY_BROWSER_DOUBLE_TAP_SEEK] = p.doubleTapSeek
        prefs[KEY_BROWSER_DEFAULT_SPEED] = p.defaultSpeed
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
            wifiOnly = prefs[KEY_NOTIFICATIONS_WIFI_ONLY] ?: default.wifiOnly,
        )
    }

    private fun writeNotificationPrefs(prefs: MutablePreferences, p: NotificationPrefs) {
        prefs[KEY_NOTIFICATIONS_USE_TRACKER] = p.useTracker
        prefs[KEY_NOTIFICATIONS_ENABLED] = p.enabled
        prefs[KEY_NOTIFICATIONS_NOTIFY_FOLLOWED] = p.notifyFollowed
        prefs[KEY_NOTIFICATIONS_WATCH_SAVED] = p.watchSaved
        prefs[KEY_NOTIFICATIONS_NOTIFY_NEW_POSTS] = p.notifyNewPosts
        prefs[KEY_NOTIFICATIONS_INTERVAL] = p.interval.name
        prefs[KEY_NOTIFICATIONS_WIFI_ONLY] = p.wifiOnly
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
