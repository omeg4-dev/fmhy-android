package io.github.omeg4dev.fmhy.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import io.github.omeg4dev.fmhy.SectionNavKey
import io.github.omeg4dev.fmhy.WhatsNewNavKey
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.ui.common.EntryDetailBottomSheet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.nav.LocalAppNav
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import io.github.omeg4dev.fmhy.nav.Tab
import io.github.omeg4dev.fmhy.ui.common.LocalPasteResolver
import io.github.omeg4dev.fmhy.ui.common.SyncBanner
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import io.github.omeg4dev.fmhy.ui.common.sectionIcon
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    container: AppContainer,
    onNavigateToSearch: () -> Unit,
    onNavigateToSection: (sectionId: String, anchor: String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCheckLink: () -> Unit = {},
    onNavigateToReader: (String, String) -> Unit = { _, _ -> },
    onNavigateToPosts: () -> Unit = {},
    onShowSnackbar: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)
    val listState = rememberLazyListState()
    val nav = LocalAppNav.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var showRandomPickDetails by remember { mutableStateOf(false) }

    LaunchedEffect(nav) {
        nav.reselect.filter { it == Tab.BROWSE }.collect {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.fmhy_logo),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        FmhyWordmark()
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCheckLink) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = "Check a link")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!state.isReady) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Preparing FMHY dataset...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (state.isEmptyState) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            "No data yet — pull to update",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.triggerSync() }) {
                            Text("Download Dataset")
                        }
                    }
                }
            } else {
                val totalEntries = remember(state.sections) { state.sections.sumOf { it.entryCount } }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 16.dp + LocalBottomBarPadding.current,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Sync banner item
                    item(key = "sync-banner") {
                        AnimatedVisibility(
                            visible = state.syncBanner != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                state.syncBanner?.let { banner ->
                                    SyncBanner(
                                        text = banner.text,
                                        isLoading = banner.isLoading,
                                        isError = banner.isError,
                                        onDismiss = { viewModel.dismissBanner() },
                                    )
                                }
                            }
                        }
                    }

                    // Search pill (16dp horizontal margin, 8dp bottom)
                    item(key = "search-pill") {
                        Surface(
                            onClick = onNavigateToSearch,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(horizontal = 16.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 16.dp, end = 16.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Search ${"%,d".format(totalEntries)} sites",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // "New since last visit: N" card (when > 0)
                    if (state.newSinceLastVisitCount > 0) {
                        item(key = "new-since-last-visit") {
                            Surface(
                                onClick = {
                                    haptics.click()
                                    nav.push(WhatsNewNavKey(filter = "all"))
                                },
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            ) {
                                ListItem(
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent,
                                        headlineColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        leadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        trailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                    leadingContent = {
                                        Icon(Icons.Outlined.NewReleases, contentDescription = null)
                                    },
                                    headlineContent = {
                                        Text(
                                            text = "${state.newSinceLastVisitCount} new since your last visit",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    },
                                    trailingContent = {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                    },
                                )
                            }
                        }
                    }

                    // Random pick card
                    state.randomPick?.let { pick ->
                        item(key = "random-pick") {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .combinedClickable(
                                        onClick = {
                                            haptics.click()
                                            linkOpener.openUrl(pick.url)
                                        },
                                        onLongClick = {
                                            haptics.longPress()
                                            showRandomPickDetails = true
                                        },
                                    ),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Casino,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Random pick",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(
                                            onClick = {
                                                haptics.click()
                                                viewModel.refreshRandomPick()
                                            },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Reroll random pick",
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = pick.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (pick.description.isNotEmpty()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = pick.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    // Where it lives: section icon + "Section › category"; tap opens the section
                                    val sectionTitle = state.sections.firstOrNull { it.id == pick.sectionId }?.title
                                    val origin = remember(pick.headingPath, sectionTitle) {
                                        val segments = pick.headingPath
                                            .split(Regex("""\s*[›>]\s*"""))
                                            .filter { it.isNotBlank() }
                                            .map { CrossLinks.splitLinkTitle(it).first }
                                        val section = sectionTitle ?: segments.firstOrNull().orEmpty()
                                        val category = segments.drop(1).lastOrNull()
                                        listOfNotNull(section.ifEmpty { null }, category).joinToString(" › ")
                                    }
                                    if (origin.isNotEmpty()) {
                                        Spacer(Modifier.height(10.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.small)
                                                .clickable {
                                                    haptics.click()
                                                    scope.launch {
                                                        val anchor = try {
                                                            pick.headingId?.let { hid ->
                                                                container.datasetStore.headings(pick.sectionId).firstOrNull { it.id == hid }?.anchor
                                                            }
                                                        } catch (_: Exception) { null }
                                                        nav.push(SectionNavKey(pick.sectionId, anchor = anchor, focusEntryId = pick.id))
                                                    }
                                                }
                                                .padding(vertical = 2.dp),
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = sectionIcon(pick.sectionId),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = origin,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pinned block (only when personalHome is on)
                    if (state.personalHome) {
                        if (state.pins.isEmpty()) {
                            item(key = "pins-empty-card") {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(
                                        text = "Pinned",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                    Surface(
                                        shape = MaterialTheme.shapes.large,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = "Pin sections or categories from their menu to see them here.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(16.dp),
                                        )
                                    }
                                }
                            }
                        } else {
                            item(key = "pins-block") {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(
                                        text = "Pinned",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        state.pins.forEach { pin ->
                                            AssistChip(
                                                onClick = {
                                                    haptics.click()
                                                    onNavigateToSection(pin.sectionId, pin.anchor)
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = sectionIcon(pin.sectionId),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = pin.label,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                ),
                                                border = null,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Sections header
                    state.sectionGroups.forEach { group ->
                    item(key = "sections-header-${group.title}") {
                        Text(
                            text = group.title ?: "Sections",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                        )
                    }

                    // Sections of one group inside one Surface
                    item(key = "sections-list-${group.title}") {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                group.sections.forEachIndexed { index, section ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 72.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            thickness = 0.5.dp,
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 64.dp)
                                            .clickable {
                                                haptics.click()
                                                when (section.id) {
                                                    "audio-tools" -> linkOpener.openUrl("https://fmhy.net/audio#audio-tools")
                                                    "educational-tools" -> linkOpener.openUrl("https://fmhy.net/educational#educational-tools")
                                                    "beginners-guide" -> onNavigateToReader("beginners-guide.md", "Beginner's guide")
                                                    "faq" -> onNavigateToReader("other/FAQ.md", "FAQ")
                                                    "backups" -> onNavigateToReader("other/backups.md", "Backups & mirrors")
                                                    "news-and-posts" -> onNavigateToPosts()
                                                    else -> onNavigateToSection(section.id, null)
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.size(40.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = sectionIcon(section.id),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = section.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (section.description.isNotBlank()) {
                                                Text(
                                                    text = section.description,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            if (section.entryCount > 0) {
                                                Text(
                                                    text = "${"%,d".format(section.entryCount)} sites",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }

                    // Hidden sections toggle (only when personalHome is on)
                    if (state.personalHome && state.hiddenSectionsCount > 0) {
                        item(key = "hidden-sections-toggle") {
                            val text = if (state.showingHiddenSections) {
                                "${state.hiddenSectionsCount} hidden sections · Hide"
                            } else {
                                "${state.hiddenSectionsCount} hidden sections · Show"
                            }
                            Surface(
                                onClick = {
                                    haptics.click()
                                    viewModel.toggleShowHiddenSections()
                                },
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRandomPickDetails && state.randomPick != null) {
        val pick = state.randomPick!!
        val context = LocalContext.current
        val pasteResolver = LocalPasteResolver.current
        EntryDetailBottomSheet(
            entry = pick,
            onShowInSection = {
                scope.launch {
                    val anchor = try {
                        pick.headingId?.let { hid ->
                            container.datasetStore.headings(pick.sectionId).firstOrNull { it.id == hid }?.anchor
                        }
                    } catch (_: Exception) { null }
                    nav.push(SectionNavKey(pick.sectionId, anchor = anchor, focusEntryId = pick.id))
                }
            },
            onDismiss = { showRandomPickDetails = false },
            onOpenUrl = { linkOpener.openUrl(it) },
            onCopyLink = { url ->
                showRandomPickDetails = false
                scope.launch {
                    val text = pasteResolver?.resolve(url)?.joinToString("\n") ?: url
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", text))
                    onShowSnackbar(if ('\n' in text) "Links copied to clipboard" else "Link copied to clipboard")
                }
            },
            onShareLink = { url ->
                showRandomPickDetails = false
                scope.launch {
                    val text = pasteResolver?.resolve(url)?.joinToString("\n") ?: url
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share ${pick.name}"))
                }
            },
            onShowSnackbar = onShowSnackbar,
        )
    }

    UnsafeWarningDialog(linkOpener)
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    container: AppContainer,
    onNavigateToSearch: () -> Unit,
    onNavigateToSection: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCheckLink: () -> Unit = {},
    onShowSnackbar: (String) -> Unit = {},
) = HomeScreen(
    viewModel = viewModel,
    container = container,
    onNavigateToSearch = onNavigateToSearch,
    onNavigateToSection = { sectionId, _ -> onNavigateToSection(sectionId) },
    onNavigateToSettings = onNavigateToSettings,
    onNavigateToCheckLink = onNavigateToCheckLink,
    onShowSnackbar = onShowSnackbar,
)
