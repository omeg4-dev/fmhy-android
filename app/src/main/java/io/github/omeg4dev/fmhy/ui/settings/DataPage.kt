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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.ui.shell.JobKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dataPrefs by viewModel.dataPrefs.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()

    val sitesStr = if (state.entryCount > 0) "%,d sites".format(state.entryCount) else "No sites loaded"
    val footerText = "$sitesStr · last synced ${state.lastSyncedText}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data & sync") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
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
                    InfoRow(title = "Last updated", value = state.lastSyncedText)
                    val wikiJob = jobs.firstOrNull { it.kind == JobKind.WIKI }
                    if (wikiJob != null) {
                        JobProgressRow(wikiJob)
                    }
                    ButtonRow(
                        text = "Update now",
                        loading = false,
                        enabled = !state.isUpdating,
                        onClick = { viewModel.updateNow() },
                    )
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
                        title = "Sync on launch",
                        checked = dataPrefs.syncOnLaunch,
                        onChange = { checked ->
                            viewModel.updateData { it.copy(syncOnLaunch = checked) }
                        },
                    )
                }
            }

            extraRows()
        }
    }
}
