package io.github.omeg4dev.fmhy.ui.saved

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.SectionNavKey
import io.github.omeg4dev.fmhy.SettingsPage
import io.github.omeg4dev.fmhy.SettingsPageNavKey
import io.github.omeg4dev.fmhy.data.Bookmark
import io.github.omeg4dev.fmhy.data.SavedSort
import io.github.omeg4dev.fmhy.data.fmhyx.FmhyXAccountState
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.nsfw.NSFW_SECTION_ID
import io.github.omeg4dev.fmhy.data.user.Folder
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.nav.LocalAppNav
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import io.github.omeg4dev.fmhy.nav.Tab
import io.github.omeg4dev.fmhy.ui.common.CriticalNoteDialog
import io.github.omeg4dev.fmhy.ui.common.CrossfadeContent
import io.github.omeg4dev.fmhy.ui.common.EmptyState
import io.github.omeg4dev.fmhy.ui.common.EntryDetailSheetHost
import io.github.omeg4dev.fmhy.ui.common.LocalSnackbarHost
import io.github.omeg4dev.fmhy.ui.common.SectionNamePrefs
import io.github.omeg4dev.fmhy.ui.common.SkeletonRows
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.enterStaggerOrdered
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import io.github.omeg4dev.fmhy.ui.common.rememberListEntrance
import io.github.omeg4dev.fmhy.ui.common.sectionDisplayTitle
import io.github.omeg4dev.fmhy.ui.common.showUndo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val StringSetSaver: Saver<Set<String>, List<String>> = Saver(
    save = { it.toList() },
    restore = { it.toSet() },
)

