package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.user.HistoryItem
import java.net.URI

/** One site in the start screen's Recent list: all history rows of one host, shown once. */
data class RecentSite(
    val key: String,          // siteKey() of the group
    val host: String,         // host as shown, "www." removed, lowercase
    val title: String,        // newest row's title if not blank, else host
    val url: String,          // newest row's url (tap opens this)
    val lastVisit: Long,      // newest row's ts
    val historyIds: List<Long>, // ids of ALL rows of this site in the input (for "remove this one")
)

/** Lowercase host without a leading "www.", or null for rows that are not http(s) sites
 *  (about:blank, data:, file:, javascript:, blank, unparsable). */
fun siteKey(url: String): String? {
    if (url.isBlank()) return null
    val trimmed = url.trim()
    val lower = trimmed.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
        return null
    }

    val host = try {
        val uri = URI(trimmed)
        uri.host
    } catch (_: Exception) {
        null
    } ?: run {
        try {
            val afterScheme = trimmed.substring(trimmed.indexOf("://") + 3)
            val hostPart = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
            val hostWithoutAuth = if (hostPart.contains('@')) hostPart.substringAfter('@') else hostPart
            val hostWithoutPort = hostWithoutAuth.substringBefore(':')
            hostWithoutPort.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    } ?: return null

    val cleanHost = host.lowercase().trim()
    if (cleanHost.isEmpty()) return null
    val stripped = if (cleanHost.startsWith("www.")) {
        cleanHost.removePrefix("www.")
    } else {
        cleanHost
    }
    return stripped.ifEmpty { null }
}

/** Groups [history] by [siteKey], newest group first (by its newest ts, ties: higher id first),
 *  skips rows with a null key, returns at most [limit] groups. Input order is not trusted. */
fun groupRecent(history: List<HistoryItem>, limit: Int = 5): List<RecentSite> {
    if (limit <= 0 || history.isEmpty()) return emptyList()

    val groups = LinkedHashMap<String, MutableList<HistoryItem>>()
    for (item in history) {
        val key = siteKey(item.url) ?: continue
        groups.getOrPut(key) { mutableListOf() }.add(item)
    }

    if (groups.isEmpty()) return emptyList()

    val comparator = compareBy<HistoryItem>({ it.ts }, { it.id })

    data class GroupEntry(
        val site: RecentSite,
        val newest: HistoryItem,
    )

    val entries = groups.map { (key, items) ->
        val newest = items.maxWith(comparator)
        val title = newest.title.trim().ifEmpty { key }
        GroupEntry(
            site = RecentSite(
                key = key,
                host = key,
                title = title,
                url = newest.url,
                lastVisit = newest.ts,
                historyIds = items.map { it.id },
            ),
            newest = newest,
        )
    }

    return entries.sortedWith { a, b ->
        val tsCmp = b.newest.ts.compareTo(a.newest.ts)
        if (tsCmp != 0) {
            tsCmp
        } else {
            b.newest.id.compareTo(a.newest.id)
        }
    }.take(limit).map { it.site }
}
