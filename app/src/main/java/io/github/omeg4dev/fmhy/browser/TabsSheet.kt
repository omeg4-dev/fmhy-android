package io.github.omeg4dev.fmhy.browser

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.TabsView
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.showUndo
import kotlinx.coroutines.launch

/** Full-screen tab overview (list by default, grid switchable). System back closes it. */
@Composable
fun TabsSheet(
    tabs: List<TabState>,
    currentTabId: Long?,
    tabManager: TabManager,
    tabsView: TabsView,
    onTabsViewChange: (TabsView) -> Unit,
    onDismiss: () -> Unit,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onNewTab: (isPrivate: Boolean) -> Unit,
    onCloseAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val thumbnailVersion by tabManager.thumbnailVersion.collectAsStateWithLifecycle()
    val expanded by tabManager.expandedTabGroups.collectAsStateWithLifecycle()
    var showOverflow by remember { mutableStateOf(false) }
    var showCloseAllDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val isGrid = tabsView == TabsView.GRID

    LaunchedEffect(currentTabId) {
        if (currentTabId != null) tabManager.captureThumbnail(currentTabId)
    }

    val showSearch = tabs.size >= TabsLogic.SEARCH_MIN_TABS
    val allGrouped = TabsLogic.group(tabs)
    val rows = TabsLogic.rows(
        tabs = tabs,
        query = if (showSearch) query else "",
        grouping = !isGrid,
        expanded = expanded,
        currentTabId = currentTabId,
    )
    val gridState = rememberLazyGridState()

    // Bring the current tab into view when the overview opens.
    LaunchedEffect(Unit) {
        val key = TabsLogic.scrollKey(rows, currentTabId) ?: return@LaunchedEffect
        val idx = rows.indexOfFirst { it.key == key }
        if (idx > 1) gridState.scrollToItem(idx - 1)
    }

    fun closeWithUndo(tab: TabState) {
        haptics.remove()
        val index = tabs.indexOfFirst { it.id == tab.id }
        onCloseTab(tab.id)
        scope.launch {
            if (snackbarHostState.showUndo("Tab closed")) {
                tabManager.restoreTab(
                    url = tab.url,
                    title = tab.title,
                    index = index,
                    isPrivate = tab.isPrivate,
                    entryId = tab.entryId,
                )
            }
        }
    }

    fun closeGroupWithUndo(group: TabsRow.Group) {
        haptics.remove()
        val n = group.tabs.size
        val savedTabs = group.tabs.map { tab ->
            val index = tabs.indexOfFirst { it.id == tab.id }
            tab to index
        }
        val containsCurrent = group.tabs.any { it.id == currentTabId }
        val currentTab = group.tabs.firstOrNull { it.id == currentTabId }
        val nonCurrentTabs = group.tabs.filter { it.id != currentTabId }
        // Closing every open tab would end the browser (and the Undo with it): keep it open on an empty tab,
        // which closes itself once Undo switches back to the group.
        val closesAll = group.tabs.size == tabs.size
        if (closesAll) tabManager.openNewTabPage(isPrivate = false)

        nonCurrentTabs.forEach { onCloseTab(it.id) }
        if (currentTab != null) {
            onCloseTab(currentTab.id)
        }

        scope.launch {
            if (snackbarHostState.showUndo("$n tabs closed")) {
                var restoredCurrentTabId: Long? = null
                var firstRestoredId: Long? = null
                savedTabs.sortedBy { it.second }.forEach { (tab, index) ->
                    val restoredId = tabManager.restoreTab(
                        url = tab.url,
                        title = tab.title,
                        index = if (index != -1) index else tabs.size,
                        isPrivate = tab.isPrivate,
                        entryId = tab.entryId,
                        select = false,
                    )
                    if (tab.id == currentTabId) {
                        restoredCurrentTabId = restoredId
                    }
                    if (firstRestoredId == null) firstRestoredId = restoredId
                }
                val select = if (containsCurrent) restoredCurrentTabId else if (closesAll) firstRestoredId else null
                if (select != null) tabManager.selectTab(select)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler(onBack = onDismiss)
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { it / 12 },
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopRow(
                            title = TabsLogic.title(tabs.size, allGrouped.priv.size),
                            isGrid = isGrid,
                            onBack = onDismiss,
                            onToggleView = { onTabsViewChange(if (isGrid) TabsView.LIST else TabsView.GRID) },
                            onNewTab = { onNewTab(false) },
                            showOverflow = showOverflow,
                            onOverflow = { showOverflow = it },
                            onNewPrivate = { onNewTab(true) },
                            onCloseAll = { showCloseAllDialog = true },
                        )
                        if (showSearch) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                placeholder = { Text("Search tabs") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (query.isNotEmpty()) {
                                        IconButton(onClick = { query = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(28.dp),
                                keyboardOptions = KeyboardOptions.Default,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(if (isGrid) 2 else 1),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isGrid) 12.dp else 8.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        ) {
                            rows.forEach { row ->
                                when (row) {
                                    is TabsRow.Header -> item(key = row.key, span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            text = "${row.label} · ${row.count}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                                        )
                                    }
                                    is TabsRow.Group -> item(key = row.key) {
                                        TabGroupRow(
                                            group = row,
                                            onToggle = { tabManager.toggleTabGroup(row.groupKey) },
                                            onClose = { closeGroupWithUndo(row) },
                                            modifier = Modifier.animateItem(),
                                        )
                                    }
                                    is TabsRow.Item -> item(key = row.key) {
                                        val tab = row.tab
                                        var isDismissed by remember(tab.id) { mutableStateOf(false) }
                                        @Suppress("DEPRECATION")
                                        val dismissState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { value ->
                                                if (value != SwipeToDismissBoxValue.Settled && !isDismissed) {
                                                    isDismissed = true
                                                    closeWithUndo(tab)
                                                }
                                                true
                                            },
                                        )
                                        SwipeToDismissBox(
                                            state = dismissState,
                                            backgroundContent = {},
                                            modifier = Modifier
                                                .animateItem()
                                                .padding(start = if (row.indented) 16.dp else 0.dp),
                                        ) {
                                            if (isGrid) {
                                                TabCard(
                                                    tab = tab,
                                                    thumbnail = remember(tab.id, thumbnailVersion) { tabManager.getThumbnail(tab.id) },
                                                    isSelected = tab.id == currentTabId,
                                                    onSelect = { onSelectTab(tab.id) },
                                                    onClose = { closeWithUndo(tab) },
                                                )
                                            } else {
                                                TabListRow(
                                                    tab = tab,
                                                    isSelected = tab.id == currentTabId,
                                                    onSelect = { onSelectTab(tab.id) },
                                                    onClose = { closeWithUndo(tab) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (rows.isEmpty() && query.isNotBlank()) {
                        Text(
                            text = "No tabs match \"${query.trim()}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    )
                }
            }
        }
    }

    if (showCloseAllDialog) {
        AlertDialog(
            onDismissRequest = { showCloseAllDialog = false },
            title = { Text("Close all ${tabs.size} tabs?") },
            confirmButton = {
                TextButton(onClick = {
                    showCloseAllDialog = false
                    haptics.remove()
                    onCloseAll()
                }) { Text("Close all") }
            },
            dismissButton = { TextButton(onClick = { showCloseAllDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TopRow(
    title: String,
    isGrid: Boolean,
    onBack: () -> Unit,
    onToggleView: () -> Unit,
    onNewTab: () -> Unit,
    showOverflow: Boolean,
    onOverflow: (Boolean) -> Unit,
    onNewPrivate: () -> Unit,
    onCloseAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close tab overview") }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        IconButton(onClick = onToggleView) {
            Icon(
                imageVector = if (isGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                contentDescription = if (isGrid) "Show as list" else "Show as grid",
            )
        }
        IconButton(onClick = onNewTab) { Icon(Icons.Default.Add, contentDescription = "New tab") }
        Box {
            IconButton(onClick = { onOverflow(true) }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
            DropdownMenu(expanded = showOverflow, onDismissRequest = { onOverflow(false) }) {
                DropdownMenuItem(
                    text = { Text("New private tab") },
                    onClick = { onOverflow(false); onNewPrivate() },
                    leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("Close all tabs") },
                    onClick = { onOverflow(false); onCloseAll() },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun TabGroupRow(
    group: TabsRow.Group,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val colors = MaterialTheme.colorScheme
    val arrowRotation by animateFloatAsState(
        targetValue = if (group.expanded) 180f else 0f,
        label = "arrowRotation",
    )
    val firstFavicon = remember(group.tabs) {
        group.tabs.firstOrNull { it.favicon != null }?.favicon
    }

    Surface(
        shape = shape,
        color = if (group.containsCurrent) colors.secondaryContainer else colors.surfaceContainerHigh,
        border = if (group.containsCurrent) BorderStroke(2.dp, colors.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .semantics {
                stateDescription = if (group.expanded) "Expanded" else "Collapsed"
            }
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (group.isPrivate) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                } else if (firstFavicon != null) {
                    Image(
                        bitmap = firstFavicon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${group.site} · ${group.tabs.size} tabs",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (group.containsCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (group.containsCurrent) colors.onSecondaryContainer else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (group.containsCurrent) {
                    Text(
                        text = "Current tab inside",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(arrowRotation),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close ${group.tabs.size} tabs",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TabListRow(
    tab: TabState,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = shape,
        color = if (isSelected) colors.secondaryContainer else colors.surfaceContainerHigh,
        border = if (isSelected) BorderStroke(2.dp, colors.primary) else null,
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (tab.isPrivate) {
                    Icon(Icons.Default.Security, contentDescription = "Private tab", tint = colors.primary, modifier = Modifier.size(20.dp))
                } else if (tab.favicon != null) {
                    Image(bitmap = tab.favicon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(20.dp).clip(CircleShape))
                } else {
                    Icon(Icons.Outlined.Public, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tab.title.ifEmpty { "New tab" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val domain = TabsLogic.domain(tab.url)
                val sub = when {
                    isSelected && domain.isNotEmpty() -> "Current · $domain"
                    isSelected -> "Current"
                    else -> domain
                }
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close tab", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: TabState,
    thumbnail: Bitmap?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val borderStroke = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onSelect),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top: favicon 16 dp + title (1 line) + close X (48 dp target)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab.isPrivate) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Private Tab",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                } else if (tab.favicon != null) {
                    Image(
                        bitmap = tab.favicon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tab.title.ifEmpty { "New tab" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val domain = TabsLogic.domain(tab.url)
                    if (domain.isNotEmpty()) {
                        Text(
                            text = domain,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close tab",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Body: thumbnail 3:4, ContentScale.Crop, top-aligned
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
            ) {
                if (tab.isPrivate) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Private",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                } else if (thumbnail != null && !thumbnail.isRecycled) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        }
    }
}