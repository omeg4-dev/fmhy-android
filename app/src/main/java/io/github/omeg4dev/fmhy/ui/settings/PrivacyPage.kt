package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    var showClearDataConfirmDialog by remember { mutableStateOf(false) }

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
            contentPadding = innerPadding,
        ) {
            item {
                SwitchRow(
                    title = "Block third-party cookies",
                    checked = privacyPrefs.blockThirdPartyCookies,
                    onChange = { checked ->
                        viewModel.updatePrivacy { it.copy(blockThirdPartyCookies = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Hide cookie banners",
                    checked = privacyPrefs.hideCookieBanners,
                    onChange = { checked ->
                        viewModel.updatePrivacy { it.copy(hideCookieBanners = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Clear site data on exit",
                    checked = privacyPrefs.clearDataOnExit,
                    onChange = { checked ->
                        viewModel.updatePrivacy { it.copy(clearDataOnExit = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Open new tabs as private",
                    checked = privacyPrefs.privateTabsByDefault,
                    onChange = { checked ->
                        viewModel.updatePrivacy { it.copy(privateTabsByDefault = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Ask sites not to track me",
                    summary = "Sends Global Privacy Control",
                    checked = privacyPrefs.sendGpc,
                    onChange = { checked ->
                        viewModel.updatePrivacy { it.copy(sendGpc = checked) }
                    },
                )
            }

            item {
                NavRow(
                    title = "Clear browsing data now",
                    onClick = { showClearDataConfirmDialog = true },
                )
            }

            extraRows()
        }
    }

    if (showClearDataConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirmDialog = false },
            title = { Text("Clear browsing data") },
            text = { Text("Clear cookies, cache, and history from the in-app browser?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDataConfirmDialog = false
                        onShowSnackbar("Nothing to clear yet")
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
