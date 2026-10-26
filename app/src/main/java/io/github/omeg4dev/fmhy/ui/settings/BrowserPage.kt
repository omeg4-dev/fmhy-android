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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.OpenLinksWith
import io.github.omeg4dev.fmhy.data.SearchEngine
import io.github.omeg4dev.fmhy.data.ToolbarPosition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToSiteSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val browserPrefs by viewModel.browserPrefs.collectAsStateWithLifecycle()
    val inApp = browserPrefs.openLinksWith == OpenLinksWith.IN_APP

    val eOpenLinksWith = SettingsCatalog.browserOpenLinksWith
    val eSearchEngine = SettingsCatalog.browserSearchEngine
    val eAddressBar = SettingsCatalog.browserAddressBar
    val eSiteBar = SettingsCatalog.browserSiteBar
    // Dead-site rescue
    val eDeadSiteRescue = SettingsCatalog.browserDeadSiteRescue
    val eMaxTabs = SettingsCatalog.browserMaxTabs
    val eRestoreTabs = SettingsCatalog.browserRestoreTabs
    val eForceDark = SettingsCatalog.browserForceDark
    val eTextZoomFollowsApp = SettingsCatalog.browserTextZoomFollowsApp
    // Allow zoom on every site
    val eForceZoom = SettingsCatalog.browserForceZoom
    val eSiteSettings = SettingsCatalog.browserSiteSettings
    val eJavascript = SettingsCatalog.browserJavascript
    val eDesktopDefault = SettingsCatalog.browserDesktopDefault

    val generalIds = if (inApp) {
        listOf(eOpenLinksWith.id, eSearchEngine.id, eAddressBar.id)
    } else {
        listOf(eOpenLinksWith.id)
    }
    val helpersIds = listOf(eSiteBar.id, eDeadSiteRescue.id)
    val tabsIds = listOf(eMaxTabs.id, eRestoreTabs.id)
    val pagesIds = listOf(eForceDark.id, eTextZoomFollowsApp.id, eForceZoom.id, eSiteSettings.id)
    val advancedIds = listOf(eJavascript.id, eDesktopDefault.id)

    val sectionSettingIds = remember(inApp) {
        if (inApp) {
            listOf(generalIds, helpersIds, tabsIds, pagesIds, advancedIds)
        } else {
            listOf(generalIds)
        }
    }
    val listState = rememberFocusedListState(sectionSettingIds)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browser") },
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
            // General section
            item {
                SettingsSection(title = "General") {
                    OptionCardRow(
                        title = eOpenLinksWith.title,
                        options = listOf(
                            OptionCardItem(
                                value = OpenLinksWith.IN_APP,
                                title = "In-app browser",
                                subtitle = "Ad blocking, site bar, and media controls built in",
                                isRecommended = true,
                            ),
                            OptionCardItem(
                                value = OpenLinksWith.CUSTOM_TAB,
                                title = "Custom tab",
                                subtitle = "Opens in a fast Chrome or browser tab over the app",
                            ),
                            OptionCardItem(
                                value = OpenLinksWith.EXTERNAL,
                                title = "External browser",
                                subtitle = "Hands links off to your default browser app",
                            ),
                        ),
                        selected = browserPrefs.openLinksWith,
                        settingId = eOpenLinksWith.id,
                        onSelect = { option ->
                            viewModel.updateBrowser { it.copy(openLinksWith = option) }
                        },
                    )

                    if (inApp) {
                        SettingsDivider()
                        ChoiceRow(
                            title = eSearchEngine.title,
                            options = SearchEngine.entries.map { it to it.label },
                            selected = browserPrefs.searchEngine,
                            settingId = eSearchEngine.id,
                            onSelect = { engine ->
                                viewModel.updateBrowser { it.copy(searchEngine = engine) }
                            },
                        )
                        SettingsDivider()
                        ChoiceRow(
                            title = eAddressBar.title,
                            options = listOf(
                                ToolbarPosition.TOP to "Top",
                                ToolbarPosition.BOTTOM to "Bottom",
                            ),
                            selected = browserPrefs.toolbarPosition,
                            settingId = eAddressBar.id,
                            onSelect = { pos ->
                                viewModel.updateBrowser { it.copy(toolbarPosition = pos) }
                            },
                        )
                    }
                }
            }

            if (!inApp) {
                item {
                    Text(
                        text = "These options apply to the in-app browser.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    )
                }
            } else {
                // FMHY helpers
                item {
                    SettingsSection(title = "FMHY helpers") {
                        SwitchRow(
                            title = eSiteBar.title,
                            summary = eSiteBar.description,
                            checked = browserPrefs.siteBar,
                            settingId = eSiteBar.id,
                            onChange = { checked ->
                                viewModel.updateBrowser { it.copy(siteBar = checked) }
                            },
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = eDeadSiteRescue.title,
                            summary = eDeadSiteRescue.description,
                            checked = browserPrefs.deadSiteRescue,
                            settingId = eDeadSiteRescue.id,
                            onChange = { checked ->
                                viewModel.updateBrowser { it.copy(deadSiteRescue = checked) }
                            },
                        )
                    }
                }

                // Tabs
                item {
                    SettingsSection(title = "Tabs") {
                        ChoiceRow(
                            title = eMaxTabs.title,
                            options = listOf(
                                5 to "5",
                                10 to "10",
                                20 to "20",
                            ),
                            selected = browserPrefs.maxTabs,
                            settingId = eMaxTabs.id,
                            onSelect = { tabs ->
                                viewModel.updateBrowser { it.copy(maxTabs = tabs) }
                            },
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = eRestoreTabs.title,
                            summary = eRestoreTabs.description,
                            checked = browserPrefs.restoreTabs,
                            settingId = eRestoreTabs.id,
                            onChange = { checked ->
                                viewModel.updateBrowser { it.copy(restoreTabs = checked) }
                            },
                        )
                    }
                }

                // Pages
                item {
                    SettingsSection(title = "Pages") {
                        SwitchRow(
                            title = eForceDark.title,
                            summary = eForceDark.description,
                            checked = browserPrefs.forceDark,
                            settingId = eForceDark.id,
                            onChange = { checked ->
                                viewModel.updateBrowser { it.copy(forceDark = checked) }
                            },
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = eTextZoomFollowsApp.title,
                            summary = eTextZoomFollowsApp.description,
                            checked = browserPrefs.textZoomFollowsApp,
                            settingId = eTextZoomFollowsApp.id,
                            onChange = { checked ->
                                viewModel.updateBrowser { it.copy(textZoomFollowsApp = checked) }
                            },
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = eForceZoom.title,
                            summary = eForceZoom.description,
                            checked = browserPrefs.forceZoom,
                            settingId = eForceZoom.id,
                            onChange = { checked ->
                                viewModel.updateBrowser { it.copy(forceZoom = checked) }
                            },
                        )
                        SettingsDivider()
                        NavRow(
                            title = eSiteSettings.title,
                            summary = eSiteSettings.description,
                            settingId = eSiteSettings.id,
                            onClick = onNavigateToSiteSettings,
                        )
                    }
                }

                // Advanced group
                item {
                    ExpandableSection(
                        title = "Advanced",
                        settingIds = advancedIds,
                    ) {
                        SwitchRow(
                            title = eJavascript.title,
                            summary = eJavascript.description,
                            checked = browserPrefs.javascript,
                            settingId = eJavascript.id,
                            onChange = { checked ->
                                viewModel.updateBrowser { it.copy(javascript = checked) }
                            },
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = eDesktopDefault.title,
                            summary = eDesktopDefault.description,
                            checked = browserPrefs.desktopByDefault,
                            settingId = eDesktopDefault.id,
                            onChange = { checked ->
                                viewModel.updateBrowser { it.copy(desktopByDefault = checked) }
                            },
                        )
                    }
                }
            }

            extraRows()
        }
    }
}
