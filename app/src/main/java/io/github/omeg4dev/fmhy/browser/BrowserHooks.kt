package io.github.omeg4dev.fmhy.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.view.View
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import androidx.compose.runtime.Composable

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

    val siteBar: @Composable (TabState) -> Unit

    fun shouldOverrideNavigation(
        context: Context,
        tab: TabState,
        request: WebResourceRequest,
        onAskOpenExternal: (intent: Intent, appName: String) -> Unit
    ): Boolean
}

class DefaultBrowserHooks : BrowserHooks {
    override fun handleCreateWindow(
        context: Context,
        tab: TabState,
        isUserGesture: Boolean,
        resultMsg: Message?,
        openNewTab: (url: String) -> Unit
    ): Boolean {
        return io.github.omeg4dev.fmhy.browser.handleCreateWindow(context, isUserGesture, resultMsg, openNewTab)
    }

    override fun onDownloadStart(
        context: Context,
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        try {
            val uri = Uri.parse(url)
            val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val req = DownloadManager.Request(uri).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                setDescription("Downloading file…")
                setTitle(filename)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            }
            dm.enqueue(req)
        } catch (_: Exception) {}
    }

    override val siteBar: @Composable (TabState) -> Unit = {}

    override fun shouldOverrideNavigation(
        context: Context,
        tab: TabState,
        request: WebResourceRequest,
        onAskOpenExternal: (intent: Intent, appName: String) -> Unit
    ): Boolean {
        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "data" || scheme == "blob") {
            return false
        }

        try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
            }
            val pm = context.packageManager
            val resolveInfo = pm.resolveActivity(intent, 0)
            val appName = resolveInfo?.loadLabel(pm)?.toString() ?: "an external application"
            onAskOpenExternal(intent, appName)
            return true
        } catch (_: Exception) {
            return true
        }
    }
}
