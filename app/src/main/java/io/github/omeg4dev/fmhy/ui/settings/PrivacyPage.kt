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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

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
    val dataPrefs by viewModel.dataPrefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val eBlockThirdPartyCookies = SettingsCatalog.privacyBlockThirdPartyCookies
    val eHideCookieBanners = SettingsCatalog.privacyHideCookieBanners
    val eSendGpc = SettingsCatalog.privacySendGpc
    val ePrivateTabsByDefault = SettingsCatalog.privacyPrivateTabsByDefault
    val eClearDataOnExit = SettingsCatalog.privacyClearDataOnExit
    val eKeepHistory = SettingsCatalog.privacyKeepHistory
    val eClearHistory = SettingsCatalog.privacyClearHistory
    val eClearBrowsingData = SettingsCatalog.privacyClearBrowsingData

    val privacyIds = listOf(
        eBlockThirdPartyCookies.id,
        eHideCookieBanners.id,
        eSendGpc.id,
        ePrivateTabsByDefault.id,
        eClearDataOnExit.id,
    )
    val historyIds = listOf(eKeepHistory.id, eClearHistory.id)
    val browsingDataIds = listOf(eClearBrowsingData.id)

    val sectionSettingIds = remember { listOf(privacyIds, historyIds, browsingDataIds) }
    val listState = rememberFocusedListState(sectionSettingIds)

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showClearHistoryConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & history") },
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
            // Privacy section
            item {
                SettingsSection(title = "Privacy") {
                    SwitchRow(
                        title = eBlockThirdPartyCookies.title,
                        summary = eBlockThirdPartyCookies.description,
                        checked = privacyPrefs.blockThirdPartyCookies,
                        settingId = eBlockThirdPartyCookies.id,
                        onChange = { checked ->
                            viewModel.updatePrivacy { it.copy(blockThirdPartyCookies = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eHideCookieBanners.title,
                        summary = eHideCookieBanners.description,
                        checked = privacyPrefs.hideCookieBanners,
                        settingId = eHideCookieBanners.id,
                        onChange = { checked ->
                            viewModel.updatePrivacy { it.copy(hideCookieBanners = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eSendGpc.title,
                        summary = eSendGpc.description,
                        checked = privacyPrefs.sendGpc,
                        settingId = eSendGpc.id,
                        onChange = { checked ->
                            viewModel.updatePrivacy { it.copy(sendGpc = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = ePrivateTabsByDefault.title,
                        summary = ePrivateTabsByDefault.description,
                        checked = privacyPrefs.privateTabsByDefault,
                        settingId = ePrivateTabsByDefault.id,
                        onChange = { checked ->
                            viewModel.updatePrivacy { it.copy(privateTabsByDefault = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eClearDataOnExit.title,
                        summary = eClearDataOnExit.description,
                        checked = privacyPrefs.clearDataOnExit,
                        settingId = eClearDataOnExit.id,
                        onChange = { checked ->
                            viewModel.updatePrivacy { it.copy(clearDataOnExit = checked) }
                        },
                    )
                }
            }

            // History section
            item {
                SettingsSection(title = "History") {
                    SwitchRow(
                        title = eKeepHistory.title,
                        summary = eKeepHistory.description,
                        checked = dataPrefs.keepHistory,
                        settingId = eKeepHistory.id,
                        onChange = { checked ->
                            viewModel.updateData { it.copy(keepHistory = checked) }
                        },
                    )
                    SettingsDivider()
                    NavRow(
                        title = eClearHistory.title,
                        summary = eClearHistory.description,
                        settingId = eClearHistory.id,
                        onClick = { showClearHistoryConfirmDialog = true },
                    )
                }
            }

            // Browsing data section
            item {
                SettingsSection(title = "Browsing data") {
                    NavRow(
                        title = eClearBrowsingData.title,
                        summary = eClearBrowsingData.description,
                        settingId = eClearBrowsingData.id,
                        onClick = { showClearDataDialog = true },
                    )
                }
            }

            extraRows()
        }
    }

    if (showClearHistoryConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirmDialog = false },
            title = { Text("Clear history") },
            text = { Text("Clear all recorded browsing history?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryConfirmDialog = false
                        scope.launch {
                            viewModel.clearHistory()
                            onShowSnackbar("History cleared")
                        }
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
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
