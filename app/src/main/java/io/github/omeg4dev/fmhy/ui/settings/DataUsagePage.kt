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
import androidx.compose.runtime.remember
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

    val eDownloadWifiOnly = SettingsCatalog.dataUsageDownloadWifiOnly
    val eBrowseWifiOnly = SettingsCatalog.dataUsageBrowseWifiOnly
    val eAppDataWifiOnly = SettingsCatalog.dataUsageAppDataWifiOnly

    val dataUsageIds = listOf(
        eDownloadWifiOnly.id,
        eBrowseWifiOnly.id,
        eAppDataWifiOnly.id,
    )
    val sectionSettingIds = remember { listOf(dataUsageIds) }
    val listState = rememberFocusedListState(sectionSettingIds)

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
            state = listState,
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
                        title = eDownloadWifiOnly.title,
                        summary = eDownloadWifiOnly.description,
                        checked = prefs.downloadWifiOnly,
                        settingId = eDownloadWifiOnly.id,
                        onChange = { checked ->
                            viewModel.updateDataUsage { it.copy(downloadWifiOnly = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eBrowseWifiOnly.title,
                        summary = eBrowseWifiOnly.description,
                        checked = prefs.browseWifiOnly,
                        settingId = eBrowseWifiOnly.id,
                        onChange = { checked ->
                            viewModel.updateDataUsage { it.copy(browseWifiOnly = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eAppDataWifiOnly.title,
                        summary = eAppDataWifiOnly.description,
                        checked = prefs.appDataWifiOnly,
                        settingId = eAppDataWifiOnly.id,
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
