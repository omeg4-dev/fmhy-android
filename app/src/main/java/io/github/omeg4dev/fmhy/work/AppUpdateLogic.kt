package io.github.omeg4dev.fmhy.work

import io.github.omeg4dev.fmhy.data.UpdatePrefs
import io.github.omeg4dev.fmhy.update.ReleaseInfo
import io.github.omeg4dev.fmhy.update.ReleaseParser
import io.github.omeg4dev.fmhy.update.UpdateCheckPolicy

sealed interface AppUpdateDecision {
    data object NoUpdate : AppUpdateDecision
    data class UpdateKnown(val version: String) : AppUpdateDecision
    data class Notify(val version: String, val title: String, val text: String) : AppUpdateDecision
}

object AppUpdateLogic {

    fun extractNotificationText(notes: String?): String {
        if (notes.isNullOrBlank()) return "Tap to update"
        for (line in notes.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val cleaned = trimmed.trimStart { it == '#' || it == '-' || it == '*' || it.isWhitespace() }.trim()
            if (cleaned.isNotEmpty()) {
                return if (cleaned.length > 120) cleaned.take(120) else cleaned
            }
        }
        return "Tap to update"
    }

    fun decide(
        release: ReleaseInfo?,
        installed: String,
        prefs: UpdatePrefs,
        nowMs: Long,
    ): AppUpdateDecision {
        if (release == null) return AppUpdateDecision.NoUpdate
        val isNewer = ReleaseParser.compareVersions(release.version, installed) > 0
        if (!isNewer) return AppUpdateDecision.NoUpdate

        return if (UpdateCheckPolicy.shouldNotify(
                available = release.version,
                installed = installed,
                skipped = prefs.skippedVersion,
                laterUntilMs = prefs.laterUntilMs,
                notifiedVersion = prefs.notifiedVersion,
                nowMs = nowMs,
            )
        ) {
            val title = "FMHY ${release.version} is available"
            val text = extractNotificationText(release.notes)
            AppUpdateDecision.Notify(version = release.version, title = title, text = text)
        } else {
            AppUpdateDecision.UpdateKnown(version = release.version)
        }
    }
}
