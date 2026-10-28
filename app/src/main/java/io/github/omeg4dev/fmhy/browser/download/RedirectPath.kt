package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.browser.InterstitialMerge
import io.github.omeg4dev.fmhy.browser.LookalikeAllow
import io.github.omeg4dev.fmhy.browser.WarningReason
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.safety.SafeGuard
import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import okhttp3.HttpUrl

data class HopFlag(
    val host: String,
    val url: String,
    val reason: WarningReason,
    val lookalikes: List<Entry> = emptyList(),
) {
    /** A lookalike flag that only points back at the site the redirect chain started on. */
    fun isStartSiteLookalike(startHost: String?): Boolean =
        reason == WarningReason.LOOKALIKE && LookalikeAllow.isStartSite(lookalikes, startHost)
}

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
                HopFlag(host = host, url = url, reason = reason, lookalikes = verdict.lookalikes)
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

    const val INSECURE_REDIRECT_MESSAGE = "The site tried to switch this download to an insecure connection"

    /** True when a redirect would move a download from https to plain http. */
    fun isInsecureDowngrade(current: HttpUrl, next: HttpUrl): Boolean =
        current.isHttps && !next.isHttps

    suspend fun firstFlaggedHop(
        urls: List<String>,
        checker: HopChecker,
        allowed: Set<String>,
        startHost: String? = null,
    ): HopFlag? {
        for (url in urls) {
            val flag = checker.check(url) ?: continue
            if (flag.host !in allowed && !flag.isStartSiteLookalike(startHost)) {
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
