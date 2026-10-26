package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.SectionLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToLegend: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val homePrefs by viewModel.homePrefs.collectAsStateWithLifecycle()
    val appearance = state.appearance

    // Website style
    val eWebsiteStyle = SettingsCatalog.listsWebsiteStyle
    val eSectionLayout = SettingsCatalog.listsSectionLayout
    val eStarredOnly = SettingsCatalog.listsStarredOnly
    // Show indexes
    val eShowIndexes = SettingsCatalog.listsShowIndexes
    val eNewBadges = SettingsCatalog.listsNewBadges
    // Swap tap and hold
    val eSwapTapAndHold = SettingsCatalog.listsSwapTapAndHold
    val eSwipeToBookmark = SettingsCatalog.listsSwipeToBookmark
    val eSwipeBetweenScreens = SettingsCatalog.listsSwipeBetweenScreens
    // Haptic feedback
    val eHapticFeedback = SettingsCatalog.listsHapticFeedback
    val eIconLegend = SettingsCatalog.listsIconLegend

    val sectionSettingIds = remember {
        listOf(
            listOf(
                eWebsiteStyle.id,
                eSectionLayout.id,
                eStarredOnly.id,
                eShowIndexes.id,
                eNewBadges.id,
            ),
            listOf(
                eSwapTapAndHold.id,
                eSwipeToBookmark.id,
                eSwipeBetweenScreens.id,
                eHapticFeedback.id,
                eIconLegend.id,
            ),
        )
    }
    val listState = rememberFocusedListState(sectionSettingIds)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lists & gestures") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Lists section
            item {
                SettingsSection(title = "Lists") {
                    SwitchRow(
                        title = eWebsiteStyle.title,
                        summary = eWebsiteStyle.description,
                        checked = appearance.websiteStyle,
                        settingId = eWebsiteStyle.id,
                        onChange = { viewModel.setWebsiteStyle(it) },
                    )
                    SettingsDivider()
                    ChoiceRow(
                        title = eSectionLayout.title,
                        options = listOf(
                            SectionLayout.CONTINUOUS to "Continuous",
                            SectionLayout.CATEGORIES to "Categories",
                        ),
                        selected = appearance.sectionLayout,
                        settingId = eSectionLayout.id,
                        onSelect = { viewModel.setSectionLayout(it) },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eStarredOnly.title,
                        summary = eStarredOnly.description,
                        checked = appearance.starredOnly,
                        settingId = eStarredOnly.id,
                        onChange = { viewModel.setStarredOnly(it) },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eShowIndexes.title,
                        summary = eShowIndexes.description,
                        checked = appearance.showIndexes,
                        settingId = eShowIndexes.id,
                        onChange = { viewModel.setShowIndexes(it) },
                    )
                    SettingsDivider()
                    ChoiceRow(
                        title = eNewBadges.title,
                        options = listOf(
                            0 to "Off",
                            3 to "3 days",
                            7 to "7 days",
                            14 to "14 days",
                        ),
                        selected = homePrefs.newBadgeDays,
                        settingId = eNewBadges.id,
                        onSelect = { days ->
                            viewModel.updateHome { it.copy(newBadgeDays = days) }
                        },
                    )
                }
            }

            // Gestures & feedback section
            item {
                SettingsSection(title = "Gestures & feedback") {
                    SwitchRow(
                        title = eSwapTapAndHold.title,
                        summary = eSwapTapAndHold.description,
                        checked = homePrefs.swapTapHold,
                        settingId = eSwapTapAndHold.id,
                        onChange = { checked ->
                            viewModel.updateHome { it.copy(swapTapHold = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eSwipeToBookmark.title,
                        summary = eSwipeToBookmark.description,
                        checked = homePrefs.swipeToBookmark,
                        settingId = eSwipeToBookmark.id,
                        onChange = { checked ->
                            viewModel.updateHome { it.copy(swipeToBookmark = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eSwipeBetweenScreens.title,
                        summary = eSwipeBetweenScreens.description,
                        checked = homePrefs.swipeBetweenScreens,
                        settingId = eSwipeBetweenScreens.id,
                        onChange = { checked ->
                            viewModel.updateHome { it.copy(swipeBetweenScreens = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eHapticFeedback.title,
                        summary = eHapticFeedback.description,
                        checked = appearance.hapticFeedback,
                        settingId = eHapticFeedback.id,
                        onChange = { viewModel.setHapticFeedback(it) },
                    )
                    SettingsDivider()
                    NavRow(
                        title = eIconLegend.title,
                        summary = eIconLegend.description,
                        settingId = eIconLegend.id,
                        onClick = onNavigateToLegend,
                    )
                }
            }

            extraRows()
        }
    }
}
