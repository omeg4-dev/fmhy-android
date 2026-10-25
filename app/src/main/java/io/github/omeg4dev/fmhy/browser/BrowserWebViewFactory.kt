package io.github.omeg4dev.fmhy.browser

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Message
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
        }

        // Setup Clients
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.isForMainFrame) {
                    record.sourceUrl = request.url.toString()
                }
                return browserHooks.shouldOverrideNavigation(context, record.toState(), request, onExternalAppRequest)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                record.sourceUrl = url
                record.url = url
                record.hasStartedFirstLoad = true
                // Only the displayed page's icon: drop the old one now, the new one comes with the page.
                record.favicon = null
                record.showsBlockPage = RescueDecision.sameAddress(record.blockedMainFrameUrl, url)
                (browserHooks as? DefaultBrowserHooks)?.onMainFramePageStarted(record, url)
                tabManager.onPlaybackPageStarted(record.id, url)
                tabManager.notifyTabChanged(record.id)
                tabManager.updateTabUrlAndTitle(record.id, url, record.title)
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
                if (isReload || url.substringBefore('#') == record.url.substringBefore('#')) return
                if (hostOf(url) != hostOf(record.url)) record.favicon = null
                record.url = url
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
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                super.onPageCommitVisible(view, url)
                (browserHooks as? DefaultBrowserHooks)?.onPageCommitVisible(record, url)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.isForMainFrame) {
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
                )
                // A replaced main-frame document is the ad blocker's block page.
                if (request.isForMainFrame) {
                    record.blockedMainFrameUrl = if (response != null) request.url.toString() else null
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
            browserHooks.onDownloadStart(context, url, userAgent, contentDisposition, mimetype, contentLength)
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
    }
}

private fun hostOf(url: String?): String? =
    try { android.net.Uri.parse(url ?: return null).host?.lowercase() } catch (_: Exception) { null }
