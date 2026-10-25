package io.github.omeg4dev.fmhy.ui.whatsnew

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import io.github.omeg4dev.fmhy.ui.common.Motion
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.FollowingNavKey
import io.github.omeg4dev.fmhy.SectionNavKey
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.nav.LocalAppNav
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import io.github.omeg4dev.fmhy.ui.common.CrossfadeContent
import io.github.omeg4dev.fmhy.ui.common.EmptyState
import io.github.omeg4dev.fmhy.ui.common.EntryDetailBottomSheet
import io.github.omeg4dev.fmhy.ui.common.LocalPasteDecoder
import io.github.omeg4dev.fmhy.ui.common.PasteAction
import io.github.omeg4dev.fmhy.ui.common.linkText
import io.github.omeg4dev.fmhy.ui.common.SkeletonRows
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.enterStagger
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun WhatsNewScreen(
    viewModel: WhatsNewViewModel,
    container: AppContainer,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit = {},
    /** Open on the hub (All changes + one tile per kind) instead of the feed. */
    startInHub: Boolean = false,
    onNavigateToFollowing: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val nav = LocalAppNav.current
    val actualOnNavigateToFollowing = onNavigateToFollowing ?: { nav.push(FollowingNavKey) }
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)
    val pasteDecoder = LocalPasteDecoder.current

    // Hub -> feed is internal state so the system back from the feed lands on the hub again.
    var inFeed by rememberSaveable { mutableStateOf(!startInHub) }
    var searchText by rememberSaveable { mutableStateOf("") }
    val showFeed = inFeed || searchText.isNotBlank()
    fun backToHub() {
        inFeed = false
        searchText = ""
        viewModel.setQuery("")
        viewModel.setScope(WhatsNewScope.All)
    }
    BackHandler(enabled = startInHub && showFeed) { backToHub() }

    var showLegendSheet by remember { mutableStateOf(false) }
    var showSectionsSheet by remember { mutableStateOf(false) }
    var selectedRemovedCard by remember { mutableStateOf<ChangeCard?>(null) }
    var selectedDetailEntry by remember { mutableStateOf<Entry?>(null) }
    var detailHistory by remember { mutableStateOf<List<ChangeItem>?>(null) }

    val sectionTitles = remember(state.availableSections) {
        state.availableSections.associate { it.id to it.title }
    }

    LaunchedEffect(selectedDetailEntry?.id) {
        val entry = selectedDetailEntry
        detailHistory = if (entry != null) {
            viewModel.getEntryHistory(entry.id)
        } else null
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
                            text = "From fmhy/edit · ${state.period.label.lowercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (startInHub && showFeed) backToHub() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            showLegendSheet = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "What do the colours mean?",
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.refresh()
                        }
                    ) {
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
            // Hide the feed's filters after 48 dp of scrolling down, show them again after 32 dp up.
            var filtersVisible by rememberSaveable { mutableStateOf(true) }
            val density = LocalDensity.current
            val filterBarScroll = remember(density) {
                val hidePx = with(density) { 48.dp.toPx() }
                val showPx = with(density) { 32.dp.toPx() }
                object : NestedScrollConnection {
                    var acc = 0f
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        val dy = available.y
                        if (dy == 0f) return Offset.Zero
                        if ((acc < 0f) != (dy < 0f)) acc = 0f
                        acc += dy
                        if (acc <= -hidePx && filtersVisible) {
                            filtersVisible = false
                            acc = 0f
                        } else if (acc >= showPx && !filtersVisible) {
                            filtersVisible = true
                            acc = 0f
                        }
                        return Offset.Zero
                    }
                }
            }
            // One search field for hub and feed, so typing keeps its focus when the hub turns into the feed.
            Column(modifier = Modifier.fillMaxSize()) {
            WhatsNewSearchField(
                text = searchText,
                onChange = {
                    // A search from the hub looks through every kind, including edits.
                    if (!showFeed && it.isNotBlank()) viewModel.setTypes(emptySet())
                    searchText = it
                    viewModel.setQuery(it)
                },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (!showFeed) {
                WhatsNewHub(
                    state = state,
                    onSetPeriod = {
                        haptics.tick()
                        viewModel.setPeriod(it)
                    },
                    onOpenAll = {
                        viewModel.setTypes(emptySet())
                        inFeed = true
                    },
                    onOpenKind = {
                        viewModel.setTypes(setOf(it))
                        inFeed = true
                    },
                    onOpenScope = {
                        viewModel.setTypes(WhatsNewGrouping.DEFAULT_TYPES)
                        viewModel.setScope(it)
                        inFeed = true
                    },
                )
            } else
            Column(modifier = Modifier.fillMaxSize().nestedScroll(filterBarScroll)) {
                // Filters + summary slide away while scrolling down the feed; the search field stays.
                AnimatedVisibility(
                    visible = filtersVisible || state.dayGroups.isEmpty(),
                    enter = expandVertically(Motion.enter()) + fadeIn(Motion.enter()),
                    exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit()),
                ) {
                Column {
                // Filter dropdown pills row
                FilterBar(
                    state = state,
                    sectionTitles = sectionTitles,
                    onSetPeriod = {
                        haptics.tick()
                        viewModel.setPeriod(it)
                    },
                    onToggleType = {
                        haptics.tick()
                        viewModel.toggleType(it)
                    },
                    onClearTypes = {
                        haptics.remove()
                        viewModel.clearTypes()
                    },
                    onSetTypes = {
                        haptics.tick()
                        viewModel.setTypes(it)
                    },
                    onSetScope = {
                        haptics.tick()
                        viewModel.setScope(it)
                    },
                    onOpenSectionsSheet = {
                        showSectionsSheet = true
                    },
                    onManageFollowing = actualOnNavigateToFollowing,
                )

                // Summary line
                SummaryLine(
                    counts = state.counts,
                    selectedTypes = state.types,
                    onSelectKind = { kind ->
                        haptics.tick()
                        if (state.types == setOf(kind)) {
                            viewModel.setTypes(WhatsNewGrouping.DEFAULT_TYPES)
                        } else {
                            viewModel.setTypes(setOf(kind))
                        }
                    },
                )
                }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // Content area
                CrossfadeContent(
                    targetState = state.isLoading to state.dayGroups.isEmpty(),
                ) { (loading, empty) ->
                    when {
                        loading -> {
                            SkeletonRows(count = 6)
                        }
                        empty -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.scope is WhatsNewScope.Following && !state.hasFollows) {
                                    EmptyState(
                                        icon = Icons.Outlined.HistoryToggleOff,
                                        title = "No followed sections",
                                        body = "Follow sections or subsections to see their updates here.",
                                        actionLabel = "Follow sections",
                                        onAction = actualOnNavigateToFollowing,
                                    )
                                } else {
                                    EmptyState(
                                        icon = Icons.Outlined.HistoryToggleOff,
                                        title = "Nothing here for these filters",
                                        body = "Try a longer period or all types.",
                                        actionLabel = "Show everything",
                                        onAction = {
                                            viewModel.resetFilters()
                                        },
                                    )
                                }
                            }
                        }
                        else -> {
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
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            )
                                        }
                                    }

                                    itemsIndexed(dayGroup.cards, key = { _, card -> card.key }) { index, card ->
                                        val staggerModifier = if (index < 8) Modifier.enterStagger(index) else Modifier
                                        ChangeCardRow(
                                            card = card,
                                            sectionTitles = sectionTitles,
                                            onClick = {
                                                if (card.primaryKind == ChangeKind.REMOVED || card.entry == null) {
                                                    selectedRemovedCard = card
                                                } else {
                                                    selectedDetailEntry = card.entry
                                                }
                                            },
                                            onLongClick = {
                                                haptics.longPress()
                                                linkOpener.openUrl(card.url)
                                            },
                                            modifier = Modifier
                                                .animateItem()
                                                .then(staggerModifier),
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
            }
            }
        }
    }

    if (showLegendSheet) {
        LegendBottomSheet(onDismiss = { showLegendSheet = false })
    }

    if (showSectionsSheet) {
        val selectedSectionIds = when (val s = state.scope) {
            is WhatsNewScope.Sections -> s.sections
            else -> emptySet()
        }
        val countsBySection = remember(state.dayGroups) {
            state.dayGroups.flatMap { it.cards }
                .groupingBy { it.sectionId }
                .eachCount()
        }
        ChooseSectionsBottomSheet(
            availableSections = state.availableSections,
            selectedSections = selectedSectionIds,
            countsBySection = countsBySection,
            onToggleSection = { sectionId ->
                haptics.toggle(sectionId !in selectedSectionIds)
                viewModel.toggleSectionScope(sectionId)
            },
            onDismiss = { showSectionsSheet = false },
        )
    }

    selectedRemovedCard?.let { card ->
        ReplacementsBottomSheet(
            card = card,
            viewModel = viewModel,
            onOpenUrl = { linkOpener.openUrl(it) },
            onShowSnackbar = onShowSnackbar,
            onDismiss = { selectedRemovedCard = null },
        )
    }

    selectedDetailEntry?.let { entry ->
        EntryDetailBottomSheet(
            entry = entry,
            history = detailHistory,
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
            onDismiss = {
                selectedDetailEntry = null
                detailHistory = null
            },
            onOpenUrl = { linkOpener.openUrl(it) },
            onCopyLink = { url ->
                selectedDetailEntry = null
                pasteDecoder.linkText(url, PasteAction.COPY) { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", text))
                    onShowSnackbar(if ('\n' in text) "Links copied to clipboard" else "Link copied to clipboard")
                }
            },
            onShareLink = { url ->
                selectedDetailEntry = null
                pasteDecoder.linkText(url, PasteAction.SHARE) { text ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share ${entry.name}"))
                }
            },
            onShowSnackbar = onShowSnackbar,
        )
    }

    UnsafeWarningDialog(linkOpener)
}
