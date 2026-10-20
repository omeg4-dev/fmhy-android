package io.github.omeg4dev.fmhy.browser

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface RedirectDecision {
    data object Allow : RedirectDecision
    data object BlockJavascript : RedirectDecision
    data class BlockRedirect(val host: String, val targetUrl: String) : RedirectDecision
    data class BlockAppLink(val url: String, val fallbackUrl: String?) : RedirectDecision
    data class AskAppLink(val url: String, val fallbackUrl: String?) : RedirectDecision
}

object RedirectPolicy {
    private val HTTP_SCHEMES = setOf("http", "https", "about", "data", "blob")
    private val TWO_PART_TLDS = setOf("co.uk", "com.au", "org.uk", "gov.uk", "net.au", "co.jp", "com.br", "co.nz", "co.in")

    fun getTopPrivateDomain(httpUrl: HttpUrl): String {
        val domain = try {
            httpUrl.topPrivateDomain()
        } catch (_: Throwable) {
            null
        }
        if (domain != null) return domain

        val host = httpUrl.host
        val parts = host.split('.')
        if (parts.size <= 2) return host
        val lastTwo = "${parts[parts.size - 2]}.${parts.last()}"
        return if (TWO_PART_TLDS.contains(lastTwo) && parts.size >= 3) {
            "${parts[parts.size - 3]}.$lastTwo"
        } else {
            lastTwo
        }
    }

    fun isSameSite(url1: String, url2: String): Boolean {
        val u1 = url1.toHttpUrlOrNull() ?: return false
        val u2 = url2.toHttpUrlOrNull() ?: return false
        val domain1 = getTopPrivateDomain(u1)
        val domain2 = getTopPrivateDomain(u2)
        return domain1.equals(domain2, ignoreCase = true)
    }

    fun extractFallbackUrl(intentUriString: String): String? {
        return try {
            val match = Regex("[;?]S\\.browser_fallback_url=([^;]+)").find(intentUriString)
            match?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        } catch (_: Exception) {
            null
        }
    }

    fun evaluate(
        targetUrl: String,
        currentUrl: String,
        isForMainFrame: Boolean,
        hasGesture: Boolean,
        isRedirect: Boolean,
        isFirstLoad: Boolean,
        redirectGuardEnabled: Boolean,
        blockAppLinksEnabled: Boolean,
        isAllowedOnce: Boolean = false,
    ): RedirectDecision {
        if (isAllowedOnce) {
            return RedirectDecision.Allow
        }

        val targetHttpUrl = targetUrl.toHttpUrlOrNull()
        val scheme = targetHttpUrl?.scheme ?: targetUrl.substringBefore(':').lowercase()

        // 1. Check scheme
        if (scheme == "javascript") {
            return RedirectDecision.BlockJavascript
        }

        if (!HTTP_SCHEMES.contains(scheme)) {
            val fallback = extractFallbackUrl(targetUrl)
            return if (blockAppLinksEnabled) {
                RedirectDecision.BlockAppLink(targetUrl, fallback)
            } else {
                RedirectDecision.AskAppLink(targetUrl, fallback)
            }
        }

        // Subframe navigations are not redirected-guarded here
        if (!isForMainFrame) {
            return RedirectDecision.Allow
        }

        // The first load of a tab is never blocked
        if (isFirstLoad || currentUrl.isBlank() || currentUrl == "about:blank" || currentUrl == targetUrl) {
            return RedirectDecision.Allow
        }

        // Ignore server-side 3xx redirects: request.isRedirect -> allow
        if (isRedirect) {
            return RedirectDecision.Allow
        }

        // User gesture allows navigation
        if (hasGesture) {
            return RedirectDecision.Allow
        }

        // Same-site navigations are always allowed
        if (isSameSite(currentUrl, targetUrl)) {
            return RedirectDecision.Allow
        }

        // If redirectGuard on:
        if (redirectGuardEnabled) {
            val host = targetHttpUrl?.host ?: targetUrl
            return RedirectDecision.BlockRedirect(host = host, targetUrl = targetUrl)
        }

        return RedirectDecision.Allow
    }
}
