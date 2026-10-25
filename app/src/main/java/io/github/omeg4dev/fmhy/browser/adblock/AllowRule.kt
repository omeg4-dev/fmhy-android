package io.github.omeg4dev.fmhy.browser.adblock

import io.github.omeg4dev.fmhy.browser.PopupPolicy
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object AllowRule {
    fun pageSite(pageUrl: String): String? {
        val httpUrl = pageUrl.toHttpUrlOrNull() ?: return null
        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") return null
        val domain = PopupPolicy.getTopPrivateDomain(httpUrl)
        return domain.ifBlank { null }?.lowercase()
    }

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
}
