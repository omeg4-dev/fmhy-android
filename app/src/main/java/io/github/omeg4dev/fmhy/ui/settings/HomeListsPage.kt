package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.SectionOrder
import io.github.omeg4dev.fmhy.ui.common.sectionIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeListsPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToLegend: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val homePrefs by viewModel.homePrefs.collectAsStateWithLifecycle()
    val pins by viewModel.pins.collectAsStateWithLifecycle()
    val hiddenSections by viewModel.hiddenSections.collectAsStateWithLifecycle()
    val allSections by viewModel.allSections.collectAsStateWithLifecycle()
    val appearance = state.appearance

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home & lists") },
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
            // Group: Home
            item {
                SettingsGroupHeader(text = "Home")
            }

            item {
                SwitchRow(
                    title = "Personal Home",
                    summary = "Pin categories and hide sections you don't use",
                    checked = homePrefs.personalHome,
                    onChange = { vmVal ->
                        viewModel.updateHome { it.copy(personalHome = vmVal) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Show random pick",
                    summary = "A random recommended site on the Browse tab",
                    checked = appearance.showRandomPick,
                    onChange = { viewModel.setShowRandomPick(it) },
                )
            }

            item {
                ChoiceRow(
                    title = "Section order",
                    options = listOf(
                        SectionOrder.WEBSITE to "Website",
                        SectionOrder.ALPHABETICAL to "A–Z",
                    ),
                    selected = appearance.sectionOrder,
                    onSelect = { viewModel.setSectionOrder(it) },
                )
            }

            // Group: Lists
            item {
                SettingsGroupHeader(text = "Lists")
            }

            item {
                ChoiceRow(
                    title = "Section layout",
                    options = listOf(
                        SectionLayout.CONTINUOUS to "Continuous",
                        SectionLayout.CATEGORIES to "Categories",
                    ),
                    selected = appearance.sectionLayout,
                    onSelect = { viewModel.setSectionLayout(it) },
                )
            }

            item {
                SwitchRow(
                    title = "Starred only",
                    summary = "Show only sites FMHY recommends (⭐ / 🌟)",
                    checked = appearance.starredOnly,
                    onChange = { viewModel.setStarredOnly(it) },
                )
            }

            item {
                SwitchRow(
                    title = "Show indexes",
                    summary = "Include 🌐 index sites that list other sites",
                    checked = appearance.showIndexes,
                    onChange = { viewModel.setShowIndexes(it) },
                )
            }

            item {
                ChoiceRow(
                    title = "NEW badges",
                    options = listOf(
                        0 to "Off",
                        3 to "3 days",
                        7 to "7 days",
                        14 to "14 days",
                    ),
                    selected = homePrefs.newBadgeDays,
                    onSelect = { days ->
                        viewModel.updateHome { it.copy(newBadgeDays = days) }
                    },
                )
            }

            item {
                NavRow(
                    title = "Icon legend",
                    summary = "What every icon means",
                    onClick = onNavigateToLegend,
                )
            }

            if (homePrefs.personalHome) {
                item {
                    SettingsGroupHeader(text = "Pinned")
                }

                if (pins.isEmpty()) {
                    item {
                        InfoRow(
                            title = "No pins yet",
                            value = "Pin sections or categories from their menus to see them here",
                        )
                    }
                } else {
                    itemsIndexed(pins, key = { _, pin -> "${pin.sectionId}:${pin.headingId}" }) { index, pin ->
                        val sectionTitle = allSections.firstOrNull { it.id == pin.sectionId }?.title ?: pin.sectionId
                        val titleText = if (pin.headingId.isEmpty()) sectionTitle else "$sectionTitle › ${pin.title}"
                        ListItem(
                            leadingContent = {
                                Icon(
                                    imageVector = sectionIcon(pin.sectionId),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = titleText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.movePinUp(index) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            contentDescription = "Move up",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.movePinDown(index) },
                                        enabled = index < pins.lastIndex,
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowDownward,
                                            contentDescription = "Move down",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removePin(pin.sectionId, pin.headingId) },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove pin",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }

                item {
                    SettingsGroupHeader(text = "Hidden sections")
                }

                items(allSections, key = { it.id }) { section ->
                    val isHidden = section.id in hiddenSections
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = sectionIcon(section.id),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        headlineContent = { Text(section.title) },
                        trailingContent = {
                            Checkbox(
                                checked = isHidden,
                                onCheckedChange = { checked ->
                                    viewModel.setSectionHidden(section.id, checked)
                                },
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setSectionHidden(section.id, !isHidden)
                            },
                    )
                }
            }

            extraRows()
        }
    }
}
