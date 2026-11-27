package io.github.omeg4dev.fmhy.ui.saved

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Link
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.ui.common.EntryMarker
import io.github.omeg4dev.fmhy.ui.common.HEADING_PATH_SEPARATOR
import io.github.omeg4dev.fmhy.ui.common.LocalBookmarks
import io.github.omeg4dev.fmhy.ui.common.LocalCrossLinks
import io.github.omeg4dev.fmhy.ui.common.LocalGesturePrefs
import io.github.omeg4dev.fmhy.ui.common.RowAction
import io.github.omeg4dev.fmhy.ui.common.SwipeAction
import io.github.omeg4dev.fmhy.ui.common.SwipeActionRow
import io.github.omeg4dev.fmhy.ui.common.linkIconFor
import io.github.omeg4dev.fmhy.ui.common.linkIconTint
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.rememberSaveActions
import io.github.omeg4dev.fmhy.ui.common.rowGestures
import io.github.omeg4dev.fmhy.ui.common.sectionDisplayTitle

@Composable
internal fun SavedCompactSiteRow(
    item: SavedEntryItem,
    liveEntry: Entry,
    sectionLabel: String,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    onOpenUrl: (String) -> Unit,
    onShowDetails: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
    onMoveTo: () -> Unit,
    onEditNote: () -> Unit,
    onRemove: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gestures = rowGestures(LocalGesturePrefs.current.swapTapHold)
    val crossLinks = LocalCrossLinks.current
    val isCrossLink = remember(liveEntry.url, crossLinks) {
        crossLinks?.resolve(liveEntry.url) != null
    }
    val noteFirstLine = remember(item.bookmark.note) {
        item.bookmark.note.lines().firstOrNull { it.isNotBlank() }?.trim()
    }
    val line2IsNote = !noteFirstLine.isNullOrEmpty()
    val line2Text = when {
        !noteFirstLine.isNullOrEmpty() -> noteFirstLine
        liveEntry.description.isNotBlank() -> liveEntry.description
        else -> liveEntry.headingPath.ifBlank { item.bookmark.headingPath }
    }
    val iconLinks = remember(liveEntry.extraLinks) {
        liveEntry.extraLinks.filter { linkIconFor(it) != null }.take(5)
    }

    SwipeToDismissSavedRow(
        entry = liveEntry,
        enabled = !inSelectionMode,
        onShowSnackbar = onShowSnackbar,
        modifier = modifier,
    ) {
        CompactRowContainer(
            inSelectionMode = inSelectionMode,
            isSelected = isSelected,
            onClick = {
                if (inSelectionMode) {
                    onToggleSelect()
                } else {
                    when (gestures.onTap) {
                        RowAction.OPEN_SITE -> onOpenUrl(liveEntry.url)
                        RowAction.OPEN_MENU -> onShowDetails()
                        RowAction.SHOW_IN_SECTION -> onShowDetails()
                    }
                }
            },
            onLongClick = if (!inSelectionMode) onEnterSelection else null,
            longClickLabel = if (!inSelectionMode) "Select" else null,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = if (!inSelectionMode) 4.dp else 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (inSelectionMode) {
                    SelectionLeadingIndicator(isSelected = isSelected)
                    Spacer(Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        EntryMarker(liveEntry)
                        Text(
                            text = liveEntry.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isCrossLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (liveEntry.tier != Tier.NORMAL) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = sectionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp),
                        )
                    }

                    if (line2Text.isNotBlank() || (!inSelectionMode && iconLinks.isNotEmpty())) {
                        Spacer(Modifier.height(2.dp))
                        CompactRowSecondLine(
                            text = line2Text,
                            isItalic = line2IsNote,
                            iconLinks = if (inSelectionMode) emptyList() else iconLinks,
                            onOpenUrl = onOpenUrl,
                        )
                    }
                }

                if (!inSelectionMode) {
                    SavedRowOverflowMenu(
                        isRemoved = false,
                        onDetailsOrReplacements = onShowDetails,
                        onMoveTo = onMoveTo,
                        onEditNote = onEditNote,
                        onRemove = onRemove,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SavedCompactRemovedRow(
    item: SavedEntryItem,
    sectionLabel: String,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    onOpenUrl: () -> Unit,
    onReplacements: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
    onMoveTo: () -> Unit,
    onEditNote: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val removedText = if (item.removedDateText != null) {
        "No longer listed on FMHY · ${item.removedDateText}"
    } else {
        "No longer listed on FMHY"
    }

    CompactRowContainer(
        inSelectionMode = inSelectionMode,
        isSelected = isSelected,
        onClick = {
            if (inSelectionMode) onToggleSelect() else onOpenUrl()
        },
        onLongClick = if (!inSelectionMode) onEnterSelection else null,
        longClickLabel = if (!inSelectionMode) "Select" else null,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = if (!inSelectionMode) 4.dp else 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inSelectionMode) {
                SelectionLeadingIndicator(isSelected = isSelected)
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = item.bookmark.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = sectionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp),
                    )
                }

                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = removedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!inSelectionMode) {
                        Text(
                            text = "Replacements",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(role = Role.Button, onClick = onReplacements)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (!inSelectionMode) {
                SavedRowOverflowMenu(
                    isRemoved = true,
                    onDetailsOrReplacements = onReplacements,
                    onMoveTo = onMoveTo,
                    onEditNote = onEditNote,
                    onRemove = onRemove,
                )
            }
        }
    }
}

@Composable
internal fun HistoryCompactLiveRow(
    entry: Entry,
    onOpenUrl: (String) -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gestures = rowGestures(LocalGesturePrefs.current.swapTapHold)
    val crossLinks = LocalCrossLinks.current
    val isCrossLink = remember(entry.url, crossLinks) {
        crossLinks?.resolve(entry.url) != null
    }
    val isSaved = entry.id in LocalBookmarks.current.savedIds
    val sectionLabel = remember(entry.sectionId, entry.headingPath) {
        val segments = entry.headingPath
            .split(HEADING_PATH_SEPARATOR)
            .filter { it.isNotBlank() }
            .map { CrossLinks.splitLinkTitle(it).first }
        val rawSec = segments.firstOrNull() ?: entry.sectionId
        sectionDisplayTitle(entry.sectionId, rawSec)
    }
    val line2Text = entry.description.ifBlank { entry.headingPath }
    val iconLinks = remember(entry.extraLinks) {
        entry.extraLinks.filter { linkIconFor(it) != null }.take(5)
    }

    fun performAction(action: RowAction) {
        when (action) {
            RowAction.OPEN_SITE -> onOpenUrl(entry.url)
            RowAction.OPEN_MENU -> onShowDetails()
            RowAction.SHOW_IN_SECTION -> onShowDetails()
        }
    }

    fun labelOf(action: RowAction): String = when (action) {
        RowAction.OPEN_SITE -> "Open site"
        RowAction.OPEN_MENU -> "Show details"
        RowAction.SHOW_IN_SECTION -> "Show in section"
    }

    CompactRowContainer(
        inSelectionMode = false,
        isSelected = false,
        onClick = { performAction(gestures.onTap) },
        onLongClick = { performAction(gestures.onHold) },
        clickLabel = labelOf(gestures.onTap),
        longClickLabel = labelOf(gestures.onHold),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EntryMarker(entry)
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCrossLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (entry.tier != Tier.NORMAL) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isSaved) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = "Saved",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = sectionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
            }

            if (line2Text.isNotBlank() || iconLinks.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                CompactRowSecondLine(
                    text = line2Text,
                    isItalic = false,
                    iconLinks = iconLinks,
                    onOpenUrl = onOpenUrl,
                )
            }
        }
    }
}

