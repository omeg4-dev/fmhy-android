package io.github.omeg4dev.fmhy.browser.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.omeg4dev.fmhy.data.CheckPrefs
import io.github.omeg4dev.fmhy.update.ui.InstallPermissionDialog
import kotlinx.coroutines.launch

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
    checkPrefs: CheckPrefs,
    checks: DownloadChecks,
    onOpenUrl: (String) -> Unit,
    onOpenCheckSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nowMs = remember { System.currentTimeMillis() }
    val items by controller.items.collectAsStateWithLifecycle()
    val activeItems = remember(items) {
        items.filter {
            it.status == DownloadStatus.WAITING ||
                it.status == DownloadStatus.WAITING_WIFI ||
                it.status == DownloadStatus.RUNNING ||
                it.status == DownloadStatus.PAUSED ||
                it.status == DownloadStatus.FAILED ||
                it.status == DownloadStatus.CHECKING ||
                it.status == DownloadStatus.AWAITING_DECISION
        }
    }
    val finishedItems = remember(items) {
        items.filter { it.status == DownloadStatus.FINISHED }
    }

    var pasteText by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf<String?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<DownloadItem?>(null) }
    var pendingDiscardItem by remember { mutableStateOf<DownloadItem?>(null) }
    var pendingInstallPermissionItem by remember { mutableStateOf<DownloadItem?>(null) }
    var headerMenuExpanded by remember { mutableStateOf(false) }

    var showSetUpChecksDialog by remember { mutableStateOf(false) }
    var pendingCheckChooserItem by remember { mutableStateOf<Pair<DownloadItem, Boolean>?>(null) }
    var viewingResultItem by remember { mutableStateOf<DownloadItem?>(null) }
    var pendingDangerousOpenItem by remember { mutableStateOf<DownloadItem?>(null) }
    var pendingDangerousInstallItem by remember { mutableStateOf<DownloadItem?>(null) }
    var pendingDangerousKeepItem by remember { mutableStateOf<DownloadItem?>(null) }
    var pendingUploadWarning by remember { mutableStateOf<Pair<ScanService, Long>?>(null) }
    var pendingUploadTooBigService by remember { mutableStateOf<ScanService?>(null) }

    // Picked phone file state
    var pickedFileUri by remember { mutableStateOf<Uri?>(null) }
    var pickedFileName by remember { mutableStateOf("") }
    var pickedFileSize by remember { mutableStateOf(-1L) }
    var pickedFileCalculating by remember { mutableStateOf(false) }
    var pickedFileProgress by remember { mutableFloatStateOf(0f) }
    var pickedFileChecking by remember { mutableStateOf(false) }
    var pickedFileSha by remember { mutableStateOf<String?>(null) }
    var pickedFileResult by remember { mutableStateOf<ScanResult?>(null) }
    var pickedFileError by remember { mutableStateOf<String?>(null) }
    var pickedFileUploadWarningService by remember { mutableStateOf<ScanService?>(null) }
    var pickedFileUploadTooBigService by remember { mutableStateOf<ScanService?>(null) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            if (checkPrefs.enabledServices.isEmpty()) {
                showSetUpChecksDialog = true
            } else {
                var name = "File"
                var size = -1L
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                            if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                        }
                    }
                } catch (_: Exception) {}

                pickedFileUri = uri
                pickedFileName = name
                pickedFileSize = size
                pickedFileCalculating = true
                pickedFileProgress = 0f
                pickedFileChecking = false
                pickedFileSha = null
                pickedFileResult = null
                pickedFileError = null

                scope.launch {
                    try {
                        val source = CheckSource.OfUri(context, uri, name, size)
                        val sha = checks.fingerprint(source) { bytesRead ->
                            if (size > 0) {
                                pickedFileProgress = (bytesRead.toFloat() / size).coerceIn(0f, 1f)
                            }
                        }
                        pickedFileSha = sha
                        pickedFileCalculating = false

                        if (checkPrefs.readyServices.isNotEmpty()) {
                            pickedFileChecking = true
                            val res = checks.checkFile(sha, checkPrefs.readyServices)
                            pickedFileChecking = false
                            pickedFileResult = res
                        } else {
                            onOpenUrl(ScanLinks.fileReport(checkPrefs.enabledServices.first(), sha))
                            pickedFileUri = null
                        }
                    } catch (e: Exception) {
                        pickedFileCalculating = false
                        pickedFileChecking = false
                        pickedFileError = e.message ?: "Failed to check file"
                    }
                }
            }
        }
    }

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

    fun triggerCheck(item: DownloadItem, isHeld: Boolean) {
        when {
            checkPrefs.readyServices.size == 2 -> {
                pendingCheckChooserItem = item to isHeld
            }
            checkPrefs.readyServices.size == 1 -> {
                if (isHeld) {
                    controller.recheckHeld(item.id, checkPrefs.readyServices)
                } else {
                    controller.checkFile(item.id, checkPrefs.readyServices)
                }
            }
            checkPrefs.readyServices.isEmpty() && checkPrefs.enabledServices.isNotEmpty() -> {
                scope.launch {
                    val sha = controller.fingerprintOf(item.id)
                    if (sha != null) {
                        onOpenUrl(ScanLinks.fileReport(checkPrefs.enabledServices.first(), sha))
                    }
                }
            }
            else -> {
                showSetUpChecksDialog = true
            }
        }
    }

    fun openFile(item: DownloadItem) {
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

    fun installFile(item: DownloadItem) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { controller.clearFinished() },
                            enabled = finishedItems.isNotEmpty(),
                        ) {
                            Text("Clear finished")
                        }
                        Box {
                            IconButton(onClick = { headerMenuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = headerMenuExpanded,
                                onDismissRequest = { headerMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Check a file from the phone") },
                                    onClick = {
                                        headerMenuExpanded = false
                                        pickFileLauncher.launch(arrayOf("*/*"))
                                    },
                                )
                            }
                        }
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
                        checkPrefs = checkPrefs,
                        checks = checks,
                        nowMs = nowMs,
                        onOpenUrl = onOpenUrl,
                        onMessage = onMessage,
                        onInstallClick = { installFile(item) },
                        onDeleteClick = { pendingDeleteItem = item },
                        onCheckFinished = { triggerCheck(it, isHeld = false) },
                        onKeepClick = { heldItem ->
                            if (heldItem.check?.verdict == ScanVerdict.DANGEROUS) {
                                pendingDangerousKeepItem = heldItem
                            } else {
                                controller.keep(heldItem.id)
                            }
                        },
                        onDiscardClick = { heldItem ->
                            pendingDiscardItem = heldItem
                        },
                        onCheckHeldClick = { triggerCheck(it, isHeld = true) },
                        onUploadHeldClick = { heldItem, service ->
                            val fileSize = if (heldItem.totalBytes > 0) heldItem.totalBytes else heldItem.downloadedBytes
                            val maxBytes = checks.client(service).maxUploadBytes
                            if (fileSize > maxBytes) {
                                pendingUploadTooBigService = service
                            } else {
                                pendingUploadWarning = service to heldItem.id
                            }
                        },
                        onShowResult = { viewingResultItem = it },
                        onDangerousOpenClick = { pendingDangerousOpenItem = it },
                        onDangerousInstallClick = { pendingDangerousInstallItem = it },
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
                        checkPrefs = checkPrefs,
                        checks = checks,
                        nowMs = nowMs,
                        onOpenUrl = onOpenUrl,
                        onMessage = onMessage,
                        onInstallClick = { installFile(item) },
                        onDeleteClick = { pendingDeleteItem = item },
                        onCheckFinished = { triggerCheck(it, isHeld = false) },
                        onKeepClick = {},
                        onDiscardClick = { pendingDiscardItem = it },
                        onCheckHeldClick = {},
                        onUploadHeldClick = { _, _ -> },
                        onShowResult = { viewingResultItem = it },
                        onDangerousOpenClick = { pendingDangerousOpenItem = it },
                        onDangerousInstallClick = { pendingDangerousInstallItem = it },
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

    if (pendingDiscardItem != null) {
        val toDiscard = pendingDiscardItem!!
        AlertDialog(
            onDismissRequest = { pendingDiscardItem = null },
            title = { Text("Delete file?") },
            text = { Text("“${toDiscard.fileName}” will be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.discard(toDiscard.id)
                        pendingDiscardItem = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscardItem = null }) {
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

    if (showSetUpChecksDialog) {
        SetUpChecksDialog(
            onOpenSettings = {
                showSetUpChecksDialog = false
                onOpenCheckSettings()
            },
            onDismiss = { showSetUpChecksDialog = false },
        )
    }

    if (pendingCheckChooserItem != null) {
        val (itemToCheck, isHeld) = pendingCheckChooserItem!!
        CheckChooserDialog(
            onChoose = { services ->
                pendingCheckChooserItem = null
                if (isHeld) {
                    controller.recheckHeld(itemToCheck.id, services)
                } else {
                    controller.checkFile(itemToCheck.id, services)
                }
            },
            onDismiss = { pendingCheckChooserItem = null },
        )
    }

    if (pendingDangerousOpenItem != null) {
        val dangerousItem = pendingDangerousOpenItem!!
        DangerousActionConfirmDialog(
            isInstall = false,
            onConfirm = {
                pendingDangerousOpenItem = null
                openFile(dangerousItem)
            },
            onDismiss = { pendingDangerousOpenItem = null },
        )
    }

    if (pendingDangerousInstallItem != null) {
        val dangerousItem = pendingDangerousInstallItem!!
        DangerousActionConfirmDialog(
            isInstall = true,
            onConfirm = {
                pendingDangerousInstallItem = null
                installFile(dangerousItem)
            },
            onDismiss = { pendingDangerousInstallItem = null },
        )
    }

    if (pendingDangerousKeepItem != null) {
        val dangerousItem = pendingDangerousKeepItem!!
        DangerousKeepConfirmDialog(
            onConfirm = {
                pendingDangerousKeepItem = null
                controller.keep(dangerousItem.id)
            },
            onDismiss = { pendingDangerousKeepItem = null },
        )
    }

    if (pendingUploadWarning != null) {
        val (service, itemId) = pendingUploadWarning!!
        UploadWarningDialog(
            service = service,
            onUpload = {
                pendingUploadWarning = null
                controller.upload(itemId, service)
            },
            onDismiss = { pendingUploadWarning = null },
        )
    }

    if (pendingUploadTooBigService != null) {
        val service = pendingUploadTooBigService!!
        UploadTooBigDialog(
            service = service,
            onOpenWebsite = {
                pendingUploadTooBigService = null
                onOpenUrl(ScanLinks.uploadPage(service))
            },
            onDismiss = { pendingUploadTooBigService = null },
        )
    }

    if (viewingResultItem != null) {
        val item = viewingResultItem!!
        val result = item.check
        if (result != null) {
            AlertDialog(
                onDismissRequest = { viewingResultItem = null },
                title = { Text("File check") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ScanResultView(
                            result = result,
                            onOpenReport = onOpenUrl,
                            isLink = false,
                        )
                    }
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (serviceRes in result.services) {
                            if (serviceRes.error in setOf(ScanError.BAD_KEY, ScanError.LIMIT, ScanError.TOO_BIG, ScanError.OFFLINE)) {
                                TextButton(
                                    onClick = {
                                        if (serviceRes.error == ScanError.TOO_BIG) {
                                            onOpenUrl(ScanLinks.uploadPage(serviceRes.service))
                                        } else {
                                            val sha = result.sha256
                                            if (sha != null) {
                                                onOpenUrl(ScanLinks.fileReport(serviceRes.service, sha))
                                            } else {
                                                onOpenUrl(ScanLinks.uploadPage(serviceRes.service))
                                            }
                                        }
                                    }
                                ) {
                                    Text("Open website")
                                }
                            }
                        }

                        val unknownReadyService = checkPrefs.readyServices.firstOrNull { ready ->
                            val sr = result.services.firstOrNull { it.service == ready }
                            sr?.verdict == ScanVerdict.UNKNOWN && sr?.error == null
                        }
                        if (unknownReadyService != null) {
                            TextButton(
                                onClick = {
                                    val fileSize = if (item.totalBytes > 0) item.totalBytes else item.downloadedBytes
                                    val maxBytes = checks.client(unknownReadyService).maxUploadBytes
                                    if (fileSize > maxBytes) {
                                        pendingUploadTooBigService = unknownReadyService
                                    } else {
                                        pendingUploadWarning = unknownReadyService to item.id
                                    }
                                }
                            ) {
                                Text("Upload for analysis")
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewingResultItem = null }) {
                        Text("Close")
                    }
                },
            )
        }
    }

    if (pickedFileUri != null) {
        AlertDialog(
            onDismissRequest = { pickedFileUri = null },
            title = { Text("Check a file") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = pickedFileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (pickedFileSize > 0) {
                        Text(
                            text = "Size: ${DownloadPolicy.formatBytes(pickedFileSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (pickedFileCalculating) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Calculating fingerprint…",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (pickedFileSize > 0) {
                                LinearProgressIndicator(
                                    progress = { pickedFileProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    if (pickedFileChecking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Checking…",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    if (pickedFileResult != null) {
                        ScanResultView(
                            result = pickedFileResult!!,
                            onOpenReport = onOpenUrl,
                            isLink = false,
                        )
                    }

                    if (pickedFileError != null) {
                        Text(
                            text = pickedFileError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pickedFileResult?.let { res ->
                        for (serviceRes in res.services) {
                            if (serviceRes.error in setOf(ScanError.BAD_KEY, ScanError.LIMIT, ScanError.TOO_BIG, ScanError.OFFLINE)) {
                                TextButton(
                                    onClick = {
                                        if (serviceRes.error == ScanError.TOO_BIG) {
                                            onOpenUrl(ScanLinks.uploadPage(serviceRes.service))
                                        } else {
                                            val sha = res.sha256
                                            if (sha != null) {
                                                onOpenUrl(ScanLinks.fileReport(serviceRes.service, sha))
                                            } else {
                                                onOpenUrl(ScanLinks.uploadPage(serviceRes.service))
                                            }
                                        }
                                    }
                                ) {
                                    Text("Open website")
                                }
                            }
                        }

                        val unknownReadyService = checkPrefs.readyServices.firstOrNull { ready ->
                            val sr = res.services.firstOrNull { it.service == ready }
                            sr?.verdict == ScanVerdict.UNKNOWN && sr?.error == null
                        }
                        if (unknownReadyService != null) {
                            TextButton(
                                onClick = {
                                    val maxBytes = checks.client(unknownReadyService).maxUploadBytes
                                    if (pickedFileSize > maxBytes) {
                                        pickedFileUploadTooBigService = unknownReadyService
                                    } else {
                                        pickedFileUploadWarningService = unknownReadyService
                                    }
                                }
                            ) {
                                Text("Upload for analysis")
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pickedFileUri = null }) {
                    Text("Close")
                }
            },
        )
    }

    if (pickedFileUploadWarningService != null) {
        val service = pickedFileUploadWarningService!!
        val uri = pickedFileUri
        val sha = pickedFileSha
        val name = pickedFileName
        val size = pickedFileSize
        UploadWarningDialog(
            service = service,
            onUpload = {
                pickedFileUploadWarningService = null
                if (uri != null && sha != null) {
                    scope.launch {
                        val source = CheckSource.OfUri(context, uri, name, size)
                        val uploadRes = checks.upload(source, sha, service, pickedFileResult)
                        pickedFileResult = uploadRes
                        if (uploadRes.isRunning) {
                            val notifyName = if (isPrivateTab) null else name
                            checks.trackInBackground(uploadRes, notifyName)
                        }
                    }
                }
            },
            onDismiss = { pickedFileUploadWarningService = null },
        )
    }

    if (pickedFileUploadTooBigService != null) {
        val service = pickedFileUploadTooBigService!!
        UploadTooBigDialog(
            service = service,
            onOpenWebsite = {
                pickedFileUploadTooBigService = null
                onOpenUrl(ScanLinks.uploadPage(service))
            },
            onDismiss = { pickedFileUploadTooBigService = null },
        )
    }
}

@Composable
private fun DownloadEntryRow(
    item: DownloadItem,
    controller: DownloadController,
    checkPrefs: CheckPrefs,
    checks: DownloadChecks,
    nowMs: Long,
    onOpenUrl: (String) -> Unit,
    onMessage: (String) -> Unit,
    onInstallClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCheckFinished: (DownloadItem) -> Unit,
    onKeepClick: (DownloadItem) -> Unit,
    onDiscardClick: (DownloadItem) -> Unit,
    onCheckHeldClick: (DownloadItem) -> Unit,
    onUploadHeldClick: (DownloadItem, ScanService) -> Unit,
    onShowResult: (DownloadItem) -> Unit,
    onDangerousOpenClick: (DownloadItem) -> Unit,
    onDangerousInstallClick: (DownloadItem) -> Unit,
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val isFinished = item.status == DownloadStatus.FINISHED
    val rowModifier = if (isFinished) {
        Modifier
            .fillMaxWidth()
            .clickable {
                if (CheckPolicy.needsExtraConfirm(item)) {
                    onDangerousOpenClick(item)
                } else if (!controller.fileExists(item)) {
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

            // Progress bar (active downloading only)
            if (!isFinished && item.status != DownloadStatus.CHECKING && item.status != DownloadStatus.AWAITING_DECISION) {
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

            // Label line on any entry with check != null
            if (item.check != null) {
                val checkColor = when (item.check.verdict) {
                    ScanVerdict.DANGEROUS -> MaterialTheme.colorScheme.error
                    ScanVerdict.SUSPICIOUS -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = CheckUiText.label(item.check, nowMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = checkColor,
                    modifier = Modifier.clickable { onShowResult(item) },
                )
            }

            // Checking indicator
            if (item.checking) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Checking…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Dangerous finished entry red warning
            if (isFinished && item.check?.verdict == ScanVerdict.DANGEROUS) {
                Text(
                    text = "This file was flagged as dangerous. Delete it unless you are sure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
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
                DownloadStatus.AWAITING_DECISION -> {
                    Text(
                        text = item.status.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.check == null && !item.checking) {
                        Text(
                            text = "Not checked yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DownloadStatus.CHECKING -> {
                    Text(
                        text = item.status.label,
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

            // Buttons row for AWAITING_DECISION
            if (item.status == DownloadStatus.AWAITING_DECISION) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isDangerous = item.check?.verdict == ScanVerdict.DANGEROUS

                    @Composable
                    fun DeleteButton() {
                        TextButton(
                            onClick = { onDiscardClick(item) },
                            colors = if (isDangerous) {
                                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.textButtonColors()
                            },
                        ) {
                            Text("Delete")
                        }
                    }

                    @Composable
                    fun KeepButton() {
                        TextButton(onClick = { onKeepClick(item) }) {
                            Text("Keep")
                        }
                    }

                    if (isDangerous) {
                        DeleteButton()
                        KeepButton()
                    } else {
                        KeepButton()
                        DeleteButton()
                    }

                    if (item.check == null && !item.checking) {
                        TextButton(onClick = { onCheckHeldClick(item) }) {
                            Text("Check")
                        }
                    }

                    val unknownReadyService = checkPrefs.readyServices.firstOrNull { ready ->
                        val sr = item.check?.services?.firstOrNull { it.service == ready }
                        sr?.verdict == ScanVerdict.UNKNOWN && sr?.error == null
                    }
                    if (unknownReadyService != null) {
                        TextButton(onClick = { onUploadHeldClick(item, unknownReadyService) }) {
                            Text("Upload for analysis")
                        }
                    }
                }
            }

            // Buttons row for CHECKING
            if (item.status == DownloadStatus.CHECKING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    TextButton(onClick = { onDiscardClick(item) }) {
                        Text("Delete")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Trailing action buttons
        if (!isFinished && item.status != DownloadStatus.CHECKING && item.status != DownloadStatus.AWAITING_DECISION) {
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
        } else if (isFinished) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    val isDangerous = item.check?.verdict == ScanVerdict.DANGEROUS
                    val checkLabel = if (item.check != null) "Check again" else "Check file"

                    @Composable
                    fun ShareMenuItem() {
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
                    }

                    @Composable
                    fun CheckMenuItem() {
                        DropdownMenuItem(
                            text = { Text(checkLabel) },
                            onClick = {
                                menuExpanded = false
                                onCheckFinished(item)
                            },
                        )
                    }

                    @Composable
                    fun InstallMenuItem() {
                        if (item.isApk) {
                            DropdownMenuItem(
                                text = { Text("Install") },
                                onClick = {
                                    menuExpanded = false
                                    if (CheckPolicy.needsExtraConfirm(item)) {
                                        onDangerousInstallClick(item)
                                    } else {
                                        onInstallClick()
                                    }
                                },
                            )
                        }
                    }

                    @Composable
                    fun DeleteMenuItem() {
                        DropdownMenuItem(
                            text = { Text("Delete file") },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            },
                        )
                    }

                    @Composable
                    fun RemoveMenuItem() {
                        DropdownMenuItem(
                            text = { Text("Remove from list") },
                            onClick = {
                                menuExpanded = false
                                controller.removeFromList(item.id)
                            },
                        )
                    }

                    if (isDangerous) {
                        DeleteMenuItem()
                        ShareMenuItem()
                        CheckMenuItem()
                        InstallMenuItem()
                        RemoveMenuItem()
                    } else {
                        ShareMenuItem()
                        CheckMenuItem()
                        InstallMenuItem()
                        DeleteMenuItem()
                        RemoveMenuItem()
                    }
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
