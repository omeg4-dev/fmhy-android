package io.github.omeg4dev.fmhy.ui.search

import androidx.compose.material3.ListItemDefaults
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.nav.LocalAppNav
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import io.github.omeg4dev.fmhy.nav.Tab
import io.github.omeg4dev.fmhy.ui.common.EntryRow
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import io.github.omeg4dev.fmhy.ui.common.sectionIcon
import io.github.omeg4dev.fmhy.ui.check.VerdictCard
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    container: AppContainer,
    onBack: () -> Unit,
    showBack: Boolean = true,
    onShowSnackbar: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val nav = LocalAppNav.current
    val resultsListState = rememberLazyListState()
    var showFiltersSheet by remember { mutableStateOf(false) }

    val linkOpener = rememberLinkOpener(container, onShowSnackbar)

    val trySuggestions = remember {
        listOf(
            "stremio",
            "in:video anime",
            "is:starred adblock",
            "site:github.com",
            "pdf",
            "emulator",
        )
    }

    val activeFilterCount = (if (state.starredOnly) 1 else 0) +
        (if (state.b64Only) 1 else 0) +
        state.selectedSectionFilters.size

    LaunchedEffect(nav) {
        nav.searchFocusRequests.collect {
            val forced = nav.consumeSearchFocusForced()
            if (forced || viewModel.queryText.isEmpty()) {
                focusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(nav) {
        nav.reselect.filter { it == Tab.SEARCH }.collect {
            resultsListState.animateScrollToItem(0)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top area: Surface 52dp tall, 16dp horizontal margin, 8dp top with status-bar inset
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                TextField(
                    value = viewModel.queryText,
                    onValueChange = { viewModel.onQueryChange(it) },
                    placeholder = { Text("Search FMHY...") },
                    leadingIcon = {
                        if (showBack) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    },
                    trailingIcon = {
                        if (viewModel.queryText.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            viewModel.onSearchSubmitted(viewModel.queryText)
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }

            // Filters row under search field (horizontal scroll, 16dp padding, 8dp gaps)
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = activeFilterCount > 0,
                    onClick = { showFiltersSheet = true },
                    leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                    label = { Text(if (activeFilterCount > 0) "Filters · $activeFilterCount" else "Filters") },
                )

                if (state.starredOnly) {
                    InputChip(
                        selected = true,
                        onClick = { viewModel.toggleStarredOnly() },
                        label = { Text("Starred") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove Starred filter",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.toggleStarredOnly() },
                            )
                        },
                    )
                }

                if (state.b64Only) {
                    InputChip(
                        selected = true,
                        onClick = { viewModel.toggleB64Only() },
                        label = { Text("Hidden links") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove Hidden links filter",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.toggleB64Only() },
                            )
                        },
                    )
                }

                state.selectedSectionFilters.forEach { secId ->
                    val title = state.availableSections.find { it.id == secId }?.title ?: secId
                    InputChip(
                        selected = true,
                        onClick = { viewModel.toggleSectionFilter(secId) },
                        label = { Text(title) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove $title filter",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.toggleSectionFilter(secId) },
                            )
                        },
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp)

            if (!state.hasSearched) {
                // Empty query state: Recent searches then "Try" suggestions
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    if (state.recentSearches.isNotEmpty()) {
                        item(key = "hdr_recent") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                            ) {
                                Text(
                                    text = "Recent",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.clearRecentSearches() }) {
                                    Text("Clear all")
                                }
                            }
                        }

                        items(state.recentSearches, key = { "recent_$it" }) { recent ->
                            ListItem(
                                leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                                headlineContent = { Text(recent) },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.removeRecentSearch(recent) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectRecentSearch(recent)
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    },
                            )
                        }
                    }

                    item(key = "hdr_try") {
                        Text(
                            text = "Try",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                        )
                    }

                    item(key = "try_chips") {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            trySuggestions.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { viewModel.onQueryChange(suggestion) },
                                    label = { Text(suggestion) },
                                )
                            }
                        }
                    }
                }
            } else if (state.total == 0 && state.verdict == null) {
                // No-results state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No results",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Check the spelling or remove filters",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (activeFilterCount > 0) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.clearAllFilters() }) {
                                Text("Clear filters")
                            }
                        }
                    }
                }
            } else {
                // Results count line (drop the ms)
                if (state.total > 0) {
                    Text(
                        text = if (state.total >= 200) "200+ results" else "${"%,d".format(state.total)} results",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                LazyColumn(
                    state = resultsListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current),
                ) {
                    if (state.verdict != null) {
                        item(key = "hdr_verdict_card") {
                            VerdictCard(
                                verdict = state.verdict!!,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                onOpenUrl = { linkOpener.openUrl(it) },
                                onShowSnackbar = onShowSnackbar,
                            )
                        }
                    }

                    if (state.total == 0 && state.verdict != null) {
                        item(key = "no_search_results_under_verdict") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No matching entries in search results",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    state.groups.forEach { group ->
                        stickyHeader(key = "hdr_${group.section.id}") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                Text(
                                    text = group.section.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
                                )
                            }
                        }

                        items(group.hits, key = { "hit_${it.entry.id}" }) { hit ->
                            val segments = remember(hit.entry.headingPath) {
                                hit.entry.headingPath
                                    .split(Regex("""\s*[›>]\s*"""))
                                    .filter { it.isNotBlank() }
                            }
                            val headingSegments = if (segments.size > 1) segments.drop(1) else emptyList()
                            val breadcrumb = remember(headingSegments) {
                                headingSegments.joinToString(" › ") { CrossLinks.splitLinkTitle(it).first }
                            }

                            if (breadcrumb.isNotEmpty()) {
                                Text(
                                    text = breadcrumb,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 0.dp),
                                )
                            }

                            EntryRow(
                                entry = hit.entry,
                                nameHl = hit.nameHl,
                                descHl = hit.descriptionHl,
                                onOpenUrl = { linkOpener.openUrl(it) },
                                onShowSnackbar = onShowSnackbar,
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFiltersSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { showFiltersSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Filters",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (activeFilterCount > 0) {
                        TextButton(onClick = { viewModel.clearAllFilters() }) {
                            Text("Clear all")
                        }
                    }
                }

                // Show only group
                Text(
                    text = "Show only",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Starred by FMHY") },
                    trailingContent = {
                        Switch(
                            checked = state.starredOnly,
                            onCheckedChange = { viewModel.toggleStarredOnly() },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.toggleStarredOnly() },
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Hidden (base64) links") },
                    trailingContent = {
                        Switch(
                            checked = state.b64Only,
                            onCheckedChange = { viewModel.toggleB64Only() },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.toggleB64Only() },
                )

                // Sections group
                Text(
                    text = "Sections",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                state.availableSections.forEach { sec ->
                    val isChecked = state.selectedSectionFilters.contains(sec.id)
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Icon(
                                imageVector = sectionIcon(sec.id),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        headlineContent = { Text(sec.title) },
                        trailingContent = {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { viewModel.toggleSectionFilter(sec.id) },
                            )
                        },
                        modifier = Modifier.clickable { viewModel.toggleSectionFilter(sec.id) },
                    )
                }
            }
        }
    }

    UnsafeWarningDialog(linkOpener)
}
