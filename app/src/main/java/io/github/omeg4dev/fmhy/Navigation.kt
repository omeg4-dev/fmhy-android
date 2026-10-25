package io.github.omeg4dev.fmhy

import android.app.Activity
import io.github.omeg4dev.fmhy.ui.common.pasteResolverFor
import io.github.omeg4dev.fmhy.ui.common.LocalPasteResolver
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import io.github.omeg4dev.fmhy.ui.shell.BrowserBarItem
import io.github.omeg4dev.fmhy.ui.shell.BrowserMenuSheet
import io.github.omeg4dev.fmhy.ui.shell.StatusNoticeOverlay
import io.github.omeg4dev.fmhy.browser.bringBrowserToFront
import io.github.omeg4dev.fmhy.browser.openBrowserNewTab
import io.github.omeg4dev.fmhy.browser.formatRelativeTime
import io.github.omeg4dev.fmhy.data.adblock.FilterListCatalog
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.ui.settings.InfoRow
import io.github.omeg4dev.fmhy.ui.settings.NavRow
import io.github.omeg4dev.fmhy.ui.settings.SettingsGroupHeader
import io.github.omeg4dev.fmhy.ui.settings.SiteSettingsScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.omeg4dev.fmhy.browser.bringBrowserToFront
import io.github.omeg4dev.fmhy.browser.openBrowserNewTab
import io.github.omeg4dev.fmhy.nav.AppNavState
import io.github.omeg4dev.fmhy.ui.shell.BrowserMenuSheet
import io.github.omeg4dev.fmhy.shortcuts.EXTRA_ENTRY_ID
import io.github.omeg4dev.fmhy.shortcuts.EXTRA_FILTER
import io.github.omeg4dev.fmhy.shortcuts.EXTRA_HEADING_ID
import io.github.omeg4dev.fmhy.shortcuts.EXTRA_OPEN
import io.github.omeg4dev.fmhy.shortcuts.EXTRA_SECTION_ID
import io.github.omeg4dev.fmhy.shortcuts.ShortcutDestination
import io.github.omeg4dev.fmhy.shortcuts.shortcutDestination
import io.github.omeg4dev.fmhy.nav.LocalAppNav
import io.github.omeg4dev.fmhy.nav.LocalAppNavOrNull
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import io.github.omeg4dev.fmhy.nav.Tab
import io.github.omeg4dev.fmhy.ui.common.BookmarksState
import io.github.omeg4dev.fmhy.ui.common.LocalDatasetStore
import io.github.omeg4dev.fmhy.ui.common.LocalUserStore
import io.github.omeg4dev.fmhy.ui.common.LocalBookmarks
import io.github.omeg4dev.fmhy.ui.common.SavedInfo
import io.github.omeg4dev.fmhy.ui.common.LocalCrossLinks
import io.github.omeg4dev.fmhy.ui.home.HomeScreen
import io.github.omeg4dev.fmhy.ui.home.HomeViewModel
import io.github.omeg4dev.fmhy.ui.saved.SavedScreen
import io.github.omeg4dev.fmhy.ui.saved.SavedViewModel
import io.github.omeg4dev.fmhy.ui.search.SearchScreen
import io.github.omeg4dev.fmhy.ui.search.SearchViewModel
import io.github.omeg4dev.fmhy.ui.section.CategoryScreen
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.section.SectionScreen
import io.github.omeg4dev.fmhy.ui.section.SectionViewModel
import io.github.omeg4dev.fmhy.ui.legend.LegendScreen
import io.github.omeg4dev.fmhy.ui.check.CheckLinkScreen
import io.github.omeg4dev.fmhy.ui.reader.ReaderScreen
import io.github.omeg4dev.fmhy.ui.posts.PostsScreen
import io.github.omeg4dev.fmhy.ui.settings.AboutPage
import io.github.omeg4dev.fmhy.ui.settings.AppearancePage
import io.github.omeg4dev.fmhy.ui.settings.BlockingPage
import io.github.omeg4dev.fmhy.ui.settings.BrowserPage
import io.github.omeg4dev.fmhy.ui.settings.DataPage
import io.github.omeg4dev.fmhy.ui.settings.HomeListsPage
import io.github.omeg4dev.fmhy.ui.settings.NavRow
import io.github.omeg4dev.fmhy.ui.settings.NotificationsPage
import io.github.omeg4dev.fmhy.ui.settings.PrivacyPage
import io.github.omeg4dev.fmhy.ui.settings.ProtectionPage
import io.github.omeg4dev.fmhy.ui.settings.SavedPage
import io.github.omeg4dev.fmhy.ui.settings.SettingsHub
import io.github.omeg4dev.fmhy.ui.settings.SettingsViewModel
import io.github.omeg4dev.fmhy.ui.settings.VideoPage
import io.github.omeg4dev.fmhy.ui.onboarding.ONBOARDING_VERSION
import io.github.omeg4dev.fmhy.ui.onboarding.OnboardingScreen
import android.content.Intent
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.NoteSheet
import io.github.omeg4dev.fmhy.nav.resolveFmhyUri
import io.github.omeg4dev.fmhy.nav.FmhyLinkDestination
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

