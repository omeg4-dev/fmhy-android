// Ported from FMHY-SafeGuard (GPL-3.0), src/js/catalogue.js, resources.js, config.js, site-metadata.js
package io.github.omeg4dev.fmhy.data.safety

import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.Entry
import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class SafetyStatus {
    FMHY_SITE,
    STARRED,
    LISTED,
    DOMAIN_LISTED,
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
    val via: Via? = null,
    val lookalikes: List<Entry> = emptyList(),
    val sharedHostListedCount: Int = 0,
)

class SafeGuard(private val store: DatasetStore) {

    suspend fun check(input: String): Verdict {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Verdict(SafetyStatus.INVALID, input, null, null, null, emptyList())
        }

        val urlWithScheme = if (!trimmed.contains("://")) "https://$trimmed" else trimmed
        val rawHost = try {
            val u = URI(urlWithScheme)
            u.host ?: urlWithScheme.substringAfter("://").substringBefore('/').substringBefore(':').substringBefore('?')
        } catch (_: Exception) {
            urlWithScheme.substringAfter("://").substringBefore('/').substringBefore(':').substringBefore('?')
        }.trim()

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

        val path = (try { URI(urlWithScheme).path } catch (_: Exception) { null }
            ?: ("/" + urlWithScheme.substringAfter("://").substringAfter('/', ""))).lowercase().trimEnd('/')

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

        val allHostMatches = mutableListOf<HostMatch>()
        val seenMatchKeys = mutableSetOf<String>()
        for (h in candidateHosts.distinct()) {
            val rows = store.entryMatchesForHost(h)
            for (m in rows) {
                if (seenMatchKeys.add("${m.entry.id}:${m.matchedUrl}:${m.via}")) {
                    allHostMatches.add(m)
                }
            }
        }

        val exactMatches = allHostMatches
            .filter { urlMatchesListedResource(urlWithScheme, it.matchedUrl) }
            .sortedWith(compareByDescending<HostMatch> { it.entry.tier.value }.thenBy { it.entry.ord })
        val exactMatchingEntries = exactMatches.map { it.entry }.distinctBy { it.id }

        // SafeGuard classification priority order
        // A. unsafe from site_lists
        val unsafeRow = siteListRows.firstOrNull { it.list == "unsafe" }
        if (unsafeRow != null) {
            val reason = siteListRows.filter { it.list == "unsafe" && it.reason.isNotEmpty() }
                .maxByOrNull { it.key.length }?.reason
                ?: store.unsafeReason(normalizedUrl)
            return Verdict(SafetyStatus.UNSAFE, input, normalizedUrl, host, reason, exactMatchingEntries)
        }

        // B. potentially_unsafe from site_lists
        val potentiallyUnsafeRow = siteListRows.firstOrNull { it.list == "potentially_unsafe" }
        if (potentiallyUnsafeRow != null) {
            val reason = siteListRows.filter { it.list == "potentially_unsafe" && it.reason.isNotEmpty() }
                .maxByOrNull { it.key.length }?.reason
                ?: store.unsafeReason(normalizedUrl)
            return Verdict(SafetyStatus.POTENTIALLY_UNSAFE, input, normalizedUrl, host, reason, exactMatchingEntries)
        }

        // C. fmhy from site_lists
        val fmhyRow = siteListRows.firstOrNull { it.list == "fmhy" }
        if (fmhyRow != null) {
            return Verdict(SafetyStatus.FMHY_SITE, input, normalizedUrl, host, null, exactMatchingEntries)
        }

        // D. Starred / Listed exact match from entries table
        if (exactMatches.isNotEmpty()) {
            val isStarred = exactMatches.any { it.entry.tier.value >= 1 }
            val status = if (isStarred) SafetyStatus.STARRED else SafetyStatus.LISTED
            val bestMatch = exactMatches.first()
            return Verdict(
                status = status,
                input = input,
                normalizedUrl = normalizedUrl,
                host = host,
                reason = null,
                matches = exactMatchingEntries,
                via = bestMatch.via,
            )
        }

        // E. DOMAIN_LISTED (host or subdomain matches an entry but path does not, or match came from a subdomain)
        // Shared hosts (github.com, etc.) are never DOMAIN_LISTED
        val isShared = isSharedResourceHost(host)
        val sharedHostListedCount = if (isShared) {
            allHostMatches.map { it.entry }.distinctBy { it.id }.size
        } else {
            0
        }

        if (!isShared && allHostMatches.isNotEmpty()) {
            val domainEntries = allHostMatches
                .map { it.entry }
                .distinctBy { it.id }
                .sortedWith(compareByDescending<Entry> { it.tier.value }.thenBy { it.ord })
            val lookalikes = findLookalikes(host)
            return Verdict(
                status = SafetyStatus.DOMAIN_LISTED,
                input = input,
                normalizedUrl = normalizedUrl,
                host = host,
                reason = null,
                matches = domainEntries,
                via = null,
                lookalikes = lookalikes,
            )
        }

        // F. Fallback unsafeReason from unsafe_domains
        val unsafeReason = store.unsafeReason(normalizedUrl)
        if (unsafeReason != null) {
            return Verdict(SafetyStatus.UNSAFE, input, normalizedUrl, host, unsafeReason, emptyList())
        }

