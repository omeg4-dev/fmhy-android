package io.github.omeg4dev.fmhy.browser

import android.content.Context
import android.os.Message
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Handles window.open / target="_blank" window creation.
 * For now opens the new window's URL in a new tab only when isUserGesture, otherwise ignores it.
 * E2 will replace this with the full pop-up blocker.
 */
fun handleCreateWindow(
    context: Context,
    isUserGesture: Boolean,
    resultMsg: Message?,
    openNewTab: (url: String) -> Unit
): Boolean {
    if (!isUserGesture || resultMsg == null) return false
    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
    val tempWebView = WebView(context)
    tempWebView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            openNewTab(request.url.toString())
            tempWebView.destroy()
            return true
        }
    }
    transport.webView = tempWebView
    resultMsg.sendToTarget()
    return true
}
