package io.github.omeg4dev.fmhy.ui.common

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Segment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Entry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailBottomSheet(
    entry: Entry,
    alternatives: List<Entry> = emptyList(),
    history: List<ChangeItem>? = null,
    onShowInSection: (() -> Unit)? = null,
    followState: FollowState? = null,
    onToggleFollow: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
    onShowSnackbar: (String) -> Unit = {},
    savedInfo: SavedInfo? = null,
    onEditNote: ((String) -> Unit)? = null,
    onMoveToFolder: ((Long?) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bookmarksState = LocalBookmarks.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val saveActions = rememberSaveActions(onShowSnackbar = onShowSnackbar)
    val isSaved = entry.id in bookmarksState.savedIds || savedInfo != null
    val currentSavedInfo = savedInfo ?: bookmarksState.savedInfo(entry.id) ?: (if (isSaved) SavedInfo() else null)
    val editNoteAction = onEditNote ?: { note: String -> bookmarksState.setNote(entry.id, note) }
    val moveToFolderAction = onMoveToFolder ?: { folderId: Long? -> bookmarksState.moveToFolder(entry.id, folderId) }

    var showFolderPicker by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteInput by remember(currentSavedInfo?.note) { mutableStateOf(currentSavedInfo?.note.orEmpty()) }
    var newFolderName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            if (alternatives.isEmpty()) {
                val crossLinks = LocalCrossLinks.current
                val host = remember(entry.url, crossLinks) {
                    // A link to another FMHY page opens in the app: say so instead of "reddit.com".
                    val target = crossLinks?.resolve(entry.url)
                    if (target != null) {
                        val heading = crossLinks.headingTitle(target)
                        if (heading != null && heading != entry.name) "Opens in the app · $heading" else "Opens in the app"
                    } else try {
                        Uri.parse(entry.url).host?.removePrefix("www.").orEmpty()
                    } catch (_: Exception) {
                        ""
                    }
                }

                // Header
                EntryDetailHeader(entry = entry, host = host)

                // Action row
                EntryPrimaryActionRow(
                    entry = entry,
                    isSaved = isSaved,
                    saveActions = saveActions,
                    haptics = haptics,
                    onOpenUrl = onOpenUrl,
                    onCopyLink = onCopyLink,
                    onShareLink = onShareLink,
                    onDismiss = onDismiss,
                )

                if (isSaved && currentSavedInfo != null) {
                    EntrySavedSection(
                        savedInfo = currentSavedInfo,
                        onPickFolder = { showFolderPicker = true },
                        onEditNote = {
                            noteInput = currentSavedInfo.note
                            showNoteDialog = true
                        },
                    )
                }

                val origUrl = entry.originalUrl
                if (entry.isBase64 && !origUrl.isNullOrEmpty()) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Open original (base64)") },
                        supportingContent = { Text(origUrl, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                        modifier = Modifier.clickable { onOpenUrl(origUrl) }
                    )
                }

                if (entry.mirrors.isNotEmpty()) {
                    Text(
                        text = "Mirrors",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    entry.mirrors.forEach { mirror ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("Mirror ${mirror.label}") },
                            supportingContent = { Text(mirror.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable { onOpenUrl(mirror.url) }
                        )
                    }
                }

                if (entry.extraLinks.isNotEmpty()) {
                    Text(
                        text = "Extra Links",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    entry.extraLinks.forEach { extra ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(extra.label) },
                            supportingContent = { Text(extra.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable { onOpenUrl(extra.url) }
                        )
                    }
                }
            } else {
                val allSites = listOf(entry) + alternatives
                allSites.forEachIndexed { index, site ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = if (index == 1) 12.dp else 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }

                    val siteHost = try {
                        Uri.parse(site.url).host?.removePrefix("www.").orEmpty()
                    } catch (_: Exception) {
                        ""
                    }
                    val isSiteSaved = site.id in bookmarksState.savedIds

                    if (index > 0) {
                        // Alternatives on the same line: one compact row, small icon actions.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 12.dp)
                                .enterStagger(index = index),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EntryMarker(site, size = 16.dp, modifier = Modifier.padding(end = 8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = site.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (siteHost.isNotEmpty()) {
                                    Text(
                                        text = siteHost,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                            EntryAltActions(
                                site = site,
                                isSiteSaved = isSiteSaved,
                                saveActions = saveActions,
                                haptics = haptics,
                                onOpenUrl = onOpenUrl,
                                onCopyLink = onCopyLink,
                                onShareLink = onShareLink,
                            )
                        }
                    } else {
                        EntrySiteHeader(
                            site = site,
                            siteHost = siteHost,
                            isPrimary = (index == 0),
                        )

                        // Action row for THIS site
                        EntrySiteActionRow(
                            site = site,
                            isSiteSaved = isSiteSaved,
                            saveActions = saveActions,
                            haptics = haptics,
                            onOpenUrl = onOpenUrl,
                            onCopyLink = onCopyLink,
                            onShareLink = onShareLink,
                        )
                    }

                    val siteOrigUrl = site.originalUrl
                    if (site.isBase64 && !siteOrigUrl.isNullOrEmpty()) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("Open original (base64)") },
                            supportingContent = { Text(siteOrigUrl, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                            modifier = Modifier.clickable { onOpenUrl(siteOrigUrl) }
                        )
                    }

                    // Mirrors for this site
                    if (site.mirrors.isNotEmpty()) {
                        Text(
                            text = "Mirrors",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                        site.mirrors.forEach { mirror ->
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Mirror ${mirror.label}") },
                                supportingContent = { Text(mirror.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.clickable { onOpenUrl(mirror.url) }
                            )
                        }
                    }
                }

                // Shared description / extra links below once
                val sharedDesc = entry.description.ifEmpty {
                    alternatives.firstOrNull { it.description.isNotEmpty() }?.description.orEmpty()
                }
                if (sharedDesc.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = sharedDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }

                // Every site's own links (e.g. uBO Lite's Note), each once; an alternative's are tagged with its name.
                val sharedExtras = allSites
                    .flatMap { site -> site.extraLinks.map { site to it } }
                    .distinctBy { (_, link) -> link.url }
                if (sharedExtras.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Extra Links",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    sharedExtras.forEach { (site, extra) ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(if (site === entry) extra.label else "${extra.label} · ${site.name}") },
                            supportingContent = { Text(extra.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable { onOpenUrl(extra.url) }
                        )
                    }
                }
            }

            if (onShowInSection != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Segment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = {
                        Text("Show in section", style = MaterialTheme.typography.titleSmall)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onShowInSection()
                        }
                        .padding(horizontal = 8.dp),
                )
            }

            if (followState != null && onToggleFollow != null) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            imageVector = if (followState.isFollowing) Icons.Default.NotificationsActive else Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = if (followState.isFollowing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    headlineContent = {
                        Text(
                            text = if (followState.isFollowing) "Following ${followState.categoryTitle}" else "Follow ${followState.categoryTitle}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                    trailingContent = {
                        if (followState.isFollowing) {
                            FilledTonalButton(onClick = onToggleFollow) {
                                Text("Following")
                            }
                        } else {
                            Button(onClick = onToggleFollow) {
                                Text("Follow")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleFollow() }
                        .padding(horizontal = 8.dp),
                )
            }

            if (!history.isNullOrEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = "History",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                history.forEach { item ->
                    val dateStr = DateFormatters.formatHistoryDate(item.ts)
                    when (item.kind) {
                        ChangeKind.ADDED -> {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Added $dateStr") },
                            )
                        }
                        ChangeKind.STARRED -> {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Starred $dateStr") },
                            )
                        }
                        ChangeKind.UNSTARRED -> {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Unstarred $dateStr") },
                            )
                        }
                        ChangeKind.EDITED -> {
                            var isExpanded by rememberSaveable { mutableStateOf(false) }
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = {
                                    Text(
                                        text = if (item.detail.isNotEmpty()) "Changed $dateStr — was: ${item.detail}" else "Changed $dateStr",
                                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.clickable { isExpanded = !isExpanded },
                            )
                        }
                        ChangeKind.URL_CHANGED -> {
                            val host = try { Uri.parse(item.url).host?.removePrefix("www.") ?: item.url } catch (_: Exception) { item.url }
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("New link: $host · $dateStr") },
                            )
                        }
                        ChangeKind.REMOVED -> {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Removed $dateStr") },
                            )
                        }
                        ChangeKind.RENAMED -> {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Renamed $dateStr") },
                            )
                        }
                        ChangeKind.MOVED -> {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Moved $dateStr") },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFolderPicker) {
        EntryFolderPickerDialog(
            folders = bookmarksState.folders,
            onDismiss = { showFolderPicker = false },
            onSelectFolder = { folderId -> moveToFolderAction(folderId); showFolderPicker = false },
            onCreateNewFolder = { showFolderPicker = false; newFolderName = ""; showNewFolderDialog = true },
        )
    }

    if (showNewFolderDialog) {
        EntryNewFolderDialog(
            folderName = newFolderName,
            onFolderNameChange = { newFolderName = it },
            onDismiss = { showNewFolderDialog = false },
            onConfirm = {
                val trimmed = newFolderName.trim()
                if (trimmed.isNotEmpty()) {
                    bookmarksState.createFolder(trimmed) { createdFolder -> moveToFolderAction(createdFolder.id) }
                }
                showNewFolderDialog = false
            },
        )
    }

    if (showNoteDialog) {
        EntryNoteDialog(
            noteInput = noteInput,
            onNoteInputChange = { noteInput = it },
            onDismiss = { showNoteDialog = false },
            onSave = { editNoteAction(noteInput.trim()); showNoteDialog = false },
        )
    }
}
