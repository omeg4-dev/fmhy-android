package io.github.omeg4dev.fmhy.ui.settings

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val protectionPrefs by viewModel.protectionPrefs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protection") },
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
                    title = "Warn before unsafe sites",
                    checked = protectionPrefs.unsafeWarning,
                    onChange = { checked ->
                        viewModel.updateProtection { it.copy(unsafeWarning = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Warn before potentially unsafe sites",
                    checked = protectionPrefs.potentiallyUnsafeWarning,
                    onChange = { checked ->
                        viewModel.updateProtection { it.copy(potentiallyUnsafeWarning = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Block pop-ups",
                    checked = protectionPrefs.popupBlocker,
                    onChange = { checked ->
                        viewModel.updateProtection { it.copy(popupBlocker = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Block redirects without a tap",
                    checked = protectionPrefs.redirectGuard,
                    onChange = { checked ->
                        viewModel.updateProtection { it.copy(redirectGuard = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Block links that open apps",
                    summary = "intent:// and market:// links; off = ask",
                    checked = protectionPrefs.blockAppLinks,
                    onChange = { checked ->
                        viewModel.updateProtection { it.copy(blockAppLinks = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Ask before downloads",
                    checked = protectionPrefs.askBeforeDownload,
                    onChange = { checked ->
                        viewModel.updateProtection { it.copy(askBeforeDownload = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Warn about APK and EXE downloads",
                    checked = protectionPrefs.warnRiskyDownloads,
                    onChange = { checked ->
                        viewModel.updateProtection { it.copy(warnRiskyDownloads = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Google Safe Browsing",
                    checked = protectionPrefs.safeBrowsing,
                    onChange = { checked ->
                        viewModel.updateProtection { it.copy(safeBrowsing = checked) }
                    },
                )
            }

            extraRows()
        }
    }
}
