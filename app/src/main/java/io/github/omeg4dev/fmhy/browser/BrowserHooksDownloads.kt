package io.github.omeg4dev.fmhy.browser

/** Download dispatching and confirmation handling for [DefaultBrowserHooks]. */

import android.content.Context
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.browser.download.DownloadOffer
import io.github.omeg4dev.fmhy.browser.download.DownloadOfferText
import io.github.omeg4dev.fmhy.browser.download.DownloadRequest
import io.github.omeg4dev.fmhy.browser.download.DownloadSourceStatus
import io.github.omeg4dev.fmhy.browser.download.NewDownload
import io.github.omeg4dev.fmhy.browser.download.buildOffer
import io.github.omeg4dev.fmhy.data.adblock.FilterRequest
import io.github.omeg4dev.fmhy.data.adblock.MatchResult
import io.github.omeg4dev.fmhy.data.adblock.ResourceType
import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import io.github.omeg4dev.fmhy.data.safety.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun DefaultBrowserHooks.onDownloadStartImpl(
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
            chainStartHost = record.chainStartHost,
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

fun DefaultBrowserHooks.offerPastedLink(context: Context, url: String, isPrivate: Boolean) {
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

fun DefaultBrowserHooks.confirmDownload(offer: DownloadOffer, allowMobile: Boolean) {
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

