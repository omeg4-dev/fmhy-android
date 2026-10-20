// Ported from FMHY-SafeGuard (GPL-3.0), src/js/catalogue.js, resources.js, config.js, site-metadata.js
package io.github.omeg4dev.fmhy.data.safety

import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.Entry
import java.net.URI

enum class SafetyStatus {
    FMHY_SITE,
    STARRED,
    LISTED,
    POTENTIALLY_UNSAFE,
    UNSAFE,
    SEARCH_ENGINE,
    NO_DATA,
    INVALID,
}

data class Verdict(
    val status: SafetyStatus,
    val input: String,
    val normalizedUrl: String?,
    val host: String?,
    val reason: String?,
    val matches: List<Entry>,
)

class SafeGuard(private val store: DatasetStore) {

    suspend fun check(input: String): Verdict {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Verdict(SafetyStatus.INVALID, input, null, null, null, emptyList())
        }

        val urlWithScheme = if (!trimmed.contains("://")) "https://$trimmed" else trimmed
        val parsedUri = try {
            val u = URI(urlWithScheme)
            if (u.host == null) null else u
        } catch (_: Exception) {
            null
        } ?: return Verdict(SafetyStatus.INVALID, input, null, null, null, emptyList())

        val rawHost = parsedUri.host?.trim() ?: return Verdict(SafetyStatus.INVALID, input, null, null, null, emptyList())
        if (rawHost.isEmpty() || rawHost.contains(' ') || rawHost.contains('%')) {
            return Verdict(SafetyStatus.INVALID, input, null, null, null, emptyList())
        }

        // Must have at least one dot unless localhost
        if (!rawHost.contains('.') && !rawHost.equals("localhost", ignoreCase = true)) {
            return Verdict(SafetyStatus.INVALID, input, null, null, null, emptyList())
        }

        val host = rawHost.lowercase().removePrefix("www.")
        val normalizedUrl = normalizeUrl(urlWithScheme)
            ?: return Verdict(SafetyStatus.INVALID, input, null, null, null, emptyList())

        // 1. Search engine check (never rated)
        if (isSearchEngine(host)) {
            return Verdict(SafetyStatus.SEARCH_ENGINE, input, normalizedUrl, host, null, emptyList())
        }

        val path = (parsedUri.path ?: "").lowercase().trimEnd('/')

        // 2. Prepare candidate keys for site_lists
        val candidateKeys = mutableListOf<String>()

        // Path prefixes for shared hosts (and any path-specific listings)
        if (path.isNotEmpty()) {
            val segments = path.split('/').filter { it.isNotEmpty() }
            for (i in segments.size downTo 1) {
                candidateKeys.add("$host/" + segments.take(i).joinToString("/"))
            }
        }
        candidateKeys.add(host)

        // Subdomain candidates for non-shared hosts
        if (!isSharedResourceHost(host)) {
            val labels = host.split('.')
            for (i in 1 until labels.size - 1) {
                val parentHost = labels.drop(i).joinToString(".")
                if (SUBDOMAIN_HOSTING_HOSTS.contains(parentHost)) break
                candidateKeys.add(parentHost)
            }
        }

        val siteListRows = store.siteListEntries(candidateKeys.distinct())

        // Find candidate matches in entries table
        val candidateHosts = mutableListOf(host)
        if (!isSharedResourceHost(host)) {
            val labels = host.split('.')
            for (i in 1 until labels.size - 1) {
                val parentHost = labels.drop(i).joinToString(".")
                if (SUBDOMAIN_HOSTING_HOSTS.contains(parentHost)) break
                candidateHosts.add(parentHost)
            }
        }

        val entryCandidates = mutableListOf<Entry>()
        val seenEntryIds = mutableSetOf<String>()
        for (h in candidateHosts.distinct()) {
            val rows = store.entriesMatchingHost(h)
            for (e in rows) {
                if (seenEntryIds.add(e.id)) {
                    entryCandidates.add(e)
                }
            }
        }

        val matchingEntries = entryCandidates
            .filter { urlMatchesListedResource(urlWithScheme, it.url) }
            .sortedWith(compareByDescending<Entry> { it.tier.value }.thenBy { it.ord })

