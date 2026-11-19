package io.github.omeg4dev.fmhy.browser

/** Bottom sheets and download confirmation for [BrowserScreen]. */

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.browser.adblock.AllowRule
import io.github.omeg4dev.fmhy.browser.download.DownloadRequest
import io.github.omeg4dev.fmhy.browser.download.DownloadsSheet
import io.github.omeg4dev.fmhy.browser.download.KeyPromptDialog
import io.github.omeg4dev.fmhy.browser.download.RedirectWarningText
import io.github.omeg4dev.fmhy.browser.download.ScanService
import io.github.omeg4dev.fmhy.browser.download.VideoSourcesSheet
import io.github.omeg4dev.fmhy.browser.download.VtSiteChecks
import io.github.omeg4dev.fmhy.ui.vpn.ConnectionSafety
import io.github.omeg4dev.fmhy.ui.vpn.IpLine
import io.github.omeg4dev.fmhy.ui.vpn.openVpnSettings
import io.github.omeg4dev.fmhy.ui.vpn.rememberIpLine
import kotlinx.coroutines.launch

@Composable
internal fun BrowserScreenScope.BrowserScreenSheets() {
    // Download confirmation
    if (hooks != null && pendingDownload != null) {
        val showDialogOrConfirm = pendingDownload.interstitial == null || offerAccepted
        if (showDialogOrConfirm) {
            if (pendingDownload.needsConfirmation) {
                DownloadDialog(
                    offer = pendingDownload,
                    onConfirm = { allowMobile ->
                        hooks.confirmDownload(pendingDownload, allowMobile)
                    },
                    onCancel = { hooks.pendingDownload = null },
                    checkServices = checkPrefs.enabledServices,
                    readyServices = checkPrefs.readyServices,
                    onCheckLink = { url -> container.checks.checkLink(url, checkPrefs.readyServices) },
                    onOpenUrl = { tabManager.openTab(it, isPrivate = currentTab?.isPrivate == true) },
                    onShowInSection = { entryId ->
                        scope.launch {
                            val entry = datasetStore.entry(entryId)
                            if (entry != null) {
                                showEntryInApp(context, entry)
                            }
                        }
                    },
                )
            } else {
                LaunchedEffect(pendingDownload, offerAccepted) {
                    hooks.confirmDownload(pendingDownload, allowMobile = false)
                }
            }
        }
    }

    if (hooks != null) {
        // "Open in another app?" from the redirect/app-link guard (call site 1)
        val appDialog = hooks.pendingExternalAppDialog
        if (appDialog != null) {
            OpenInAnotherAppDialog(
                appName = appDialog.appName,
                onConfirm = {
                    hooks.pendingExternalAppDialog = null
                    try {
                        appDialog.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(appDialog.intent)
                    } catch (_: Exception) {
                        appDialog.fallbackUrl?.let { u -> currentTab?.let { tabManager.loadUrl(it.id, u) } }
                    }
                },
                onDismiss = { hooks.pendingExternalAppDialog = null },
            )
        }
    }

    // Site menu behind the site icon: connection details (when VPN state is shown) and the VirusTotal check
    if (showConnectionSheet && currentTab != null) {
        val sheetTab = currentTab
        val level = connectionVpn?.let { ConnectionSafety.levelOf(sheetTab.url, it) }
        val vtHost = DownloadRequest.hostOf(sheetTab.url).ifBlank { null }
            ?.takeIf { DownloadRequest.isHttpUrl(sheetTab.url) }
        val kind = sheetTab.siteBarState?.kind
        val vtAllowed = vtHost != null && kind != SiteBarKind.FMHY_SITE && kind != SiteBarKind.UNSAFE
        if (level == null && !vtAllowed) {
            showConnectionSheet = false
        } else {
            val vtMarks by container.vtChecks.checked.collectAsStateWithLifecycle()
            val vtState = if (vtAllowed && vtHost != null) {
                VtSheetState(
                    host = vtHost,
                    isPrivate = sheetTab.isPrivate,
                    mark = vtMarks[VtSiteChecks.normalizeHost(vtHost)]?.takeIf { container.vtChecks.markFor(vtHost) != null },
                    ready = ScanService.VIRUSTOTAL in container.checks.prefs.value.readyServices,
                    onCheck = {
                        val result = container.checks.checkLink("https://$vtHost/", listOf(ScanService.VIRUSTOTAL))
                        val vt = result.services.firstOrNull { it.service == ScanService.VIRUSTOTAL }
                        if (vt != null && vt.error == null && VtSiteChecks.shouldRemember(vt.verdict) && !sheetTab.isPrivate) {
                            container.vtChecks.remember(vtHost, vt.flagged, vt.engines)
                        }
                        result
                    },
                    onSubmit = {
                        val result = container.checks.submitLink("https://$vtHost/", ScanService.VIRUSTOTAL)
                        val vt = result.services.firstOrNull { it.service == ScanService.VIRUSTOTAL }
                        if (vt != null && vt.error == null && VtSiteChecks.shouldRemember(vt.verdict) && !sheetTab.isPrivate) {
                            container.vtChecks.remember(vtHost, vt.flagged, vt.engines)
                        }
                        result
                    },
                    onSetup = {
                        showConnectionSheet = false
                        tabManager.openFileCheckSettings("downloads.virustotal_key")
                    },
                )
            } else {
                null
            }
            ConnectionSheet(
                url = sheetTab.url,
                level = level,
                vpnState = connectionVpn,
                ipLine = if (connectionVpn != null) {
                    rememberIpLine(container.ipInfoRepository, protectionPrefs.showIpCountry, connectionVpn, active = true)
                } else {
                    IpLine.Hidden
                },
                onOpenVpnSettings = { openVpnSettings(context) },
                onDismiss = { showConnectionSheet = false },
                vt = vtState,
            )
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
            redirectGuard = protectionPrefs.redirectGuard,
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
                val site = AllowRule.pageSite(currentTab.url)
                if (site != null) {
                    val allowed = AllowRule.allowedHostsFor(blockingPrefs.customRules, site)
                    for (h in allowed) {
                        tabManager.unallowBlockedHost(currentTab.id, h)
                    }
                }
            },
            onHideElement = {
                showShieldSheet = false
                tabManager.startElementPicker(currentTab.id)
            },
            onOpenBlockedList = {
                showShieldSheet = false
                showBlockedSheet = true
            },
        )
    }

    // Blocked Sheet
    if (showBlockedSheet && currentTab != null) {
        val site = currentTab.url.let { AllowRule.pageSite(it) }.orEmpty()
        BlockedSheet(
            tab = currentTab,
            allowedHosts = AllowRule.allowedHostsFor(blockingPrefs.customRules, site),
            onAllow = { host -> tabManager.allowBlockedHost(currentTab.id, host) },
            onUnallow = { host -> tabManager.unallowBlockedHost(currentTab.id, host) },
            onReload = { tabManager.reloadTab(currentTab.id) },
            onDismiss = { showBlockedSheet = false },
        )
    }

    // Tabs Sheet
    if (showTabsSheet) {
        TabsSheet(
            tabs = tabs,
            currentTabId = currentTabId,
            tabManager = tabManager,
            tabsView = browserPrefs.tabsView,
            onTabsViewChange = { v -> scope.launch { settingsRepository.updateBrowser { it.copy(tabsView = v) } } },
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
                tabManager.openNewTabPage(isPrivate = isPrivate)
            },
            onCloseAll = {
                showTabsSheet = false
                tabManager.closeAllTabs()
                onFinishActivity()
            },
        )
    }

    // Downloads Sheet
    if (showDownloadsSheet) {
        DownloadsSheet(
            controller = downloadController,
            isPrivateTab = currentTab?.isPrivate == true,
            onPasteLink = { hooks?.offerPastedLink(context, it, currentTab?.isPrivate == true) },
            onDismiss = { showDownloadsSheet = false },
            onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
            installExplained = updatePrefs.installExplained,
            onInstallExplained = { scope.launch { settingsRepository.updateUpdatePrefs { it.copy(installExplained = true) } } },
            checkPrefs = checkPrefs,
            checks = container.checks,
            onOpenUrl = { tabManager.openTab(it, isPrivate = currentTab?.isPrivate == true) },
            onOpenCheckSettings = { tabManager.openFileCheckSettings() },
            onShowInSection = { entryId ->
                scope.launch {
                    val entry = datasetStore.entry(entryId)
                    if (entry != null) {
                        showEntryInApp(context, entry)
                    }
                }
            },
        )
    }

    redirectWarningEvent?.let { warn ->
        AlertDialog(
            onDismissRequest = { redirectWarningEvent = null },
            title = { Text("Download redirected") },
            text = { Text(RedirectWarningText.sentence(warn.fromHost, warn.flag)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = warn.id
                        redirectWarningEvent = null
                        downloadController.continueAfterRedirectWarning(id)
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val id = warn.id
                        redirectWarningEvent = null
                        downloadController.cancel(id)
                    }
                ) {
                    Text("Cancel download")
                }
            },
        )
    }

    if (showKeyPromptDialog) {
        KeyPromptDialog(
            onSetUp = {
                showKeyPromptDialog = false
                tabManager.openFileCheckSettings()
            },
            onNotNow = {
                showKeyPromptDialog = false
            },
        )
    }

    // Video Sources Sheet
    if (showVideoSheet) {
        val sources = currentTab?.videoSources.orEmpty()
        VideoSourcesSheet(
            sources = sources,
            onPick = { source ->
                showVideoSheet = false
                val id = currentTab?.id ?: return@VideoSourcesSheet
                val record = tabManager.getTabRecord(id) ?: return@VideoSourcesSheet
                val userAgent = record.webView?.settings?.userAgentString.orEmpty()
                browserHooks.onDownloadStart(
                    context = context,
                    record = record,
                    url = source.url,
                    userAgent = userAgent,
                    contentDisposition = "",
                    mimeType = source.mimeType,
                    contentLength = -1L,
                )
            },
            onDismiss = { showVideoSheet = false },
        )
    }
}
