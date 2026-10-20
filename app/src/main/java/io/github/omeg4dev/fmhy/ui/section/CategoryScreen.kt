package io.github.omeg4dev.fmhy.ui.section

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import io.github.omeg4dev.fmhy.data.HomePrefs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import io.github.omeg4dev.fmhy.ui.common.CrossfadeContent
import io.github.omeg4dev.fmhy.ui.common.EntryRow
import io.github.omeg4dev.fmhy.ui.common.LocalSnackbarHost
import io.github.omeg4dev.fmhy.ui.common.SkeletonRows
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.enterStagger
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import io.github.omeg4dev.fmhy.ui.common.showUndo
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
    val haptics = rememberHaptics()
    val snackbarHostState = remember { SnackbarHostState() }
    val bottomBarPadding = LocalBottomBarPadding.current
    val bottomBarController = LocalBottomBarController.current
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)

    val listState = rememberLazyListState()
    var showJumpBottomSheet by remember { mutableStateOf(false) }
    var showLegendBottomSheet by remember { mutableStateOf(false) }
    var highlightedHeadingId by remember { mutableStateOf<String?>(null) }
    var highlightedEntryId by remember { mutableStateOf<String?>(null) }
    var pendingSubSelection by remember { mutableStateOf<Int?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    var follows by remember { mutableStateOf<List<io.github.omeg4dev.fmhy.data.user.Follow>>(emptyList()) }
    LaunchedEffect(container.userStore.changes) {
        follows = container.userStore.follows()
    }
    val currentSectionId = state.section?.id ?: key.sectionId
    val currentSectionTitle = state.section?.title ?: "Section"

    val isFilterActive = state.starredOnly || !state.showIndexes

    val isOverview = key.categoryHeadingId == "__overview"
    val homePrefs by container.settingsRepository.homePrefs.collectAsStateWithLifecycle(initialValue = HomePrefs())
    val userChanges by container.userStore.changes.collectAsStateWithLifecycle()
    var isCategoryPinned by remember { mutableStateOf(false) }

    val category = remember(state.structure.categories, key.categoryHeadingId) {
        if (isOverview) null else state.structure.categories.firstOrNull { it.heading.id == key.categoryHeadingId }
    }
    val isCategoryFollowed = remember(follows, currentSectionId, category) {
        category != null && follows.any { it.sectionId == currentSectionId && it.headingId == category.heading.id }
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

    // Scroll to focusEntryId or focusHeadingId on first composition and highlight
    var hasScrolledToFocus by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(displayItems, key.focusHeadingId, key.focusEntryId) {
        if (!hasScrolledToFocus && displayItems.isNotEmpty()) {
            val focusEntryId = key.focusEntryId
            if (!focusEntryId.isNullOrEmpty()) {
                val entryIdx = displayItems.indexOfFirst {
                    when (it) {
                        is SectionItem.EntryItem -> it.entry.id == focusEntryId || it.alternatives.any { a -> a.id == focusEntryId }
                        is SectionItem.MoreGroup -> it.entries.any { e -> e.id == focusEntryId }
                        else -> false
                    }
                }
                if (entryIdx != -1) {
                    hasScrolledToFocus = true
                    listState.scrollToItem(entryIdx)
                    highlightedEntryId = focusEntryId
                    delay(1500)
                    highlightedEntryId = null
                    return@LaunchedEffect
                }
            }

            val focusId = key.focusHeadingId
            if (!focusId.isNullOrEmpty()) {
                val targetIdx = displayItems.indexOfFirst {
                    it is SectionItem.HeadingItem &&
                        (it.heading.id == focusId || it.heading.anchor.equals(focusId, ignoreCase = true) || it.heading.id.endsWith(focusId))
                }
                if (targetIdx != -1) {
                    hasScrolledToFocus = true
                    listState.scrollToItem(targetIdx)
                    val matchedHeading = (displayItems[targetIdx] as SectionItem.HeadingItem).heading
                    highlightedHeadingId = matchedHeading.id
                    delay(1200)
                    highlightedHeadingId = null
                }
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
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomBarPadding),
            )
        },
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
                        val canFollowCat = !isOverview && category != null
                        if (canFollowCat || homePrefs.personalHome) {
                            val catHeadingId = if (isOverview) "__overview" else (category?.heading?.id ?: key.categoryHeadingId)
                            LaunchedEffect(userChanges, key.sectionId, catHeadingId) {
                                val pins = container.userStore.pins()
                                isCategoryPinned = pins.any { it.sectionId == key.sectionId && it.headingId == catHeadingId }
                            }
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                ) {
                                    if (canFollowCat) {
                                        val fullCatTitle = "$currentSectionTitle › $categoryTitle"
                                        DropdownMenuItem(
                                            text = { Text(if (isCategoryFollowed) "Unfollow this category" else "Follow this category") },
                                            onClick = {
                                                showOverflowMenu = false
                                                scope.launch {
                                                    if (isCategoryFollowed) {
                                                        val snapshot = follows.firstOrNull { it.sectionId == currentSectionId && it.headingId == category.heading.id } ?: io.github.omeg4dev.fmhy.data.user.Follow(currentSectionId, category.heading.id, fullCatTitle, System.currentTimeMillis())
                                                        container.userStore.unfollow(currentSectionId, category.heading.id)
                                                        haptics.click()
                                                        val undone = snackbarHostState.showUndo("Unfollowed $fullCatTitle")
                                                        if (undone) {
                                                            container.userStore.restoreFollow(snapshot)
                                                        }
                                                    } else {
                                                        container.userStore.follow(currentSectionId, category.heading.id, fullCatTitle)
                                                        onShowSnackbar("Following $fullCatTitle")
                                                    }
                                                }
                                            },
                                        )
                                    }
                                    if (homePrefs.personalHome) {
                                        if (isCategoryPinned) {
                                            DropdownMenuItem(
                                                text = { Text("Unpin") },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    scope.launch {
                                                        val snapshot = container.userStore.pins().firstOrNull { it.sectionId == key.sectionId && it.headingId == catHeadingId } ?: io.github.omeg4dev.fmhy.data.user.Pin(key.sectionId, catHeadingId, categoryTitle, 0)
                                                        container.userStore.unpin(key.sectionId, catHeadingId)
                                                        haptics.click()
                                                        val undone = snackbarHostState.showUndo("Unpinned $categoryTitle")
                                                        if (undone) {
                                                            container.userStore.restorePin(snapshot)
                                                        }
                                                    }
                                                },
                                            )
                                        } else {
                                            DropdownMenuItem(
                                                text = { Text("Pin to Home") },
                                                onClick = {
                                                    showOverflowMenu = false
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
                        onLongClickChip = { index ->
                            val sub = subHeadings.getOrNull(index) ?: return@CategoryChipRail
                            val subTitle = CrossLinks.splitLinkTitle(sub.title).first
                            val fullTitle = "$currentSectionTitle › $subTitle"
                            val subFollow = follows.firstOrNull { it.sectionId == currentSectionId && it.headingId == sub.id }
                            scope.launch {
                                if (subFollow != null) {
                                    container.userStore.unfollow(currentSectionId, sub.id)
                                    haptics.click()
                                    val undone = snackbarHostState.showUndo("Unfollowed $fullTitle")
                                    if (undone) {
                                        container.userStore.restoreFollow(subFollow)
                                    }
                                } else {
                                    container.userStore.follow(currentSectionId, sub.id, fullTitle)
                                    onShowSnackbar("Following $fullTitle")
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
        CompositionLocalProvider(LocalSnackbarHost provides snackbarHostState) {
            CrossfadeContent(
                targetState = state.isLoading,
                modifier = Modifier.fillMaxSize(),
            ) { loading ->
                if (loading) {
                    SkeletonRows(
                        count = 8,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = innerPadding.calculateTopPadding()),
                    )
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
                            bottom = bottomBarPadding + 16.dp,
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
                            Box(
                                modifier = Modifier
                                    .animateItem()
                                    .enterStagger(index, key.categoryHeadingId)
                            ) {
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
                                        val isEntryHighlighted = item.entry.id == highlightedEntryId || item.alternatives.any { it.id == highlightedEntryId }
                                        val targetBg = if (isEntryHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
                                        val entryBg by animateColorAsState(
                                            targetValue = targetBg,
                                            animationSpec = tween(1500),
                                            label = "category_entry_highlight",
                                        )
                                        Column(modifier = Modifier.background(entryBg)) {
                                            EntryRow(
                                                entry = item.entry,
                                                alternatives = item.alternatives,
                                                isNew = item.entry.id in state.newEntryIds,
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

