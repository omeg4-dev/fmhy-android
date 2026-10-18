package io.github.omeg4dev.fmhy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.omeg4dev.fmhy.ui.home.HomeScreen
import io.github.omeg4dev.fmhy.ui.home.HomeViewModel
import io.github.omeg4dev.fmhy.ui.search.SearchScreen
import io.github.omeg4dev.fmhy.ui.search.SearchViewModel
import io.github.omeg4dev.fmhy.ui.section.SectionScreen
import io.github.omeg4dev.fmhy.ui.section.SectionViewModel
import io.github.omeg4dev.fmhy.ui.settings.SettingsScreen
import io.github.omeg4dev.fmhy.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val app = context.applicationContext as FmhyApp
    val container = app.container

    val backStack = rememberNavBackStack(HomeNavKey)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(container.snackbarEvents) {
        container.snackbarEvents.collect { msg ->
            val hasAction = msg.contains("sites")
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = if (hasAction) "View" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                backStack.add(SearchNavKey())
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
        // Only hosts the snackbar: each screen's own Scaffold handles system bar insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<HomeNavKey> {
                        val homeViewModel: HomeViewModel = viewModel {
                            HomeViewModel(container.datasetStore, container.settingsRepository, container)
                        }
                        HomeScreen(
                            viewModel = homeViewModel,
                            container = container,
                            onNavigateToSearch = { backStack.add(SearchNavKey()) },
                            onNavigateToSection = { sectionId -> backStack.add(SectionNavKey(sectionId)) },
                            onNavigateToSettings = { backStack.add(SettingsNavKey) },
                            onShowSnackbar = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }

                    entry<SectionNavKey> { key ->
                        val sectionViewModel: SectionViewModel = viewModel(key = key.sectionId) {
                            SectionViewModel(key.sectionId, key.anchor, container.datasetStore)
                        }
                        SectionScreen(
                            viewModel = sectionViewModel,
                            container = container,
                            onBack = { backStack.removeLastOrNull() },
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
                            onBack = { backStack.removeLastOrNull() },
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
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }
                }
            )
        }
    }
}