fun interface BottomBarController {
    fun show()
}

val LocalBottomBarController = staticCompositionLocalOf { BottomBarController {} }

@Composable
fun MainNavigation(intentFlow: SharedFlow<Intent>? = null) {
    val context = LocalContext.current
    val app = context.applicationContext as FmhyApp
    val container = app.container
    val activity = context as? Activity

    val browseStack = rememberNavBackStack(HomeNavKey)
    val searchStack = rememberNavBackStack(SearchNavKey())
    val savedStack = rememberNavBackStack(SavedNavKey)

    var savedTabName by rememberSaveable { mutableStateOf(Tab.BROWSE.name) }
    val initialTab = remember {
        runCatching { Tab.valueOf(savedTabName) }.getOrDefault(Tab.BROWSE)
    }
    val nav = remember {
        AppNavState(
            stacks = mapOf(
                Tab.BROWSE to browseStack,
                Tab.SEARCH to searchStack,
                Tab.SAVED to savedStack,
            ),
            initial = initialTab,
        )
    }
    LaunchedEffect(nav.current) {
        savedTabName = nav.current.name
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val linkOpener = rememberLinkOpener(container, onShowSnackbar = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }, nav = nav)

    if (intentFlow != null) {
        LaunchedEffect(intentFlow) {
            intentFlow.collect { incomingIntent ->
                val checkLink = incomingIntent.getStringExtra(EXTRA_CHECK_LINK_INITIAL)
                if (!checkLink.isNullOrEmpty()) {
                    nav.select(Tab.BROWSE)
                    nav.push(CheckLinkNavKey(checkLink))
                    return@collect
                }

                val shortcut = shortcutDestination(
                    open = incomingIntent.getStringExtra(EXTRA_OPEN),
                    filter = incomingIntent.getStringExtra(EXTRA_FILTER),
                    entryId = incomingIntent.getStringExtra(EXTRA_ENTRY_ID),
                    sectionId = incomingIntent.getStringExtra(EXTRA_SECTION_ID),
                    headingId = incomingIntent.getStringExtra(EXTRA_HEADING_ID),
                )
                if (shortcut != null) {
                    // Handle each intent once: a replayed intent (rotation, re-collect) must not re-navigate.
                    incomingIntent.removeExtra(EXTRA_OPEN)
                    when (shortcut) {
                        ShortcutDestination.Search -> {
                            nav.select(Tab.SEARCH)
                            nav.requestSearchFocus()
                        }
                        ShortcutDestination.Saved -> nav.select(Tab.SAVED)
                        is ShortcutDestination.WhatsNew -> {
                            nav.select(Tab.BROWSE)
                            nav.push(WhatsNewNavKey(shortcut.filter))
                        }
                        ShortcutDestination.Posts -> {
                            nav.select(Tab.BROWSE)
                            nav.push(PostsNavKey)
                        }
                        ShortcutDestination.CheckLink -> {
                            nav.select(Tab.BROWSE)
                            nav.push(CheckLinkNavKey())
                        }
                        is ShortcutDestination.Random -> {
                            val store = container.datasetStore
                            val entry = runCatching {
                                shortcut.entryId?.let { store.entry(it) } ?: store.randomTopPick()
                            }.getOrNull()
                            if (entry != null) {
                                navigateToEntry(store, nav, entry)
                            }
                        }
                        is ShortcutDestination.Entry -> {
                            val store = container.datasetStore
                            val entry = runCatching { store.entry(shortcut.entryId) }.getOrNull()
                            if (entry != null) {
                                navigateToEntry(store, nav, entry)
                            } else if (shortcut.sectionId != null) {
                                val anchor = runCatching {
                                    shortcut.headingId?.let { hid ->
                                        store.headings(shortcut.sectionId).firstOrNull { it.id == hid }?.anchor
                                    }
                                }.getOrNull()
                                nav.select(Tab.BROWSE)
                                nav.push(SectionNavKey(shortcut.sectionId, anchor = anchor))
                            }
                        }
                        is ShortcutDestination.Pin -> {
                            nav.select(Tab.BROWSE)
                            nav.push(SectionNavKey(shortcut.sectionId, shortcut.headingId.ifEmpty { null }))
                        }
                    }
                    return@collect
                }

                if (incomingIntent.action == Intent.ACTION_VIEW) {
                    val data = incomingIntent.dataString
                    if (!data.isNullOrEmpty()) {
                        val cross = if (container.datasetStore.isAvailable) {
                            container.crossLinks.first { it != null }
                        } else null

                        when (val dest = resolveFmhyUri(data, cross)) {
                            is FmhyLinkDestination.Navigate -> {
                                nav.select(Tab.BROWSE)
                                if (dest.navKey is HomeNavKey) {
                                    if (browseStack.lastOrNull() != HomeNavKey) {
                                        nav.push(HomeNavKey)
                                    }
                                } else {
                                    nav.push(dest.navKey)
                                }
                            }
                            is FmhyLinkDestination.OpenBrowser -> {
                                linkOpener.openUrl(dest.url)
                            }
                            null -> {
                                linkOpener.openUrl(data)
                            }
                        }
                    }
                }
            }
        }
    }

    val crossLinks by container.crossLinks.collectAsStateWithLifecycle()
    val savedIds by container.bookmarks.savedIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val bookmarksList by container.bookmarks.bookmarks.collectAsStateWithLifecycle(initialValue = emptyList())
    val foldersList by container.bookmarks.folders.collectAsStateWithLifecycle(initialValue = emptyList())

    var sectionTitles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(container.datasetStore.changes) {
        if (container.datasetStore.isAvailable) {
            try {
                sectionTitles = container.datasetStore.sections().associate { it.id to it.title }
            } catch (_: Exception) {}
        }
    }

    val bookmarksState = remember(savedIds, bookmarksList, foldersList, sectionTitles) {
        val folderMap = foldersList.associate { it.id to it.name }
        val bookmarkMap = bookmarksList.associateBy { it.entryId }
        BookmarksState(
            savedIds = savedIds,
            toggle = { entry ->
                scope.launch {
                    val title = sectionTitles[entry.sectionId] ?: entry.headingPath
                    container.bookmarks.toggle(entry, title)
                }
            },
            folders = foldersList,
            savedInfo = { entryId ->
                val b = bookmarkMap[entryId] ?: return@BookmarksState null
                SavedInfo(
                    folderId = b.folderId,
                    folderName = b.folderId?.let { folderMap[it] },
                    note = b.note,
                )
            },
            setNote = { entryId, note ->
                scope.launch {
                    container.bookmarks.setNote(entryId, note)
                }
            },
            moveToFolder = { entryId, folderId ->
                scope.launch {
                    container.bookmarks.moveToFolder(entryId, folderId)
                }
            },
            createFolder = { name, onCreated ->
                scope.launch {
                    val f = container.bookmarks.createFolder(name)
                    onCreated(f)
                }
            }
        )
    }

    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarPadding = 80.dp + navBarInset

    LaunchedEffect(container.snackbarEvents) {
        container.snackbarEvents.collect { msg ->
            val hasAction = msg.contains("sites")
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = if (hasAction) "View" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                nav.select(Tab.SEARCH)
            }
        }
    }

    LaunchedEffect(container.navigationEvents) {
        container.navigationEvents.collect { key ->
            nav.select(Tab.BROWSE)
            nav.push(key)
        }
    }

    var isBarVisible by remember { mutableStateOf(true) }
    val bottomBarController = remember {
        BottomBarController {
            isBarVisible = true
        }
    }
    val currentTopKey = nav.currentStack.lastOrNull()
    LaunchedEffect(nav.current, currentTopKey) {
        isBarVisible = true
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -8f) {
                    isBarVisible = false
                } else if (available.y > 8f) {
                    isBarVisible = true
                }
                return Offset.Zero
            }
        }
    }

    val onboardingVersion by container.settingsRepository.onboardingVersion.collectAsStateWithLifecycle(initialValue = -1)
    val showOnboarding = onboardingVersion in 0 until ONBOARDING_VERSION

    val barHaptics = rememberHaptics()
    val browserTabCount by container.tabManager.browserTabCount.collectAsStateWithLifecycle()
    var showBrowserMenu by rememberSaveable { mutableStateOf(false) }

    val shouldShowBar = isBarVisible && !showOnboarding && currentTopKey !is SettingsNavKey && currentTopKey !is SettingsPageNavKey && currentTopKey !is LegendNavKey && currentTopKey !is SiteSettingsNavKey && currentTopKey !is ReaderNavKey

    BackHandler(enabled = nav.current != Tab.BROWSE && nav.currentStack.size == 1) {
        nav.back()
    }

    val sharedEntryProvider = entryProvider {
        entry<HomeNavKey> {
            val homeViewModel: HomeViewModel = viewModel {
                HomeViewModel(container.datasetStore, container.settingsRepository, container)
            }
            HomeScreen(
                viewModel = homeViewModel,
                container = container,
                onNavigateToSearch = {
                    nav.select(Tab.SEARCH)
                    nav.requestSearchFocus()
                },
                onNavigateToSection = { sectionId, anchor -> nav.push(SectionNavKey(sectionId, anchor)) },
                onNavigateToSettings = { nav.push(SettingsNavKey) },
                onNavigateToCheckLink = { nav.push(CheckLinkNavKey()) },
                onNavigateToReader = { path, title -> nav.push(ReaderNavKey(path, title)) },
                onNavigateToPosts = { nav.push(PostsNavKey) },
                onShowSnackbar = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            )
        }

        entry<SectionNavKey> { key ->
            val sectionViewModel: SectionViewModel = viewModel(key = key.sectionId) {
                SectionViewModel(key.sectionId, key.anchor, container.datasetStore, container.settingsRepository)
            }
            SectionScreen(
                viewModel = sectionViewModel,
                key = key,
                container = container,
                onBack = { if (!nav.back()) activity?.finish() },
                onShowSnackbar = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            )
        }

        entry<CategoryNavKey> { key ->
            val sectionViewModel: SectionViewModel = viewModel(key = key.sectionId) {
                SectionViewModel(key.sectionId, null, container.datasetStore, container.settingsRepository)
            }
            CategoryScreen(
                key = key,
                viewModel = sectionViewModel,
                container = container,
                onBack = { if (!nav.back()) activity?.finish() },
                onShowSnackbar = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            )
        }

        entry<SearchNavKey> { key ->
            val searchViewModel: SearchViewModel = viewModel {
                SearchViewModel(
                    key.initialQuery,
                    container.datasetStore,
                    container.searchEngine,
                    container.settingsRepository,
                    container.safeGuard,
                )
            }
            SearchScreen(
                viewModel = searchViewModel,
                container = container,
                showBack = nav.current != Tab.SEARCH || nav.currentStack.size > 1,
                onBack = { if (!nav.back()) activity?.finish() },
                onShowSnackbar = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            )
        }

        entry<SettingsNavKey> {
            val settingsViewModel: SettingsViewModel = viewModel {
                SettingsViewModel(
                    container.datasetStore,
                    container.settingsRepository,
                    container,
                )
            }
            SettingsHub(
                viewModel = settingsViewModel,
                onBack = { if (!nav.back()) activity?.finish() },
                onNavigateToPage = { page -> nav.push(SettingsPageNavKey(page)) },
            )
        }

        entry<SettingsPageNavKey> { key ->
            val settingsViewModel: SettingsViewModel = viewModel {
                SettingsViewModel(
                    container.datasetStore,
                    container.settingsRepository,
                    container,
                )
            }
            when (key.page) {
                SettingsPage.APPEARANCE -> AppearancePage(
                    viewModel = settingsViewModel,
                    onBack = { if (!nav.back()) activity?.finish() },
                )
                SettingsPage.HOME_LISTS -> HomeListsPage(
                    viewModel = settingsViewModel,
                    onBack = { if (!nav.back()) activity?.finish() },
                    onNavigateToLegend = { nav.push(LegendNavKey) },
                )
                SettingsPage.BROWSER -> BrowserPage(
                    viewModel = settingsViewModel,
                    onBack = { if (!nav.back()) activity?.finish() },
                    onNavigateToPage = { page -> nav.push(SettingsPageNavKey(page)) },
                    onNavigateToSiteSettings = { nav.push(SiteSettingsNavKey) },
                )
                SettingsPage.VIDEO -> VideoPage(
                    viewModel = settingsViewModel,
                    onBack = { if (!nav.back()) activity?.finish() },
                    onShowSnackbar = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                )
                SettingsPage.BLOCKING,
                SettingsPage.PROTECTION -> BlockingPage(
                    viewModel = settingsViewModel,
                    onBack = { if (!nav.back()) activity?.finish() },
                )
                SettingsPage.PRIVACY -> PrivacyPage(
                    viewModel = settingsViewModel,
                    onBack = { if (!nav.back()) activity?.finish() },
                    onShowSnackbar = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                )
                SettingsPage.NOTIFICATIONS -> NotificationsPage(
                    viewModel = settingsViewModel,
                    onBack = { if (!nav.back()) activity?.finish() },
                    onShowSnackbar = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    onNavigateToWhatsNew = { nav.push(WhatsNewNavKey()) },
                )
                SettingsPage.DATA -> DataPage(
                    viewModel = settingsViewModel,
                    onBack = { if (!nav.back()) activity?.finish() },
                )
                SettingsPage.SAVED -> SavedPage(
                    viewModel = settingsViewModel,
                    onBack = { if (!nav.back()) activity?.finish() },
                    onShowSnackbar = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                )
                SettingsPage.ABOUT -> AboutPage(
                    viewModel = settingsViewModel,
                    onBack = { if (!nav.back()) activity?.finish() },
                )
            }
        }

        entry<WhatsNewNavKey> { key ->
            val whatsNewViewModel: io.github.omeg4dev.fmhy.ui.whatsnew.WhatsNewViewModel = viewModel(key = "whatsnew-${key.filter}-${key.type}") {
                io.github.omeg4dev.fmhy.ui.whatsnew.WhatsNewViewModel(key.filter, key.type, container)
            }
            io.github.omeg4dev.fmhy.ui.whatsnew.WhatsNewScreen(
                viewModel = whatsNewViewModel,
                container = container,
                onBack = { if (!nav.back()) activity?.finish() },
                onShowSnackbar = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                },
                startInHub = key.filter == "all" && key.type == null,
            )
        }

        entry<LegendNavKey> {
            LegendScreen(
                onBack = { if (!nav.back()) activity?.finish() },
            )
        }

        entry<SiteSettingsNavKey> {
            SiteSettingsScreen(
                userStore = container.userStore,
                settingsRepository = container.settingsRepository,
                onBack = { if (!nav.back()) activity?.finish() },
            )
        }

        entry<SavedNavKey> {
            val savedViewModel: SavedViewModel = viewModel {
                SavedViewModel(
                    container.datasetStore,
                    container.bookmarks,
                    container.userStore,
                    container.settingsRepository,
                )
            }
            SavedScreen(
                viewModel = savedViewModel,
                container = container,
                onShowSnackbar = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            )
        }

        entry<CheckLinkNavKey> { key ->
            CheckLinkScreen(
                initialQuery = key.initial,
                container = container,
                onBack = { if (!nav.back()) activity?.finish() },
                onShowSnackbar = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            )
        }

        entry<ReaderNavKey> { key ->
            ReaderScreen(
                path = key.path,
                title = key.title,
                container = container,
                onBack = { if (!nav.back()) activity?.finish() },
                onShowSnackbar = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            )
        }

        entry<PostsNavKey> {
            PostsScreen(
                container = container,
                onBack = { if (!nav.back()) activity?.finish() },
                onNavigateToReader = { path, title ->
                    nav.push(ReaderNavKey(path, title))
                },
                onShowSnackbar = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            )
        }
    }

    CompositionLocalProvider(
        LocalAppNav provides nav,
        LocalAppNavOrNull provides nav,
        LocalBottomBarPadding provides bottomBarPadding,
        LocalBottomBarController provides bottomBarController,
        LocalCrossLinks provides crossLinks,
        LocalPasteResolver provides remember(container) { pasteResolverFor(container) },
        LocalBookmarks provides bookmarksState,
        LocalDatasetStore provides container.datasetStore,
        LocalUserStore provides container.userStore,
    ) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = if (shouldShowBar) bottomBarPadding else 0.dp),
                )
            },
            modifier = Modifier.fillMaxSize(),
            // Only hosts the snackbar: each screen's own Scaffold handles system bar insets.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Content box with hide-on-scroll connection
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                ) {
                    val holder = rememberSaveableStateHolder()
                    AnimatedContent(
                        targetState = nav.current,
                        transitionSpec = {
                            fadeIn(tween(150)) togetherWith fadeOut(tween(90))
                        },
                        label = "tab_switch",
                    ) { targetTab ->
                        holder.SaveableStateProvider(targetTab.name) {
                            NavDisplay(
                                backStack = nav.stacks.getValue(targetTab),
                                onBack = { if (!nav.back()) activity?.finish() },
                                transitionSpec = {
                                    slideInHorizontally(tween(220)) { width -> width / 6 } + fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                                },
                                popTransitionSpec = {
                                    fadeIn(tween(220)) togetherWith slideOutHorizontally(tween(220)) { width -> width / 6 } + fadeOut(tween(180))
                                },
                                predictivePopTransitionSpec = { _ ->
                                    fadeIn(tween(220)) togetherWith slideOutHorizontally(tween(220)) { width -> width / 6 } + fadeOut(tween(180))
                                },
                                entryProvider = sharedEntryProvider,
                            )
                        }
                    }
                }

                // Bottom navigation bar overlaying content
                AnimatedVisibility(
                    visible = shouldShowBar,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    ) { it },
                    exit = slideOutVertically(
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    ) { it },
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        NavigationBarItem(
                            selected = nav.current == Tab.BROWSE,
                            onClick = {
                                nav.select(Tab.BROWSE)
                            },
                            icon = {
                                Icon(
                                    imageVector = if (nav.current == Tab.BROWSE) Icons.Filled.Explore else Icons.Outlined.Explore,
                                    contentDescription = "Browse",
                                )
                            },
                            label = { Text("Browse") },
                            alwaysShowLabel = true,
                        )
                        NavigationBarItem(
                            selected = nav.current == Tab.SEARCH,
                            onClick = {
                                nav.select(Tab.SEARCH)
                            },
                            icon = {
                                Icon(
                                    imageVector = if (nav.current == Tab.SEARCH) Icons.Filled.Search else Icons.Outlined.Search,
                                    contentDescription = "Search",
                                )
                            },
                            label = { Text("Search") },
                            alwaysShowLabel = true,
                        )
                        BrowserBarItem(
                            tabCount = browserTabCount,
                            onClick = {
                                if (browserTabCount > 0) bringBrowserToFront(context) else openBrowserNewTab(context)
                            },
                            onLongClick = {
                                barHaptics.longPress()
                                showBrowserMenu = true
                            },
                        )
                        NavigationBarItem(
                            selected = nav.current == Tab.SAVED,
                            onClick = {
                                nav.select(Tab.SAVED)
                            },
                            icon = {
                                Icon(
                                    imageVector = if (nav.current == Tab.SAVED) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Saved",
                                )
                            },
                            label = { Text("Saved") },
                            alwaysShowLabel = true,
                        )
                    }
                }

                // Sync / ad-block pills: an overlay at the top, takes no layout space
                if (!showOnboarding) {
                    StatusNoticeOverlay(
                        notices = remember(container) { container.statusNotices },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(2f)
                            .statusBarsPadding()
                            .padding(top = 8.dp),
                    )
                }

                if (showBrowserMenu && !showOnboarding) {
                    val menuTabs by container.tabManager.tabs.collectAsStateWithLifecycle()
                    BrowserMenuSheet(
                        tabManager = container.tabManager,
                        tabs = menuTabs,
                        count = browserTabCount,
                        onDismiss = { showBrowserMenu = false },
                    )
                }

                // Onboarding full screen overlay
                if (showOnboarding) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        OnboardingScreen(
                            container = container,
                            onFinished = {},
                        )
                    }
                }
            }
        }
        UnsafeWarningDialog(linkOpener)
    }
}

private suspend fun navigateToEntry(store: DatasetStore, nav: AppNavState, entry: Entry) {
    val anchor = runCatching {
        entry.headingId?.let { hid ->
            store.headings(entry.sectionId).firstOrNull { it.id == hid }?.anchor
        }
    }.getOrNull()
    nav.select(Tab.BROWSE)
    nav.push(SectionNavKey(entry.sectionId, anchor = anchor, focusEntryId = entry.id))
}
