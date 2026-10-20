package io.github.omeg4dev.fmhy.ui.check

import java.net.URI

private val WEB_URL_FALLBACK_REGEX = Regex(
    """https?://[^\s<>"{}|\\^`]+|(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}(?::\d{1,5})?(?:/[^\s<>"{}|\\^`]*)?"""
)

fun looksLikeUrl(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty() || trimmed.contains(' ') || trimmed.contains('\t') || trimmed.contains('\n')) {
        return false
    }
    val hasScheme = trimmed.contains("://")
    val hasDot = trimmed.contains('.')
    val isLocalhost = trimmed.equals("localhost", ignoreCase = true) ||
        trimmed.startsWith("localhost:", ignoreCase = true) ||
        trimmed.startsWith("localhost/", ignoreCase = true)

    if (!hasScheme && !hasDot && !isLocalhost) {
        return false
    }

    val urlWithScheme = if (!hasScheme) "https://$trimmed" else trimmed
    return try {
        val uri = URI(urlWithScheme)
        val host = uri.host?.trim()
        if (host.isNullOrEmpty() || host.contains(' ') || host.contains('%')) {
            false
        } else {
            host.contains('.') || host.equals("localhost", ignoreCase = true)
        }
    } catch (_: Exception) {
        false
    }
}

fun extractFirstUrl(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ""

    val pattern = try {
        android.util.Patterns.WEB_URL
    } catch (_: Throwable) {
        null
    }

    if (pattern != null) {
        val matcher = pattern.matcher(trimmed)
        if (matcher.find()) {
            return matcher.group().trimEnd('.', ',', ';', '!', '?', ')')
        }
    } else {
        val match = WEB_URL_FALLBACK_REGEX.find(trimmed)
        if (match != null) {
            return match.value.trimEnd('.', ',', ';', '!', '?', ')')
        }
    }
    return trimmed
}
