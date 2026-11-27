package io.github.omeg4dev.fmhy.ui.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.SavedSort
import io.github.omeg4dev.fmhy.data.user.Folder
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.shell.StatusNotices
import io.github.omeg4dev.fmhy.ui.shell.SyncStatusIcon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedTopBar(
    statusNotices: StatusNotices,
    showHistory: Boolean,
    hasHistory: Boolean,
    sort: SavedSort,
    groupBySection: Boolean,
    onSelectSort: (SavedSort) -> Unit,
    onToggleGroupBySection: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("Saved") },
        actions = {
            SyncStatusIcon(statusNotices)
            if (!showHistory) {
                Box {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                    }
                    SavedSortMenu(
                        expanded = sortMenuExpanded,
                        sort = sort,
                        groupBySection = groupBySection,
                        onDismiss = { sortMenuExpanded = false },
                        onSelectSort = {
                            sortMenuExpanded = false
                            onSelectSort(it)
                        },
                        onToggleGroupBySection = {
                            sortMenuExpanded = false
                            onToggleGroupBySection(it)
                        },
                    )
                }
            } else if (hasHistory) {
                IconButton(onClick = onClearHistory) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear history")
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedSelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onMoveSelection: () -> Unit,
    onShareSelection: () -> Unit,
    onRemoveSelection: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
        },
        title = { Text("$selectedCount selected") },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = "Select all")
            }
            IconButton(onClick = onMoveSelection, enabled = selectedCount > 0) {
                Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "Move to folder")
            }
            IconButton(onClick = onShareSelection, enabled = selectedCount > 0) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
            IconButton(onClick = onRemoveSelection, enabled = selectedCount > 0) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove")
            }
        },
    )
}

@Composable
internal fun SavedSortMenu(
    expanded: Boolean,
    sort: SavedSort,
    groupBySection: Boolean,
    onDismiss: () -> Unit,
    onSelectSort: (SavedSort) -> Unit,
    onToggleGroupBySection: (Boolean) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        val options = listOf(
            SavedSort.RECENT to "Recently saved",
            SavedSort.A_Z to "A\u2013Z",
            SavedSort.SECTION to "Section",
        )
        options.forEach { (option, label) ->
            DropdownMenuItem(
                text = { Text(label) },
                trailingIcon = if (sort == option) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = { onSelectSort(option) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Group by section") },
            trailingIcon = {
                Checkbox(
                    checked = groupBySection,
                    onCheckedChange = null,
                )
            },
            onClick = { onToggleGroupBySection(!groupBySection) },
        )
    }
}

@Composable
internal fun SavedTabSwitch(
    totalSavedCount: Int,
    showHistory: Boolean,
    onSelectHistory: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SegmentedButton(
            selected = !showHistory,
            onClick = { onSelectHistory(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text("Saved $totalSavedCount", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SegmentedButton(
            selected = showHistory,
            onClick = { onSelectHistory(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text("History", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun SavedSearchField(
    query: String,
    folderSiteCount: Int,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val siteWord = if (folderSiteCount == 1) "site" else "sites"
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = {
            Text(
                text = "Search $folderSiteCount saved $siteWord",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        shape = CircleShape,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun SavedFolderChipRow(
    folders: List<Folder>,
    folderCounts: Map<Long, Int>,
    selectedFolderId: Long?,
    onSelectFolder: (Long?) -> Unit,
    onNewFolder: () -> Unit,
    onRenameFolder: (Folder) -> Unit,
    onShareFolder: (Folder) -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedFolderId == null,
            onClick = { onSelectFolder(null) },
            label = { Text("All") },
        )

        folders.forEach { folder ->
            val count = folderCounts[folder.id] ?: 0
            SavedFolderChip(
                folder = folder,
                count = count,
                selected = selectedFolderId == folder.id,
                onSelect = { onSelectFolder(folder.id) },
                onRename = { onRenameFolder(folder) },
                onShare = { onShareFolder(folder) },
                onDelete = { onDeleteFolder(folder) },
            )
        }

        AssistChip(
            onClick = onNewFolder,
            label = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New folder",
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
    }
}

@Composable
private fun SavedFolderChip(
    folder: Folder,
    count: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val viewConfig = LocalViewConfiguration.current
    val haptics = rememberHaptics()

    LaunchedEffect(interactionSource) {
        var pressJob: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    longPressed = false
                    pressJob?.cancel()
                    pressJob = launch {
                        delay(viewConfig.longPressTimeoutMillis)
                        longPressed = true
                        haptics.longPress()
                        menuExpanded = true
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressJob?.cancel()
                }
            }
        }
    }

    Box {
        FilterChip(
            selected = selected,
            onClick = {
                if (longPressed) {
                    longPressed = false
                } else {
                    onSelect()
                }
            },
            interactionSource = interactionSource,
            label = { Text("${folder.name} ($count)") },
            trailingIcon = if (selected) {
                {
                    Box(
                        modifier = Modifier
                            .size(FilterChipDefaults.IconSize)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { menuExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Folder options",
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                }
            } else {
                null
            },
            modifier = Modifier.semantics {
                onLongClick(label = "Folder options") {
                    menuExpanded = true
                    true
                }
            },
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    menuExpanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text("Share as list") },
                onClick = {
                    menuExpanded = false
                    onShare()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
internal fun SavedSectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle)
            .semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
