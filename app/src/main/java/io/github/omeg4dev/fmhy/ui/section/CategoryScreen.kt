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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.CategoryNavKey
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.di.AppContainer
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
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)

    val listState = rememberLazyListState()
    var highlightedHeadingId by remember { mutableStateOf<String?>(null) }
    var pendingSubSelection by remember { mutableStateOf<Int?>(null) }

    val isOverview = key.categoryHeadingId == "__overview"
    val category = remember(state.structure.categories, key.categoryHeadingId) {
        if (isOverview) null else state.structure.categories.firstOrNull { it.heading.id == key.categoryHeadingId }
    }

    val items = remember(state.structure, isOverview, category) {
        if (isOverview) {
            state.structure.overview
        } else {
            category?.items ?: emptyList()
        }
    }

    val categoryTitle = remember(isOverview, category) {
        when {
            isOverview -> "Overview"
            category != null -> CrossLinks.splitLinkTitle(category.heading.title).first
            else -> "Category"
        }
    }

    val subHeadings = remember(isOverview, category, items) {
        if (isOverview) {
            items.filterIsInstance<SectionItem.HeadingItem>().map { it.heading }
        } else {
            category?.subHeadings ?: emptyList()
        }
    }

    // Scroll to focusHeadingId on first composition and highlight
    LaunchedEffect(items, key.focusHeadingId) {
        val focusId = key.focusHeadingId
        if (!focusId.isNullOrEmpty() && items.isNotEmpty()) {
            val targetIdx = items.indexOfFirst {
                it is SectionItem.HeadingItem &&
                    (it.heading.id == focusId || it.heading.anchor.equals(focusId, ignoreCase = true) || it.heading.id.endsWith(focusId))
            }
            if (targetIdx != -1) {
                listState.scrollToItem(targetIdx)
                val matchedHeading = (items[targetIdx] as SectionItem.HeadingItem).heading
                highlightedHeadingId = matchedHeading.id
                delay(1200)
                highlightedHeadingId = null
            }
        }
    }

    val derivedSubHeadingIndex by remember(items, subHeadings) {
        derivedStateOf {
            val firstIdx = listState.firstVisibleItemIndex
            var activeSubIdx = -1
            for (i in 0..firstIdx.coerceAtMost(items.lastIndex)) {
                val item = items.getOrNull(i)
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
                            val sub = subHeadings.getOrNull(index) ?: return@CategoryChipRail
                            val targetIdx = items.indexOfFirst {
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
                    items,
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
                                HeadingHeader(
                                    heading = item.heading,
                                    isHighlighted = item.heading.id == highlightedHeadingId,
                                    isFirstItem = index == 0,
                                    entryCount = null,
                                    onOpenUrl = { linkOpener.openUrl(it) },
                                )
                            }
                            is SectionItem.EntryItem -> {
                                Column {
                                    EntryRow(
                                        entry = item.entry,
                                        onOpenUrl = { linkOpener.openUrl(it) },
                                        onShowSnackbar = onShowSnackbar,
                                    )
                                    val nextItem = items.getOrNull(index + 1)
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

    UnsafeWarningDialog(linkOpener)
}
