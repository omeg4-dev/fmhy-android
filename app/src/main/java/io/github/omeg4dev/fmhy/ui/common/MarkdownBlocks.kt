package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

typealias MdBlock = MarkdownBlock

enum class ReaderStyle {
    GUIDE,
    POST,
    DOC,
}

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class ListItem(
        val text: String,
        val level: Int = 0,
        val isOrdered: Boolean = false,
        val number: Int = 1,
    ) : MarkdownBlock
    data class Blockquote(val text: String, val children: List<MarkdownBlock> = emptyList()) : MarkdownBlock
    data class Callout(
        val type: String,
        val title: String? = null,
        val children: List<MarkdownBlock> = emptyList(),
    ) : MarkdownBlock
    data class FencedCode(val code: String, val language: String? = null) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
    data class Image(val alt: String, val url: String) : MarkdownBlock
}

/**
 * Shifts top-level headings so the shallowest one becomes level 1. Docs like the beginners guide use `###` for
 * their main sections, which otherwise rendered as small bold lines instead of section headings.
 */
fun normalizeHeadingLevels(blocks: List<MarkdownBlock>): List<MarkdownBlock> {
    val minLevel = blocks.filterIsInstance<MarkdownBlock.Heading>().minOfOrNull { it.level } ?: return blocks
    if (minLevel <= 1) return blocks
    return blocks.map { if (it is MarkdownBlock.Heading) it.copy(level = it.level - minLevel + 1) else it }
}

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    val total = lines.size
    val indentStack = mutableListOf(0)

    fun isHr(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length < 3) return false
        val first = trimmed[0]
        if (first != '*' && first != '-' && first != '_') return false
        return trimmed.all { it == first || it.isWhitespace() } && trimmed.count { it == first } >= 3
    }

    fun isTableDelimiter(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.contains('|') && !trimmed.contains('-')) return false
        val cells = trimmed.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        return cells.isNotEmpty() && cells.all { it.matches(Regex("""^:?-+:?$""")) }
    }

    val calloutHeaderRegex = Regex("""^\[!(TIP|INFO|NOTE|WARNING|DANGER|DETAILS)\](?:\s+(.*))?$""", RegexOption.IGNORE_CASE)
    val containerStartRegex = Regex("""^:::\s*(tip|info|note|warning|danger|details)\b(?:\s+(.*))?$""", RegexOption.IGNORE_CASE)
    val admonitionRegex = Regex("""^!!!\s*(note|tip|warning|danger|info)\b(?:\s+(.*))?$""", RegexOption.IGNORE_CASE)

    while (i < total) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            indentStack.clear()
            indentStack.add(0)
            i++
            continue
        }

        // 1. Fenced Code Block
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            indentStack.clear(); indentStack.add(0)
            val fence = if (trimmed.startsWith("```")) "```" else "~~~"
            val language = trimmed.removePrefix(fence).trim().takeIf { it.isNotEmpty() }
            val codeLines = mutableListOf<String>()
            i++
            while (i < total) {
                val codeLine = lines[i]
                if (codeLine.trim().startsWith(fence)) {
                    i++
                    break
                }
                codeLines.add(codeLine)
                i++
            }
            blocks.add(MarkdownBlock.FencedCode(code = codeLines.joinToString("\n"), language = language))
            continue
        }

        // 2. Heading: # to ####
        val headingMatch = Regex("""^(#{1,6})\s+(.*)$""").matchEntire(trimmed)
        if (headingMatch != null) {
            indentStack.clear(); indentStack.add(0)
            val level = headingMatch.groupValues[1].length.coerceAtMost(4)
            val text = headingMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.Heading(level = level, text = text))
            i++
            continue
        }

        // 3. Horizontal Rule
        if (isHr(trimmed)) {
            indentStack.clear(); indentStack.add(0)
            blocks.add(MarkdownBlock.HorizontalRule)
            i++
            continue
        }

        // 4. Standalone Image
        val imageMatch = Regex("""^!\[(.*?)\]\((.*?)\)$""").matchEntire(trimmed)
        if (imageMatch != null) {
            indentStack.clear(); indentStack.add(0)
            blocks.add(MarkdownBlock.Image(alt = imageMatch.groupValues[1], url = imageMatch.groupValues[2]))
            i++
            continue
        }

        // 5. Container fences: :::tip [title] ... :::
        val containerMatch = containerStartRegex.matchEntire(trimmed)
        if (containerMatch != null) {
            indentStack.clear(); indentStack.add(0)
            val rawType = containerMatch.groupValues[1]
            val type = when (rawType.lowercase()) {
                "details" -> "NOTE"
                else -> rawType.uppercase()
            }
            val title = containerMatch.groupValues[2].trim().takeIf { it.isNotEmpty() }
            val containerLines = mutableListOf<String>()
            i++
            while (i < total && !lines[i].trim().equals(":::", ignoreCase = true)) {
                containerLines.add(lines[i])
                i++
            }
            if (i < total && lines[i].trim().equals(":::", ignoreCase = true)) {
                i++
            }
            val children = parseMarkdownBlocks(containerLines.joinToString("\n"))
            blocks.add(MarkdownBlock.Callout(type = type, title = title, children = children))
            continue
        }

        // 6. Python-markdown style admonitions: !!!note text
        val admMatch = admonitionRegex.matchEntire(trimmed)
        if (admMatch != null) {
            indentStack.clear(); indentStack.add(0)
            val type = admMatch.groupValues[1].uppercase()
            val firstLine = admMatch.groupValues[2].trim()
            val admLines = mutableListOf<String>()
            if (firstLine.isNotEmpty()) {
                admLines.add(firstLine)
            }
            i++
            while (i < total) {
                val nextLine = lines[i]
                if (nextLine.startsWith("    ") || nextLine.startsWith("\t")) {
                    admLines.add(nextLine.trimStart())
                    i++
                } else {
                    break
                }
            }
            val children = parseMarkdownBlocks(admLines.joinToString("\n"))
            blocks.add(MarkdownBlock.Callout(type = type, title = null, children = children))
            continue
        }

        // 7. Table
        if (trimmed.contains('|') && i + 1 < total && isTableDelimiter(lines[i + 1])) {
            indentStack.clear(); indentStack.add(0)
            val headerCells = trimmed.split('|').map { it.trim() }.let { list ->
                var sub = list
                if (trimmed.startsWith("|") && sub.isNotEmpty()) sub = sub.drop(1)
                if (trimmed.endsWith("|") && sub.isNotEmpty()) sub = sub.dropLast(1)
                sub
            }
            i += 2
            val rowList = mutableListOf<List<String>>()
            while (i < total) {
                val rowLine = lines[i].trim()
                if (rowLine.isEmpty() || !rowLine.contains('|')) break
                val cells = rowLine.split('|').map { it.trim() }.let { list ->
                    var sub = list
                    if (rowLine.startsWith("|") && sub.isNotEmpty()) sub = sub.drop(1)
                    if (rowLine.endsWith("|") && sub.isNotEmpty()) sub = sub.dropLast(1)
                    sub
                }
                rowList.add(cells)
                i++
            }
            blocks.add(MarkdownBlock.Table(headers = headerCells, rows = rowList))
            continue
        }

        // 8. Blockquote or Callout: lines starting with >
        if (trimmed.startsWith(">")) {
            indentStack.clear(); indentStack.add(0)
            val quoteLines = mutableListOf<String>()
            while (i < total) {
                val qLine = lines[i].trim()
                if (qLine.startsWith(">")) {
                    quoteLines.add(qLine.removePrefix(">").trimStart())
                    i++
                } else if (qLine.isEmpty() && i + 1 < total && lines[i + 1].trim().startsWith(">")) {
                    quoteLines.add("")
                    i++
                } else {
                    break
                }
            }

            // Check if this blockquote is a GitHub-style alert / callout: > [!TIP] title
            val firstLine = quoteLines.firstOrNull()?.trim().orEmpty()
            val calloutMatch = calloutHeaderRegex.matchEntire(firstLine)

            if (calloutMatch != null) {
                val rawType = calloutMatch.groupValues[1]
                val type = when (rawType.lowercase()) {
                    "details" -> "NOTE"
                    else -> rawType.uppercase()
                }
                val title = calloutMatch.groupValues[2].trim().takeIf { it.isNotEmpty() }
                val remainingText = quoteLines.drop(1).joinToString("\n")
                val children = parseMarkdownBlocks(remainingText)
                blocks.add(MarkdownBlock.Callout(type = type, title = title, children = children))
            } else {
                val fullQuoteText = quoteLines.joinToString("\n")
                val children = parseMarkdownBlocks(fullQuoteText)
                blocks.add(MarkdownBlock.Blockquote(text = fullQuoteText, children = children))
            }
            continue
        }

        // 9. List Item
        val listMatch = Regex("""^(\s*)([*+-]|\d+\.)\s+(.*)$""").matchEntire(line)
        if (listMatch != null) {
            val indentSpaces = listMatch.groupValues[1].length
            val marker = listMatch.groupValues[2]
            var itemText = listMatch.groupValues[3]
            val isOrdered = marker.last() == '.'
            val number = if (isOrdered) marker.dropLast(1).toIntOrNull() ?: 1 else 1

            if (indentSpaces > indentStack.last()) {
                indentStack.add(indentSpaces)
            } else if (indentSpaces < indentStack.last()) {
                while (indentStack.size > 1 && indentSpaces < indentStack.last()) {
                    indentStack.removeAt(indentStack.size - 1)
                }
            }
            val level = indentStack.size - 1

            i++
            // List continuation lines: indented under list item, not themselves a list marker
            while (i < total) {
                val nextLine = lines[i]
                val nextTrimmed = nextLine.trim()
                if (nextTrimmed.isEmpty()) break
                if (nextTrimmed.startsWith("```") || nextTrimmed.startsWith("~~~")) break
                if (nextTrimmed.startsWith("#")) break
                if (isHr(nextTrimmed)) break
                if (nextTrimmed.startsWith(">")) break
                if (nextTrimmed.startsWith(":::") || nextTrimmed.startsWith("!!!")) break
                if (Regex("""^(\s*)([*+-]|\d+\.)\s+""").containsMatchIn(nextLine)) break
                if (isTableDelimiter(nextTrimmed)) break

                val leadingSpaces = nextLine.takeWhile { it.isWhitespace() }.length
                if (leadingSpaces > 0 || nextLine.startsWith("\t")) {
                    itemText += " " + nextTrimmed
                    i++
                } else {
                    break
                }
            }

            blocks.add(MarkdownBlock.ListItem(text = itemText, level = level, isOrdered = isOrdered, number = number))
            continue
        } else {
            indentStack.clear(); indentStack.add(0)
        }

        // 10. Paragraph
        val paraLines = mutableListOf<String>()
        while (i < total) {
            val pLine = lines[i]
            val pTrimmed = pLine.trim()
            if (pTrimmed.isEmpty()) break
            if (pTrimmed.startsWith("```") || pTrimmed.startsWith("~~~")) break
            if (pTrimmed.startsWith("#")) break
            if (isHr(pTrimmed)) break
            if (pTrimmed.startsWith(">")) break
            if (pTrimmed.startsWith(":::") || pTrimmed.startsWith("!!!")) break
            if (Regex("""^(\s*)([*+-]|\d+\.)\s+""").containsMatchIn(pLine)) break
            if (Regex("""^!\[(.*?)\]\((.*?)\)$""").matches(pTrimmed)) break
            if (pTrimmed.contains('|') && i + 1 < total && isTableDelimiter(lines[i + 1])) break

            paraLines.add(pTrimmed)
            i++
        }
        if (paraLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString(" ")))
        }
    }

    return blocks
}

