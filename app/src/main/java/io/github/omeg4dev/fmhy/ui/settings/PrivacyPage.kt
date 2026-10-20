package io.github.omeg4dev.fmhy.ui.settings

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val privacyPrefs by viewModel.privacyPrefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearDataDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
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
                SettingsSection(title = "Privacy") {
                    SwitchRow(
                        title = "Block third-party cookies",
                        checked = privacyPrefs.blockThirdPartyCookies,
                        onChange = { checked ->
                            viewModel.updatePrivacy { it.copy(blockThirdPartyCookies = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Hide cookie banners",
                        checked = privacyPrefs.hideCookieBanners,
                        onChange = { checked ->
                            viewModel.updatePrivacy { it.copy(hideCookieBanners = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Clear site data on exit",
                        checked = privacyPrefs.clearDataOnExit,
                        onChange = { checked ->
                            viewModel.updatePrivacy { it.copy(clearDataOnExit = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Open new tabs as private",
                        checked = privacyPrefs.privateTabsByDefault,
                        onChange = { checked ->
                            viewModel.updatePrivacy { it.copy(privateTabsByDefault = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Ask sites not to track me",
                        summary = "Sends Global Privacy Control",
                        checked = privacyPrefs.sendGpc,
                        onChange = { checked ->
                            viewModel.updatePrivacy { it.copy(sendGpc = checked) }
                        },
                    )
                }
            }

            item {
                SettingsSection(title = "Browsing data") {
                    NavRow(
                        title = "Clear browsing data",
                        summary = "Cookies, cache, and history",
                        onClick = { showClearDataDialog = true },
                    )
                }
            }

            extraRows()
        }
    }

    if (showClearDataDialog) {
        var clearCookies by remember { mutableStateOf(true) }
        var clearCache by remember { mutableStateOf(true) }
        var clearHistory by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear browsing data") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Choose what to remove from the in-app browser:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clearCookies = !clearCookies }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = clearCookies,
                            onCheckedChange = { clearCookies = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Cookies & site data", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Cookies, web storage, and saved form data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clearCache = !clearCache }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = clearCache,
                            onCheckedChange = { clearCache = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Cache", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Cached images and page files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clearHistory = !clearHistory }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = clearHistory,
                            onCheckedChange = { clearHistory = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("History of opened sites", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Recorded visited site links",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = clearCookies || clearCache || clearHistory,
                    onClick = {
                        showClearDataDialog = false
                        viewModel.clearBrowsingData(
                            clearCookiesAndStorage = clearCookies,
                            clearCache = clearCache,
                            clearHistory = clearHistory,
                            context = context,
                            onFinished = {
                                onShowSnackbar("Browsing data cleared")
                            },
                        )
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
