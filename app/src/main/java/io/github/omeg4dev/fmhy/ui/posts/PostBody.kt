package io.github.omeg4dev.fmhy.ui.posts

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.ui.common.MarkdownBlock
import io.github.omeg4dev.fmhy.ui.common.MarkdownBlocks
import io.github.omeg4dev.fmhy.ui.common.MarkdownText
import io.github.omeg4dev.fmhy.ui.common.ReaderStyle
import io.github.omeg4dev.fmhy.ui.common.parseMarkdownBlocks
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics

/** Title and month label shown above a monthly post. */
@Composable
fun PostHeader(title: String, monthLabel: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (monthLabel.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Body of a monthly post: headings with an accent bar, each run of bullets in one card and
 * bullets that start with a link rendered as site rows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostBody(
    markdown: String,
    onOpenUrl: (String) -> Unit,
    onSiteTap: (PostSiteRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = remember(markdown) { groupPostBlocks(parseMarkdownBlocks(markdown)) }
    val haptics = rememberHaptics()
    Column(modifier = modifier.fillMaxWidth()) {
        for (section in sections) {
            section.heading?.let { h ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(top = 24.dp, bottom = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    MarkdownText(
                        markdown = h.text,
                        style = (if (h.level == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                            .copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        onOpenUrl = onOpenUrl,
                    )
                }
            }
            var i = 0
            val blocks = section.blocks
            while (i < blocks.size) {
                val b = blocks[i]
                if (b is MarkdownBlock.ListItem) {
                    val run = mutableListOf<MarkdownBlock.ListItem>()
                    while (i < blocks.size && blocks[i] is MarkdownBlock.ListItem) {
                        run.add(blocks[i] as MarkdownBlock.ListItem)
                        i++
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            run.forEachIndexed { idx, item ->
                                val row = if (item.level == 0 && !item.isOrdered) parsePostSiteRow(item.text) else null
                                if (idx > 0 && row != null) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    )
                                }
                                if (row != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    haptics.click()
                                                    onSiteTap(row)
                                                },
                                                onLongClick = {
                                                    haptics.longPress()
                                                    onOpenUrl(row.url)
                                                },
                                            )
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                    ) {
                                        Text(
                                            text = row.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        if (row.description.isNotBlank()) {
                                            MarkdownText(
                                                markdown = row.description,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                onOpenUrl = onOpenUrl,
                                                modifier = Modifier.padding(top = 2.dp),
                                            )
                                        }
                                    }
                                } else {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                                        MarkdownBlocks(
                                            blocks = listOf(item),
                                            onOpenUrl = onOpenUrl,
                                            readerStyle = ReaderStyle.POST,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    MarkdownBlocks(
                        blocks = listOf(b),
                        onOpenUrl = onOpenUrl,
                        readerStyle = ReaderStyle.POST,
                    )
                    i++
                }
            }
        }
    }
}
