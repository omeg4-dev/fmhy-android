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
import io.github.omeg4dev.fmhy.browser.PLAYBACK_SPEEDS
import io.github.omeg4dev.fmhy.browser.speedLabel
import io.github.omeg4dev.fmhy.data.AutoplayMode
import io.github.omeg4dev.fmhy.data.FullscreenOrientation

/** Settings → Browsing → Video: fullscreen, picture-in-picture and playback options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onShowSnackbar: (String) -> Unit = {},
    extraRows: LazyListScope.() -> Unit = {},
) {
    val prefs by viewModel.browserPrefs.collectAsStateWithLifecycle()

    val eFullscreenOrientation = SettingsCatalog.videoFullscreenOrientation
    val eDoubleTapSeek = SettingsCatalog.videoDoubleTapSeek
    val eKeepScreenOn = SettingsCatalog.videoKeepScreenOn
    val ePictureInPicture = SettingsCatalog.videoPictureInPicture
    val eAutoPip = SettingsCatalog.videoAutoPip
    val eAutoplay = SettingsCatalog.videoAutoplay
    val eBackgroundPlayback = SettingsCatalog.videoBackgroundPlayback
    val eDefaultSpeed = SettingsCatalog.videoDefaultSpeed
    val eForgetSiteSpeeds = SettingsCatalog.videoForgetSiteSpeeds

    val fullscreenIds = listOf(eFullscreenOrientation.id, eDoubleTapSeek.id, eKeepScreenOn.id)
    val pipIds = listOf(ePictureInPicture.id, eAutoPip.id)
    val playbackIds = listOf(eAutoplay.id, eBackgroundPlayback.id, eDefaultSpeed.id)
    val advancedIds = listOf(eForgetSiteSpeeds.id)

    val sectionSettingIds = remember {
        listOf(fullscreenIds, pipIds, playbackIds, advancedIds)
    }
    val listState = rememberFocusedListState(sectionSettingIds)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video") },
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
            // Fullscreen section
            item {
                SettingsSection(title = "Fullscreen") {
                    ChoiceRow(
                        title = eFullscreenOrientation.title,
                        options = FullscreenOrientation.entries.map { it to it.label },
                        selected = prefs.fullscreenOrientation,
                        settingId = eFullscreenOrientation.id,
                        onSelect = { choice -> viewModel.updateBrowser { it.copy(fullscreenOrientation = choice) } },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eDoubleTapSeek.title,
                        summary = eDoubleTapSeek.description,
                        checked = prefs.doubleTapSeek,
                        settingId = eDoubleTapSeek.id,
                        onChange = { checked -> viewModel.updateBrowser { it.copy(doubleTapSeek = checked) } },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eKeepScreenOn.title,
                        summary = eKeepScreenOn.description,
                        checked = prefs.keepScreenOn,
                        settingId = eKeepScreenOn.id,
                        onChange = { checked -> viewModel.updateBrowser { it.copy(keepScreenOn = checked) } },
                    )
                }
            }

            // Picture-in-picture section
            item {
                SettingsSection(title = "Picture-in-picture") {
                    SwitchRow(
                        title = ePictureInPicture.title,
                        summary = ePictureInPicture.description,
                        checked = prefs.pictureInPicture,
                        settingId = ePictureInPicture.id,
                        onChange = { checked -> viewModel.updateBrowser { it.copy(pictureInPicture = checked) } },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eAutoPip.title,
                        summary = eAutoPip.description,
                        checked = prefs.autoPip,
                        enabled = prefs.pictureInPicture,
                        settingId = eAutoPip.id,
                        onChange = { checked -> viewModel.updateBrowser { it.copy(autoPip = checked) } },
                    )
                }
            }

            // Playback section
            item {
                SettingsSection(title = "Playback") {
                    ChoiceRow(
                        title = eAutoplay.title,
                        options = AutoplayMode.entries.map { it to it.label },
                        selected = prefs.autoplay,
                        settingId = eAutoplay.id,
                        onSelect = { mode -> viewModel.updateBrowser { it.copy(autoplay = mode) } },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eBackgroundPlayback.title,
                        summary = eBackgroundPlayback.description,
                        checked = prefs.backgroundPlayback,
                        settingId = eBackgroundPlayback.id,
                        onChange = { checked -> viewModel.updateBrowser { it.copy(backgroundPlayback = checked) } },
                    )
                    SettingsDivider()
                    ChoiceRow(
                        title = eDefaultSpeed.title,
                        options = PLAYBACK_SPEEDS.map { it to speedLabel(it) },
                        selected = prefs.defaultSpeed,
                        settingId = eDefaultSpeed.id,
                        onSelect = { speed -> viewModel.updateBrowser { it.copy(defaultSpeed = speed) } },
                    )
                }
            }

            // Advanced group
            item {
                ExpandableSection(
                    title = "Advanced",
                    settingIds = advancedIds,
                ) {
                    NavRow(
                        title = eForgetSiteSpeeds.title,
                        summary = eForgetSiteSpeeds.description,
                        settingId = eForgetSiteSpeeds.id,
                        onClick = {
                            viewModel.clearSiteSpeeds { onShowSnackbar("Per-site speeds cleared") }
                        },
                    )
                }
            }

            extraRows()
        }
    }
}
