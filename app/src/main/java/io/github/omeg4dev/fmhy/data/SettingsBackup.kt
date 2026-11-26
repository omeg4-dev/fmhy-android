package io.github.omeg4dev.fmhy.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Serializes and restores DataStore app settings for JSON backups.
 *
 * Only keys present in [specs] are exported and wiped/restored; keys in [excludedKeys]
 * (secrets, device-local URIs, ephemeral hints, update cache) never leave the device.
 */
internal object SettingsBackup {

    private sealed class KeySpec<T>(val key: Preferences.Key<T>) {
        abstract fun toJson(value: T): JsonElement
        abstract fun applyFromJson(prefs: MutablePreferences, element: JsonElement)

        fun exportIfPresent(prefs: Preferences): JsonElement? =
            prefs[key]?.let(::toJson)

        class Bool(key: Preferences.Key<Boolean>) : KeySpec<Boolean>(key) {
            override fun toJson(value: Boolean): JsonElement = JsonPrimitive(value)
            override fun applyFromJson(prefs: MutablePreferences, element: JsonElement) {
                val prim = element as? JsonPrimitive ?: return
                if (prim.isString) return
                val parsed = prim.booleanOrNull ?: return
                prefs[key] = parsed
            }
        }

        class IntSpec(
            key: Preferences.Key<Int>,
            private val range: IntRange? = null,
        ) : KeySpec<Int>(key) {
            override fun toJson(value: Int): JsonElement = JsonPrimitive(value)
            override fun applyFromJson(prefs: MutablePreferences, element: JsonElement) {
                val prim = element as? JsonPrimitive ?: return
                if (prim.isString) return
                val parsed = prim.intOrNull ?: return
                prefs[key] = if (range != null) parsed.coerceIn(range.first, range.last) else parsed
            }
        }

        class LongSpec(
            key: Preferences.Key<Long>,
            private val range: LongRange? = null,
        ) : KeySpec<Long>(key) {
            override fun toJson(value: Long): JsonElement = JsonPrimitive(value)
            override fun applyFromJson(prefs: MutablePreferences, element: JsonElement) {
                val prim = element as? JsonPrimitive ?: return
                if (prim.isString) return
                val parsed = prim.longOrNull ?: return
                prefs[key] = if (range != null) parsed.coerceIn(range.first, range.last) else parsed
            }
        }

        class FloatSpec(
            key: Preferences.Key<Float>,
            private val range: ClosedFloatingPointRange<Float>? = null,
        ) : KeySpec<Float>(key) {
            override fun toJson(value: Float): JsonElement = JsonPrimitive(value)
            override fun applyFromJson(prefs: MutablePreferences, element: JsonElement) {
                val prim = element as? JsonPrimitive ?: return
                if (prim.isString) return
                val parsed = prim.floatOrNull?.takeIf { it.isFinite() } ?: return
                prefs[key] = if (range != null) parsed.coerceIn(range.start, range.endInclusive) else parsed
            }
        }

        class Str(key: Preferences.Key<String>) : KeySpec<String>(key) {
            override fun toJson(value: String): JsonElement = JsonPrimitive(value)
            override fun applyFromJson(prefs: MutablePreferences, element: JsonElement) {
                val prim = element as? JsonPrimitive ?: return
                if (!prim.isString) return
                prefs[key] = prim.content
            }
        }

        class StrSet(key: Preferences.Key<Set<String>>) : KeySpec<Set<String>>(key) {
            override fun toJson(value: Set<String>): JsonElement =
                JsonArray(value.sorted().map(::JsonPrimitive))

            override fun applyFromJson(prefs: MutablePreferences, element: JsonElement) {
                val arr = element as? JsonArray ?: return
                if (!arr.all { it is JsonPrimitive && it.isString }) return
                prefs[key] = arr.mapTo(linkedSetOf()) { (it as JsonPrimitive).content }
            }
        }
    }