@Composable
fun SavedScreen(
    viewModel: SavedViewModel,
    container: AppContainer,
    modifier: Modifier = Modifier,
    onShowSnackbar: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fmhyXState by remember(container.fmhyXSync) {
        container.fmhyXSync?.state ?: MutableStateFlow(FmhyXAccountState.SignedOut)
    }.collectAsStateWithLifecycle()
    val sharedFolderIds by remember(container.fmhyXSync) {
        container.fmhyXSync?.sharedFolderIds ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle()
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)
    val savedListState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    val nav = LocalAppNav.current
    val bottomBarPadding = LocalBottomBarPadding.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val snackbarHostState = remember { SnackbarHostState() }

    val showInSection: (Entry) -> Unit = { entry ->
        scope.launch {
            val anchor = try {
                entry.headingId?.let { hid ->
                    if (entry.sectionId == NSFW_SECTION_ID) {
                        container.nsfwSource?.readCached()?.headings?.firstOrNull { it.id == hid }?.anchor
                    } else {
                        container.datasetStore.headings(entry.sectionId).firstOrNull { it.id == hid }?.anchor
                    }
                }
            } catch (_: Exception) {
                null
            }
            nav.select(Tab.BROWSE)
            nav.push(SectionNavKey(entry.sectionId, anchor = anchor, focusEntryId = entry.id))
        }
    }

    var showHistory by rememberSaveable { mutableStateOf(false) }
    var selectedFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var collapsedSections by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet()) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet()) }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    BackHandler(enabled = selectionMode) {
        exitSelection()
    }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    var moveTargetIds by remember { mutableStateOf<List<String>?>(null) }
    var bookmarkForNote by remember { mutableStateOf<Bookmark?>(null) }

    var bookmarkForReplacements by remember { mutableStateOf<Bookmark?>(null) }
    var replacementsList by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var isReplacementsLoading by remember { mutableStateOf(false) }

    var detailEntry by remember { mutableStateOf<Entry?>(null) }
    var showDetailSheet by remember { mutableStateOf(false) }

    LaunchedEffect(nav) {
        nav.reselect.filter { it == Tab.SAVED }.collect {
            val targetList = if (showHistory) historyListState else savedListState
            targetList.animateScrollToItem(0)
        }
    }

    // A just-created folder reaches state.folders after the create callback; selecting it earlier would
    // be undone by the deleted-folder fallback below.
    var createdFolderId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.folders, createdFolderId) {
        val id = createdFolderId ?: return@LaunchedEffect
        if (state.folders.any { it.id == id }) {
            selectedFolderId = id
            createdFolderId = null
        }
    }

    val selectedFolder = selectedFolderId?.let { id -> state.folders.firstOrNull { it.id == id } }
    LaunchedEffect(state.folders, selectedFolderId, state.isLoading) {
        // Read the id here, not through selectedFolder: the effect above may have just changed it.
        val id = selectedFolderId ?: return@LaunchedEffect
        if (!state.isLoading && state.folders.none { it.id == id }) {
            selectedFolderId = null
        }
    }

    val websiteNames = SectionNamePrefs.websiteNames
    val titleOf: (Bookmark) -> String = remember(websiteNames) {
        { bm -> sectionDisplayTitle(bm.sectionId, bm.sectionTitle) }
    }

    val filteredItems = remember(state.items, selectedFolderId, query, state.sort, titleOf) {
        SavedGrouping.filterAndSort(
            items = state.items,
            folderId = selectedFolderId,
            query = query,
            sort = state.sort,
            titleOf = titleOf,
        )
    }

    val groupedItems = remember(filteredItems, state.sort, state.groupBySection, titleOf) {
        if (state.groupBySection) {
            SavedGrouping.groupSorted(filteredItems, state.sort, titleOf)
        } else {
            emptyList()
        }
    }

    val visibleOrderItems = remember(filteredItems, groupedItems, state.groupBySection) {
        if (state.groupBySection) groupedItems.flatMap { it.items } else filteredItems
    }

    val selectedVisibleItems = remember(visibleOrderItems, selectedIds) {
        if (selectedIds.isEmpty()) emptyList() else visibleOrderItems.filter { it.bookmark.entryId in selectedIds }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (selectionMode && !showHistory) {
                SavedSelectionTopBar(
                    selectedCount = selectedVisibleItems.size,
                    onClearSelection = ::exitSelection,
                    onSelectAll = {
                        selectedIds = filteredItems.mapTo(HashSet()) { it.bookmark.entryId }
                    },
                    onMoveSelection = {
                        moveTargetIds = selectedVisibleItems.map { it.bookmark.entryId }
                    },
                    onShareSelection = {
                        val title = selectedFolder?.name ?: "Saved"
                        val shareText = ShareListFormatter.format(title, selectedVisibleItems)
                        exitSelection()
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share $title"))
                    },
                    onRemoveSelection = {
                        val idsToRemove = selectedVisibleItems.map { it.bookmark.entryId }
                        exitSelection()
                        haptics.remove()
                        scope.launch {
                            val snapshots = viewModel.removeBookmarksWithSnapshot(idsToRemove)
                            val count = idsToRemove.size
                            val msg = if (count == 1) "Removed from Saved" else "Removed $count sites from Saved"
                            if (snackbarHostState.showUndo(msg)) {
                                viewModel.restoreBookmarks(snapshots)
                            }
                        }
                    },
                )
            } else {
                SavedTopBar(
                    statusNotices = container.statusNotices,
                    fmhyXState = fmhyXState,
                    onOpenFmhyXAccount = {
                        nav.push(SettingsPageNavKey(SettingsPage.FMHYX_ACCOUNT))
                    },
                    showHistory = showHistory,
                    hasHistory = state.historyGroups.isNotEmpty(),
                    sort = state.sort,
                    groupBySection = state.groupBySection,
                    onSelectSort = viewModel::setSort,
                    onToggleGroupBySection = viewModel::setGroupBySection,
                    onClearHistory = { showClearHistoryDialog = true },
                )
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomBarPadding),
            )
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalSnackbarHost provides snackbarHostState) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                SavedTabSwitch(
                    totalSavedCount = state.items.size,
                    showHistory = showHistory,
                    onSelectHistory = { history ->
                        if (history && selectionMode) {
                            exitSelection()
                        }
                        showHistory = history
                    },
                )

                CrossfadeContent(
                    targetState = state.isLoading,
                    modifier = Modifier.fillMaxSize(),
                ) { isLoading ->
                    if (isLoading) {
                        SkeletonRows(count = 6)
                    } else {
                        CrossfadeContent(
                            targetState = showHistory,
                            modifier = Modifier.fillMaxSize(),
                        ) { onHistory ->
                            if (onHistory) {
                                HistoryTabContent(
                                    state = state,
                                    listState = historyListState,
                                    bottomBarPadding = bottomBarPadding,
                                    onEnableKeepHistory = viewModel::enableKeepHistory,
                                    onOpenUrl = linkOpener::openUrl,
                                    onShowDetails = { entry ->
                                        detailEntry = entry
                                        showDetailSheet = true
                                    },
                                )
                            } else {
                                val entrance = rememberListEntrance(selectedFolderId ?: -1L)
                                val folderSiteCount = if (selectedFolderId == null) {
                                    state.items.size
                                } else {
                                    state.folderCounts[selectedFolderId] ?: 0
                                }
                                val isSearching = query.isNotBlank() && state.items.isNotEmpty()

                                LazyColumn(
                                    state = savedListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 16.dp + bottomBarPadding),
                                ) {
                                    if (state.items.isNotEmpty()) {
                                        item(key = "saved_search") {
                                            SavedSearchField(
                                                query = query,
                                                folderSiteCount = folderSiteCount,
                                                onQueryChange = { query = it },
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                            )
                                        }
                                    }

                                    item(key = "saved_chips") {
                                        SavedFolderChipRow(
                                            folders = state.folders,
                                            folderCounts = state.folderCounts,
                                            selectedFolderId = selectedFolderId,
                                            onSelectFolder = { selectedFolderId = it },
                                            onNewFolder = { showCreateFolderDialog = true },
                                            onRenameFolder = { folderToRename = it },
                                            onShareFolder = { folder ->
                                                val folderItems = SavedGrouping.filterAndSort(
                                                    items = state.items,
                                                    folderId = folder.id,
                                                    query = "",
                                                    sort = SavedSort.RECENT,
                                                    titleOf = titleOf,
                                                )
                                                val ordered = SavedGrouping.groupSorted(
                                                    items = folderItems,
                                                    sort = SavedSort.RECENT,
                                                    titleOf = titleOf,
                                                ).flatMap { it.items }
                                                val shareText = ShareListFormatter.format(folder.name, ordered)
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, "Share ${folder.name}"))
                                            },
                                            onDeleteFolder = { folderToDelete = it },
                                        )
                                    }

                                    when {
                                        state.items.isEmpty() && selectedFolder == null -> {
                                            item(key = "empty_all") {
                                                EmptyState(
                                                    icon = Icons.Outlined.BookmarkBorder,
                                                    title = "Nothing saved yet",
                                                    body = "Tap a site, then Save. Saved sites work offline and you can sort them into folders.",
                                                    actionLabel = "Browse FMHY",
                                                    onAction = { nav.select(Tab.BROWSE) },
                                                )
                                            }
                                        }
                                        filteredItems.isEmpty() && isSearching -> {
                                            item(key = "empty_search") {
                                                EmptyState(
                                                    icon = Icons.Default.Search,
                                                    title = "No saved site matches \"${query.trim()}\"",
                                                )
                                            }
                                        }
                                        filteredItems.isEmpty() -> {
                                            item(key = "empty_folder") {
                                                EmptyState(
                                                    icon = Icons.Outlined.FolderOpen,
                                                    title = "This folder is empty",
                                                    body = "Move sites here from their menu.",
                                                )
                                            }
                                        }
                                        state.groupBySection -> {
                                            groupedItems.forEach { group ->
                                                val expanded = isSearching || (group.sectionTitle !in collapsedSections)
                                                item(key = "header_${group.sectionTitle}") {
                                                    SavedSectionHeader(
                                                        title = group.sectionTitle,
                                                        count = group.items.size,
                                                        expanded = expanded,
                                                        onToggle = {
                                                            if (!isSearching) {
                                                                collapsedSections = if (group.sectionTitle in collapsedSections) {
                                                                    collapsedSections - group.sectionTitle
                                                                } else {
                                                                    collapsedSections + group.sectionTitle
                                                                }
                                                            }
                                                        },
                                                    )
                                                }
                                                if (expanded) {
                                                    savedItemsList(
                                                        items = group.items,
                                                        titleOf = titleOf,
                                                        selectionMode = selectionMode,
                                                        selectedIds = selectedIds,
                                                        entrance = entrance,
                                                        onOpenUrl = linkOpener::openUrl,
                                                        onShowDetails = { entry ->
                                                            detailEntry = entry
                                                            showDetailSheet = true
                                                        },
                                                        onReplacements = { bm ->
                                                            bookmarkForReplacements = bm
                                                            isReplacementsLoading = true
                                                            scope.launch {
                                                                replacementsList = viewModel.getReplacements(bm)
                                                                isReplacementsLoading = false
                                                            }
                                                        },
                                                        onToggleSelect = { id ->
                                                            val next = if (id in selectedIds) selectedIds - id else selectedIds + id
                                                            if (next.isEmpty()) exitSelection() else selectedIds = next
                                                        },
                                                        onEnterSelection = { id ->
                                                            selectionMode = true
                                                            selectedIds = setOf(id)
                                                        },
                                                        onMoveTo = { bm -> moveTargetIds = listOf(bm.entryId) },
                                                        onEditNote = { bm -> bookmarkForNote = bm },
                                                        onRemove = { bm ->
                                                            haptics.remove()
                                                            scope.launch {
                                                                val snapshots = viewModel.removeBookmarksWithSnapshot(listOf(bm.entryId))
                                                                if (snackbarHostState.showUndo("Removed from Saved")) {
                                                                    viewModel.restoreBookmarks(snapshots)
                                                                }
                                                            }
                                                        },
                                                        onShowSnackbar = onShowSnackbar,
                                                    )
                                                }
                                            }
                                        }
                                        else -> {
                                            savedItemsList(
                                                items = filteredItems,
                                                titleOf = titleOf,
                                                selectionMode = selectionMode,
                                                selectedIds = selectedIds,
                                                entrance = entrance,
                                                onOpenUrl = linkOpener::openUrl,
                                                onShowDetails = { entry ->
                                                    detailEntry = entry
                                                    showDetailSheet = true
                                                },
                                                onReplacements = { bm ->
                                                    bookmarkForReplacements = bm
                                                    isReplacementsLoading = true
                                                    scope.launch {
                                                        replacementsList = viewModel.getReplacements(bm)
                                                        isReplacementsLoading = false
                                                    }
                                                },
                                                onToggleSelect = { id ->
                                                    val next = if (id in selectedIds) selectedIds - id else selectedIds + id
                                                    if (next.isEmpty()) exitSelection() else selectedIds = next
                                                },
                                                onEnterSelection = { id ->
                                                    selectionMode = true
                                                    selectedIds = setOf(id)
                                                },
                                                onMoveTo = { bm -> moveTargetIds = listOf(bm.entryId) },
                                                onEditNote = { bm -> bookmarkForNote = bm },
                                                onRemove = { bm ->
                                                    haptics.remove()
                                                    scope.launch {
                                                        val snapshots = viewModel.removeBookmarksWithSnapshot(listOf(bm.entryId))
                                                        if (snackbarHostState.showUndo("Removed from Saved")) {
                                                            viewModel.restoreBookmarks(snapshots)
                                                        }
                                                    }
                                                },
                                                onShowSnackbar = onShowSnackbar,
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
    }

    detailEntry?.let { entry ->
        // Outside the Scaffold, so provide the screen's snackbar host again: without it the sheet
        // loses Undo and the pin button.
        CompositionLocalProvider(LocalSnackbarHost provides snackbarHostState) {
            EntryDetailSheetHost(
                entry = entry,
                visible = showDetailSheet,
                onDismiss = { showDetailSheet = false },
                onOpenUrl = linkOpener::openUrl,
                onShowInSection = { showInSection(entry) },
                onShowSnackbar = onShowSnackbar,
            )
        }
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { name ->
                viewModel.createFolder(name) { created ->
                    createdFolderId = created.id
                }
            },
        )
    }

    folderToRename?.let { folder ->
        RenameFolderDialog(
            folder = folder,
            onDismiss = { folderToRename = null },
            onRename = { name -> viewModel.renameFolder(folder.id, name) },
        )
    }

    folderToDelete?.let { folder ->
        DeleteFolderDialog(
            folder = folder,
            isSharedOnFmhyX = folder.id in sharedFolderIds,
            onDismiss = { folderToDelete = null },
            onConfirm = {
                folderToDelete = null
                if (selectedFolderId == folder.id) {
                    selectedFolderId = null
                }
                haptics.remove()
                scope.launch {
                    val snapshot = viewModel.deleteFolderWithSnapshot(folder.id)
                    if (snackbarHostState.showUndo("Deleted folder ${folder.name}")) {
                        if (snapshot != null) viewModel.restoreFolder(snapshot)
                    }
                }
            },
        )
    }

    if (showClearHistoryDialog) {
        ClearHistoryDialog(
            onDismiss = { showClearHistoryDialog = false },
            onConfirm = {
                showClearHistoryDialog = false
                haptics.remove()
                scope.launch {
                    val snapshot = viewModel.clearHistoryWithSnapshot()
                    if (snackbarHostState.showUndo("History cleared")) {
                        viewModel.restoreHistory(snapshot)
                    }
                }
            },
        )
    }

    moveTargetIds?.let { targetIds ->
        MoveToFolderDialog(
            folders = state.folders,
            onDismiss = { moveTargetIds = null },
            onSelectFolder = { folderId ->
                viewModel.moveToFolder(targetIds, folderId)
                moveTargetIds = null
                if (selectionMode) exitSelection()
            },
            onCreateAndMove = { name ->
                viewModel.createFolder(name) { created ->
                    viewModel.moveToFolder(targetIds, created.id)
                }
                moveTargetIds = null
                if (selectionMode) exitSelection()
            },
        )
    }

    bookmarkForNote?.let { bm ->
        EditNoteDialog(
            initialNote = bm.note,
            onDismiss = { bookmarkForNote = null },
            onSave = { note ->
                viewModel.setNote(bm.entryId, note)
                bookmarkForNote = null
            },
        )
    }

    bookmarkForReplacements?.let { bm ->
        ReplacementsBottomSheet(
            bookmark = bm,
            replacements = replacementsList,
            isLoading = isReplacementsLoading,
            onDismiss = { bookmarkForReplacements = null },
            onOpenUrl = linkOpener::openUrl,
            onShowSnackbar = onShowSnackbar,
        )
    }

    UnsafeWarningDialog(linkOpener)
    CriticalNoteDialog(linkOpener)
}

@Composable
private fun HistoryTabContent(
    state: SavedUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    bottomBarPadding: androidx.compose.ui.unit.Dp,
    onEnableKeepHistory: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShowDetails: (Entry) -> Unit,
) {
    if (!state.keepHistory) {
        EmptyState(
            icon = Icons.Outlined.History,
            title = "History is off",
            actionLabel = "Turn on",
            onAction = onEnableKeepHistory,
        )
    } else if (state.historyGroups.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.History,
            title = "No history yet",
            body = "Sites you open from the app show up here.",
        )
    } else {
        val entrance = rememberListEntrance("history")
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
                items(group.items, key = { item -> "hist_${item.history.id}" }) { item ->
                    val rowModifier = Modifier
                        .animateItem()
                        .enterStaggerOrdered(entrance)
                    if (item.liveEntry != null) {
                        HistoryCompactLiveRow(
                            entry = item.liveEntry,
                            onOpenUrl = onOpenUrl,
                            onShowDetails = { onShowDetails(item.liveEntry) },
                            modifier = rowModifier,
                        )
                    } else {
                        HistoryCompactRawRow(
                            item = item,
                            onOpenUrl = { onOpenUrl(item.history.url) },
                            modifier = rowModifier,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.savedItemsList(
    items: List<SavedEntryItem>,
    titleOf: (Bookmark) -> String,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    entrance: io.github.omeg4dev.fmhy.ui.common.ListEntrance,
    onOpenUrl: (String) -> Unit,
    onShowDetails: (Entry) -> Unit,
    onReplacements: (Bookmark) -> Unit,
    onToggleSelect: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onMoveTo: (Bookmark) -> Unit,
    onEditNote: (Bookmark) -> Unit,
    onRemove: (Bookmark) -> Unit,
    onShowSnackbar: (String) -> Unit,
) {
    items(items, key = { it.bookmark.entryId }) { item ->
        val rowModifier = Modifier
            .animateItem()
            .enterStaggerOrdered(entrance)
        val sectionLabel = titleOf(item.bookmark)
        val isSelected = item.bookmark.entryId in selectedIds
        if (item.liveEntry != null) {
            SavedCompactSiteRow(
                item = item,
                liveEntry = item.liveEntry,
                sectionLabel = sectionLabel,
                inSelectionMode = selectionMode,
                isSelected = isSelected,
                onOpenUrl = onOpenUrl,
                onShowDetails = { onShowDetails(item.liveEntry) },
                onToggleSelect = { onToggleSelect(item.bookmark.entryId) },
                onEnterSelection = { onEnterSelection(item.bookmark.entryId) },
                onMoveTo = { onMoveTo(item.bookmark) },
                onEditNote = { onEditNote(item.bookmark) },
                onRemove = { onRemove(item.bookmark) },
                onShowSnackbar = onShowSnackbar,
                modifier = rowModifier,
            )
        } else {
            SavedCompactRemovedRow(
                item = item,
                sectionLabel = sectionLabel,
                inSelectionMode = selectionMode,
                isSelected = isSelected,
                onOpenUrl = { onOpenUrl(item.bookmark.url) },
                onReplacements = { onReplacements(item.bookmark) },
                onToggleSelect = { onToggleSelect(item.bookmark.entryId) },
                onEnterSelection = { onEnterSelection(item.bookmark.entryId) },
                onMoveTo = { onMoveTo(item.bookmark) },
                onEditNote = { onEditNote(item.bookmark) },
                onRemove = { onRemove(item.bookmark) },
                modifier = rowModifier,
            )
        }
    }
}
