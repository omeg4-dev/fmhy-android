package io.github.omeg4dev.fmhy.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.omeg4dev.fmhy.data.BookmarksRepository

@Composable
fun BrowserToolbar(
    tab: TabState?,
    tabCount: Int,
    visible: Boolean,
    isFindMode: Boolean,
    findQuery: String,
    findActiveIndex: Int,
    findTotalMatches: Int,
    showBlockedCount: Boolean,
    bookmarksRepository: BookmarksRepository?,
    isSaved: Boolean,
    onShieldClick: () -> Unit,
    onAddressClick: () -> Unit,
    onTabsClick: () -> Unit,
    onReloadClick: () -> Unit,
    onStopClick: () -> Unit,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onNewTabClick: (isPrivate: Boolean) -> Unit,
    onFindInPageClick: () -> Unit,
    onFindQueryChange: (String) -> Unit,
    onFindNext: () -> Unit,
    onFindPrevious: () -> Unit,
    onFindClose: () -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showOverflowMenu by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            if (isFindMode) {
                FindInPageBar(
                    query = findQuery,
                    activeIndex = findActiveIndex,
                    totalMatches = findTotalMatches,
                    onQueryChange = onFindQueryChange,
                    onNext = onFindNext,
                    onPrevious = onFindPrevious,
                    onClose = onFindClose,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Shield Button
                    IconButton(onClick = onShieldClick) {
                        if (showBlockedCount && tab != null && tab.blocked > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ) {
                                        Text(tab.blocked.toString())
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = "Shield")
                            }
                        } else {
                            Icon(Icons.Default.Shield, contentDescription = "Shield")
                        }
                    }

                    // Address Pill
                    AddressPill(
                        tab = tab,
                        onClick = onAddressClick,
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Tabs Button (Rounded square with count)
                    IconButton(onClick = onTabsClick) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tabCount.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Overflow ⋮
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }

                        OverflowMenu(
                            expanded = showOverflowMenu,
                            tab = tab,
                            isSaved = isSaved,
                            onDismiss = { showOverflowMenu = false },
                            onBack = { showOverflowMenu = false; onBackClick() },
                            onForward = { showOverflowMenu = false; onForwardClick() },
                            onReload = { showOverflowMenu = false; onReloadClick() },
                            onStop = { showOverflowMenu = false; onStopClick() },
                            onNewTab = { showOverflowMenu = false; onNewTabClick(false) },
                            onNewPrivateTab = { showOverflowMenu = false; onNewTabClick(true) },
                            onFindInPage = { showOverflowMenu = false; onFindInPageClick() },
                            onShare = {
                                showOverflowMenu = false
                                tab?.url?.let { url ->
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, url)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                }
                            },
                            onCopyLink = {
                                showOverflowMenu = false
                                tab?.url?.let { url ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
                                    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpenInBrowser = {
                                showOverflowMenu = false
                                tab?.url?.let { url ->
                                    openInExternalBrowser(context, url)
                                }
                            },
                            onToggleDesktop = { showOverflowMenu = false; onToggleDesktop() },
                            onToggleBookmark = { showOverflowMenu = false; onToggleBookmark() },
                        )
                    }
                }
            }

            // Progress indicator (2 dp height under toolbar)
            if (tab?.isLoading == true) {
                LinearProgressIndicator(
                    progress = { tab.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
        }
    }
}

@Composable
private fun AddressPill(
    tab: TabState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val url = tab?.url.orEmpty()
    val isSecure = url.startsWith("https://", ignoreCase = true)
    val host = try {
        if (url == "about:blank" || url.isEmpty()) "Search or type URL"
        else Uri.parse(url).host ?: url
    } catch (_: Exception) {
        url
    }

    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (url.isNotEmpty() && url != "about:blank") {
            if (isSecure) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Secure",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Not secure",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = host,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FindInPageBar(
    query: String,
    activeIndex: Int,
    totalMatches: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Find in page") },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
        )

        Spacer(modifier = Modifier.width(8.dp))

        val matchText = if (totalMatches > 0) "${activeIndex + 1}/$totalMatches" else "0/0"
        Text(
            text = matchText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}

@Composable
private fun OverflowMenu(
    expanded: Boolean,
    tab: TabState?,
    isSaved: Boolean,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onFindInPage: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        // Icon row: Back / Forward / Reload or Stop
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = tab?.canGoBack == true) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = onForward, enabled = tab?.canGoForward == true) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
            }
            if (tab?.isLoading == true) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Close, contentDescription = "Stop")
                }
            } else {
                IconButton(onClick = onReload) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                }
            }
        }

        HorizontalDivider()

        DropdownMenuItem(
            text = { Text("New tab") },
            onClick = onNewTab,
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("New private tab") },
            onClick = onNewPrivateTab,
            leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Find in page") },
            onClick = onFindInPage,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Share") },
            onClick = onShare,
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Copy link") },
            onClick = onCopyLink,
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Open in browser") },
            onClick = onOpenInBrowser,
            leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
        )

        DropdownMenuItem(
            text = { Text("Desktop site") },
            onClick = onToggleDesktop,
            trailingIcon = {
                Checkbox(
                    checked = tab?.desktopSite == true,
                    onCheckedChange = { onToggleDesktop() },
                )
            },
        )

        if (tab?.entryId != null) {
            DropdownMenuItem(
                text = { Text(if (isSaved) "Saved" else "Save to Saved") },
                onClick = onToggleBookmark,
                leadingIcon = {
                    Icon(
                        if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
        }
    }
}

fun openInExternalBrowser(context: Context, url: String) {
    val uri = Uri.parse(url)
    val intent = Intent(Intent.ACTION_VIEW, uri)
    val pm = context.packageManager
    val resolveInfos = pm.queryIntentActivities(intent, 0)
    val targetIntents = mutableListOf<Intent>()
    for (info in resolveInfos) {
        val packageName = info.activityInfo.packageName
        if (packageName != context.packageName) {
            val target = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(packageName)
            }
            targetIntents.add(target)
        }
    }
    if (targetIntents.isNotEmpty()) {
        val chooser = Intent.createChooser(targetIntents.removeAt(0), "Open with").apply {
            if (targetIntents.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toTypedArray())
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } else {
        try {
            val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Exception) {}
    }
}
