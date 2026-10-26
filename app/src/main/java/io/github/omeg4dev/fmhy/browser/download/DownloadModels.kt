package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.data.user.DownloadRecord

enum class DownloadStatus(val label: String) {
    WAITING("Waiting"),
    WAITING_WIFI("Waiting for Wi-Fi"),
    RUNNING("Downloading"),
    PAUSED("Paused"),
    FAILED("Failed"),
    FINISHED("Finished");

    /** Counts towards "something is going on": the service stays up and the bar ring shows. */
    val isActive: Boolean get() = this == WAITING || this == WAITING_WIFI || this == RUNNING
}

data class DownloadItem(
    val id: Long,
    val url: String,
    val fileName: String,
    val mimeType: String = "",
    val sourceHost: String = "",
    val referer: String? = null,
    val userAgent: String? = null,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.WAITING,
    val resumable: Boolean = true,
    val restartNote: Boolean = false,
    val allowMobile: Boolean = false,
    val isPrivate: Boolean = false,
    val error: String? = null,
    val fileUri: String? = null,
    val folderLabel: String = "",
    val etag: String? = null,
    val lastModified: String? = null,
    val createdAt: Long = 0,
    val finishedAt: Long? = null,
    /** Transient (never stored): bytes per second, null when unknown. */
    val speedBps: Long? = null,
    /** Transient: seconds left, null when unknown. */
    val etaSeconds: Long? = null,
) {
    /** 0f..1f, or null when the total size is unknown. */
    val fraction: Float? get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null

    val isApk: Boolean
        get() = fileName.substringAfterLast('.', "").lowercase() in setOf("apk", "xapk", "apks") ||
            mimeType.equals("application/vnd.android.package-archive", ignoreCase = true)
}

/** Summary for the address-bar ring and the notification. */
data class ActiveSummary(val count: Int, val fraction: Float?)

fun DownloadItem.toRecord(): DownloadRecord {
    return DownloadRecord(
        id = id,
        url = url,
        fileName = fileName,
        mimeType = mimeType,
        sourceHost = sourceHost,
        referer = referer,
        userAgent = userAgent,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        status = status.name,
        resumable = resumable,
        restartNote = restartNote,
        allowMobile = allowMobile,
        error = error,
        fileUri = fileUri,
        folderLabel = folderLabel,
        etag = etag,
        lastModified = lastModified,
        createdAt = createdAt,
        finishedAt = finishedAt,
    )
}

fun DownloadRecord.toItem(): DownloadItem {
    val parsedStatus = try {
        DownloadStatus.valueOf(status)
    } catch (_: IllegalArgumentException) {
        DownloadStatus.FAILED
    }
    return DownloadItem(
        id = id,
        url = url,
        fileName = fileName,
        mimeType = mimeType,
        sourceHost = sourceHost,
        referer = referer,
        userAgent = userAgent,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        status = parsedStatus,
        resumable = resumable,
        restartNote = restartNote,
        allowMobile = allowMobile,
        isPrivate = false,
        error = error,
        fileUri = fileUri,
        folderLabel = folderLabel,
        etag = etag,
        lastModified = lastModified,
        createdAt = createdAt,
        finishedAt = finishedAt,
        speedBps = null,
        etaSeconds = null,
    )
}

fun activeSummary(items: List<DownloadItem>): ActiveSummary? {
    val activeItems = items.filter { it.status.isActive }
    if (activeItems.isEmpty()) return null
    val allTotalKnown = activeItems.all { it.totalBytes > 0 }
    val fraction = if (allTotalKnown) {
        val totalSum = activeItems.sumOf { it.totalBytes }
        val downloadedSum = activeItems.sumOf { it.downloadedBytes }
        if (totalSum > 0) (downloadedSum.toFloat() / totalSum).coerceIn(0f, 1f) else null
    } else {
        null
    }
    return ActiveSummary(count = activeItems.size, fraction = fraction)
}
