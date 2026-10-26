package io.github.omeg4dev.fmhy.browser

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.ScriptletLibrary
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.AutoplayMode
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.user.SitePrefs

class BrowserWebViewFactory(
    private val adblockService: AdblockService,
    private val scriptletLibrary: ScriptletLibrary,
    private val browserHooks: BrowserHooks,
    private val bridgeName: String,
) {
    private val forceZoomHandlers = java.util.Collections.synchronizedMap(java.util.WeakHashMap<WebView, androidx.webkit.ScriptHandler>())
    @Suppress("DEPRECATION")
    fun createWebView(
        context: Context,
        record: TabRecord,
        tabManager: TabManager,
        browserPrefs: BrowserPrefs,
        protectionPrefs: ProtectionPrefs,
        appearance: Appearance,
        isAppDark: Boolean,
        onPickerResult: (tabId: Long, selector: String) -> Unit,
        onPickerCancel: (tabId: Long) -> Unit,
        onExternalAppRequest: (intent: android.content.Intent, appName: String) -> Unit,
    ): WebView {
        val webView = WebView(context)
        // WRAP_CONTENT makes the WebView report `vh`/`dvh` as 0, which collapses sites such as chat.qwen.ai to a blank page.
        webView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val settings = webView.settings
        val defaultUa = WebSettings.getDefaultUserAgent(context)

        // JavaScript
        val jsAllowed = record.sitePrefs?.javascript ?: browserPrefs.javascript
        settings.javaScriptEnabled = jsAllowed

        // Storage
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        // Media
        settings.mediaPlaybackRequiresUserGesture = browserPrefs.autoplay == AutoplayMode.BLOCK

        // Windows
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true

        // Zoom controls & layout
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

        // Text zoom
        if (browserPrefs.textZoomFollowsApp) {
            settings.textZoom = Math.round(appearance.textScale * 100)
        }

        // Force dark / Algorithmic darkening
        val shouldDarken = browserPrefs.forceDark && isAppDark && (record.sitePrefs?.dark != false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, shouldDarken)
        }

        // Desktop site
        val isDesktop = record.sitePrefs?.desktop ?: (record.desktopSite || browserPrefs.desktopByDefault)
        record.desktopSite = isDesktop
        if (isDesktop) {
            settings.userAgentString = DesktopUa.transform(defaultUa)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        } else {
            settings.userAgentString = defaultUa
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
        }

        // Safe browsing
        val safeBrowsing = protectionPrefs.safeBrowsing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = safeBrowsing
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(settings, safeBrowsing)
        }

        // Multi-profile private tabs. Without MULTI_PROFILE support private tabs share the default
        // profile (cookies/storage); TabManager then wipes cookies and WebStorage when the last
        // private tab closes. This is a documented limitation.
        var cookieManager = android.webkit.CookieManager.getInstance()
        if (record.isPrivate && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                val profile = androidx.webkit.ProfileStore.getInstance().getOrCreateProfile("private")
                WebViewCompat.setProfile(webView, "private")
                cookieManager = profile.cookieManager
            } catch (_: Exception) {}
        }

        // Third-party cookies
        cookieManager.setAcceptThirdPartyCookies(webView, !tabManager.currentPrivacyPrefs.blockThirdPartyCookies)

        // Register JS bridge
        val bridge = BrowserJsBridge(
            tabId = record.id,
            tabManager = tabManager,
            adblockService = adblockService,
            scriptletLibrary = scriptletLibrary,
            onPickerResult = onPickerResult,
            onPickerCancel = onPickerCancel,
        )
        webView.addJavascriptInterface(bridge, bridgeName)

        val cosmeticsJs = BrowserJsBridge.buildCosmeticsScript(bridgeName, sendGpc = tabManager.currentPrivacyPrefs.sendGpc)
        val videoJs = VideoJs.script(bridgeName)
        val hasDocumentStartScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (hasDocumentStartScript) {
            WebViewCompat.addDocumentStartJavaScript(webView, cosmeticsJs, setOf("*"))
            WebViewCompat.addDocumentStartJavaScript(webView, videoJs, setOf("*"))
            if (browserPrefs.forceZoom) {
                try {
                    val handle = WebViewCompat.addDocumentStartJavaScript(webView, FORCE_ZOOM_SCRIPT, setOf("*"))
                    forceZoomHandlers[webView] = handle
                } catch (_: Exception) {}
            }
        }

        // Setup Clients
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (request.isForMainFrame) {
                    record.sourceUrl = url
                    record.isBackForwardNavigation = false
                    record.zoomLevel = 1f
                    record.zoomBaseScale = 0f
                    if (BrowsePolicy.shouldBlock(
                            url = url,
                            browseWifiOnly = tabManager.currentDataUsagePrefs.browseWifiOnly,
                            kind = tabManager.currentNetworkKind,
                            tabOverride = record.id in tabManager.mobileOkTabs,
                        )
                    ) {
                        record.url = url
                        if (!EmptyTabPolicy.isStartUrl(url)) record.hadSite = true
                        tabManager.loadWifiOnlyPage(record)
                        return true
                    }
                }
                return browserHooks.shouldOverrideNavigation(context, record.toState(), request, onExternalAppRequest)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                record.sourceUrl = url
                if (!record.showsWifiOnlyPage && !url.startsWith("data:")) {
                    record.url = url
                }
                if (!record.isBackForwardNavigation) {
                    record.zoomLevel = 1f
                    record.zoomBaseScale = 0f
                }
                record.hasStartedFirstLoad = true
                // Only the displayed page's icon: drop the old one now, the new one comes with the page.
                record.favicon = null
                record.showsBlockPage = RescueDecision.sameAddress(record.blockedMainFrameUrl, url)
                (browserHooks as? DefaultBrowserHooks)?.onMainFramePageStarted(record, url)
                tabManager.onPlaybackPageStarted(record.id, url)
                tabManager.notifyTabChanged(record.id)
                tabManager.updateTabUrlAndTitle(record.id, record.url, record.title)
                (browserHooks as? DefaultBrowserHooks)?.onPageStarted(record, url)

                if (!hasDocumentStartScript) {
                    view.evaluateJavascript(cosmeticsJs, null)
                    view.evaluateJavascript(videoJs, null)
                }
            }

            // In-page navigations (pushState, e.g. GitHub between repos) don't fire onPageStarted:
            // refresh the URL and the FMHY status when the page itself changes.
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                // Back/forward availability changes on every history update (redirects, back/forward, pushState routes),
                // not only when a page finishes loading.
                val back = view.canGoBack()
                val forward = view.canGoForward()
                if (back != record.canGoBack || forward != record.canGoForward) {
                    record.canGoBack = back
                    record.canGoForward = forward
                    tabManager.notifyTabChanged(record.id)
                }
                if (isReload || url.substringBefore('#') == record.url.substringBefore('#')) return
                if (hostOf(url) != hostOf(record.url)) record.favicon = null
                record.url = url
                record.zoomBaseScale = 0f
                tabManager.updateTabUrlAndTitle(record.id, url, view.title ?: record.title)
                (browserHooks as? DefaultBrowserHooks)?.onPageStarted(record, url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                record.canGoBack = view.canGoBack()
                record.canGoForward = view.canGoForward()
                tabManager.notifyTabChanged(record.id)
                tabManager.recordHistory(record.id, url, record.title)
                (browserHooks as? DefaultBrowserHooks)?.onMainFramePageFinished(record, view, url)

                if (record.zoomLevel == 1f || record.zoomBaseScale <= 0f) {
                    @Suppress("DEPRECATION")
                    val s = view.scale
                    if (s > 0f) record.zoomBaseScale = s
                }
                if (record.isBackForwardNavigation && record.zoomLevel != 1f) {
                    val restoreAction = {
                        @Suppress("DEPRECATION")
                        val currentScale = view.scale
                        val expectedScale = record.zoomBaseScale * record.zoomLevel
                        if (currentScale > 0f && expectedScale > 0f && kotlin.math.abs(currentScale - expectedScale) > 0.01f) {
                            val factor = expectedScale / currentScale
                            record.isProgrammaticZoom = true
                            view.zoomBy(factor)
                            view.postDelayed({ record.isProgrammaticZoom = false }, 100L)
                        }
                        record.isBackForwardNavigation = false
                    }
                    try {
                        view.postVisualStateCallback(0L, object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                restoreAction()
                            }
                        })
                    } catch (_: Exception) {
                        view.post(restoreAction)
                    }
                }
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                super.onPageCommitVisible(view, url)
                if (record.zoomLevel == 1f || record.zoomBaseScale <= 0f) {
                    @Suppress("DEPRECATION")
                    val s = view.scale
                    if (s > 0f) record.zoomBaseScale = s
                }
                (browserHooks as? DefaultBrowserHooks)?.onPageCommitVisible(record, url)
            }

            override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
                super.onScaleChanged(view, oldScale, newScale)
                if (record.isProgrammaticZoom) return
                if (record.zoomBaseScale <= 0f) {
                    record.zoomBaseScale = if (oldScale > 0f) oldScale else newScale
                }
                val baseScale = if (record.zoomBaseScale > 0f) record.zoomBaseScale else 1f
                val level = newScale / baseScale
                record.zoomLevel = ZoomLogic.clamp(level)
                tabManager.notifyTabChanged(record.id)

                if (ZoomMath.isZoomOutBelowNormal(level) || ZoomMath.isZoomInAboveMax(level)) {
                    val softTarget = ZoomLogic.softLimit(level)
                    val target = ZoomLogic.clamp(softTarget)
                    val steps = 4
                    val totalFactor = target / level
                    val factorPerStep = ZoomMath.stepFactor(totalFactor, steps)
                    record.isProgrammaticZoom = true
                    var step = 0
                    val settleRunnable = object : Runnable {
                        override fun run() {
                            if (record.webView !== view) {
                                record.isProgrammaticZoom = false
                                return
                            }
                            if (step < steps) {
                                view.zoomBy(factorPerStep)
                                step++
                                if (step < steps) {
                                    view.postDelayed(this, 30L)
                                } else {
                                    record.isProgrammaticZoom = false
                                    record.zoomLevel = target
                                    tabManager.notifyTabChanged(record.id)
                                }
                            } else {
                                record.isProgrammaticZoom = false
                            }
                        }
                    }
                    view.post(settleRunnable)
                }
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.isForMainFrame) {
                    record.blockedMainFrameRule = null
                    tabManager.resetBlocked(record.id)
                }
                val response = adblockService.shouldInterceptRequest(
                    request = request,
                    sourceUrl = record.sourceUrl,
                    sitePrefs = record.sitePrefs,
                    tabAllowedOnceUrls = record.allowedOnceUrls,
                    tabBypass = record.bypass,
                    skipBlockingFor = record.skipBlockingOnceUrl,
                    onBlocked = { tabManager.incrementBlocked(record.id, it) },
                    bridgeName = bridgeName,
                    onMainFrameBlocked = { record.blockedMainFrameRule = it },
                )
                // A replaced main-frame document is the ad blocker's block page.
                if (request.isForMainFrame) {
                    record.blockedMainFrameUrl = if (response != null) request.url.toString() else null
                    if (response == null) {
                        record.blockedMainFrameRule = null
                    }
                }
                return response
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    browserHooks.onMainFrameError(record.toState(), error.errorCode, error.description?.toString().orEmpty(), request.url.toString())
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request.isForMainFrame) {
                    browserHooks.onMainFrameHttpError(record.toState(), errorResponse.statusCode, request.url.toString())
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                return tabManager.handleRenderProcessGone(view)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                val isLoading = newProgress < 100
                val progressFloat = newProgress / 100f
                tabManager.updateTabProgress(record.id, isLoading, progressFloat)
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank()) {
                    tabManager.updateTabTitle(record.id, title)
                }
            }

            override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                // Late icons of the previous page, and icons behind the block page, are dropped.
                if (record.showsBlockPage || hostOf(view.url) != hostOf(record.url)) return
                tabManager.updateTabFavicon(record.id, icon)
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                return browserHooks.handleCreateWindow(context, record.toState(), isUserGesture, resultMsg) { newUrl ->
                    tabManager.openTab(newUrl)
                }
            }

            override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
                browserHooks.onShowCustomView(view, callback)
            }

            override fun onHideCustomView() {
                browserHooks.onHideCustomView()
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            browserHooks.onDownloadStart(context, record, url, userAgent, contentDisposition, mimetype, contentLength)
        }

        webView.setOnLongClickListener {
            @Suppress("USELESS_ELVIS")
            val hitResult = webView.hitTestResult ?: return@setOnLongClickListener false
            val kind = hitKindFor(hitResult.type)
            if (kind == HitKind.OTHER) {
                return@setOnLongClickListener false
            }

            val extra = hitResult.extra
            when (kind) {
                HitKind.IMAGE -> {
                    if (extra.isNullOrBlank() || linkMenuItems(kind, extra).isEmpty()) {
                        return@setOnLongClickListener false
                    }
                    webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    tabManager.onLinkMenuRequestCallback?.invoke(
                        LinkMenuRequest(
                            tabId = record.id,
                            kind = kind,
                            url = extra,
                            imageUrl = extra,
                        )
                    )
                    true
                }
                HitKind.LINK -> {
                    if (!extra.isNullOrBlank()) {
                        if (linkMenuItems(kind, extra).isEmpty()) {
                            return@setOnLongClickListener false
                        }
                        webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        tabManager.onLinkMenuRequestCallback?.invoke(
                            LinkMenuRequest(
                                tabId = record.id,
                                kind = kind,
                                url = extra,
                                imageUrl = null,
                            )
                        )
                        true
                    } else {
                        webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        val handler = object : Handler(Looper.getMainLooper()) {
                            override fun handleMessage(msg: Message) {
                                val href = msg.data?.getString("url")?.takeIf { it.isNotBlank() }
                                val src = msg.data?.getString("src")?.takeIf { it.isNotBlank() }
                                if (href != null && linkMenuItems(kind, href).isNotEmpty()) {
                                    tabManager.onLinkMenuRequestCallback?.invoke(
                                        LinkMenuRequest(
                                            tabId = record.id,
                                            kind = kind,
                                            url = href,
                                            imageUrl = src,
                                        )
                                    )
                                }
                            }
                        }
                        val msg = handler.obtainMessage()
                        webView.requestFocusNodeHref(msg)
                        true
                    }
                }
                HitKind.IMAGE_LINK -> {
                    webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    val handler = object : Handler(Looper.getMainLooper()) {
                        override fun handleMessage(msg: Message) {
                            val href = msg.data?.getString("url")?.takeIf { it.isNotBlank() }
                            val src = msg.data?.getString("src")?.takeIf { it.isNotBlank() } ?: extra
                            if (href != null && linkMenuItems(kind, href).isNotEmpty()) {
                                tabManager.onLinkMenuRequestCallback?.invoke(
                                    LinkMenuRequest(
                                        tabId = record.id,
                                        kind = kind,
                                        url = href,
                                        imageUrl = src,
                                    )
                                )
                            } else if (!src.isNullOrBlank() && linkMenuItems(HitKind.IMAGE, src).isNotEmpty()) {
                                tabManager.onLinkMenuRequestCallback?.invoke(
                                    LinkMenuRequest(
                                        tabId = record.id,
                                        kind = HitKind.IMAGE,
                                        url = src,
                                        imageUrl = src,
                                    )
                                )
                            }
                        }
                    }
                    val msg = handler.obtainMessage()
                    webView.requestFocusNodeHref(msg)
                    true
                }
                HitKind.OTHER -> false
            }
        }

        return webView
    }

    fun applySettings(
        webView: WebView,
        record: TabRecord,
        browserPrefs: BrowserPrefs,
        protectionPrefs: ProtectionPrefs,
        appearance: Appearance,
        isAppDark: Boolean
    ) {
        val settings = webView.settings
        val defaultUa = WebSettings.getDefaultUserAgent(webView.context)
        settings.mediaPlaybackRequiresUserGesture = browserPrefs.autoplay == AutoplayMode.BLOCK

        val jsAllowed = record.sitePrefs?.javascript ?: browserPrefs.javascript
        settings.javaScriptEnabled = jsAllowed

        if (browserPrefs.textZoomFollowsApp) {
            settings.textZoom = Math.round(appearance.textScale * 100)
        }

        val shouldDarken = browserPrefs.forceDark && isAppDark && (record.sitePrefs?.dark != false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, shouldDarken)
        }

        val isDesktop = record.sitePrefs?.desktop ?: (record.desktopSite || browserPrefs.desktopByDefault)
        record.desktopSite = isDesktop
        if (isDesktop) {
            settings.userAgentString = DesktopUa.transform(defaultUa)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        } else {
            settings.userAgentString = defaultUa
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
        }

        // Zoom controls & layout
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            if (browserPrefs.forceZoom) {
                if (!forceZoomHandlers.containsKey(webView)) {
                    try {
                        val handle = WebViewCompat.addDocumentStartJavaScript(webView, FORCE_ZOOM_SCRIPT, setOf("*"))
                        forceZoomHandlers[webView] = handle
                    } catch (_: Exception) {}
                }
            } else {
                forceZoomHandlers.remove(webView)?.remove()
            }
        }
    }

    companion object {
        internal fun hitKindFor(hitTestResultType: Int): HitKind = when (hitTestResultType) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> HitKind.LINK
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> HitKind.IMAGE_LINK
            WebView.HitTestResult.IMAGE_TYPE -> HitKind.IMAGE
            else -> HitKind.OTHER
        }

        const val FORCE_ZOOM_SCRIPT: String = """
            (function() {
                function rewriteViewport() {
                    var metas = document.querySelectorAll('meta[name="viewport"]');
                    for (var i = 0; i < metas.length; i++) {
                        var meta = metas[i];
                        var content = meta.getAttribute('content');
                        if (!content) continue;
                        var parts = content.split(',');
                        var newParts = [];
                        for (var j = 0; j < parts.length; j++) {
                            var p = parts[j].trim();
                            var lower = p.replace(/\s+/g, '').toLowerCase();
                            if (lower.indexOf('user-scalable') === 0 || lower.indexOf('maximum-scale') === 0 || lower.indexOf('minimum-scale') === 0) {
                                continue;
                            }
                            if (p.length > 0) newParts.push(p);
                        }
                        newParts.push('user-scalable=yes');
                        newParts.push('maximum-scale=5');
                        newParts.push('minimum-scale=1');
                        var newContent = newParts.join(', ');
                        if (content !== newContent) {
                            meta.setAttribute('content', newContent);
                        }
                    }
                }
                var observing = false;
                function tryObserve() {
                    rewriteViewport();
                    if (!observing && window.MutationObserver && document.documentElement) {
                        observing = true;
                        var observer = new MutationObserver(function() {
                            rewriteViewport();
                        });
                        observer.observe(document.documentElement, {
                            childList: true,
                            subtree: true,
                            attributes: true,
                            attributeFilter: ['content']
                        });
                    }
                }
                tryObserve();
                if (!observing) {
                    document.addEventListener('readystatechange', tryObserve);
                    window.addEventListener('DOMContentLoaded', tryObserve);
                }
            })();
        """
    }
}

private fun hostOf(url: String?): String? =
    try { android.net.Uri.parse(url ?: return null).host?.lowercase() } catch (_: Exception) { null }
