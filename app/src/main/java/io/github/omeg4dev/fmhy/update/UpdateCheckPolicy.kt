package io.github.omeg4dev.fmhy.update

import io.github.omeg4dev.fmhy.data.UpdateCheckInterval
import io.github.omeg4dev.fmhy.data.UpdatePrefs
import io.github.omeg4dev.fmhy.work.NetworkKind
import io.github.omeg4dev.fmhy.work.NetworkPolicy

/**
 * Policies governing update checks, snoozing prompts, settings indicators, and network constraints.
 */
object UpdateCheckPolicy {
    /** 3-day snooze duration when the user taps "Later". */
    const val LATER_MS = 3L * 24 * 60 * 60 * 1000

    fun intervalMs(interval: UpdateCheckInterval): Long = interval.hours * 3_600_000L

    /**
     * Determines whether an automatic update check should run on app startup.
     */
    fun shouldCheckOnStartup(autoCheck: Boolean): Boolean = autoCheck

    /**
     * Returns the pending release to show immediately at app startup, or null if prompts should be suppressed.
     */
    fun startupRelease(prefs: UpdatePrefs, installed: String, nowMs: Long): ReleaseInfo? {
        if (!prefs.autoCheck) return null
        val release = prefs.knownRelease ?: return null
        return if (shouldShowPrompt(release.version, installed, prefs.skippedVersion, prefs.laterUntilMs, nowMs)) {
            release
        } else {
            null
        }
    }

    /**
     * Returns the release matching a tapped update notification, or null if mismatched, not newer, or skipped.
     */
    fun tapRelease(prefs: UpdatePrefs, extraVersion: String?, installed: String): ReleaseInfo? {
        val release = prefs.knownRelease ?: return null
        val isSkipped = prefs.skippedVersion != null && (
            release.version == prefs.skippedVersion ||
                (ReleaseParser.parseVersion(prefs.skippedVersion) != null &&
                    ReleaseParser.compareVersions(release.version, prefs.skippedVersion) == 0)
        )
        if (extraVersion != null &&
            extraVersion == release.version &&
            ReleaseParser.compareVersions(release.version, installed) > 0 &&
            !isSkipped
        ) {
            return release
        }
        return null
    }

    fun shouldNotify(
        available: String,
        installed: String,
        skipped: String?,
        laterUntilMs: Long,
        notifiedVersion: String?,
        nowMs: Long,
    ): Boolean = shouldShowPrompt(available, installed, skipped, laterUntilMs, nowMs) && available != notifiedVersion

    /**
     * Calculates the timestamp until which update prompts are snoozed after tapping "Later" at [nowMs].
     */
    fun laterUntil(nowMs: Long): Long = nowMs + LATER_MS

    /**
     * Evaluates whether an update prompt dialog should be presented to the user.
     *
     * Shown when [available] is newer than [installed], not marked as [skipped],
     * and the snooze period [laterUntilMs] has expired ([nowMs] >= [laterUntilMs]).
     */
    fun shouldShowPrompt(
        available: String,
        installed: String,
        skipped: String?,
        laterUntilMs: Long,
        nowMs: Long,
    ): Boolean {
        if (nowMs < laterUntilMs) return false
        return ReleaseParser.isNewer(available, installed, skipped)
    }

    /**
     * Determines whether the settings screen should display an update badge/marker.
     *
     * Returns true if [available] is strictly newer than [installed].
     * Unlike the dialog prompt, this marker ignores the "Later" snooze and persists even if [available] was skipped.
     */
    fun showSettingsMarker(available: String?, installed: String): Boolean {
        if (available == null) return false
        return ReleaseParser.compareVersions(available, installed) > 0
    }

    /**
     * Recommended network action for performing an update check or download.
     */
    enum class NetworkDecision {
        PROCEED,
        WAIT_FOR_WIFI,
        ASK_MOBILE_DATA,
    }

    /**
     * Evaluates whether an update action may proceed, must wait for Wi-Fi, or requires user confirmation.
     *
     * For manual requests:
     * - If [overrideThisTime] is true: returns [NetworkDecision.PROCEED].
     * - If Wi-Fi only is enabled and network is mobile data: returns [NetworkDecision.ASK_MOBILE_DATA].
     * - Otherwise: returns [NetworkDecision.PROCEED] (disconnected requests proceed and surface network errors directly).
     *
     * For automatic checks:
     * - If [overrideThisTime] is true or network policy allows the request: returns [NetworkDecision.PROCEED].
     * - Otherwise: returns [NetworkDecision.WAIT_FOR_WIFI].
     */
    fun networkDecision(
        manual: Boolean,
        wifiOnlySwitch: Boolean,
        kind: NetworkKind,
        overrideThisTime: Boolean = false,
    ): NetworkDecision {
        if (overrideThisTime) return NetworkDecision.PROCEED

        return if (manual) {
            if (NetworkPolicy.needsAsk(manual = true, switchOn = wifiOnlySwitch, kind = kind)) {
                NetworkDecision.ASK_MOBILE_DATA
            } else {
                NetworkDecision.PROCEED
            }
        } else {
            if (NetworkPolicy.allowed(switchOn = wifiOnlySwitch, kind = kind, overrideThisTime = false)) {
                NetworkDecision.PROCEED
            } else {
                NetworkDecision.WAIT_FOR_WIFI
            }
        }
    }
}
