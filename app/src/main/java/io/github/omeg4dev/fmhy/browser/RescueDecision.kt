package io.github.omeg4dev.fmhy.browser

import java.net.URI

/** A main-frame load that failed: a network error, or a 5xx whose body may still be a real page. */
data class FailedLoad(val url: String, val reason: String, val isHttp: Boolean)

/**
 * Pure "show the dead-site rescue screen?" decision. The rescue is only for the main page of the
 * address the tab is loading, only while nothing of that page is on screen, and only after a couple
 * of quiet background retries.
 */
object RescueDecision {
    const val MAX_SILENT_RETRIES = 2

    /** Wait after a 5xx page finished before probing it: challenge pages redirect within this time. */
    const val HTTP_SETTLE_MS = 2_000L

    enum class Action { IGNORE, RETRY, SHOW }

    fun decide(
        isForMainFrame: Boolean,
        failingUrl: String,
        currentUrls: Collection<String>,
        contentShown: Boolean,
        retriesDone: Int,
    ): Action = when {
        !isForMainFrame -> Action.IGNORE
        currentUrls.none { sameAddress(it, failingUrl) } -> Action.IGNORE
        contentShown -> Action.IGNORE
        retriesDone < MAX_SILENT_RETRIES -> Action.RETRY
        else -> Action.SHOW
    }

    /** Delay before silent retry number [retriesDone] + 1: 1 s, then 2 s. */
    fun retryDelayMs(retriesDone: Int): Long = 1_000L shl retriesDone.coerceIn(0, 4)

    /**
     * Same page address: ignores the fragment, http/https, a leading `www.`, host case and a trailing
     * slash. Path and query must match.
     */
    fun sameAddress(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val ka = addressKey(a) ?: return a.substringBefore('#') == b.substringBefore('#')
        val kb = addressKey(b) ?: return false
        return ka == kb
    }

    private fun addressKey(url: String): String? {
        val uri = try { URI(url.substringBefore('#')) } catch (_: Exception) { return null }
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val path = (uri.rawPath ?: "").trimEnd('/')
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        val port = if (uri.port == -1 || uri.port == 80 || uri.port == 443) "" else ":${uri.port}"
        return "$host$port$path$query"
    }

    /** What the page shows, as counted by [PROBE_JS]. */
    data class PageProbe(val textLength: Int, val links: Int, val media: Int)

    /** Counts visible text, links and media elements; result is "text,links,media". */
    const val PROBE_JS = "(function(){var b=document.body;if(!b)return '0,0,0';" +
        "return [(b.innerText||'').trim().length,document.links.length," +
        "document.querySelectorAll('img,video,iframe,canvas').length].join(',');})()"

    /** Parses the raw evaluateJavascript result (a JSON string such as "\"123,4,5\""). */
    fun parseProbe(raw: String?): PageProbe? {
        val parts = raw?.trim()?.removeSurrounding("\"")?.split(',') ?: return null
        if (parts.size != 3) return null
        val nums = parts.map { it.trim().toIntOrNull() ?: return null }
        return PageProbe(nums[0], nums[1], nums[2])
    }

    /**
     * A real page (served with an error status) rather than a server's error page: nginx and
     * Cloudflare 5xx pages have well under 2000 characters, a handful of links and few images.
     */
    fun looksLikeContent(probe: PageProbe?): Boolean {
        probe ?: return false
        return probe.textLength >= 2000 || probe.links >= 15 || probe.media >= 4
    }
}
