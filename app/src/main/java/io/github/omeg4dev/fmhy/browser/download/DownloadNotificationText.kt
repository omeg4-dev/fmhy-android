package io.github.omeg4dev.fmhy.browser.download

object DownloadNotificationText {

    data class Content(
        val title: String,
        val text: String,
        val percent: Int?,
        val showProgress: Boolean,
        val single: Boolean,
    )

    /** null when no item is active. */
    fun ongoing(items: List<DownloadItem>): Content? {
        val active = items.filter { it.status.isActive }
        if (active.isEmpty()) return null

        if (active.size == 1) {
            val item = active.first()
            val title = if (item.isPrivate) "Private download" else item.fileName
            val isRunning = item.status == DownloadStatus.RUNNING
            val pct = if (isRunning && item.fraction != null) (item.fraction!! * 100).toInt() else null

            val text = if (isRunning) {
                val sizePart = if (pct != null) {
                    "$pct%"
                } else {
                    DownloadPolicy.formatBytes(item.downloadedBytes)
                }
                val speedPart = item.speedBps?.let { DownloadPolicy.formatSpeed(it) }
                val etaPart = item.etaSeconds?.let { DownloadPolicy.formatEta(it) }
                listOfNotNull(sizePart, speedPart, etaPart).joinToString(" · ")
            } else {
                item.status.label
            }

            return Content(
                title = title,
                text = text,
                percent = pct,
                showProgress = isRunning,
                single = true,
            )
        } else {
            val title = "${active.size} downloads"
            val runningCount = active.count { it.status == DownloadStatus.RUNNING }
            val summary = activeSummary(active)
            val combinedFraction = summary?.fraction
            val pct = if (runningCount > 0 && combinedFraction != null) (combinedFraction * 100).toInt() else null
            val showProgress = runningCount > 0

            val text = if (runningCount > 0) {
                if (pct != null) {
                    "$pct% · $runningCount downloading"
                } else {
                    "$runningCount downloading"
                }
            } else {
                if (active.any { it.status == DownloadStatus.WAITING_WIFI }) {
                    "Waiting for Wi-Fi"
                } else {
                    "Waiting"
                }
            }

            return Content(
                title = title,
                text = text,
                percent = pct,
                showProgress = showProgress,
                single = false,
            )
        }
    }

    fun finishedTitle(item: DownloadItem): String {
        return if (item.isPrivate) "Private download finished" else "Download finished"
    }

    fun finishedText(item: DownloadItem): String? {
        return if (item.isPrivate) null else item.fileName
    }
}
