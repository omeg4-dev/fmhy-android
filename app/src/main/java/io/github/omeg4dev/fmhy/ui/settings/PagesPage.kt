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
fun PagesPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val browserPrefs by viewModel.browserPrefs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pages") },
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
                    title = "JavaScript",
                    checked = browserPrefs.javascript,
                    onChange = { checked ->
                        viewModel.updateBrowser { it.copy(javascript = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Dark pages in dark theme",
                    checked = browserPrefs.forceDark,
                    onChange = { checked ->
                        viewModel.updateBrowser { it.copy(forceDark = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Desktop site by default",
                    checked = browserPrefs.desktopByDefault,
                    onChange = { checked ->
                        viewModel.updateBrowser { it.copy(desktopByDefault = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Text size follows app",
                    checked = browserPrefs.textZoomFollowsApp,
                    onChange = { checked ->
                        viewModel.updateBrowser { it.copy(textZoomFollowsApp = checked) }
                    },
                )
            }

            extraRows()
        }
    }
}
