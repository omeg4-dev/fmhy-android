package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.browser.InterstitialState
import io.github.omeg4dev.fmhy.browser.RiskyDownload
import io.github.omeg4dev.fmhy.work.NetworkKind

/** Everything the confirmation needs. */
data class DownloadOffer(
    val url: String,
    val fileName: String,
    val totalBytes: Long,
    val mimeType: String,
    val host: String,
    val referer: String?,
    val userAgent: String?,
    val isPrivate: Boolean,
    val isRisky: Boolean,          // RiskyDownload.isRiskyExtension(fileName) && protectionPrefs.warnRiskyDownloads
    val waitsForWifi: Boolean,     // DownloadPolicy.waitsForWifi(dataUsagePrefs.downloadWifiOnly, currentNetworkKind)
    val folderLabel: String,       // controller.targetLabel()
    val interstitial: InterstitialState?,  // non-null → show the warning screen first
    val needsConfirmation: Boolean,
    val sourceStatus: DownloadSourceStatus? = null,
) {
    val emphasizeLinkCheck: Boolean get() = DownloadOfferRules.emphasizeLinkCheck(sourceStatus?.kind, isRisky)
    val firmRiskConfirm: Boolean get() = DownloadOfferRules.firmRiskConfirm(sourceStatus?.kind, isRisky)
}

object DownloadOfferRules {
    fun emphasizeLinkCheck(kind: SourceKind?, isRisky: Boolean): Boolean = when (kind) {
        SourceKind.STARRED -> false
        SourceKind.LISTED -> isRisky
        SourceKind.NOT_LISTED,
        SourceKind.UNSAFE,
        SourceKind.LOOKALIKE -> true
        null -> isRisky
    }

    fun firmRiskConfirm(kind: SourceKind?, isRisky: Boolean): Boolean =
        isRisky && (kind == SourceKind.NOT_LISTED || kind == SourceKind.LOOKALIKE || kind == SourceKind.UNSAFE)
}

object DownloadOfferText {
    fun sizeText(totalBytes: Long): String? = if (totalBytes > 0) DownloadPolicy.formatBytes(totalBytes) else null
    const val WIFI_NOTE = "Download only on Wi-Fi is on. This file will wait for Wi-Fi."
    const val RISK_TITLE = "This file can install software"
    const val UNLISTED_RISK_NOTE = "FMHY does not list this site. Only continue if you trust where the file comes from."
    fun started(fileName: String, waits: Boolean) = if (waits) "Waiting for Wi-Fi: $fileName" else "Downloading $fileName"
}

fun buildOffer(
    url: String,
    fileName: String,
    totalBytes: Long,
    mimeType: String,
    host: String,
    referer: String?,
    userAgent: String?,
    isPrivate: Boolean,
    warnRiskyDownloads: Boolean,
    downloadWifiOnly: Boolean,
    networkKind: NetworkKind,
    folderLabel: String,
    interstitial: InterstitialState?,
    askBeforeDownload: Boolean,
    lastTouchAt: Long,
    nowMs: Long,
    forceConfirmation: Boolean = false,
    sourceStatus: DownloadSourceStatus? = null,
): DownloadOffer {
    val isRisky = RiskyDownload.isRiskyExtension(fileName) && warnRiskyDownloads
    val waitsForWifi = DownloadPolicy.waitsForWifi(downloadWifiOnly, networkKind)
    val needsConfirmation = forceConfirmation || DownloadPolicy.needsConfirmation(
        askBeforeDownload = askBeforeDownload,
        riskyWarn = isRisky,
        waitsForWifi = waitsForWifi,
        lastTouchAt = lastTouchAt,
        nowMs = nowMs,
    )
    return DownloadOffer(
        url = url,
        fileName = fileName,
        totalBytes = totalBytes,
        mimeType = mimeType,
        host = host,
        referer = referer,
        userAgent = userAgent,
        isPrivate = isPrivate,
        isRisky = isRisky,
        waitsForWifi = waitsForWifi,
        folderLabel = folderLabel,
        interstitial = interstitial,
        needsConfirmation = needsConfirmation,
        sourceStatus = sourceStatus,
    )
}
