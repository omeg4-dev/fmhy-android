package io.github.omeg4dev.fmhy.data.search

/**
 * Parses search strings supporting:
 * - plain words
 * - "phrases"
 * - -exclude
 * - in:<section id or title prefix> (repeatable)
 * - is:starred (tier >= 1)
 * - is:top (tier 2)
 * - is:b64
 * - site:<host>
 */
object QueryParser {
    fun parse(raw: String): Query {
        val words = mutableListOf<String>()
        val phrases = mutableListOf<String>()
        val excludes = mutableListOf<String>()
        val inSections = mutableListOf<String>()
        var isStarred = false
        var isTop = false
        var isB64 = false
        var site: String? = null

        var i = 0
        val len = raw.length
        while (i < len) {
            while (i < len && raw[i].isWhitespace()) i++
            if (i >= len) break

            // Check for in:"..."
            if (raw.startsWith("in:\"", i)) {
                i += 4
                val start = i
                while (i < len && raw[i] != '"') i++
                val sec = raw.substring(start, i).trim()
                if (i < len && raw[i] == '"') i++
                if (sec.isNotEmpty()) inSections.add(sec)
                continue
            }

            // Check for site:"..."
            if (raw.startsWith("site:\"", i)) {
                i += 6
                val start = i
                while (i < len && raw[i] != '"') i++
                val s = raw.substring(start, i).trim().lowercase().removePrefix("www.")
                if (i < len && raw[i] == '"') i++
                if (s.isNotEmpty()) site = s
                continue
            }

            var isExclude = false
            if (raw[i] == '-' && i + 1 < len && !raw[i + 1].isWhitespace()) {
                isExclude = true
                i++
            }

            if (i < len && raw[i] == '"') {
                i++ // skip opening quote
                val start = i
                while (i < len && raw[i] != '"') i++
                val phrase = raw.substring(start, i).trim()
                if (i < len && raw[i] == '"') i++ // skip closing quote
                if (phrase.isNotEmpty()) {
                    if (isExclude) excludes.add(phrase)
                    else phrases.add(phrase)
                }
            } else {
                val start = i
                while (i < len && !raw[i].isWhitespace()) i++
                val token = raw.substring(start, i)
                val lowerToken = token.lowercase()

                if (isExclude) {
                    if (token.isNotEmpty()) excludes.add(token)
                } else if (lowerToken.startsWith("in:")) {
                    val sec = token.substring(3).trim().trim('"')
                    if (sec.isNotEmpty()) inSections.add(sec)
                } else if (lowerToken == "is:starred") {
                    isStarred = true
                } else if (lowerToken == "is:top") {
                    isTop = true
                } else if (lowerToken == "is:b64") {
                    isB64 = true
                } else if (lowerToken.startsWith("site:")) {
                    val s = token.substring(5).trim().trim('"').lowercase().removePrefix("www.")
                    if (s.isNotEmpty()) site = s
                } else {
                    if (token.isNotEmpty()) words.add(token)
                }
            }
        }

        return Query(
            raw = raw,
            words = words,
            phrases = phrases,
            excludes = excludes,
            inSections = inSections,
            isStarred = isStarred,
            isTop = isTop,
            isB64 = isB64,
            site = site,
        )
    }
}
