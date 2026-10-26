package io.github.omeg4dev.fmhy.update

import io.github.omeg4dev.fmhy.work.NetworkKind
import io.github.omeg4dev.fmhy.work.NetworkPolicy

/**
 * Policies governing update checks, snoozing prompts, settings indicators, and network constraints.
 */
object UpdateCheckPolicy {
    /** 24-hour interval between automatic update checks on startup. */
    const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** 3-day snooze duration when the user taps "Later". */
    const val LATER_MS = 3L * 24 * 60 * 60 * 1000

    /**
     * Determines whether an automatic update check should run on app startup.
     *
     * Returns true if automatic checking is enabled and either no previous check has run
     * (or the system clock moved backwards), or at least [CHECK_INTERVAL_MS] has elapsed since [lastCheckMs].
     */
    fun shouldCheckOnStartup(autoCheck: Boolean, lastCheckMs: Long, nowMs: Long): Boolean {
        if (!autoCheck) return false
        if (lastCheckMs <= 0L || lastCheckMs > nowMs) return true
        return (nowMs - lastCheckMs) >= CHECK_INTERVAL_MS
    }

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
