package io.github.omeg4dev.fmhy.ui.check

import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import io.github.omeg4dev.fmhy.data.safety.Verdict
import io.github.omeg4dev.fmhy.data.safety.Via

fun verdictTitle(status: SafetyStatus): String = when (status) {
    SafetyStatus.FMHY_SITE -> "Official FMHY site"
    SafetyStatus.STARRED -> "Starred on FMHY"
    SafetyStatus.LISTED -> "Listed on FMHY"
    SafetyStatus.DOMAIN_LISTED -> "This domain is on FMHY"
    SafetyStatus.POTENTIALLY_UNSAFE -> "Potentially unsafe"
    SafetyStatus.UNSAFE -> "Unsafe"
    SafetyStatus.SEARCH_ENGINE -> "Search engine"
    SafetyStatus.NO_DATA -> "Not on FMHY"
    SafetyStatus.INVALID -> "Not a link"
}

fun verdictBody(
    status: SafetyStatus,
    reason: String? = null,
    host: String? = null,
    via: Via? = null,
    matchedEntryName: String? = null,
    sharedHostListedCount: Int = 0,
): String {
    val cleanReason = reason?.trim().orEmpty()
    return when (status) {
        SafetyStatus.FMHY_SITE -> "This site belongs to FMHY."
        SafetyStatus.STARRED -> {
            val suffix = when {
                via == Via.MIRROR && !matchedEntryName.isNullOrBlank() -> " (as a mirror of $matchedEntryName)"
                via == Via.EXTRA && !matchedEntryName.isNullOrBlank() -> " (as an extra link of $matchedEntryName)"
                else -> ""
            }
            "FMHY recommends this site$suffix."
        }
        SafetyStatus.LISTED -> {
            val suffix = when {
                via == Via.MIRROR && !matchedEntryName.isNullOrBlank() -> " (as a mirror of $matchedEntryName)"
                via == Via.EXTRA && !matchedEntryName.isNullOrBlank() -> " (as an extra link of $matchedEntryName)"
                else -> ""
            }
            "This site is listed on FMHY$suffix."
        }
        SafetyStatus.DOMAIN_LISTED -> {
            val h = host ?: "this domain"
            "FMHY lists other pages on $h, not this exact link."
        }
        SafetyStatus.POTENTIALLY_UNSAFE -> {
            val base = "FMHY lists this site as not recommended or potentially unsafe."
            if (cleanReason.isNotEmpty()) {
                val r = if (cleanReason.startsWith("Reason:", ignoreCase = true)) cleanReason else "Reason: $cleanReason"
                "$base $r"
            } else {
                base
            }
        }
        SafetyStatus.UNSAFE -> {
            val base = "FMHY warns against this site."
            if (cleanReason.isNotEmpty()) {
                val r = if (cleanReason.startsWith("Reason:", ignoreCase = true)) cleanReason else "Reason: $cleanReason"
                "$base $r"
            } else {
                base
            }
        }
        SafetyStatus.SEARCH_ENGINE -> "Search engines aren't rated."
        SafetyStatus.NO_DATA -> {
            if (sharedHostListedCount > 0 && !host.isNullOrBlank()) {
                "$host hosts many projects. FMHY lists $sharedHostListedCount of them, but not this one."
            } else {
                "FMHY doesn't list this site. That doesn't make it unsafe \u2014 or safe."
            }
        }
        SafetyStatus.INVALID -> "Enter a web address like example.com."
    }
}

fun verdictBody(verdict: Verdict): String {
    val matchedName = verdict.matches.firstOrNull()?.name
    return verdictBody(
        status = verdict.status,
        reason = verdict.reason,
        host = verdict.host,
        via = verdict.via,
        matchedEntryName = matchedName,
        sharedHostListedCount = verdict.sharedHostListedCount,
    )
}