        // G. No data
        val lookalikes = findLookalikes(host)
        return Verdict(
            status = SafetyStatus.NO_DATA,
            input = input,
            normalizedUrl = normalizedUrl,
            host = host,
            reason = null,
            matches = emptyList(),
            via = null,
            lookalikes = lookalikes,
            sharedHostListedCount = sharedHostListedCount,
        )
    }

    private suspend fun findLookalikes(host: String): List<Entry> {
        // A repo on github.com or a page on a hosting site says nothing about look-alike domains.
        if (isSharedResourceHost(host) || SUBDOMAIN_HOSTING_HOSTS.any { host == it || host.endsWith(".$it") }) return emptyList()
        val targetLabel = extractMainLabel(host)
        if (targetLabel.length < 4) return emptyList()

        val candidates = store.findLookalikeCandidates(targetLabel)
        val cleanHost = host.lowercase().removePrefix("www.")

        data class RankedLookalike(val entry: Entry, val score: Int)
        val ranked = mutableListOf<RankedLookalike>()
        val seenIds = mutableSetOf<String>()

        for (entry in candidates) {
            val cHost = extractHostFromUrl(entry.url) ?: continue
            if (cHost == cleanHost) continue // Exclude entries on the same host
            if (isSharedResourceHost(cHost) || SUBDOMAIN_HOSTING_HOSTS.any { cHost == it || cHost.endsWith(".$it") }) continue

            val candidateLabel = extractMainLabel(cHost)
            if (candidateLabel.length < 4) continue

            val score = when {
                candidateLabel == targetLabel -> 0
                candidateLabel.contains(targetLabel) || targetLabel.contains(candidateLabel) -> 1
                targetLabel.length >= 6 && candidateLabel.length >= 6 && levenshteinDistance(targetLabel, candidateLabel) <= 2 -> 2
                else -> -1
            }

            if (score >= 0 && seenIds.add(entry.id)) {
                ranked.add(RankedLookalike(entry, score))
            }
        }

        return ranked.sortedWith(
            compareBy<RankedLookalike> { it.score }
                .thenByDescending { it.entry.tier.value }
                .thenBy { it.entry.ord }
        ).take(3).map { it.entry }
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
                val rawHost = uri.host ?: source.substringAfter("://").substringBefore('/').substringBefore(':').substringBefore('?')
                if (rawHost.isEmpty()) return null
                val host = rawHost.lowercase().removePrefix("www.")
                val rawPath = (uri.path ?: ("/" + source.substringAfter("://").substringAfter('/', ""))).trimEnd('/')
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
                val rawHost = uri.host ?: source.substringAfter("://").substringBefore('/').substringBefore(':').substringBefore('?')
                if (rawHost.isEmpty()) return null
                val host = rawHost.lowercase().removePrefix("www.")
                val rawPath = (uri.path ?: ("/" + source.substringAfter("://").substringAfter('/', ""))).trimEnd('/')
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

            val currentHost = extractHostFromUrl(curNorm) ?: return false
            val listedHost = extractHostFromUrl(listNorm) ?: return false

            val sharedHost = isSharedResourceHost(currentHost) || isSharedResourceHost(listedHost)
            val hostMatches = if (sharedHost || SUBDOMAIN_HOSTING_HOSTS.contains(listedHost)) {
                currentHost == listedHost
            } else {
                currentHost == listedHost || currentHost.endsWith(".$listedHost")
            }
            if (!hostMatches) return false

            val curUri = try { URI(curNorm) } catch (_: Exception) { null }
            val listUri = try { URI(listNorm) } catch (_: Exception) { null }

            val currentPath = (curUri?.path ?: ("/" + curNorm.substringAfter("://").substringAfter('/', ""))).lowercase().trimEnd('/')
            val listedPath = (listUri?.path ?: ("/" + listNorm.substringAfter("://").substringAfter('/', ""))).lowercase().trimEnd('/')

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
                val curQuery = parseQueryParams(curUri?.rawQuery)
                val listQuery = parseQueryParams(listUri?.rawQuery)
                for ((k, v) in listQuery) {
                    if (curQuery[k] != v) return false
                }
            }

            // Fragment matching for rentry / matrix
            if (listedHost == "rentry.co" || listedHost == "rentry.org" || listedHost == "matrix.to") {
                val curFrag = curUri?.fragment
                val listFrag = listUri?.fragment
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

        fun extractHostFromUrl(url: String): String? {
            val clean = url.trim()
            val rawHost = try {
                val u = if (!clean.contains("://")) "https://$clean" else clean
                URI(u).host
            } catch (_: Exception) {
                null
            } ?: clean.substringAfter("://").substringBefore('/').substringBefore(':').substringBefore('?')
            return rawHost.lowercase().removePrefix("www.")
        }

        fun extractMainLabel(host: String): String {
            val clean = host.lowercase().removePrefix("www.")
            val regDomain = try {
                val httpUrl = "http://$clean/".toHttpUrlOrNull()
                httpUrl?.topPrivateDomain() ?: clean
            } catch (_: Exception) {
                clean
            }
            val domainPart = if (regDomain.contains('.')) {
                regDomain.substringBefore('.')
            } else {
                regDomain
            }
            val tokens = domainPart.split('-', '_').filter { it.isNotEmpty() }
            return tokens.maxByOrNull { it.length } ?: domainPart
        }

        fun levenshteinDistance(s1: String, s2: String): Int {
            if (s1 == s2) return 0
            if (s1.isEmpty()) return s2.length
            if (s2.isEmpty()) return s1.length

            val prev = IntArray(s2.length + 1) { it }
            val curr = IntArray(s2.length + 1)

            for (i in s1.indices) {
                curr[0] = i + 1
                for (j in s2.indices) {
                    val cost = if (s1[i] == s2[j]) 0 else 1
                    curr[j + 1] = minOf(
                        curr[j] + 1,
                        prev[j + 1] + 1,
                        prev[j] + cost
                    )
                }
                System.arraycopy(curr, 0, prev, 0, curr.size)
            }
            return prev[s2.length]
        }
    }
}