    val excludedKeys: Set<Preferences.Key<*>> = setOf(
        SettingsKeys.KEY_LAST_VISIT_TS,
        SettingsKeys.KEY_RECENT_SEARCHES,
        SettingsKeys.KEY_ONBOARDING_VERSION,
        SettingsKeys.KEY_FOLLOW_NOTIFY_PROMPT_SHOWN,
        SettingsKeys.KEY_GESTURE_HINT_SHOWN,
        SettingsKeys.KEY_SCREEN_SWIPE_HINT_SHOWN,
        SettingsKeys.KEY_CHECK_VT_KEY,
        SettingsKeys.KEY_CHECK_HA_KEY,
        SettingsKeys.KEY_CHECK_KEY_PROMPT_SHOWN,
        SettingsKeys.KEY_CHECK_EXPLAIN_SHOWN,
        SettingsKeys.KEY_UPDATE_LAST_CHECK_MS,
        SettingsKeys.KEY_UPDATE_LATER_UNTIL_MS,
        SettingsKeys.KEY_UPDATE_SKIPPED_VERSION,
        SettingsKeys.KEY_UPDATE_INSTALL_EXPLAINED,
        SettingsKeys.KEY_UPDATE_KNOWN_AVAILABLE,
        SettingsKeys.KEY_UPDATE_KNOWN_RELEASE,
        SettingsKeys.KEY_UPDATE_NOTIFIED_VERSION,
        SettingsKeys.KEY_DOWNLOAD_FOLDER_URI,
        SettingsKeys.KEY_DOWNLOAD_FOLDER_FALLBACK_NOTIFIED,
    )

