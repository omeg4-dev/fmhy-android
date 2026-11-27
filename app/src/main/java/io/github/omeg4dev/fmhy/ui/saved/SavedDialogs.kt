package io.github.omeg4dev.fmhy.ui.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.Bookmark
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.user.Folder
import io.github.omeg4dev.fmhy.ui.common.EntryRow
import io.github.omeg4dev.fmhy.ui.common.sectionDisplayTitle

@Composable
internal fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = folderName.trim()
                    if (trimmed.isNotEmpty()) {
                        onCreate(trimmed)
                    }
                    onDismiss()
                },
                enabled = folderName.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun RenameFolderDialog(
    folder: Folder,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var renameText by remember(folder) { mutableStateOf(folder.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename folder") },
        text = {
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                label = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = renameText.trim()
                    if (trimmed.isNotEmpty()) {
                        onRename(trimmed)
                    }
                    onDismiss()
                },
                enabled = renameText.isNotBlank(),
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun DeleteFolderDialog(
    folder: Folder,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete folder") },
        text = { Text("Delete folder \"${folder.name}\"? Sites in this folder will remain in Saved.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun ClearHistoryDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear history") },
        text = { Text("Clear all recorded browsing history?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Clear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun MoveToFolderDialog(
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onSelectFolder: (Long?) -> Unit,
    onCreateAndMove: (String) -> Unit,
) {
    var showNewFolder by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                ListItem(
                    headlineContent = { Text("No folder") },
                    modifier = Modifier.clickable { onSelectFolder(null) },
                )
                folders.forEach { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        modifier = Modifier.clickable { onSelectFolder(folder.id) },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                ListItem(
                    headlineContent = { Text("+ New folder…", color = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showNewFolder = true },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (showNewFolder) {
        CreateFolderDialog(
            onDismiss = { showNewFolder = false },
            onCreate = { name ->
                showNewFolder = false
                onCreateAndMove(name)
            },
        )
    }
}

@Composable
internal fun EditNoteDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var noteText by remember(initialNote) { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note") },
        text = {
            Column {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = {
                        if (it.length <= 500) {
                            noteText = it
                        }
                    },
                    label = { Text("Note (max 500 chars)") },
                    supportingText = {
                        Text(
                            text = "${noteText.length} / 500",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                    maxLines = 5,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(noteText.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReplacementsBottomSheet(
    bookmark: Bookmark,
    replacements: List<Entry>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShowSnackbar: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Replacements",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Other sites in ${bookmark.headingPath.ifEmpty { sectionDisplayTitle(bookmark.sectionId, bookmark.sectionTitle) }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (replacements.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No replacements found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                replacements.forEach { replacement ->
                    EntryRow(
                        entry = replacement,
                        onOpenUrl = {
                            onDismiss()
                            onOpenUrl(it)
                        },
                        onShowSnackbar = onShowSnackbar,
                    )
                }
            }
        }
    }
}
