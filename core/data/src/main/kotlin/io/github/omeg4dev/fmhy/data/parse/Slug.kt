package io.github.omeg4dev.fmhy.data.parse

import java.text.Normalizer
import java.util.Locale

class AnchorTracker {
    private val seen = mutableMapOf<String, Int>()

    fun add(rawAnchor: String): String {
        val count = seen[rawAnchor]
        if (count == null) {
            seen[rawAnchor] = 0
            return rawAnchor
        }
        val next = count + 1
        seen[rawAnchor] = next
        return "$rawAnchor-$next"
    }
}

object Slug {
    private val COMBINING_RE = Regex("[\u0300-\u036f]")
    private val CTRL_RE = Regex("[\u0000-\u001f\u007f-\u009f]")
    private val SEP_RE = Regex("""[\s~`!@#$%^&*()\-_+=\[\]{}|\\;:\"'<>,.?/]+""")

    fun stripTitlePrefix(title: String): String {
        val strippedMarkers = title.replace("►", "").replace("▷", "")
        val sb = StringBuilder()
        var inPrefix = true
        var i = 0
        val len = strippedMarkers.length
        while (i < len) {
            val cp = strippedMarkers.codePointAt(i)
            val charCount = Character.charCount(cp)
            if (inPrefix && isEmojiCodePoint(cp)) {
                i += charCount
                continue
            }
            inPrefix = false
            sb.append(strippedMarkers, i, i + charCount)
            i += charCount
        }
        return sb.toString().trimStart()
    }

    private fun isEmojiCodePoint(cp: Int): Boolean {
        return (cp in 0x1F300..0x1FAFF) ||
            (cp in 0x2600..0x27BF) ||
            (cp in 0xFE00..0xFE0F) ||
            (cp in 0x2190..0x21FF) ||
            (cp in 0x2300..0x23FF) ||
            (cp in 0x2700..0x27BF) ||
            (cp in 0x2B00..0x2BFF) ||
            (cp in 0x3000..0x303F) ||
            cp == 0xFE0F
    }

    fun slugify(title: String): String {
        var s = stripTitlePrefix(title)
        s = Normalizer.normalize(s, Normalizer.Form.NFKD)
        s = COMBINING_RE.replace(s, "")
        s = CTRL_RE.replace(s, "")
        s = SEP_RE.replace(s, "-")
        s = s.trim('-')
        if (s.isNotEmpty() && s[0].isDigit()) {
            s = "_$s"
        }
        return s.lowercase(Locale.ROOT)
    }
}
