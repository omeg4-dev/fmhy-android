package io.github.omeg4dev.fmhy.browser

/** Pop-up window and external application navigation handling for [DefaultBrowserHooks]. */

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.adblock.FilterRequest
import io.github.omeg4dev.fmhy.data.adblock.MatchResult
import io.github.omeg4dev.fmhy.data.adblock.ResourceType
import io.github.omeg4dev.fmhy.data.user.SitePrefs
import kotlinx.coroutines.launch

internal fun DefaultBrowserHooks.handleCreateWindowImpl(
    context: Context,
    tab: TabState,
    isUserGesture: Boolean,
    resultMsg: Message?,
    openNewTab: (url: String) -> Unit
): Boolean {
    if (resultMsg == null) return false
    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
    if (livePopupCount.get() >= 3) {
        tabManager?.incrementBlocked(tab.id)
        blockedFlow.tryEmit(
            BlockedNoticeEvent(
                tabId = tab.id,
                entry = BlockedEntry(
                    kind = BlockedKind.POPUP,
                    host = null,
                    targetUrl = "",
                    actionLabel = "Open",
                    onAction = {},
                    onAlwaysAllow = null,
                    actionable = false,
                )
            )
        )
        return false
    }

    livePopupCount.incrementAndGet()
    val tempWebView = WebView(context).apply {
        settings.javaScriptEnabled = tabManager?.effectiveJavaScript(tab.sitePrefs) ?: false
    }
    var cleanedUp = false
    fun cleanup() {
        if (!cleanedUp) {
            cleanedUp = true
            livePopupCount.decrementAndGet()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try { tempWebView.destroy() } catch (_: Exception) {}
            }
        }
    }
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ cleanup() }, 10_000L)

    tempWebView.webViewClient = object : WebViewClient() {
        private var handled = false

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val pm = tabManager?.proxyManager
            if (pm != null && !pm.awaitWebAllowed()) {
                return BrowserWebViewFactory.proxyBlockedResponse()
            }
            return super.shouldInterceptRequest(view, request)
        }

        private fun processUrl(targetUrl: String) {
            if (handled || targetUrl.isBlank() || targetUrl == "about:blank") return
            handled = true
            cleanup()

            // Site prefs load asynchronously after a page start, so a pop-up opened while the page
            // loads can arrive before the tab knows its overrides: read them from the store then.
            val manager = tabManager
            val pageHost = runCatching { Uri.parse(tab.url).host }.getOrNull()
            if (manager != null && tab.sitePrefs == null && !pageHost.isNullOrEmpty()) {
                manager.appScope.launch {
                    val loaded = runCatching { manager.userStore.sitePrefs(pageHost) }.getOrNull()
                    android.os.Handler(android.os.Looper.getMainLooper()).post { decidePopup(targetUrl, loaded) }
                }
            } else {
                decidePopup(targetUrl, tab.sitePrefs)
            }
        }

        private fun decidePopup(targetUrl: String, sitePrefs: SitePrefs?) {
            val protectionPrefs = tabManager?.currentProtectionPrefs ?: ProtectionPrefs()
            val decision = PopupPolicy.evaluate(
                targetUrl = targetUrl,
                currentUrl = tab.url,
                isUserGesture = isUserGesture,
                popupBlockerEnabled = protectionPrefs.popupBlocker,
                popupsSitePref = sitePrefs?.popups,
                isBlockedByEngine = { target, current ->
                    val engine = tabManager?.adblockService?.currentEngine
                    if (engine != null) {
                        val req = FilterRequest(
                            url = target,
                            sourceUrl = current,
                            type = ResourceType.POPUP,
                        )
                        engine.match(req) is MatchResult.Block
                    } else false
                }
            )

            when (decision) {
                is PopupDecision.Allow -> {
                    val newTabId = tabManager?.openTab(
                        url = targetUrl,
                        isPrivate = tab.isPrivate,
                        select = !decision.isBackground,
                    )
                    if (decision.isBackground && newTabId != null) {
                        val n = tabMerge.record(newTabId, System.currentTimeMillis())
                        tabNoticeFlow.tryEmit(n)
                        tabPulseFlow.tryEmit(Unit)
                    }
                }
                is PopupDecision.Block -> {
                    tabManager?.incrementBlocked(tab.id)
                    val host = try { Uri.parse(targetUrl).host } catch (_: Exception) { null }
                    blockedFlow.tryEmit(
                        BlockedNoticeEvent(
                            tabId = tab.id,
                            entry = BlockedEntry(
                                kind = BlockedKind.POPUP,
                                host = host,
                                targetUrl = targetUrl,
                                actionLabel = "Open",
                                onAction = {
                                    tabManager?.openTab(
                                        url = targetUrl,
                                        isPrivate = tab.isPrivate,
                                        select = true,
                                    )
                                },
                                onAlwaysAllow = {
                                    updateSitePrefs(tab, reload = false) {
                                        it.copy(popups = SitePopupPref.toStored(false))
                                    }
                                    tabManager?.openTab(
                                        url = targetUrl,
                                        isPrivate = tab.isPrivate,
                                        select = true,
                                    )
                                },
                            )
                        )
                    )
                }
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            processUrl(request.url.toString())
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            processUrl(url)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
            cleanup()
        }
    }

    transport.webView = tempWebView
    resultMsg.sendToTarget()
    return true
}

