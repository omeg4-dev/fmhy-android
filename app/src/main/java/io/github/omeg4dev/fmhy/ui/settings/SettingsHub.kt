package io.github.omeg4dev.fmhy.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.SettingsPage
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.OpenLinksWith
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.adblock.FilterListCatalog
import io.github.omeg4dev.fmhy.theme.ACCENT_PRESETS
import io.github.omeg4dev.fmhy.ui.common.Motion

/** Height of the overlaid search field (56dp field + 8dp padding above and below). */
private val SearchFieldSlot = 72.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHub(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToPage: (SettingsPage) -> Unit,
    onNavigateToSetting: (SettingsPage, String) -> Unit,
    onNavigateToFollowing: () -> Unit,
    onNavigateToWhatsNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val homePrefs by viewModel.homePrefs.collectAsStateWithLifecycle()
    val browserPrefs by viewModel.browserPrefs.collectAsStateWithLifecycle()
    val blockingPrefs by viewModel.blockingPrefs.collectAsStateWithLifecycle()
    val privacyPrefs by viewModel.privacyPrefs.collectAsStateWithLifecycle()
    val notificationPrefs by viewModel.notificationPrefs.collectAsStateWithLifecycle()
    val updatePrefs by viewModel.updatePrefs.collectAsStateWithLifecycle()
    val dataUsagePrefs by viewModel.dataUsagePrefs.collectAsStateWithLifecycle()
    val follows by viewModel.follows.collectAsStateWithLifecycle()

    var isRevealed by rememberSaveable { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var pendingFocus by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 12.dp.toPx() } }

    val revealState = remember(thresholdPx) {
        ScrollRevealState(threshold = thresholdPx, initialRevealed = isRevealed) {
            isRevealed = it
        }
    }

    val nestedScrollConnection = remember(revealState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                revealState.onScroll(available.y)
                return Offset.Zero
            }
        }
    }

    val isSearchVisible = isRevealed || isFocused || query.isNotEmpty()

    LaunchedEffect(pendingFocus) {
        if (pendingFocus) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
            pendingFocus = false
        }
    }

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

    val searchResults = remember(query, viewModel.updateController.enabled) {
        if (query.isEmpty()) {
            emptyList()
        } else {
            val exclude = if (viewModel.updateController.enabled) {
                emptySet()
            } else {
                setOf(SettingsPage.APP_UPDATES)
            }
            SettingsSearchIndex.search(query = query, excludePages = exclude)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            revealState.reveal()
                            isRevealed = true
                            pendingFocus = true
                        }
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search settings")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
        modifier = modifier,
    ) { innerPadding ->
        // The search field overlays the list: revealing it on scroll must not change the
        // list's size or position, or the content jumps under the finger (BUG-18).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding(),
                ),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = if (isFocused || query.isNotEmpty()) SearchFieldSlot else 8.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (query.isNotEmpty()) {
                    if (searchResults.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 48.dp, start = 24.dp, end = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "No settings match \"$query\"",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "Try a shorter or different word",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        items(searchResults, key = { it.entry.id }) { result ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToSetting(result.entry.page, result.entry.id) }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = result.entry.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = result.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = result.entry.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else {
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
                        val homeSummary = if (homePrefs.personalHome) "Personal Home on" else "Personal Home off"
                        val listsSummary = when (state.appearance.sectionLayout) {
                            SectionLayout.CONTINUOUS -> "Continuous"
                            SectionLayout.CATEGORIES -> "Categories"
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
                                title = "Home",
                                summary = homeSummary,
                                icon = Icons.Default.Home,
                                onClick = { onNavigateToPage(SettingsPage.HOME) },
                            )
                            SettingsDivider(hasIcon = true)
                            NavRow(
                                title = "Lists & gestures",
                                summary = listsSummary,
                                icon = Icons.AutoMirrored.Filled.List,
                                onClick = { onNavigateToPage(SettingsPage.LISTS) },
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
                                title = "Downloads",
                                summary = "Folder, checks, FMHY status",
                                icon = Icons.Default.Download,
                                onClick = { onNavigateToPage(SettingsPage.DOWNLOADS) },
                            )
                        }
                    }

                    // Safety & privacy
                    item {
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

                        SettingsSection(title = "Safety & privacy") {
                            NavRow(
                                title = "Ad blocking",
                                summary = adblockStr,
                                icon = Icons.Default.Shield,
                                onClick = { onNavigateToPage(SettingsPage.AD_BLOCKING) },
                            )
                            SettingsDivider(hasIcon = true)
                            NavRow(
                                title = "Protection",
                                summary = "Pop-ups, redirects, unsafe sites",
                                icon = Icons.Default.Security,
                                onClick = { onNavigateToPage(SettingsPage.PROTECTION) },
                            )
                            SettingsDivider(hasIcon = true)
                            NavRow(
                                title = "Privacy & history",
                                summary = privacyStr,
                                icon = Icons.Default.Lock,
                                onClick = { onNavigateToPage(SettingsPage.PRIVACY) },
                            )
                        }
                    }

                    // Updates
                    item {
                        val count = follows.size
                        val followingSummary = if (count > 0) {
                            "$count followed ${if (count == 1) "section" else "sections"}"
                        } else {
                            "Nothing followed yet"
                        }
                        val notifSummary = if (notificationPrefs.enabled) "Notifications on" else "Notifications off"
                        val appUpdatesSummary = if (updatePrefs.autoCheck) "Automatic checks on" else "Automatic checks off"

                        SettingsSection(title = "Updates") {
                            NavRow(
                                title = "Following",
                                summary = followingSummary,
                                icon = Icons.Default.Bookmarks,
                                onClick = onNavigateToFollowing,
                            )
                            SettingsDivider(hasIcon = true)
                            NavRow(
                                title = "Notifications",
                                summary = notifSummary,
                                icon = Icons.Default.NotificationsActive,
                                onClick = { onNavigateToPage(SettingsPage.NOTIFICATIONS) },
                            )
                            SettingsDivider(hasIcon = true)
                            NavRow(
                                title = "What's new",
                                summary = "Recent changes to FMHY",
                                icon = Icons.Default.NewReleases,
                                onClick = onNavigateToWhatsNew,
                            )
                            if (viewModel.updateController.enabled) {
                                SettingsDivider(hasIcon = true)
                                NavRow(
                                    title = "App updates",
                                    summary = appUpdatesSummary,
                                    icon = Icons.Default.SystemUpdate,
                                    onClick = { onNavigateToPage(SettingsPage.APP_UPDATES) },
                                )
                            }
                        }
                    }

                    // Your data
                    item {
                        val syncStr = if (state.lastSyncedText.startsWith("Never")) {
                            "Never synced"
                        } else {
                            "Synced ${state.lastSyncedText.substringBefore(" via ")}"
                        }
                        val activeWifiOnlyCount = listOf(
                            dataUsagePrefs.downloadWifiOnly,
                            dataUsagePrefs.browseWifiOnly,
                            dataUsagePrefs.appDataWifiOnly,
                        ).count { it }
                        val dataUsageSummary = if (activeWifiOnlyCount > 0) {
                            "Wi-Fi only: $activeWifiOnlyCount of 3 on"
                        } else {
                            "Off"
                        }

                        SettingsSection(title = "Your data") {
                            NavRow(
                                title = "Sync",
                                summary = syncStr,
                                icon = Icons.Default.Sync,
                                onClick = { onNavigateToPage(SettingsPage.SYNC) },
                            )
                            SettingsDivider(hasIcon = true)
                            NavRow(
                                title = "Data usage",
                                summary = dataUsageSummary,
                                icon = Icons.Default.Wifi,
                                onClick = { onNavigateToPage(SettingsPage.DATA_USAGE) },
                            )
                            SettingsDivider(hasIcon = true)
                            NavRow(
                                title = "Backup & restore",
                                summary = "Export or import your data",
                                icon = Icons.Outlined.Backup,
                                onClick = { onNavigateToPage(SettingsPage.BACKUP) },
                            )
                        }
                    }

                    // Help & about
                    item {
                        SettingsSection(title = "Help & about") {
                            NavRow(
                                title = "Report a problem",
                                summary = "Tell us what went wrong",
                                icon = Icons.Default.BugReport,
                                onClick = { onNavigateToPage(SettingsPage.REPORT) },
                            )
                            SettingsDivider(hasIcon = true)
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

            AnimatedVisibility(
                visible = isSearchVisible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically(animationSpec = Motion.enter()) { -it } + fadeIn(animationSpec = Motion.enter()),
                exit = slideOutVertically(animationSpec = Motion.exit()) { -it } + fadeOut(animationSpec = Motion.exit()),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search settings") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }

        }
    }
}
