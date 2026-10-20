package io.github.omeg4dev.fmhy.ui.whatsnew

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.SectionNavKey
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.nav.LocalAppNav
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import io.github.omeg4dev.fmhy.ui.common.CrossfadeContent
import io.github.omeg4dev.fmhy.ui.common.EmptyState
import io.github.omeg4dev.fmhy.ui.common.EntryDetailBottomSheet
import io.github.omeg4dev.fmhy.ui.common.EntryRow
import io.github.omeg4dev.fmhy.ui.common.LocalPasteResolver
import io.github.omeg4dev.fmhy.ui.common.SkeletonRows
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.enterStagger
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import io.github.omeg4dev.fmhy.ui.common.sectionIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)
    val pasteResolver = LocalPasteResolver.current

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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptics.tick()
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
                            haptics.click()
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
            Column(modifier = Modifier.fillMaxSize()) {
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
                        haptics.tick()
                        viewModel.clearTypes()
                    },
                    onSetScope = {
                        haptics.tick()
                        viewModel.setScope(it)
                    },
                    onOpenSectionsSheet = {
                        haptics.tick()
                        showSectionsSheet = true
                    },
                )

                // Summary line
                SummaryLine(
                    counts = state.counts,
                    selectedTypes = state.types,
                    onSelectKind = { kind ->
                        haptics.tick()
                        if (state.types == setOf(kind)) {
                            viewModel.clearTypes()
                        } else {
                            viewModel.setTypes(setOf(kind))
                        }
                    },
                )

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
                                EmptyState(
                                    icon = Icons.Outlined.HistoryToggleOff,
                                    title = "Nothing here for these filters",
                                    body = "Try a longer period or all types.",
                                    actionLabel = "Show everything",
                                    onAction = {
                                        haptics.click()
                                        viewModel.resetFilters()
                                    },
                                )
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
                haptics.tick()
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
                scope.launch {
                    val text = pasteResolver?.resolve(url)?.joinToString("\n") ?: url
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", text))
                    onShowSnackbar(if ('\n' in text) "Links copied to clipboard" else "Link copied to clipboard")
                }
            },
            onShareLink = { url ->
                selectedDetailEntry = null
                scope.launch {
                    val text = pasteResolver?.resolve(url)?.joinToString("\n") ?: url
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

@Composable
private fun FilterBar(
    state: WhatsNewUiState,
    sectionTitles: Map<String, String>,
    onSetPeriod: (WhatsNewPeriod) -> Unit,
    onToggleType: (ChangeKind) -> Unit,
    onClearTypes: () -> Unit,
    onSetScope: (WhatsNewScope) -> Unit,
    onOpenSectionsSheet: () -> Unit,
) {
    var periodMenuExpanded by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var scopeMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Period Pill
        Box {
            FilterDropdownPill(
                label = state.period.label,
                active = state.period != WhatsNewPeriod.WEEK,
                onClick = { periodMenuExpanded = true },
            )
            DropdownMenu(
                expanded = periodMenuExpanded,
                onDismissRequest = { periodMenuExpanded = false },
            ) {
                WhatsNewPeriod.entries.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.label) },
                        trailingIcon = if (state.period == p) {
                            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        onClick = {
                            periodMenuExpanded = false
                            onSetPeriod(p)
                        },
                    )
                }
            }
        }

        // 2. Type Pill
        val typeLabel = when {
            state.types.isEmpty() -> "All types"
            state.types.size == 1 -> state.types.first().label()
            else -> "${state.types.size} types"
        }
        Box {
            FilterDropdownPill(
                label = typeLabel,
                active = state.types.isNotEmpty(),
                onClick = { typeMenuExpanded = true },
            )
            DropdownMenu(
                expanded = typeMenuExpanded,
                onDismissRequest = { typeMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("All types") },
                    trailingIcon = if (state.types.isEmpty()) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        onClearTypes()
                    },
                )
                HorizontalDivider()
                ChangeKind.entries.forEach { kind ->
                    val count = state.counts[kind] ?: 0
                    val isSelected = kind in state.types
                    if (count > 0 || isSelected) {
                        val look = kind.style()
                        DropdownMenuItem(
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(look.containerColor),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = look.icon,
                                        contentDescription = null,
                                        tint = look.contentColor,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
                            text = { Text(look.label) },
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null,
                                    )
                                }
                            },
                            onClick = {
                                onToggleType(kind)
                            },
                        )
                    }
                }
            }
        }

        // 3. Scope Pill
        val scopeLabel = when (val s = state.scope) {
            is WhatsNewScope.All -> "All sections"
            is WhatsNewScope.Following -> "Following"
            is WhatsNewScope.Saved -> "Saved"
            is WhatsNewScope.Sections -> {
                if (s.sections.size == 1) {
                    val secId = s.sections.first()
                    sectionTitles[secId] ?: "1 section"
                } else {
                    "${s.sections.size} sections"
                }
            }
        }
        Box {
            FilterDropdownPill(
                label = scopeLabel,
                active = state.scope !is WhatsNewScope.All,
                onClick = { scopeMenuExpanded = true },
            )
            DropdownMenu(
                expanded = scopeMenuExpanded,
                onDismissRequest = { scopeMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("All sections") },
                    trailingIcon = if (state.scope is WhatsNewScope.All) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        scopeMenuExpanded = false
                        onSetScope(WhatsNewScope.All)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Following") },
                    enabled = state.hasFollows,
                    trailingIcon = if (state.scope is WhatsNewScope.Following) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        scopeMenuExpanded = false
                        onSetScope(WhatsNewScope.Following)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Saved sites") },
                    trailingIcon = if (state.scope is WhatsNewScope.Saved) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        scopeMenuExpanded = false
                        onSetScope(WhatsNewScope.Saved)
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Choose sections…") },
                    onClick = {
                        scopeMenuExpanded = false
                        onOpenSectionsSheet()
                    },
                )
            }
        }
    }
}

