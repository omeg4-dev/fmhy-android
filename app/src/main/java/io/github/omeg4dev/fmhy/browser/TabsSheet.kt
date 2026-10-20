package io.github.omeg4dev.fmhy.browser

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.showUndo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsSheet(
    tabs: List<TabState>,
    currentTabId: Long?,
    tabManager: TabManager,
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
    var showOverflow by remember { mutableStateOf(false) }
    var showCloseAllDialog by remember { mutableStateOf(false) }
    val sheetSnackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(currentTabId) {
        if (currentTabId != null) {
            tabManager.captureThumbnail(currentTabId)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Tabs (${tabs.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                haptics.tick()
                                onNewTab(false)
                            },
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New tab")
                        }

                        Box {
                            IconButton(onClick = {
                                haptics.tick()
                                showOverflow = true
                            }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                            }

                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New private tab") },
                                    onClick = {
                                        showOverflow = false
                                        haptics.tick()
                                        onNewTab(true)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Security, contentDescription = null)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Close all tabs") },
                                    onClick = {
                                        showOverflow = false
                                        showCloseAllDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    },
                                )
                            }
                        }
                    }
                }

                // 2-column LazyVerticalGrid with 12 dp gaps and 16 dp padding
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        var isDismissed by remember(tab.id) { mutableStateOf(false) }
                        @Suppress("DEPRECATION")
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value != SwipeToDismissBoxValue.Settled && !isDismissed) {
                                    isDismissed = true
                                    haptics.click()
                                    val index = tabs.indexOfFirst { it.id == tab.id }
                                    onCloseTab(tab.id)
                                    scope.launch {
                                        val undo = sheetSnackbarHostState.showUndo("Tab closed")
                                        if (undo) {
                                            tabManager.restoreTab(
                                                url = tab.url,
                                                title = tab.title,
                                                index = index,
                                                isPrivate = tab.isPrivate,
                                                entryId = tab.entryId,
                                            )
                                        }
                                    }
                                    true
                                } else {
                                    true
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {},
                            modifier = Modifier.animateItem(),
                        ) {
                            TabCard(
                                tab = tab,
                                thumbnail = remember(tab.id, thumbnailVersion) { tabManager.getThumbnail(tab.id) },
                                isSelected = tab.id == currentTabId,
                                onSelect = {
                                    haptics.tick()
                                    onSelectTab(tab.id)
                                },
                                onClose = {
                                    haptics.click()
                                    val index = tabs.indexOfFirst { it.id == tab.id }
                                    onCloseTab(tab.id)
                                    scope.launch {
                                        val undo = sheetSnackbarHostState.showUndo("Tab closed")
                                        if (undo) {
                                            tabManager.restoreTab(
                                                url = tab.url,
                                                title = tab.title,
                                                index = index,
                                                isPrivate = tab.isPrivate,
                                                entryId = tab.entryId,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // SnackbarHost inside the bottom sheet
            SnackbarHost(
                hostState = sheetSnackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            )
        }
    }

    if (showCloseAllDialog) {
        AlertDialog(
            onDismissRequest = { showCloseAllDialog = false },
            title = { Text("Close all tabs") },
            text = { Text("Close all ${tabs.size} tabs?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloseAllDialog = false
                        haptics.click()
                        onCloseAll()
                    }
                ) {
                    Text("Close all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseAllDialog = false }) {
                    Text("Cancel")
                }
            },
        )
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
        BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
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

                Text(
                    text = tab.title.ifEmpty { "New tab" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

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