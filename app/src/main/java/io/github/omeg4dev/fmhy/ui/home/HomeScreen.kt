package io.github.omeg4dev.fmhy.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import io.github.omeg4dev.fmhy.SectionNavKey
import io.github.omeg4dev.fmhy.WhatsNewNavKey
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.user.Pin
import io.github.omeg4dev.fmhy.ui.common.EntryDetailBottomSheet
import io.github.omeg4dev.fmhy.ui.onboarding.ONBOARDING_VERSION
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import io.github.omeg4dev.fmhy.report.ReportAction
import io.github.omeg4dev.fmhy.report.ReportArea
import io.github.omeg4dev.fmhy.report.ReportRequest
import io.github.omeg4dev.fmhy.nav.LocalAppNav
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import io.github.omeg4dev.fmhy.nav.Tab
import io.github.omeg4dev.fmhy.ui.common.CrossfadeContent
import io.github.omeg4dev.fmhy.ui.common.LocalPasteDecoder
import io.github.omeg4dev.fmhy.ui.common.PasteAction
import io.github.omeg4dev.fmhy.ui.common.linkText
import io.github.omeg4dev.fmhy.ui.common.LocalSnackbarHost
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.enterStagger
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import io.github.omeg4dev.fmhy.ui.common.sectionIcon
import io.github.omeg4dev.fmhy.ui.common.showUndo
import io.github.omeg4dev.fmhy.ui.common.unpinItem
import io.github.omeg4dev.fmhy.ui.common.showFollowing
import io.github.omeg4dev.fmhy.ui.search.SearchBarShape
import io.github.omeg4dev.fmhy.ui.search.searchBarBorder
import io.github.omeg4dev.fmhy.ui.search.searchBarContainerColor
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
    val snackbarHostState = remember { SnackbarHostState() }
    val bottomBarPadding = LocalBottomBarPadding.current
    var showRandomPickDetails by remember { mutableStateOf(false) }

    val offerCrashes by container.settingsRepository.reportOfferCrashes.collectAsStateWithLifecycle(initialValue = true)
    var pendingCrash by remember { mutableStateOf(container.crashReportStore.peek()) }
    val reportRequest by container.reportRequest.collectAsStateWithLifecycle()
    LaunchedEffect(reportRequest) {
        if (reportRequest == null && pendingCrash != null && container.crashReportStore.peek() == null) {
            pendingCrash = null
        }
    }

    val screenSwipeHintShown by container.settingsRepository.screenSwipeHintShown.collectAsStateWithLifecycle(initialValue = true)
    val onboardingVersion by container.settingsRepository.onboardingVersion.collectAsStateWithLifecycle(initialValue = -1)

    if (!screenSwipeHintShown && onboardingVersion >= ONBOARDING_VERSION) {
        AlertDialog(
            onDismissRequest = {
                scope.launch { container.settingsRepository.setScreenSwipeHintShown(true) }
            },
            title = { Text("New: swipe between screens") },
            text = {
                Text("You can now swipe between screens. Swipe left or right to move between Browse, Search and Saved. You can turn this off in Settings > Lists & gestures.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { container.settingsRepository.setScreenSwipeHintShown(true) }
                    }
                ) {
                    Text("Got it")
                }
            },
        )
    }

    LaunchedEffect(nav) {
        nav.reselect.filter { it == Tab.BROWSE }.collect {
            listState.animateScrollToItem(0)
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
        CompositionLocalProvider(LocalSnackbarHost provides snackbarHostState) {
            CrossfadeContent(
                targetState = when {
                    !state.isReady -> 0
                    state.isEmptyState -> 1
                    else -> 2
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) { page ->
                when (page) {
                    0 -> {
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
                    }
                    1 -> {
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
                    }
                    2 -> {
                        val totalEntries = remember(state.sections) { state.sections.sumOf { it.entryCount } }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 16.dp + LocalBottomBarPadding.current,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (offerCrashes && pendingCrash != null) {
                        val crash = pendingCrash!!
                        item(key = "crash-report-card") {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = "The app closed unexpectedly. Report it?",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                container.openReport(
                                                    ReportRequest(
                                                        defaultArea = ReportArea.OTHER,
                                                        pageUrl = null,
                                                        isPrivateTab = false,
                                                        crash = crash,
                                                        emphasizedAction = ReportAction.GITHUB,
                                                    )
                                                )
                                            },
                                        ) {
                                            Text("Report on GitHub")
                                        }
                                        FilledTonalButton(
                                            onClick = {
                                                container.openReport(
                                                    ReportRequest(
                                                        defaultArea = ReportArea.OTHER,
                                                        pageUrl = null,
                                                        isPrivateTab = false,
                                                        crash = crash,
                                                        emphasizedAction = ReportAction.DISCORD,
                                                    )
                                                )
                                            },
                                        ) {
                                            Text("Message on Discord")
                                        }
                                        TextButton(
                                            onClick = {
                                                container.crashReportStore.clear()
                                                pendingCrash = null
                                            },
                                        ) {
                                            Text("Dismiss")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Search pill (16dp horizontal margin, 8dp bottom)
                    item(key = "search-pill") {
                        Surface(
                            onClick = onNavigateToSearch,
                            shape = SearchBarShape,
                            color = searchBarContainerColor(),
                            border = searchBarBorder(focused = false),
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
                                    tint = MaterialTheme.colorScheme.primary,
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
                                    nav.push(WhatsNewNavKey(filter = "all", type = "added", period = "since_last_visit"))
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

                    // Random pick row
                    state.randomPick?.let { pick ->
                        item(key = "random-pick") {
                            val sectionTitle = state.sections.firstOrNull { it.id == pick.sectionId }?.title
                            val category = remember(pick.headingPath, sectionTitle) {
                                val segments = pick.headingPath
                                    .split(Regex("""\s*[›>]\s*"""))
                                    .filter { it.isNotBlank() }
                                    .map { CrossLinks.splitLinkTitle(it).first }
                                segments.drop(1).lastOrNull() ?: sectionTitle.orEmpty()
                            }
                            val labelMediumStyle = MaterialTheme.typography.labelMedium
                            val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                            val annotatedText = remember(pick.name, category, labelMediumStyle, onSurfaceVariant) {
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(pick.name)
                                    }
                                    if (category.isNotEmpty()) {
                                        append("  ")
                                        withStyle(
                                            SpanStyle(
                                                fontSize = labelMediumStyle.fontSize,
                                                fontWeight = FontWeight.Normal,
                                                color = onSurfaceVariant,
                                            )
                                        ) {
                                            append(category)
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = "Random pick",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.large)
                                        .combinedClickable(
                                            onClick = {
                                                linkOpener.openUrl(pick.url)
                                            },
                                            onLongClick = {
                                                haptics.longPress()
                                                showRandomPickDetails = true
                                            },
                                        ),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Casino,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = annotatedText,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(
                                            onClick = {
                                                viewModel.refreshRandomPick()
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Reroll random pick",
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
                                Column(
                                    modifier = Modifier
                                        .animateItem()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
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
                                Column(
                                    modifier = Modifier
                                        .animateItem()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = "Pinned",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        state.pins.chunked(2).forEach { rowPins ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(IntrinsicSize.Min),
                                            ) {
                                                rowPins.forEach { pin ->
                                                    val isFollowed = (pin.sectionId to pin.headingId) in state.followedKeys
                                                    PinTile(
                                                        pin = pin,
                                                        isFollowed = isFollowed,
                                                        onNavigate = {
                                                            onNavigateToSection(pin.sectionId, pin.anchor)
                                                        },
                                                        onUnpin = {
                                                            scope.launch {
                                                                unpinItem(
                                                                    container = container,
                                                                    snackbarHostState = snackbarHostState,
                                                                    haptics = haptics,
                                                                    sectionId = pin.sectionId,
                                                                    headingId = pin.headingId,
                                                                    title = pin.label,
                                                                )
                                                            }
                                                        },
                                                        onToggleFollow = {
                                                            scope.launch {
                                                                val isMainSection = pin.headingId.isEmpty()
                                                                if (isMainSection) {
                                                                    if (isFollowed) {
                                                                        container.userStore.unfollowSection(pin.sectionId)
                                                                        onShowSnackbar("Unfollowed ${pin.title}")
                                                                    } else {
                                                                        container.userStore.followSection(pin.sectionId, pin.title)
                                                                        showFollowing(snackbarHostState, pin.title, isPinned = true) {}
                                                                    }
                                                                } else {
                                                                    val nextFollowed = !isFollowed
                                                                    val fullTitle = if (pin.parentTitle != null) "${pin.parentTitle} › ${pin.title}" else pin.title
                                                                    container.userStore.setSubsectionFollowed(
                                                                        pin.sectionId,
                                                                        pin.headingId,
                                                                        fullTitle,
                                                                        nextFollowed,
                                                                    )
                                                                    if (nextFollowed) {
                                                                        showFollowing(snackbarHostState, pin.title, isPinned = true) {}
                                                                    } else {
                                                                        onShowSnackbar("Unfollowed ${pin.title}")
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight(),
                                                    )
                                                }
                                                if (rowPins.size == 1) {
                                                    Spacer(Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Sections header
                    state.sectionGroups.forEachIndexed { groupIndex, group ->
                    item(key = "sections-header-${group.title}") {
                        Text(
                            text = group.title ?: "Sections",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .animateItem()
                                .enterStagger(groupIndex * 2, state.datasetVersion)
                                .padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                        )
                    }

                    // Sections of one group inside one Surface
                    item(key = "sections-list-${group.title}") {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .animateItem()
                                .enterStagger(groupIndex * 2 + 1, state.datasetVersion)
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
                                            if (section.id == "news-and-posts" && state.unreadPosts > 0) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = "New",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
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
}
}

    if (showRandomPickDetails && state.randomPick != null) {
        val pick = state.randomPick!!
        val context = LocalContext.current
        val pasteDecoder = LocalPasteDecoder.current
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
                pasteDecoder.linkText(url, PasteAction.COPY) { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", text))
                    onShowSnackbar(if ('\n' in text) "Links copied to clipboard" else "Link copied to clipboard")
                }
            },
            onShareLink = { url ->
                showRandomPickDetails = false
                pasteDecoder.linkText(url, PasteAction.SHARE) { text ->
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

@Composable
private fun PinTile(
    pin: ResolvedPin,
    isFollowed: Boolean,
    onNavigate: () -> Unit,
    onUnpin: () -> Unit,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    Box(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 56.dp)
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = onNavigate,
                    onLongClick = {
                        haptics.longPress()
                        menuExpanded = true
                    },
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = sectionIcon(pin.sectionId),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = pin.title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (pin.parentTitle != null) {
                        Text(
                            text = pin.parentTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Unpin") },
                onClick = {
                    menuExpanded = false
                    onUnpin()
                },
            )
            DropdownMenuItem(
                text = { Text(if (isFollowed) "Unfollow" else "Follow") },
                onClick = {
                    menuExpanded = false
                    onToggleFollow()
                },
            )
        }
    }
}
