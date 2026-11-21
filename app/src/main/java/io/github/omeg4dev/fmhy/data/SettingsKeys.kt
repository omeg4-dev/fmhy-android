package io.github.omeg4dev.fmhy.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

internal object SettingsKeys {
    val KEY_LAST_VISIT_TS = longPreferencesKey("last_visit_ts")
    val KEY_OPEN_EXTERNAL_BROWSER = booleanPreferencesKey("open_in_external_browser")
    val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    val KEY_COLOR_SOURCE = stringPreferencesKey("color_source")
    val KEY_SECTION_LAYOUT = stringPreferencesKey("section_layout")
    val KEY_PURE_BLACK = booleanPreferencesKey("pure_black")
    val KEY_ACCENT_SEED = intPreferencesKey("accent_seed")
    val KEY_TEXT_SCALE = floatPreferencesKey("text_scale")
    val KEY_SECTION_ORDER = stringPreferencesKey("section_order")
    val KEY_SHOW_RANDOM_PICK = booleanPreferencesKey("show_random_pick")
    val KEY_STARRED_ONLY = booleanPreferencesKey("starred_only")
    val KEY_SHOW_INDEXES = booleanPreferencesKey("show_indexes")
    val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
    val KEY_WEBSITE_STYLE = booleanPreferencesKey("website_style")
    val KEY_APP_ICON = stringPreferencesKey("app_icon")
    val KEY_BOTTOM_BAR_STYLE = stringPreferencesKey("appearance_bottom_bar_style")
    val KEY_ONBOARDING_VERSION = intPreferencesKey("onboarding_version")

    // Home
    val KEY_HOME_BLOCKS = stringPreferencesKey("home_blocks")
    val KEY_HOME_PERSONAL_HOME = booleanPreferencesKey("home_personal_home")
    val KEY_HOME_NEW_BADGE_DAYS = intPreferencesKey("home_new_badge_days")
    val KEY_HOME_SEARCH_EXACT = booleanPreferencesKey("home_search_exact")
    val KEY_HOME_SWIPE_TO_BOOKMARK = booleanPreferencesKey("home_swipe_to_bookmark")
    val KEY_HOME_SWAP_TAP_HOLD = booleanPreferencesKey("home_swap_tap_hold")
    val KEY_HOME_SEARCH_TAP_TARGET = stringPreferencesKey("home_search_tap_target")
    val KEY_HOME_SWIPE_BETWEEN_SCREENS = booleanPreferencesKey("home_swipe_between_screens")
    val KEY_HOME_WEBSITE_CATEGORY_NAMES = booleanPreferencesKey("home_website_category_names")

    // Browser
    val KEY_BROWSER_OPEN_LINKS_WITH = stringPreferencesKey("browser_open_links_with")
    val KEY_BROWSER_SITE_BAR = booleanPreferencesKey("browser_site_bar")
    val KEY_BROWSER_DEAD_SITE_RESCUE = booleanPreferencesKey("browser_dead_site_rescue")
    val KEY_BROWSER_MAX_TABS = intPreferencesKey("browser_max_tabs")
    val KEY_BROWSER_RESTORE_TABS = booleanPreferencesKey("browser_restore_tabs")
    val KEY_BROWSER_SEARCH_ENGINE = stringPreferencesKey("browser_search_engine")
    val KEY_BROWSER_TOOLBAR_POSITION = stringPreferencesKey("browser_toolbar_position")
    val KEY_BROWSER_BUTTON = stringPreferencesKey("browser_button_style")
    val KEY_BROWSER_BUBBLE_HIDDEN = booleanPreferencesKey("browser_bubble_hidden")
    val KEY_BROWSER_BUBBLE_SIDE = stringPreferencesKey("browser_bubble_side")
    val KEY_BROWSER_BUBBLE_Y = floatPreferencesKey("browser_bubble_y")
    val KEY_BROWSER_TABS_VIEW = stringPreferencesKey("browser_tabs_view")
    val KEY_BROWSER_JAVASCRIPT = booleanPreferencesKey("browser_javascript")
    val KEY_BROWSER_FORCE_DARK = booleanPreferencesKey("browser_force_dark")
    val KEY_BROWSER_DESKTOP_BY_DEFAULT = booleanPreferencesKey("browser_desktop_by_default")
    val KEY_BROWSER_TEXT_ZOOM_FOLLOWS_APP = booleanPreferencesKey("browser_text_zoom_follows_app")
    val KEY_BROWSER_FORCE_ZOOM = booleanPreferencesKey("browser_force_zoom")
    val KEY_BROWSER_FULLSCREEN_ORIENTATION = stringPreferencesKey("browser_fullscreen_orientation")
    /** Pre-0.5.2 boolean; only read, as the fallback for [KEY_BROWSER_FULLSCREEN_ORIENTATION]. */
    val KEY_BROWSER_VIDEO_AUTO_ROTATE = booleanPreferencesKey("browser_video_auto_rotate")
    val KEY_BROWSER_PICTURE_IN_PICTURE = booleanPreferencesKey("browser_picture_in_picture")
    val KEY_BROWSER_AUTO_PIP = booleanPreferencesKey("browser_auto_pip")
    val KEY_BROWSER_DEFAULT_SPEED = floatPreferencesKey("browser_default_speed")
    val KEY_BROWSER_DOUBLE_TAP_SEEK = booleanPreferencesKey("browser_double_tap_seek")
    val KEY_BROWSER_AUTOPLAY = stringPreferencesKey("browser_autoplay")
    val KEY_BROWSER_BACKGROUND_PLAYBACK = booleanPreferencesKey("browser_background_playback")
    val KEY_BROWSER_KEEP_SCREEN_ON = booleanPreferencesKey("browser_keep_screen_on")
    val KEY_BROWSER_VIDEO_DOWNLOAD_BUTTON = booleanPreferencesKey("browser_video_download_button")

