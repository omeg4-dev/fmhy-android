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
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Tune as TuneIcon
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.omeg4dev.fmhy.WhatsNewNavKey
import io.github.omeg4dev.fmhy.data.search.QueryAssist
import io.github.omeg4dev.fmhy.data.search.QueryEdit
import io.github.omeg4dev.fmhy.data.search.QuerySuggestion
import io.github.omeg4dev.fmhy.data.search.SuggestionKind
import io.github.omeg4dev.fmhy.data.search.SearchHit
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import io.github.omeg4dev.fmhy.ui.common.sectionIcon
import io.github.omeg4dev.fmhy.ui.check.VerdictCard
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Categories shown before "View all". */
private const val CATEGORY_PREVIEW = 3

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
    var categoriesExpanded by rememberSaveable(state.query) { mutableStateOf(false) }

    val linkOpener = rememberLinkOpener(container, onShowSnackbar)

    val browserPrefs by container.settingsRepository.browserPrefs.collectAsStateWithLifecycle(initialValue = BrowserPrefs())
    val liveQuery = viewModel.queryText
    val encodedQuery = remember(liveQuery) {
        val plain = QueryEdit.split(liveQuery).free.trim()
        try {
            java.net.URLEncoder.encode(plain, "UTF-8")
        } catch (_: Exception) {
            plain
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
            "stremio" to "Just a name: matches title, description and link",
            "in:video anime" to "Search inside one section",
            "is:starred adblock" to "Only FMHY's starred picks",
            "site:github.com" to "Only links on one website",
            "\"free movies\"" to "An exact phrase",
            "player -android" to "Leave out results with a word",
        )
    }

    val haptics = rememberHaptics()
    val raw = viewModel.queryText
    val split = remember(raw) { QueryEdit.split(raw) }
    var field by remember { mutableStateOf(fieldValueFor(split.free)) }
    var fieldFocused by remember { mutableStateOf(false) }
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    // The query can change from outside the field (filter sheet, recent search, Try rows, Clear).
    LaunchedEffect(raw) {
        // Read the live query, not the keyed one: keystrokes may already have moved on (stale effect resets the caret/text).
        val current = QueryEdit.split(viewModel.queryText).free
        if (current != field.free) field = fieldValueFor(current)
    }
    LaunchedEffect(field.text, field.selection) {
        viewModel.onCaretChange(field.free, field.freeCursor)
    }

    fun onFieldChange(new: TextFieldValue) {
        val edit = applyFieldEdit(QueryEdit.split(viewModel.queryText).pills, field, new)
        field = edit.value
        if (edit.raw != viewModel.queryText) viewModel.onQueryChange(edit.raw)
    }

    fun pickSuggestion(sug: QuerySuggestion) {
        val partial = QueryAssist.partialAt(field.free, field.freeCursor) ?: return
        val (text, caret) = QueryAssist.apply(field.free, partial, sug)
        onFieldChange(TextFieldValue(SENTINEL + text, TextRange(caret + 1)))
    }

    val activeFilterCount = state.filterCount

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
                SearchBarField(
                    value = field,
                    onValueChange = ::onFieldChange,
                    pills = split.pills,
                    sections = state.availableSections,
                    onRemovePill = { viewModel.removePill(it.text) },
                    onClear = { viewModel.onQueryChange("") },
                    onSearch = {
                        viewModel.onSearchSubmitted(viewModel.queryText)
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    },
                    showBack = showBack,
                    onBack = onBack,
                    onFocusChange = { fieldFocused = it },
                    focusRequester = focusRequester,
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

                FilterChip(
                    selected = state.exact,
                    onClick = {
                        haptics.toggle(!state.exact)
                        viewModel.setExact(!state.exact)
                    },
                    leadingIcon = {
                        Icon(
                            if (state.exact) Icons.Outlined.FormatQuote else Icons.Outlined.AutoFixHigh,
                            contentDescription = null,
                        )
                    },
                    label = { Text(if (state.exact) "Exact" else "Fuzzy") },
                    modifier = Modifier.semantics {
                        contentDescription = if (state.exact) "Exact match, tap for fuzzy" else "Fuzzy match, tap for exact"
                    },
                )
            }

            HorizontalDivider(thickness = 0.5.dp)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            CrossfadeContent(
                targetState = contentMode,
                modifier = Modifier.fillMaxSize(),
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

                            items(trySuggestions, key = { "try_${it.first}" }) { (example, meaning) ->
                                TryRow(
                                    example = example,
                                    meaning = meaning,
                                    onClick = { viewModel.setQuery(example) },
                                )
                            }

                            item(key = "whats_new") {
                                WhatsNewCard(
                                    count = state.whatsNewWeekCount,
                                    onClick = {
                                        nav.select(Tab.BROWSE)
                                        nav.push(WhatsNewNavKey())
                                    },
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp + bottomBarPadding),
                                )
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
                                    title = "No sites match “${state.query.trim()}”",
                                    body = "Try fewer words or another spelling.",
                                    actionLabel = "Search the web",
                                    onAction = { linkOpener.openUrl(searchUrl) },
                                )
                                state.suggestion?.let { suggestion ->
                                    TextButton(onClick = {
                                        viewModel.setQuery(suggestion)
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
                                            title = "No sites match “${state.query.trim()}”",
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
                                    val shownCategories = if (categoriesExpanded) state.categories else state.categories.take(CATEGORY_PREVIEW)
                                    itemsIndexed(
                                        shownCategories,
                                        key = { _, c -> "cat_${c.heading?.id ?: c.section.id}" },
                                    ) { _, cat ->
                                        CategoryRow(
                                            hit = cat,
                                            query = state.query,
                                            onClick = {
                                                nav.select(Tab.BROWSE)
                                                nav.push(SectionNavKey(cat.section.id, anchor = cat.heading?.anchor))
                                            },
                                            modifier = Modifier.animateItem(),
                                        )
                                    }
                                    if (state.categories.size > CATEGORY_PREVIEW) {
                                        item(key = "cat_toggle") {
                                            val n = state.categories.size
                                            TextButton(
                                                onClick = { categoriesExpanded = !categoriesExpanded },
                                                modifier = Modifier.padding(start = 8.dp).animateItem(),
                                            ) {
                                                Icon(
                                                    if (categoriesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    if (categoriesExpanded) "Show fewer categories"
                                                    else "View all ${if (n >= 50) "50+" else n.toString()} categories",
                                                )
                                            }
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

                                            MatchLine(hit)

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
            SuggestionPanel(
                suggestions = if (fieldFocused) suggestions else emptyList(),
                onPick = { pickSuggestion(it) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
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
    val words = remember(query) { plainQueryWords(query) }
    val hlColor = MaterialTheme.colorScheme.onPrimaryContainer
    val hlBackground = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    val styled = remember(title, words, hlColor, hlBackground) { parseHighlight(categoryHighlight(title, words), hlColor, hlBackground) }
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

/** One example of the search syntax in the RECENT mode: the query, and what it does. */
@Composable
private fun TryRow(example: String, meaning: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = example,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = meaning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WhatsNewCard(count: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                Icons.Outlined.NewReleases,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("What's new on FMHY", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when (count) {
                        null -> "See what was added, starred or moved"
                        0 -> "No changes this week"
                        1 -> "1 change this week"
                        else -> "${"%,d".format(count)} changes this week"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Dropdown under the search bar with what can complete the `in:` / `is:` / `site:` being typed. */
@Composable
private fun SuggestionPanel(
    suggestions: List<QuerySuggestion>,
    onPick: (QuerySuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the last non-empty list around while the panel animates out.
    var shown by remember { mutableStateOf(suggestions) }
    if (suggestions.isNotEmpty()) shown = suggestions
    AnimatedVisibility(
        visible = suggestions.isNotEmpty(),
        enter = fadeIn(Motion.enter(Motion.SHORT)) + expandVertically(Motion.enter(Motion.MEDIUM)),
        exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 312.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                shown.forEach { sug ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(suggestionIcon(sug), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = {
                            Text(
                                text = sug.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = if (sug.kind == SuggestionKind.OPERATOR || sug.kind == SuggestionKind.SITE)
                                    MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                                else MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = sug.detail?.let { d ->
                            { Text(d, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        },
                        modifier = Modifier.clickable { onPick(sug) },
                    )
                }
            }
        }
    }
}

private fun suggestionIcon(s: QuerySuggestion) = when (s.kind) {
    SuggestionKind.SECTION -> sectionIcon(s.sectionId ?: "")
    SuggestionKind.SITE -> Icons.Outlined.Language
    SuggestionKind.OPERATOR -> Icons.Outlined.TuneIcon
    SuggestionKind.IS -> when {
        s.insert.endsWith("starred") -> Icons.Filled.Star
        s.insert.endsWith("top") -> Icons.Outlined.WorkspacePremium
        else -> Icons.Outlined.VisibilityOff
    }
}

/** "in link: github.com/..." under a result whose match is in a column the row does not show. */
@Composable
private fun MatchLine(hit: SearchHit) {
    val label = hit.extraLabel ?: return
    val hl = hit.extraHl ?: return
    val hlColor = MaterialTheme.colorScheme.onPrimaryContainer
    val hlBackground = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val text = remember(label, hl, hlColor, hlBackground, muted) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Medium)) { append("in $label: ") }
            append(parseHighlight(hl, hlColor, hlBackground))
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}
