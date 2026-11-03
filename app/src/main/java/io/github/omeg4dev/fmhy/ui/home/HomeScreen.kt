package io.github.omeg4dev.fmhy.ui.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.user.Pin
import io.github.omeg4dev.fmhy.ui.common.CheckLinkIcon
import io.github.omeg4dev.fmhy.ui.common.EntryDetailBottomSheet
import io.github.omeg4dev.fmhy.ui.onboarding.ONBOARDING_VERSION
import io.github.omeg4dev.fmhy.ui.shell.SyncStatusIcon
import io.github.omeg4dev.fmhy.ui.vpn.IpLine
import io.github.omeg4dev.fmhy.ui.vpn.openVpnSettings
import io.github.omeg4dev.fmhy.ui.vpn.rememberIpLine
import io.github.omeg4dev.fmhy.ui.vpn.vpnColor
import io.github.omeg4dev.fmhy.ui.vpn.vpnIcon
import io.github.omeg4dev.fmhy.ui.vpn.vpnStateHint
import io.github.omeg4dev.fmhy.ui.vpn.vpnStateText
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.omeg4dev.fmhy.ui.common.displayTitle
import io.github.omeg4dev.fmhy.ui.common.sectionDisplayTitle
import io.github.omeg4dev.fmhy.ui.common.sectionIcon
import io.github.omeg4dev.fmhy.ui.common.showUndo
import io.github.omeg4dev.fmhy.ui.common.unpinItem
import io.github.omeg4dev.fmhy.ui.common.showFollowing
import io.github.omeg4dev.fmhy.ui.search.SearchBarShape
import io.github.omeg4dev.fmhy.ui.search.searchBarBorder
import io.github.omeg4dev.fmhy.ui.search.searchBarContainerColor
import io.github.omeg4dev.fmhy.ui.home.blocks.favouritesBlock
import io.github.omeg4dev.fmhy.ui.home.blocks.newInPinnedBlock
import io.github.omeg4dev.fmhy.ui.home.blocks.newSinceBlock
import io.github.omeg4dev.fmhy.ui.home.blocks.pinnedBlock
import io.github.omeg4dev.fmhy.ui.home.blocks.randomPickBlock
import io.github.omeg4dev.fmhy.ui.home.blocks.searchBlock
import io.github.omeg4dev.fmhy.ui.home.blocks.sectionsBlock
import io.github.omeg4dev.fmhy.ui.home.blocks.suggestionsBlock
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
    var editing by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = editing) { editing = false }
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)
    val listState = rememberLazyListState()
    val nav = LocalAppNav.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val bottomBarPadding = LocalBottomBarPadding.current
    var detailEntry by remember { mutableStateOf<io.github.omeg4dev.fmhy.data.model.Entry?>(null) }

    val offerCrashes by container.settingsRepository.reportOfferCrashes.collectAsStateWithLifecycle(initialValue = true)
    val protectionPrefs by container.settingsRepository.protectionPrefs.collectAsStateWithLifecycle(initialValue = ProtectionPrefs())
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
                    if (editing) {
                        Text("Edit Home")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.fmhy_logo),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            FmhyWordmark()
                        }
                    }
                },
                actions = {
                    if (editing) {
                        TextButton(onClick = { editing = false }) {
                            Text("Done")
                        }
                    } else {
                        // Tighter action row: 40 dp buttons instead of the 48 dp default.
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                            SyncStatusIcon(container.statusNotices)
                            if (protectionPrefs.showVpnHomeIcon) {
                                VpnStatusAction(container = container, protectionPrefs = protectionPrefs)
                            }
                            IconButton(onClick = onNavigateToCheckLink) {
                                Icon(CheckLinkIcon, contentDescription = "Check a link")
                            }
                            IconButton(onClick = { nav.push(WhatsNewNavKey()) }) {
                                Icon(Icons.Outlined.NewReleases, contentDescription = "Recent changes")
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
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
                        if (editing) {
                            EditHomeList(
                                layout = state.layout,
                                onChange = viewModel::updateLayout,
                                modifier = Modifier.fillMaxSize(),
                            )
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

                    state.blocks.forEach { block ->
                        when (block) {
                            HomeBlock.SEARCH -> searchBlock(
                                totalEntries = totalEntries,
                                onNavigateToSearch = onNavigateToSearch,
                                showHeadline = state.blocks.firstOrNull() != HomeBlock.SEARCH,
                            )
                            HomeBlock.NEW_SINCE -> newSinceBlock(
                                count = state.newSinceLastVisitCount,
                                onNavigateToNewSince = {
                                    nav.push(WhatsNewNavKey(filter = "all", type = "added", period = "since_last_visit"))
                                },
                            )
                            HomeBlock.RANDOM_PICK -> randomPickBlock(
                                pick = state.randomPick,
                                sections = state.sections,
                                onOpenUrl = { linkOpener.openUrl(it) },
                                onShowDetails = { pick ->
                                    haptics.longPress()
                                    detailEntry = pick
                                },
                                onRefresh = { viewModel.refreshRandomPick() },
                            )
                            HomeBlock.PINNED -> pinnedBlock(
                                pins = state.pins,
                                followedKeys = state.followedKeys,
                                onNavigateToSection = onNavigateToSection,
                                onUnpin = { pin ->
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
                                onToggleFollow = { pin, isFollowed ->
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
                            )
                            HomeBlock.NEW_IN_PINNED -> newInPinnedBlock(
                                pinsCount = state.pins.size,
                                items = state.newInPinned,
                                sections = state.sections,
                                onOpenUrl = { linkOpener.openUrl(it) },
                                onSeeAll = {
                                    nav.push(WhatsNewNavKey(filter = "all", type = "added", period = "since_last_visit"))
                                },
                            )
                            HomeBlock.FAVOURITES -> favouritesBlock(
                                favourites = state.favourites,
                                onOpenUrl = { linkOpener.openUrl(it) },
                                onNavigateToSaved = {
                                    nav.select(Tab.SAVED)
                                },
                            )
                            HomeBlock.SUGGESTIONS -> suggestionsBlock(
                                suggestions = state.suggestions,
                                sections = state.sections,
                                onOpenUrl = { linkOpener.openUrl(it) },
                                onShowDetails = { entry ->
                                    haptics.longPress()
                                    detailEntry = entry
                                },
                            )
                            HomeBlock.SECTIONS -> sectionsBlock(
                                sectionGroups = state.sectionGroups,
                                datasetVersion = state.datasetVersion,
                                unreadPosts = state.unreadPosts,
                                hiddenSectionsCount = state.hiddenSectionsCount,
                                showingHiddenSections = state.showingHiddenSections,
                                onToggleShowHidden = { viewModel.toggleShowHiddenSections() },
                                onOpenUrl = { linkOpener.openUrl(it) },
                                onNavigateToSection = { sectionId -> onNavigateToSection(sectionId, null) },
                                onNavigateToReader = onNavigateToReader,
                                onNavigateToPosts = onNavigateToPosts,
                            )
                        }
                    }

                    item(key = "edit-home") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = { editing = true }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Edit Home")
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

    if (detailEntry != null) {
        val pick = detailEntry!!
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
            onDismiss = { detailEntry = null },
            onOpenUrl = { linkOpener.openUrl(it) },
            onCopyLink = { url ->
                detailEntry = null
                pasteDecoder.linkText(url, PasteAction.COPY) { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", text))
                    onShowSnackbar(if ('\n' in text) "Links copied to clipboard" else "Link copied to clipboard")
                }
            },
            onShareLink = { url ->
                detailEntry = null
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
private fun VpnStatusAction(
    container: AppContainer,
    protectionPrefs: ProtectionPrefs,
) {
    val context = LocalContext.current
    val vpnState by container.vpnMonitor.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    val ipLine = rememberIpLine(
        repo = container.ipInfoRepository,
        enabled = protectionPrefs.showIpCountry,
        vpn = vpnState,
        active = expanded,
    )

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = vpnIcon(vpnState),
                contentDescription = vpnStateText(vpnState),
                tint = vpnColor(vpnState),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = vpnStateText(vpnState),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = vpnColor(vpnState),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = vpnStateHint(vpnState),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (ipLine) {
                    is IpLine.Loading -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Checking IP…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is IpLine.Text -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ipLine.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    IpLine.Hidden -> Unit
                }
            }
            DropdownMenuItem(
                text = { Text("Open VPN settings") },
                onClick = {
                    expanded = false
                    openVpnSettings(context)
                },
            )
        }
    }
}
