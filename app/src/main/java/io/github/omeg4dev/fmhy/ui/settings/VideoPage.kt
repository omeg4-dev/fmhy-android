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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
                    SwitchRow(
                        title = "Rotate for fullscreen video",
                        checked = prefs.videoAutoRotate,
                        onChange = { checked -> viewModel.updateBrowser { it.copy(videoAutoRotate = checked) } },
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
                }
            }
            extraRows()
        }
    }
}
