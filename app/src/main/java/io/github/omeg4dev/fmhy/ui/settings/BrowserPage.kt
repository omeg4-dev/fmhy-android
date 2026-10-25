package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.SettingsPage
import io.github.omeg4dev.fmhy.data.OpenLinksWith
import io.github.omeg4dev.fmhy.data.SearchEngine
import io.github.omeg4dev.fmhy.data.BrowserButtonStyle
import io.github.omeg4dev.fmhy.data.ToolbarPosition
import io.github.omeg4dev.fmhy.data.adblock.FilterListCatalog
import io.github.omeg4dev.fmhy.ui.common.Motion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToPage: (SettingsPage) -> Unit = {},
    onNavigateToSiteSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val browserPrefs by viewModel.browserPrefs.collectAsStateWithLifecycle()
    val blockingPrefs by viewModel.blockingPrefs.collectAsStateWithLifecycle()
    val privacyPrefs by viewModel.privacyPrefs.collectAsStateWithLifecycle()
    val inApp = browserPrefs.openLinksWith == OpenLinksWith.IN_APP

    val adblockStr = when {
        !blockingPrefs.adblock -> "Off"
        blockingPrefs.enabledLists == FilterListCatalog.balancedIds -> "Balanced"
        blockingPrefs.enabledLists == FilterListCatalog.strictIds -> "Strict"
        else -> "Custom"
    }

    val privacyOnCount = listOf(
        privacyPrefs.blockThirdPartyCookies,
        privacyPrefs.hideCookieBanners,
        privacyPrefs.clearDataOnExit,
        privacyPrefs.privateTabsByDefault,
        privacyPrefs.sendGpc,
    ).count { it }
    val privacyStr = if (privacyOnCount >= 3) "Strict" else "Standard"

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
                        title = "Open links with",
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
                        onSelect = { option ->
                            viewModel.updateBrowser { it.copy(openLinksWith = option) }
                        },
                    )

                    if (inApp) {
                        SettingsDivider()
                        ChoiceRow(
                            title = "Search engine",
                            options = SearchEngine.entries.map { it to it.label },
                            selected = browserPrefs.searchEngine,
                            onSelect = { engine ->
                                viewModel.updateBrowser { it.copy(searchEngine = engine) }
                            },
                        )
                        SettingsDivider()
                        ChoiceRow(
                            title = "Address bar",
                            options = listOf(
                                ToolbarPosition.TOP to "Top",
                                ToolbarPosition.BOTTOM to "Bottom",
                            ),
                            selected = browserPrefs.toolbarPosition,
                            onSelect = { pos ->
                                viewModel.updateBrowser { it.copy(toolbarPosition = pos) }
                            },
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = "Back-to-FMHY button",
                            summary = "Floating button over pages that returns to FMHY. The FMHY logo in the address bar always does too.",
                            checked = !browserPrefs.backBubbleHidden,
                            onChange = { checked ->
                                viewModel.updateBrowser { it.copy(backBubbleHidden = !checked) }
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
            }

            // In-app only sections
            item {
                AnimatedVisibility(
                    visible = inApp,
                    enter = expandVertically(animationSpec = Motion.enter()) + fadeIn(animationSpec = Motion.enter()),
                    exit = shrinkVertically(animationSpec = Motion.exit()) + fadeOut(animationSpec = Motion.exit()),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Tabs
                        SettingsSection(title = "Tabs") {
                            ChoiceRow(
                                title = "Maximum tabs",
                                options = listOf(
                                    5 to "5",
                                    10 to "10",
                                    20 to "20",
                                ),
                                selected = browserPrefs.maxTabs,
                                onSelect = { tabs ->
                                    viewModel.updateBrowser { it.copy(maxTabs = tabs) }
                                },
                            )
                            SettingsDivider()
                            SwitchRow(
                                title = "Restore tabs on start",
                                checked = browserPrefs.restoreTabs,
                                onChange = { checked ->
                                    viewModel.updateBrowser { it.copy(restoreTabs = checked) }
                                },
                            )
                        }

                        // FMHY helpers
                        SettingsSection(title = "FMHY helpers") {
                            SwitchRow(
                                title = "FMHY status icon",
                                summary = "Small icon in the address bar showing if a site is on FMHY",
                                checked = browserPrefs.siteBar,
                                onChange = { checked ->
                                    viewModel.updateBrowser { it.copy(siteBar = checked) }
                                },
                            )
                            SettingsDivider()
                            SwitchRow(
                                title = "Dead-site rescue",
                                summary = "Offers mirrors and alternatives when a site doesn't load",
                                checked = browserPrefs.deadSiteRescue,
                                onChange = { checked ->
                                    viewModel.updateBrowser { it.copy(deadSiteRescue = checked) }
                                },
                            )
                        }

                        // Pages
                        SettingsSection(title = "Pages") {
                            SwitchRow(
                                title = "JavaScript",
                                checked = browserPrefs.javascript,
                                onChange = { checked ->
                                    viewModel.updateBrowser { it.copy(javascript = checked) }
                                },
                            )
                            SettingsDivider()
                            SwitchRow(
                                title = "Dark pages in dark theme",
                                checked = browserPrefs.forceDark,
                                onChange = { checked ->
                                    viewModel.updateBrowser { it.copy(forceDark = checked) }
                                },
                            )
                            SettingsDivider()
                            SwitchRow(
                                title = "Desktop site by default",
                                checked = browserPrefs.desktopByDefault,
                                onChange = { checked ->
                                    viewModel.updateBrowser { it.copy(desktopByDefault = checked) }
                                },
                            )
                            SettingsDivider()
                            SwitchRow(
                                title = "Text size follows app",
                                checked = browserPrefs.textZoomFollowsApp,
                                onChange = { checked ->
                                    viewModel.updateBrowser { it.copy(textZoomFollowsApp = checked) }
                                },
                            )
                        }

                        // More / NavRows
                        SettingsSection {
                            NavRow(
                                title = "Video",
                                summary = "Fullscreen, picture-in-picture, speed",
                                onClick = { onNavigateToPage(SettingsPage.VIDEO) },
                            )
                            SettingsDivider()
                            NavRow(
                                title = "Ad blocking & protection",
                                summary = adblockStr,
                                onClick = { onNavigateToPage(SettingsPage.BLOCKING) },
                            )
                            SettingsDivider()
                            NavRow(
                                title = "Privacy",
                                summary = privacyStr,
                                onClick = { onNavigateToPage(SettingsPage.PRIVACY) },
                            )
                            SettingsDivider()
                            NavRow(
                                title = "Site settings",
                                summary = "Manage permissions and overrides per site",
                                onClick = onNavigateToSiteSettings,
                            )
                        }
                    }
                }
            }

            extraRows()
        }
    }
}
