package io.github.omeg4dev.fmhy.data.search

import io.github.omeg4dev.fmhy.data.model.Section

/**
 * String-level editing of a raw query, shared by the search bar and the filter sheet. The raw string stays the
 * single source of truth; the UI shows its committed parameters (`in:x`, `is:starred`, `site:x`, `-word`
 * followed by a space) as pills and only the remaining text in the field.
 *
 * Canonical form: `<pill> <pill> <free text>`.
 */
object QueryEdit {

    /** [pills] are the committed parameter tokens (de-duplicated, in order), [free] the rest of the text and
     *  [cursor] the caret position inside [free]. */
    data class Split(val pills: List<Token>, val free: String, val cursor: Int)

    fun split(raw: String, cursor: Int = raw.length): Split {
        val tokens = QueryParser.tokens(raw)
        val pills = ArrayList<Token>()
        val removed = ArrayList<IntRange>() // [start, endExclusive)
        for (t in tokens) {
            // Committed = a separator follows the token (so the last token while typing stays text).
            if (!t.isPill || t.end >= raw.length) continue
            var e = t.end
            while (e < raw.length && raw[e].isWhitespace()) e++
            removed.add(t.start until e)
            if (pills.none { it.text.equals(t.text, ignoreCase = true) }) pills.add(t)
        }
        val sb = StringBuilder()
        var pos = 0
        var newCursor = cursor.coerceIn(0, raw.length)
        val cur = newCursor
        for (r in removed) {
            sb.append(raw, pos, r.first)
            pos = r.last + 1
            val overlap = minOf(cur, r.last + 1) - r.first
            if (overlap > 0) newCursor -= overlap
        }
        sb.append(raw, pos, raw.length)
        return Split(pills, sb.toString(), newCursor.coerceIn(0, sb.length))
    }

    fun compose(pills: List<Token>, free: String): String =
        if (pills.isEmpty()) free else pills.joinToString(" ") { it.text } + " " + free

    /** Makes a trailing parameter count as committed (used for queries that arrive from outside the field). */
    fun commitAll(raw: String): String {
        val last = QueryParser.tokens(raw).lastOrNull() ?: return raw
        return if (last.isPill && last.end >= raw.length) "$raw " else raw
    }

    /** Re-serialises [raw] into canonical form with every parameter committed (for queries set from outside the field). */
    fun canonical(raw: String): String {
        val s = split(commitAll(raw))
        return compose(s.pills, s.free.trimEnd())
    }

    fun paramText(key: String, value: String): String =
        if (value.any { it.isWhitespace() || it == '"' }) "$key:\"${value.replace("\"", "")}\"" else "$key:$value"

    private fun paramToken(key: String, value: String): Token =
        QueryParser.tokens(paramText(key, value)).first()

    fun pills(raw: String): List<Token> = split(commitAll(raw)).pills

    fun hasParam(raw: String, key: String, value: String): Boolean =
        pills(raw).any { it.kind == TokenKind.PARAM && it.key == key && it.value.equals(value, ignoreCase = true) }

    fun addParam(raw: String, key: String, value: String): String {
        val s = split(commitAll(raw))
        if (s.pills.any { it.kind == TokenKind.PARAM && it.key == key && it.value.equals(value, true) }) return compose(s.pills, s.free)
        // `site:` is single-valued in the parser: replace instead of stacking.
        val kept = if (key == "site") s.pills.filterNot { it.key == "site" } else s.pills
        return compose(kept + paramToken(key, value), s.free)
    }

    fun removePill(raw: String, text: String): String {
        val s = split(commitAll(raw))
        return compose(s.pills.filterNot { it.text.equals(text, ignoreCase = true) }, s.free)
    }

    fun toggleParam(raw: String, key: String, value: String): String =
        if (hasParam(raw, key, value)) {
            val s = split(commitAll(raw))
            compose(s.pills.filterNot { it.kind == TokenKind.PARAM && it.key == key && it.value.equals(value, true) }, s.free)
        } else addParam(raw, key, value)

    /** Removes the last pill (Backspace at the start of the text). */
    fun dropLastPill(raw: String): String {
        val s = split(commitAll(raw))
        return compose(s.pills.dropLast(1), s.free)
    }

    /** The section ids an `in:<pattern>` resolves to: an exact id wins, else id / title prefixes. */
    fun resolveSections(pattern: String, sections: List<Section>): Set<String> {
        val exact = sections.filter { it.id.equals(pattern, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact.map { it.id }.toSet()
        return sections.filter {
            it.id.startsWith(pattern, ignoreCase = true) || it.title.startsWith(pattern, ignoreCase = true)
        }.map { it.id }.toSet()
    }

    /** Parameters currently set in [raw] that apply to [sections]: the ids selected through `in:` pills. */
    fun selectedSections(raw: String, sections: List<Section>): Set<String> =
        pills(raw).filter { it.kind == TokenKind.PARAM && it.key == "in" }
            .flatMap { resolveSections(it.value, sections) }.toSet()

    /** Toggles a section through its `in:` pill. Unchecking removes every `in:` pill that resolves to it. */
    fun toggleSection(raw: String, sectionId: String, sections: List<Section>): String {
        val s = split(commitAll(raw))
        val hit = s.pills.filter { it.kind == TokenKind.PARAM && it.key == "in" && sectionId in resolveSections(it.value, sections) }
        return if (hit.isEmpty()) addParam(raw, "in", sectionId)
        else compose(s.pills - hit.toSet(), s.free)
    }
}