    // Blocking
    val KEY_BLOCKING_ADBLOCK = booleanPreferencesKey("blocking_adblock")
    val KEY_BLOCKING_ENABLED_LISTS = stringSetPreferencesKey("blocking_enabled_lists")
    val KEY_BLOCKING_CUSTOM_RULES = stringPreferencesKey("blocking_custom_rules")
    val KEY_BLOCKING_ALLOWLIST = stringSetPreferencesKey("blocking_allowlist")
    val KEY_BLOCKING_SHOW_BLOCKED_COUNT = booleanPreferencesKey("blocking_show_blocked_count")
    val KEY_BLOCKING_ELEMENT_HIDING = booleanPreferencesKey("blocking_element_hiding")
    val KEY_BLOCKING_SCRIPTLETS = booleanPreferencesKey("blocking_scriptlets")
    val KEY_BLOCKING_AUTO_UPDATE_LISTS = booleanPreferencesKey("blocking_auto_update_lists")

    // Protection
    val KEY_PROTECTION_UNSAFE_WARNING = booleanPreferencesKey("protection_unsafe_warning")
    val KEY_PROTECTION_CRITICAL_NOTE_WARNING = booleanPreferencesKey("protection_critical_note_warning")
    val KEY_PROTECTION_POTENTIALLY_UNSAFE_WARNING = booleanPreferencesKey("protection_potentially_unsafe_warning")
    val KEY_PROTECTION_POPUP_BLOCKER = booleanPreferencesKey("protection_popup_blocker")
    val KEY_PROTECTION_REDIRECT_GUARD = booleanPreferencesKey("protection_redirect_guard")
    val KEY_PROTECTION_BLOCK_APP_LINKS = booleanPreferencesKey("protection_block_app_links")
    val KEY_PROTECTION_ASK_BEFORE_DOWNLOAD = booleanPreferencesKey("protection_ask_before_download")
    val KEY_PROTECTION_WARN_RISKY_DOWNLOADS = booleanPreferencesKey("protection_warn_risky_downloads")
    val KEY_PROTECTION_SAFE_BROWSING = booleanPreferencesKey("protection_safe_browsing")
    val KEY_PROTECTION_SHOW_VPN_STATE = booleanPreferencesKey("protection_show_vpn_state")
    val KEY_PROTECTION_SHOW_VPN_HOME_ICON = booleanPreferencesKey("protection_show_vpn_home_icon")
    val KEY_PROTECTION_SHOW_IP_COUNTRY = booleanPreferencesKey("protection_show_ip_country")
    val KEY_PROTECTION_BLOCK_WITHOUT_VPN = booleanPreferencesKey("protection_block_without_vpn")

    // Privacy
    val KEY_PRIVACY_BLOCK_THIRD_PARTY_COOKIES = booleanPreferencesKey("privacy_block_third_party_cookies")
    val KEY_PRIVACY_HIDE_COOKIE_BANNERS = booleanPreferencesKey("privacy_hide_cookie_banners")
    val KEY_PRIVACY_CLEAR_DATA_ON_EXIT = booleanPreferencesKey("privacy_clear_data_on_exit")
    val KEY_PRIVACY_PRIVATE_TABS_BY_DEFAULT = booleanPreferencesKey("privacy_private_tabs_by_default")
    val KEY_PRIVACY_SEND_GPC = booleanPreferencesKey("privacy_send_gpc")

