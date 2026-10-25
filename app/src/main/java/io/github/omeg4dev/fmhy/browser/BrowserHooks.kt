package io.github.omeg4dev.fmhy.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Message
import android.view.View
import android.webkit.URLUtil
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
import io.github.omeg4dev.fmhy.ui.common.Motion
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
import kotlinx.coroutines.withContext

data class BrowserSnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

data class DownloadDialogState(
    val url: String,
    val filename: String,
    val sizeText: String,
    val mimeType: String,
    val host: String,
    val userAgent: String,
    val isRisky: Boolean,
    val riskWarning: String?,
    val safeGuardVerdict: Verdict?,
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
    var pendingDownloadDialog by mutableStateOf<DownloadDialogState?>(null)
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
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ) {
        val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val host = try { Uri.parse(url).host.orEmpty() } catch (_: Exception) { "" }
        val sizeText = if (contentLength > 0) android.text.format.Formatter.formatFileSize(context, contentLength) else "unknown size"
        val isRisky = RiskyDownload.isRiskyExtension(filename)
        val protectionPrefs = tabManager?.currentProtectionPrefs ?: ProtectionPrefs()
        val warnRisky = isRisky && protectionPrefs.warnRiskyDownloads

        fun doDownload() {
            executeDownload(context, url, filename, userAgent, mimeType)
        }

        if (protectionPrefs.askBeforeDownload || warnRisky) {
            val record = tabManager?.getActiveTabRecord()
            val currentTabUrl = record?.url.orEmpty()
            tabManager?.appScope?.launch(Dispatchers.IO) {
                val verdict = if (warnRisky && currentTabUrl.isNotEmpty()) {
                    safeGuard?.check(currentTabUrl)
                } else null

                val riskWarning = if (warnRisky) {
                    RiskyDownload.warningMessage(host.ifEmpty { "this site" })
                } else null

                withContext(Dispatchers.Main) {
                    pendingDownloadDialog = DownloadDialogState(
                        url = url,
                        filename = filename,
                        sizeText = sizeText,
                        mimeType = mimeType,
                        host = host,
                        userAgent = userAgent,
                        isRisky = isRisky,
                        riskWarning = riskWarning,
                        safeGuardVerdict = verdict,
                    )
                }
            }
        } else {
            doDownload()
        }
    }

    fun executeDownload(
        context: Context,
        url: String,
        filename: String,
        userAgent: String,
        mimeType: String,
    ) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        try {
            val uri = Uri.parse(url)
            val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
            val req = DownloadManager.Request(uri).apply {
                if (mimeType.isNotBlank()) setMimeType(mimeType)
                if (userAgent.isNotBlank()) addRequestHeader("User-Agent", userAgent)
                if (!cookies.isNullOrBlank()) addRequestHeader("Cookie", cookies)
                setDescription("Downloading file…")
                setTitle(filename)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                } else {
                    setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
                }
            }
            dm.enqueue(req)
            snackbarFlow.tryEmit(BrowserSnackbarEvent(message = "Downloading $filename"))
        } catch (_: Exception) {}
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
        record.failedLoad = FailedLoad(failingUrl, RescueReason.fromErrorCode(errorCode, description), isHttp = false)
    }

    override fun onMainFrameHttpError(tab: TabState, statusCode: Int, failingUrl: String) {
        val browserPrefs = tabManager?.currentBrowserPrefs ?: BrowserPrefs()
        rescueLog("http $statusCode url=$failingUrl tab=${tab.url}")
        if (!browserPrefs.deadSiteRescue) return
        if (!RescueReason.shouldTriggerHttp(statusCode)) return
        val record = tabManager?.getTabRecord(tab.id) ?: return
        record.failedLoad = FailedLoad(failingUrl, RescueReason.fromHttpStatus(statusCode), isHttp = true)
    }

    /** A new document starts in the main frame (not called for in-page navigations). */
    fun onMainFramePageStarted(record: TabRecord, url: String) {
        record.rescueJob?.cancel()
        record.rescueJob = null
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
    }

    /**
     * First paint of the new document: unless an error is known for it, the page has content. The
     * rescue itself only closes in onPageFinished, so a late error never makes it flash.
     */
    fun onPageCommitVisible(record: TabRecord, url: String) {
        if (RescueDecision.sameAddress(url, record.failedLoad?.url)) return
        record.contentShown = true
    }

    /** End of a load attempt; every main-frame error of the attempt has arrived by now. */
    fun onMainFramePageFinished(record: TabRecord, view: WebView, url: String) {
        val failed = record.failedLoad?.takeIf { RescueDecision.sameAddress(it.url, url) }
        record.failedLoad = null
        if (failed == null) {
            if (RescueDecision.sameAddress(url, record.url)) onContentShown(record, url)
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
            RescueDecision.Action.SHOW -> buildRescueState(record, failed.url, failed.reason)
        }
    }

    private fun buildRescueState(record: TabRecord, failingUrl: String, reason: String) {
        // Same address already rescued (e.g. the user pressed Retry): update it in place, no flash.
        val current = record.rescueState
        if (current != null && RescueDecision.sameAddress(current.failingUrl, failingUrl)) {
            tabManager?.setRescueState(record.id, current.copy(reason = reason, retrying = false))
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
                if (record.contentShown) return@withContext
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

    fun onPageStarted(record: TabRecord, url: String) {
        val browserPrefs = tabManager?.currentBrowserPrefs ?: BrowserPrefs()
        val protectionPrefs = tabManager?.currentProtectionPrefs ?: ProtectionPrefs()
        val host = try { Uri.parse(url).host } catch (_: Exception) { null }
        if (host == null) {
            tabManager?.setSiteBarState(record.id, null)
            tabManager?.setInterstitialState(record.id, null)
            return
        }

        tabManager?.appScope?.launch(Dispatchers.IO) {
            val sg = safeGuard ?: return@launch
            val key = url.substringBefore('#')
            val verdict = synchronized(verdictCache) { verdictCache[key] }
                ?: sg.check(url).also { synchronized(verdictCache) { verdictCache[key] = it } }

            withContext(Dispatchers.Main) {
                // Drop stale results when the tab already moved on to another host.
                val currentHost = try { Uri.parse(record.url).host } catch (_: Exception) { null }
                if (currentHost != null && !currentHost.equals(host, ignoreCase = true)) return@withContext
                // Interstitial check for UNSAFE / POTENTIALLY_UNSAFE
                val isAllowed = record.allowedHosts.contains(host)
                if (!isAllowed) {
                    if (verdict.status == SafetyStatus.UNSAFE && protectionPrefs.unsafeWarning) {
                        tabManager?.setInterstitialState(
                            record.id,
                            InterstitialState(host = host, isUnsafe = true, reason = verdict.reason)
                        )
                    } else if (verdict.status == SafetyStatus.POTENTIALLY_UNSAFE && protectionPrefs.potentiallyUnsafeWarning) {
                        tabManager?.setInterstitialState(
                            record.id,
                            InterstitialState(host = host, isUnsafe = false, reason = verdict.reason)
                        )
                    } else {
                        tabManager?.setInterstitialState(record.id, null)
                    }
                } else {
                    tabManager?.setInterstitialState(record.id, null)
                }

                // SiteBar check
                if (browserPrefs.siteBar) {
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
                var showPopover by remember { mutableStateOf(false) }
                var detailEntry by remember { mutableStateOf<Entry?>(null) }
                val state = st

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
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText("Link", urlToCopy))
                        },
                        onShareLink = { urlToShare ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, urlToShare)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share link"))
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
