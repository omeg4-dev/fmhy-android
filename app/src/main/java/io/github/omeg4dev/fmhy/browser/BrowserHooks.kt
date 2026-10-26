package io.github.omeg4dev.fmhy.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.browser.download.DownloadOffer
import io.github.omeg4dev.fmhy.browser.download.DownloadOfferText
import io.github.omeg4dev.fmhy.browser.download.DownloadPolicy
import io.github.omeg4dev.fmhy.browser.download.DownloadRequest
import io.github.omeg4dev.fmhy.browser.download.DownloadSourceStatus
import io.github.omeg4dev.fmhy.browser.download.NewDownload
import io.github.omeg4dev.fmhy.browser.download.buildOffer
import kotlinx.coroutines.flow.first
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.PasteAction
import io.github.omeg4dev.fmhy.ui.common.PasteDecodeUi
import io.github.omeg4dev.fmhy.ui.common.linkText
import io.github.omeg4dev.fmhy.ui.common.rememberPasteDecoder
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.adblock.FilterRequest
import io.github.omeg4dev.fmhy.data.adblock.MatchResult
import io.github.omeg4dev.fmhy.data.adblock.ResourceType
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.safety.SafeGuard
import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import io.github.omeg4dev.fmhy.data.safety.Verdict
import io.github.omeg4dev.fmhy.ui.common.EntryDetailBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class BrowserSnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

data class ExternalAppDialogState(
    val intent: Intent,
    val appName: String,
    val fallbackUrl: String?,
)

interface BrowserHooks {
    fun onMainFrameError(tab: TabState, errorCode: Int, description: String, failingUrl: String) {}
    fun onMainFrameHttpError(tab: TabState, statusCode: Int, failingUrl: String) {}
    fun handleCreateWindow(
        context: Context,
        tab: TabState,
        isUserGesture: Boolean,
        resultMsg: Message?,
        openNewTab: (url: String) -> Unit
    ): Boolean

    fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {}
    fun onHideCustomView() {}

    fun onDownloadStart(
        context: Context,
        record: TabRecord,
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    )

    /** Small FMHY status icon (with its tap popover) for the address pill; draws nothing without a state. */
    val siteStatusIcon: @Composable (TabState) -> Unit

    fun shouldOverrideNavigation(
        context: Context,
        tab: TabState,
        request: WebResourceRequest,
        onAskOpenExternal: (intent: Intent, appName: String) -> Unit
    ): Boolean
}

