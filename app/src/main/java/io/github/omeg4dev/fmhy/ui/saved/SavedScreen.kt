package io.github.omeg4dev.fmhy.ui.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.SectionNavKey
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
    val scope = rememberCoroutineScope()
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)
    val listState = rememberLazyListState()
    val nav = LocalAppNav.current
    val bottomBarPadding = LocalBottomBarPadding.current

    LaunchedEffect(nav) {
        nav.reselect.filter { it == Tab.SAVED }.collect {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved") }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
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
            } else {
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
                            if (item.liveEntry != null) {
                                EntryRow(
                                    entry = item.liveEntry,
                                    onShowInSection = {
                                        scope.launch {
                                            val anchor = try {
                                                item.liveEntry.headingId?.let { hid ->
                                                    container.datasetStore.headings(item.liveEntry.sectionId).firstOrNull { it.id == hid }?.anchor
                                                }
                                            } catch (_: Exception) { null }
                                            nav.select(Tab.BROWSE)
                                            nav.push(SectionNavKey(item.liveEntry.sectionId, anchor = anchor, focusEntryId = item.liveEntry.id))
                                        }
                                    },
                                    onOpenUrl = { linkOpener.openUrl(it) },
                                    onShowSnackbar = onShowSnackbar,
                                )
                            } else {
                                ListItem(
                                    headlineContent = { Text(item.bookmark.name) },
                                    supportingContent = {
                                        Text(
                                            text = "No longer listed on FMHY",
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { viewModel.removeBookmark(item.bookmark.entryId) }) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove")
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { linkOpener.openUrl(item.bookmark.url) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    UnsafeWarningDialog(linkOpener)
}
