package io.github.omeg4dev.fmhy.ui.settings

import android.net.Uri
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val eExportBackup = SettingsCatalog.backupExportBackup
    val eImportBackup = SettingsCatalog.backupImportBackup

    val backupIds = listOf(eExportBackup.id, eImportBackup.id)
    val sectionSettingIds = remember { listOf(backupIds) }
    val listState = rememberFocusedListState(sectionSettingIds)

    var showExportConfirmDialog by remember { mutableStateOf(false) }
    var exportIncludeHistory by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = viewModel.exportBackup(includeHistory = exportIncludeHistory)
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    }
                    onShowSnackbar("Backup exported")
                } catch (e: Exception) {
                    onShowSnackbar(e.message ?: "Failed to export backup")
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: ""
                    val result = viewModel.importBackup(json)
                    onShowSnackbar("Imported ${result.bookmarks} bookmarks, ${result.folders} folders")
                } catch (e: Exception) {
                    onShowSnackbar(e.message ?: "Failed to import backup")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & restore") },
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
    }

    if (showExportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExportConfirmDialog = false },
            title = { Text("Export backup") },
            text = {
                Column {
                    Text("Export bookmarks, folders, and followed sections to a JSON file.")
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
}
