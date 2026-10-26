package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.SectionOrder
import io.github.omeg4dev.fmhy.ui.common.sectionIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val homePrefs by viewModel.homePrefs.collectAsStateWithLifecycle()
    val pins by viewModel.pins.collectAsStateWithLifecycle()
    val hiddenSections by viewModel.hiddenSections.collectAsStateWithLifecycle()
    val allSections by viewModel.allSections.collectAsStateWithLifecycle()
    val appearance = state.appearance

    val ePersonalHome = SettingsCatalog.homePersonalHome
    val eRandomPick = SettingsCatalog.homeRandomPick
    val eSectionOrder = SettingsCatalog.homeSectionOrder
    val ePinned = SettingsCatalog.homePinnedSections
    val eHidden = SettingsCatalog.homeHiddenSections

    val sectionSettingIds = remember(homePrefs.personalHome) {
        val base = listOf(listOf(ePersonalHome.id, eRandomPick.id, eSectionOrder.id))
        if (homePrefs.personalHome) {
            base + listOf(listOf(ePinned.id), listOf(eHidden.id))
        } else {
            base
        }
    }
    val listState = rememberFocusedListState(sectionSettingIds)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home") },
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
            // General Home section
            item {
                SettingsSection(title = "Home") {
                    SwitchRow(
                        title = ePersonalHome.title,
                        summary = ePersonalHome.description,
                        checked = homePrefs.personalHome,
                        settingId = ePersonalHome.id,
                        onChange = { vmVal ->
                            viewModel.updateHome { it.copy(personalHome = vmVal) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eRandomPick.title,
                        summary = eRandomPick.description,
                        checked = appearance.showRandomPick,
                        settingId = eRandomPick.id,
                        onChange = { viewModel.setShowRandomPick(it) },
                    )
                    SettingsDivider()
                    ChoiceRow(
                        title = eSectionOrder.title,
                        options = listOf(
                            SectionOrder.WEBSITE to "Website",
                            SectionOrder.ALPHABETICAL to "A–Z",
                        ),
                        selected = appearance.sectionOrder,
                        settingId = eSectionOrder.id,
                        onSelect = { viewModel.setSectionOrder(it) },
                    )
                }
            }

            if (homePrefs.personalHome) {
                // Pinned sections
                item {
                    SettingsSection(
                        title = ePinned.title,
                        modifier = Modifier.settingHighlight(ePinned.id),
                    ) {
                        if (pins.isEmpty()) {
                            InfoRow(
                                title = "No pins yet",
                                value = "Pin sections or categories from their menus to see them here",
                            )
                        } else {
                            pins.forEachIndexed { index, pin ->
                                if (index > 0) SettingsDivider()
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
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }

                // Hidden sections
                item {
                    SettingsSection(
                        title = eHidden.title,
                        modifier = Modifier.settingHighlight(eHidden.id),
                    ) {
                        allSections.forEachIndexed { index, section ->
                            if (index > 0) SettingsDivider()
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
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setSectionHidden(section.id, !isHidden)
                                    },
                            )
                        }
                    }
                }
            }

            extraRows()
        }
    }
}
