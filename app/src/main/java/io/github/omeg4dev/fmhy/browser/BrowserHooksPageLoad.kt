package io.github.omeg4dev.fmhy.browser

/** Page lifecycle and content probe handling for [DefaultBrowserHooks]. */

import android.net.Uri
import android.webkit.WebView
import io.github.omeg4dev.fmhy.browser.download.DownloadRequest
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.ui.common.displayTitle
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** A new document starts in the main frame (not called for in-page navigations). */
fun DefaultBrowserHooks.onMainFramePageStarted(record: TabRecord, url: String) {
    if (!RescueDecision.sameAddress(url, record.skipBlockingOnceUrl)) {
        record.skipBlockingOnceUrl = null
    }
    record.rescueJob?.cancel()
    record.rescueJob = null
    record.isWaitingRetry = false
    record.isWaitingGraceRetry = false
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
fun DefaultBrowserHooks.onPageCommitVisible(record: TabRecord, url: String) {
    if (RescueDecision.sameAddress(url, record.failedLoad?.url)) return
    record.contentShown = true
    record.stallJob?.cancel()
    record.stallJob = null
}

private suspend fun DefaultBrowserHooks.evaluate(view: WebView?, js: String): String? {
    if (view == null) return null
    return suspendCancellableCoroutine { continuation ->
        view.evaluateJavascript(js) { raw -> if (continuation.isActive) continuation.resume(raw) }
    }
}

private suspend fun DefaultBrowserHooks.evaluateProbe(view: WebView?): RescueDecision.PageProbe? {
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
fun DefaultBrowserHooks.onMainFramePageFinished(record: TabRecord, view: WebView, url: String) {
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

private fun DefaultBrowserHooks.onContentShown(record: TabRecord, url: String) {
    record.contentShown = true
    record.silentRetries = 0
    record.silentRetryUrl = url
    if (record.rescueState != null) {
        rescueLog("content shown, rescue closes url=$url")
        tabManager?.setRescueState(record.id, null)
    }
}

fun DefaultBrowserHooks.onPageStarted(record: TabRecord, url: String) {
    val newKey = url.substringBefore('#')
    val prevKey = record.lastBlockedPageUrl?.substringBefore('#')
    if (prevKey != newKey) {
        record.lastBlockedPageUrl = url
        blockedClearFlow.tryEmit(record.id)
    }

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
                    chainStartHost = record.chainStartHost,
                )
            )

            // SiteBar check
            if (browserPrefs.siteBar && verdict != null) {
                val isRedirected = record.entryHost != null && !record.entryHost.equals(host, ignoreCase = true)
                val isDismissed = record.dismissedSiteBarHosts.contains(host)
                val sectionTitle = verdict.matches.firstOrNull()?.sectionId?.let { secId ->
                    datasetStore?.sections()?.firstOrNull { it.id == secId }?.displayTitle
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

internal fun DefaultBrowserHooks.hostOf(url: String?): String? {
    if (url == null || !DownloadRequest.isHttpUrl(url)) return null
    val h = DownloadRequest.hostOf(url)
    return h.ifBlank { null }
}

