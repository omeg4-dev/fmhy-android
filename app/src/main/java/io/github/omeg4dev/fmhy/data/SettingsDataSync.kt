package io.github.omeg4dev.fmhy.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_DATA_KEEP_HISTORY
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_DATA_SYNC_ON_LAUNCH
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_DATA_USAGE_APP_DATA_WIFI_ONLY
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_DATA_USAGE_BROWSE_WIFI_ONLY
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_DATA_USAGE_DOWNLOAD_WIFI_ONLY
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_DOWNLOAD_FOLDER_FALLBACK_NOTIFIED
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_DOWNLOAD_FOLDER_URI
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_NOTIFICATIONS_ENABLED
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_NOTIFICATIONS_INTERVAL
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_NOTIFICATIONS_NOTIFY_FOLLOWED
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_NOTIFICATIONS_NOTIFY_NEW_POSTS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_NOTIFICATIONS_USE_TRACKER
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_NOTIFICATIONS_WATCH_SAVED
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_NOTIFICATIONS_WIFI_ONLY
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_UPDATE_AUTO_CHECK
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_UPDATE_INSTALL_EXPLAINED
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_UPDATE_INTERVAL
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_UPDATE_KNOWN_AVAILABLE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_UPDATE_KNOWN_RELEASE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_UPDATE_LAST_CHECK_MS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_UPDATE_LATER_UNTIL_MS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_UPDATE_NOTIFIED_VERSION
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_UPDATE_NOTIFY
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_UPDATE_SKIPPED_VERSION
import io.github.omeg4dev.fmhy.update.ReleaseInfo
import kotlinx.serialization.encodeToString

// DefaultSettingsRepository read/write helpers: notifications, data usage, updates, downloads, and sync.

internal fun DefaultSettingsRepository.readNotificationPrefs(prefs: Preferences): NotificationPrefs {
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
internal fun DefaultSettingsRepository.writeNotificationPrefs(prefs: MutablePreferences, p: NotificationPrefs) {
    prefs[KEY_NOTIFICATIONS_USE_TRACKER] = p.useTracker
    prefs[KEY_NOTIFICATIONS_ENABLED] = p.enabled
    prefs[KEY_NOTIFICATIONS_NOTIFY_FOLLOWED] = p.notifyFollowed
    prefs[KEY_NOTIFICATIONS_WATCH_SAVED] = p.watchSaved
    prefs[KEY_NOTIFICATIONS_NOTIFY_NEW_POSTS] = p.notifyNewPosts
    prefs[KEY_NOTIFICATIONS_INTERVAL] = p.interval.name
}
internal fun DefaultSettingsRepository.readDataUsagePrefs(prefs: Preferences): DataUsagePrefs {
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
internal fun DefaultSettingsRepository.writeDataUsagePrefs(prefs: MutablePreferences, p: DataUsagePrefs) {
    prefs[KEY_DATA_USAGE_DOWNLOAD_WIFI_ONLY] = p.downloadWifiOnly
    prefs[KEY_DATA_USAGE_BROWSE_WIFI_ONLY] = p.browseWifiOnly
    prefs[KEY_DATA_USAGE_APP_DATA_WIFI_ONLY] = p.appDataWifiOnly
}
internal fun DefaultSettingsRepository.readUpdatePrefs(prefs: Preferences): UpdatePrefs {
    val default = UpdatePrefs()
    val interval = parseUpdateCheckInterval(prefs[KEY_UPDATE_INTERVAL], default.interval)
    val knownRelease = prefs[KEY_UPDATE_KNOWN_RELEASE]?.let { raw ->
        runCatching { json.decodeFromString<ReleaseInfo>(raw) }.getOrNull()
    }
    return UpdatePrefs(
        autoCheck = prefs[KEY_UPDATE_AUTO_CHECK] ?: default.autoCheck,
        lastCheckMs = prefs[KEY_UPDATE_LAST_CHECK_MS] ?: default.lastCheckMs,
        laterUntilMs = prefs[KEY_UPDATE_LATER_UNTIL_MS] ?: default.laterUntilMs,
        skippedVersion = prefs[KEY_UPDATE_SKIPPED_VERSION] ?: default.skippedVersion,
        installExplained = prefs[KEY_UPDATE_INSTALL_EXPLAINED] ?: default.installExplained,
        knownAvailable = prefs[KEY_UPDATE_KNOWN_AVAILABLE] ?: default.knownAvailable,
        knownRelease = knownRelease,
        notify = prefs[KEY_UPDATE_NOTIFY] ?: default.notify,
        interval = interval,
        notifiedVersion = prefs[KEY_UPDATE_NOTIFIED_VERSION] ?: default.notifiedVersion,
    )
}
internal fun DefaultSettingsRepository.writeUpdatePrefs(prefs: MutablePreferences, p: UpdatePrefs) {
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
    if (p.knownRelease != null) {
        prefs[KEY_UPDATE_KNOWN_RELEASE] = json.encodeToString(p.knownRelease)
    } else {
        prefs.remove(KEY_UPDATE_KNOWN_RELEASE)
    }
    prefs[KEY_UPDATE_NOTIFY] = p.notify
    prefs[KEY_UPDATE_INTERVAL] = p.interval.name
    if (p.notifiedVersion != null) {
        prefs[KEY_UPDATE_NOTIFIED_VERSION] = p.notifiedVersion
    } else {
        prefs.remove(KEY_UPDATE_NOTIFIED_VERSION)
    }
}
internal fun DefaultSettingsRepository.readDownloadPrefs(prefs: Preferences): DownloadPrefs {
    val default = DownloadPrefs()
    return DownloadPrefs(
        folderUri = prefs[KEY_DOWNLOAD_FOLDER_URI] ?: default.folderUri,
        folderFallbackNotified = prefs[KEY_DOWNLOAD_FOLDER_FALLBACK_NOTIFIED] ?: default.folderFallbackNotified,
    )
}
internal fun DefaultSettingsRepository.writeDownloadPrefs(prefs: MutablePreferences, p: DownloadPrefs) {
    if (p.folderUri != null) {
        prefs[KEY_DOWNLOAD_FOLDER_URI] = p.folderUri
    } else {
        prefs.remove(KEY_DOWNLOAD_FOLDER_URI)
    }
    prefs[KEY_DOWNLOAD_FOLDER_FALLBACK_NOTIFIED] = p.folderFallbackNotified
}
internal fun DefaultSettingsRepository.readDataPrefs(prefs: Preferences): DataPrefs {
    val default = DataPrefs()
    return DataPrefs(
        syncOnLaunch = prefs[KEY_DATA_SYNC_ON_LAUNCH] ?: default.syncOnLaunch,
        keepHistory = prefs[KEY_DATA_KEEP_HISTORY] ?: default.keepHistory,
    )
}
internal fun DefaultSettingsRepository.writeDataPrefs(prefs: MutablePreferences, p: DataPrefs) {
    prefs[KEY_DATA_SYNC_ON_LAUNCH] = p.syncOnLaunch
    prefs[KEY_DATA_KEEP_HISTORY] = p.keepHistory
}
internal fun parseUpdateCheckInterval(
    raw: String?,
    default: UpdateCheckInterval = UpdateCheckInterval.H12,
): UpdateCheckInterval {
    if (raw == null) return default
    return try {
        UpdateCheckInterval.valueOf(raw)
    } catch (_: Exception) {
        default
    }
}
