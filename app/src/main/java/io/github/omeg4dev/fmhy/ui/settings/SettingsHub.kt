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
import androidx.compose.material.icons.filled.CloudSync
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
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import io.github.omeg4dev.fmhy.ui.home.HomeBlock
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
import io.github.omeg4dev.fmhy.BuildConfig
import io.github.omeg4dev.fmhy.SettingsPage
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.OpenLinksWith
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.adblock.FilterListCatalog
import io.github.omeg4dev.fmhy.data.fmhyx.FmhyXAccountState
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
    val follows by viewModel.follows.collectAsStateWithLifecycle()
    val fmhyXState by viewModel.fmhyXAccountState.collectAsStateWithLifecycle()

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
            val exclude = buildSet {
                if (!viewModel.updateController.enabled) add(SettingsPage.APP_UPDATES)
                if (!BuildConfig.FMHYX_ENABLED) add(SettingsPage.FMHYX_ACCOUNT)
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
                                    style = settingsRowTitleStyle(),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = result.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = SettingsDescriptionGap),
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
                    val homeSummary = "${homePrefs.blocks.enabledCount} of ${HomeBlock.entries.size} blocks on"
                    val listsSummary = when (state.appearance.sectionLayout) {
                        SectionLayout.CONTINUOUS -> "Continuous"
                        SectionLayout.CATEGORIES -> "Categories"
                    }
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
                    val count = follows.size
                    val followingSummary = if (count > 0) {
                        "$count followed ${if (count == 1) "section" else "sections"}"
                    } else {
                        "Nothing followed yet"
                    }
                    val notifSummary = if (notificationPrefs.enabled) "Notifications on" else "Notifications off"
                    val appUpdatesSummary = if (updatePrefs.autoCheck) "Automatic checks on" else "Automatic checks off"
                    val syncStr = if (state.lastSyncedText.startsWith("Never")) {
                        "Never synced"
                    } else {
                        "Synced ${state.lastSyncedText.substringBefore(" via ")}"
                    }
                    val fmhyXSummary = when (val fx = fmhyXState) {
                        FmhyXAccountState.SignedOut -> "Not signed in"
                        is FmhyXAccountState.SignedIn -> when {
                            fx.running -> "Syncing…"
                            fx.error != null -> formatFmhyXError(fx.error)
                            fx.lastSyncedAt != null -> "Synced ${formatFmhyXSyncTime(fx.lastSyncedAt)}"
                            else -> "Not synced yet"
                        }
                    }

                    SettingsTree.groups.forEach { group ->
                        val visibleItems = group.items.filter { item ->
                            when (item) {
                                is HubItem.Page -> when (item.page) {
                                    SettingsPage.APP_UPDATES -> viewModel.updateController.enabled
                                    SettingsPage.FMHYX_ACCOUNT -> BuildConfig.FMHYX_ENABLED
                                    else -> true
                                }
                                HubItem.Following, HubItem.WhatsNew -> true
                            }
                        }
                        if (visibleItems.isNotEmpty()) {
                            item(key = group.title) {
                                SettingsSection(title = group.title) {
                                    visibleItems.forEachIndexed { index, hubItem ->
                                        if (index > 0) SettingsDivider(hasIcon = true)
                                        when (hubItem) {
                                            is HubItem.Page -> {
                                                val page = hubItem.page
                                                val (summary, icon) = when (page) {
                                                    SettingsPage.APPEARANCE -> "$themeStr · $accentStr" to Icons.Default.Palette
                                                    SettingsPage.HOME -> homeSummary to Icons.Default.Home
                                                    SettingsPage.LISTS -> listsSummary to Icons.AutoMirrored.Filled.List
                                                    SettingsPage.BROWSER -> browserStr to Icons.Default.Public
                                                    SettingsPage.VIDEO -> "Fullscreen, picture-in-picture, speed" to Icons.Outlined.OndemandVideo
                                                    SettingsPage.DOWNLOADS -> "Folder, file checks, FMHY status" to Icons.Default.Download
                                                    SettingsPage.AD_BLOCKING -> adblockStr to Icons.Default.Shield
                                                    SettingsPage.PROTECTION -> "Pop-ups, redirects, unsafe sites" to Icons.Default.Security
                                                    SettingsPage.CONNECTION -> "VPN ring, proxy, Private DNS" to Icons.Default.VpnKey
                                                    SettingsPage.PRIVACY -> privacyStr to Icons.Default.Lock
                                                    SettingsPage.NOTIFICATIONS -> notifSummary to Icons.Default.NotificationsActive
                                                    SettingsPage.APP_UPDATES -> appUpdatesSummary to Icons.Default.SystemUpdate
                                                    SettingsPage.DATA -> syncStr to Icons.Default.Storage
                                                    SettingsPage.FMHYX_ACCOUNT -> fmhyXSummary to Icons.Default.CloudSync
                                                    SettingsPage.REPORT -> "Tell us what went wrong" to Icons.Default.BugReport
                                                    SettingsPage.ABOUT -> "Version $versionName" to Icons.Default.Info
                                                }
                                                NavRow(
                                                    title = page.label(),
                                                    summary = summary,
                                                    icon = icon,
                                                    onClick = { onNavigateToPage(page) },
                                                )
                                            }
                                            HubItem.Following -> NavRow(
                                                title = "Following",
                                                summary = followingSummary,
                                                icon = Icons.Default.Bookmarks,
                                                onClick = onNavigateToFollowing,
                                            )
                                            HubItem.WhatsNew -> NavRow(
                                                title = "What's new",
                                                summary = "Recent changes to FMHY",
                                                icon = Icons.Default.NewReleases,
                                                onClick = onNavigateToWhatsNew,
                                            )
                                        }
                                    }
                                }
                            }
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
