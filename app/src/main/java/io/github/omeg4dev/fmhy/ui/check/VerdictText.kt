package io.github.omeg4dev.fmhy.ui.check

import io.github.omeg4dev.fmhy.data.safety.SafetyStatus

fun verdictTitle(status: SafetyStatus): String = when (status) {
    SafetyStatus.FMHY_SITE -> "Official FMHY site"
    SafetyStatus.STARRED -> "Starred on FMHY"
    SafetyStatus.LISTED -> "Listed on FMHY"
    SafetyStatus.POTENTIALLY_UNSAFE -> "Potentially unsafe"
    SafetyStatus.UNSAFE -> "Unsafe"
    SafetyStatus.SEARCH_ENGINE -> "Search engine"
    SafetyStatus.NO_DATA -> "Not on FMHY"
    SafetyStatus.INVALID -> "Not a link"
}

fun verdictBody(status: SafetyStatus, reason: String? = null): String {
    val cleanReason = reason?.trim().orEmpty()
    return when (status) {
        SafetyStatus.FMHY_SITE -> "This site belongs to FMHY."
        SafetyStatus.STARRED -> "FMHY recommends this site."
        SafetyStatus.LISTED -> "This site is listed on FMHY."
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
        SafetyStatus.NO_DATA -> "FMHY has no information about this site. That doesn't mean it's safe."
        SafetyStatus.INVALID -> "Enter a web address like example.com."
    }
}