    private val specs: List<KeySpec<*>> = listOf(
        // Appearance & general (including legacy fallback)
        KeySpec.Bool(SettingsKeys.KEY_OPEN_EXTERNAL_BROWSER),
        KeySpec.Str(SettingsKeys.KEY_THEME_MODE),
        KeySpec.Str(SettingsKeys.KEY_COLOR_SOURCE),
        KeySpec.Str(SettingsKeys.KEY_SECTION_LAYOUT),
        KeySpec.Bool(SettingsKeys.KEY_PURE_BLACK),
        KeySpec.IntSpec(SettingsKeys.KEY_ACCENT_SEED),
        KeySpec.FloatSpec(SettingsKeys.KEY_TEXT_SCALE, 0.85f..1.30f),
        KeySpec.Str(SettingsKeys.KEY_SECTION_ORDER),
        KeySpec.Bool(SettingsKeys.KEY_SHOW_RANDOM_PICK),
        KeySpec.Bool(SettingsKeys.KEY_STARRED_ONLY),
        KeySpec.Bool(SettingsKeys.KEY_SHOW_INDEXES),
        KeySpec.Bool(SettingsKeys.KEY_HAPTIC_FEEDBACK),
        KeySpec.Bool(SettingsKeys.KEY_WEBSITE_STYLE),
        KeySpec.Str(SettingsKeys.KEY_APP_ICON),
        KeySpec.Str(SettingsKeys.KEY_BOTTOM_BAR_STYLE),

        // Home
        KeySpec.Str(SettingsKeys.KEY_HOME_BLOCKS),
        KeySpec.Bool(SettingsKeys.KEY_HOME_PERSONAL_HOME),
        KeySpec.IntSpec(SettingsKeys.KEY_HOME_NEW_BADGE_DAYS),
        KeySpec.Bool(SettingsKeys.KEY_HOME_SEARCH_EXACT),
        KeySpec.Bool(SettingsKeys.KEY_HOME_SWIPE_TO_BOOKMARK),
        KeySpec.Bool(SettingsKeys.KEY_HOME_SWAP_TAP_HOLD),
        KeySpec.Str(SettingsKeys.KEY_HOME_SEARCH_TAP_TARGET),
        KeySpec.Bool(SettingsKeys.KEY_HOME_SWIPE_BETWEEN_SCREENS),
        KeySpec.Bool(SettingsKeys.KEY_HOME_WEBSITE_CATEGORY_NAMES),
        KeySpec.Bool(SettingsKeys.KEY_HOME_SHOW_NSFW_LINK),

        // Browser
        KeySpec.Str(SettingsKeys.KEY_BROWSER_OPEN_LINKS_WITH),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_SITE_BAR),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_DEAD_SITE_RESCUE),
        KeySpec.IntSpec(SettingsKeys.KEY_BROWSER_MAX_TABS, 5..20),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_RESTORE_TABS),
        KeySpec.Str(SettingsKeys.KEY_BROWSER_SEARCH_ENGINE),
        KeySpec.Str(SettingsKeys.KEY_BROWSER_TOOLBAR_POSITION),
        KeySpec.Str(SettingsKeys.KEY_BROWSER_BUTTON),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_BUBBLE_HIDDEN),
        KeySpec.Str(SettingsKeys.KEY_BROWSER_BUBBLE_SIDE),
        KeySpec.FloatSpec(SettingsKeys.KEY_BROWSER_BUBBLE_Y, 0f..1f),
        KeySpec.Str(SettingsKeys.KEY_BROWSER_TABS_VIEW),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_JAVASCRIPT),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_FORCE_DARK),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_DESKTOP_BY_DEFAULT),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_TEXT_ZOOM_FOLLOWS_APP),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_FORCE_ZOOM),
        KeySpec.Str(SettingsKeys.KEY_BROWSER_FULLSCREEN_ORIENTATION),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_VIDEO_AUTO_ROTATE),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_PICTURE_IN_PICTURE),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_AUTO_PIP),
        KeySpec.FloatSpec(SettingsKeys.KEY_BROWSER_DEFAULT_SPEED, 0.5f..2f),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_DOUBLE_TAP_SEEK),
        KeySpec.Str(SettingsKeys.KEY_BROWSER_AUTOPLAY),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_BACKGROUND_PLAYBACK),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_KEEP_SCREEN_ON),
        KeySpec.Bool(SettingsKeys.KEY_BROWSER_VIDEO_DOWNLOAD_BUTTON),

        // Blocking
        KeySpec.Bool(SettingsKeys.KEY_BLOCKING_ADBLOCK),
        KeySpec.StrSet(SettingsKeys.KEY_BLOCKING_ENABLED_LISTS),
        KeySpec.Str(SettingsKeys.KEY_BLOCKING_CUSTOM_RULES),
        KeySpec.StrSet(SettingsKeys.KEY_BLOCKING_ALLOWLIST),
        KeySpec.Bool(SettingsKeys.KEY_BLOCKING_SHOW_BLOCKED_COUNT),
        KeySpec.Bool(SettingsKeys.KEY_BLOCKING_ELEMENT_HIDING),
        KeySpec.Bool(SettingsKeys.KEY_BLOCKING_SCRIPTLETS),
        KeySpec.Bool(SettingsKeys.KEY_BLOCKING_AUTO_UPDATE_LISTS),

        // Protection
        KeySpec.Bool(SettingsKeys.KEY_PROTECTION_UNSAFE_WARNING),
        KeySpec.Bool(SettingsKeys.KEY_PROTECTION_CRITICAL_NOTE_WARNING),
        KeySpec.Bool(SettingsKeys.KEY_PROTECTION_POTENTIALLY_UNSAFE_WARNING),
        KeySpec.Bool(SettingsKeys.KEY_PROTECTION_POPUP_BLOCKER),
        KeySpec.Bool(SettingsKeys.KEY_PROTECTION_REDIRECT_GUARD),
        KeySpec.Bool(SettingsKeys.KEY_PROTECTION_BLOCK_APP_LINKS),
        KeySpec.Bool(SettingsKeys.KEY_PROTECTION_ASK_BEFORE_DOWNLOAD),
        KeySpec.Bool(SettingsKeys.KEY_PROTECTION_WARN_RISKY_DOWNLOADS),
        KeySpec.Bool(SettingsKeys.KEY_PROTECTION_SAFE_BROWSING),
        KeySpec.Bool(SettingsKeys.KEY_PROTECTION_SHOW_VPN_STATE),
        KeySpec.Bool(SettingsKeys.KEY_PROTECTION_SHOW_VPN_HOME_ICON),
        KeySpec.Bool(SettingsKeys.KEY_PROTECTION_SHOW_IP_COUNTRY),
        KeySpec.Bool(SettingsKeys.KEY_PROTECTION_BLOCK_WITHOUT_VPN),

        // Privacy
        KeySpec.Bool(SettingsKeys.KEY_PRIVACY_BLOCK_THIRD_PARTY_COOKIES),
        KeySpec.Bool(SettingsKeys.KEY_PRIVACY_HIDE_COOKIE_BANNERS),
        KeySpec.Bool(SettingsKeys.KEY_PRIVACY_CLEAR_DATA_ON_EXIT),
        KeySpec.Bool(SettingsKeys.KEY_PRIVACY_PRIVATE_TABS_BY_DEFAULT),
        KeySpec.Bool(SettingsKeys.KEY_PRIVACY_SEND_GPC),

        // Notifications
        KeySpec.Bool(SettingsKeys.KEY_NOTIFICATIONS_USE_TRACKER),
        KeySpec.Bool(SettingsKeys.KEY_NOTIFICATIONS_ENABLED),
        KeySpec.Bool(SettingsKeys.KEY_NOTIFICATIONS_NOTIFY_FOLLOWED),
        KeySpec.Bool(SettingsKeys.KEY_NOTIFICATIONS_WATCH_SAVED),
        KeySpec.Bool(SettingsKeys.KEY_NOTIFICATIONS_NOTIFY_NEW_POSTS),
        KeySpec.Str(SettingsKeys.KEY_NOTIFICATIONS_INTERVAL),
        KeySpec.Bool(SettingsKeys.KEY_NOTIFICATIONS_WIFI_ONLY),

        // What's new filters
        KeySpec.Bool(SettingsKeys.KEY_WHATSNEW_REMEMBER_FILTERS),
        KeySpec.Str(SettingsKeys.KEY_WHATSNEW_FILTER_PERIOD),
        KeySpec.Str(SettingsKeys.KEY_WHATSNEW_FILTER_TYPES),
        KeySpec.Str(SettingsKeys.KEY_WHATSNEW_FILTER_SCOPE),

        // Data usage
        KeySpec.Bool(SettingsKeys.KEY_DATA_USAGE_DOWNLOAD_WIFI_ONLY),
        KeySpec.Bool(SettingsKeys.KEY_DATA_USAGE_BROWSE_WIFI_ONLY),
        KeySpec.Bool(SettingsKeys.KEY_DATA_USAGE_APP_DATA_WIFI_ONLY),

        // Data
        KeySpec.Bool(SettingsKeys.KEY_DATA_SYNC_ON_LAUNCH),
        KeySpec.Bool(SettingsKeys.KEY_DATA_KEEP_HISTORY),

        // Problem reports
        KeySpec.Bool(SettingsKeys.KEY_REPORT_OFFER_CRASHES),

        // In-app update
        KeySpec.Bool(SettingsKeys.KEY_UPDATE_AUTO_CHECK),
        KeySpec.Bool(SettingsKeys.KEY_UPDATE_NOTIFY),
        KeySpec.Str(SettingsKeys.KEY_UPDATE_INTERVAL),

        // Malware checks
        KeySpec.Bool(SettingsKeys.KEY_CHECK_VT_ON),
        KeySpec.Bool(SettingsKeys.KEY_CHECK_HA_ON),
        KeySpec.Bool(SettingsKeys.KEY_CHECK_HOLD),
        KeySpec.Bool(SettingsKeys.KEY_CHECK_AUTO_RISKY),
        KeySpec.Bool(SettingsKeys.KEY_CHECK_FMHY_DOWNLOAD_STATUS),
    )

    val backedUpKeys: Set<Preferences.Key<*>> = specs.mapTo(linkedSetOf()) { it.key }

    fun export(prefs: Preferences): JsonObject = buildJsonObject {
        for (spec in specs) {
            val encoded = spec.exportIfPresent(prefs) ?: continue
            put(spec.key.name, encoded)
        }
    }

    suspend fun restore(dataStore: DataStore<Preferences>, settings: JsonObject) {
        dataStore.edit { prefs ->
            for (spec in specs) {
                prefs.remove(spec.key)
            }
            for (spec in specs) {
                val element = settings[spec.key.name] ?: continue
                spec.applyFromJson(prefs, element)
            }
        }
    }
}
