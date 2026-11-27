package io.github.omeg4dev.fmhy.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.ui.shell.JobKind
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import org.json.JSONException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dataPrefs by viewModel.dataPrefs.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val dataUsagePrefs by viewModel.dataUsagePrefs.collectAsStateWithLifecycle()
    val pendingMobileAction by viewModel.pendingMobileAction.collectAsStateWithLifecycle()

    val eSyncNow = SettingsCatalog.syncSyncNow
    val eSyncOnLaunch = SettingsCatalog.syncSyncOnLaunch
    val eLastSynced = SettingsCatalog.syncLastSynced
    val eDownloadWifiOnly = SettingsCatalog.dataUsageDownloadWifiOnly
    val eBrowseWifiOnly = SettingsCatalog.dataUsageBrowseWifiOnly
    val eAppDataWifiOnly = SettingsCatalog.dataUsageAppDataWifiOnly
    val eExportBackup = SettingsCatalog.backupExportBackup
    val eImportBackup = SettingsCatalog.backupImportBackup

    val fmhyDataIds = listOf(eSyncNow.id, eSyncOnLaunch.id, eLastSynced.id)
    val mobileDataIds = listOf(eDownloadWifiOnly.id, eBrowseWifiOnly.id, eAppDataWifiOnly.id)
    val backupIds = listOf(eExportBackup.id, eImportBackup.id)
    val sectionSettingIds = remember { listOf(fmhyDataIds, mobileDataIds, backupIds) }
    val listState = rememberFocusedListState(sectionSettingIds)

    val sitesStr = if (state.entryCount > 0) "%,d sites".format(Locale.getDefault(), state.entryCount) else "No sites loaded"
    val footerText = "$sitesStr · last synced ${state.lastSyncedText}"

    var showExportConfirmDialog by remember { mutableStateOf(false) }
    var exportIncludeHistory by remember { mutableStateOf(false) }
    val pendingBackupImport by viewModel.pendingBackupImport.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            viewModel.updateNotifications { it.copy(enabled = false) }
            onShowSnackbar("Notification permission was denied")
        }
    }

    suspend fun performBackupIo(fallbackMessage: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: IOException) {
            onShowSnackbar(e.message ?: fallbackMessage)
        } catch (e: SecurityException) {
            onShowSnackbar(e.message ?: fallbackMessage)
        } catch (e: IllegalArgumentException) {
            onShowSnackbar(e.message ?: fallbackMessage)
        } catch (e: IllegalStateException) {
            onShowSnackbar(e.message ?: fallbackMessage)
        } catch (e: JSONException) {
            onShowSnackbar(e.message ?: fallbackMessage)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                performBackupIo("Failed to export backup") {
                    val json = viewModel.exportBackup(includeHistory = exportIncludeHistory)
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    }
                    onShowSnackbar("Backup exported")
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                performBackupIo("Failed to import backup") {
                    val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: ""
                    if (viewModel.hasSettingsBackup(json)) {
                        viewModel.stageBackupImport(json)
                    } else {
                        val result = viewModel.importBackup(json, restoreSettings = false)
                        onShowSnackbar("Imported ${result.bookmarks} bookmarks, ${result.folders} folders")
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data & backup") },
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
            item {
                SettingsSection(
                    title = "FMHY data",
                    footer = footerText,
                ) {
                    ButtonRow(
                        text = eSyncNow.title,
                        loading = false,
                        enabled = !state.isUpdating,
                        settingId = eSyncNow.id,
                        onClick = { viewModel.updateNow() },
                    )
                    val wikiJob = jobs.firstOrNull { it.kind == JobKind.WIKI }
                    if (wikiJob != null) {
                        JobProgressRow(wikiJob)
                    }
                    if (!state.updateStatusMessage.isNullOrEmpty()) {
                        Text(
                            text = state.updateStatusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    SettingsDivider()
                    SwitchRow(
                        title = eSyncOnLaunch.title,
                        summary = eSyncOnLaunch.description,
                        checked = dataPrefs.syncOnLaunch,
                        settingId = eSyncOnLaunch.id,
                        onChange = { checked ->
                            viewModel.updateData { it.copy(syncOnLaunch = checked) }
                        },
                    )
                    SettingsDivider()
                    InfoRow(
                        title = eLastSynced.title,
                        value = state.lastSyncedText,
                        settingId = eLastSynced.id,
                    )
                }
            }

            item {
                SettingsSection(title = "Mobile data") {
                    SwitchRow(
                        title = eDownloadWifiOnly.title,
                        summary = eDownloadWifiOnly.description,
                        checked = dataUsagePrefs.downloadWifiOnly,
                        settingId = eDownloadWifiOnly.id,
                        onChange = { checked ->
                            viewModel.updateDataUsage { it.copy(downloadWifiOnly = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eBrowseWifiOnly.title,
                        summary = eBrowseWifiOnly.description,
                        checked = dataUsagePrefs.browseWifiOnly,
                        settingId = eBrowseWifiOnly.id,
                        onChange = { checked ->
                            viewModel.updateDataUsage { it.copy(browseWifiOnly = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eAppDataWifiOnly.title,
                        summary = eAppDataWifiOnly.description,
                        checked = dataUsagePrefs.appDataWifiOnly,
                        settingId = eAppDataWifiOnly.id,
                        onChange = { checked ->
                            viewModel.updateDataUsage { it.copy(appDataWifiOnly = checked) }
                        },
                    )
                }
            }

            item {
                SettingsSection(title = "Backup & restore") {
                    NavRow(
                        title = eExportBackup.title,
                        summary = eExportBackup.description,
                        settingId = eExportBackup.id,
                        onClick = { showExportConfirmDialog = true },
                    )
                    SettingsDivider()
                    NavRow(
                        title = eImportBackup.title,
                        summary = eImportBackup.description,
                        settingId = eImportBackup.id,
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                    )
                }
            }

            extraRows()
        }

        MobileDataConfirmationDialog(
            visible = pendingMobileAction != null,
            onConfirm = viewModel::confirmMobileAction,
            onDismiss = viewModel::dismissMobileActionDialog,
        )
    }

    if (showExportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExportConfirmDialog = false },
            title = { Text("Export backup") },
            text = {
                Column {
                    Text("Export bookmarks, folders, followed sections and settings to a JSON file. API keys and proxy settings are not included.")
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { exportIncludeHistory = !exportIncludeHistory }
                    ) {
                        Checkbox(
                            checked = exportIncludeHistory,
                            onCheckedChange = { exportIncludeHistory = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Include browsing history", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportConfirmDialog = false
                        val dateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).format(LocalDate.now())
                        exportLauncher.launch("fmhy-backup-$dateStr.json")
                    }
                ) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingBackupImport?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingBackupImport() },
            title = { Text("Import backup") },
            text = {
                Column {
                    Text("Bookmarks, folders and followed sections are merged with yours.")
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setPendingRestoreSettings(!pending.restoreSettings) }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = pending.restoreSettings,
                            onCheckedChange = { viewModel.setPendingRestoreSettings(it) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Restore settings", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Replaces your current settings with the ones in this file. The proxy stays as it is.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissPendingBackupImport()
                        scope.launch {
                            performBackupIo("Failed to import backup") {
                                val result = viewModel.importBackup(
                                    pending.json,
                                    restoreSettings = pending.restoreSettings,
                                )
                                val suffix = if (pending.restoreSettings) " and settings" else ""
                                onShowSnackbar("Imported ${result.bookmarks} bookmarks, ${result.folders} folders$suffix")
                                if (pending.restoreSettings &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    viewModel.notificationsEnabled() &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingBackupImport() }) {
                    Text("Cancel")
                }
            },
        )
    }
}
