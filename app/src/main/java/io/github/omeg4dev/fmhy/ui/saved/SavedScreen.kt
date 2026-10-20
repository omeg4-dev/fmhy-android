package io.github.omeg4dev.fmhy.ui.saved

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.Bookmark
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.user.Folder
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.nav.LocalAppNav
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import io.github.omeg4dev.fmhy.nav.Tab
import io.github.omeg4dev.fmhy.ui.common.EntryRow
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    viewModel: SavedViewModel,
    container: AppContainer,
    onShowSnackbar: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)
    val listState = rememberLazyListState()
    val nav = LocalAppNav.current
    val bottomBarPadding = LocalBottomBarPadding.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showInSection: (io.github.omeg4dev.fmhy.data.model.Entry) -> Unit = { entry ->
        scope.launch {
            val anchor = try {
                entry.headingId?.let { hid ->
                    container.datasetStore.headings(entry.sectionId).firstOrNull { it.id == hid }?.anchor
                }
            } catch (_: Exception) { null }
            nav.select(Tab.BROWSE)
            nav.push(io.github.omeg4dev.fmhy.SectionNavKey(entry.sectionId, anchor = anchor, focusEntryId = entry.id))
        }
    }

    var selectedTab by rememberSaveable { mutableStateOf("all") }

    // Dialog & sheet states
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    var bookmarkForMove by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkForNote by remember { mutableStateOf<Bookmark?>(null) }
    var noteInputText by remember { mutableStateOf("") }
    var newFolderNameInput by remember { mutableStateOf("") }

    var bookmarkForReplacements by remember { mutableStateOf<Bookmark?>(null) }
    var replacementsList by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var isReplacementsLoading by remember { mutableStateOf(false) }

    LaunchedEffect(nav) {
        nav.reselect.filter { it == Tab.SAVED }.collect {
            listState.animateScrollToItem(0)
        }
    }

    // Resolve active folder if on a folder tab
    val selectedFolderId = if (selectedTab.startsWith("folder_")) {
        selectedTab.removePrefix("folder_").toLongOrNull()
    } else null
    val selectedFolder = selectedFolderId?.let { id -> state.folders.firstOrNull { it.id == id } }

    // If folder was deleted, fallback to all
    LaunchedEffect(state.folders, selectedFolderId) {
        if (selectedFolderId != null && selectedFolder == null && !state.isLoading) {
            selectedTab = "all"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved") },
                actions = {
                    if (selectedTab == "history" && state.historyGroups.isNotEmpty()) {
                        IconButton(onClick = { showClearHistoryDialog = true }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear history")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Chip row under top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = selectedTab == "all",
                    onClick = { selectedTab = "all" },
                    label = { Text("All") },
                )

                state.folders.forEach { folder ->
                    val count = state.folderCounts[folder.id] ?: 0
                    FilterChip(
                        selected = selectedTab == "folder_${folder.id}",
                        onClick = { selectedTab = "folder_${folder.id}" },
                        label = { Text("${folder.name} ($count)") },
                    )
                }

                FilterChip(
                    selected = selectedTab == "history",
                    onClick = { selectedTab = "history" },
                    label = { Text("History") },
                )

                AssistChip(
                    onClick = {
                        newFolderNameInput = ""
                        showCreateFolderDialog = true
                    },
                    label = { Text("+ Folder") },
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else when {
                    selectedTab == "history" -> {
                        if (!state.keepHistory) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = "History is off",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = { viewModel.enableKeepHistory() }) {
                                        Text("Turn on history")
                                    }
                                }
                            }
                        } else if (state.historyGroups.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = "Sites you open show up here.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp + bottomBarPadding),
                            ) {
                                state.historyGroups.forEach { group ->
                                    item(key = "hist_header_${group.dayLabel}") {
                                        Text(
                                            text = group.dayLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
                                        )
                                    }
                                    items(group.items, key = { "hist_${it.history.id}" }) { item ->
                                        if (item.liveEntry != null) {
                                            EntryRow(
                                                entry = item.liveEntry,
                                                onShowInSection = { showInSection(item.liveEntry) },
                                                onOpenUrl = { linkOpener.openUrl(it) },
                                                onShowSnackbar = onShowSnackbar,
                                            )
                                        } else {
                                            ListItem(
                                                headlineContent = { Text(item.history.title) },
                                                supportingContent = {
                                                    Text(
                                                        text = "${item.host} · ${item.timeLabel}",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { linkOpener.openUrl(item.history.url) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    selectedFolder != null -> {
                        // Folder View
                        val folderGroups = state.folderGroups[selectedFolder.id] ?: emptyList()
                        val folderItemsCount = state.folderCounts[selectedFolder.id] ?: 0

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp + bottomBarPadding),
                        ) {
                            item(key = "folder_header_card") {
                                var overflowExpanded by remember { mutableStateOf(false) }
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = selectedFolder.name,
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "$folderItemsCount ${if (folderItemsCount == 1) "site" else "sites"}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        Box {
                                            IconButton(onClick = { overflowExpanded = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Folder options")
                                            }
                                            DropdownMenu(
                                                expanded = overflowExpanded,
                                                onDismissRequest = { overflowExpanded = false },
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Rename") },
                                                    onClick = {
                                                        overflowExpanded = false
                                                        folderToRename = selectedFolder
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Share as list") },
                                                    onClick = {
                                                        overflowExpanded = false
                                                        val allFolderItems = folderGroups.flatMap { it.items }
                                                        val shareText = ShareListFormatter.format(selectedFolder.name, allFolderItems)
                                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                                        }
                                                        context.startActivity(Intent.createChooser(sendIntent, "Share ${selectedFolder.name}"))
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete") },
                                                    onClick = {
                                                        overflowExpanded = false
                                                        folderToDelete = selectedFolder
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (folderGroups.isEmpty()) {
                                item(key = "empty_folder_notice") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 48.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "No sites in this folder yet",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                folderGroups.forEach { group ->
                                    item(key = "header_${group.sectionTitle}") {
                                        Text(
                                            text = group.sectionTitle,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
                                        )
                                    }
                                    items(group.items, key = { it.bookmark.entryId }) { item ->
                                        val noteFirstLine = item.bookmark.note.lines().firstOrNull { it.isNotBlank() }
                                        if (item.liveEntry != null) {
                                            EntryRow(
                                                entry = item.liveEntry,
                                                onShowInSection = { showInSection(item.liveEntry) },
                                                notePreview = noteFirstLine,
                                                onOpenUrl = { linkOpener.openUrl(it) },
                                                onShowSnackbar = onShowSnackbar,
                                                trailingContent = {
                                                    RowOverflowMenu(
                                                        bookmark = item.bookmark,
                                                        onMoveTo = { bookmarkForMove = item.bookmark },
                                                        onEditNote = {
                                                            bookmarkForNote = item.bookmark
                                                            noteInputText = item.bookmark.note
                                                        },
                                                        onRemove = { viewModel.removeBookmark(item.bookmark.entryId) },
                                                    )
                                                }
                                            )
                                        } else {
                                            RemovedSiteRow(
                                                item = item,
                                                notePreview = noteFirstLine,
                                                onOpenUrl = { linkOpener.openUrl(item.bookmark.url) },
                                                onReplacements = {
                                                    bookmarkForReplacements = item.bookmark
                                                    isReplacementsLoading = true
                                                    scope.launch {
                                                        replacementsList = viewModel.getReplacements(item.bookmark)
                                                        isReplacementsLoading = false
                                                    }
                                                },
                                                trailingContent = {
                                                    RowOverflowMenu(
                                                        bookmark = item.bookmark,
                                                        onMoveTo = { bookmarkForMove = item.bookmark },
                                                        onEditNote = {
                                                            bookmarkForNote = item.bookmark
                                                            noteInputText = item.bookmark.note
                                                        },
                                                        onRemove = { viewModel.removeBookmark(item.bookmark.entryId) },
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    state.groups.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BookmarkBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "Nothing saved yet",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Long-press any site and tap Save.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    else -> {
                        // All View
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp + bottomBarPadding),
                        ) {
                            state.groups.forEach { group ->
                                item(key = "header_${group.sectionTitle}") {
                                    Text(
                                        text = group.sectionTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
                                    )
                                }
                                items(group.items, key = { it.bookmark.entryId }) { item ->
                                    val noteFirstLine = item.bookmark.note.lines().firstOrNull { it.isNotBlank() }
                                    if (item.liveEntry != null) {
                                        EntryRow(
                                            entry = item.liveEntry,
                                            onShowInSection = { showInSection(item.liveEntry) },
                                            folderLabel = item.folderName,
                                            notePreview = noteFirstLine,
                                            onOpenUrl = { linkOpener.openUrl(it) },
                                            onShowSnackbar = onShowSnackbar,
                                        )
                                    } else {
                                        RemovedSiteRow(
                                            item = item,
                                            folderLabel = item.folderName,
                                            notePreview = noteFirstLine,
                                            onOpenUrl = { linkOpener.openUrl(item.bookmark.url) },
                                            onReplacements = {
                                                bookmarkForReplacements = item.bookmark
                                                isReplacementsLoading = true
                                                scope.launch {
                                                    replacementsList = viewModel.getReplacements(item.bookmark)
                                                    isReplacementsLoading = false
                                                }
                                            },
                                            trailingContent = {
                                                IconButton(onClick = { viewModel.removeBookmark(item.bookmark.entryId) }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = newFolderNameInput,
                    onValueChange = { newFolderNameInput = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newFolderNameInput.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.createFolder(trimmed) { created ->
                                selectedTab = "folder_${created.id}"
                            }
                        }
                        showCreateFolderDialog = false
                    },
                    enabled = newFolderNameInput.isNotBlank(),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    folderToRename?.let { folder ->
        var renameText by remember(folder) { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text("Rename folder") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.renameFolder(folder.id, trimmed)
                        }
                        folderToRename = null
                    },
                    enabled = renameText.isNotBlank(),
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete folder") },
            text = { Text("Delete folder \"${folder.name}\"? Sites in this folder will remain in Saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFolder(folder.id)
                        if (selectedTab == "folder_${folder.id}") {
                            selectedTab = "all"
                        }
                        folderToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear history") },
            text = { Text("Clear all recorded browsing history?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryDialog = false
                        viewModel.clearHistory()
                        onShowSnackbar("History cleared")
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    bookmarkForMove?.let { bm ->
        var showMoveNewFolder by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { bookmarkForMove = null },
            title = { Text("Move to folder") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    ListItem(
                        headlineContent = { Text("No folder") },
                        modifier = Modifier.clickable {
                            viewModel.moveToFolder(bm.entryId, null)
                            bookmarkForMove = null
                        }
                    )
                    state.folders.forEach { f ->
                        ListItem(
                            headlineContent = { Text(f.name) },
                            modifier = Modifier.clickable {
                                viewModel.moveToFolder(bm.entryId, f.id)
                                bookmarkForMove = null
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ListItem(
                        headlineContent = { Text("+ New folder…", color = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            newFolderNameInput = ""
                            showMoveNewFolder = true
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { bookmarkForMove = null }) { Text("Cancel") }
            }
        )

        if (showMoveNewFolder) {
            AlertDialog(
                onDismissRequest = { showMoveNewFolder = false },
                title = { Text("New folder") },
                text = {
                    OutlinedTextField(
                        value = newFolderNameInput,
                        onValueChange = { newFolderNameInput = it },
                        label = { Text("Folder name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val trimmed = newFolderNameInput.trim()
                            if (trimmed.isNotEmpty()) {
                                viewModel.createFolder(trimmed) { created ->
                                    viewModel.moveToFolder(bm.entryId, created.id)
                                }
                            }
                            showMoveNewFolder = false
                            bookmarkForMove = null
                        },
                        enabled = newFolderNameInput.isNotBlank(),
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMoveNewFolder = false }) { Text("Cancel") }
                }
            )
        }
    }

    bookmarkForNote?.let { bm ->
        AlertDialog(
            onDismissRequest = { bookmarkForNote = null },
            title = { Text("Note") },
            text = {
                Column {
                    OutlinedTextField(
                        value = noteInputText,
                        onValueChange = {
                            if (it.length <= 500) {
                                noteInputText = it
                            }
                        },
                        label = { Text("Note (max 500 chars)") },
                        supportingText = {
                            Text(
                                text = "${noteInputText.length} / 500",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                            )
                        },
                        maxLines = 5,
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setNote(bm.entryId, noteInputText.trim())
                        bookmarkForNote = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { bookmarkForNote = null }) { Text("Cancel") }
            }
        )
    }

    // Replacements Sheet
    bookmarkForReplacements?.let { bm ->
        ModalBottomSheet(
            onDismissRequest = { bookmarkForReplacements = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Replacements",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Other sites in ${bm.headingPath.ifEmpty { bm.sectionTitle }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isReplacementsLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (replacementsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No replacements found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    replacementsList.forEach { replacement ->
                        EntryRow(
                            entry = replacement,
                            onOpenUrl = {
                                bookmarkForReplacements = null
                                linkOpener.openUrl(it)
                            },
                            onShowSnackbar = onShowSnackbar,
                        )
                    }
                }
            }
        }
    }

    UnsafeWarningDialog(linkOpener)
}

@Composable
private fun RowOverflowMenu(
    bookmark: Bookmark,
    onMoveTo: () -> Unit,
    onEditNote: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Row options")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Move to…") },
                onClick = {
                    expanded = false
                    onMoveTo()
                }
            )
            DropdownMenuItem(
                text = { Text("Edit note") },
                onClick = {
                    expanded = false
                    onEditNote()
                }
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                onClick = {
                    expanded = false
                    onRemove()
                }
            )
        }
    }
}

@Composable
private fun RemovedSiteRow(
    item: SavedEntryItem,
    folderLabel: String? = null,
    notePreview: String? = null,
    onOpenUrl: () -> Unit,
    onReplacements: () -> Unit,
    trailingContent: @Composable () -> Unit,
) {
    val removedText = if (item.removedDateText != null) {
        "No longer listed on FMHY · ${item.removedDateText}"
    } else {
        "No longer listed on FMHY"
    }

    ListItem(
        headlineContent = { Text(item.bookmark.name) },
        supportingContent = {
            Column {
                Text(
                    text = removedText,
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (folderLabel != null || !notePreview.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (folderLabel != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = folderLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (!notePreview.isNullOrEmpty()) {
                            Text(
                                text = notePreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onReplacements,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("Replacements", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        trailingContent = trailingContent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenUrl),
    )
}