@Composable
fun MarkdownBlocks(
    markdown: String,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit = {},
    readerStyle: ReaderStyle? = null,
) {
    val blocks = remember(markdown) { normalizeHeadingLevels(parseMarkdownBlocks(markdown)) }
    MarkdownBlocks(
        blocks = blocks,
        modifier = modifier,
        onOpenUrl = onOpenUrl,
        readerStyle = readerStyle,
    )
}

@Composable
fun MarkdownBlocks(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit = {},
    readerStyle: ReaderStyle? = null,
) {
    val isGuideOrDoc = readerStyle == ReaderStyle.GUIDE || readerStyle == ReaderStyle.DOC

    Column(modifier = modifier.fillMaxWidth()) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Heading -> {
                    if (isGuideOrDoc) {
                        if (block.level == 2) {
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp,
                            )
                        }
                        val (style, topPad, botPad) = when (block.level) {
                            1 -> Triple(MaterialTheme.typography.titleLarge, 28.dp, 8.dp)
                            2 -> Triple(MaterialTheme.typography.titleMedium, 0.dp, 6.dp)
                            3 -> Triple(MaterialTheme.typography.titleSmall, 14.dp, 4.dp)
                            else -> Triple(MaterialTheme.typography.titleSmall, 10.dp, 4.dp)
                        }
                        val headingColor = if (block.level <= 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        MarkdownText(
                            markdown = block.text,
                            style = style.copy(fontWeight = FontWeight.Bold),
                            color = headingColor,
                            onOpenUrl = onOpenUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = topPad, bottom = botPad),
                        )
                    } else {
                        val (style, topPad, botPad) = when (block.level) {
                            1 -> Triple(MaterialTheme.typography.headlineMedium, 20.dp, 8.dp)
                            2 -> Triple(MaterialTheme.typography.titleLarge, 16.dp, 6.dp)
                            3 -> Triple(MaterialTheme.typography.titleMedium, 12.dp, 4.dp)
                            else -> Triple(MaterialTheme.typography.titleSmall, 10.dp, 4.dp)
                        }
                        MarkdownText(
                            markdown = block.text,
                            style = style.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            onOpenUrl = onOpenUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = topPad, bottom = botPad),
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    val bodyStyle = if (isGuideOrDoc) {
                        MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.5f
                        )
                    } else {
                        MaterialTheme.typography.bodyMedium
                    }
                    val paraPad = if (isGuideOrDoc) 6.dp else 4.dp
                    MarkdownText(
                        markdown = block.text,
                        style = bodyStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        onOpenUrl = onOpenUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = paraPad),
                    )
                }
                is MarkdownBlock.ListItem -> {
                    val itemPad = if (isGuideOrDoc) 3.dp else 2.dp
                    val itemStyle = if (isGuideOrDoc) {
                        MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.5f
                        )
                    } else {
                        MaterialTheme.typography.bodyMedium
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.level * 16).dp, top = itemPad, bottom = itemPad),
                        verticalAlignment = Alignment.Top,
                    ) {
                        val bullet = if (block.isOrdered) "${block.number}." else when (block.level % 3) {
                            0 -> "•"
                            1 -> "◦"
                            else -> "▪"
                        }
                        Text(
                            text = bullet,
                            style = itemStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(if (block.isOrdered) 24.dp else 16.dp),
                        )
                        MarkdownText(
                            markdown = block.text,
                            style = itemStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            onOpenUrl = onOpenUrl,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
                is MarkdownBlock.Callout -> {
                    CalloutBox(
                        type = block.type,
                        title = block.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        MarkdownBlocks(
                            blocks = block.children,
                            onOpenUrl = onOpenUrl,
                        )
                    }
                }
                is MarkdownBlock.Blockquote -> {
                    val borderColor = MaterialTheme.colorScheme.primary
                    Surface(
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .drawBehind {
                                drawLine(
                                    color = borderColor,
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = 3.dp.toPx(),
                                )
                            },
                    ) {
                        Box(modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
                            if (block.children.isNotEmpty()) {
                                MarkdownBlocks(
                                    blocks = block.children,
                                    onOpenUrl = onOpenUrl,
                                )
                            } else {
                                MarkdownText(
                                    markdown = block.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    onOpenUrl = onOpenUrl,
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.FencedCode -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            if (!block.language.isNullOrBlank()) {
                                Text(
                                    text = block.language,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                            Text(
                                text = block.code,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                is MarkdownBlock.Table -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            if (block.headers.isNotEmpty()) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    block.headers.forEach { h ->
                                        Box(modifier = Modifier.widthIn(min = 100.dp).padding(8.dp)) {
                                            MarkdownText(
                                                markdown = "**$h**",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                onOpenUrl = onOpenUrl,
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            block.rows.forEachIndexed { idx, row ->
                                if (idx > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                }
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    row.forEach { cell ->
                                        Box(modifier = Modifier.widthIn(min = 100.dp).padding(8.dp)) {
                                            MarkdownText(
                                                markdown = cell,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                onOpenUrl = onOpenUrl,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                is MarkdownBlock.Image -> {
                    val label = if (block.alt.isBlank()) "🖼 View image" else "🖼 View image — ${block.alt}"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable { onOpenUrl(block.url) },
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
