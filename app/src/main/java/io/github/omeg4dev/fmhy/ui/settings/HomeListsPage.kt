package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.SectionOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeListsPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToLegend: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val homePrefs by viewModel.homePrefs.collectAsStateWithLifecycle()
    val appearance = state.appearance

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home & lists") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            // Group: Home
            item {
                SettingsGroupHeader(text = "Home")
            }

            item {
                SwitchRow(
                    title = "Personal Home",
                    summary = "Pin categories and hide sections you don't use",
                    checked = homePrefs.personalHome,
                    onChange = { vmVal ->
                        viewModel.updateHome { it.copy(personalHome = vmVal) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Show random pick",
                    summary = "A random recommended site on the Browse tab",
                    checked = appearance.showRandomPick,
                    onChange = { viewModel.setShowRandomPick(it) },
                )
            }

            item {
                ChoiceRow(
                    title = "Section order",
                    options = listOf(
                        SectionOrder.WEBSITE to "Website",
                        SectionOrder.ALPHABETICAL to "A–Z",
                    ),
                    selected = appearance.sectionOrder,
                    onSelect = { viewModel.setSectionOrder(it) },
                )
            }

            // Group: Lists
            item {
                SettingsGroupHeader(text = "Lists")
            }

            item {
                ChoiceRow(
                    title = "Section layout",
                    options = listOf(
                        SectionLayout.CONTINUOUS to "Continuous",
                        SectionLayout.CATEGORIES to "Categories",
                    ),
                    selected = appearance.sectionLayout,
                    onSelect = { viewModel.setSectionLayout(it) },
                )
            }

            item {
                SwitchRow(
                    title = "Starred only",
                    summary = "Show only sites FMHY recommends (⭐ / 🌟)",
                    checked = appearance.starredOnly,
                    onChange = { viewModel.setStarredOnly(it) },
                )
            }

            item {
                SwitchRow(
                    title = "Show indexes",
                    summary = "Include 🌐 index sites that list other sites",
                    checked = appearance.showIndexes,
                    onChange = { viewModel.setShowIndexes(it) },
                )
            }

            item {
                ChoiceRow(
                    title = "NEW badges",
                    options = listOf(
                        0 to "Off",
                        3 to "3 days",
                        7 to "7 days",
                        14 to "14 days",
                    ),
                    selected = homePrefs.newBadgeDays,
                    onSelect = { days ->
                        viewModel.updateHome { it.copy(newBadgeDays = days) }
                    },
                )
            }

            item {
                NavRow(
                    title = "Icon legend",
                    summary = "What every icon means",
                    onClick = onNavigateToLegend,
                )
            }

            extraRows()
        }
    }
}
