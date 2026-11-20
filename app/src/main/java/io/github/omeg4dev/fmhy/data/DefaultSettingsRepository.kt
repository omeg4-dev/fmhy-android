package io.github.omeg4dev.fmhy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_ACCENT_SEED
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_APP_ICON
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BLOCKING_ENABLED_LISTS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BOTTOM_BAR_STYLE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_COLOR_SOURCE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_FOLLOW_NOTIFY_PROMPT_SHOWN
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_GESTURE_HINT_SHOWN
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_HAPTIC_FEEDBACK
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_LAST_VISIT_TS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_ONBOARDING_VERSION
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PRIVACY_HIDE_COOKIE_BANNERS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PURE_BLACK
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_RECENT_SEARCHES
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_REPORT_OFFER_CRASHES
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_SCREEN_SWIPE_HINT_SHOWN
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_SECTION_LAYOUT
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_SECTION_ORDER
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_SHOW_INDEXES
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_SHOW_RANDOM_PICK
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_STARRED_ONLY
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_TEXT_SCALE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_THEME_MODE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_WEBSITE_STYLE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_WHATSNEW_FILTER_PERIOD
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_WHATSNEW_FILTER_SCOPE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_WHATSNEW_FILTER_TYPES
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_WHATSNEW_REMEMBER_FILTERS
import io.github.omeg4dev.fmhy.data.adblock.FilterListCatalog
import io.github.omeg4dev.fmhy.nav.BottomBarStyle
import io.github.omeg4dev.fmhy.ui.whatsnew.WhatsNewFilterCodec
import io.github.omeg4dev.fmhy.ui.whatsnew.WhatsNewFilterPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DefaultSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    constructor(context: Context) : this(context.dataStore)

    internal val json = Json { ignoreUnknownKeys = true }
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
        readAppearance(prefs)
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

    override suspend fun setBottomBarStyle(style: BottomBarStyle) {
        dataStore.edit { prefs ->
            prefs[KEY_BOTTOM_BAR_STYLE] = style.name
        }
    }
    // --- HomePrefs ---
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

    override val whatsNewFilterPrefs: Flow<WhatsNewFilterPrefs> = dataStore.data.map { prefs ->
        WhatsNewFilterPrefs(
            remember = prefs[KEY_WHATSNEW_REMEMBER_FILTERS] ?: false,
            period = WhatsNewFilterCodec.decodePeriod(prefs[KEY_WHATSNEW_FILTER_PERIOD]),
            types = WhatsNewFilterCodec.decodeTypes(prefs[KEY_WHATSNEW_FILTER_TYPES]),
            scope = WhatsNewFilterCodec.decodeScope(prefs[KEY_WHATSNEW_FILTER_SCOPE]),
        )
    }.distinctUntilChanged()

    override suspend fun updateWhatsNewFilterPrefs(f: (WhatsNewFilterPrefs) -> WhatsNewFilterPrefs) {
        dataStore.edit { prefs ->
            val current = WhatsNewFilterPrefs(
                remember = prefs[KEY_WHATSNEW_REMEMBER_FILTERS] ?: false,
                period = WhatsNewFilterCodec.decodePeriod(prefs[KEY_WHATSNEW_FILTER_PERIOD]),
                types = WhatsNewFilterCodec.decodeTypes(prefs[KEY_WHATSNEW_FILTER_TYPES]),
                scope = WhatsNewFilterCodec.decodeScope(prefs[KEY_WHATSNEW_FILTER_SCOPE]),
            )
            val updated = f(current)
            prefs[KEY_WHATSNEW_REMEMBER_FILTERS] = updated.remember
            prefs[KEY_WHATSNEW_FILTER_PERIOD] = WhatsNewFilterCodec.encodePeriod(updated.period)
            prefs[KEY_WHATSNEW_FILTER_TYPES] = WhatsNewFilterCodec.encodeTypes(updated.types)
            prefs[KEY_WHATSNEW_FILTER_SCOPE] = WhatsNewFilterCodec.encodeScope(updated.scope)
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

    override val screenSwipeHintShown: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SCREEN_SWIPE_HINT_SHOWN] ?: false
    }.distinctUntilChanged()

    override suspend fun setScreenSwipeHintShown(shown: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SCREEN_SWIPE_HINT_SHOWN] = shown
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
