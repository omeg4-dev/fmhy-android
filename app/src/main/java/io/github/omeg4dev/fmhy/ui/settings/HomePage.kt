package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.SectionOrder
import io.github.omeg4dev.fmhy.ui.common.displayTitle
import io.github.omeg4dev.fmhy.ui.common.sectionDisplayTitle
import io.github.omeg4dev.fmhy.ui.common.sectionIcon
import io.github.omeg4dev.fmhy.ui.home.HomeBlock
import io.github.omeg4dev.fmhy.ui.home.HomeBlockInfo
import io.github.omeg4dev.fmhy.ui.home.HomeLayout
import io.github.omeg4dev.fmhy.ui.home.homeBlockInfo
import sh.calvin.reorderable.ReorderableColumn

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

    val eBlocks = SettingsCatalog.homeBlocks
    val eSectionOrder = SettingsCatalog.homeSectionOrder
    val ePinned = SettingsCatalog.homePinnedSections
    val eHidden = SettingsCatalog.homeHiddenSections

    val sectionSettingIds = remember {
        listOf(
            listOf(eBlocks.id),
            listOf(eSectionOrder.id),
            listOf(ePinned.id),
            listOf(eHidden.id),
        )
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
            // Build your Home section
            item {
                SettingsSection(
                    title = eBlocks.title,
                    modifier = Modifier.settingHighlight(eBlocks.id),
                ) {
                    Text(
                        text = "Switch blocks on or off and drag the handle to reorder them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    )
                    ReorderableColumn(
                        list = homePrefs.blocks.entries,
                        onSettle = { from, to ->
                            viewModel.updateHome { it.copy(blocks = it.blocks.move(from, to)) }
                        },
                    ) { index, entry, isDragging ->
                        val elevation by animateDpAsState(
                            targetValue = if (isDragging) 4.dp else 0.dp,
                            label = "elevation",
                        )
                        val blockInfo = homeBlockInfo(entry.block)
                        val actions = remember(index, homePrefs.blocks.entries.size) {
                            buildList {
                                if (index > 0) {
                                    add(
                                        CustomAccessibilityAction("Move up") {
                                            viewModel.updateHome { it.copy(blocks = it.blocks.move(index, index - 1)) }
                                            true
                                        }
                                    )
                                }
                                if (index < homePrefs.blocks.entries.lastIndex) {
                                    add(
                                        CustomAccessibilityAction("Move down") {
                                            viewModel.updateHome { it.copy(blocks = it.blocks.move(index, index + 1)) }
                                            true
                                        }
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            if (index > 0) {
                                SettingsDivider()
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 64.dp)
                                    .semantics { customActions = actions }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Reorder ${blockInfo.title}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .draggableHandle()
                                        .size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Icon(
                                    imageVector = blockInfo.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = blockInfo.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = blockInfo.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = entry.enabled,
                                    onCheckedChange = { on ->
                                        viewModel.updateHome { it.copy(blocks = it.blocks.withEnabled(entry.block, on)) }
                                    },
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.updateHome { it.copy(blocks = it.blocks.reset()) }
                            },
                            enabled = homePrefs.blocks != HomeLayout.default(),
                        ) {
                            Text("Reset to default")
                        }
                    }
                }
            }

            // General Home section
            item {
                SettingsSection(title = "Home") {
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
                            val sectionTitle = allSections.firstOrNull { it.id == pin.sectionId }?.displayTitle ?: sectionDisplayTitle(pin.sectionId, pin.sectionId)
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
                            headlineContent = { Text(section.displayTitle) },
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

            extraRows()
        }
    }
}
