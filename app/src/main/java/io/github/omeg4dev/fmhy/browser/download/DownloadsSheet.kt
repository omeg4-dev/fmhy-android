package io.github.omeg4dev.fmhy.browser.download

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.update.ui.InstallPermissionDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(
    controller: DownloadController,
    isPrivateTab: Boolean,
    onPasteLink: (String) -> Unit,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    installExplained: Boolean = false,
    onInstallExplained: () -> Unit = {},
) {
    val context = LocalContext.current
    val items by controller.items.collectAsStateWithLifecycle()
    val activeItems = remember(items) {
        items.filter {
            it.status == DownloadStatus.WAITING ||
                it.status == DownloadStatus.WAITING_WIFI ||
                it.status == DownloadStatus.RUNNING ||
                it.status == DownloadStatus.PAUSED ||
                it.status == DownloadStatus.FAILED
        }
    }
    val finishedItems = remember(items) {
        items.filter { it.status == DownloadStatus.FINISHED }
    }

    var pasteText by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf<String?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<DownloadItem?>(null) }
    var pendingInstallPermissionItem by remember { mutableStateOf<DownloadItem?>(null) }

    fun submitPaste() {
        val trimmed = pasteText.trim()
        if (!DownloadRequest.isHttpUrl(trimmed)) {
            pasteError = "Enter a web address starting with http:// or https://"
        } else {
            pasteError = null
            onPasteLink(trimmed)
            pasteText = ""
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    TextButton(
                        onClick = { controller.clearFinished() },
                        enabled = finishedItems.isNotEmpty(),
                    ) {
                        Text("Clear finished")
                    }
                }
            }

            // Paste row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = if (pasteError != null) Alignment.Top else Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = {
                            pasteText = it
                            if (pasteError != null) pasteError = null
                        },
                        placeholder = { Text("Paste a link") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { submitPaste() }),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                                    if (!text.isNullOrBlank()) {
                                        pasteText = text
                                        if (pasteError != null) pasteError = null
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                )
                            }
                        },
                        isError = pasteError != null,
                        supportingText = if (pasteError != null) {
                            {
                                Text(
                                    text = pasteError!!,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else null,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { submitPaste() },
                        modifier = Modifier.padding(top = if (pasteError != null) 8.dp else 0.dp),
                    ) {
                        Text("Download")
                    }
                }
            }

            // Section: Active
            item {
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (activeItems.isEmpty()) {
                item {
                    Text(
                        text = "No active downloads",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(activeItems, key = { it.id }) { item ->
                    DownloadEntryRow(
                        item = item,
                        controller = controller,
                        onMessage = onMessage,
                        onInstallClick = {},
                        onDeleteClick = {},
                    )
                }
            }

            // Section: Finished
            item {
                Text(
                    text = "Finished",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            if (finishedItems.isEmpty()) {
                item {
                    Text(
                        text = "No finished downloads",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(finishedItems, key = { it.id }) { item ->
                    DownloadEntryRow(
                        item = item,
                        controller = controller,
                        onMessage = onMessage,
                        onInstallClick = {
                            if (controller.canRequestInstalls()) {
                                val installIntent = controller.installIntent(item)
                                try {
                                    if (installIntent != null) {
                                        context.startActivity(installIntent)
                                    } else {
                                        onMessage("File not found")
                                    }
                                } catch (_: Exception) {
                                    onMessage("Couldn't open the installer")
                                }
                            } else {
                                if (!installExplained) {
                                    pendingInstallPermissionItem = item
                                } else {
                                    context.startActivity(controller.unknownSourcesIntent())
                                }
                            }
                        },
                        onDeleteClick = {
                            pendingDeleteItem = item
                        },
                    )
                }
            }
        }
    }

    if (pendingDeleteItem != null) {
        val toDelete = pendingDeleteItem!!
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            title = { Text("Delete file?") },
            text = { Text("“${toDelete.fileName}” will be deleted from your phone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.deleteFile(toDelete.id)
                        pendingDeleteItem = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (pendingInstallPermissionItem != null) {
        InstallPermissionDialog(
            onConfirm = {
                pendingInstallPermissionItem = null
                onInstallExplained()
                context.startActivity(controller.unknownSourcesIntent())
            },
            onDismiss = {
                pendingInstallPermissionItem = null
            },
        )
    }
}

@Composable
private fun DownloadEntryRow(
    item: DownloadItem,
    controller: DownloadController,
    onMessage: (String) -> Unit,
    onInstallClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val isFinished = item.status == DownloadStatus.FINISHED
    val rowModifier = if (isFinished) {
        Modifier
            .fillMaxWidth()
            .clickable {
                if (!controller.fileExists(item)) {
                    onMessage("File not found")
                } else {
                    val intent = controller.openIntent(item)
                    try {
                        if (intent != null) {
                            context.startActivity(intent)
                        } else {
                            onMessage("File not found")
                        }
                    } catch (_: ActivityNotFoundException) {
                        onMessage("No app can open this file")
                    }
                }
            }
            .padding(vertical = 4.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = itemIcon(item),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Second line: <sourceHost> · <size>
            val sizePart = if (item.totalBytes > 0) DownloadPolicy.formatBytes(item.totalBytes) else null
            val secondLine = listOfNotNull(item.sourceHost.ifBlank { null }, sizePart).joinToString(" · ")
            if (secondLine.isNotEmpty()) {
                Text(
                    text = secondLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Progress bar (active only)
            if (!isFinished) {
                val hideProgress = (item.status == DownloadStatus.WAITING ||
                    item.status == DownloadStatus.WAITING_WIFI ||
                    item.status == DownloadStatus.FAILED) && item.downloadedBytes == 0L

                if (!hideProgress) {
                    val fraction = item.fraction
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                    } else if (item.status == DownloadStatus.RUNNING) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }

            // Status line
            when (item.status) {
                DownloadStatus.RUNNING -> {
                    Text(
                        text = DownloadPolicy.progressLine(
                            downloaded = item.downloadedBytes,
                            total = item.totalBytes,
                            speedBps = item.speedBps,
                            etaSeconds = item.etaSeconds,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.restartNote) {
                        Text(
                            text = "Started again from the beginning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DownloadStatus.FAILED -> {
                    val errorPrefix = if (!item.error.isNullOrBlank()) "Failed: ${item.error}" else "Failed"
                    Text(
                        text = errorPrefix,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (!item.resumable) {
                        Text(
                            text = "This site doesn't allow resuming. Retry starts again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DownloadStatus.FINISHED -> {
                    Text(
                        text = "Finished",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        text = item.status.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.restartNote && item.status == DownloadStatus.WAITING) {
                        Text(
                            text = "Started again from the beginning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Trailing action buttons
        if (!isFinished) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (item.status) {
                    DownloadStatus.RUNNING, DownloadStatus.WAITING, DownloadStatus.WAITING_WIFI -> {
                        IconButton(onClick = { controller.pause(item.id) }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause")
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        IconButton(onClick = { controller.resume(item.id) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                        }
                    }
                    DownloadStatus.FAILED -> {
                        IconButton(onClick = { controller.retry(item.id) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry")
                        }
                    }
                    else -> {}
                }
                IconButton(onClick = { controller.cancel(item.id) }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
            }
        } else {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            menuExpanded = false
                            val shareIntent = controller.shareIntent(item)
                            if (shareIntent != null) {
                                try {
                                    context.startActivity(shareIntent)
                                } catch (_: Exception) {}
                            }
                        },
                    )
                    if (item.isApk) {
                        DropdownMenuItem(
                            text = { Text("Install") },
                            onClick = {
                                menuExpanded = false
                                onInstallClick()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete file") },
                        onClick = {
                            menuExpanded = false
                            onDeleteClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from list") },
                        onClick = {
                            menuExpanded = false
                            controller.removeFromList(item.id)
                        },
                    )
                }
            }
        }
    }
}

private fun itemIcon(item: DownloadItem): ImageVector {
    if (item.isApk) return Icons.Default.Android
    val ext = item.fileName.substringAfterLast('.', "").lowercase()
    if (item.mimeType.startsWith("video/", ignoreCase = true) || ext in setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "m4v")) {
        return Icons.Default.Movie
    }
    if (ext in setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz") ||
        item.mimeType.contains("zip") || item.mimeType.contains("compressed") || item.mimeType.contains("tar")
    ) {
        return Icons.Default.FolderZip
    }
    return Icons.AutoMirrored.Filled.InsertDriveFile
}
