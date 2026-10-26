package io.github.omeg4dev.fmhy.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.browser.download.ChecksExplainDialog
import io.github.omeg4dev.fmhy.browser.download.DownloadStorage
import io.github.omeg4dev.fmhy.browser.download.ScanService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val downloadPrefs by viewModel.downloadPrefs.collectAsStateWithLifecycle()
    val checkPrefs by viewModel.checkPrefs.collectAsStateWithLifecycle()
    val browserPrefs by viewModel.browserPrefs.collectAsStateWithLifecycle()

    val eDownloadFolder = SettingsCatalog.downloadsDownloadFolder
    val eVideoDownloadButton = SettingsCatalog.downloadsVideoDownloadButton
    val eVirusTotal = SettingsCatalog.downloadsVirusTotal
    val eVirusTotalKey = SettingsCatalog.downloadsVirusTotalKey
    val eHybridAnalysis = SettingsCatalog.downloadsHybridAnalysis
    val eHybridAnalysisKey = SettingsCatalog.downloadsHybridAnalysisKey
    val eHoldUntilChecked = SettingsCatalog.downloadsHoldUntilChecked
    val eCheckRiskyFiles = SettingsCatalog.downloadsCheckRiskyFiles
    val eShowFmhyStatus = SettingsCatalog.downloadsShowFmhyStatus

    val generalIds = listOf(eDownloadFolder.id, eVideoDownloadButton.id)
    val checkIds = listOf(
        eVirusTotal.id,
        eVirusTotalKey.id,
        eHybridAnalysis.id,
        eHybridAnalysisKey.id,
        eHoldUntilChecked.id,
        eCheckRiskyFiles.id,
        eShowFmhyStatus.id,
    )
    val sectionSettingIds = remember { listOf(generalIds, checkIds) }
    val listState = rememberFocusedListState(sectionSettingIds)

    var showDownloadFolderDialog by remember { mutableStateOf(false) }
    var showChecksExplainDialog by remember { mutableStateOf(false) }
    var pendingTurnOnService by remember { mutableStateOf<ScanService?>(null) }
    var showVtKeyDialog by remember { mutableStateOf(false) }
    var showHaKeyDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
                return@rememberLauncherForActivityResult
            }

            val oldUriString = downloadPrefs.folderUri
            if (oldUriString != null && oldUriString != uri.toString()) {
                try {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(oldUriString),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
            }

            viewModel.updateDownloadPrefs {
                it.copy(folderUri = uri.toString(), folderFallbackNotified = false)
            }
        }
    }

    val downloadFolderSummary = remember(downloadPrefs.folderUri) {
        val folderUri = downloadPrefs.folderUri
        if (folderUri == null) {
            "Downloads (default)"
        } else {
            val uri = Uri.parse(folderUri)
            val baseLabel = DownloadStorage.folderLabel(context, uri) ?: "Chosen folder"
            if (!DownloadStorage.isTreeAvailable(context, uri)) {
                "$baseLabel · not available, using Downloads"
            } else {
                baseLabel
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // General downloads section
            item {
                SettingsSection(title = "Downloads") {
                    NavRow(
                        title = eDownloadFolder.title,
                        summary = downloadFolderSummary,
                        settingId = eDownloadFolder.id,
                        onClick = { showDownloadFolderDialog = true },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eVideoDownloadButton.title,
                        summary = eVideoDownloadButton.description,
                        checked = browserPrefs.videoDownloadButton,
                        settingId = eVideoDownloadButton.id,
                        onChange = { checked ->
                            viewModel.updateBrowser { it.copy(videoDownloadButton = checked) }
                        },
                    )
                }
            }

            // File checks section
            item {
                SettingsSection(
                    title = "File checks",
                    footer = "What is sent: link checks send the web address, only when you tap “Check link first”. File checks send the file's fingerprint (SHA-256), not the file. Files are uploaded only after you agree. Without a key, the check opens the service's website instead.",
                ) {
                    val vtSummary = if (checkPrefs.virusTotalKey.isBlank()) "Needs a free key" else "Key added"
                    SwitchRow(
                        title = eVirusTotal.title,
                        summary = vtSummary,
                        checked = checkPrefs.virusTotalOn,
                        settingId = eVirusTotal.id,
                        onChange = { checked ->
                            if (checked && !checkPrefs.explainShown) {
                                pendingTurnOnService = ScanService.VIRUSTOTAL
                                showChecksExplainDialog = true
                            } else {
                                viewModel.updateCheckPrefs { it.copy(virusTotalOn = checked) }
                            }
                        },
                    )
                    SettingsDivider()
                    val vtKeySummary = if (checkPrefs.virusTotalKey.isBlank()) "Not set" else "••••" + checkPrefs.virusTotalKey.takeLast(4)
                    NavRow(
                        title = eVirusTotalKey.title,
                        summary = vtKeySummary,
                        settingId = eVirusTotalKey.id,
                        onClick = { showVtKeyDialog = true },
                    )
                    SettingsDivider()
                    val haSummary = if (checkPrefs.hybridKey.isBlank()) "Needs a free key" else "Key added"
                    SwitchRow(
                        title = eHybridAnalysis.title,
                        summary = haSummary,
                        checked = checkPrefs.hybridOn,
                        settingId = eHybridAnalysis.id,
                        onChange = { checked ->
                            if (checked && !checkPrefs.explainShown) {
                                pendingTurnOnService = ScanService.HYBRID_ANALYSIS
                                showChecksExplainDialog = true
                            } else {
                                viewModel.updateCheckPrefs { it.copy(hybridOn = checked) }
                            }
                        },
                    )
                    SettingsDivider()
                    val haKeySummary = if (checkPrefs.hybridKey.isBlank()) "Not set" else "••••" + checkPrefs.hybridKey.takeLast(4)
                    NavRow(
                        title = eHybridAnalysisKey.title,
                        summary = haKeySummary,
                        settingId = eHybridAnalysisKey.id,
                        onClick = { showHaKeyDialog = true },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eHoldUntilChecked.title,
                        summary = eHoldUntilChecked.description,
                        checked = checkPrefs.holdUntilChecked,
                        settingId = eHoldUntilChecked.id,
                        onChange = { checked ->
                            viewModel.updateCheckPrefs { it.copy(holdUntilChecked = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eCheckRiskyFiles.title,
                        summary = eCheckRiskyFiles.description,
                        checked = checkPrefs.autoCheckRisky,
                        settingId = eCheckRiskyFiles.id,
                        onChange = { checked ->
                            viewModel.updateCheckPrefs { it.copy(autoCheckRisky = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eShowFmhyStatus.title,
                        summary = eShowFmhyStatus.description,
                        checked = checkPrefs.fmhyDownloadStatus,
                        settingId = eShowFmhyStatus.id,
                        onChange = { checked ->
                            viewModel.updateCheckPrefs { it.copy(fmhyDownloadStatus = checked) }
                        },
                    )
                }
            }

            extraRows()
        }
    }

    if (showDownloadFolderDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadFolderDialog = false },
            title = { Text("Download folder") },
            text = {
                Text("New downloads are saved here. Files already downloaded stay where they are.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDownloadFolderDialog = false
                        folderPickerLauncher.launch(downloadPrefs.folderUri?.let { Uri.parse(it) })
                    }
                ) {
                    Text("Choose folder")
                }
            },
            dismissButton = {
                Row {
                    if (downloadPrefs.folderUri != null) {
                        TextButton(
                            onClick = {
                                val oldUriString = downloadPrefs.folderUri
                                if (oldUriString != null) {
                                    try {
                                        context.contentResolver.releasePersistableUriPermission(
                                            Uri.parse(oldUriString),
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                                viewModel.updateDownloadPrefs {
                                    it.copy(folderUri = null, folderFallbackNotified = false)
                                }
                                showDownloadFolderDialog = false
                            }
                        ) {
                            Text("Use default")
                        }
                    }
                    TextButton(onClick = { showDownloadFolderDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
        )
    }

    if (showChecksExplainDialog) {
        ChecksExplainDialog(
            onOk = {
                showChecksExplainDialog = false
                viewModel.updateCheckPrefs { prefs ->
                    val withExplain = prefs.copy(explainShown = true)
                    when (pendingTurnOnService) {
                        ScanService.VIRUSTOTAL -> withExplain.copy(virusTotalOn = true)
                        ScanService.HYBRID_ANALYSIS -> withExplain.copy(hybridOn = true)
                        null -> withExplain
                    }
                }
                pendingTurnOnService = null
            }
        )
    }

    if (showVtKeyDialog) {
        ApiKeyDialog(
            title = "VirusTotal key",
            helpText = "Get a free key from your VirusTotal account (Profile → API key). Free keys have usage limits.",
            currentKey = checkPrefs.virusTotalKey,
            getKeyUrl = "https://www.virustotal.com/gui/my-apikey",
            onSave = { key ->
                viewModel.updateCheckPrefs { it.copy(virusTotalKey = key) }
            },
            onRemove = {
                viewModel.updateCheckPrefs { it.copy(virusTotalKey = "") }
            },
            onDismiss = { showVtKeyDialog = false },
        )
    }

    if (showHaKeyDialog) {
        ApiKeyDialog(
            title = "Hybrid Analysis key",
            helpText = "Get a free key from your Hybrid Analysis profile (API key tab). Free keys have usage limits.",
            currentKey = checkPrefs.hybridKey,
            getKeyUrl = "https://hybrid-analysis.com/my-account?tab=%23api-key-tab",
            onSave = { key ->
                viewModel.updateCheckPrefs { it.copy(hybridKey = key) }
            },
            onRemove = {
                viewModel.updateCheckPrefs { it.copy(hybridKey = "") }
            },
            onDismiss = { showHaKeyDialog = false },
        )
    }
}

@Composable
private fun ApiKeyDialog(
    title: String,
    helpText: String,
    currentKey: String,
    getKeyUrl: String,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = helpText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getKeyUrl)))
                        } catch (_: Exception) {}
                    }
                ) {
                    Text("Get a key")
                }
            }
        },
        confirmButton = {
            Row {
                if (currentKey.isNotBlank()) {
                    TextButton(
                        onClick = {
                            onRemove()
                            onDismiss()
                        }
                    ) {
                        Text("Remove")
                    }
                }
                TextButton(
                    onClick = {
                        onSave(keyInput.trim())
                        onDismiss()
                    }
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