@Composable
fun FilterDropdownPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryLine(
    counts: Map<ChangeKind, Int>,
    selectedTypes: Set<ChangeKind>,
    onSelectKind: (ChangeKind) -> Unit,
) {
    val order = listOf(
        ChangeKind.ADDED,
        ChangeKind.RENAMED,
        ChangeKind.URL_CHANGED,
        ChangeKind.MOVED,
        ChangeKind.STARRED,
        ChangeKind.UNSTARRED,
        ChangeKind.REMOVED,
        ChangeKind.EDITED,
    )

    val activeSegments = order.mapNotNull { kind ->
        val count = counts[kind] ?: 0
        if (count > 0) kind to count else null
    }

    if (activeSegments.isEmpty()) return

    // One scrollable line: each segment keeps a 48 dp touch target without wrapping into tall gaps.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        activeSegments.forEachIndexed { index, (kind, count) ->
            val look = kind.style()
            val text = when (kind) {
                ChangeKind.ADDED -> "$count new"
                ChangeKind.RENAMED -> "$count renamed"
                ChangeKind.URL_CHANGED -> if (count == 1) "1 new link" else "$count new links"
                ChangeKind.MOVED -> "$count moved"
                ChangeKind.STARRED -> "$count starred"
                ChangeKind.UNSTARRED -> "$count unstarred"
                ChangeKind.REMOVED -> "$count removed"
                ChangeKind.EDITED -> "$count edited"
            }

            val isSelected = selectedTypes == setOf(kind)
            Surface(
                onClick = { onSelectKind(kind) },
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) look.containerColor else MaterialTheme.colorScheme.surface,
                modifier = Modifier.defaultMinSize(minHeight = 28.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = look.contentColor,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            if (index < activeSegments.lastIndex) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChangeCardRow(
    card: ChangeCard,
    sectionTitles: Map<String, String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryLook = card.primaryKind.style()
    val isRemoved = card.primaryKind == ChangeKind.REMOVED

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Leading badge icon (40 dp rounded square)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(primaryLook.containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = primaryLook.icon,
                contentDescription = primaryLook.label,
                tint = primaryLook.contentColor,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Line 1: Name + ⭐ + other kinds badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val isStarred = card.entry?.starredAt != null ||
                    (card.entry?.tier?.let { it >= Tier.STAR } == true) ||
                    ChangeKind.STARRED in card.kinds

                Text(
                    text = if (isStarred) "${card.name} ⭐" else card.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (isRemoved) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    color = if (isRemoved) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                val otherKinds = card.kinds.filter { it != card.primaryKind }
                otherKinds.forEach { otherKind ->
                    val otherLook = otherKind.style()
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = otherLook.containerColor,
                    ) {
                        Text(
                            text = otherLook.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = otherLook.contentColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // Line 2: The change itself by primary kind
            when (card.primaryKind) {
                ChangeKind.ADDED -> {
                    val desc = card.entry?.description?.ifBlank { null } ?: card.name
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ChangeKind.REMOVED -> {
                    Text(
                        text = "Removed · tap for replacements",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ChangeKind.RENAMED -> {
                    if (!card.oldName.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = card.oldName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = TextDecoration.LineThrough,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = " → ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = card.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = primaryLook.contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(
                            text = "Renamed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ChangeKind.URL_CHANGED -> {
                    val formatted = WhatsNewGrouping.formatUrlChange(card.oldUrl, card.url)
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ChangeKind.MOVED -> {
                    val newTitle = sectionTitles[card.sectionId] ?: card.sectionId
                    val oldSecId = card.oldSectionId
                    if (oldSecId != null) {
                        val oldTitle = sectionTitles[oldSecId] ?: oldSecId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = sectionIcon(oldSecId),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = oldTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "→",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                imageVector = sectionIcon(card.sectionId),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = primaryLook.contentColor,
                            )
                            Text(
                                text = newTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryLook.contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(
                            text = "Moved to $newTitle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ChangeKind.STARRED -> {
                    Text(
                        text = "Now starred on FMHY",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ChangeKind.UNSTARRED -> {
                    Text(
                        text = "No longer starred",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ChangeKind.EDITED -> {
                    if (card.oldLine.isNullOrBlank()) {
                        Text(
                            text = "Description changed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        EditedWordDiffView(
                            oldLine = card.oldLine,
                            newLine = card.entry?.description.orEmpty(),
                            primaryLook = primaryLook,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Line 3: Section path with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = sectionIcon(card.sectionId),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val secTitle = sectionTitles[card.sectionId] ?: card.sectionId
                val headingPath = card.entry?.headingPath
                val pathDisplay = if (!headingPath.isNullOrBlank()) {
                    if (headingPath.startsWith(secTitle)) headingPath else "$secTitle › $headingPath"
                } else {
                    secTitle
                }
                Text(
                    text = pathDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EditedWordDiffView(
    oldLine: String,
    newLine: String,
    primaryLook: ChangeTypeLook,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    val oldDesc = remember(oldLine) { WordDiff.descriptionOf(oldLine) }
    val newDesc = remember(newLine) { WordDiff.descriptionOf(newLine) }
    val segments = remember(oldDesc, newDesc) {
        val diff = WordDiff.wordDiff(oldDesc, newDesc)
        WordDiff.compactDiff(diff, context = 4)
    }

    val annotatedString = remember(segments, colorScheme, primaryLook) {
        buildAnnotatedString {
            segments.forEachIndexed { i, seg ->
                if (i > 0) append(" ")
                when (seg.type) {
                    DiffType.SAME -> {
                        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                            append(seg.text)
                        }
                    }
                    DiffType.REMOVED -> {
                        withStyle(
                            SpanStyle(
                                color = colorScheme.error,
                                background = colorScheme.errorContainer.copy(alpha = 0.5f),
                                textDecoration = TextDecoration.LineThrough,
                            )
                        ) {
                            append(seg.text)
                        }
                    }
                    DiffType.ADDED -> {
                        withStyle(
                            SpanStyle(
                                color = primaryLook.contentColor,
                                background = primaryLook.containerColor,
                            )
                        ) {
                            append(seg.text)
                        }
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable { isExpanded = !isExpanded },
        )
        if (annotatedString.length > 80 || segments.size > 5) {
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegendBottomSheet(
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "What the colours mean",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            val kinds = listOf(
                ChangeKind.ADDED,
                ChangeKind.REMOVED,
                ChangeKind.RENAMED,
                ChangeKind.URL_CHANGED,
                ChangeKind.MOVED,
                ChangeKind.STARRED,
                ChangeKind.UNSTARRED,
                ChangeKind.EDITED,
            )
            kinds.forEach { kind ->
                val look = kind.style()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(look.containerColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = look.icon,
                            contentDescription = null,
                            tint = look.contentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = look.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = look.contentColor,
                        )
                        Text(
                            text = look.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooseSectionsBottomSheet(
    availableSections: List<Section>,
    selectedSections: Set<String>,
    countsBySection: Map<String, Int>,
    onToggleSection: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Choose sections",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(availableSections, key = { it.id }) { section ->
                    val isChecked = section.id in selectedSections
                    val count = countsBySection[section.id] ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleSection(section.id) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = sectionIcon(section.id),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (count > 0) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleSection(section.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplacementsBottomSheet(
    card: ChangeCard,
    viewModel: WhatsNewViewModel,
    onOpenUrl: (String) -> Unit,
    onShowSnackbar: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var replacements by remember { mutableStateOf<List<Entry>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(card.key) {
        isLoading = true
        replacements = viewModel.getReplacements(card)
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
                    text = "Replacements for ${card.name}",
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
