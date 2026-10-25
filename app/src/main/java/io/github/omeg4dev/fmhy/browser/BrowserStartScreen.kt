package io.github.omeg4dev.fmhy.browser

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.MainActivity
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.ToolbarPosition
import io.github.omeg4dev.fmhy.data.user.UserBookmark
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.shortcuts.EXTRA_OPEN
import io.github.omeg4dev.fmhy.theme.fmhyBlue
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import kotlinx.coroutines.launch

@Composable
fun BrowserStartScreen(
    isPrivate: Boolean,
    userStore: UserStore,
    datasetStore: DatasetStore,
    toolbarPosition: ToolbarPosition,
    contentPadding: PaddingValues,          // space taken by the toolbar overlay (top or bottom)
    onOpenAddressBar: () -> Unit,           // big field tapped → BrowserScreen sets isEditMode = true
    onNavigate: (url: String, entryId: String?) -> Unit,
    onShowMessage: (String) -> Unit,        // snackbar
    onCloseActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    var bookmarks by remember { mutableStateOf<List<UserBookmark>>(emptyList()) }
    var recentSites by remember { mutableStateOf<List<RecentSite>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var isRandomPickRunning by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isPrivate, reloadTrigger) {
        if (!isPrivate) {
            bookmarks = try {
                userStore.bookmarks().sortedByDescending { it.savedAt }.take(8)
            } catch (_: Exception) {
                emptyList()
            }
            recentSites = try {
                groupRecent(userStore.history(limit = 500), limit = 5)
            } catch (_: Exception) {
                emptyList()
            }
        }
        loaded = true
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear recent sites?") },
            text = { Text("This deletes your whole browsing history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            try {
                                userStore.clearHistory()
                            } catch (_: Exception) {}
                            reloadTrigger++
                        }
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isBottom = toolbarPosition == ToolbarPosition.BOTTOM
        val columns = if (maxWidth < 340.dp) 3 else 4
        val scrollState = rememberScrollState()
        val viewportMinusPadding = (maxHeight - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding())
            .coerceAtLeast(0.dp)

        val heroBlock = @Composable {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        fmhyBlue().copy(alpha = 0.22f),
                                        Color.Transparent,
                                    )
                                ),
                                shape = CircleShape,
                            )
                    )
                    Image(
                        painter = painterResource(R.drawable.fmhy_logo_hero),
                        contentDescription = "FMHY",
                        modifier = Modifier.size(88.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Search or type a link",
                        ) {
                            onOpenAddressBar()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Search or type a link",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        val privateBlock = @Composable {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Private tab",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "Sites you open here stay out of your history, and their cookies and site data are deleted when you close your last private tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val tilesBlock = @Composable {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Saved",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (bookmarks.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    putExtra(EXTRA_OPEN, "saved")
                                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                    if (context !is Activity) {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                }
                                context.startActivity(intent)
                                onCloseActivity()
                            }
                        ) {
                            Text("Show all")
                        }
                    }
                }

                val tileItems = buildList<@Composable () -> Unit> {
                    add {
                        RandomPickTile(
                            onClick = {
                                if (isRandomPickRunning) return@RandomPickTile
                                isRandomPickRunning = true
                                scope.launch {
                                    try {
                                        val pick = runCatching { datasetStore.randomTopPick() }.getOrNull()
                                        if (pick != null) {
                                            onNavigate(pick.url, pick.id)
                                        } else {
                                            onShowMessage("FMHY lists are still loading. Try again in a moment.")
                                        }
                                    } finally {
                                        isRandomPickRunning = false
                                    }
                                }
                            }
                        )
                    }
                    for (b in bookmarks) {
                        add {
                            BookmarkTile(
                                bookmark = b,
                                onClick = { onNavigate(b.url, b.entryId) }
                            )
                        }
                    }
                }

                val chunked = tileItems.chunked(columns)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    chunked.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowItems.forEach { tile ->
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.TopCenter,
                                ) {
                                    tile()
                                }
                            }
                            val pad = columns - rowItems.size
                            repeat(pad) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (loaded && bookmarks.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Nothing saved yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Save sites you like and they'll show up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                    if (context !is Activity) {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                }
                                context.startActivity(intent)
                                onCloseActivity()
                            }
                        ) {
                            Text("Find sites on FMHY")
                        }
                    }
                }
            }
        }

        val recentBlock = @Composable {
            if (recentSites.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Recent",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = { showClearDialog = true }) {
                            Text("Clear")
                        }
                    }

                    recentSites.forEach { site ->
                        RecentRow(
                            site = site,
                            onClick = { onNavigate(site.url, null) },
                            onRemove = {
                                haptics.remove()
                                scope.launch {
                                    try {
                                        userStore.deleteHistory(site.historyIds)
                                    } catch (_: Exception) {}
                                    reloadTrigger++
                                }
                            }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(
                    state = scrollState,
                    reverseScrolling = isBottom,
                )
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .heightIn(min = (viewportMinusPadding - 32.dp).coerceAtLeast(0.dp)),
            verticalArrangement = if (isBottom) {
                Arrangement.spacedBy(28.dp, Alignment.Bottom)
            } else {
                Arrangement.spacedBy(28.dp, Alignment.CenterVertically)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isPrivate) {
                if (isBottom) {
                    privateBlock()
                    heroBlock()
                } else {
                    heroBlock()
                    privateBlock()
                }
            } else {
                if (isBottom) {
                    recentBlock()
                    tilesBlock()
                    heroBlock()
                } else {
                    heroBlock()
                    tilesBlock()
                    recentBlock()
                }
            }
        }
    }
}

@Composable
private fun RandomPickTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(fmhyBlue().copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Casino,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = fmhyBlue(),
            )
        }

        Text(
            text = "Random pick",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BookmarkTile(
    bookmark: UserBookmark,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = bookmark.name.trim().firstOrNull()?.uppercase() ?: "•"

    Column(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(fmhyBlue().copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = fmhyBlue(),
            )
        }

        Text(
            text = bookmark.name.ifBlank { bookmark.url },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    site: RecentSite,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = site.host.firstOrNull()?.uppercase() ?: "•"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onRemove,
                onLongClickLabel = "Remove from recent",
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(fmhyBlue().copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = fmhyBlue(),
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = site.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${site.host} · ${formatRelativeTime(site.lastVisit)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
