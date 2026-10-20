package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.foundation.BorderStroke
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

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class ListItem(
        val text: String,
        val level: Int = 0,
        val isOrdered: Boolean = false,
        val number: Int = 1,
    ) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    data class FencedCode(val code: String, val language: String? = null) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
    data class Image(val alt: String, val url: String) : MarkdownBlock
}

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    val total = lines.size

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

    while (i < total) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 1. Fenced Code Block
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
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
            val level = headingMatch.groupValues[1].length.coerceAtMost(4)
            val text = headingMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.Heading(level = level, text = text))
            i++
            continue
        }

        // 3. Horizontal Rule
        if (isHr(trimmed)) {
            blocks.add(MarkdownBlock.HorizontalRule)
            i++
            continue
        }

        // 4. Standalone Image
        val imageMatch = Regex("""^!\[(.*?)\]\((.*?)\)$""").matchEntire(trimmed)
        if (imageMatch != null) {
            blocks.add(MarkdownBlock.Image(alt = imageMatch.groupValues[1], url = imageMatch.groupValues[2]))
            i++
            continue
        }

        // 5. Table
        if (trimmed.contains('|') && i + 1 < total && isTableDelimiter(lines[i + 1])) {
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

        // 6. Blockquote: lines starting with >
        if (trimmed.startsWith(">")) {
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
            blocks.add(MarkdownBlock.Blockquote(text = quoteLines.joinToString("\n")))
            continue
        }

        // 7. List Item
        val listMatch = Regex("""^(\s*)([*+-]|\d+\.)\s+(.*)$""").matchEntire(line)
        if (listMatch != null) {
            val indentSpaces = listMatch.groupValues[1].length
            val marker = listMatch.groupValues[2]
            val itemText = listMatch.groupValues[3]
            val isOrdered = marker.last() == '.'
            val number = if (isOrdered) marker.dropLast(1).toIntOrNull() ?: 1 else 1
            val level = (indentSpaces / 2)
            blocks.add(MarkdownBlock.ListItem(text = itemText, level = level, isOrdered = isOrdered, number = number))
            i++
            continue
        }

        // 8. Paragraph
        val paraLines = mutableListOf<String>()
        while (i < total) {
            val pLine = lines[i]
            val pTrimmed = pLine.trim()
            if (pTrimmed.isEmpty()) break
            if (pTrimmed.startsWith("```") || pTrimmed.startsWith("~~~")) break
            if (pTrimmed.startsWith("#")) break
            if (isHr(pTrimmed)) break
            if (pTrimmed.startsWith(">")) break
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
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    MarkdownBlocks(blocks = blocks, modifier = modifier, onOpenUrl = onOpenUrl)
}

@Composable
fun MarkdownBlocks(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Heading -> {
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
                is MarkdownBlock.Paragraph -> {
                    MarkdownText(
                        markdown = block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        onOpenUrl = onOpenUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }
                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.level * 16).dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        val bullet = if (block.isOrdered) "${block.number}." else when (block.level % 3) {
                            0 -> "•"
                            1 -> "◦"
                            else -> "▪"
                        }
                        Text(
                            text = bullet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(if (block.isOrdered) 24.dp else 16.dp),
                        )
                        MarkdownText(
                            markdown = block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            onOpenUrl = onOpenUrl,
                            modifier = Modifier.weight(1f, fill = false),
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
                            MarkdownText(
                                markdown = block.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                onOpenUrl = onOpenUrl,
                            )
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
                    if (block.alt.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = "📷 ${block.alt}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
