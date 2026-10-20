package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.SearchEngine
import java.net.URLEncoder

object UrlInput {
    private val SCHEME_REGEX = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://.*""")

    fun resolve(text: String, engine: SearchEngine): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed == "about:blank" || trimmed.startsWith("about:") || trimmed.matches(SCHEME_REGEX)) {
            return trimmed
        }
        if (trimmed.startsWith("localhost", ignoreCase = true) && !trimmed.contains(" ")) {
            return "http://$trimmed"
        }
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            return "https://$trimmed"
        }
        return engine.queryUrl + URLEncoder.encode(trimmed, "UTF-8")
    }
}
