package io.github.omeg4dev.fmhy.ui.section

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Note
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.ui.common.EntryRow
import io.github.omeg4dev.fmhy.ui.common.LinkOpener
import io.github.omeg4dev.fmhy.ui.common.MarkdownText
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SectionScreen(
    viewModel: SectionViewModel,
    container: AppContainer,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val linkOpener = remember {
        LinkOpener(
            context = context,
            store = container.datasetStore,
            settingsRepository = container.settingsRepository,
            coroutineScope = scope,
            onShowSnackbar = onShowSnackbar,
        )
    }

    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    var showJumpBottomSheet by remember { mutableStateOf(false) }

    // Handle initial scroll to anchor
    LaunchedEffect(state.items, viewModel.anchor) {
        val anchor = viewModel.anchor
        if (!anchor.isNullOrEmpty() && state.items.isNotEmpty()) {
            val targetIdx = state.items.indexOfFirst {
                it is SectionItem.HeadingItem &&
                    (it.heading.anchor.equals(anchor, ignoreCase = true) || it.heading.id.endsWith(anchor))
            }
            if (targetIdx != -1) {
                listState.scrollToItem(targetIdx)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = state.section?.title ?: "Section",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            )
        },
        floatingActionButton = {
            if (state.headings.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showJumpBottomSheet = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Default.FormatListNumbered, contentDescription = "Jump to heading")
                }
            }
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 80.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.items.forEachIndexed { index, item ->
                    when (item) {
                        is SectionItem.HeadingItem -> {
                            val heading = item.heading
                            if (heading.level in 1..2) {
                                stickyHeader(
                                    key = "h_${heading.id}",
                                    contentType = "heading"
                                ) {
                                    HeadingHeader(heading = heading, isSticky = true)
                                }
                            } else {
                                item(
                                    key = "h_${heading.id}",
                                    contentType = "heading"
                                ) {
                                    HeadingHeader(heading = heading, isSticky = false)
                                }
                            }
                        }
                        is SectionItem.EntryItem -> {
                            item(
                                key = "e_${item.entry.id}",
                                contentType = "entry"
                            ) {
                                EntryRow(
                                    entry = item.entry,
                                    onOpenUrl = { linkOpener.openUrl(it) },
                                    onShowSnackbar = onShowSnackbar,
                                )
                            }
                        }
                        is SectionItem.NoteItem -> {
                            item(
                                key = "n_${item.note.id}",
                                contentType = "note"
                            ) {
                                NoteCallout(note = item.note)
                            }
                        }
                        is SectionItem.MoreGroup -> {
                            item(
                                key = "mg_${item.entries.first().id}",
                                contentType = "more"
                            ) {
                                MoreGroupRow(
                                    entries = item.entries,
                                    onOpenUrl = { linkOpener.openUrl(it) }
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
            headings = state.headings,
            onDismiss = { showJumpBottomSheet = false },
            onSelectHeading = { heading ->
                showJumpBottomSheet = false
                val targetIdx = state.items.indexOfFirst {
                    it is SectionItem.HeadingItem && it.heading.id == heading.id
                }
                if (targetIdx != -1) {
                    scope.launch {
                        listState.animateScrollToItem(targetIdx)
                    }
                }
            }
        )
    }

    UnsafeWarningDialog(linkOpener)
}

@Composable
fun HeadingHeader(heading: Heading, isSticky: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isSticky) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        shadowElevation = if (isSticky) 2.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (heading.level <= 2) 16.dp else 12.dp,
                    bottom = 8.dp,
                )
        ) {
            val style = when (heading.level) {
                1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                3 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                else -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = heading.title,
                style = style,
                color = MaterialTheme.colorScheme.primary,
            )
            if (isSticky) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun NoteCallout(note: Note) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            MarkdownText(
                markdown = note.textMd,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreGroupRow(
    entries: List<Entry>,
    onOpenUrl: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Text(
                    text = "More (${entries.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (!expanded) {
                val previewText = remember(entries) {
                    entries.joinToString(" · ") { it.name }
                }
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { expanded = true }
                )
            } else {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    entries.forEach { entry ->
                        InputChip(
                            selected = false,
                            onClick = { onOpenUrl(entry.url) },
                            label = { Text(entry.name, style = MaterialTheme.typography.bodySmall) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpToHeadingBottomSheet(
    headings: List<Heading>,
    onDismiss: () -> Unit,
    onSelectHeading: (Heading) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Jump to Heading",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(headings, key = { _, h -> h.id }) { _, heading ->
                    val indent = ((heading.level - 1).coerceAtLeast(0) * 16).dp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectHeading(heading) }
                            .padding(start = 24.dp + indent, end = 24.dp, top = 12.dp, bottom = 12.dp)
                    ) {
                        Text(
                            text = heading.title,
                            style = when (heading.level) {
                                1 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                2 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                else -> MaterialTheme.typography.bodyMedium
                            },
                            color = if (heading.level == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
