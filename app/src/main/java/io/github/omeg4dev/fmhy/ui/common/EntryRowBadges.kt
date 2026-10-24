package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.model.Entry

/** Badges and secondary markers shown in the [EntryRow] title line. */
@Composable
internal fun RowScope.EntryTitleBadges(
    entry: Entry,
    alternatives: List<Entry>,
    isNew: Boolean,
    isCrossLink: Boolean,
    onOpenUrl: (String) -> Unit,
    onShowBottomSheet: () -> Unit,
    saveActions: SaveActions,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    if (isNew) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = "NEW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }

    if (alternatives.isNotEmpty()) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .semantics { contentDescription = "${alternatives.size} more on this line" }
                .clickable {
                    onShowBottomSheet()
                },
        ) {
            Text(
                text = "+${alternatives.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }

    if (entry.mirrors.isNotEmpty()) {
        // "7Movies, 2, 3" as on fmhy.net: the comma hugs the name, so no row spacing here.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.offset(x = (-6).dp),
        ) {
            entry.mirrors.forEach { mirror ->
                Text(
                    text = ",",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val mirrorIcon = socialIconFor(mirror.label)
                val mirrorModifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onOpenUrl(mirror.url) }
                    .padding(start = 4.dp, end = 1.dp, top = 2.dp, bottom = 2.dp)
                if (mirrorIcon != null) {
                    Icon(
                        painter = painterResource(mirrorIcon),
                        contentDescription = mirror.label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = mirrorModifier.size(18.dp),
                    )
                } else {
                    Text(
                        text = mirror.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = mirrorModifier
                            .semantics { contentDescription = "Mirror ${mirror.label}" },
                    )
                }
            }
        }
    }

    if (entry.isBase64) {
        Icon(
            imageVector = Icons.Outlined.Key,
            contentDescription = "Hidden link, decoded",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }

    val bookmarksState = LocalBookmarks.current
    val isPrimarySaved = entry.id in bookmarksState.savedIds
    val isAnyAlternativeSaved = alternatives.any { it.id in bookmarksState.savedIds }
    if (isPrimarySaved || isAnyAlternativeSaved) {
        Icon(
            imageVector = Icons.Filled.Bookmark,
            contentDescription = "Saved",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(16.dp)
                .clickable {
                    if (alternatives.isNotEmpty()) {
                        onShowBottomSheet()
                    } else {
                        saveActions.unsave(entry)
                    }
                },
        )
    }

    if (isCrossLink) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }

    if (trailingContent != null) {
        Spacer(Modifier.weight(1f))
        trailingContent()
    }
}

/** Folder chip and note preview line shown below the entry description in [EntryRow]. */
@Composable
internal fun EntryFolderNoteMetadata(
    folderLabel: String?,
    notePreview: String?,
    modifier: Modifier = Modifier,
) {
    Spacer(Modifier.height(4.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        if (folderLabel != null) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = folderLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (!notePreview.isNullOrEmpty()) {
            Text(
                text = notePreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Saved folder chip and user note card displayed in the detail sheet. */
@Composable
internal fun EntrySavedSection(
    savedInfo: SavedInfo,
    onPickFolder: () -> Unit,
    onEditNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .enterStagger(index = 2)
    ) {
        Text(
            text = "My FMHY",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AssistChip(
                onClick = onPickFolder,
                label = { Text(savedInfo.folderName ?: "No folder") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
            )
        }

        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEditNote() }
                .padding(vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (savedInfo.note.isNotBlank()) savedInfo.note else "Add note…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (savedInfo.note.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Header block for single-site entries in [EntryDetailBottomSheet]. */
@Composable
internal fun EntryDetailHeader(
    entry: Entry,
    host: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .enterStagger(index = 0)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntryMarker(entry, size = 22.dp, modifier = Modifier.padding(end = 8.dp))
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (host.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = host,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.description.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Header block for individual alternative sites in [EntryDetailBottomSheet]. */
@Composable
internal fun EntrySiteHeader(
    site: Entry,
    siteHost: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .enterStagger(index = 0)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntryMarker(site, size = 20.dp, modifier = Modifier.padding(end = 8.dp))
            Text(
                text = site.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Normal,
            )
        }
        if (siteHost.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = siteHost,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
