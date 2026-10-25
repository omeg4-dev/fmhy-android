package io.github.omeg4dev.fmhy.data.safety

private val MARKDOWN_LINK_RE = Regex("""\[([^\]]*)\]\([^)]*\)""")
// A URL token preceded by an optional separator+whitespace: remove the separator too
private val SEP_THEN_URL_RE = Regex("""(?:[ \t]*(?:-{1,2}|–|—|:)[ \t]+)?https?://\S+""")
// URL in parens: remove the parens too when they become empty
private val PAREN_URL_RE = Regex("""\(\s*https?://\S*\s*\)""")
// Bare www link (no http), optionally preceded by a separator
private val SEP_THEN_WWW_RE = Regex("""(?:[ \t]*(?:-{1,2}|–|—|:)[ \t]+)?(?<!\S)www\.\S+""")
// Trailing separators left at end of string
private val TRAILING_SEP_RE = Regex("""[ \t]*(?:[-–—:/]|\s)+${'$'}""")
// Leading separators at start of string
private val LEADING_SEP_RE = Regex("""^(?:[-–—:/]|\s)+""")
private val MULTI_SPACE_RE = Regex("""[ \t]{2,}""")

/**
 * An unsafe-list reason without raw links (links can't be opened from where reasons are shown).
 * Null when nothing readable is left.
 */
fun readableReason(raw: String?): String? {
    if (raw.isNullOrBlank()) return null

    var text = raw

    // Step 2: Replace markdown links [label](url) → label
    text = MARKDOWN_LINK_RE.replace(text) { it.groupValues[1] }

    // Step 3: Remove URLs in parens first (to avoid leaving empty parens)
    text = PAREN_URL_RE.replace(text, "")

    // Remove separator+URL pairs and bare URLs (removing the separator if immediately before URL)
    text = SEP_THEN_URL_RE.replace(text, "")
    text = SEP_THEN_WWW_RE.replace(text, "")

    // Step 4: Tidy up trailing/leading dangling separators
    text = TRAILING_SEP_RE.replace(text, "")
    text = LEADING_SEP_RE.replace(text, "")

    // Collapse repeated whitespace
    text = MULTI_SPACE_RE.replace(text, " ")

    // Trim
    text = text.trim()

    return text.ifBlank { null }
}
