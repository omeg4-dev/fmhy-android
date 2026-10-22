package io.github.omeg4dev.fmhy.ui.shell

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.browser.TabManager
import io.github.omeg4dev.fmhy.browser.TabState
import io.github.omeg4dev.fmhy.browser.bringBrowserToFront
import io.github.omeg4dev.fmhy.browser.openBrowserNewTab
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics

/** The most recently used tabs for the menu, newest first. Pure, unit-tested. */
fun menuTabs(tabs: List<TabState>, limit: Int = 5): List<TabState> =
    tabs.sortedByDescending { it.lastActiveTime }.take(limit)

/** Host without "www." for the second line of a tab row ("" when the URL has none). */
fun menuDomain(url: String): String =
    try { Uri.parse(url).host?.removePrefix("www.").orEmpty() } catch (_: Exception) { "" }

/** "N tab(s)" subtitle, or a hint when there are none. */
fun menuSubtitle(count: Int): String = when (count) {
    0 -> "No tabs open"
    1 -> "1 tab"
    else -> "$count tabs"
}

private fun menuTitle(tab: TabState): String {
    if (tab.title.isNotBlank() && tab.title != "about:blank") return tab.title
    return menuDomain(tab.url).ifBlank { "New tab" }
}

/**
 * Menu opened by holding the bottom bar's Browser item: recent tabs (close with the cross), then
 * new tab / new private tab / show all tabs / close all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuSheet(
    tabManager: TabManager,
    tabs: List<TabState>,
    count: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var showCloseAll by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val recent = menuTabs(tabs)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    Text("Browser", style = MaterialTheme.typography.titleLarge)
                    Text(
                        menuSubtitle(count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (recent.isNotEmpty()) {
                item(key = "tabs-gap") { Spacer(Modifier.size(8.dp)) }
                items(recent, key = { "tab-${it.id}" }) { tab ->
                    TabRow(
                        tab = tab,
                        onOpen = {
                            bringBrowserToFront(context, selectTabId = tab.id)
                            onDismiss()
                        },
                        onClose = {
                            haptics.remove()
                            tabManager.closeTab(tab.id)
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            } else if (count > 0) {
                item(key = "restorable") {
                    Text(
                        if (count == 1) "1 tab from last time" else "$count tabs from last time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).animateItem(),
                    )
                }
            }
            item(key = "divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).animateItem(),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            item(key = "new") {
                ActionRow(Icons.Outlined.Add, "New tab", Modifier.animateItem()) {
                    openBrowserNewTab(context)
                    onDismiss()
                }
            }
            item(key = "new-private") {
                ActionRow(Icons.Outlined.VisibilityOff, "New private tab", Modifier.animateItem()) {
                    openBrowserNewTab(context, private = true)
                    onDismiss()
                }
            }
            if (count > 0) {
                item(key = "show-all") {
                    ActionRow(Icons.Outlined.GridView, "Show all tabs", Modifier.animateItem()) {
                        bringBrowserToFront(context, showTabs = true)
                        onDismiss()
                    }
                }
                item(key = "close-all") {
                    ActionRow(
                        Icons.Outlined.DeleteSweep,
                        "Close all tabs",
                        Modifier.animateItem(),
                        tint = MaterialTheme.colorScheme.error,
                    ) { showCloseAll = true }
                }
            }
        }
    }

    if (showCloseAll) {
        AlertDialog(
            onDismissRequest = { showCloseAll = false },
            title = { Text(if (count == 1) "Close the tab?" else "Close all $count tabs?") },
            confirmButton = {
                TextButton(onClick = {
                    showCloseAll = false
                    haptics.remove()
                    tabManager.closeAllTabsIncludingRestorable()
                    onDismiss()
                }) { Text("Close all") }
            },
            dismissButton = { TextButton(onClick = { showCloseAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TabRow(tab: TabState, onOpen: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onOpen)
            .padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val fav = tab.favicon
            when {
                tab.isPrivate -> Icon(
                    Icons.Outlined.Security, contentDescription = "Private tab",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp),
                )
                fav != null -> Image(
                    bitmap = fav.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)),
                )
                else -> Icon(
                    Icons.Outlined.Language, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(menuTitle(tab), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val domain = menuDomain(tab.url)
            if (domain.isNotEmpty()) {
                Text(
                    domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(
                Icons.Filled.Close, contentDescription = "Close ${menuTitle(tab)}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
