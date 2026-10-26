package io.github.omeg4dev.fmhy.browser

import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.ui.common.showUndo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedSheet(
    tab: TabState,
    allowedHosts: List<String>,
    onAllow: (host: String) -> Unit,
    onUnallow: (host: String) -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageHost = remember(tab.url) {
        try {
            Uri.parse(tab.url).host.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedHostForDialog by remember { mutableStateOf<String?>(null) }
    var hasChanged by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val sortedBlockedHosts = remember(tab.blockedHosts, pageHost) {
        BlockedList.sorted(tab.blockedHosts, pageHost)
    }
    val filteredBlockedHosts = remember(sortedBlockedHosts, searchQuery) {
        BlockedList.filter(sortedBlockedHosts, searchQuery)
    }
    val showSearchField = BlockedList.showSearch(tab.blockedHosts.size)

    val filteredAllowedHosts = remember(allowedHosts, searchQuery) {
        if (searchQuery.isBlank()) {
            allowedHosts
        } else {
            allowedHosts.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier.fillMaxHeight(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp),
            ) {
                // 1. Title + close affordance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Blocked on this page",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 2. Help line
                Text(
                    text = "If this site breaks or says it detected an ad blocker, allow the address it needs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 3. Search field
                if (showSearchField) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search addresses") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        } else null,
                    )
                }

                // Reload button after any change
                if (hasChanged) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onReload) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reload page")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4. LazyColumn for blocked and allowed hosts
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (filteredBlockedHosts.isEmpty()) {
                        item(key = "empty_blocked") {
                            Text(
                                text = "Nothing blocked on this page",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .wrapContentHeight(Alignment.CenterVertically),
                            )
                        }
                    } else {
                        items(
                            items = filteredBlockedHosts,
                            key = { "blocked_${it.host}" },
                        ) { blockedHost ->
                            BlockedHostRow(
                                host = blockedHost.host,
                                count = blockedHost.count,
                                onShowFull = { selectedHostForDialog = blockedHost.host },
                                onAllow = {
                                    hasChanged = true
                                    onAllow(blockedHost.host)
                                    scope.launch {
                                        val undone = snackbarHostState.showUndo("Allowed ${blockedHost.host}")
                                        if (undone) {
                                            hasChanged = true
                                            onUnallow(blockedHost.host)
                                        }
                                    }
                                },
                            )
                        }
                    }

                    // 5. Allowed on this site section
                    if (allowedHosts.isNotEmpty()) {
                        item(key = "allowed_header") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Allowed on this site",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 36.dp)
                                    .wrapContentHeight(Alignment.CenterVertically),
                            )
                        }

                        items(
                            items = filteredAllowedHosts,
                            key = { "allowed_$it" },
                        ) { allowedHost ->
                            AllowedHostRow(
                                host = allowedHost,
                                onShowFull = { selectedHostForDialog = allowedHost },
                                onUnallow = {
                                    hasChanged = true
                                    onUnallow(allowedHost)
                                },
                            )
                        }
                    }
                }
            }

            // 6. Own SnackbarHost inside sheet
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )
        }
    }

    if (selectedHostForDialog != null) {
        AlertDialog(
            onDismissRequest = { selectedHostForDialog = null },
            title = { Text("Address details") },
            text = {
                SelectionContainer {
                    Text(
                        text = selectedHostForDialog.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedHostForDialog = null }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun BlockedHostRow(
    host: String,
    count: Int,
    onShowFull: () -> Unit,
    onAllow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .pointerInput(host) {
                    detectTapGestures(onLongPress = { onShowFull() })
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = BlockedList.middleEllipsis(host),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "×$count",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onAllow) {
            Text("Allow")
        }
    }
}

@Composable
private fun AllowedHostRow(
    host: String,
    onShowFull: () -> Unit,
    onUnallow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = BlockedList.middleEllipsis(host),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .pointerInput(host) {
                    detectTapGestures(onLongPress = { onShowFull() })
                },
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onUnallow) {
            Text("Block again")
        }
    }
}
