package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.ui.shell.JobKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dataPrefs by viewModel.dataPrefs.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()

    val eSyncNow = SettingsCatalog.syncSyncNow
    val eSyncOnLaunch = SettingsCatalog.syncSyncOnLaunch
    val eLastSynced = SettingsCatalog.syncLastSynced

    val syncIds = listOf(eSyncNow.id, eSyncOnLaunch.id, eLastSynced.id)
    val sectionSettingIds = remember { listOf(syncIds) }
    val listState = rememberFocusedListState(sectionSettingIds)

    val sitesStr = if (state.entryCount > 0) "%,d sites".format(state.entryCount) else "No sites loaded"
    val footerText = "$sitesStr · last synced ${state.lastSyncedText}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync") },
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
                    title = "Sync",
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

            extraRows()
        }

        MobileDataConfirmationDialog(viewModel)
    }
}
