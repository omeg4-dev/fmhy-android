package io.github.omeg4dev.fmhy.browser.adblock

import io.github.omeg4dev.fmhy.browser.PopupPolicy
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object AllowRule {
    fun pageSite(pageUrl: String): String? {
        if (pageUrl.isBlank()) return null
        val url = if (!pageUrl.contains("://") && !pageUrl.startsWith("about:") && !pageUrl.startsWith("data:")) {
            "https://$pageUrl"
        } else {
            pageUrl
        }
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") return null
        val domain = PopupPolicy.getTopPrivateDomain(httpUrl)
        return domain.ifBlank { null }?.lowercase()
    }

    fun pageSiteFromHost(host: String): String? = pageSite(host)

    fun forHost(host: String, pageSite: String): String =
        "@@||${host.lowercase()}^\$domain=${pageSite.lowercase()}"

    fun append(customRules: String, rule: String): String {
        val trimmedRule = rule.trim()
        if (customRules.lineSequence().any { it.trim() == trimmedRule }) {
            return customRules
        }
        val trimmed = customRules.trimEnd()
        return if (trimmed.isEmpty()) rule else "$trimmed\n$rule"
    }

    fun bypassKey(pageSite: String, host: String): String =
        "${pageSite.lowercase()} ${host.lowercase()}"

    fun allowedHostsFor(customRules: String, site: String): List<String> {
        if (site.isBlank()) return emptyList()
        val targetSite = site.lowercase()
        val prefix = "@@||"
        val marker = "^\$domain="
        return customRules.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith(prefix) && it.contains(marker) }
            .mapNotNull { line ->
                val afterPrefix = line.removePrefix(prefix)
                val host = afterPrefix.substringBefore(marker).trim()
                val domain = afterPrefix.substringAfter(marker).trim()
                if (domain.equals(targetSite, ignoreCase = true) && host.isNotEmpty()) {
                    host.lowercase()
                } else {
                    null
                }
            }
            .distinct()
            .sorted()
            .toList()
    }

    fun remove(customRules: String, host: String, site: String): String {
        val target = forHost(host, site)
        return customRules.lines()
            .filter { it.trim().lowercase() != target.lowercase() }
            .joinToString("\n")
    }
}
