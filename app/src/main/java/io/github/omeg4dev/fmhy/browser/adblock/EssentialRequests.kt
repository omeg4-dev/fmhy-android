package io.github.omeg4dev.fmhy.browser.adblock

import io.github.omeg4dev.fmhy.browser.PopupPolicy
import io.github.omeg4dev.fmhy.data.adblock.ResourceType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Determines whether a web resource request is essential to page operation or human verification,
 * and therefore must be exempted from generic ad blocking rules.
 */
object EssentialRequests {

    val ESSENTIAL_TYPES = setOf(
        ResourceType.DOCUMENT,
        ResourceType.SUBDOCUMENT,
        ResourceType.SCRIPT,
        ResourceType.STYLESHEET,
        ResourceType.XHR,
        ResourceType.WEBSOCKET,
        ResourceType.FONT,
        ResourceType.OTHER,
    )

    private val VERIFICATION_HOSTS = setOf(
        "challenges.cloudflare.com",
        "hcaptcha.com",
        "recaptcha.net",
        "arkoselabs.com",
        "funcaptcha.com",
        "geetest.com",
        "geevisit.com",
    )

    private val RULE_DELIMITERS = charArrayOf('^', '/', '*', '$', ':')

    /**
     * Returns true if the URL belongs to a known human-verification or captcha provider.
     */
    fun isVerification(url: String): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        val host = httpUrl.host.lowercase()
        val path = httpUrl.encodedPath

        if (VERIFICATION_HOSTS.any { host == it || host.endsWith(".$it") }) {
            return true
        }

        if ((host == "www.google.com" || host == "google.com") && path.startsWith("/recaptcha/")) {
            return true
        }
        if (host == "www.gstatic.com" && path.startsWith("/recaptcha/")) {
            return true
        }
        if (host == "g.alicdn.com" && path.startsWith("/captcha-frontend/")) {
            return true
        }

        if (host.endsWith(".captcha-open.aliyuncs.com") || host == "captcha-open.aliyuncs.com" ||
            host.endsWith(".captcha.aliyuncs.com") || host == "captcha.aliyuncs.com"
        ) {
            return true
        }

        return false
    }

    /**
     * Returns true if the rule specifically targets the given page domain (eTLD+1).
     */
    fun isSiteSpecificRule(rule: String, pageDomain: String): Boolean {
        val normalizedPageDomain = pageDomain.trim().lowercase()
        if (normalizedPageDomain.isEmpty()) return false

        val lastDollar = rule.lastIndexOf('$')
        val pattern = if (lastDollar != -1) rule.substring(0, lastDollar) else rule
        val options = if (lastDollar != -1) rule.substring(lastDollar + 1) else ""

        if (pattern.startsWith("||")) {
            val after = pattern.substring(2)
            val delimIndex = after.indexOfAny(RULE_DELIMITERS)
            val rawHost = if (delimIndex != -1) after.substring(0, delimIndex) else after
            if (rawHost.isNotEmpty()) {
                val hostDomain = domainOfHost(rawHost)
                if (hostDomain.equals(normalizedPageDomain, ignoreCase = true)) {
                    return true
                }
            }
        }

        if (options.isNotEmpty()) {
            val optionList = options.split(',')
            for (opt in optionList) {
                val trimmedOpt = opt.trim()
                val value = when {
                    trimmedOpt.startsWith("domain=", ignoreCase = true) ->
                        trimmedOpt.substring("domain=".length)
                    trimmedOpt.startsWith("from=", ignoreCase = true) ->
                        trimmedOpt.substring("from=".length)
                    else -> null
                }
                if (value != null) {
                    val entries = value.split('|')
                    for (entry in entries) {
                        val trimmedEntry = entry.trim()
                        if (trimmedEntry.startsWith("~")) continue
                        if (trimmedEntry.isEmpty()) continue
                        val entryDomain = domainOfHost(trimmedEntry)
                        if (entryDomain.equals(normalizedPageDomain, ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    /**
     * Returns true if a blocked or redirected request is essential and should be allowed through.
     */
    fun exempt(requestUrl: String, pageUrl: String, type: ResourceType, rule: String): Boolean {
        if (isVerification(requestUrl)) return true
        if (type !in ESSENTIAL_TYPES) return false

        val reqHttpUrl = requestUrl.toHttpUrlOrNull() ?: return false
        val pageHttpUrl = pageUrl.toHttpUrlOrNull() ?: return false

        val reqDomain = PopupPolicy.getTopPrivateDomain(reqHttpUrl)
        val pageDomain = PopupPolicy.getTopPrivateDomain(pageHttpUrl)
        if (!reqDomain.equals(pageDomain, ignoreCase = true)) return false

        return !isSiteSpecificRule(rule, pageDomain.lowercase())
    }

    private fun domainOfHost(host: String): String {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return ""
        val httpUrl = "https://$trimmed/".toHttpUrlOrNull()
        return if (httpUrl != null) {
            PopupPolicy.getTopPrivateDomain(httpUrl).lowercase()
        } else {
            trimmed.lowercase()
        }
    }
}
