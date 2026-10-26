package io.github.omeg4dev.fmhy

import android.app.Activity
import io.github.omeg4dev.fmhy.ui.common.LocalPasteDecoder
import io.github.omeg4dev.fmhy.ui.common.PasteDecodeUi
import io.github.omeg4dev.fmhy.ui.common.rememberPasteDecoder
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import io.github.omeg4dev.fmhy.ui.shell.BrowserBarItem
import io.github.omeg4dev.fmhy.ui.shell.BrowserMenuSheet
import io.github.omeg4dev.fmhy.ui.shell.StatusNoticeOverlay
import io.github.omeg4dev.fmhy.ui.shell.AppBottomBar
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.nav.contentPaddingDp
import io.github.omeg4dev.fmhy.nav.metrics
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf

import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.omeg4dev.fmhy.data.HomePrefs
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
import io.github.omeg4dev.fmhy.ui.common.LocalAppContainer
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
import io.github.omeg4dev.fmhy.ui.settings.AdBlockingPage
import io.github.omeg4dev.fmhy.ui.settings.AppearancePage
import io.github.omeg4dev.fmhy.ui.settings.AppUpdatesPage
import io.github.omeg4dev.fmhy.ui.settings.BackupPage
import io.github.omeg4dev.fmhy.ui.settings.BrowserPage
import io.github.omeg4dev.fmhy.ui.settings.DataUsagePage
import io.github.omeg4dev.fmhy.ui.settings.DownloadsPage
import io.github.omeg4dev.fmhy.ui.settings.FollowingScreen
import io.github.omeg4dev.fmhy.ui.settings.HomePage
import io.github.omeg4dev.fmhy.ui.settings.ListsPage
import io.github.omeg4dev.fmhy.ui.settings.NavRow
import io.github.omeg4dev.fmhy.ui.settings.NotificationsPage
import io.github.omeg4dev.fmhy.ui.settings.PrivacyPage
import io.github.omeg4dev.fmhy.ui.settings.ProtectionPage
import io.github.omeg4dev.fmhy.ui.settings.ProvideSettingFocus
import io.github.omeg4dev.fmhy.ui.settings.ReportPage
import io.github.omeg4dev.fmhy.ui.settings.SettingsHub
import io.github.omeg4dev.fmhy.ui.settings.SettingsViewModel
import io.github.omeg4dev.fmhy.ui.settings.SyncPage
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

    val pagerState = rememberPagerState(initialPage = initialTab.ordinal) { Tab.entries.size }
    LaunchedEffect(nav.current) {
        val targetIndex = nav.current.ordinal
        if (pagerState.currentPage != targetIndex) {
            if (kotlin.math.abs(pagerState.currentPage - targetIndex) > 1) {
                pagerState.scrollToPage(targetIndex)
            } else {
                pagerState.animateScrollToPage(targetIndex)
            }
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            val targetTab = Tab.entries[settled]
            if (targetTab != nav.current) {
                nav.select(targetTab)
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val linkOpener = rememberLinkOpener(container, onShowSnackbar = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }, nav = nav)
    val pasteDecoder = rememberPasteDecoder(container)

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

    val appearance by container.settingsRepository.appearance.collectAsStateWithLifecycle(initialValue = Appearance())
    val barMetrics = remember(appearance.bottomBarStyle) { appearance.bottomBarStyle.metrics() }
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarPadding = barMetrics.contentPaddingDp().dp + navBarInset

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

    val homePrefs by container.settingsRepository.homePrefs.collectAsStateWithLifecycle(initialValue = HomePrefs())
    val onboardingVersion by container.settingsRepository.onboardingVersion.collectAsStateWithLifecycle(initialValue = -1)
    val showOnboarding = onboardingVersion in 0 until ONBOARDING_VERSION

    val barHaptics = rememberHaptics()
    val browserTabCount by container.tabManager.browserTabCount.collectAsStateWithLifecycle()
    var showBrowserMenu by rememberSaveable { mutableStateOf(false) }

    val showsBarScreen = !showOnboarding &&
        currentTopKey !is SettingsNavKey &&
        currentTopKey !is SettingsPageNavKey &&
        currentTopKey !is LegendNavKey &&
        currentTopKey !is SiteSettingsNavKey &&
        currentTopKey !is ReaderNavKey &&
        currentTopKey !is FollowingNavKey
    val shouldShowBar = isBarVisible && showsBarScreen
    val swipeEnabled = homePrefs.swipeBetweenScreens && showsBarScreen && nav.currentStack.size == 1

    val highlightedTab = if (pagerState.isScrollInProgress) {
        Tab.entries.getOrElse(pagerState.targetPage) { nav.current }
    } else {
        nav.current
    }

    val systemGestures = WindowInsets.systemGestures
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var gutterDown by remember { mutableStateOf(false) }

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
                onNavigateToSetting = { page, id -> nav.push(SettingsPageNavKey(page, focusSetting = id)) },
                onNavigateToFollowing = { nav.push(FollowingNavKey) },
                onNavigateToWhatsNew = { nav.push(WhatsNewNavKey()) },
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
            ProvideSettingFocus(key.focusSetting) {
                when (key.page) {
                    SettingsPage.APPEARANCE -> AppearancePage(
                        viewModel = settingsViewModel,
                        onBack = { if (!nav.back()) activity?.finish() },
                    )
                    SettingsPage.HOME -> HomePage(
                        viewModel = settingsViewModel,
                        onBack = { if (!nav.back()) activity?.finish() },
                    )
                    SettingsPage.LISTS -> ListsPage(
                        viewModel = settingsViewModel,
                        onBack = { if (!nav.back()) activity?.finish() },
                        onNavigateToLegend = { nav.push(LegendNavKey) },
                    )
                    SettingsPage.BROWSER -> BrowserPage(
                        viewModel = settingsViewModel,
                        onBack = { if (!nav.back()) activity?.finish() },
                        onNavigateToSiteSettings = { nav.push(SiteSettingsNavKey) },
                    )
                    SettingsPage.VIDEO -> VideoPage(
                        viewModel = settingsViewModel,
                        onBack = { if (!nav.back()) activity?.finish() },
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                    )
                    SettingsPage.DOWNLOADS -> DownloadsPage(
                        viewModel = settingsViewModel,
                        onBack = { if (!nav.back()) activity?.finish() },
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                    )
                    SettingsPage.AD_BLOCKING -> AdBlockingPage(
                        viewModel = settingsViewModel,
                        onBack = { if (!nav.back()) activity?.finish() },
                    )
                    SettingsPage.PROTECTION -> ProtectionPage(
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
                    )
                    SettingsPage.APP_UPDATES -> AppUpdatesPage(
                        viewModel = settingsViewModel,
                        onBack = { if (!nav.back()) activity?.finish() },
                    )
                    SettingsPage.SYNC -> SyncPage(
                        viewModel = settingsViewModel,
                        onBack = { if (!nav.back()) activity?.finish() },
                    )
                    SettingsPage.DATA_USAGE -> DataUsagePage(
                        viewModel = settingsViewModel,
                        onBack = { if (!nav.back()) activity?.finish() },
                    )
                    SettingsPage.BACKUP -> BackupPage(
                        viewModel = settingsViewModel,
                        onBack = { if (!nav.back()) activity?.finish() },
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                    )
                    SettingsPage.REPORT -> ReportPage(
                        viewModel = settingsViewModel,
                        onBack = { if (!nav.back()) activity?.finish() },
                        onReportProblem = {
                            container.openReport(
                                io.github.omeg4dev.fmhy.report.ReportRequest(
                                    defaultArea = io.github.omeg4dev.fmhy.report.ReportArea.OTHER,
                                    pageUrl = null,
                                    isPrivateTab = false,
                                    crash = null,
                                )
                            )
                        },
                    )
                    SettingsPage.ABOUT -> AboutPage(
                        viewModel = settingsViewModel,
                        container = container,
                        onBack = { if (!nav.back()) activity?.finish() },
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                    )
                }
            }
        }

        entry<WhatsNewNavKey> { key ->
            val whatsNewViewModel: io.github.omeg4dev.fmhy.ui.whatsnew.WhatsNewViewModel = viewModel(key = "whatsnew-${key.filter}-${key.type}-${key.period}") {
                io.github.omeg4dev.fmhy.ui.whatsnew.WhatsNewViewModel(
                    initialFilter = key.filter,
                    initialType = key.type,
                    initialPeriod = key.period,
                    container = container,
                )
            }
            io.github.omeg4dev.fmhy.ui.whatsnew.WhatsNewScreen(
                viewModel = whatsNewViewModel,
                container = container,
                onBack = { if (!nav.back()) activity?.finish() },
                onShowSnackbar = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                },
                startInHub = key.filter == "all" && key.type == null && key.period == null,
                onNavigateToFollowing = { nav.push(FollowingNavKey) },
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

        entry<FollowingNavKey> {
            FollowingScreen(
                userStore = container.userStore,
                datasetStore = container.datasetStore,
                onBack = { if (!nav.back()) activity?.finish() },
            )
        }
    }

    CompositionLocalProvider(
        LocalAppNav provides nav,
        LocalAppNavOrNull provides nav,
        LocalBottomBarPadding provides bottomBarPadding,
        LocalBottomBarController provides bottomBarController,
        LocalCrossLinks provides crossLinks,
        LocalPasteDecoder provides pasteDecoder,
        LocalBookmarks provides bookmarksState,
        LocalDatasetStore provides container.datasetStore,
        LocalUserStore provides container.userStore,
        LocalAppContainer provides container,
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
                // Content box with hide-on-scroll connection and system-gesture gutter detection
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                        // Ignore drags starting within system gesture insets at left/right edges
                        .pointerInput(systemGestures, density, layoutDirection) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val leftGutter = systemGestures.getLeft(density, layoutDirection)
                                val rightGutter = systemGestures.getRight(density, layoutDirection)
                                val inGutter = down.position.x < leftGutter || down.position.x > (size.width - rightGutter)
                                if (inGutter) {
                                    gutterDown = true
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.none { it.pressed }) break
                                        }
                                    } finally {
                                        gutterDown = false
                                    }
                                }
                            }
                        }
                ) {
                    val holder = rememberSaveableStateHolder()
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = Tab.entries.size - 1,
                        userScrollEnabled = swipeEnabled && !gutterDown,
                        key = { Tab.entries[it].name },
                        modifier = Modifier.fillMaxSize(),
                    ) { pageIndex ->
                        val tab = Tab.entries[pageIndex]
                        holder.SaveableStateProvider(tab.name) {
                            NavDisplay(
                                backStack = nav.stacks.getValue(tab),
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
                    AppBottomBar(
                        style = appearance.bottomBarStyle,
                        current = highlightedTab,
                        browserTabCount = browserTabCount,
                        onSelect = { nav.select(it) },
                        onBrowserClick = {
                            if (browserTabCount > 0) bringBrowserToFront(context) else openBrowserNewTab(context)
                        },
                        onBrowserLongClick = {
                            barHaptics.longPress()
                            showBrowserMenu = true
                        },
                    )
                }

                // Update prompt overlay above bottom bar / system insets
                if (!showOnboarding && container.updateController.enabled) {
                    io.github.omeg4dev.fmhy.update.ui.UpdatePrompt(
                        controller = container.updateController,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (shouldShowBar) bottomBarPadding else navBarInset)
                            .zIndex(1.5f),
                    )
                }

                // Sync / ad-block pills: an overlay at the top, takes no layout space
                if (!showOnboarding) {
                    StatusNoticeOverlay(
                        notices = remember(container) { container.statusNotices },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .zIndex(2f)
                            .statusBarsPadding()
                            .padding(top = TopAppBarDefaults.TopAppBarExpandedHeight),
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
        PasteDecodeUi(pasteDecoder)
        UnsafeWarningDialog(linkOpener)

        if (container.updateController.enabled) {
            io.github.omeg4dev.fmhy.update.ui.UpdateMobileDataDialog(controller = container.updateController)
            io.github.omeg4dev.fmhy.update.ui.InstallPermissionDialog(controller = container.updateController)
        }

        val firstWikiNeedsMobilePermission by container.firstWikiNeedsMobilePermission.collectAsStateWithLifecycle()
        if (firstWikiNeedsMobilePermission) {
            AlertDialog(
                onDismissRequest = { container.firstWikiNeedsMobilePermission.value = false },
                title = { Text("Wiki data needed") },
                text = { Text("FMHY needs to download its data to work. Wi-Fi only is on and you are on mobile data. Use mobile data once?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            container.firstWikiNeedsMobilePermission.value = false
                            scope.launch { container.sync(force = true) }
                        }
                    ) {
                        Text("Use mobile data")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            container.firstWikiNeedsMobilePermission.value = false
                        }
                    ) {
                        Text("Not now")
                    }
                },
            )
        }

        val reportRequest by container.reportRequest.collectAsStateWithLifecycle()
        reportRequest?.let { req ->
            io.github.omeg4dev.fmhy.report.ReportSheet(
                request = req,
                container = container,
                onDismiss = { container.reportRequest.value = null },
            )
        }
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
