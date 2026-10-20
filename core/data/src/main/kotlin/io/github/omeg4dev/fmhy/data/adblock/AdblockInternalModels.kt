package io.github.omeg4dev.fmhy.data.adblock

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class NetworkRule(
    val id: Int,
    val rawRule: String,
    val isException: Boolean,
    val isImportant: Boolean,
    val party: Int, // 0 = any, 1 = 1p, 2 = 3p
    val typesMask: Int,
    val redirectResource: String?,
    val isRedirectRule: Boolean,
    val blocksDocument: Boolean,
    val caseSensitive: Boolean,
    val domains: List<String>,
    val toDomains: List<String>,
    val denyAllowDomains: List<String>,
    val isPureHost: Boolean,
    val pureHost: String?,
    val isRegex: Boolean,
    val regexPattern: String?,
    val pattern: String,
    val token: String?,
    var compiledRegex: Regex? = null,
    var lastMatchGen: Int = 0
) {
    fun canonicalKey(): String {
        return "$isException|$pattern|$party|$typesMask|$domains|$toDomains|$denyAllowDomains"
    }
}

internal data class CosmeticRule(
    val domains: List<String>,
    val selector: String
)

internal data class ScriptletRule(
    val domains: List<String>,
    val name: String,
    val args: List<String>
)

internal data class ScriptletException(
    val domains: List<String>,
    val name: String? // null = disables all scriptlets for domain (#@#+js())
)

internal object DomainUtils {
    fun extractHost(url: String): String? {
        val start = url.indexOf("://")
        val hostStart = if (start != -1) start + 3 else 0
        var hostEnd = url.indexOf('/', hostStart)
        if (hostEnd == -1) hostEnd = url.indexOf('?', hostStart)
        if (hostEnd == -1) hostEnd = url.indexOf('#', hostStart)
        if (hostEnd == -1) hostEnd = url.length
        val hostPort = url.substring(hostStart, hostEnd)
        val colon = hostPort.indexOf(':')
        val host = if (colon != -1) hostPort.substring(0, colon) else hostPort
        return host.lowercase().takeIf { it.isNotEmpty() }
    }

    private val topDomainCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun getTopPrivateDomain(host: String): String {
        return topDomainCache.computeIfAbsent(host) { h ->
            try {
                val httpUrl = "http://$h/".toHttpUrlOrNull()
                httpUrl?.topPrivateDomain() ?: h
            } catch (_: Exception) {
                h
            }
        }
    }

    fun matchesDomainConstraints(
        host: String?,
        constraints: List<String>,
        topDomain: String = if (host != null) getTopPrivateDomain(host) else ""
    ): Boolean {
        if (host == null || constraints.isEmpty()) return true
        var hasPositive = false
        var positiveMatched = false

        for (c in constraints) {
            if (c.startsWith("~")) {
                val domain = c.substring(1)
                if (matchesDomainOrEntity(host, domain, topDomain)) {
                    return false
                }
            } else {
                hasPositive = true
                if (!positiveMatched && matchesDomainOrEntity(host, c, topDomain)) {
                    positiveMatched = true
                }
            }
        }

        if (hasPositive) {
            return positiveMatched
        }
        return true
    }

    fun matchesDomainOrEntity(
        host: String,
        domainPattern: String,
        topDomain: String = getTopPrivateDomain(host)
    ): Boolean {
        if (domainPattern.endsWith(".*")) {
            val baseEntity = domainPattern.removeSuffix(".*")
            val label = topDomain.substringBefore('.')
            if (label.equals(baseEntity, ignoreCase = true)) return true
            if (host.equals(baseEntity, ignoreCase = true) || host.startsWith("$baseEntity.", ignoreCase = true)) {
                return true
            }
            return false
        }

        val d = domainPattern.lowercase()
        val h = host.lowercase()
        return h == d || h.endsWith(".$d")
    }

    fun extractParentDomains(host: String): List<String> {
        val results = mutableListOf<String>()
        var cur = host
        results.add(cur)
        var dot = cur.indexOf('.')
        while (dot != -1 && dot < cur.length - 1) {
            cur = cur.substring(dot + 1)
            results.add(cur)
            dot = cur.indexOf('.')
        }
        return results
    }
}
