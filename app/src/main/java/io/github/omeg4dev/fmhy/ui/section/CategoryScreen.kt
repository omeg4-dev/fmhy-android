package io.github.omeg4dev.fmhy.ui.section

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import io.github.omeg4dev.fmhy.data.HomePrefs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.CategoryNavKey
import io.github.omeg4dev.fmhy.LegendNavKey
import io.github.omeg4dev.fmhy.LocalBottomBarController
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.nav.LocalAppNav
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import io.github.omeg4dev.fmhy.ui.common.EntryRow
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    key: CategoryNavKey,
    viewModel: SectionViewModel,
    container: AppContainer,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val nav = LocalAppNav.current
    val bottomBarController = LocalBottomBarController.current
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)

    val listState = rememberLazyListState()
    var showJumpBottomSheet by remember { mutableStateOf(false) }
    var showLegendBottomSheet by remember { mutableStateOf(false) }
    var highlightedHeadingId by remember { mutableStateOf<String?>(null) }
    var pendingSubSelection by remember { mutableStateOf<Int?>(null) }

    val isFilterActive = state.starredOnly || !state.showIndexes

    val isOverview = key.categoryHeadingId == "__overview"
    val homePrefs by container.settingsRepository.homePrefs.collectAsStateWithLifecycle(initialValue = HomePrefs())
    val userChanges by container.userStore.changes.collectAsStateWithLifecycle()
    var showCategoryMenu by remember { mutableStateOf(false) }
    var isCategoryPinned by remember { mutableStateOf(false) }

    val category = remember(state.structure.categories, key.categoryHeadingId) {
        if (isOverview) null else state.structure.categories.firstOrNull { it.heading.id == key.categoryHeadingId }
    }

    val overviewEntryCount = remember(state.structure.overview) {
        state.structure.overview.sumOf {
            when (it) {
                is SectionItem.EntryItem -> 1
                is SectionItem.MoreGroup -> it.entries.size
                else -> 0
            }
        }
    }

    val displayItems = remember(state.structure, isOverview, category, overviewEntryCount) {
        if (isOverview) {
            if (overviewEntryCount > 0) {
                val overviewHeading = Heading(
                    id = "__overview",
                    sectionId = state.section?.id ?: key.sectionId,
                    parentId = null,
                    level = 1,
                    title = "Overview",
                    anchor = "overview",
                    ord = -1,
                )
                listOf(SectionItem.HeadingItem(overviewHeading)) + state.structure.overview
            } else {
                state.structure.overview
            }
        } else if (category != null) {
            listOf(SectionItem.HeadingItem(category.heading)) + category.items
        } else {
            emptyList()
        }
    }

    val categoryTitle = remember(isOverview, category, key.sectionId) {
        when {
            isOverview -> "Overview"
            category != null -> {
                val title = CrossLinks.splitLinkTitle(category.heading.title).first
                val flag = flagFor(key.sectionId, category.heading.title)
                if (flag != null) "$flag $title" else title
            }
            else -> "Category"
        }
    }

    val subHeadings = remember(isOverview, category, displayItems) {
        if (isOverview) {
            displayItems.filterIsInstance<SectionItem.HeadingItem>().filter { it.heading.id != "__overview" }.map { it.heading }
        } else {
            category?.subHeadings ?: emptyList()
        }
    }

    // Scroll to focusHeadingId on first composition and highlight
    LaunchedEffect(displayItems, key.focusHeadingId) {
        val focusId = key.focusHeadingId
        if (!focusId.isNullOrEmpty() && displayItems.isNotEmpty()) {
            val targetIdx = displayItems.indexOfFirst {
                it is SectionItem.HeadingItem &&
                    (it.heading.id == focusId || it.heading.anchor.equals(focusId, ignoreCase = true) || it.heading.id.endsWith(focusId))
            }
            if (targetIdx != -1) {
                listState.scrollToItem(targetIdx)
                val matchedHeading = (displayItems[targetIdx] as SectionItem.HeadingItem).heading
                highlightedHeadingId = matchedHeading.id
                delay(1200)
                highlightedHeadingId = null
            }
        }
    }

    val derivedSubHeadingIndex by remember(displayItems, subHeadings) {
        derivedStateOf {
            val firstIdx = listState.firstVisibleItemIndex
            var activeSubIdx = -1
            for (i in 0..firstIdx.coerceAtMost(displayItems.lastIndex)) {
                val item = displayItems.getOrNull(i)
                if (item is SectionItem.HeadingItem) {
                    val subIdx = subHeadings.indexOfFirst { it.id == item.heading.id }
                    if (subIdx != -1) {
                        activeSubIdx = subIdx
                    }
                }
            }
            activeSubIdx
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = categoryTitle,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val sectionTitle = state.section?.title
                            if (!sectionTitle.isNullOrEmpty()) {
                                Text(
                                    text = sectionTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showLegendBottomSheet = true }) {
                            BadgedBox(
                                badge = {
                                    if (isFilterActive) {
                                        Badge()
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.FilterList, contentDescription = "Legend and options")
                            }
                        }
                        IconButton(onClick = { showJumpBottomSheet = true }) {
                            Icon(Icons.AutoMirrored.Outlined.List, contentDescription = "Outline")
                        }
                        if (homePrefs.personalHome) {
                            val catHeadingId = if (isOverview) "__overview" else (category?.heading?.id ?: key.categoryHeadingId)
                            LaunchedEffect(userChanges, key.sectionId, catHeadingId) {
                                val pins = container.userStore.pins()
                                isCategoryPinned = pins.any { it.sectionId == key.sectionId && it.headingId == catHeadingId }
                            }
                            Box {
                                IconButton(onClick = { showCategoryMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(
                                    expanded = showCategoryMenu,
                                    onDismissRequest = { showCategoryMenu = false },
                                ) {
                                    if (isCategoryPinned) {
                                        DropdownMenuItem(
                                            text = { Text("Unpin") },
                                            onClick = {
                                                showCategoryMenu = false
                                                scope.launch {
                                                    container.userStore.unpin(key.sectionId, catHeadingId)
                                                    onShowSnackbar("Unpinned from Home")
                                                }
                                            },
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text("Pin to Home") },
                                            onClick = {
                                                showCategoryMenu = false
                                                scope.launch {
                                                    container.userStore.pin(key.sectionId, catHeadingId, categoryTitle)
                                                    onShowSnackbar("Pinned to Home")
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )

                if (subHeadings.size >= 3) {
                    val chips = remember(subHeadings) {
                        subHeadings.map { CrossLinks.splitLinkTitle(it.title).first }
                    }
                    val isAtTop by remember {
                        derivedStateOf {
                            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                        }
                    }
                    val dividerAlpha by animateFloatAsState(
                        targetValue = if (isAtTop) 0f else 1f,
                        label = "sub_rail_divider_alpha",
                    )

                    CategoryChipRail(
                        chips = chips,
                        selectedIndex = pendingSubSelection ?: derivedSubHeadingIndex,
                        onSelectChip = { index ->
                            bottomBarController.show()
                            val sub = subHeadings.getOrNull(index) ?: return@CategoryChipRail
                            val targetIdx = displayItems.indexOfFirst {
                                it is SectionItem.HeadingItem && it.heading.id == sub.id
                            }
                            if (targetIdx != -1) {
                                scope.launch {
                                    pendingSubSelection = index
                                    listState.animateScrollToItem(targetIdx)
                                    pendingSubSelection = null
                                }
                            }
                        },
                    )

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = dividerAlpha),
                    )
                }
            }
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (displayItems.isEmpty() && isFilterActive) {
            FilterEmptyState(
                onShowAll = {
                    viewModel.setStarredOnly(false)
                    viewModel.setShowIndexes(true)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = LocalBottomBarPadding.current + 16.dp,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                itemsIndexed(
                    displayItems,
                    key = { _, item ->
                        when (item) {
                            is SectionItem.HeadingItem -> "h_${item.heading.id}"
                            is SectionItem.EntryItem -> "e_${item.entry.id}"
                            is SectionItem.NoteItem -> "n_${item.note.id}"
                            is SectionItem.MoreGroup -> "mg_${item.entries.first().id}"
                        }
                    }
                ) { index, item ->
                    Box(modifier = Modifier.animateItem()) {
                        when (item) {
                            is SectionItem.HeadingItem -> {
                                val entryCount = remember(category, overviewEntryCount, isOverview, item.heading.id) {
                                    when {
                                        !isOverview && category != null && item.heading.id == category.heading.id -> category.entryCount
                                        isOverview && item.heading.id == "__overview" -> overviewEntryCount
                                        else -> null
                                    }
                                }
                                HeadingHeader(
                                    heading = item.heading,
                                    isHighlighted = item.heading.id == highlightedHeadingId,
                                    isFirstItem = index == 0,
                                    entryCount = entryCount,
                                    onOpenUrl = { linkOpener.openUrl(it) },
                                    sectionId = key.sectionId,
                                )
                            }
                            is SectionItem.EntryItem -> {
                                Column {
                                    EntryRow(
                                        entry = item.entry,
                                        alternatives = item.alternatives,
                                        onOpenUrl = { linkOpener.openUrl(it) },
                                        onShowSnackbar = onShowSnackbar,
                                    )
                                    val nextItem = displayItems.getOrNull(index + 1)
                                    if (nextItem is SectionItem.EntryItem) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 16.dp),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                }
                            }
                            is SectionItem.NoteItem -> {
                                NoteCallout(note = item.note)
                            }
                            is SectionItem.MoreGroup -> {
                                MoreGroupRow(
                                    entries = item.entries,
                                    onOpenUrl = { linkOpener.openUrl(it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showJumpBottomSheet) {
        JumpToHeadingBottomSheet(
            categories = state.structure.categories,
            currentCategoryId = category?.heading?.id,
            onDismiss = { showJumpBottomSheet = false },
            onReturnToTop = {
                showJumpBottomSheet = false
                bottomBarController.show()
                onBack()
            },
            onSelectHeading = { heading ->
                showJumpBottomSheet = false
                bottomBarController.show()
                val targetCategoryHeadingId = findCategoryHeadingId(state.structure, heading.id)
                if (targetCategoryHeadingId == key.categoryHeadingId) {
                    val targetIdx = displayItems.indexOfFirst {
                        it is SectionItem.HeadingItem && it.heading.id == heading.id
                    }
                    if (targetIdx != -1) {
                        scope.launch {
                            listState.animateScrollToItem(targetIdx)
                        }
                    }
                } else if (targetCategoryHeadingId != null) {
                    nav.push(
                        CategoryNavKey(
                            sectionId = state.section?.id ?: key.sectionId,
                            categoryHeadingId = targetCategoryHeadingId,
                            focusHeadingId = heading.id,
                        )
                    )
                }
            },
            headings = state.headings,
            sectionId = key.sectionId,
        )
    }

    if (showLegendBottomSheet) {
        LegendBottomSheet(
            starredOnly = state.starredOnly,
            showIndexes = state.showIndexes,
            onToggleStarredOnly = { viewModel.setStarredOnly(it) },
            onToggleShowIndexes = { viewModel.setShowIndexes(it) },
            onOpenFullLegend = {
                showLegendBottomSheet = false
                nav.push(LegendNavKey)
            },
            onDismiss = { showLegendBottomSheet = false },
        )
    }

    UnsafeWarningDialog(linkOpener)
}

