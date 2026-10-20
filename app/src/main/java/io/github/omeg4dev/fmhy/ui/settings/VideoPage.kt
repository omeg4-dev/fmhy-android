package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsSection(title = "Fullscreen") {
                    ChoiceRow(
                        title = "Fullscreen orientation",
                        options = FullscreenOrientation.entries.map { it to it.label },
                        selected = prefs.fullscreenOrientation,
                        onSelect = { choice -> viewModel.updateBrowser { it.copy(fullscreenOrientation = choice) } },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Double-tap to seek",
                        summary = "Double-tap the left or right side of a fullscreen video to skip 10 seconds",
                        checked = prefs.doubleTapSeek,
                        onChange = { checked -> viewModel.updateBrowser { it.copy(doubleTapSeek = checked) } },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Keep screen on while playing",
                        checked = prefs.keepScreenOn,
                        onChange = { checked -> viewModel.updateBrowser { it.copy(keepScreenOn = checked) } },
                    )
                }
            }
            item {
                SettingsSection(title = "Picture-in-picture") {
                    SwitchRow(
                        title = "Picture-in-picture",
                        checked = prefs.pictureInPicture,
                        onChange = { checked -> viewModel.updateBrowser { it.copy(pictureInPicture = checked) } },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Start automatically",
                        summary = "Shrink the video into a window when you leave the app",
                        checked = prefs.autoPip,
                        enabled = prefs.pictureInPicture,
                        onChange = { checked -> viewModel.updateBrowser { it.copy(autoPip = checked) } },
                    )
                }
            }
            item {
                SettingsSection(title = "Playback") {
                    ChoiceRow(
                        title = "Autoplay",
                        options = AutoplayMode.entries.map { it to it.label },
                        selected = prefs.autoplay,
                        onSelect = { mode -> viewModel.updateBrowser { it.copy(autoplay = mode) } },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Play in background",
                        summary = "Keep audio playing when you leave the app or lock the screen",
                        checked = prefs.backgroundPlayback,
                        onChange = { checked -> viewModel.updateBrowser { it.copy(backgroundPlayback = checked) } },
                    )
                    SettingsDivider()
                    ChoiceRow(
                        title = "Default speed",
                        options = PLAYBACK_SPEEDS.map { it to speedLabel(it) },
                        selected = prefs.defaultSpeed,
                        onSelect = { speed -> viewModel.updateBrowser { it.copy(defaultSpeed = speed) } },
                    )
                    SettingsDivider()
                    Text(
                        text = "Speeds set from the browser menu are remembered per site.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    TextButton(
                        onClick = {
                            viewModel.clearSiteSpeeds { onShowSnackbar("Per-site speeds cleared") }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text("Forget per-site speeds")
                    }
                }
            }
            extraRows()
        }
    }
}
