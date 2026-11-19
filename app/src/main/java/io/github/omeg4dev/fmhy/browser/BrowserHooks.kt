package io.github.omeg4dev.fmhy.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.browser.download.CheckChipTone
import io.github.omeg4dev.fmhy.browser.download.DownloadOffer
import io.github.omeg4dev.fmhy.browser.download.VtSiteChecks
import io.github.omeg4dev.fmhy.browser.download.chipColors
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.PasteAction
import io.github.omeg4dev.fmhy.ui.common.PasteDecodeUi
import io.github.omeg4dev.fmhy.ui.common.linkText
import io.github.omeg4dev.fmhy.ui.common.rememberPasteDecoder
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.user.SitePrefs
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.safety.SafeGuard
import io.github.omeg4dev.fmhy.data.safety.Verdict
import io.github.omeg4dev.fmhy.ui.common.EntryDetailBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext

data class BrowserSnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

data class BlockedNoticeEvent(
    val tabId: Long,
    val entry: BlockedEntry,
)

data class ExternalAppDialogState(
    val intent: Intent,
    val appName: String,
    val fallbackUrl: String?,
)

interface BrowserHooks {
    val blockedFlow: MutableSharedFlow<BlockedNoticeEvent> get() = MutableSharedFlow(extraBufferCapacity = 10)
    val tabNoticeFlow: MutableSharedFlow<TabNotice> get() = MutableSharedFlow(extraBufferCapacity = 10)
    val tabPulseFlow: MutableSharedFlow<Unit> get() = MutableSharedFlow(extraBufferCapacity = 1)
    val blockedClearFlow: MutableSharedFlow<Long> get() = MutableSharedFlow(extraBufferCapacity = 10)

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
    internal val datasetStore: DatasetStore? = null,
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
    override val blockedFlow = MutableSharedFlow<BlockedNoticeEvent>(extraBufferCapacity = 10)
    override val tabNoticeFlow = MutableSharedFlow<TabNotice>(extraBufferCapacity = 10)
    override val tabPulseFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val blockedClearFlow = MutableSharedFlow<Long>(extraBufferCapacity = 10)
    internal val tabMerge = BackgroundTabMerge()

    fun updateSitePrefs(tab: TabState, reload: Boolean = false, update: (SitePrefs) -> SitePrefs) {
        val host = try { Uri.parse(tab.url).host.orEmpty() } catch (_: Exception) { "" }
        if (host.isNotEmpty()) {
            tabManager?.updateSitePrefs(tab.id, host, reload = reload, update = update)
        }
    }
    var pendingDownload by mutableStateOf<DownloadOffer?>(null)
    val downloadStarted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    internal var appContext: Context? = null
    var pendingExternalAppDialog by mutableStateOf<ExternalAppDialogState?>(null)
    var customView by mutableStateOf<View?>(null)
    var customViewCallback: WebChromeClient.CustomViewCallback? = null
    var onShowCustomViewListener: ((View) -> Unit)? = null
    var onHideCustomViewListener: (() -> Unit)? = null

    /** Verdicts per page URL (fragment dropped): shared hosts like github.com differ per path. */
    internal val verdictCache = object : LinkedHashMap<String, Verdict>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Verdict>?) = size > 64
    }

    suspend fun checkVerdict(url: String): Verdict? = withContext(Dispatchers.IO) {
        val sg = safeGuard ?: return@withContext null
        val key = url.substringBefore('#')
        synchronized(verdictCache) { verdictCache[key] }
            ?: runCatching { sg.check(url).also { synchronized(verdictCache) { verdictCache[key] = it } } }.getOrNull()
    }
    internal val livePopupCount = java.util.concurrent.atomic.AtomicInteger(0)

    override fun handleCreateWindow(
        context: Context,
        tab: TabState,
        isUserGesture: Boolean,
        resultMsg: Message?,
        openNewTab: (url: String) -> Unit
    ): Boolean = handleCreateWindowImpl(context, tab, isUserGesture, resultMsg, openNewTab)

    override fun shouldOverrideNavigation(
        context: Context,
        tab: TabState,
        request: WebResourceRequest,
        onAskOpenExternal: (intent: Intent, appName: String) -> Unit
    ): Boolean = shouldOverrideNavigationImpl(context, tab, request, onAskOpenExternal)

    override fun onDownloadStart(
        context: Context,
        record: TabRecord,
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ) = onDownloadStartImpl(context, record, url, userAgent, contentDisposition, mimeType, contentLength)

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
            errorCode = errorCode,
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
            errorCode = statusCode,
        )
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

                val host = remember(tab.url) { hostOf(tab.url) }
                val marks by container.vtChecks.checked.collectAsState()
                LaunchedEffect(Unit) { container.vtChecks.load() }
                val mark = host?.let { marks[VtSiteChecks.normalizeHost(it)] }?.takeIf { container.vtChecks.markFor(host) != null }

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
                    if (mark != null && state.kind != SiteBarKind.FMHY_SITE && state.kind != SiteBarKind.UNSAFE) {
                        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        val (_, okContent) = chipColors(CheckChipTone.OK, isDark, MaterialTheme.colorScheme)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-2).dp, y = (-2).dp)
                                .size(10.dp)
                                .border(width = 1.5.dp, color = MaterialTheme.colorScheme.surface, shape = CircleShape)
                                .background(color = okContent, shape = CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Checked with VirusTotal",
                                tint = Color.White,
                                modifier = Modifier.size(7.dp),
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
