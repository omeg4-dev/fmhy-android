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
    val eCriticalNoteWarning = SettingsCatalog.protectionCriticalNoteWarning
    val eResetHiddenWarnings = SettingsCatalog.protectionResetHiddenWarnings
    val eWarnPotentiallyUnsafeSites = SettingsCatalog.protectionWarnPotentiallyUnsafeSites
    val eGoogleSafeBrowsing = SettingsCatalog.protectionGoogleSafeBrowsing
    val eBlockPopups = SettingsCatalog.protectionBlockPopups
    val eBlockRedirects = SettingsCatalog.protectionBlockRedirects
    val eBlockAppLinks = SettingsCatalog.protectionBlockAppLinks

    val sitesIds = listOf(
        eWarnUnsafeSites.id,
        eWarnPotentiallyUnsafeSites.id,
        eGoogleSafeBrowsing.id,
    )
    val criticalIds = listOf(
        eCriticalNoteWarning.id,
        eResetHiddenWarnings.id,
    )
    val popupIds = listOf(
        eBlockPopups.id,
        eBlockRedirects.id,
        eBlockAppLinks.id,
    )
    val sectionSettingIds = remember {
        listOf(sitesIds, criticalIds, popupIds)
    }
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
                SettingsSection(title = "Unsafe sites") {
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
                }
            }

            item {
                SettingsSection(title = "Critical notes") {
                    SwitchRow(
                        title = eCriticalNoteWarning.title,
                        summary = eCriticalNoteWarning.description,
                        checked = protectionPrefs.criticalNoteWarning,
                        settingId = eCriticalNoteWarning.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(criticalNoteWarning = checked) }
                        },
                    )
                    SettingsDivider()
                    val hiddenWarningsCount by viewModel.hiddenWarningsCount.collectAsStateWithLifecycle()
                    val hiddenSummary = if (hiddenWarningsCount == 0) "None hidden" else "$hiddenWarningsCount hidden"
                    NavRow(
                        title = eResetHiddenWarnings.title,
                        summary = hiddenSummary,
                        settingId = eResetHiddenWarnings.id,
                        onClick = {
                            viewModel.resetHiddenWarnings {
                                onShowSnackbar("Hidden warnings reset")
                            }
                        },
                    )
                }
            }

            item {
                SettingsSection(title = "Pop-ups and redirects") {
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
                }
            }

            extraRows()
        }
    }
}
