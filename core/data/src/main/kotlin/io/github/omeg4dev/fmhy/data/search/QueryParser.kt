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
    private val IS_VALUES = setOf("starred", "top", "b64")

    /**
     * Splits [raw] into the same tokens [parse] consumes, with their character ranges.
     * [Token.end] is exclusive. A token whose quote is not closed runs to the end of the string.
     */
    fun tokens(raw: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        val len = raw.length
        while (i < len) {
            while (i < len && raw[i].isWhitespace()) i++
            if (i >= len) break
            val tokStart = i

            // in:"..." / site:"..."
            for (key in listOf("in", "site")) {
                val head = "$key:\""
                if (raw.regionMatches(i, head, 0, head.length, ignoreCase = false)) {
                    i += head.length
                    val vStart = i
                    while (i < len && raw[i] != '"') i++
                    val value = raw.substring(vStart, i).trim()
                    val closed = i < len
                    if (closed) i++
                    out.add(Token(tokStart, i, if (value.isNotEmpty()) TokenKind.PARAM else TokenKind.WORD, key, value, raw.substring(tokStart, i), closed))
                    break
                }
            }
            if (i != tokStart) continue

            var exclude = false
            if (raw[i] == '-' && i + 1 < len && !raw[i + 1].isWhitespace()) {
                exclude = true
                i++
            }
            if (i < len && raw[i] == '"') {
                i++
                val vStart = i
                while (i < len && raw[i] != '"') i++
                val value = raw.substring(vStart, i).trim()
                val closed = i < len
                if (closed) i++
                val kind = when {
                    value.isEmpty() -> TokenKind.WORD
                    exclude -> TokenKind.EXCLUDE
                    else -> TokenKind.PHRASE
                }
                out.add(Token(tokStart, i, kind, null, value, raw.substring(tokStart, i), closed))
            } else {
                val wStart = i
                while (i < len && !raw[i].isWhitespace()) i++
                val word = raw.substring(wStart, i)
                val lower = word.lowercase()
                val text = raw.substring(tokStart, i)
                val token = when {
                    exclude -> Token(tokStart, i, TokenKind.EXCLUDE, null, word, text, true)
                    lower.startsWith("in:") -> {
                        val v = word.substring(3).trim().trim('"')
                        Token(tokStart, i, if (v.isNotEmpty()) TokenKind.PARAM else TokenKind.WORD, "in", v, text, true)
                    }
                    lower.startsWith("is:") && lower.substring(3) in IS_VALUES ->
                        Token(tokStart, i, TokenKind.PARAM, "is", lower.substring(3), text, true)
                    lower.startsWith("site:") -> {
                        val v = word.substring(5).trim().trim('"').lowercase().removePrefix("www.")
                        Token(tokStart, i, if (v.isNotEmpty()) TokenKind.PARAM else TokenKind.WORD, "site", v, text, true)
                    }
                    else -> Token(tokStart, i, TokenKind.WORD, null, word, text, true)
                }
                out.add(token)
            }
        }
        return out
    }

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
