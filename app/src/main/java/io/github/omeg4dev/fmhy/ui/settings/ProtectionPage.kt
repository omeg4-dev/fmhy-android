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
fun ProtectionPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onShowSnackbar: (String) -> Unit = {},
    extraRows: LazyListScope.() -> Unit = {},
) {
    val protectionPrefs by viewModel.protectionPrefs.collectAsStateWithLifecycle()

    val eWarnUnsafeSites = SettingsCatalog.protectionWarnUnsafeSites
    val eWarnPotentiallyUnsafeSites = SettingsCatalog.protectionWarnPotentiallyUnsafeSites
    val eGoogleSafeBrowsing = SettingsCatalog.protectionGoogleSafeBrowsing
    val eShowVpnState = SettingsCatalog.protectionShowVpnState
    val eShowVpnHomeIcon = SettingsCatalog.protectionShowVpnHomeIcon
    val eShowIpCountry = SettingsCatalog.protectionShowIpCountry
    val eBlockWithoutVpn = SettingsCatalog.protectionBlockWithoutVpn
    val eBlockPopups = SettingsCatalog.protectionBlockPopups
    val eBlockRedirects = SettingsCatalog.protectionBlockRedirects
    val eBlockAppLinks = SettingsCatalog.protectionBlockAppLinks
    val eAskBeforeDownloads = SettingsCatalog.protectionAskBeforeDownloads
    val eWarnRiskyDownloads = SettingsCatalog.protectionWarnRiskyDownloads
    val eForgetVtChecks = SettingsCatalog.protectionForgetVtChecks

    val protectionIds = listOf(
        eWarnUnsafeSites.id,
        eWarnPotentiallyUnsafeSites.id,
        eGoogleSafeBrowsing.id,
        eShowVpnState.id,
        eShowVpnHomeIcon.id,
        eShowIpCountry.id,
        eBlockWithoutVpn.id,
        eBlockPopups.id,
        eBlockRedirects.id,
        eBlockAppLinks.id,
        eAskBeforeDownloads.id,
        eWarnRiskyDownloads.id,
        eForgetVtChecks.id,
    )
    val sectionSettingIds = remember { listOf(protectionIds) }
    val listState = rememberFocusedListState(sectionSettingIds)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protection") },
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
                SettingsSection(title = "Protection") {
                    SwitchRow(
                        title = eWarnUnsafeSites.title,
                        summary = eWarnUnsafeSites.description,
                        checked = protectionPrefs.unsafeWarning,
                        settingId = eWarnUnsafeSites.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(unsafeWarning = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eWarnPotentiallyUnsafeSites.title,
                        summary = eWarnPotentiallyUnsafeSites.description,
                        checked = protectionPrefs.potentiallyUnsafeWarning,
                        settingId = eWarnPotentiallyUnsafeSites.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(potentiallyUnsafeWarning = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eGoogleSafeBrowsing.title,
                        summary = eGoogleSafeBrowsing.description,
                        checked = protectionPrefs.safeBrowsing,
                        settingId = eGoogleSafeBrowsing.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(safeBrowsing = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eShowVpnState.title,
                        summary = eShowVpnState.description,
                        checked = protectionPrefs.showVpnState,
                        settingId = eShowVpnState.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(showVpnState = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eShowVpnHomeIcon.title,
                        summary = eShowVpnHomeIcon.description,
                        checked = protectionPrefs.showVpnHomeIcon,
                        settingId = eShowVpnHomeIcon.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(showVpnHomeIcon = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eShowIpCountry.title,
                        summary = eShowIpCountry.description,
                        checked = protectionPrefs.showIpCountry,
                        settingId = eShowIpCountry.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(showIpCountry = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eBlockWithoutVpn.title,
                        summary = eBlockWithoutVpn.description,
                        checked = protectionPrefs.blockWithoutVpn,
                        enabled = protectionPrefs.showVpnState,
                        settingId = eBlockWithoutVpn.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(blockWithoutVpn = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eBlockPopups.title,
                        summary = eBlockPopups.description,
                        checked = protectionPrefs.popupBlocker,
                        settingId = eBlockPopups.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(popupBlocker = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eBlockRedirects.title,
                        summary = eBlockRedirects.description,
                        checked = protectionPrefs.redirectGuard,
                        settingId = eBlockRedirects.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(redirectGuard = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eBlockAppLinks.title,
                        summary = eBlockAppLinks.description,
                        checked = protectionPrefs.blockAppLinks,
                        settingId = eBlockAppLinks.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(blockAppLinks = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eAskBeforeDownloads.title,
                        summary = eAskBeforeDownloads.description,
                        checked = protectionPrefs.askBeforeDownload,
                        settingId = eAskBeforeDownloads.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(askBeforeDownload = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eWarnRiskyDownloads.title,
                        summary = eWarnRiskyDownloads.description,
                        checked = protectionPrefs.warnRiskyDownloads,
                        settingId = eWarnRiskyDownloads.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(warnRiskyDownloads = checked) }
                        },
                    )
                    SettingsDivider()
                    NavRow(
                        title = eForgetVtChecks.title,
                        summary = eForgetVtChecks.description,
                        settingId = eForgetVtChecks.id,
                        onClick = {
                            viewModel.clearVtChecks { onShowSnackbar("Checked sites forgotten") }
                        },
                    )
                }
            }

            extraRows()
        }
    }
}
