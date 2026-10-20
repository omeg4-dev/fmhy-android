package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

sealed interface InlineNode {
    data class Text(val text: String) : InlineNode
    data class Bold(val children: List<InlineNode>) : InlineNode
    data class Italic(val children: List<InlineNode>) : InlineNode
    data class BoldItalic(val children: List<InlineNode>) : InlineNode
    data class Strike(val children: List<InlineNode>) : InlineNode
    data class Code(val code: String) : InlineNode
    data class Link(val children: List<InlineNode>, val url: String) : InlineNode
}

class MarkdownInlineParser(
    private val text: String,
) {
    fun parse(): List<InlineNode> {
        return parseRange(0, text.length, inLink = false)
    }

    private data class LinkParseResult(
        val node: InlineNode,
        val nextIndex: Int,
    )

    private data class BareUrlResult(
        val url: String,
        val nextIndex: Int,
    )

    private fun isEscapable(c: Char): Boolean =
        c in "\\*`_~[]()<>-+.!"

    private fun parseRange(start: Int, end: Int, inLink: Boolean): List<InlineNode> {
        val nodes = mutableListOf<InlineNode>()
        var i = start
        val currentText = StringBuilder()

        fun flushText() {
            if (currentText.isNotEmpty()) {
                nodes.add(InlineNode.Text(currentText.toString()))
                currentText.clear()
            }
        }

        while (i < end) {
            val c = text[i]

            // 1. Escapes
            if (c == '\\' && i + 1 < end) {
                val next = text[i + 1]
                if (isEscapable(next)) {
                    currentText.append(next)
                    i += 2
                    continue
                }
            }

            // 2. Inline Code: `code`
            if (c == '`') {
                val closeIdx = findClosingBacktick(i + 1, end)
                if (closeIdx != -1) {
                    flushText()
                    val codeContent = text.substring(i + 1, closeIdx)
                    nodes.add(InlineNode.Code(codeContent))
                    i = closeIdx + 1
                    continue
                } else {
                    currentText.append(c)
                    i++
                    continue
                }
            }

            // 3. Image: ![alt](url)
            if (!inLink && c == '!' && i + 1 < end && text[i + 1] == '[') {
                val linkParse = tryParseLinkOrImage(i + 1, end, isImage = true)
                if (linkParse != null) {
                    flushText()
                    nodes.add(linkParse.node)
                    i = linkParse.nextIndex
                    continue
                }
            }

            // 4. Link: [label](url)
            if (!inLink && c == '[') {
                val linkParse = tryParseLinkOrImage(i, end, isImage = false)
                if (linkParse != null) {
                    flushText()
                    nodes.add(linkParse.node)
                    i = linkParse.nextIndex
                    continue
                }
            }

            // 5. Autolink: <http://...> or <https://...>
            if (!inLink && c == '<' && (text.startsWith("<http://", i) || text.startsWith("<https://", i))) {
                val closeAngle = text.indexOf('>', i)
                if (closeAngle != -1 && closeAngle < end && !text.substring(i + 1, closeAngle).any { it.isWhitespace() }) {
                    flushText()
                    val url = text.substring(i + 1, closeAngle)
                    nodes.add(InlineNode.Link(listOf(InlineNode.Text(url)), url))
                    i = closeAngle + 1
                    continue
                }
            }

            // 6. Bold Italic: ***text***
            if (text.startsWith("***", i)) {
                if (canOpenEmphasis(i, 3, end)) {
                    val closeIdx = findClosingDelimiter(i + 3, end, "***", isUnderscore = false)
                    if (closeIdx != -1) {
                        flushText()
                        val children = parseRange(i + 3, closeIdx, inLink = inLink)
                        nodes.add(InlineNode.BoldItalic(children))
                        i = closeIdx + 3
                        continue
                    }
                }
            }

            // 7. Bold: **text**
            if (text.startsWith("**", i)) {
                if (canOpenEmphasis(i, 2, end)) {
                    val closeIdx = findClosingDelimiter(i + 2, end, "**", isUnderscore = false)
                    if (closeIdx != -1) {
                        flushText()
                        val children = parseRange(i + 2, closeIdx, inLink = inLink)
                        nodes.add(InlineNode.Bold(children))
                        i = closeIdx + 2
                        continue
                    }
                }
            }

            // 8. Bold: __text__ (word boundary check)
            if (text.startsWith("__", i)) {
                if (canOpenUnderscore(i, 2, end)) {
                    val closeIdx = findClosingDelimiter(i + 2, end, "__", isUnderscore = true)
                    if (closeIdx != -1) {
                        flushText()
                        val children = parseRange(i + 2, closeIdx, inLink = inLink)
                        nodes.add(InlineNode.Bold(children))
                        i = closeIdx + 2
                        continue
                    }
                }
            }

            // 9. Italic: *text*
            if (c == '*' && !text.startsWith("**", i)) {
                if (canOpenEmphasis(i, 1, end)) {
                    val closeIdx = findClosingDelimiter(i + 1, end, "*", isUnderscore = false)
                    if (closeIdx != -1) {
                        flushText()
                        val children = parseRange(i + 1, closeIdx, inLink = inLink)
                        nodes.add(InlineNode.Italic(children))
                        i = closeIdx + 1
                        continue
                    }
                }
            }

            // 10. Italic: _text_ (word boundary check)
            if (c == '_' && !text.startsWith("__", i)) {
                if (canOpenUnderscore(i, 1, end)) {
                    val closeIdx = findClosingDelimiter(i + 1, end, "_", isUnderscore = true)
                    if (closeIdx != -1) {
                        flushText()
                        val children = parseRange(i + 1, closeIdx, inLink = inLink)
                        nodes.add(InlineNode.Italic(children))
                        i = closeIdx + 1
                        continue
                    }
                }
            }

            // 11. Strikethrough: ~~text~~
            if (text.startsWith("~~", i)) {
                if (canOpenEmphasis(i, 2, end)) {
                    val closeIdx = findClosingDelimiter(i + 2, end, "~~", isUnderscore = false)
                    if (closeIdx != -1) {
                        flushText()
                        val children = parseRange(i + 2, closeIdx, inLink = inLink)
                        nodes.add(InlineNode.Strike(children))
                        i = closeIdx + 2
                        continue
                    }
                }
            }

            // 12. Bare URL: http:// or https://
            if (!inLink && (text.startsWith("http://", i) || text.startsWith("https://", i))) {
                val prevChar = if (i > 0) text[i - 1] else null
                if (prevChar == null || !prevChar.isLetterOrDigit()) {
                    val bareUrlResult = tryParseBareUrl(i, end)
                    if (bareUrlResult != null) {
                        flushText()
                        nodes.add(InlineNode.Link(listOf(InlineNode.Text(bareUrlResult.url)), bareUrlResult.url))
                        i = bareUrlResult.nextIndex
                        continue
                    }
                }
            }

            // Plain character
            currentText.append(c)
            i++
        }

        flushText()
        return nodes
    }

    private fun findClosingBacktick(start: Int, end: Int): Int {
        var j = start
        while (j < end) {
            if (text[j] == '`') return j
            j++
        }
        return -1
    }

    private fun canOpenEmphasis(i: Int, delimiterLen: Int, end: Int): Boolean {
        val nextIdx = i + delimiterLen
        return nextIdx < end && !text[nextIdx].isWhitespace()
    }

    private fun canOpenUnderscore(i: Int, delimiterLen: Int, end: Int): Boolean {
        // Left word boundary check: not preceded by a letter/digit
        if (i > 0 && text[i - 1].isLetterOrDigit()) return false
        val nextIdx = i + delimiterLen
        return nextIdx < end && !text[nextIdx].isWhitespace() && text[nextIdx] != '_'
    }

    private fun findClosingDelimiter(
        start: Int,
        end: Int,
        delimiter: String,
        isUnderscore: Boolean,
    ): Int {
        var j = start
        val len = delimiter.length
        while (j <= end - len) {
            // Escapes
            if (text[j] == '\\') {
                j += 2
                continue
            }
            // Code spans
            if (text[j] == '`') {
                val closeBacktick = findClosingBacktick(j + 1, end)
                if (closeBacktick != -1) {
                    j = closeBacktick + 1
                    continue
                } else {
                    j++
                    continue
                }
            }
            // Links / Images: skip nested [label](url) so delimiters inside URLs aren't matched
            if (text[j] == '[' || (text[j] == '!' && j + 1 < end && text[j + 1] == '[')) {
                val startBracket = if (text[j] == '!') j + 1 else j
                val linkParse = tryParseLinkOrImage(startBracket, end, isImage = text[j] == '!')
                if (linkParse != null) {
                    j = linkParse.nextIndex
                    continue
                }
            }

            if (text.startsWith(delimiter, j)) {
                // Must not be preceded by whitespace
                if (j > 0 && !text[j - 1].isWhitespace()) {
                    if (isUnderscore) {
                        // Right word boundary check: cannot be followed by letter/digit or underscore
                        val afterIdx = j + len
                        if (text[j - 1] != '_' && (afterIdx == end || !text[afterIdx].isLetterOrDigit())) {
                            return j
                        }
                    } else {
                        // For * or **, avoid matching if part of a longer sequence like ***
                        if (delimiter == "*" && j + 1 < end && text[j + 1] == '*') {
                            // Don't match single * when it's **
                            j++
                            continue
                        }
                        if (delimiter == "**" && j + 2 < end && text[j + 2] == '*') {
                            // Don't match ** when it's ***
                            j++
                            continue
                        }
                        return j
                    }
                }
            }
            j++
        }
        return -1
    }

    private fun tryParseLinkOrImage(
        startBracket: Int,
        end: Int,
        isImage: Boolean,
    ): LinkParseResult? {
        // startBracket points to '['
        var bracketDepth = 1
        var j = startBracket + 1
        var closeBracket = -1

        while (j < end) {
            val c = text[j]
            if (c == '\\' && j + 1 < end) {
                j += 2
                continue
            }
            if (c == '`') {
                val closeBacktick = findClosingBacktick(j + 1, end)
                if (closeBacktick != -1) {
                    j = closeBacktick + 1
                    continue
                }
            }
            if (c == '[') {
                bracketDepth++
            } else if (c == ']') {
                bracketDepth--
                if (bracketDepth == 0) {
                    closeBracket = j
                    break
                }
            }
            j++
        }

        if (closeBracket == -1 || closeBracket + 1 >= end || text[closeBracket + 1] != '(') {
            return null
        }

        val urlStart = closeBracket + 2
        if (urlStart >= end) return null

        var rawUrl: String
        var closeParen = -1

        if (text[urlStart] == '<') {
            // [label](<url with spaces>)
            val closeAngle = text.indexOf('>', urlStart)
            if (closeAngle != -1 && closeAngle + 1 < end && text[closeAngle + 1] == ')') {
                rawUrl = text.substring(urlStart + 1, closeAngle).trim()
                closeParen = closeAngle + 1
            } else {
                return null
            }
        } else {
            // Balanced parentheses scan
            var parenDepth = 1
            var k = urlStart
            while (k < end) {
                val c = text[k]
                if (c == '\\' && k + 1 < end) {
                    k += 2
                    continue
                }
                if (c == '(') {
                    parenDepth++
                } else if (c == ')') {
                    parenDepth--
                    if (parenDepth == 0) {
                        closeParen = k
                        break
                    }
                }
                k++
            }
            if (closeParen == -1) return null
            val insideParen = text.substring(urlStart, closeParen).trim()
            rawUrl = insideParen.substringBefore(' ')
        }

        val label = text.substring(startBracket + 1, closeBracket)

        val node = if (isImage) {
            val alt = label.trim()
            val display = "🖼 " + if (alt.isBlank()) "image" else alt
            InlineNode.Link(listOf(InlineNode.Text(display)), rawUrl)
        } else {
            val labelNodes = MarkdownInlineParser(label).parseRange(0, label.length, inLink = true)
            InlineNode.Link(labelNodes, rawUrl)
        }

        return LinkParseResult(node, closeParen + 1)
    }

    private fun tryParseBareUrl(start: Int, end: Int): BareUrlResult? {
        var k = start
        var openParens = 0

        while (k < end) {
            val c = text[k]
            if (c.isWhitespace() || c in "<>[]\"'`") break
            if (c == '(') {
                openParens++
            } else if (c == ')') {
                if (openParens > 0) {
                    openParens--
                } else {
                    // Outer closing paren; stop before it
                    break
                }
            }
            k++
        }

        if (k == start) return null

        // Strip trailing punctuation: .,;:!?
        while (k > start && text[k - 1] in ".,;:!?") {
            k--
        }

        val url = text.substring(start, k)
        if (url == "http://" || url == "https://") return null

        return BareUrlResult(url, k)
    }
}

