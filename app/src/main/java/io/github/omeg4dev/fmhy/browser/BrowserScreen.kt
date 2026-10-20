package io.github.omeg4dev.fmhy.browser

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.SelectorPicker
import io.github.omeg4dev.fmhy.data.BookmarksRepository
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.search.SearchEngine as DatasetSearchEngine
import io.github.omeg4dev.fmhy.data.user.UserBookmark
import kotlinx.coroutines.launch

@Composable
fun BrowserScreen(
    tabManager: TabManager,
    adblockService: AdblockService,
    settingsRepository: SettingsRepository,
    bookmarksRepository: BookmarksRepository,
    datasetSearchEngine: DatasetSearchEngine,
    browserHooks: BrowserHooks,
    isAppDark: Boolean,
    onFinishActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tabs by tabManager.tabs.collectAsStateWithLifecycle()
    val currentTabId by tabManager.currentTabId.collectAsStateWithLifecycle()
    val adblockStatus by adblockService.status.collectAsStateWithLifecycle()

    val browserPrefs by settingsRepository.browserPrefs.collectAsStateWithLifecycle(initialValue = tabManager.currentBrowserPrefs)
    val blockingPrefs by settingsRepository.blockingPrefs.collectAsStateWithLifecycle(initialValue = adblockService.currentBlockingPrefs)
    val protectionPrefs by settingsRepository.protectionPrefs.collectAsStateWithLifecycle(initialValue = tabManager.currentProtectionPrefs)

    val currentTab = remember(tabs, currentTabId) {
        tabs.firstOrNull { it.id == currentTabId }
    }

    // UI state
    var isToolbarVisible by remember { mutableStateOf(true) }
    var isEditMode by remember { mutableStateOf(false) }
    var showShieldSheet by remember { mutableStateOf(false) }
    var showTabsSheet by remember { mutableStateOf(false) }

    // Find in page state
    var isFindMode by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findActiveIndex by remember { mutableIntStateOf(0) }
    var findTotalMatches by remember { mutableIntStateOf(0) }

    // Dialogs state
    var pendingHideElementRule by remember { mutableStateOf<String?>(null) }
    var pendingExternalApp by remember { mutableStateOf<Pair<Intent, String>?>(null) }

    val savedIds by bookmarksRepository.savedIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val isSaved = currentTab?.entryId != null && savedIds.contains(currentTab.entryId)

    // Register tab manager callbacks
    LaunchedEffect(tabManager) {
        tabManager.onPickerResultCallback = { tabId, selector ->
            val record = tabManager.getTabRecord(tabId)
            val host = try { Uri.parse(record?.url.orEmpty()).host.orEmpty() } catch (_: Exception) { "" }
            if (host.isNotEmpty() && selector.isNotEmpty()) {
                pendingHideElementRule = SelectorPicker.computeRule(host, selector)
            }
        }
        tabManager.onPickerCancelCallback = {
            // Nothing to clean up
        }
        tabManager.onExternalAppCallback = { intent, appName ->
            pendingExternalApp = Pair(intent, appName)
        }
    }

    // Close activity if no tabs remain
    LaunchedEffect(tabs) {
        if (tabs.isEmpty() && currentTabId == null) {
            onFinishActivity()
        }
    }

    // System Back Handling:
    // 1. find bar open -> close it
    // 2. edit mode -> leave it
    // 3. webview canGoBack() -> goBack()
    // 4. else -> finish() (tabs stay alive)
    BackHandler(enabled = true) {
        when {
            isFindMode -> {
                isFindMode = false
                currentTab?.let { tabManager.getTabRecord(it.id)?.webView?.clearMatches() }
            }
            isEditMode -> {
                isEditMode = false
            }
            currentTab != null && tabManager.goBack(currentTab.id) -> {
                // Navigated back in WebView
            }
            else -> {
                onFinishActivity()
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                BrowserToolbar(
                    tab = currentTab,
                    tabCount = tabs.size,
                    visible = isToolbarVisible || isFindMode,
                    isFindMode = isFindMode,
                    findQuery = findQuery,
                    findActiveIndex = findActiveIndex,
                    findTotalMatches = findTotalMatches,
                    showBlockedCount = blockingPrefs.showBlockedCount,
                    bookmarksRepository = bookmarksRepository,
                    isSaved = isSaved,
                    onShieldClick = { showShieldSheet = true },
                    onAddressClick = { isEditMode = true },
                    onTabsClick = { showTabsSheet = true },
                    onReloadClick = { currentTab?.let { tabManager.reloadTab(it.id) } },
                    onStopClick = { currentTab?.let { tabManager.stopTab(it.id) } },
                    onBackClick = { currentTab?.let { tabManager.goBack(it.id) } },
                    onForwardClick = { currentTab?.let { tabManager.goForward(it.id) } },
                    onNewTabClick = { isPrivate ->
                        tabManager.openTab(url = "about:blank", title = "New tab", isPrivate = isPrivate, select = true)
                    },
                    onFindInPageClick = {
                        isFindMode = true
                        findQuery = ""
                    },
                    onFindQueryChange = { query ->
                        findQuery = query
                        currentTab?.let { t ->
                            val record = tabManager.getTabRecord(t.id)
                            record?.webView?.findAllAsync(query)
                        }
                    },
                    onFindNext = {
                        currentTab?.let { t ->
                            tabManager.getTabRecord(t.id)?.webView?.findNext(true)
                        }
                    },
                    onFindPrevious = {
                        currentTab?.let { t ->
                            tabManager.getTabRecord(t.id)?.webView?.findNext(false)
                        }
                    },
                    onFindClose = {
                        isFindMode = false
                        currentTab?.let { t ->
                            tabManager.getTabRecord(t.id)?.webView?.clearMatches()
                        }
                    },
                    onToggleDesktop = {
                        currentTab?.let { tabManager.toggleDesktopSite(it.id) }
                    },
                    onToggleBookmark = {
                        val tab = currentTab
                        val entryId = tab?.entryId
                        if (entryId != null) {
                            scope.launch {
                                if (isSaved) {
                                    bookmarksRepository.remove(entryId)
                                } else {
                                    tabManager.userStore.upsertBookmark(
                                        UserBookmark(
                                            entryId = entryId,
                                            name = tab.title.ifEmpty { tab.url },
                                            url = tab.url,
                                            sectionId = "",
                                            sectionTitle = "",
                                            headingPath = "",
                                            savedAt = System.currentTimeMillis(),
                                        )
                                    )
                                }
                            }
                        }
                    },
                )

                // SiteBar slot for E2
                currentTab?.let { tabState ->
                    browserHooks.siteBar(tabState)
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isBlankTab = currentTab == null || currentTab.url == "about:blank" || currentTab.url.isEmpty()

            if (isBlankTab) {
                // New tab page: edit mode suggestions directly on screen
                BrowserEditMode(
                    initialText = "",
                    searchEngine = browserPrefs.searchEngine,
                    datasetSearchEngine = datasetSearchEngine,
                    onNavigate = { url ->
                        currentTab?.let { tabManager.loadUrl(it.id, url) }
                    },
                    onDismiss = {
                        if (tabs.size > 1) {
                            currentTab?.let { tabManager.closeTab(it.id) }
                        } else {
                            onFinishActivity()
                        }
                    },
                )
            } else {
                // Active web content
                val record = tabManager.getTabRecord(currentTab.id)
                val webView = record?.webView

                if (webView != null) {
                    DisposableEffect(webView) {
                        // Setup find listener
                        webView.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                            findActiveIndex = activeMatchOrdinal
                            findTotalMatches = numberOfMatches
                        }

                        // Setup scroll listener for toolbar hide/show
                        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                            val dy = scrollY - oldScrollY
                            if (dy > 12 && scrollY > 60) {
                                isToolbarVisible = false
                            } else if (dy < -12) {
                                isToolbarVisible = true
                            }
                        }

                        onDispose {
                            webView.setOnScrollChangeListener(null)
                        }
                    }

                    AndroidView(
                        factory = {
                            val parent = webView.parent as? ViewGroup
                            parent?.removeView(webView)
                            webView
                        },
                        update = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Edit Mode overlay (when typing a URL over an existing page)
            if (isEditMode && !isBlankTab) {
                BrowserEditMode(
                    initialText = currentTab.url,
                    searchEngine = browserPrefs.searchEngine,
                    datasetSearchEngine = datasetSearchEngine,
                    onNavigate = { url ->
                        isEditMode = false
                        tabManager.loadUrl(currentTab.id, url)
                    },
                    onDismiss = { isEditMode = false },
                )
            }
        }
    }

    // Shield Sheet
    if (showShieldSheet && currentTab != null) {
        ShieldSheet(
            tab = currentTab,
            adblockStatus = adblockStatus,
            blockingPrefs = blockingPrefs,
            protectionPrefs = protectionPrefs,
            browserPrefs = browserPrefs,
            isAppDark = isAppDark,
            onDismiss = { showShieldSheet = false },
            onUpdateSitePref = { update ->
                val host = try { Uri.parse(currentTab.url).host.orEmpty() } catch (_: Exception) { "" }
                if (host.isNotEmpty()) {
                    tabManager.updateSitePrefs(currentTab.id, host, update)
                }
            },
            onResetDefaults = {
                val host = try { Uri.parse(currentTab.url).host.orEmpty() } catch (_: Exception) { "" }
                if (host.isNotEmpty()) {
                    tabManager.clearSitePrefs(currentTab.id, host)
                }
            },
            onHideElement = {
                showShieldSheet = false
                tabManager.startElementPicker(currentTab.id)
            },
        )
    }

    // Tabs Sheet
    if (showTabsSheet) {
        TabsSheet(
            tabs = tabs,
            currentTabId = currentTabId,
            onDismiss = { showTabsSheet = false },
            onSelectTab = { tabId ->
                showTabsSheet = false
                tabManager.selectTab(tabId)
            },
            onCloseTab = { tabId ->
                tabManager.closeTab(tabId)
            },
            onNewTab = { isPrivate ->
                showTabsSheet = false
                tabManager.openTab(url = "about:blank", title = "New tab", isPrivate = isPrivate, select = true)
            },
            onCloseAll = {
                showTabsSheet = false
                tabManager.closeAllTabs()
                onFinishActivity()
            },
        )
    }

    // Hide Element Confirmation Dialog
    val ruleToHide = pendingHideElementRule
    if (ruleToHide != null) {
        AlertDialog(
            onDismissRequest = { pendingHideElementRule = null },
            title = { Text("Hide element on this site") },
            text = {
                Column {
                    Text("Add cosmetic filter rule:")
                    Text(
                        text = ruleToHide,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingHideElementRule = null
                        scope.launch {
                            settingsRepository.updateBlocking { bp ->
                                val existing = bp.customRules.trim()
                                val newRules = if (existing.isEmpty()) ruleToHide else "$existing\n$ruleToHide"
                                bp.copy(customRules = newRules)
                            }
                            currentTab?.let { tabManager.reloadTab(it.id) }
                        }
                    }
                ) {
                    Text("Hide")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingHideElementRule = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Open External App Dialog
    val externalApp = pendingExternalApp
    if (externalApp != null) {
        AlertDialog(
            onDismissRequest = { pendingExternalApp = null },
            title = { Text("Open in another app?") },
            text = { Text("Do you want to open this link in ${externalApp.second}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = externalApp.first
                        pendingExternalApp = null
                        try {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                ) {
                    Text("Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingExternalApp = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
