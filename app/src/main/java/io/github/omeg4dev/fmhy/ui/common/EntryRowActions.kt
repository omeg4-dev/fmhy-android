package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.link.PasteLinks
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.user.Folder

/** Primary Open / Copy / Share / Save action row in [EntryDetailBottomSheet] for single-site entries. */
@Composable
internal fun EntryPrimaryActionRow(
    entry: Entry,
    isSaved: Boolean,
    saveActions: SaveActions,
    haptics: AppHaptics,
    onOpenUrl: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .enterStagger(index = 1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Open
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledTonalIconButton(
                onClick = {
                    onOpenUrl(entry.url)
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open")
            }
            Spacer(Modifier.height(8.dp))
            Text("Open", style = MaterialTheme.typography.labelMedium)
        }

        // Copy
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledTonalIconButton(
                onClick = {
                    haptics.success()
                    onCopyLink(entry.url)
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy link")
            }
            Spacer(Modifier.height(8.dp))
            Text("Copy", style = MaterialTheme.typography.labelMedium)
        }

        // Share
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledTonalIconButton(
                onClick = {
                    onShareLink(entry.url)
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
            Spacer(Modifier.height(8.dp))
            Text("Share", style = MaterialTheme.typography.labelMedium)
        }

        // Save / Saved
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledTonalIconButton(
                onClick = {
                    if (isSaved) {
                        saveActions.unsave(entry)
                    } else {
                        saveActions.save(entry)
                    }
                    onDismiss()
                },
                modifier = Modifier.size(56.dp),
            ) {
                AnimatedContent(
                    targetState = isSaved,
                    transitionSpec = {
                        (fadeIn(Motion.standard(Motion.SHORT)) + scaleIn(initialScale = 0.8f, animationSpec = Motion.standard(Motion.SHORT)))
                            .togetherWith(fadeOut(Motion.exit(Motion.SHORT)) + scaleOut(targetScale = 0.8f, animationSpec = Motion.exit(Motion.SHORT)))
                    },
                    label = "saveIconPop",
                ) { saved ->
                    Icon(
                        imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (saved) "Saved" else "Save",
                        tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(if (isSaved) "Saved" else "Save", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Action row in [EntryDetailBottomSheet] for the primary alternative site (index == 0). */
@Composable
internal fun EntrySiteActionRow(
    site: Entry,
    isSiteSaved: Boolean,
    saveActions: SaveActions,
    haptics: AppHaptics,
    onOpenUrl: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Open
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledTonalIconButton(
                onClick = {
                    onOpenUrl(site.url)
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open")
            }
            Spacer(Modifier.height(4.dp))
            Text("Open", style = MaterialTheme.typography.labelSmall)
        }

        // Copy
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledTonalIconButton(
                onClick = {
                    haptics.success()
                    onCopyLink(site.url)
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy link")
            }
            Spacer(Modifier.height(4.dp))
            Text("Copy", style = MaterialTheme.typography.labelSmall)
        }

        // Share
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledTonalIconButton(
                onClick = {
                    onShareLink(site.url)
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
            Spacer(Modifier.height(4.dp))
            Text("Share", style = MaterialTheme.typography.labelSmall)
        }

        // Save
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledTonalIconButton(
                onClick = {
                    if (isSiteSaved) {
                        saveActions.unsave(site)
                    } else {
                        saveActions.save(site)
                    }
                },
                modifier = Modifier.size(48.dp),
            ) {
                AnimatedContent(
                    targetState = isSiteSaved,
                    transitionSpec = {
                        (fadeIn(Motion.standard(Motion.SHORT)) + scaleIn(initialScale = 0.8f, animationSpec = Motion.standard(Motion.SHORT)))
                            .togetherWith(fadeOut(Motion.exit(Motion.SHORT)) + scaleOut(targetScale = 0.8f, animationSpec = Motion.exit(Motion.SHORT)))
                    },
                    label = "primaryAltSavePop",
                ) { saved ->
                    Icon(
                        imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (saved) "Saved" else "Save",
                        tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(if (isSiteSaved) "Saved" else "Save", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Compact icon buttons for alternative sites on the same line in [EntryDetailBottomSheet]. */
@Composable
internal fun EntryAltActions(
    site: Entry,
    isSiteSaved: Boolean,
    saveActions: SaveActions,
    haptics: AppHaptics,
    onOpenUrl: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
) {
    IconButton(
        onClick = {
            onOpenUrl(site.url)
        },
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open ${site.name}", modifier = Modifier.size(20.dp))
    }
    IconButton(
        onClick = {
            haptics.success()
            onCopyLink(site.url)
        },
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
    ) {
        Icon(Icons.Default.ContentCopy, contentDescription = "Copy link", modifier = Modifier.size(20.dp))
    }
    IconButton(
        onClick = {
            onShareLink(site.url)
        },
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
    ) {
        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(20.dp))
    }
    IconButton(
        onClick = {
            if (isSiteSaved) {
                saveActions.unsave(site)
            } else {
                saveActions.save(site)
            }
        },
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp),
    ) {
        AnimatedContent(
            targetState = isSiteSaved,
            transitionSpec = {
                (fadeIn(Motion.standard(Motion.SHORT)) + scaleIn(initialScale = 0.8f, animationSpec = Motion.standard(Motion.SHORT)))
                    .togetherWith(fadeOut(Motion.exit(Motion.SHORT)) + scaleOut(targetScale = 0.8f, animationSpec = Motion.exit(Motion.SHORT)))
            },
            label = "altSavePop",
        ) { saved ->
            Icon(
                imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (saved) "Saved" else "Save",
                tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Dialog for moving an entry to an existing folder or prompting to create a new one. */
@Composable
internal fun EntryFolderPickerDialog(
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onSelectFolder: (Long?) -> Unit,
    onCreateNewFolder: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                ListItem(
                    headlineContent = { Text("No folder") },
                    modifier = Modifier.clickable {
                        onSelectFolder(null)
                    }
                )
                folders.forEach { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        modifier = Modifier.clickable {
                            onSelectFolder(folder.id)
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                ListItem(
                    headlineContent = { Text("+ New folder…", color = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        onCreateNewFolder()
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Dialog for creating a new bookmark folder. */
@Composable
internal fun EntryNewFolderDialog(
    folderName: String,
    onFolderNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = onFolderNameChange,
                label = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = folderName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Dialog for editing the personal note on an entry. */
@Composable
internal fun EntryNoteDialog(
    noteInput: String,
    onNoteInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note") },
        text = {
            Column {
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = {
                        if (it.length <= 500) {
                            onNoteInputChange(it)
                        }
                    },
                    label = { Text("Note (max 500 chars)") },
                    supportingText = {
                        Text(
                            text = "${noteInput.length} / 500",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    },
                    maxLines = 5,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** The link to copy or share: a pastebin-hidden link is decoded first (several links → one per line). Without a
 *  decoder, or for other links, [onText] gets [url] at once. */
internal fun PasteDecoder?.linkText(url: String, action: PasteAction, onText: (String) -> Unit) {
    if (this == null || PasteLinks.rawUrl(url) == null) {
        onText(url)
    } else {
        decode(
            url = url,
            action = action,
            onLinks = { onText(it.joinToString("\n")) },
            onUseRaw = { onText(url) },
        )
    }
}
