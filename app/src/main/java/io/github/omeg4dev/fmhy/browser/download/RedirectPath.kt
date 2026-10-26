package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.browser.InterstitialMerge
import io.github.omeg4dev.fmhy.browser.WarningReason
import io.github.omeg4dev.fmhy.data.safety.SafeGuard
import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import okhttp3.HttpUrl

data class HopFlag(
    val host: String,
    val url: String,
    val reason: WarningReason,
)

fun interface HopChecker {
    suspend fun check(url: String): HopFlag?

    companion object {
        fun from(safeGuard: SafeGuard): HopChecker = HopChecker { url ->
            val verdict = safeGuard.check(url)
            val reason = when {
                verdict.status == SafetyStatus.UNSAFE -> WarningReason.UNSAFE
                verdict.status == SafetyStatus.POTENTIALLY_UNSAFE -> WarningReason.POTENTIALLY_UNSAFE
                verdict.status == SafetyStatus.NO_DATA && verdict.lookalikes.isNotEmpty() -> WarningReason.LOOKALIKE
                else -> null
            }
            if (reason != null) {
                val host = verdict.host ?: SafeGuard.extractHostFromUrl(url) ?: ""
                HopFlag(host = host, url = url, reason = reason)
            } else {
                null
            }
        }
    }
}

object RedirectPath {
    const val MAX_HOPS = 10

    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    fun nextUrl(current: HttpUrl, location: String?, code: Int): HttpUrl? {
        if (code !in REDIRECT_CODES) return null
        val loc = location?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val target = current.resolve(loc) ?: return null
        if (target.scheme != "http" && target.scheme != "https") return null
        return target
    }

    suspend fun firstFlaggedHop(
        urls: List<String>,
        checker: HopChecker,
        allowed: Set<String>,
    ): HopFlag? {
        for (url in urls) {
            val flag = checker.check(url) ?: continue
            if (flag.host !in allowed) {
                return flag
            }
        }
        return null
    }
}

object RedirectWarningText {
    fun sentence(from: String, flag: HopFlag): String {
        val raw = InterstitialMerge.sentence(flag.reason)
        val clause = raw.replace(" this site", "")
            .let { if (it.startsWith("An ")) "an " + it.removePrefix("An ") else it }
        return "The download was redirected from $from to ${flag.host}, which $clause."
    }
}
