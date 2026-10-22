package io.github.omeg4dev.fmhy.data.search

import io.github.omeg4dev.fmhy.data.model.Section

enum class SuggestionKind { OPERATOR, SECTION, IS, SITE }

/** One entry of the suggestion list under the search bar. [insert] replaces the partial token;
 *  [commit] adds a separating space so the result becomes a pill. */
data class QuerySuggestion(
    val kind: SuggestionKind,
    val label: String,
    val detail: String?,
    val insert: String,
    val commit: Boolean,
    val sectionId: String? = null,
)

/** What the user is typing at the cursor: an operator name, or the value after `in:` / `is:` / `site:`. */
data class AssistRequest(val kind: SuggestionKind, val prefix: String)

/** The token being typed: [start] until [end] (exclusive) of the free text. */
data class Partial(val start: Int, val end: Int, val text: String)

object QueryAssist {
    private val OPERATORS = listOf(
        "in" to "Search inside one section",
        "is" to "Starred, top picks or hidden links",
        "site" to "Only links on one website",
    )

    private val IS_OPTIONS = listOf(
        Triple("starred", "Starred", "Picked by FMHY (starred and top picks)"),
        Triple("top", "Top picks", "Only the very best of FMHY"),
        Triple("b64", "Hidden links", "Links stored as base64"),
    )

    /** The token that contains [cursor], or null when the caret sits in whitespace. */
    fun partialAt(free: String, cursor: Int): Partial? {
        val c = cursor.coerceIn(0, free.length)
        val t = QueryParser.tokens(free).firstOrNull { c >= it.start && c <= it.end } ?: return null
        return Partial(t.start, t.end, t.text)
    }

    fun request(partial: String): AssistRequest? {
        val lower = partial.lowercase()
        val colon = lower.indexOf(':')
        if (colon > 0) {
            val key = lower.substring(0, colon)
            val prefix = partial.substring(colon + 1).trimStart('"').trim()
            return when (key) {
                "in" -> AssistRequest(SuggestionKind.SECTION, prefix)
                "is" -> AssistRequest(SuggestionKind.IS, prefix)
                "site" -> AssistRequest(SuggestionKind.SITE, prefix)
                else -> null
            }
        }
        if (lower.length >= 2 && OPERATORS.any { it.first.startsWith(lower) }) {
            return AssistRequest(SuggestionKind.OPERATOR, lower)
        }
        return null
    }

    fun operators(prefix: String): List<QuerySuggestion> =
        OPERATORS.filter { it.first.startsWith(prefix) }.map { (op, detail) ->
            QuerySuggestion(SuggestionKind.OPERATOR, "$op:", detail, "$op:", commit = false)
        }

    fun isOptions(prefix: String): List<QuerySuggestion> =
        IS_OPTIONS.filter { it.first.startsWith(prefix.lowercase()) }.map { (v, label, detail) ->
            QuerySuggestion(SuggestionKind.IS, label, detail, "is:$v", commit = true)
        }

    fun sections(prefix: String, sections: List<Section>, limit: Int = 8): List<QuerySuggestion> {
        val p = prefix.lowercase()
        val matches = if (p.isEmpty()) sections else sections.filter { s ->
            s.id.startsWith(p, ignoreCase = true) ||
                s.title.split(Regex("[^\\p{L}\\p{N}]+")).any { it.startsWith(p, ignoreCase = true) } ||
                s.title.startsWith(p, ignoreCase = true)
        }.sortedBy { s ->
            when {
                s.id.equals(p, true) -> 0
                s.id.startsWith(p, true) || s.title.startsWith(p, true) -> 1
                else -> 2
            }
        }
        return matches.take(limit).map { s ->
            QuerySuggestion(SuggestionKind.SECTION, s.title, s.id, QueryEdit.paramText("in", s.id), commit = true, sectionId = s.id)
        }
    }

    fun sites(prefix: String, hosts: List<String>): List<QuerySuggestion> =
        hosts.map { QuerySuggestion(SuggestionKind.SITE, it, null, QueryEdit.paramText("site", it), commit = true) }

    /** Replaces [partial] in [free] with the suggestion; returns the new text and the caret position. */
    fun apply(free: String, partial: Partial, s: QuerySuggestion): Pair<String, Int> {
        val ins = if (s.commit) s.insert + " " else s.insert
        val text = free.substring(0, partial.start) + ins + free.substring(partial.end)
        return text to partial.start + ins.length
    }
}
