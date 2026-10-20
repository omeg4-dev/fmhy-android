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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.SectionNavKey
import io.github.omeg4dev.fmhy.data.search.CategoryHit
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.nav.LocalAppNav
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import io.github.omeg4dev.fmhy.nav.Tab
import io.github.omeg4dev.fmhy.ui.common.CrossfadeContent
import io.github.omeg4dev.fmhy.ui.common.EmptyState
import io.github.omeg4dev.fmhy.ui.common.EntryRow
import io.github.omeg4dev.fmhy.ui.common.LocalSnackbarHost
import io.github.omeg4dev.fmhy.ui.common.SkeletonRows
import io.github.omeg4dev.fmhy.ui.common.SwipeToSave
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.enterStagger
import io.github.omeg4dev.fmhy.ui.common.parseHighlight
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import io.github.omeg4dev.fmhy.ui.common.sectionIcon
import io.github.omeg4dev.fmhy.ui.check.VerdictCard
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private enum class SearchContentMode {
    RECENT,
    SEARCHING,
    EMPTY,
    RESULTS,
}

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
    val scope = rememberCoroutineScope()
    var showFiltersSheet by remember { mutableStateOf(false) }

    val linkOpener = rememberLinkOpener(container, onShowSnackbar)
    val haptics = rememberHaptics()

    val browserPrefs by container.settingsRepository.browserPrefs.collectAsStateWithLifecycle(initialValue = BrowserPrefs())
    val encodedQuery = remember(state.query) {
        try {
            java.net.URLEncoder.encode(state.query, "UTF-8")
        } catch (_: Exception) {
            state.query
        }
    }
    val searchUrl = "${browserPrefs.searchEngine.queryUrl}$encodedQuery"

    val snackbarHostState = remember { SnackbarHostState() }
    val bottomBarPadding = LocalBottomBarPadding.current

    val contentMode = when {
        !state.hasSearched -> SearchContentMode.RECENT
        state.isSearching -> SearchContentMode.SEARCHING
        state.total == 0 && state.verdict == null && state.categories.isEmpty() -> SearchContentMode.EMPTY
        else -> SearchContentMode.RESULTS
    }

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

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomBarPadding),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        CompositionLocalProvider(LocalSnackbarHost provides snackbarHostState) {
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
                                    .minimumInteractiveComponentSize()
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
                                    .minimumInteractiveComponentSize()
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
                                    .minimumInteractiveComponentSize()
                                    .size(16.dp)
                                    .clickable { viewModel.toggleSectionFilter(secId) },
                            )
                        },
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp)

            CrossfadeContent(
                targetState = contentMode,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { mode ->
                when (mode) {
                    SearchContentMode.RECENT -> {
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
                    }
                    SearchContentMode.SEARCHING -> {
                        SkeletonRows(count = 6, modifier = Modifier.padding(top = 16.dp))
                    }
                    SearchContentMode.EMPTY -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                EmptyState(
                                    icon = Icons.Outlined.SearchOff,
                                    title = "No sites match “${state.query}”",
                                    body = "Try fewer words or another spelling.",
                                    actionLabel = "Search the web",
                                    onAction = { linkOpener.openUrl(searchUrl) },
                                )
                                state.suggestion?.let { suggestion ->
                                    TextButton(onClick = {
                                        haptics.tick()
                                        viewModel.onQueryChange(suggestion)
                                    }) {
                                        Text(
                                            text = buildAnnotatedString {
                                                append("Did you mean ")
                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(suggestion) }
                                                append("?")
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    SearchContentMode.RESULTS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
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
                                contentPadding = PaddingValues(bottom = 24.dp + bottomBarPadding),
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
                                        EmptyState(
                                            icon = Icons.Outlined.SearchOff,
                                            title = "No sites match “${state.query}”",
                                            body = "Try fewer words or another spelling.",
                                            actionLabel = "Search the web",
                                            onAction = { linkOpener.openUrl(searchUrl) },
                                        )
                                    }
                                }

                                if (state.categories.isNotEmpty()) {
                                    stickyHeader(key = "hdr_categories") {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.surface,
                                        ) {
                                            Text(
                                                text = "Categories",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
                                            )
                                        }
                                    }
                                    itemsIndexed(
                                        state.categories,
                                        key = { _, c -> "cat_${c.heading?.id ?: c.section.id}" },
                                    ) { _, cat ->
                                        CategoryRow(
                                            hit = cat,
                                            query = state.query,
                                            onClick = {
                                                haptics.click()
                                                nav.select(Tab.BROWSE)
                                                nav.push(SectionNavKey(cat.section.id, anchor = cat.heading?.anchor))
                                            },
                                            modifier = Modifier.animateItem(),
                                        )
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

                                    itemsIndexed(group.hits, key = { _, hit -> "hit_${hit.entry.id}" }) { index, hit ->
                                        Column(
                                            modifier = Modifier
                                                .animateItem()
                                                .enterStagger(index, state.query)
                                        ) {
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

                                            SwipeToSave(entry = hit.entry) {
                                                EntryRow(
                                                    entry = hit.entry,
                                                    nameHl = hit.nameHl,
                                                    descHl = hit.descriptionHl,
                                                    isNew = hit.entry.id in state.newEntryIds,
                                                    onShowInSection = {
                                                        scope.launch {
                                                            val anchor = try {
                                                                hit.entry.headingId?.let { hid ->
                                                                    container.datasetStore.headings(hit.entry.sectionId).firstOrNull { it.id == hid }?.anchor
                                                                }
                                                            } catch (_: Exception) { null }
                                                            nav.select(Tab.BROWSE)
                                                            nav.push(SectionNavKey(hit.entry.sectionId, anchor = anchor, focusEntryId = hit.entry.id))
                                                        }
                                                    },
                                                    onOpenUrl = { linkOpener.openUrl(it) },
                                                    onShowSnackbar = onShowSnackbar,
                                                )
                                            }

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

/** Plain search words of [query]: operators (`in:`, `is:`, `site:`), excludes and quotes dropped. */
private fun plainQueryWords(query: String): List<String> =
    query.split(Regex("\\s+")).filter { it.isNotBlank() && !it.contains(':') && !it.startsWith("-") && !it.contains('"') }

/** [title] with the first occurrence of each query word wrapped in the highlight markers parseHighlight reads. */
private fun categoryHighlight(title: String, words: List<String>): String {
    val marked = BooleanArray(title.length)
    for (w in words) {
        val i = title.indexOf(w, ignoreCase = true)
        if (i >= 0) for (k in i until i + w.length) marked[k] = true
    }
    return buildString {
        var open = false
        for ((idx, ch) in title.withIndex()) {
            if (marked[idx] && !open) { append('\u0002'); open = true }
            if (!marked[idx] && open) { append('\u0003'); open = false }
            append(ch)
        }
        if (open) append('\u0003')
    }
}

@Composable
private fun CategoryRow(
    hit: CategoryHit,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = hit.heading?.title ?: hit.section.title
    val primary = MaterialTheme.colorScheme.primary
    val words = remember(query) { plainQueryWords(query) }
    val styled = remember(title, words, primary) { parseHighlight(categoryHighlight(title, words), primary) }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = sectionIcon(hit.section.id),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(text = styled, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = if (hit.path.isNotEmpty()) {
            {
                Text(
                    text = hit.path.joinToString(" › "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else null,
        trailingContent = {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}
