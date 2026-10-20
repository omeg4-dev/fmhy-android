package io.github.omeg4dev.fmhy.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.SettingsPage
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.OpenLinksWith
import io.github.omeg4dev.fmhy.data.SectionOrder
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.theme.ACCENT_PRESETS
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHub(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToPage: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val browserPrefs by viewModel.browserPrefs.collectAsStateWithLifecycle()
    val blockingPrefs by viewModel.blockingPrefs.collectAsStateWithLifecycle()
    val notificationPrefs by viewModel.notificationPrefs.collectAsStateWithLifecycle()
    val dataPrefs by viewModel.dataPrefs.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (_: Exception) {
            null
        } ?: "1.0.0"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            // Appearance
            item {
                val themeStr = when (state.appearance.themeMode) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                }
                val accentStr = if (state.appearance.colorSource == ColorSource.MATERIAL_YOU) {
                    "Material You"
                } else {
                    ACCENT_PRESETS.firstOrNull { it.seed == state.appearance.accentSeed }?.name ?: "Custom"
                }
                val textScaleStr = "${(state.appearance.textScale * 100).roundToInt()} %"
                NavRow(
                    title = "Appearance",
                    summary = "$themeStr · $accentStr · $textScaleStr",
                    icon = Icons.Default.Palette,
                    onClick = { onNavigateToPage(SettingsPage.APPEARANCE) },
                )
            }

            // Home & lists
            item {
                val orderStr = if (state.appearance.sectionOrder == SectionOrder.WEBSITE) "Website order" else "A–Z order"
                val randomStr = if (state.appearance.showRandomPick) "Random pick on" else "Random pick off"
                NavRow(
                    title = "Home & lists",
                    summary = "$orderStr · $randomStr",
                    icon = Icons.Default.Home,
                    onClick = { onNavigateToPage(SettingsPage.HOME_LISTS) },
                )
            }

            // Browser
            item {
                val browserStr = when (browserPrefs.openLinksWith) {
                    OpenLinksWith.IN_APP -> "In-app browser"
                    OpenLinksWith.CUSTOM_TAB -> "Custom tab"
                    OpenLinksWith.EXTERNAL -> "External browser"
                }
                val adblockStr = if (blockingPrefs.adblock) "Ad blocking on" else "Ad blocking off"
                NavRow(
                    title = "Browser",
                    summary = "$browserStr · $adblockStr",
                    icon = Icons.Default.Public,
                    onClick = { onNavigateToPage(SettingsPage.BROWSER) },
                )
            }

            // What's new & notifications
            item {
                val notifStr = if (notificationPrefs.enabled) "Notifications on" else "Notifications off"
                NavRow(
                    title = "What's new & notifications",
                    summary = notifStr,
                    icon = Icons.Default.NotificationsActive,
                    onClick = { onNavigateToPage(SettingsPage.NOTIFICATIONS) },
                )
            }

            // Data & sync
            item {
                val sitesStr = if (state.entryCount > 0) "%,d sites".format(state.entryCount) else "No sites"
                val syncStr = state.lastSyncedText
                NavRow(
                    title = "Data & sync",
                    summary = "$sitesStr · $syncStr",
                    icon = Icons.Default.Sync,
                    onClick = { onNavigateToPage(SettingsPage.DATA) },
                )
            }

            // Saved & history
            item {
                val historyStr = if (dataPrefs.keepHistory) "History on" else "History off"
                NavRow(
                    title = "Saved & history",
                    summary = historyStr,
                    icon = Icons.Default.Bookmarks,
                    onClick = { onNavigateToPage(SettingsPage.SAVED) },
                )
            }

            // About
            item {
                NavRow(
                    title = "About",
                    summary = "Version $versionName",
                    icon = Icons.Default.Info,
                    onClick = { onNavigateToPage(SettingsPage.ABOUT) },
                )
            }
        }
    }
}