fun renderInlineToAnnotatedString(
    nodes: List<InlineNode>,
    linkColor: Color,
    onOpenUrl: ((String) -> Unit)? = null,
): AnnotatedString {
    return buildAnnotatedString {
        renderNodes(nodes, linkColor, onOpenUrl)
    }
}

private fun AnnotatedString.Builder.renderNodes(
    nodes: List<InlineNode>,
    linkColor: Color,
    onOpenUrl: ((String) -> Unit)?,
) {
    for (node in nodes) {
        when (node) {
            is InlineNode.Text -> append(node.text)
            is InlineNode.Bold -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    renderNodes(node.children, linkColor, onOpenUrl)
                }
            }
            is InlineNode.Italic -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    renderNodes(node.children, linkColor, onOpenUrl)
                }
            }
            is InlineNode.BoldItalic -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    renderNodes(node.children, linkColor, onOpenUrl)
                }
            }
            is InlineNode.Strike -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    renderNodes(node.children, linkColor, onOpenUrl)
                }
            }
            is InlineNode.Code -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = linkColor.copy(alpha = 0.12f),
                    )
                ) {
                    append("\u2009${node.code}\u2009")
                }
            }
            is InlineNode.Link -> {
                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium,
                    )
                )
                if (onOpenUrl != null) {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = node.url,
                            styles = linkStyles,
                            linkInteractionListener = { onOpenUrl(node.url) },
                        )
                    ) {
                        renderNodes(node.children, linkColor, onOpenUrl)
                    }
                } else {
                    withLink(
                        LinkAnnotation.Url(
                            url = node.url,
                            styles = linkStyles,
                        )
                    ) {
                        renderNodes(node.children, linkColor, onOpenUrl)
                    }
                }
            }
        }
    }
}

fun parseMarkdownInline(
    text: String,
    linkColor: Color,
    onOpenUrl: ((String) -> Unit)? = null,
): AnnotatedString {
    val nodes = MarkdownInlineParser(text).parse()
    return renderInlineToAnnotatedString(nodes, linkColor, onOpenUrl)
}
