package io.github.omeg4dev.fmhy.ui.whatsnew

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.ui.common.sectionIcon

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChangeCardRow(
    card: ChangeCard,
    sectionTitles: Map<String, String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryLook = card.primaryKind.style()
    val isRemoved = card.primaryKind == ChangeKind.REMOVED

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Leading badge icon (40 dp rounded square)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(primaryLook.containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = primaryLook.icon,
                contentDescription = primaryLook.label,
                tint = primaryLook.contentColor,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Line 1: Name + ⭐ + other kinds badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val isStarred = card.entry?.starredAt != null ||
                    (card.entry?.tier?.let { it >= Tier.STAR } == true) ||
                    ChangeKind.STARRED in card.kinds

                Text(
                    text = if (isStarred) "${card.name} ⭐" else card.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (isRemoved) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    color = if (isRemoved) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                val otherKinds = card.kinds.filter { it != card.primaryKind }
                otherKinds.forEach { otherKind ->
                    val otherLook = otherKind.style()
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = otherLook.containerColor,
                    ) {
                        Text(
                            text = otherLook.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = otherLook.contentColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // Line 2: The change itself by primary kind
            when (card.primaryKind) {
                ChangeKind.ADDED -> {
                    val desc = card.entry?.description?.ifBlank { null } ?: card.name
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ChangeKind.REMOVED -> {
                    Text(
                        text = "Removed · tap for replacements",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ChangeKind.RENAMED -> {
                    if (!card.oldName.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = card.oldName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = TextDecoration.LineThrough,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = " → ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = card.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = primaryLook.contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(
                            text = "Renamed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ChangeKind.URL_CHANGED -> {
                    val formatted = WhatsNewGrouping.formatUrlChange(card.oldUrl, card.url)
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ChangeKind.MOVED -> {
                    val newTitle = sectionTitles[card.sectionId] ?: card.sectionId
                    val oldSecId = card.oldSectionId
                    if (oldSecId != null) {
                        val oldTitle = sectionTitles[oldSecId] ?: oldSecId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = sectionIcon(oldSecId),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = oldTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "→",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                imageVector = sectionIcon(card.sectionId),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = primaryLook.contentColor,
                            )
                            Text(
                                text = newTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryLook.contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(
                            text = "Moved to $newTitle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ChangeKind.STARRED -> {
                    Text(
                        text = "Now starred on FMHY",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ChangeKind.UNSTARRED -> {
                    Text(
                        text = "No longer starred",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ChangeKind.EDITED -> {
                    // detail = "old line\nnew line"; older rows hold only the old line and get no diff.
                    val lines = card.oldLine?.split('\n')
                    if (lines == null || lines.size != 2) {
                        Text(
                            text = "Updated on FMHY",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        EditedWordDiffView(
                            oldLine = lines[0],
                            newLine = lines[1],
                            primaryLook = primaryLook,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Line 3: Section path with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = sectionIcon(card.sectionId),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val secTitle = sectionTitles[card.sectionId] ?: card.sectionId
                val headingPath = card.entry?.headingPath
                val pathDisplay = if (!headingPath.isNullOrBlank()) {
                    if (headingPath.startsWith(secTitle)) headingPath else "$secTitle › $headingPath"
                } else {
                    secTitle
                }
                Text(
                    text = pathDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EditedWordDiffView(
    oldLine: String,
    newLine: String,
    primaryLook: ChangeTypeLook,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    val oldDesc = remember(oldLine) { WordDiff.plainLine(oldLine) }
    val newDesc = remember(newLine) { WordDiff.plainLine(newLine) }
    val segments = remember(oldDesc, newDesc) {
        val diff = WordDiff.wordDiff(oldDesc, newDesc)
        WordDiff.compactDiff(diff, context = 4)
    }

    val annotatedString = remember(segments, colorScheme, primaryLook) {
        buildAnnotatedString {
            segments.forEachIndexed { i, seg ->
                if (i > 0) append(" ")
                when (seg.type) {
                    DiffType.SAME -> {
                        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                            append(seg.text)
                        }
                    }
                    DiffType.REMOVED -> {
                        withStyle(
                            SpanStyle(
                                color = colorScheme.error,
                                background = colorScheme.errorContainer.copy(alpha = 0.5f),
                                textDecoration = TextDecoration.LineThrough,
                            )
                        ) {
                            append(seg.text)
                        }
                    }
                    DiffType.ADDED -> {
                        withStyle(
                            SpanStyle(
                                color = primaryLook.contentColor,
                                background = primaryLook.containerColor,
                            )
                        ) {
                            append(seg.text)
                        }
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable { isExpanded = !isExpanded },
        )
        if (annotatedString.length > 80 || segments.size > 5) {
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
