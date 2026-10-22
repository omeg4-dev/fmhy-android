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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import io.github.omeg4dev.fmhy.ui.common.Motion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    private val hostVerdictCache = ConcurrentHashMap<String, Verdict>()
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
        if (!browserPrefs.deadSiteRescue) return
        if (!RescueReason.shouldTrigger(errorCode, description)) return

        val reason = RescueReason.fromErrorCode(errorCode, description)
        buildRescueState(tab, failingUrl, reason)
    }

    override fun onMainFrameHttpError(tab: TabState, statusCode: Int, failingUrl: String) {
        val browserPrefs = tabManager?.currentBrowserPrefs ?: BrowserPrefs()
        if (!browserPrefs.deadSiteRescue) return
        if (!RescueReason.shouldTriggerHttp(statusCode)) return

        val reason = RescueReason.fromHttpStatus(statusCode)
        buildRescueState(tab, failingUrl, reason)
    }

    private fun buildRescueState(tab: TabState, failingUrl: String, reason: String) {
        val host = try { Uri.parse(failingUrl).host ?: failingUrl } catch (_: Exception) { failingUrl }
        tabManager?.appScope?.launch(Dispatchers.IO) {
            val store = datasetStore
            val entry = if (tab.entryId != null) {
                store?.entry(tab.entryId)
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
                tabManager?.setRescueState(
                    tab.id,
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
            val verdict = hostVerdictCache.computeIfAbsent(host) {
                runBlocking { sg.check(url) }
            }

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
                var showDetailSheet by remember { mutableStateOf(false) }
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
                    DropdownMenu(
                        expanded = showPopover,
                        onDismissRequest = { showPopover = false },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        val spec = statusIconSpec(state.kind)
                        val detail = state.text.substringAfter(" · ", "")
                        val entry = state.entry
                        Column(
                            modifier = Modifier
                                .widthIn(min = 220.dp, max = 300.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    imageVector = statusGlyphVector(spec.glyph),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = statusToneColor(spec.tone),
                                )
                                Text(statusTitle(state.kind), style = MaterialTheme.typography.titleSmall)
                            }
                            if (entry != null) {
                                Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            if (detail.isNotBlank()) {
                                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val reason = state.verdict.reason
                            if (!reason.isNullOrBlank() && state.kind != SiteBarKind.STARRED && state.kind != SiteBarKind.LISTED) {
                                Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (state.kind == SiteBarKind.NOT_ON_FMHY) {
                                Text(
                                    "This site is not on the FMHY list. That does not mean it is unsafe.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (entry != null) {
                                TextButton(
                                    onClick = { showPopover = false; showDetailSheet = true },
                                    modifier = Modifier.align(Alignment.End),
                                ) { Text("FMHY entry") }
                            }
                        }
                    }
                }

                if (showDetailSheet && state.entry != null) {
                EntryDetailBottomSheet(
                    entry = state.entry,
                    onDismiss = { showDetailSheet = false },
                    onOpenUrl = { newUrl ->
                        showDetailSheet = false
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

@Composable
private fun statusGlyphVector(glyph: StatusGlyph): ImageVector = when (glyph) {
    StatusGlyph.STAR -> Icons.Filled.Star
    StatusGlyph.VERIFIED -> Icons.Filled.Verified
    StatusGlyph.INFO -> Icons.Outlined.Info
    StatusGlyph.WARNING -> Icons.Filled.GppMaybe
    StatusGlyph.ERROR -> Icons.Filled.GppBad
}

@Composable
private fun statusToneColor(tone: StatusTone): Color = when (tone) {
    StatusTone.PRIMARY -> MaterialTheme.colorScheme.primary
    StatusTone.TERTIARY -> MaterialTheme.colorScheme.tertiary
    StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    StatusTone.ERROR -> MaterialTheme.colorScheme.error
}
