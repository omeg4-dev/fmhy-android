package io.github.omeg4dev.fmhy.ui.home.blocks

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.ui.common.displayTitle
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics

fun formatEntryCategory(headingPath: String, sectionTitle: String?): String {
    val segments = headingPath
        .split(Regex("""\s*[›>]\s*"""))
        .filter { it.isNotBlank() }
        .map { CrossLinks.splitLinkTitle(it).first }
    return segments.drop(1).lastOrNull() ?: sectionTitle.orEmpty()
}

fun LazyListScope.randomPickBlock(
    pick: Entry?,
    sections: List<Section>,
    onOpenUrl: (String) -> Unit,
    onShowDetails: (Entry) -> Unit,
    onRefresh: () -> Unit,
) {
    if (pick != null) {
        item(key = "random-pick") {
            val haptics = rememberHaptics()
            val sectionTitle = sections.firstOrNull { it.id == pick.sectionId }?.displayTitle
            val category = remember(pick.headingPath, sectionTitle) {
                formatEntryCategory(pick.headingPath, sectionTitle)
            }
            val labelMediumStyle = MaterialTheme.typography.labelMedium
            val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
            val annotatedText = remember(pick.name, category, labelMediumStyle, onSurfaceVariant) {
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(pick.name)
                    }
                    if (category.isNotEmpty()) {
                        append("  ")
                        withStyle(
                            SpanStyle(
                                fontSize = labelMediumStyle.fontSize,
                                fontWeight = FontWeight.Normal,
                                color = onSurfaceVariant,
                            )
                        ) {
                            append(category)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Random pick",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .combinedClickable(
                            onClick = { onOpenUrl(pick.url) },
                            onLongClick = {
                                haptics.longPress()
                                onShowDetails(pick)
                            },
                        ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Casino,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = annotatedText,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reroll random pick",
                            )
                        }
                    }
                }
            }
        }
    }
}
