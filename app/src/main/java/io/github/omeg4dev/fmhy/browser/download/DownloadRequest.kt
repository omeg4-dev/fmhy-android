package io.github.omeg4dev.fmhy.browser.download

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

enum class ResumeDecision { APPEND, RESTART, DONE }

object DownloadRequest {

    private val INVALID_CHARS = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    private val MIME_EXT_MAP = mapOf(
        "application/pdf" to "pdf",
        "application/zip" to "zip",
        "application/vnd.android.package-archive" to "apk",
        "application/x-7z-compressed" to "7z",
        "application/x-rar-compressed" to "rar",
        "application/vnd.rar" to "rar",
        "video/mp4" to "mp4",
        "video/webm" to "webm",
        "video/x-matroska" to "mkv",
        "audio/mpeg" to "mp3",
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "text/plain" to "txt",
        "application/x-msdownload" to "exe",
        "application/epub+zip" to "epub",
    )

    fun headers(
        userAgent: String?,
        cookies: String?,
        referer: String?,
        offset: Long,
        etag: String?,
        lastModified: String?,
    ): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        if (!userAgent.isNullOrBlank()) {
            map["User-Agent"] = userAgent
        }
        if (!cookies.isNullOrBlank()) {
            map["Cookie"] = cookies
        }
        if (!referer.isNullOrBlank()) {
            try {
                val uri = URI(referer.trim())
                val scheme = uri.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    map["Referer"] = referer.trim().substringBefore('#')
                }
            } catch (_: Exception) {}
        }
        if (offset > 0L) {
            map["Range"] = "bytes=$offset-"
            if (!etag.isNullOrBlank()) {
                map["If-Range"] = etag
            } else if (!lastModified.isNullOrBlank()) {
                map["If-Range"] = lastModified
            }
        }
        return map
    }

    fun fileName(url: String, contentDisposition: String?, mimeType: String?): String {
        val cdCandidate = contentDisposition?.let { extractFromContentDisposition(it) }
        val rawCandidate = if (!cdCandidate.isNullOrBlank()) {
            cdCandidate
        } else {
            extractFromUrl(url)
        }

        var candidate = sanitizeAndCap(rawCandidate)

        val hasExtension = candidate.indexOf('.', startIndex = 1) != -1
        if (!hasExtension) {
            val cleanMime = mimeType?.substringBefore(';')?.trim()?.lowercase()
            val ext = MIME_EXT_MAP[cleanMime]
            if (ext != null) {
                candidate = capLength("$candidate.$ext", 120)
            }
        }

        return candidate
    }

    private fun extractFromContentDisposition(cd: String): String? {
        val fnStarRegex = Regex("""(?i)\bfilename\*\s*=\s*(?:(['"])(.*?)\1|([^;]+))""")
        val starMatch = fnStarRegex.find(cd)
        if (starMatch != null) {
            val raw = (starMatch.groups[2]?.value ?: starMatch.groups[3]?.value)?.trim()
            if (!raw.isNullOrEmpty()) {
                val parts = raw.split("'", limit = 3)
                if (parts.size == 3) {
                    val charsetName = parts[0]
                    val encodedVal = parts[2]
                    val charset = runCatching {
                        if (charsetName.equals("UTF-8", ignoreCase = true)) {
                            StandardCharsets.UTF_8
                        } else if (charsetName.equals("ISO-8859-1", ignoreCase = true)) {
                            StandardCharsets.ISO_8859_1
                        } else {
                            Charset.forName(charsetName)
                        }
                    }.getOrNull()

                    if (charset != null) {
                        val decoded = runCatching {
                            URLDecoder.decode(encodedVal.replace("+", "%2B"), charset.name())
                        }.getOrNull()
                        if (!decoded.isNullOrEmpty()) {
                            return decoded
                        }
                    }
                }
            }
        }

        val fnRegex = Regex("""(?i)\bfilename\s*=\s*(?:"([^"]*)"|([^;]+))""")
        val match = fnRegex.find(cd)
        if (match != null) {
            val result = (match.groups[1]?.value ?: match.groups[2]?.value)?.trim()
            if (!result.isNullOrEmpty()) {
                return result
            }
        }

        return null
    }

    private fun extractFromUrl(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val path = try {
            val uri = URI(clean)
            uri.rawPath ?: uri.path ?: clean
        } catch (_: Exception) {
            if (clean.contains("://")) {
                val afterScheme = clean.substringAfter("://")
                val slashIdx = afterScheme.indexOf('/')
                if (slashIdx != -1) afterScheme.substring(slashIdx) else ""
            } else {
                clean
            }
        }
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return "download"
        val last = segments.last()
        val decoded = runCatching {
            URLDecoder.decode(last.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrDefault(last)
        return if (decoded.isEmpty()) "download" else decoded
    }

    private fun sanitizeAndCap(rawName: String): String {
        val sanitized = buildString {
            for (ch in rawName) {
                if (ch in INVALID_CHARS || ch.code < 0x20 || ch.code == 0x7F) {
                    append('_')
                } else {
                    append(ch)
                }
            }
        }.trim(' ', '.')

        val candidate = if (sanitized.isEmpty()) "download" else sanitized
        return capLength(candidate, 120)
    }

    private fun capLength(name: String, maxLen: Int = 120): String {
        if (name.length <= maxLen) return name

        val doubleExts = listOf(".tar.gz", ".tar.xz", ".tar.bz2", ".tar.zst")
        val lower = name.lowercase()
        val matchDouble = doubleExts.firstOrNull { lower.endsWith(it) && name.length > it.length }

        val (base, ext) = if (matchDouble != null) {
            Pair(name.substring(0, name.length - matchDouble.length), name.substring(name.length - matchDouble.length))
        } else {
            val lastDot = name.lastIndexOf('.')
            if (lastDot > 0) {
                Pair(name.substring(0, lastDot), name.substring(lastDot))
            } else {
                Pair(name, "")
            }
        }

        return if (ext.length < maxLen) {
            base.take(maxLen - ext.length) + ext
        } else {
            name.take(maxLen)
        }
    }

    fun uniqueName(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val (base, ext) = splitForUniqueName(name)
        for (i in 1..9999) {
            val candidate = "$base ($i)$ext"
            if (!exists(candidate)) return candidate
        }
        return "$base (${System.currentTimeMillis()})$ext"
    }

    private fun splitForUniqueName(name: String): Pair<String, String> {
        if (name.indexOf('.', startIndex = 1) == -1) {
            return Pair(name, "")
        }

        val lower = name.lowercase()
        val doubleExts = listOf(".tar.gz", ".tar.xz", ".tar.bz2", ".tar.zst")
        for (doubleExt in doubleExts) {
            if (lower.endsWith(doubleExt) && name.length > doubleExt.length) {
                val base = name.substring(0, name.length - doubleExt.length)
                val ext = name.substring(name.length - doubleExt.length)
                return Pair(base, ext)
            }
        }

        val lastDot = name.lastIndexOf('.')
        return if (lastDot > 0) {
            Pair(name.substring(0, lastDot), name.substring(lastDot))
        } else {
            Pair(name, "")
        }
    }

    /**
     * Decides whether to append to an existing partially downloaded file, restart from the beginning,
     * or consider the download done.
     *
     * Note: HTTP 416 returns DONE; the caller should only trust this when it already has a known
     * total equal to offset. Non-2xx HTTP responses (other than 416) return RESTART; the caller
     * should handle non-2xx status as an error before calling this function.
     */
    fun resumeDecision(offset: Long, httpCode: Int, contentRangeStart: Long?): ResumeDecision {
        if (httpCode == 416) return ResumeDecision.DONE
        if (offset == 0L) return ResumeDecision.RESTART
        if (httpCode == 206 && contentRangeStart == offset) return ResumeDecision.APPEND
        return ResumeDecision.RESTART
    }

    fun parseContentRangeStart(header: String?): Long? {
        if (header == null) return null
        val match = Regex("""(?i)^bytes\s+(\d+)-\d+/""").find(header.trim()) ?: return null
        return match.groups[1]?.value?.toLongOrNull()
    }

    fun parseContentRangeTotal(header: String?): Long? {
        if (header == null) return null
        val match = Regex("""(?i)^bytes\s+(?:\d+-\d+|\*)/(\d+)$""").find(header.trim()) ?: return null
        return match.groups[1]?.value?.toLongOrNull()
    }

    fun hostOf(url: String): String {
        return try {
            val uri = URI(url.trim())
            uri.host?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun isHttpUrl(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host
            (scheme == "http" || scheme == "https") && !host.isNullOrEmpty()
        } catch (_: Exception) {
            false
        }
    }
}
