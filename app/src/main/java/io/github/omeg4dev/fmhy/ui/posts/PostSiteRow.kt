package io.github.omeg4dev.fmhy.ui.posts

import io.github.omeg4dev.fmhy.ui.common.MarkdownBlock

/** A bullet of a monthly post that starts with a link: `**[Name](url)** - description`. */
data class PostSiteRow(val name: String, val url: String, val description: String)

/** One heading of a post and the blocks below it, up to the next `#`/`##` heading. */
data class PostSection(val heading: MarkdownBlock.Heading?, val blocks: List<MarkdownBlock>)

private val DESCRIPTION_SEPARATORS = listOf("-", "–", "—", ",", ":")

/** Returns the site row for a bullet's text, or null when the text does not start with a link. */
fun parsePostSiteRow(text: String): PostSiteRow? {
    var s = text.trim()
    val bold = s.startsWith("**")
    if (bold) s = s.removePrefix("**")
    if (!s.startsWith("[")) return null
    val close = s.indexOf("](")
    if (close < 0) return null
    val label = s.substring(1, close)
    if (label.contains('[') || label.isBlank()) return null
    var i = close + 2
    var depth = 1
    val urlStart = i
    while (i < s.length) {
        when (s[i]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) break
            }
        }
        i++
    }
    if (depth != 0) return null
    val url = s.substring(urlStart, i).trim().removePrefix("<").removeSuffix(">")
    if (url.isBlank()) return null
    var rest = s.substring(i + 1)
    if (bold) {
        if (!rest.startsWith("**")) return null
        rest = rest.removePrefix("**")
    }
    val name = label.replace("**", "").replace("__", "").trim()
    if (name.isBlank()) return null
    var desc = rest.trim()
    for (sep in DESCRIPTION_SEPARATORS) {
        val trimmedSep = sep.trim()
        if (desc.startsWith("$trimmedSep ")) {
            desc = desc.removePrefix(trimmedSep).trim()
            break
        }
    }
    return PostSiteRow(name = name, url = url, description = desc)
}

/** Splits blocks into sections at every level 1 or 2 heading (leading blocks form a heading-less section). */
fun groupPostBlocks(blocks: List<MarkdownBlock>): List<PostSection> {
    val out = mutableListOf<PostSection>()
    var heading: MarkdownBlock.Heading? = null
    var current = mutableListOf<MarkdownBlock>()
    fun flush() {
        if (heading != null || current.isNotEmpty()) out.add(PostSection(heading, current.toList()))
    }
    for (b in blocks) {
        if (b is MarkdownBlock.Heading && b.level <= 2) {
            flush()
            heading = b
            current = mutableListOf()
        } else {
            current.add(b)
        }
    }
    flush()
    return out
}
