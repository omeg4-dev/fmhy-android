package io.github.omeg4dev.fmhy.ui.search

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.search.QueryEdit
import io.github.omeg4dev.fmhy.data.search.Token
import io.github.omeg4dev.fmhy.data.search.TokenKind

/**
 * The text field shows the free text of the query with a zero-width [SENTINEL] in front. Deleting the sentinel
 * (Backspace with the caret at the start of the text) is the one edit an IME reports even when there is
 * nothing before the caret, and it removes the last pill.
 */
internal const val SENTINEL = '​'

internal fun fieldValueFor(free: String, cursor: Int = free.length): TextFieldValue =
    TextFieldValue(SENTINEL + free, TextRange(cursor.coerceIn(0, free.length) + 1))

internal val TextFieldValue.free: String get() = text.removePrefix(SENTINEL.toString())

/** The caret inside the free text. */
internal val TextFieldValue.freeCursor: Int get() = (selection.end - 1).coerceIn(0, free.length)

internal data class FieldEdit(val raw: String, val value: TextFieldValue)

/**
 * Applies a keyboard edit. [pills] are the committed parameters of the current query, [old] the field before
 * the edit. Tokens that became committed (followed by a space) move out of the text into the pills; the
 * returned [FieldEdit.raw] is the new canonical query.
 */
internal fun applyFieldEdit(pills: List<Token>, old: TextFieldValue, new: TextFieldValue): FieldEdit {
    var activePills = pills
    val free: String
    var cursor: Int
    if (new.text.startsWith(SENTINEL)) {
        free = new.text.substring(1)
        cursor = new.selection.end - 1
    } else if (new.text == old.text.removePrefix(SENTINEL.toString()) && old.text.startsWith(SENTINEL) && old.selection.collapsed && old.selection.end <= 1) {
        // Backspace at the very start: the sentinel went, the text did not change.
        if (activePills.isNotEmpty()) activePills = activePills.dropLast(1)
        free = new.text
        cursor = 0
    } else {
        free = new.text.replace(SENTINEL.toString(), "")
        cursor = new.selection.end
    }
    cursor = cursor.coerceIn(0, free.length)

    val s = QueryEdit.split(free, cursor)
    val merged = (activePills + s.pills).distinctBy { it.text.lowercase() }
    val raw = QueryEdit.compose(merged, s.free)
    val untouched = s.free == free && new.text.startsWith(SENTINEL) && new.selection.start >= 1
    val value = if (untouched) new else fieldValueFor(s.free, s.cursor)
    return FieldEdit(raw, value)
}

/** Label of a parameter pill ("in Video", "site github.com", "not foo"). */
internal fun pillLabel(token: Token, sections: List<Section>): String = when {
    token.kind == TokenKind.EXCLUDE -> "not " + token.value.trim('"')
    token.key == "in" -> {
        val ids = QueryEdit.resolveSections(token.value, sections)
        val title = if (ids.size == 1) sections.first { it.id in ids }.title else token.value
        "in $title"
    }
    token.key == "is" -> when (token.value) {
        "starred" -> "Starred"
        "top" -> "Top picks"
        "b64" -> "Hidden links"
        else -> token.value
    }
    token.key == "site" -> "site ${token.value}"
    else -> token.text
}
