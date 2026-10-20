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
import io.github.omeg4dev.fmhy.SettingsPage
import io.github.omeg4dev.fmhy.data.OpenLinksWith
import io.github.omeg4dev.fmhy.data.SearchEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToPage: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val browserPrefs by viewModel.browserPrefs.collectAsStateWithLifecycle()
    val inApp = browserPrefs.openLinksWith == OpenLinksWith.IN_APP
    val disabledSummary = "Only for the in-app browser"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browser") },
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
                ChoiceRow(
                    title = "Open links with",
                    options = listOf(
                        OpenLinksWith.IN_APP to "In-app browser",
                        OpenLinksWith.CUSTOM_TAB to "Custom tab",
                        OpenLinksWith.EXTERNAL to "External browser",
                    ),
                    selected = browserPrefs.openLinksWith,
                    onSelect = { option ->
                        viewModel.updateBrowser { it.copy(openLinksWith = option) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "FMHY site bar",
                    summary = if (inApp) "Shows where a site is listed on FMHY" else disabledSummary,
                    checked = browserPrefs.siteBar,
                    enabled = inApp,
                    onChange = { checked ->
                        viewModel.updateBrowser { it.copy(siteBar = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Dead-site rescue",
                    summary = if (inApp) "Offers mirrors and alternatives when a site doesn't load" else disabledSummary,
                    checked = browserPrefs.deadSiteRescue,
                    enabled = inApp,
                    onChange = { checked ->
                        viewModel.updateBrowser { it.copy(deadSiteRescue = checked) }
                    },
                )
            }

            item {
                ChoiceRow(
                    title = "Search engine",
                    options = SearchEngine.entries.map { it to it.label },
                    selected = browserPrefs.searchEngine,
                    enabled = inApp,
                    onSelect = { engine ->
                        viewModel.updateBrowser { it.copy(searchEngine = engine) }
                    },
                )
            }

            item {
                ChoiceRow(
                    title = "Maximum tabs",
                    options = listOf(
                        5 to "5",
                        10 to "10",
                        20 to "20",
                    ),
                    selected = browserPrefs.maxTabs,
                    enabled = inApp,
                    onSelect = { tabs ->
                        viewModel.updateBrowser { it.copy(maxTabs = tabs) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Restore tabs on start",
                    summary = if (!inApp) disabledSummary else null,
                    checked = browserPrefs.restoreTabs,
                    enabled = inApp,
                    onChange = { checked ->
                        viewModel.updateBrowser { it.copy(restoreTabs = checked) }
                    },
                )
            }

            item {
                NavRow(
                    title = "Content blocking",
                    summary = if (!inApp) disabledSummary else null,
                    enabled = inApp,
                    onClick = { onNavigateToPage(SettingsPage.BLOCKING) },
                )
            }

            item {
                NavRow(
                    title = "Protection",
                    summary = if (!inApp) disabledSummary else null,
                    enabled = inApp,
                    onClick = { onNavigateToPage(SettingsPage.PROTECTION) },
                )
            }

            item {
                NavRow(
                    title = "Privacy",
                    summary = if (!inApp) disabledSummary else null,
                    enabled = inApp,
                    onClick = { onNavigateToPage(SettingsPage.PRIVACY) },
                )
            }

            item {
                NavRow(
                    title = "Pages",
                    summary = if (!inApp) disabledSummary else null,
                    enabled = inApp,
                    onClick = { onNavigateToPage(SettingsPage.PAGE) },
                )
            }

            item {
                NavRow(
                    title = "Video",
                    summary = if (!inApp) disabledSummary else null,
                    enabled = inApp,
                    onClick = { onNavigateToPage(SettingsPage.VIDEO) },
                )
            }

            extraRows()
        }
    }
}