class DefaultBrowserHooks(
    private val datasetStore: DatasetStore? = null,
) : BrowserHooks {
    var tabManager: TabManager? = null
        private set
    var safeGuard: SafeGuard? = null
        private set

    fun attachTabManager(manager: TabManager) {
        tabManager = manager
        if (datasetStore != null) {
            safeGuard = SafeGuard(datasetStore)
        }
    }

    val snackbarFlow = MutableSharedFlow<BrowserSnackbarEvent>(extraBufferCapacity = 10)
    var pendingDownload by mutableStateOf<DownloadOffer?>(null)
    val downloadStarted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var appContext: Context? = null
    var pendingExternalAppDialog by mutableStateOf<ExternalAppDialogState?>(null)
    var customView by mutableStateOf<View?>(null)
    var customViewCallback: WebChromeClient.CustomViewCallback? = null
    var onShowCustomViewListener: ((View) -> Unit)? = null
    var onHideCustomViewListener: (() -> Unit)? = null

    /** Verdicts per page URL (fragment dropped): shared hosts like github.com differ per path. */
    private val verdictCache = object : LinkedHashMap<String, Verdict>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Verdict>?) = size > 64
    }
    private val livePopupCount = java.util.concurrent.atomic.AtomicInteger(0)

    override fun handleCreateWindow(
        context: Context,
        tab: TabState,
        isUserGesture: Boolean,
        resultMsg: Message?,
        openNewTab: (url: String) -> Unit
    ): Boolean {
        if (resultMsg == null) return false
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        if (livePopupCount.get() >= 3) return false

        livePopupCount.incrementAndGet()
        val tempWebView = WebView(context)
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

            private fun processUrl(targetUrl: String) {
                if (handled || targetUrl.isBlank() || targetUrl == "about:blank") return
                handled = true
                cleanup()

                val protectionPrefs = tabManager?.currentProtectionPrefs ?: ProtectionPrefs()
                val decision = PopupPolicy.evaluate(
                    targetUrl = targetUrl,
                    currentUrl = tab.url,
                    isUserGesture = isUserGesture,
                    popupBlockerEnabled = protectionPrefs.popupBlocker,
                    popupsSitePref = tab.sitePrefs?.popups,
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
                        tabManager?.openTab(
                            url = targetUrl,
                            isPrivate = tab.isPrivate,
                            select = !decision.isBackground,
                        )
                    }
                    is PopupDecision.Block -> {
                        tabManager?.incrementBlocked(tab.id)
                        snackbarFlow.tryEmit(
                            BrowserSnackbarEvent(
                                message = "Pop-up blocked",
                                actionLabel = "Open",
                                onAction = {
                                    tabManager?.openTab(
                                        url = targetUrl,
                                        isPrivate = tab.isPrivate,
                                        select = true,
                                    )
                                }
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

    override fun shouldOverrideNavigation(
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
            redirectGuardEnabled = protectionPrefs.redirectGuard,
            blockAppLinksEnabled = protectionPrefs.blockAppLinks,
            isAllowedOnce = isAllowedOnce,
        )

        when (decision) {
            is RedirectDecision.BlockJavascript -> return true
            is RedirectDecision.BlockAppLink -> {
                snackbarFlow.tryEmit(
                    BrowserSnackbarEvent(
                        message = "Blocked a link that opens an app",
                        actionLabel = "Open",
                        onAction = {
                            openAppLink(context, decision.url, decision.fallbackUrl, tab.id)
                        }
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
                snackbarFlow.tryEmit(
                    BrowserSnackbarEvent(
                        message = "Blocked a redirect to ${decision.host}",
                        actionLabel = "Allow",
                        onAction = {
                            tabManager?.allowUrlOnce(tab.id, decision.targetUrl)
                            tabManager?.loadUrl(tab.id, decision.targetUrl)
                        }
                    )
                )
                return true
            }
            is RedirectDecision.Allow -> return false
        }
    }

    private fun parseAppIntent(url: String): Intent? {
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

    private fun openAppLink(context: Context, url: String, fallbackUrl: String?, tabId: Long) {
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

    override fun onDownloadStart(
        context: Context,
        record: TabRecord,
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ) {
        appContext = context.applicationContext
        val tm = tabManager ?: return
        val app = context.applicationContext as? FmhyApp ?: return
        val controller = app.container.downloads

        val fileName = DownloadRequest.fileName(url, contentDisposition, mimeType)
        val host = DownloadRequest.hostOf(url)
        val referer = record.url.takeIf { DownloadRequest.isHttpUrl(it) }
        val isPrivate = record.isPrivate

        val protectionPrefs = tm.currentProtectionPrefs
        val dataUsagePrefs = tm.currentDataUsagePrefs
        val networkKind = tm.currentNetworkKind

        tm.appScope.launch(Dispatchers.IO) {
            val sg = safeGuard
            suspend fun getVerdict(checkUrl: String): Verdict? {
                if (sg == null) return null
                val key = checkUrl.substringBefore('#')
                return synchronized(verdictCache) { verdictCache[key] }
                    ?: sg.check(checkUrl).also { synchronized(verdictCache) { verdictCache[key] = it } }
            }

            val fileVerdict = getVerdict(url)
            val pageVerdict = referer?.let { getVerdict(it) }

            var verdict = fileVerdict
            if (verdict?.status == SafetyStatus.NO_DATA && referer != null) {
                val refererVerdict = pageVerdict ?: getVerdict(referer)
                if (refererVerdict != null) {
                    verdict = refererVerdict
                }
            }

            val rule = (tm.adblockService.currentEngine?.match(
                FilterRequest(url = url, sourceUrl = referer ?: url, type = ResourceType.DOCUMENT)
            ) as? MatchResult.Block)?.rule

            val interstitial = InterstitialMerge.combine(
                host = host,
                url = url,
                verdict = verdict,
                unsafeWarning = protectionPrefs.unsafeWarning,
                potentiallyUnsafeWarning = protectionPrefs.potentiallyUnsafeWarning,
                hostAllowed = record.allowedHosts.contains(host),
                filterRule = rule,
            )

            val folderLabel = controller.targetLabel()

            val fmhyEnabled = runCatching { app.container.checks.prefs.value.fmhyDownloadStatus }
                .getOrElse { runCatching { app.container.settingsRepository.checkPrefs.first().fmhyDownloadStatus }.getOrDefault(true) }

            val sourceStatus = if (fmhyEnabled && sg != null && fileVerdict != null) {
                val wikiDateMs = datasetStore?.meta()?.get("last_sync")?.toLongOrNull()
                val pageHost = referer?.let { DownloadRequest.hostOf(it) }
                DownloadSourceStatus.from(
                    fileVerdict = fileVerdict,
                    pageVerdict = pageVerdict,
                    fileHost = host,
                    pageHost = pageHost,
                    wikiDateMs = wikiDateMs,
                )
            } else {
                null
            }

            val offer = buildOffer(
                url = url,
                fileName = fileName,
                totalBytes = contentLength,
                mimeType = mimeType,
                host = host,
                referer = referer,
                userAgent = userAgent,
                isPrivate = isPrivate,
                warnRiskyDownloads = protectionPrefs.warnRiskyDownloads,
                downloadWifiOnly = dataUsagePrefs.downloadWifiOnly,
                networkKind = networkKind,
                folderLabel = folderLabel,
                interstitial = interstitial,
                askBeforeDownload = protectionPrefs.askBeforeDownload,
                lastTouchAt = record.lastTouchAt,
                nowMs = System.currentTimeMillis(),
                sourceStatus = sourceStatus,
            )

            if (offer.interstitial != null || offer.needsConfirmation) {
                withContext(Dispatchers.Main) {
                    pendingDownload = offer
                }
            } else {
                controller.enqueue(
                    NewDownload(
                        url = offer.url,
                        fileName = offer.fileName,
                        mimeType = offer.mimeType,
                        totalBytes = offer.totalBytes,
                        sourceHost = offer.host,
                        referer = offer.referer,
                        userAgent = offer.userAgent,
                        isPrivate = offer.isPrivate,
                        allowMobile = false,
                        fmhy = offer.sourceStatus,
                    )
                )
                downloadStarted.tryEmit(Unit)
                snackbarFlow.tryEmit(
                    BrowserSnackbarEvent(
                        message = DownloadOfferText.started(offer.fileName, offer.waitsForWifi)
                    )
                )
            }
        }
    }

    fun offerPastedLink(context: Context, url: String, isPrivate: Boolean) {
        appContext = context.applicationContext
        val tm = tabManager ?: return
        val app = context.applicationContext as? FmhyApp ?: return
        val controller = app.container.downloads

        val fileName = DownloadRequest.fileName(url, null, null)
        val host = DownloadRequest.hostOf(url)
        val protectionPrefs = tm.currentProtectionPrefs
        val dataUsagePrefs = tm.currentDataUsagePrefs
        val networkKind = tm.currentNetworkKind

        tm.appScope.launch(Dispatchers.IO) {
            val sg = safeGuard
            suspend fun getVerdict(checkUrl: String): Verdict? {
                if (sg == null) return null
                val key = checkUrl.substringBefore('#')
                return synchronized(verdictCache) { verdictCache[key] }
                    ?: sg.check(checkUrl).also { synchronized(verdictCache) { verdictCache[key] = it } }
            }

            val fileVerdict = getVerdict(url)
            val verdict = fileVerdict
            val rule = (tm.adblockService.currentEngine?.match(
                FilterRequest(url = url, sourceUrl = url, type = ResourceType.DOCUMENT)
            ) as? MatchResult.Block)?.rule

            val interstitial = InterstitialMerge.combine(
                host = host,
                url = url,
                verdict = verdict,
                unsafeWarning = protectionPrefs.unsafeWarning,
                potentiallyUnsafeWarning = protectionPrefs.potentiallyUnsafeWarning,
                hostAllowed = false,
                filterRule = rule,
            )

            val folderLabel = controller.targetLabel()

            val fmhyEnabled = runCatching { app.container.checks.prefs.value.fmhyDownloadStatus }
                .getOrElse { runCatching { app.container.settingsRepository.checkPrefs.first().fmhyDownloadStatus }.getOrDefault(true) }

            val sourceStatus = if (fmhyEnabled && sg != null && fileVerdict != null) {
                val wikiDateMs = datasetStore?.meta()?.get("last_sync")?.toLongOrNull()
                DownloadSourceStatus.from(
                    fileVerdict = fileVerdict,
                    pageVerdict = null,
                    fileHost = host,
                    pageHost = null,
                    wikiDateMs = wikiDateMs,
                )
            } else {
                null
            }

            val offer = buildOffer(
                url = url,
                fileName = fileName,
                totalBytes = -1L,
                mimeType = "",
                host = host,
                referer = null,
                userAgent = null,
                isPrivate = isPrivate,
                warnRiskyDownloads = protectionPrefs.warnRiskyDownloads,
                downloadWifiOnly = dataUsagePrefs.downloadWifiOnly,
                networkKind = networkKind,
                folderLabel = folderLabel,
                interstitial = interstitial,
                askBeforeDownload = protectionPrefs.askBeforeDownload,
                lastTouchAt = 0L,
                nowMs = System.currentTimeMillis(),
                forceConfirmation = true,
                sourceStatus = sourceStatus,
            )

            withContext(Dispatchers.Main) {
                pendingDownload = offer
            }
        }
    }

    fun confirmDownload(offer: DownloadOffer, allowMobile: Boolean) {
        pendingDownload = null
        val app = appContext as? FmhyApp ?: return
        val controller = app.container.downloads
        tabManager?.appScope?.launch(Dispatchers.IO) {
            controller.enqueue(
                NewDownload(
                    url = offer.url,
                    fileName = offer.fileName,
                    mimeType = offer.mimeType,
                    totalBytes = offer.totalBytes,
                    sourceHost = offer.host,
                    referer = offer.referer,
                    userAgent = offer.userAgent,
                    isPrivate = offer.isPrivate,
                    allowMobile = allowMobile,
                    fmhy = offer.sourceStatus,
                )
            )
            downloadStarted.tryEmit(Unit)
        }
        snackbarFlow.tryEmit(
            BrowserSnackbarEvent(
                message = DownloadOfferText.started(offer.fileName, offer.waitsForWifi && !allowMobile)
            )
        )
    }

    override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        customView = view
        customViewCallback = callback
        onShowCustomViewListener?.invoke(view)
    }

    override fun onHideCustomView() {
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        onHideCustomViewListener?.invoke()
    }

    override fun onMainFrameError(tab: TabState, errorCode: Int, description: String, failingUrl: String) {
        val browserPrefs = tabManager?.currentBrowserPrefs ?: BrowserPrefs()
        rescueLog("error $errorCode '$description' url=$failingUrl tab=${tab.url}")
        if (!browserPrefs.deadSiteRescue) return
        if (!RescueReason.shouldTrigger(errorCode, description)) return
        val record = tabManager?.getTabRecord(tab.id) ?: return
        record.failedLoad = FailedLoad(
            url = failingUrl,
            reason = RescueReason.fromErrorCode(errorCode, description),
            isHttp = false,
            kind = RescueReason.kindForError(errorCode, tabManager?.isOnline() ?: true),
        )
    }

    override fun onMainFrameHttpError(tab: TabState, statusCode: Int, failingUrl: String) {
        val browserPrefs = tabManager?.currentBrowserPrefs ?: BrowserPrefs()
        rescueLog("http $statusCode url=$failingUrl tab=${tab.url}")
        if (!browserPrefs.deadSiteRescue) return
        if (!RescueReason.shouldTriggerHttp(statusCode) && !RescueReason.shouldTriggerRefused(statusCode)) return
        val record = tabManager?.getTabRecord(tab.id) ?: return
        record.failedLoad = FailedLoad(
            url = failingUrl,
            reason = RescueReason.fromHttpStatus(statusCode),
            isHttp = true,
            kind = RescueReason.kindForHttp(statusCode),
        )
    }

    /** A new document starts in the main frame (not called for in-page navigations). */
    fun onMainFramePageStarted(record: TabRecord, url: String) {
        if (!RescueDecision.sameAddress(url, record.skipBlockingOnceUrl)) {
            record.skipBlockingOnceUrl = null
        }
        record.rescueJob?.cancel()
        record.rescueJob = null
        record.stallJob?.cancel()
        record.stallJob = null
        record.blankJob?.cancel()
        record.blankJob = null
        record.contentShown = false
        if (!RescueDecision.sameAddress(url, record.failedLoad?.url)) record.failedLoad = null
        if (!RescueDecision.sameAddress(url, record.silentRetryUrl)) {
            record.silentRetries = 0
            record.silentRetryUrl = url
        }
        // The rescue stays through retries of its own address; going anywhere else closes it.
        val rescue = record.rescueState
        if (rescue != null && !RescueDecision.sameAddress(url, rescue.failingUrl)) {
            tabManager?.setRescueState(record.id, null)
        }

        if (RescueDecision.isWebPage(url)) {
            val appScope = tabManager?.appScope
            if (appScope != null) {
                record.stallJob = appScope.launch(Dispatchers.Main) {
                    delay(RescueDecision.STALL_MS)
                    val deadSiteRescue = tabManager?.currentBrowserPrefs?.deadSiteRescue ?: false
                    if (!record.contentShown && record.isLoading && RescueDecision.sameAddress(record.url, url) && record.rescueState == null && deadSiteRescue) {
                        buildRescueState(
                            record = record,
                            failingUrl = url,
                            reason = RescueReason.title(LoadFailureKind.TIMEOUT),
                            kind = LoadFailureKind.TIMEOUT,
                            ignoreContentShown = true,
                        )
                    }
                }
            }
        }
    }

    /**
     * First paint of the new document: unless an error is known for it, the page has content. The
     * rescue itself only closes in onPageFinished, so a late error never makes it flash.
     */
    fun onPageCommitVisible(record: TabRecord, url: String) {
        if (RescueDecision.sameAddress(url, record.failedLoad?.url)) return
        record.contentShown = true
        record.stallJob?.cancel()
        record.stallJob = null
    }

    private suspend fun evaluate(view: WebView?, js: String): String? {
        if (view == null) return null
        return suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(js) { raw -> if (continuation.isActive) continuation.resume(raw) }
        }
    }

    private suspend fun evaluateProbe(view: WebView?): RescueDecision.PageProbe? {
        if (view == null) return null
        return suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(RescueDecision.PROBE_JS) { raw ->
                if (continuation.isActive) {
                    continuation.resume(RescueDecision.parseProbe(raw))
                }
            }
        }
    }

    /** End of a load attempt; every main-frame error of the attempt has arrived by now. */
    fun onMainFramePageFinished(record: TabRecord, view: WebView, url: String) {
        record.stallJob?.cancel()
        record.stallJob = null
        val failed = record.failedLoad?.takeIf { RescueDecision.sameAddress(it.url, url) }
        record.failedLoad = null
        if (failed == null) {
            if (RescueDecision.sameAddress(url, record.url)) onContentShown(record, url)
            val browserPrefs = tabManager?.currentBrowserPrefs ?: BrowserPrefs()
            if (RescueDecision.isWebPage(url) && !record.showsBlockPage && browserPrefs.deadSiteRescue) {
                record.blankJob?.cancel()
                val appScope = tabManager?.appScope
                if (appScope != null) {
                    record.blankJob = appScope.launch(Dispatchers.Main) {
                        delay(RescueDecision.BLANK_FIRST_MS)
                        if (!RescueDecision.sameAddress(record.url, url)) return@launch
                        val probe1 = evaluateProbe(record.webView) ?: return@launch
                        if (!RescueDecision.sameAddress(record.url, url)) return@launch
                        if (!RescueDecision.isBlank(probe1)) return@launch

                        delay(RescueDecision.BLANK_SECOND_MS)
                        if (!RescueDecision.sameAddress(record.url, url)) return@launch
                        val probe2 = evaluateProbe(record.webView) ?: return@launch
                        if (!RescueDecision.sameAddress(record.url, url)) return@launch
                        if (!RescueDecision.isBlank(probe2)) return@launch
                        if (RescueDecision.parseFlag(evaluate(record.webView, RescueDecision.CENTER_DRAWN_JS))) return@launch

                        val kind = RescueDecision.blankKind(record.blocked)
                        buildRescueState(
                            record = record,
                            failingUrl = url,
                            reason = RescueReason.title(kind),
                            kind = kind,
                            ignoreContentShown = true,
                        )

                        for (i in 0 until RescueDecision.BLANK_RECHECKS) {
                            delay(RescueDecision.BLANK_RECHECK_MS)
                            if (!RescueDecision.sameAddress(record.url, url)) return@launch
                            val recheck = evaluateProbe(record.webView) ?: return@launch
                            if (!RescueDecision.sameAddress(record.url, url)) return@launch
                            if (!RescueDecision.isBlank(recheck)) {
                                tabManager?.setRescueState(record.id, null)
                                return@launch
                            }
                        }
                    }
                }
            }
            return
        }
        val scope = tabManager?.appScope ?: return
        record.rescueJob?.cancel()
        if (!failed.isHttp) {
            // The WebView shows its own error page: nothing of the site is on screen.
            record.contentShown = false
            decideRescue(record, failed)
            return
        }
        // A 5xx body is on screen. Challenge pages move on by themselves; real pages served with an
        // error status have content. Only a bare error page counts as a failure.
        record.rescueJob = scope.launch(Dispatchers.Main) {
            delay(RescueDecision.HTTP_SETTLE_MS)
            if (!RescueDecision.sameAddress(record.url, failed.url)) return@launch

            val runProbe = {
                view.evaluateJavascript(RescueDecision.PROBE_JS) { raw ->
                    if (!RescueDecision.sameAddress(record.url, failed.url)) return@evaluateJavascript
                    val probe = RescueDecision.parseProbe(raw)
                    rescueLog("probe $probe url=${failed.url}")
                    if (RescueDecision.looksLikeContent(probe)) {
                        onContentShown(record, failed.url)
                    } else {
                        decideRescue(record, failed)
                    }
                }
            }

            if (failed.kind == LoadFailureKind.REFUSED) {
                view.evaluateJavascript(RescueDecision.VERIFICATION_JS) { raw ->
                    if (!RescueDecision.sameAddress(record.url, failed.url)) return@evaluateJavascript
                    if (RescueDecision.parseFlag(raw)) {
                        onContentShown(record, failed.url)
                    } else {
                        runProbe()
                    }
                }
            } else {
                runProbe()
            }
        }
    }

    private fun onContentShown(record: TabRecord, url: String) {
        record.contentShown = true
        record.silentRetries = 0
        record.silentRetryUrl = url
        if (record.rescueState != null) {
            rescueLog("content shown, rescue closes url=$url")
            tabManager?.setRescueState(record.id, null)
        }
    }

    private fun decideRescue(record: TabRecord, failed: FailedLoad) {
        val action = RescueDecision.decide(
            isForMainFrame = true,
            failingUrl = failed.url,
            currentUrls = listOf(record.url, record.sourceUrl),
            contentShown = record.contentShown,
            retriesDone = record.silentRetries,
        )
        rescueLog("decide $action url=${failed.url} tab=${record.url} shown=${record.contentShown} retries=${record.silentRetries} (${failed.reason})")
        when (action) {
            RescueDecision.Action.IGNORE -> Unit
            RescueDecision.Action.RETRY -> {
                val scope = tabManager?.appScope ?: return
                record.rescueJob = scope.launch(Dispatchers.Main) {
                    delay(RescueDecision.retryDelayMs(record.silentRetries))
                    record.silentRetries++
                    record.silentRetryUrl = failed.url
                    record.webView?.reload()
                }
            }
            RescueDecision.Action.SHOW -> buildRescueState(
                record = record,
                failingUrl = failed.url,
                reason = failed.reason,
                kind = failed.kind,
            )
        }
    }

    private fun adblockActive(record: TabRecord): Boolean {
        val tm = tabManager ?: return false
        return tm.adblockService.currentBlockingPrefs.adblock && record.sitePrefs?.adblock != false
    }

    private fun buildRescueState(
        record: TabRecord,
        failingUrl: String,
        reason: String,
        kind: LoadFailureKind = LoadFailureKind.UNKNOWN,
        ignoreContentShown: Boolean = false,
    ) {
        val canSkipBlocking = kind != LoadFailureKind.OFFLINE && adblockActive(record)
        // Same address already rescued (e.g. the user pressed Retry): update it in place, no flash.
        val current = record.rescueState
        if (current != null && RescueDecision.sameAddress(current.failingUrl, failingUrl)) {
            tabManager?.setRescueState(
                record.id,
                current.copy(
                    reason = reason,
                    retrying = false,
                    kind = kind,
                    canSkipBlocking = canSkipBlocking,
                )
            )
            return
        }
        val host = try { Uri.parse(failingUrl).host ?: failingUrl } catch (_: Exception) { failingUrl }
        val entryId = record.entryId
        record.rescueJob = tabManager?.appScope?.launch(Dispatchers.IO) {
            val store = datasetStore
            val entry = if (entryId != null) {
                store?.entry(entryId)
            } else {
                safeGuard?.check(failingUrl)?.matches?.firstOrNull()
            }
            val mirrors = entry?.mirrors.orEmpty()
            val alternatives = if (store != null && entry?.headingId != null) {
                store.entriesForSection(entry.sectionId)
                    .filter { it.headingId == entry.headingId && it.id != entry.id }
                    .sortedWith(
                        compareByDescending<Entry> { it.tier == Tier.STAR || it.starredAt != null }
                            .thenBy { it.ord }
                    )
                    .take(8)
            } else {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                // The page may have loaded or the tab moved on while the lookup ran.
                if (!ignoreContentShown && record.contentShown) return@withContext
                if (listOf(record.url, record.sourceUrl).none { RescueDecision.sameAddress(it, failingUrl) }) return@withContext
                rescueLog("SHOW rescue url=$failingUrl ($reason)")
                tabManager?.setRescueState(
                    record.id,
                    RescueState(
                        failingUrl = failingUrl,
                        host = host,
                        reason = reason,
                        mirrors = mirrors,
                        alternatives = alternatives,
                        kind = kind,
                        canSkipBlocking = canSkipBlocking,
                    )
                )
            }
        }
    }

    /** User pressed Retry: keep the rescue up while loading; no extra silent rounds. */
    fun retryRescue(record: TabRecord) {
        val rescue = record.rescueState ?: return
        record.silentRetries = RescueDecision.MAX_SILENT_RETRIES
        record.silentRetryUrl = rescue.failingUrl
        tabManager?.setRescueState(record.id, rescue.copy(retrying = true))
        tabManager?.loadUrl(record.id, rescue.failingUrl)
    }

    fun reloadWithoutBlocking(record: TabRecord) {
        val rescue = record.rescueState ?: return
        record.skipBlockingOnceUrl = rescue.failingUrl
        record.silentRetries = RescueDecision.MAX_SILENT_RETRIES
        record.silentRetryUrl = rescue.failingUrl
        tabManager?.setRescueState(record.id, rescue.copy(retrying = true))
        tabManager?.loadUrl(record.id, rescue.failingUrl)
    }

    fun onPageStarted(record: TabRecord, url: String) {
        val browserPrefs = tabManager?.currentBrowserPrefs ?: BrowserPrefs()
        val protectionPrefs = tabManager?.currentProtectionPrefs ?: ProtectionPrefs()
        val host = try { Uri.parse(url).host } catch (_: Exception) { null }
        if (host == null) {
            tabManager?.setSiteBarState(record.id, null)
            tabManager?.setInterstitialState(record.id, null)
            return
        }

        val filterRule = record.blockedMainFrameRule?.takeIf { RescueDecision.sameAddress(record.blockedMainFrameUrl, url) }

        tabManager?.appScope?.launch(Dispatchers.IO) {
            val sg = safeGuard
            val key = url.substringBefore('#')
            val verdict = if (sg != null) {
                synchronized(verdictCache) { verdictCache[key] }
                    ?: sg.check(url).also { synchronized(verdictCache) { verdictCache[key] = it } }
            } else {
                null
            }

            withContext(Dispatchers.Main) {
                // Drop stale results when the tab already moved on to another host.
                val currentHost = try { Uri.parse(record.url).host } catch (_: Exception) { null }
                if (currentHost != null && !currentHost.equals(host, ignoreCase = true)) return@withContext

                tabManager?.setInterstitialState(
                    record.id,
                    InterstitialMerge.combine(
                        host = host,
                        url = url,
                        verdict = verdict,
                        unsafeWarning = protectionPrefs.unsafeWarning,
                        potentiallyUnsafeWarning = protectionPrefs.potentiallyUnsafeWarning,
                        hostAllowed = record.allowedHosts.contains(host),
                        filterRule = filterRule,
                    )
                )

                // SiteBar check
                if (browserPrefs.siteBar && verdict != null) {
                    val isRedirected = record.entryHost != null && !record.entryHost.equals(host, ignoreCase = true)
                    val isDismissed = record.dismissedSiteBarHosts.contains(host)
                    val sectionTitle = verdict.matches.firstOrNull()?.sectionId?.let { secId ->
                        datasetStore?.sections()?.firstOrNull { it.id == secId }?.title
                    }
                    val siteBarState = SiteBarModel.from(
                        verdict = verdict,
                        isRedirectedFromEntry = isRedirected,
                        isDismissed = isDismissed,
                        sectionTitle = sectionTitle,
                    )
                    tabManager?.setSiteBarState(record.id, siteBarState)
                } else {
                    tabManager?.setSiteBarState(record.id, null)
                }
            }
        }
    }

    override val siteStatusIcon: @Composable (TabState) -> Unit = { tab ->
        val state = tab.siteBarState
        AnimatedVisibility(
            visible = state != null,
            enter = fadeIn(Motion.enter()) + expandHorizontally(Motion.enter()),
            exit = fadeOut(Motion.exit()) + shrinkHorizontally(Motion.exit()),
        ) {
            // Keep the last state while the exit animation runs.
            val shown = remember { mutableStateOf(state) }
            if (state != null) shown.value = state
            val st = shown.value
            if (st != null) {
                val context = LocalContext.current
                val container = (context.applicationContext as FmhyApp).container
                val pasteDecoder = rememberPasteDecoder(container)
                var showPopover by remember { mutableStateOf(false) }
                var detailEntry by remember { mutableStateOf<Entry?>(null) }
                val state = st

                PasteDecodeUi(pasteDecoder)

                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = "FMHY status", role = Role.Button) { showPopover = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Crossfade(targetState = state.kind, animationSpec = Motion.standard(), label = "statusIcon") { kind ->
                            val spec = statusIconSpec(kind)
                            Icon(
                                imageVector = statusGlyphVector(spec.glyph),
                                contentDescription = statusTitle(kind),
                                modifier = Modifier.size(18.dp),
                                tint = statusToneColor(spec.tone),
                            )
                        }
                    }
                    if (showPopover) {
                        val preferAbove = tabManager?.currentBrowserPrefs?.toolbarPosition ==
                            io.github.omeg4dev.fmhy.data.ToolbarPosition.BOTTOM
                        StatusPopup(
                            state = state,
                            sectionTitle = null,
                            preferAbove = preferAbove,
                            onDismiss = { showPopover = false },
                            onShowInSection = { entry ->
                                showPopover = false
                                showEntryInApp(context, entry)
                            },
                            onEntryDetails = { entry ->
                                showPopover = false
                                detailEntry = entry
                            },
                        )
                    }
                }

                detailEntry?.let { e ->
                    EntryDetailBottomSheet(
                        entry = e,
                        onShowInSection = { showEntryInApp(context, e) },
                        onDismiss = { detailEntry = null },
                        onOpenUrl = { newUrl ->
                            detailEntry = null
                            tabManager?.loadUrl(tab.id, newUrl)
                        },
                        onCopyLink = { urlToCopy ->
                            pasteDecoder.linkText(urlToCopy, PasteAction.COPY) { text ->
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Link", text))
                            }
                        },
                        onShareLink = { urlToShare ->
                            pasteDecoder.linkText(urlToShare, PasteAction.SHARE) { text ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share link"))
                            }
                        },
                    )
                }

        }
    }
    }
}

private fun rescueLog(msg: String) {
    if (io.github.omeg4dev.fmhy.BuildConfig.DEBUG) android.util.Log.d("FmhyRescue", msg)
}