internal fun DefaultBrowserHooks.shouldOverrideNavigationImpl(
    context: Context,
    tab: TabState,
    request: WebResourceRequest,
    onAskOpenExternal: (intent: Intent, appName: String) -> Unit
): Boolean {
    val targetUrl = request.url.toString()
    val protectionPrefs = tabManager?.currentProtectionPrefs ?: ProtectionPrefs()
    val record = tabManager?.getTabRecord(tab.id)
    val isAllowedOnce = record?.allowedOnceUrls?.contains(targetUrl) == true

    val decision = RedirectPolicy.evaluate(
        targetUrl = targetUrl,
        currentUrl = tab.url,
        isForMainFrame = request.isForMainFrame,
        hasGesture = request.hasGesture(),
        isRedirect = request.isRedirect,
        isFirstLoad = record?.hasStartedFirstLoad != true,
        redirectGuardEnabled = SiteRedirectPref.effective(tab.sitePrefs?.blockRedirects, protectionPrefs.redirectGuard),
        blockAppLinksEnabled = protectionPrefs.blockAppLinks,
        isAllowedOnce = isAllowedOnce,
    )

    when (decision) {
        is RedirectDecision.BlockJavascript -> return true
        is RedirectDecision.BlockAppLink -> {
            blockedFlow.tryEmit(
                BlockedNoticeEvent(
                    tabId = tab.id,
                    entry = BlockedEntry(
                        kind = BlockedKind.APP_LINK,
                        host = null,
                        targetUrl = decision.url,
                        actionLabel = "Open",
                        onAction = {
                            openAppLink(context, decision.url, decision.fallbackUrl, tab.id)
                        },
                        onAlwaysAllow = null,
                    )
                )
            )
            return true
        }
        is RedirectDecision.AskAppLink -> {
            val intent = parseAppIntent(decision.url)
            val pm = context.packageManager
            val resolveInfo = intent?.let { pm.resolveActivity(it, 0) }
            val appName = resolveInfo?.loadLabel(pm)?.toString() ?: "an external application"
            if (intent != null) {
                pendingExternalAppDialog = ExternalAppDialogState(
                    intent = intent,
                    appName = appName,
                    fallbackUrl = decision.fallbackUrl,
                )
            } else if (decision.fallbackUrl != null) {
                tabManager?.loadUrl(tab.id, decision.fallbackUrl)
            }
            return true
        }
        is RedirectDecision.BlockRedirect -> {
            blockedFlow.tryEmit(
                BlockedNoticeEvent(
                    tabId = tab.id,
                    entry = BlockedEntry(
                        kind = BlockedKind.REDIRECT,
                        host = decision.host,
                        targetUrl = decision.targetUrl,
                        actionLabel = "Allow",
                        onAction = {
                            tabManager?.allowUrlOnce(tab.id, decision.targetUrl)
                            tabManager?.loadUrl(tab.id, decision.targetUrl)
                        },
                        onAlwaysAllow = {
                            updateSitePrefs(tab, reload = false) {
                                it.copy(blockRedirects = false)
                            }
                            tabManager?.allowUrlOnce(tab.id, decision.targetUrl)
                            tabManager?.loadUrl(tab.id, decision.targetUrl)
                        },
                    )
                )
            )
            return true
        }
        is RedirectDecision.Allow -> return false
    }
}

private fun DefaultBrowserHooks.parseAppIntent(url: String): Intent? {
    return try {
        if (url.startsWith("intent:", ignoreCase = true)) {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
            }
        } else if (url.startsWith("market:", ignoreCase = true)) {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
    } catch (_: Exception) {
        null
    }
}

private fun DefaultBrowserHooks.openAppLink(context: Context, url: String, fallbackUrl: String?, tabId: Long) {
    val intent = parseAppIntent(url)
    var started = false
    if (intent != null) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            started = true
        } catch (_: Exception) {
            started = false
        }
    }
    if (!started && fallbackUrl != null) {
        tabManager?.loadUrl(tabId, fallbackUrl)
    }
}