@Composable
internal fun HistoryCompactRawRow(
    item: HistoryUiItem,
    onOpenUrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onOpenUrl),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.history.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${item.host} · ${item.timeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactRowContainer(
    inSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    clickLabel: String? = null,
    longClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val haptics = rememberHaptics()
    val semanticsModifier = when {
        inSelectionMode -> Modifier.semantics { selected = isSelected }
        onLongClick != null && longClickLabel != null -> Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(label = longClickLabel) {
                    onLongClick()
                    true
                }
            )
        }
        else -> Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(semanticsModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (onLongClick != null) {
                    {
                        haptics.longPress()
                        onLongClick()
                    }
                } else {
                    null
                },
                onClickLabel = clickLabel,
                onLongClickLabel = longClickLabel,
            ),
        // A tint, not secondaryContainer: the app palettes make that a solid accent that hides the row text.
        color = if (inSelectionMode && isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        },
        content = content,
    )
}

@Composable
private fun SelectionLeadingIndicator(isSelected: Boolean) {
    Icon(
        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun CompactRowSecondLine(
    text: String,
    isItalic: Boolean,
    iconLinks: List<Link>,
    onOpenUrl: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (text.isNotBlank()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        iconLinks.forEach { link ->
            val iconRes = linkIconFor(link)
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = link.label,
                    tint = linkIconTint(link.url),
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable { onOpenUrl(link.url) },
                )
            }
        }
    }
}

@Composable
private fun SavedRowOverflowMenu(
    isRemoved: Boolean,
    onDetailsOrReplacements: () -> Unit,
    onMoveTo: () -> Unit,
    onEditNote: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(if (isRemoved) "Replacements" else "Details") },
                onClick = {
                    expanded = false
                    onDetailsOrReplacements()
                },
            )
            DropdownMenuItem(
                text = { Text("Move to…") },
                onClick = {
                    expanded = false
                    onMoveTo()
                },
            )
            DropdownMenuItem(
                text = { Text("Edit note") },
                onClick = {
                    expanded = false
                    onEditNote()
                },
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun SwipeToDismissSavedRow(
    entry: Entry,
    enabled: Boolean,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val saveActions = rememberSaveActions(onShowSnackbar = onShowSnackbar)
    val currentActions by rememberUpdatedState(saveActions)
    val currentEntry by rememberUpdatedState(entry)
    SwipeActionRow(
        enabled = enabled,
        gestureKey = entry.id,
        actionAtDragStart = { SwipeAction.REMOVE },
        removeIcon = Icons.Outlined.Delete,
        onCommit = { action ->
            if (action == SwipeAction.REMOVE) currentActions.unsave(currentEntry)
        },
        modifier = modifier,
        content = content,
    )
}
