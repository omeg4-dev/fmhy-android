package io.github.omeg4dev.fmhy.ui.section

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import io.github.omeg4dev.fmhy.SectionNavKey
import io.github.omeg4dev.fmhy.data.SectionLayout
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
fun SectionScreen(
    viewModel: SectionViewModel,
    key: SectionNavKey? = null,
    container: AppContainer,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val nav = LocalAppNav.current
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)

    val listState = rememberLazyListState()
    val bottomBarController = LocalBottomBarController.current
    var showJumpBottomSheet by remember { mutableStateOf(false) }
    var showLegendBottomSheet by remember { mutableStateOf(false) }
    var highlightedHeadingId by remember { mutableStateOf<String?>(null) }
    var highlightedEntryId by remember { mutableStateOf<String?>(null) }
    var pendingSelection by remember { mutableStateOf<Int?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    var follows by remember { mutableStateOf<List<io.github.omeg4dev.fmhy.data.user.Follow>>(emptyList()) }
    LaunchedEffect(container.userStore.changes) {
        follows = container.userStore.follows()
    }
    val currentSectionId = state.section?.id ?: viewModel.sectionId
    val currentSectionTitle = state.section?.title ?: "Section"
    val isSectionFollowed = remember(follows, currentSectionId) {
        follows.any { it.sectionId == currentSectionId && it.headingId == "" }
    }

    val isFilterActive = state.starredOnly || !state.showIndexes

    // In CATEGORIES mode: handle anchor or focusEntryId navigation by opening CategoryNavKey
    var hasPushedAnchorByCategory by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.items, state.headings, state.layout, viewModel.anchor, key?.focusEntryId) {
        val anchor = viewModel.anchor
        val focusEntryId = key?.focusEntryId
        if (state.layout == SectionLayout.CATEGORIES && !hasPushedAnchorByCategory && (!anchor.isNullOrEmpty() || !focusEntryId.isNullOrEmpty()) && state.items.isNotEmpty()) {
            val anchorHeading = state.headings.firstOrNull {
                it.anchor.equals(anchor, ignoreCase = true) || it.id.endsWith(anchor.orEmpty()) || it.id == anchor
            }
            val targetHeadingId = anchorHeading?.id ?: if (!focusEntryId.isNullOrEmpty()) {
                val entry = state.items.filterIsInstance<SectionItem.EntryItem>().firstOrNull { it.entry.id == focusEntryId || it.alternatives.any { a -> a.id == focusEntryId } }?.entry
                entry?.headingId
            } else null

            if (targetHeadingId != null) {
                val categoryHeadingId = findCategoryHeadingId(state.structure, targetHeadingId)
                if (categoryHeadingId != null) {
                    hasPushedAnchorByCategory = true
                    nav.push(
                        CategoryNavKey(
                            sectionId = state.section?.id ?: viewModel.sectionId,
                            categoryHeadingId = categoryHeadingId,
                            focusHeadingId = anchorHeading?.id ?: targetHeadingId,
                            focusEntryId = focusEntryId,
                        )
                    )
                }
            }
        }
    }

    // In CONTINUOUS mode: handle initial scroll to focusEntryId or anchor
    var hasScrolledToFocus by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.items, state.layout, viewModel.anchor, key?.focusEntryId) {
        if (state.layout == SectionLayout.CONTINUOUS && !hasScrolledToFocus && state.items.isNotEmpty()) {
            val focusEntryId = key?.focusEntryId
            val anchor = viewModel.anchor

            if (!focusEntryId.isNullOrEmpty()) {
                val entryIdx = state.items.indexOfFirst {
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
                } else if (!anchor.isNullOrEmpty()) {
                    // Fallback to heading when entry is filtered out
                    val headingIdx = state.items.indexOfFirst {
                        it is SectionItem.HeadingItem &&
                            (it.heading.anchor.equals(anchor, ignoreCase = true) || it.heading.id.endsWith(anchor) || it.heading.id == anchor)
                    }
                    if (headingIdx != -1) {
                        hasScrolledToFocus = true
                        listState.scrollToItem(headingIdx)
                        val matchedHeading = (state.items[headingIdx] as SectionItem.HeadingItem).heading
                        highlightedHeadingId = matchedHeading.id
                        delay(1200)
                        highlightedHeadingId = null
                    }
                }
            } else if (!anchor.isNullOrEmpty()) {
                val targetIdx = state.items.indexOfFirst {
                    it is SectionItem.HeadingItem &&
                        (it.heading.anchor.equals(anchor, ignoreCase = true) || it.heading.id.endsWith(anchor) || it.heading.id == anchor)
                }
                if (targetIdx != -1) {
                    hasScrolledToFocus = true
                    listState.scrollToItem(targetIdx)
                    val matchedHeading = (state.items[targetIdx] as SectionItem.HeadingItem).heading
                    highlightedHeadingId = matchedHeading.id
                    delay(1200)
                    highlightedHeadingId = null
                }
            }
        }
    }

    val derivedSelectedCategoryIndex by remember {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex
            val map = state.structure.categoryIndexOfItem
            if (map.isNotEmpty() && idx in map.indices) {
                map[idx]
            } else {
                -1
            }
        }
    }

    val currentCategoryHeadingId = remember(state.structure.categories, pendingSelection, derivedSelectedCategoryIndex) {
        val sel = pendingSelection ?: derivedSelectedCategoryIndex
        if (sel in state.structure.categories.indices) {
            state.structure.categories[sel].heading.id
        } else {
            null
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
                        Text(
                            text = state.section?.title ?: "Section",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                        if (state.layout == SectionLayout.CONTINUOUS) {
                            IconButton(onClick = { showJumpBottomSheet = true }) {
                                Icon(Icons.AutoMirrored.Outlined.List, contentDescription = "Outline")
                            }
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isSectionFollowed) "Unfollow this section" else "Follow this section") },
                                    onClick = {
                                        showOverflowMenu = false
                                        scope.launch {
                                            if (isSectionFollowed) {
                                                container.userStore.unfollow(currentSectionId, "")
                                                onShowSnackbar("Unfollowed $currentSectionTitle · Undo")
                                            } else {
                                                container.userStore.follow(currentSectionId, "", currentSectionTitle)
                                                onShowSnackbar("Following $currentSectionTitle · Undo")
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )

                if (state.layout == SectionLayout.CONTINUOUS && state.structure.categories.size >= 2) {
                    val chips = remember(state.structure.categories, state.section?.id) {
                        val sectionId = state.section?.id ?: viewModel.sectionId
                        state.structure.categories.map {
                            val title = CrossLinks.splitLinkTitle(it.heading.title).first
                            flagFor(sectionId, it.heading.title)?.let { flag -> "$flag $title" } ?: title
                        }
                    }
                    val isAtTop by remember {
                        derivedStateOf {
                            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                        }
                    }
                    val dividerAlpha by animateFloatAsState(
                        targetValue = if (isAtTop) 0f else 1f,
                        label = "rail_divider_alpha",
                    )

                    CategoryChipRail(
                        chips = chips,
                        selectedIndex = pendingSelection ?: derivedSelectedCategoryIndex,
                        onSelectChip = { index ->
                            bottomBarController.show()
                            val category = state.structure.categories.getOrNull(index) ?: return@CategoryChipRail
                            val targetIdx = state.items.indexOfFirst {
                                it is SectionItem.HeadingItem && it.heading.id == category.heading.id
                            }
                            if (targetIdx != -1) {
                                scope.launch {
                                    pendingSelection = index
                                    listState.animateScrollToItem(targetIdx)
                                    pendingSelection = null
                                }
                            }
                        },
                        onLongClickChip = { index ->
                            val category = state.structure.categories.getOrNull(index) ?: return@CategoryChipRail
                            val catTitle = CrossLinks.splitLinkTitle(category.heading.title).first
                            val fullTitle = "$currentSectionTitle › $catTitle"
                            val isCategoryFollowed = follows.any { it.sectionId == currentSectionId && it.headingId == category.heading.id }
                            scope.launch {
                                if (isCategoryFollowed) {
                                    container.userStore.unfollow(currentSectionId, category.heading.id)
                                    onShowSnackbar("Unfollowed $fullTitle · Undo")
                                } else {
                                    container.userStore.follow(currentSectionId, category.heading.id, fullTitle)
                                    onShowSnackbar("Following $fullTitle · Undo")
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
        } else if (state.items.isEmpty() && isFilterActive) {
            FilterEmptyState(
                onShowAll = {
                    viewModel.setStarredOnly(false)
                    viewModel.setShowIndexes(true)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else if (state.layout == SectionLayout.CATEGORIES) {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = LocalBottomBarPadding.current + 16.dp,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                if (state.structure.overview.isNotEmpty()) {
                    item(key = "cat_overview") {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Overview",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    nav.push(
                                        CategoryNavKey(
                                            sectionId = state.section?.id ?: viewModel.sectionId,
                                            categoryHeadingId = "__overview",
                                        )
                                    )
                                },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        )
                    }
                }

                itemsIndexed(
                    state.structure.categories,
                    key = { _, category -> "cat_${category.heading.id}" }
                ) { index, category ->
                    val (splitTitle, _) = remember(category.heading.title) {
                        CrossLinks.splitLinkTitle(category.heading.title)
                    }
                    val flag = remember(category.heading.title, state.section?.id) {
                        flagFor(state.section?.id ?: viewModel.sectionId, category.heading.title)
                    }
                    val titleWithFlag = if (flag != null) "$flag $splitTitle" else splitTitle
                    val supportingText = remember(category) {
                        val subNames = category.subHeadings.take(3).map { CrossLinks.splitLinkTitle(it.title).first }
                        if (subNames.isNotEmpty()) {
                            "${category.entryCount} sites · " + subNames.joinToString(", ")
                        } else {
                            "${category.entryCount} sites"
                        }
                    }

                    ListItem(
                        headlineContent = {
                            Text(
                                text = titleWithFlag,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = supportingText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                nav.push(
                                    CategoryNavKey(
                                        sectionId = state.section?.id ?: viewModel.sectionId,
                                        categoryHeadingId = category.heading.id,
                                    )
                                )
                            },
                    )

                    if (index < state.structure.categories.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
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
                    state.items,
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
                                val entryCount = remember(state.structure.categories, item.heading.id) {
                                    state.structure.categories.firstOrNull { it.heading.id == item.heading.id }?.entryCount
                                }
                                HeadingHeader(
                                    heading = item.heading,
                                    isHighlighted = item.heading.id == highlightedHeadingId,
                                    isFirstItem = index == 0,
                                    entryCount = entryCount,
                                    onOpenUrl = { linkOpener.openUrl(it) },
                                    sectionId = state.section?.id ?: viewModel.sectionId,
                                )
                            }
                            is SectionItem.EntryItem -> {
                                val isEntryHighlighted = item.entry.id == highlightedEntryId || item.alternatives.any { it.id == highlightedEntryId }
                                val targetBg = if (isEntryHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
                                val entryBg by animateColorAsState(
                                    targetValue = targetBg,
                                    animationSpec = tween(1500),
                                    label = "entry_highlight",
                                )
                                Column(modifier = Modifier.background(entryBg)) {
                                    EntryRow(
                                        entry = item.entry,
                                        alternatives = item.alternatives,
                                        isNew = item.entry.id in state.newEntryIds,
                                        onOpenUrl = { linkOpener.openUrl(it) },
                                        onShowSnackbar = onShowSnackbar,
                                    )
                                    val nextItem = state.items.getOrNull(index + 1)
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
            currentCategoryId = currentCategoryHeadingId,
            onDismiss = { showJumpBottomSheet = false },
            onReturnToTop = {
                showJumpBottomSheet = false
                bottomBarController.show()
                scope.launch {
                    listState.animateScrollToItem(0)
                }
            },
            onSelectHeading = { heading ->
                showJumpBottomSheet = false
                bottomBarController.show()
                if (state.layout == SectionLayout.CATEGORIES) {
                    val categoryHeadingId = findCategoryHeadingId(state.structure, heading.id)
                    if (categoryHeadingId != null) {
                        nav.push(
                            CategoryNavKey(
                                sectionId = state.section?.id ?: viewModel.sectionId,
                                categoryHeadingId = categoryHeadingId,
                                focusHeadingId = heading.id,
                            )
                        )
                    }
                } else {
                    val targetIdx = state.items.indexOfFirst {
                        it is SectionItem.HeadingItem && it.heading.id == heading.id
                    }
                    if (targetIdx != -1) {
                        scope.launch {
                            listState.animateScrollToItem(targetIdx)
                        }
                    }
                }
            },
            headings = state.headings,
            sectionId = state.section?.id ?: viewModel.sectionId,
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
