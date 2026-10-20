package io.github.omeg4dev.fmhy.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.SettingsPage
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.OpenLinksWith
import io.github.omeg4dev.fmhy.data.SectionOrder
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.adblock.FilterListCatalog
import io.github.omeg4dev.fmhy.theme.ACCENT_PRESETS

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
    val privacyPrefs by viewModel.privacyPrefs.collectAsStateWithLifecycle()
    val notificationPrefs by viewModel.notificationPrefs.collectAsStateWithLifecycle()
    val follows by viewModel.follows.collectAsStateWithLifecycle()
    val savedCount by viewModel.savedCount.collectAsStateWithLifecycle()

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
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Look & feel
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
                val orderStr = if (state.appearance.sectionOrder == SectionOrder.WEBSITE) {
                    "Website order"
                } else {
                    "A–Z order"
                }

                SettingsSection(title = "Look & feel") {
                    NavRow(
                        title = "Appearance",
                        summary = "$themeStr · $accentStr",
                        icon = Icons.Default.Palette,
                        onClick = { onNavigateToPage(SettingsPage.APPEARANCE) },
                    )
                    SettingsDivider(hasIcon = true)
                    NavRow(
                        title = "Home & lists",
                        summary = orderStr,
                        icon = Icons.Default.Home,
                        onClick = { onNavigateToPage(SettingsPage.HOME_LISTS) },
                    )
                }
            }

            // Browsing
            item {
                val browserStr = when (browserPrefs.openLinksWith) {
                    OpenLinksWith.IN_APP -> "In-app browser"
                    OpenLinksWith.CUSTOM_TAB -> "Custom tab"
                    OpenLinksWith.EXTERNAL -> "External browser"
                }
                val adblockStr = when {
                    !blockingPrefs.adblock -> "Off"
                    blockingPrefs.enabledLists == FilterListCatalog.balancedIds -> "Balanced"
                    blockingPrefs.enabledLists == FilterListCatalog.strictIds -> "Strict"
                    else -> "Custom"
                }
                val privacyOnCount = listOf(
                    privacyPrefs.blockThirdPartyCookies,
                    privacyPrefs.hideCookieBanners,
                    privacyPrefs.clearDataOnExit,
                    privacyPrefs.privateTabsByDefault,
                    privacyPrefs.sendGpc,
                ).count { it }
                val privacyStr = if (privacyOnCount >= 3) "Strict" else "Standard"

                SettingsSection(title = "Browsing") {
                    NavRow(
                        title = "Browser",
                        summary = browserStr,
                        icon = Icons.Default.Public,
                        onClick = { onNavigateToPage(SettingsPage.BROWSER) },
                    )
                    SettingsDivider(hasIcon = true)
                    NavRow(
                        title = "Video",
                        summary = "Fullscreen, picture-in-picture, speed",
                        icon = Icons.Outlined.OndemandVideo,
                        onClick = { onNavigateToPage(SettingsPage.VIDEO) },
                    )
                    SettingsDivider(hasIcon = true)
                    NavRow(
                        title = "Ad blocking & protection",
                        summary = adblockStr,
                        icon = Icons.Default.Shield,
                        onClick = { onNavigateToPage(SettingsPage.BLOCKING) },
                    )
                    SettingsDivider(hasIcon = true)
                    NavRow(
                        title = "Privacy",
                        summary = privacyStr,
                        icon = Icons.Default.Lock,
                        onClick = { onNavigateToPage(SettingsPage.PRIVACY) },
                    )
                }
            }

            // Updates
            item {
                val notifStr = if (!notificationPrefs.enabled) {
                    "Notifications off"
                } else if (follows.isNotEmpty()) {
                    val count = follows.size
                    "$count followed ${if (count == 1) "section" else "sections"}"
                } else {
                    "Notifications on"
                }

                SettingsSection(title = "Updates") {
                    NavRow(
                        title = "What's new & notifications",
                        summary = notifStr,
                        icon = Icons.Default.NotificationsActive,
                        onClick = { onNavigateToPage(SettingsPage.NOTIFICATIONS) },
                    )
                }
            }

            // Your data
            item {
                val savedStr = "$savedCount saved"
                val syncStr = if (state.lastSyncedText.startsWith("Never")) {
                    "Never synced"
                } else {
                    "Synced ${state.lastSyncedText.substringBefore(" via ")}"
                }

                SettingsSection(title = "Your data") {
                    NavRow(
                        title = "Saved & history",
                        summary = savedStr,
                        icon = Icons.Default.Bookmarks,
                        onClick = { onNavigateToPage(SettingsPage.SAVED) },
                    )
                    SettingsDivider(hasIcon = true)
                    NavRow(
                        title = "Data & sync",
                        summary = syncStr,
                        icon = Icons.Default.Sync,
                        onClick = { onNavigateToPage(SettingsPage.DATA) },
                    )
                }
            }

            // About
            item {
                SettingsSection(title = "About") {
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
}
