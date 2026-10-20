package io.github.omeg4dev.fmhy.ui.whatsnew

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.SectionNavKey
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.nav.LocalAppNav
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import io.github.omeg4dev.fmhy.ui.common.DateFormatters
import io.github.omeg4dev.fmhy.ui.common.EntryRow
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import io.github.omeg4dev.fmhy.ui.common.sectionIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WhatsNewScreen(
    viewModel: WhatsNewViewModel,
    container: AppContainer,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val nav = LocalAppNav.current
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)

    var selectedRemovedItem by remember { mutableStateOf<ChangeItem?>(null) }

    val subtitle = remember(state.oldestChangeTs) {
        val oldest = state.oldestChangeTs
        if (oldest != null && oldest > (System.currentTimeMillis() / 1000 - 30 * 86400L)) {
            "From fmhy/edit · since ${DateFormatters.formatHistoryDate(oldest)}"
        } else {
            "From fmhy/edit · last 30 days"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "What's new",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tabs
                PrimaryTabRow(
                    selectedTabIndex = state.selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    WhatsNewTab.entries.forEach { tab ->
                        Tab(
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = { Text(tab.title) },
                        )
                    }
                }

                // Filter chips row
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Following chip
                    item {
                        FilterChip(
                            selected = state.followingFilter,
                            onClick = {
                                if (state.hasFollows) {
                                    viewModel.toggleFollowingFilter()
                                } else {
                                    onShowSnackbar("Follow sections or categories to filter updates here")
                                }
                            },
                            label = { Text("Following") },
                            enabled = true,
                            leadingIcon = if (state.followingFilter) {
                                { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = if (!state.hasFollows) {
                                FilterChipDefaults.filterChipColors(
                                    labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            } else {
                                FilterChipDefaults.filterChipColors()
                            }
                        )
                    }

                    // Saved filter chip (if active)
                    if (state.savedFilter) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.clearSavedFilter() },
                                label = { Text("Saved only") },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Clear saved filter", modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }

                    // Section chips
                    items(state.availableSections, key = { it.id }) { section ->
                        val isSelected = section.id in state.selectedSections
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.toggleSectionFilter(section.id) },
                            label = { Text(section.title) },
                            leadingIcon = {
                                Icon(
                                    imageVector = sectionIcon(section.id),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        )
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // List
                if (state.dayGroups.isEmpty()) {
                    val emptyMsg = when (state.selectedTab) {
                        WhatsNewTab.ADDED -> "Nothing added in the last 30 days"
                        WhatsNewTab.UPDATED -> "Nothing updated in the last 30 days"
                        WhatsNewTab.REMOVED -> "Nothing removed in the last 30 days"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.HistoryToggleOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = emptyMsg,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalBottomBarPadding.current + 16.dp),
                    ) {
                        state.dayGroups.forEach { dayGroup ->
                            stickyHeader(key = "day_${dayGroup.dayHeader}") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = dayGroup.dayHeader,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                            }

                            items(dayGroup.items, key = { it.id }) { item ->
                                val entry = item.entry
                                if (entry != null) {
                                    EntryRow(
                                        entry = entry,
                                        isNew = entry.id in state.newEntryIds,
                                        updateLine = if (state.selectedTab == WhatsNewTab.UPDATED) WhatsNewGrouping.updateLine(item) else null,
                                        onShowInSection = {
                                            scope.launch {
                                                val anchor = try {
                                                    entry.headingId?.let { hid ->
                                                        container.datasetStore.headings(entry.sectionId).firstOrNull { it.id == hid }?.anchor
                                                    }
                                                } catch (_: Exception) { null }
                                                nav.push(
                                                    SectionNavKey(
                                                        sectionId = entry.sectionId,
                                                        anchor = anchor,
                                                        focusEntryId = entry.id,
                                                    )
                                                )
                                            }
                                        },
                                        onOpenUrl = { linkOpener.openUrl(it) },
                                        onShowSnackbar = onShowSnackbar,
                                    )
                                } else {
                                    // Removed or unknown entry plain row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedRemovedItem = item
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    textDecoration = TextDecoration.LineThrough,
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val host = WhatsNewGrouping.extractHost(item.url)
                                            val secTitle = state.availableSections.firstOrNull { it.id == item.sectionId }?.title ?: item.sectionId
                                            val subtitleText = if (host.isNotEmpty()) "$host · $secTitle" else secTitle
                                            Text(
                                                text = subtitleText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedRemovedItem?.let { removedItem ->
        ReplacementsBottomSheet(
            change = removedItem,
            viewModel = viewModel,
            onOpenUrl = { linkOpener.openUrl(it) },
            onShowSnackbar = onShowSnackbar,
            onDismiss = { selectedRemovedItem = null },
        )
    }

    UnsafeWarningDialog(linkOpener)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplacementsBottomSheet(
    change: ChangeItem,
    viewModel: WhatsNewViewModel,
    onOpenUrl: (String) -> Unit,
    onShowSnackbar: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var replacements by remember { mutableStateOf<List<Entry>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(change.id) {
        isLoading = true
        replacements = viewModel.getReplacements(change)
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Replacements for ${change.name}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Other sites in this category",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val list = replacements.orEmpty()
                if (list.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No replacements found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(list, key = { it.id }) { entry ->
                            EntryRow(
                                entry = entry,
                                onOpenUrl = onOpenUrl,
                                onShowSnackbar = onShowSnackbar,
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}
