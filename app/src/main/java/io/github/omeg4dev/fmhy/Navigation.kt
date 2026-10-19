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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import io.github.omeg4dev.fmhy.nav.AppNavState
import io.github.omeg4dev.fmhy.nav.LocalAppNav
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import io.github.omeg4dev.fmhy.nav.Tab
import io.github.omeg4dev.fmhy.ui.common.BookmarksState
import io.github.omeg4dev.fmhy.ui.common.LocalBookmarks
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
import io.github.omeg4dev.fmhy.ui.settings.SettingsScreen
import io.github.omeg4dev.fmhy.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

fun interface BottomBarController {
    fun show()
}

val LocalBottomBarController = staticCompositionLocalOf { BottomBarController {} }

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val app = context.applicationContext as FmhyApp
    val container = app.container
    val activity = context as? Activity
    val haptics = rememberHaptics()

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

    val crossLinks by container.crossLinks.collectAsStateWithLifecycle()
    val savedIds by container.bookmarks.savedIds.collectAsStateWithLifecycle(initialValue = emptySet())

    var sectionTitles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(container.datasetStore.changes) {
        if (container.datasetStore.isAvailable) {
            try {
                sectionTitles = container.datasetStore.sections().associate { it.id to it.title }
            } catch (_: Exception) {}
        }
    }

    val bookmarksState = remember(savedIds, sectionTitles) {
        BookmarksState(
            savedIds = savedIds,
            toggle = { entry ->
                scope.launch {
                    val title = sectionTitles[entry.sectionId] ?: entry.headingPath
                    container.bookmarks.toggle(entry, title)
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

    val shouldShowBar = isBarVisible && currentTopKey !is SettingsNavKey && currentTopKey !is LegendNavKey

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
                onNavigateToSection = { sectionId -> nav.push(SectionNavKey(sectionId)) },
                onNavigateToSettings = { nav.push(SettingsNavKey) },
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
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { if (!nav.back()) activity?.finish() },
                onNavigateToLegend = { nav.push(LegendNavKey) },
            )
        }

        entry<LegendNavKey> {
            LegendScreen(
                onBack = { if (!nav.back()) activity?.finish() },
            )
        }

        entry<SavedNavKey> {
            val savedViewModel: SavedViewModel = viewModel {
                SavedViewModel(
                    container.datasetStore,
                    container.bookmarks,
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
    }

    CompositionLocalProvider(
        LocalAppNav provides nav,
        LocalBottomBarPadding provides bottomBarPadding,
        LocalBottomBarController provides bottomBarController,
        LocalCrossLinks provides crossLinks,
        LocalPasteResolver provides remember(container) { pasteResolverFor(container) },
        LocalBookmarks provides bookmarksState,
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
                                haptics.tick()
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
                                haptics.tick()
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
                        NavigationBarItem(
                            selected = nav.current == Tab.SAVED,
                            onClick = {
                                haptics.tick()
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
            }
        }
    }
}