        // SafeGuard classification priority order
        // A. unsafe from site_lists
        val unsafeRow = siteListRows.firstOrNull { it.list == "unsafe" }
        if (unsafeRow != null) {
            val reason = siteListRows.filter { it.list == "unsafe" && it.reason.isNotEmpty() }
                .maxByOrNull { it.key.length }?.reason
                ?: store.unsafeReason(normalizedUrl)
            return Verdict(SafetyStatus.UNSAFE, input, normalizedUrl, host, reason, matchingEntries)
        }

        // B. potentially_unsafe from site_lists
        val potentiallyUnsafeRow = siteListRows.firstOrNull { it.list == "potentially_unsafe" }
        if (potentiallyUnsafeRow != null) {
            val reason = siteListRows.filter { it.list == "potentially_unsafe" && it.reason.isNotEmpty() }
                .maxByOrNull { it.key.length }?.reason
                ?: store.unsafeReason(normalizedUrl)
            return Verdict(SafetyStatus.POTENTIALLY_UNSAFE, input, normalizedUrl, host, reason, matchingEntries)
        }

        // C. fmhy from site_lists
        val fmhyRow = siteListRows.firstOrNull { it.list == "fmhy" }
        if (fmhyRow != null) {
            return Verdict(SafetyStatus.FMHY_SITE, input, normalizedUrl, host, null, matchingEntries)
        }

        // D. Starred / Listed from entries table
        if (matchingEntries.isNotEmpty()) {
            val isStarred = matchingEntries.any { it.tier.value >= 1 }
            val status = if (isStarred) SafetyStatus.STARRED else SafetyStatus.LISTED
            return Verdict(status, input, normalizedUrl, host, null, matchingEntries)
        }

        // E. Fallback unsafeReason from unsafe_domains
        val unsafeReason = store.unsafeReason(normalizedUrl)
        if (unsafeReason != null) {
            return Verdict(SafetyStatus.UNSAFE, input, normalizedUrl, host, unsafeReason, emptyList())
        }