    // Notifications
    val KEY_NOTIFICATIONS_USE_TRACKER = booleanPreferencesKey("notifications_use_tracker")
    val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val KEY_NOTIFICATIONS_NOTIFY_FOLLOWED = booleanPreferencesKey("notifications_notify_followed")
    val KEY_NOTIFICATIONS_WATCH_SAVED = booleanPreferencesKey("notifications_watch_saved")
    val KEY_NOTIFICATIONS_NOTIFY_NEW_POSTS = booleanPreferencesKey("notifications_notify_new_posts")
    val KEY_NOTIFICATIONS_INTERVAL = stringPreferencesKey("notifications_interval")
    val KEY_NOTIFICATIONS_WIFI_ONLY = booleanPreferencesKey("notifications_wifi_only")
    val KEY_FOLLOW_NOTIFY_PROMPT_SHOWN = booleanPreferencesKey("follow_notify_prompt_shown")
    val KEY_GESTURE_HINT_SHOWN = booleanPreferencesKey("gesture_hint_shown")
    val KEY_SCREEN_SWIPE_HINT_SHOWN = booleanPreferencesKey("screen_swipe_hint_shown")

    // What's new filters
    val KEY_WHATSNEW_REMEMBER_FILTERS = booleanPreferencesKey("whatsnew_remember_filters")
    val KEY_WHATSNEW_FILTER_PERIOD = stringPreferencesKey("whatsnew_filter_period")
    val KEY_WHATSNEW_FILTER_TYPES = stringPreferencesKey("whatsnew_filter_types")
    val KEY_WHATSNEW_FILTER_SCOPE = stringPreferencesKey("whatsnew_filter_scope")

    // Data usage
    val KEY_DATA_USAGE_DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("data_usage_download_wifi_only")
    val KEY_DATA_USAGE_BROWSE_WIFI_ONLY = booleanPreferencesKey("data_usage_browse_wifi_only")
    val KEY_DATA_USAGE_APP_DATA_WIFI_ONLY = booleanPreferencesKey("data_usage_app_data_wifi_only")

    // Data
    val KEY_DATA_SYNC_ON_LAUNCH = booleanPreferencesKey("data_sync_on_launch")
    val KEY_DATA_KEEP_HISTORY = booleanPreferencesKey("data_keep_history")

    // Problem reports
    val KEY_REPORT_OFFER_CRASHES = booleanPreferencesKey("report_offer_crashes")

    // In-app update
    val KEY_UPDATE_AUTO_CHECK = booleanPreferencesKey("update_auto_check")
    val KEY_UPDATE_LAST_CHECK_MS = longPreferencesKey("update_last_check_ms")
    val KEY_UPDATE_LATER_UNTIL_MS = longPreferencesKey("update_later_until_ms")
    val KEY_UPDATE_SKIPPED_VERSION = stringPreferencesKey("update_skipped_version")
    val KEY_UPDATE_INSTALL_EXPLAINED = booleanPreferencesKey("update_install_explained")
    val KEY_UPDATE_KNOWN_AVAILABLE = stringPreferencesKey("update_known_available")
    val KEY_UPDATE_KNOWN_RELEASE = stringPreferencesKey("update_known_release")
    val KEY_UPDATE_NOTIFY = booleanPreferencesKey("update_notify")
    val KEY_UPDATE_INTERVAL = stringPreferencesKey("update_interval")
    val KEY_UPDATE_NOTIFIED_VERSION = stringPreferencesKey("update_notified_version")

    // Downloads
    val KEY_DOWNLOAD_FOLDER_URI = stringPreferencesKey("download_folder_uri")
    val KEY_DOWNLOAD_FOLDER_FALLBACK_NOTIFIED = booleanPreferencesKey("download_folder_fallback_notified")

    // Malware checks
    val KEY_CHECK_VT_ON = booleanPreferencesKey("check_vt_on")
    val KEY_CHECK_VT_KEY = stringPreferencesKey("check_vt_key")
    val KEY_CHECK_HA_ON = booleanPreferencesKey("check_ha_on")
    val KEY_CHECK_HA_KEY = stringPreferencesKey("check_ha_key")
    val KEY_CHECK_HOLD = booleanPreferencesKey("check_hold")
    val KEY_CHECK_AUTO_RISKY = booleanPreferencesKey("check_auto_risky")
    val KEY_CHECK_KEY_PROMPT_SHOWN = booleanPreferencesKey("check_key_prompt_shown")
    val KEY_CHECK_EXPLAIN_SHOWN = booleanPreferencesKey("check_explain_shown")
    val KEY_CHECK_FMHY_DOWNLOAD_STATUS = booleanPreferencesKey("check_fmhy_download_status")
}
