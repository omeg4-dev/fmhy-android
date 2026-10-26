package io.github.omeg4dev.fmhy.ui.settings

import io.github.omeg4dev.fmhy.SettingsPage
import java.text.Normalizer

data class SettingsSearchResult(
    val entry: SettingEntry,
    val path: String,
)

object SettingsSearchIndex {

    private data class ScoredEntry(
        val entry: SettingEntry,
        val score: Int,
        val keywordTieBreak: Int,
    )

    fun search(
        query: String,
        entries: List<SettingEntry> = SettingsCatalog.all,
        excludePages: Set<SettingsPage> = emptySet(),
        limit: Int = 30,
    ): List<SettingsSearchResult> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val normalizedQuery = normalizeText(query)

        val scoredResults = mutableListOf<ScoredEntry>()

        for (entry in entries) {
            if (entry.page in excludePages) continue

            val titleWords = tokenize(entry.title)
            val keywordWords = entry.keywords.flatMap { tokenize(it) }
            val descWords = tokenize(entry.description)

            var matches = true
            var totalScore = 0
            var keywordTieBreak = 0

            for (token in queryTokens) {
                val tokenScore = scoreForToken(token, titleWords, keywordWords, descWords)
                if (tokenScore == 0) {
                    matches = false
                    break
                }
                totalScore += tokenScore
                keywordTieBreak += keywordMatchScore(token, keywordWords)
            }

            if (matches) {
                val normalizedTitle = normalizeText(entry.title)
                if (normalizedTitle.startsWith(normalizedQuery)) {
                    totalScore += 30
                }
                scoredResults.add(ScoredEntry(entry, totalScore, keywordTieBreak))
            }
        }

        return scoredResults
            .sortedWith(
                compareByDescending<ScoredEntry> { it.score }
                    .thenByDescending { it.keywordTieBreak }
            )
            .take(limit)
            .map { (entry, _, _) ->
                SettingsSearchResult(
                    entry = entry,
                    path = "${entry.page.heading()} › ${entry.page.label()}",
                )
            }
    }

    private fun keywordMatchScore(token: String, keywordWords: List<String>): Int {
        if (keywordWords.any { it == token }) return 2
        if (token.length >= 2 && keywordWords.any { it.startsWith(token) }) return 1
        return 0
    }

    fun normalizeText(text: String): String {
        val lower = text.lowercase()
        val nfd = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val noDiacritics = nfd.replace("\\p{M}+".toRegex(), "")
        val noHyphens = noDiacritics.replace("[-'’‘‐‑–—]".toRegex(), "")
        val alphaNum = noHyphens.replace("[^\\p{L}\\p{Nd}]+".toRegex(), " ")
        return alphaNum.trim().replace("\\s+".toRegex(), " ")
    }

    fun tokenize(text: String): List<String> {
        val norm = normalizeText(text)
        if (norm.isEmpty()) return emptyList()
        return norm.split(" ")
    }

    fun scoreForToken(
        token: String,
        titleWords: List<String>,
        keywordWords: List<String>,
        descWords: List<String>,
    ): Int {
        if (titleWords.any { it == token }) return 100
        if (token.length >= 2 && titleWords.any { it.startsWith(token) }) return 80
        if (keywordWords.any { it == token }) return 70
        if (token.length >= 2 && keywordWords.any { it.startsWith(token) }) return 60
        if (token.length >= 4 && titleWords.any { isTypoMatch(token, it) }) return 50
        if (token.length >= 4 && keywordWords.any { isTypoMatch(token, it) }) return 40
        if (descWords.any { it == token || (token.length >= 2 && it.startsWith(token)) }) return 20
        if (token.length >= 4 && descWords.any { isTypoMatch(token, it) }) return 10
        return 0
    }

    fun isTypoMatch(token: String, word: String): Boolean {
        val maxDist = when {
            token.length >= 8 -> 2
            token.length >= 4 -> 1
            else -> return false
        }
        if (damerauLevenshtein(token, word) <= maxDist) return true
        if (word.length >= token.length) {
            val prefix = word.substring(0, token.length)
            if (damerauLevenshtein(token, prefix) <= maxDist) return true
        }
        return false
    }

    fun damerauLevenshtein(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val d = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) d[i][0] = i
        for (j in 0..n) d[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                var min = minOf(
                    d[i - 1][j] + 1,       // deletion
                    d[i][j - 1] + 1,       // insertion
                    d[i - 1][j - 1] + cost // substitution
                )
                if (i > 1 && j > 1 && s1[i - 1] == s2[j - 2] && s1[i - 2] == s2[j - 1]) {
                    min = minOf(min, d[i - 2][j - 2] + 1) // transposition
                }
                d[i][j] = min
            }
        }
        return d[m][n]
    }
}

class ScrollRevealState(
    val threshold: Float = 12f,
    initialRevealed: Boolean = false,
    private val onRevealedChanged: ((Boolean) -> Unit)? = null,
) {
    var isRevealed: Boolean = initialRevealed
        set(value) {
            if (field != value) {
                field = value
                onRevealedChanged?.invoke(value)
            }
        }

    var accumulatedDelta: Float = 0f
        private set

    fun onScroll(dy: Float) {
        if (dy > 0f) {
            // Scrolling up (user drags down)
            if (accumulatedDelta < 0f) {
                accumulatedDelta = 0f
            }
            accumulatedDelta += dy
            if (accumulatedDelta >= threshold) {
                isRevealed = true
            }
        } else if (dy < 0f) {
            // Scrolling down (user drags up)
            if (accumulatedDelta > 0f) {
                accumulatedDelta = 0f
            }
            accumulatedDelta += dy
            if (accumulatedDelta <= -threshold) {
                isRevealed = false
            }
        }
    }

    fun reveal() {
        isRevealed = true
        accumulatedDelta = threshold
    }

    fun hide() {
        isRevealed = false
        accumulatedDelta = -threshold
    }

    fun reset() {
        accumulatedDelta = 0f
    }
}