        // F. No data
        return Verdict(SafetyStatus.NO_DATA, input, normalizedUrl, host, null, emptyList())
    }

    companion object {
        val SUBDOMAIN_HOSTING_HOSTS = setOf("neocities.org")

        val SHARED_RESOURCE_HOSTS = setOf(
            "github.com",
            "gist.github.com",
            "raw.githubusercontent.com",
            "greasyfork.org",
            "youtube.com",
            "chromewebstore.google.com",
            "colab.research.google.com",
            "modrinth.com",
            "f-droid.org",
            "xdaforums.com",
            "start.me",
            "sites.google.com",
            "matrix.to",
            "codepen.io",
            "vk.com",
            "gitlab.com",
            "codeberg.org",
            "sourceforge.net",
            "linktr.ee",
            "rentry.co",
            "rentry.org",
            "pastebin.com",
            "archive.org",
            "drive.google.com",
            "docs.google.com",
            "discord.com",
            "discord.gg",
            "t.me",
            "mega.nz",
            "mediafire.com",
            "gofile.io",
            "pixeldrain.com",
            "huggingface.co",
        )

        val SEARCH_ENGINES = listOf(
            "google.com",
            "bing.com",
            "duckduckgo.com",
            "kagi.com",
            "librey.org",
            "4get.ca",
            "mojeek.com",
            "qwant.com",
            "swisscows.com",
            "yacy.net",
            "startpage.com",
            "search.brave.com",
            "ekoru.org",
            "gibiru.com",
            "searx.org",
            "searx.",
            "searxng.",
            "whoogle.",
            "metager.org",
            "ecosia.org",
            "yandex.com",
            "yandex.",
            "yahoo.com",
            "yahoo.",
            "baidu.com",
            "naver.com",
            "seznam.cz",
        )

        fun isSearchEngine(hostname: String): Boolean {
            val domain = hostname.lowercase().removePrefix("www.")
            return SEARCH_ENGINES.any { se ->
                if (se.endsWith('.')) {
                    domain.startsWith(se) || domain.contains(".$se") || domain == se.dropLast(1)
                } else {
                    domain == se || domain.endsWith(".$se")
                }
            }
        }

        fun isSharedResourceHost(hostname: String): Boolean {
            val domain = hostname.lowercase().removePrefix("www.")
            return SHARED_RESOURCE_HOSTS.any { host ->
                domain == host || domain.endsWith(".$host")
            }
        }

        fun normalizeUrl(url: String): String? {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return null
            val source = if (!trimmed.contains("://")) "https://$trimmed" else trimmed
            return try {
                val uri = URI(source)
                val scheme = (uri.scheme ?: "https").lowercase()
                val rawHost = uri.host ?: return null
                val host = rawHost.lowercase().removePrefix("www.")
                val rawPath = (uri.path ?: "").trimEnd('/')
                "$scheme://$host$rawPath"
            } catch (_: Exception) {
                null
            }
        }

        fun normalizeResourceUrl(url: String): String? {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return null
            val source = if (!trimmed.contains("://")) "https://$trimmed" else trimmed
            return try {
                val uri = URI(source)
                val scheme = (uri.scheme ?: "https").lowercase()
                val rawHost = uri.host ?: return null
                val host = rawHost.lowercase().removePrefix("www.")
                val rawPath = (uri.path ?: "").trimEnd('/')
                val path = if (rawPath.isEmpty()) "/" else rawPath
                val query = if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
                val fragment = if (uri.rawFragment != null) "#${uri.rawFragment}" else ""
                val full = "$scheme://$host$path$query$fragment"
                if (query.isEmpty() && fragment.isEmpty() && full.endsWith('/') && path != "/") {
                    full.trimEnd('/')
                } else {
                    full
                }
            } catch (_: Exception) {
                null
            }
        }

        fun urlMatchesListedResource(currentUrl: String, listedUrl: String): Boolean {
            val curNorm = normalizeResourceUrl(currentUrl) ?: return false
            val listNorm = normalizeResourceUrl(listedUrl) ?: return false

            val curUri = try { URI(curNorm) } catch (_: Exception) { return false }
            val listUri = try { URI(listNorm) } catch (_: Exception) { return false }

            val currentHost = curUri.host?.lowercase()?.removePrefix("www.") ?: return false
            val listedHost = listUri.host?.lowercase()?.removePrefix("www.") ?: return false

            val sharedHost = isSharedResourceHost(currentHost) || isSharedResourceHost(listedHost)
            val hostMatches = if (sharedHost || SUBDOMAIN_HOSTING_HOSTS.contains(listedHost)) {
                currentHost == listedHost
            } else {
                currentHost == listedHost || currentHost.endsWith(".$listedHost")
            }
            if (!hostMatches) return false

            val currentPath = (curUri.path ?: "").lowercase().trimEnd('/')
            val listedPath = (listUri.path ?: "").lowercase().trimEnd('/')

            // GreasyFork script match
            val curGfMatch = Regex("""^/(?:[^/]+/)?scripts/(\d+)""").find(currentPath)
            val listGfMatch = Regex("""^/(?:[^/]+/)?scripts/(\d+)""").find(listedPath)
            if (currentHost == "greasyfork.org" && listedHost == "greasyfork.org" &&
                curGfMatch != null && listGfMatch != null && curGfMatch.groupValues[1] == listGfMatch.groupValues[1]
            ) {
                return true
            }

            // Gist platform pages
            if (currentHost == "gist.github.com" && listedHost == "gist.github.com" &&
                (currentPath == "/starred" || currentPath == "/discover") && listedPath.isEmpty()
            ) {
                return true
            }

            val pathMatches = if (listedPath.isEmpty() || listedPath == "/") {
                !sharedHost || currentPath.isEmpty() || currentPath == "/"
            } else {
                currentPath == listedPath || currentPath.startsWith("$listedPath/")
            }
            if (!pathMatches) return false

            // Query matching for youtube / archive
            if (listedHost == "youtube.com" || listedHost == "archive.org") {
                val curQuery = parseQueryParams(curUri.rawQuery)
                val listQuery = parseQueryParams(listUri.rawQuery)
                for ((k, v) in listQuery) {
                    if (curQuery[k] != v) return false
                }
            }

            // Fragment matching for rentry / matrix
            if (listedHost == "rentry.co" || listedHost == "rentry.org" || listedHost == "matrix.to") {
                val curFrag = curUri.fragment
                val listFrag = listUri.fragment
                if (!listFrag.isNullOrEmpty()) {
                    if (curFrag == null || !curFrag.equals(listFrag, ignoreCase = true)) {
                        return false
                    }
                }
            }

            return true
        }

        private fun parseQueryParams(query: String?): Map<String, String> {
            if (query.isNullOrEmpty()) return emptyMap()
            val result = mutableMapOf<String, String>()
            for (param in query.split('&')) {
                val parts = param.split('=', limit = 2)
                if (parts.isNotEmpty()) {
                    result[parts[0]] = if (parts.size > 1) parts[1] else ""
                }
            }
            return result
        }
    }
}
