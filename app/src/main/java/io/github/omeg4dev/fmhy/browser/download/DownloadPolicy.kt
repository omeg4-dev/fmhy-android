package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.work.NetworkKind
import java.util.Locale

const val GESTURE_WINDOW_MS = 5_000L

object DownloadPolicy {

    /** True when the confirmation dialog must be shown before the download starts. */
    fun needsConfirmation(
        askBeforeDownload: Boolean,
        riskyWarn: Boolean,
        waitsForWifi: Boolean,
        lastTouchAt: Long,
        nowMs: Long,
    ): Boolean {
        return askBeforeDownload ||
            riskyWarn ||
            waitsForWifi ||
            lastTouchAt <= 0L ||
            (nowMs - lastTouchAt) > GESTURE_WINDOW_MS
    }

    fun waitsForWifi(wifiOnly: Boolean, network: NetworkKind): Boolean {
        return wifiOnly && network != NetworkKind.WIFI
    }

    fun formatEta(seconds: Long): String {
        val sec = seconds.coerceAtLeast(0L)
        if (sec < 60L) {
            return "$sec s left"
        }
        if (sec < 3600L) {
            val min = (sec + 59L) / 60L
            return "$min min left"
        }
        val totalMinutes = (sec + 59L) / 60L
        val h = totalMinutes / 60L
        val m = totalMinutes % 60L
        return if (m == 0L) "$h h left" else "$h h $m min left"
    }

    fun formatSpeed(bytesPerSecond: Long): String {
        val b = bytesPerSecond.coerceAtLeast(0L)
        return when {
            b < 1024L -> "$b B/s"
            b < 1024L * 1024L -> String.format(Locale.US, "%.1f KB/s", b / 1024.0)
            b < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB/s", b / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.1f GB/s", b / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatBytes(bytes: Long): String {
        val b = bytes.coerceAtLeast(0L)
        return when {
            b < 1024L -> "$b B"
            b < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", b / 1024.0)
            b < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.1f GB", b / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun progressLine(downloaded: Long, total: Long, speedBps: Long?, etaSeconds: Long?): String {
        val sizePart = if (total > 0L) {
            "${formatBytes(downloaded)} of ${formatBytes(total)}"
        } else {
            formatBytes(downloaded)
        }
        val speedPart = speedBps?.let { formatSpeed(it) }
        val etaPart = etaSeconds?.let { formatEta(it) }

        return listOfNotNull(sizePart, speedPart, etaPart).joinToString(" · ")
    }
}
