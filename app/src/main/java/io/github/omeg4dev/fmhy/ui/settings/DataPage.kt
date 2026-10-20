package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
            contentPadding = innerPadding,
        ) {
            item {
                InfoRow(
                    title = "Sites",
                    value = if (state.entryCount > 0) "%,d sites".format(state.entryCount) else "No dataset loaded",
                    icon = Icons.Default.Storage,
                )
            }

            item {
                InfoRow(
                    title = "Last synced",
                    value = state.lastSyncedText,
                    icon = Icons.Default.Sync,
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.updateNow() },
                        enabled = !state.isUpdating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isUpdating) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Syncing…")
                        } else {
                            Text("Sync now")
                        }
                    }
                }
                if (!state.updateStatusMessage.isNullOrEmpty()) {
                    Text(
                        text = state.updateStatusMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            item {
                SwitchRow(
                    title = "Sync on launch",
                    checked = dataPrefs.syncOnLaunch,
                    onChange = { checked ->
                        viewModel.updateData { it.copy(syncOnLaunch = checked) }
                    },
                )
            }

            extraRows()
        }
    }
}
