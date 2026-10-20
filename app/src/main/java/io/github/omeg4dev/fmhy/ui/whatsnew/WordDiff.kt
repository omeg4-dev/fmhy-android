package io.github.omeg4dev.fmhy.ui.whatsnew

enum class DiffType {
    SAME,
    REMOVED,
    ADDED,
}

data class DiffSegment(
    val text: String,
    val type: DiffType,
)

object WordDiff {

    private val LINK_REGEX = Regex("""\[([^\]]+)\]\(([^)]+)\)""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    /**
     * Extracts the clean description from a markdown entry line:
     * - strips leading `*`/`-` bullet, `⭐`/`🌟` and `**`
     * - removes the FIRST `[label](url)` and a following ` - ` separator
     * - turns remaining `[label](url)` into `label`
     * - collapses whitespace
     */
    fun descriptionOf(line: String): String {
        if (line.isBlank()) return ""
        var s = line.trim()

        // Strip leading bullets, stars, bold markers and whitespace
        while (true) {
            val prev = s
            s = s.trimStart()
            when {
                s.startsWith("*") -> s = s.removePrefix("*")
                s.startsWith("-") -> s = s.removePrefix("-")
                s.startsWith("⭐") -> s = s.removePrefix("⭐")
                s.startsWith("🌟") -> s = s.removePrefix("🌟")
                s.startsWith("**") -> s = s.removePrefix("**")
            }
            if (s == prev) break
        }
        s = s.trimStart()

        // Remove the FIRST [label](url) and a following ` - ` separator
        val firstMatch = LINK_REGEX.find(s)
        if (firstMatch != null) {
            val before = s.substring(0, firstMatch.range.first).removeSuffix("**").trim()
            var after = s.substring(firstMatch.range.last + 1).trimStart()
            if (after.startsWith("**")) {
                after = after.removePrefix("**").trimStart()
            }
            if (after.startsWith("-") || after.startsWith("–") || after.startsWith("—")) {
                after = after.substring(1).trimStart()
            }
            s = if (before.isEmpty()) after else "$before $after"
        }

        // Turn remaining [label](url) into label
        s = LINK_REGEX.replace(s) { it.groupValues[1] }

        // Collapse whitespace
        return s.replace(WHITESPACE_REGEX, " ").trim()
    }

    /**
     * Computes word-level diff between [old] and [new] descriptions using LCS on whitespace-separated tokens.
     * Capped at 400 tokens per side; beyond that, returns one REMOVED + one ADDED segment.
     */
    fun wordDiff(old: String, new: String): List<DiffSegment> {
        val oldTokens = old.trim().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        val newTokens = new.trim().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }

        if (oldTokens.isEmpty() && newTokens.isEmpty()) return emptyList()
        if (oldTokens.isEmpty()) return listOf(DiffSegment(newTokens.joinToString(" "), DiffType.ADDED))
        if (newTokens.isEmpty()) return listOf(DiffSegment(oldTokens.joinToString(" "), DiffType.REMOVED))

        // Token cap (400 per side)
        if (oldTokens.size > 400 || newTokens.size > 400) {
            return listOf(
                DiffSegment(oldTokens.joinToString(" "), DiffType.REMOVED),
                DiffSegment(newTokens.joinToString(" "), DiffType.ADDED),
            )
        }

        val n = oldTokens.size
        val m = newTokens.size
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in 0 until n) {
            for (j in 0 until m) {
                if (oldTokens[i] == newTokens[j]) {
                    dp[i + 1][j + 1] = dp[i][j] + 1
                } else {
                    dp[i + 1][j + 1] = maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        var i = n
        var j = m
        val raw = mutableListOf<DiffSegment>()
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldTokens[i - 1] == newTokens[j - 1]) {
                raw.add(DiffSegment(oldTokens[i - 1], DiffType.SAME))
                i--
                j--
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                raw.add(DiffSegment(newTokens[j - 1], DiffType.ADDED))
                j--
            } else if (i > 0) {
                raw.add(DiffSegment(oldTokens[i - 1], DiffType.REMOVED))
                i--
            }
        }
        raw.reverse()

        // Merge consecutive tokens of same type
        val merged = mutableListOf<DiffSegment>()
        for (seg in raw) {
            val last = merged.lastOrNull()
            if (last != null && last.type == seg.type) {
                merged[merged.size - 1] = DiffSegment("${last.text} ${seg.text}", last.type)
            } else {
                merged.add(seg)
            }
        }
        return merged
    }

    /**
     * Keeps changed regions with [context] words of context each side, and inserts `SAME("…")` gaps.
     */
    fun compactDiff(segments: List<DiffSegment>, context: Int = 4): List<DiffSegment> {
        if (segments.isEmpty()) return emptyList()
        if (segments.none { it.type != DiffType.SAME }) return segments

        // Break segments into word-level tokens
        data class WordToken(val word: String, val type: DiffType)
        val tokens = mutableListOf<WordToken>()
        for (seg in segments) {
            val words = seg.text.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
            for (w in words) {
                tokens.add(WordToken(w, seg.type))
            }
        }

        val keep = BooleanArray(tokens.size)
        for (idx in tokens.indices) {
            if (tokens[idx].type != DiffType.SAME) {
                val start = maxOf(0, idx - context)
                val end = minOf(tokens.size - 1, idx + context)
                for (k in start..end) {
                    keep[k] = true
                }
            }
        }

        val result = mutableListOf<DiffSegment>()
        var inGap = false
        var currentMergedText = StringBuilder()
        var currentType: DiffType? = null

        fun flushCurrent() {
            if (currentType != null && currentMergedText.isNotEmpty()) {
                result.add(DiffSegment(currentMergedText.toString(), currentType!!))
                currentMergedText.clear()
                currentType = null
            }
        }

        for (idx in tokens.indices) {
            if (keep[idx]) {
                if (inGap) {
                    flushCurrent()
                    result.add(DiffSegment("…", DiffType.SAME))
                    inGap = false
                }
                val token = tokens[idx]
                if (currentType == token.type) {
                    currentMergedText.append(" ").append(token.word)
                } else {
                    flushCurrent()
                    currentType = token.type
                    currentMergedText.append(token.word)
                }
            } else {
                inGap = true
            }
        }
        flushCurrent()
        if (inGap) {
            result.add(DiffSegment("…", DiffType.SAME))
        }

        return result
    }
}
