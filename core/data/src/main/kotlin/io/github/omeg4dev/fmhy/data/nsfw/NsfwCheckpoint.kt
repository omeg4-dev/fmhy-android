package io.github.omeg4dev.fmhy.data.nsfw

import java.net.URI
import java.util.Base64

/** Extracts the raw paste URL hosted on yaso.su from checkpoint page HTML. */
object NsfwCheckpoint {

    private val BASE64_HTTP_TOKEN_RE = Regex("""aHR0[A-Za-z0-9+/_-]*={0,2}""")
    private const val YASO_HOST = "yaso.su"

    /** Returns the single path segment id for the first valid yaso.su URL, or null. */
    fun extractYasoId(html: String): String? {
        for (match in BASE64_HTTP_TOKEN_RE.findAll(html)) {
            val decoded = decodeToken(match.value) ?: continue
            val id = parseYasoId(decoded)
            if (id != null) return id
        }
        return null
    }

    /** Returns `<rawBaseUrl>/raw/<id>` for the first valid yaso.su mirror, or null. */
    fun extractYasoRawUrl(html: String, rawBaseUrl: String = NSFW_RAW_BASE_URL): String? {
        val id = extractYasoId(html) ?: return null
        val base = rawBaseUrl.trimEnd('/')
        return "$base/raw/$id"
    }

    private fun decodeToken(token: String): String? {
        val decoder = if ('-' in token || '_' in token) {
            Base64.getUrlDecoder()
        } else {
            Base64.getDecoder()
        }
        return try {
            String(decoder.decode(token), Charsets.UTF_8).trim()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun parseYasoId(candidateUrl: String): String? {
        val uri = try {
            URI(candidateUrl)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (host != YASO_HOST) return null
        val segments = (uri.path ?: "").split('/').filter { it.isNotEmpty() }
        if (segments.size != 1) return null
        return segments[0]
    }
}
