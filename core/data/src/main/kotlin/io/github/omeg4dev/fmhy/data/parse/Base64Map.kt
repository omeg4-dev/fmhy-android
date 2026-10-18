package io.github.omeg4dev.fmhy.data.parse

import java.util.Base64

class Base64Map(html: String) {
    private val map: Map<String, List<String>>

    val resolvedAnchors: Int
        get() = map.size

    init {
        map = loadBase64Map(html)
    }

    fun resolve(url: String, mirrors: List<LinkRow>): Triple<String, String?, List<LinkRow>> {
        val match = RENTRY_RE.matchEntire(url) ?: return Triple(url, null, mirrors)
        val anchor = match.groupValues[3]
        val urls = map[anchor]
        if (urls.isNullOrEmpty()) {
            return Triple(url, null, mirrors)
        }

        val primary = urls[0]
        val originalUrl = url
        val extraMirrors = urls.drop(1).mapIndexed { idx, u ->
            LinkRow(label = "${idx + 2}", url = u)
        }
        val allMirrors = extraMirrors + mirrors
        return Triple(primary, originalUrl, allMirrors)
    }

    companion object {
        private val RENTRY_RE = Regex(
            """^https?://(www\.)?rentry\.(co|org)/FMHYB64#(.+)$""",
            RegexOption.IGNORE_CASE
        )
        private val H4_RE = Regex(
            """<h4[^>]*id=["']([^"']+)["'][^>]*>(.*?)((?=<h4)|\Z)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val CODE_RE = Regex(
            """<code>(.*?)</code>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        private fun tryDecodeB64(s: String): String? {
            var str = s.trim()
            val missing = str.length % 4
            if (missing != 0) {
                str += "=".repeat(4 - missing)
            }
            return try {
                val bytes = Base64.getDecoder().decode(str)
                String(bytes, Charsets.UTF_8)
            } catch (_: Throwable) {
                try {
                    val bytes = Base64.getUrlDecoder().decode(str)
                    String(bytes, Charsets.UTF_8)
                } catch (_: Throwable) {
                    null
                }
            }
        }

        fun resolveB64String(s: String, maxRounds: Int = 3): String? {
            var current = s.trim()
            for (i in 0 until maxRounds) {
                if (current.startsWith("http://") || current.startsWith("https://")) {
                    return current
                }
                val decoded = tryDecodeB64(current) ?: return null
                current = decoded.trim()
            }
            if (current.startsWith("http://") || current.startsWith("https://")) {
                return current
            }
            return null
        }

        private fun loadBase64Map(htmlContent: String): Map<String, List<String>> {
            val result = mutableMapOf<String, List<String>>()
            for (m in H4_RE.findAll(htmlContent)) {
                val anchor = m.groupValues[1]
                val body = m.groupValues[2]
                val urls = mutableListOf<String>()
                for (codeMatch in CODE_RE.findAll(body)) {
                    val codeText = codeMatch.groupValues[1].trim()
                    val resolvedUrl = resolveB64String(codeText)
                    if (resolvedUrl != null) {
                        urls.add(resolvedUrl)
                    }
                }
                if (urls.isNotEmpty()) {
                    result[anchor] = urls
                }
            }
            return result
        }
    }
}
