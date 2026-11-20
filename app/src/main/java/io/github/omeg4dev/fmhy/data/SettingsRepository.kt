package io.github.omeg4dev.fmhy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.omeg4dev.fmhy.nav.BottomBarStyle
import io.github.omeg4dev.fmhy.ui.whatsnew.WhatsNewFilterPrefs
import kotlinx.coroutines.flow.Flow

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
    suspend fun setBottomBarStyle(style: BottomBarStyle)

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

    val whatsNewFilterPrefs: Flow<WhatsNewFilterPrefs>
        get() = kotlinx.coroutines.flow.flowOf(WhatsNewFilterPrefs())
    suspend fun updateWhatsNewFilterPrefs(f: (WhatsNewFilterPrefs) -> WhatsNewFilterPrefs) {}

    val gestureHintShown: Flow<Boolean>
        get() = kotlinx.coroutines.flow.flowOf(false)
    suspend fun setGestureHintShown(shown: Boolean) {}

    val screenSwipeHintShown: Flow<Boolean>
        get() = kotlinx.coroutines.flow.flowOf(false)
    suspend fun setScreenSwipeHintShown(shown: Boolean) {}

    val reportOfferCrashes: Flow<Boolean>
        get() = kotlinx.coroutines.flow.flowOf(true)
    suspend fun setReportOfferCrashes(enabled: Boolean) {}

    val dataPrefs: Flow<DataPrefs>
    suspend fun updateData(f: (DataPrefs) -> DataPrefs)

    val onboardingVersion: Flow<Int>
    suspend fun setOnboardingVersion(v: Int)
}
