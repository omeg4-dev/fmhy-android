package io.github.omeg4dev.fmhy.data.search

enum class TokenKind { PARAM, EXCLUDE, PHRASE, WORD }

/**
 * One token of a raw query string. [key] is `in`, `is` or `site` for a [TokenKind.PARAM] (and for a bare
 * `in:` / `site:` without a value, which is a WORD); [value] is the unquoted payload; [text] the exact source
 * text; [closed] is false only for a quote that was never closed. [end] is exclusive.
 */
data class Token(
    val start: Int,
    val end: Int,
    val kind: TokenKind,
    val key: String?,
    val value: String,
    val text: String,
    val closed: Boolean,
) {
    /** Tokens that render as a pill in the search bar. */
    val isPill: Boolean get() = (kind == TokenKind.PARAM || kind == TokenKind.EXCLUDE) && closed
}
