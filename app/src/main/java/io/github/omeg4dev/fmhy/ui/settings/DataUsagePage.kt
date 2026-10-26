package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
fun DataUsagePage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val prefs by viewModel.dataUsagePrefs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data usage") },
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsSection {
                    SwitchRow(
                        title = "Download only on Wi-Fi",
                        checked = prefs.downloadWifiOnly,
                        onChange = { checked ->
                            viewModel.updateDataUsage { it.copy(downloadWifiOnly = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Browse only on Wi-Fi",
                        checked = prefs.browseWifiOnly,
                        onChange = { checked ->
                            viewModel.updateDataUsage { it.copy(browseWifiOnly = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "App data and updates only on Wi-Fi",
                        checked = prefs.appDataWifiOnly,
                        onChange = { checked ->
                            viewModel.updateDataUsage { it.copy(appDataWifiOnly = checked) }
                        },
                    )
                }
            }

            extraRows()
        }
    }
}
