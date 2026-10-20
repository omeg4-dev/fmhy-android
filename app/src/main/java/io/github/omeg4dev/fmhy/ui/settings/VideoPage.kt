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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val browserPrefs by viewModel.browserPrefs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video") },
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
            item {
                SwitchRow(
                    title = "Rotate for fullscreen video",
                    checked = browserPrefs.videoAutoRotate,
                    onChange = { checked ->
                        viewModel.updateBrowser { it.copy(videoAutoRotate = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Picture-in-picture",
                    checked = browserPrefs.pictureInPicture,
                    onChange = { checked ->
                        viewModel.updateBrowser { it.copy(pictureInPicture = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Keep screen on while playing",
                    checked = browserPrefs.keepScreenOn,
                    onChange = { checked ->
                        viewModel.updateBrowser { it.copy(keepScreenOn = checked) }
                    },
                )
            }

            extraRows()
        }
    }
}
