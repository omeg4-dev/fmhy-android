package io.github.omeg4dev.fmhy.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormatters {
    private val dayHeaderFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val historyDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

    /**
     * Sticky day header format: "Today", "Yesterday", or "Mon 18 Oct".
     */
    fun formatDayHeader(
        tsSec: Long,
        nowSec: Long = System.currentTimeMillis() / 1000,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val changeDate = Instant.ofEpochSecond(tsSec).atZone(zoneId).toLocalDate()
        val nowDate = Instant.ofEpochSecond(nowSec).atZone(zoneId).toLocalDate()
        return when (changeDate) {
            nowDate -> "Today"
            nowDate.minusDays(1) -> "Yesterday"
            else -> changeDate.format(dayHeaderFormatter)
        }
    }

    /**
     * History item date format: "18 Oct 2026".
     */
    fun formatHistoryDate(
        tsSec: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val changeDate = Instant.ofEpochSecond(tsSec).atZone(zoneId).toLocalDate()
        return changeDate.format(historyDateFormatter)
    }
}
