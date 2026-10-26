package io.github.omeg4dev.fmhy.browser.download

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CheckUiText {

    fun label(result: ScanResult, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (result.isRunning) {
            val service = result.pending.firstOrNull()?.service?.label ?: ""
            return "$service · analysis running"
        }

        val nonErrorServices = result.services.filter { it.error == null }
        val servicesText = if (nonErrorServices.isNotEmpty()) {
            nonErrorServices.map { it.service.label }.joinToString(" + ")
        } else {
            result.services.firstOrNull()?.service?.label.orEmpty()
        }

        val checkedZdt = Instant.ofEpochMilli(result.checkedAt).atZone(zone)
        val nowZdt = Instant.ofEpochMilli(nowMs).atZone(zone)
        val pattern = if (checkedZdt.year == nowZdt.year) "d MMM" else "d MMM yyyy"
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
        val dateStr = formatter.format(checkedZdt)

        val verdictStr = result.verdict.label

        return if (servicesText.isNotEmpty()) {
            "$servicesText · $dateStr · $verdictStr"
        } else {
            "$dateStr · $verdictStr"
        }
    }

    fun serviceLine(r: ServiceResult): String {
        if (r.error != null) {
            return ScanText.error(r.service, r.error)
        }
        if (r.flagged != null && r.engines != null && r.engines > 0) {
            return ScanText.engines(r.flagged, r.engines)
        }
        return r.detail ?: ScanText.UNKNOWN_FILE
    }

    fun verdictTitle(v: ScanVerdict): String = when (v) {
        ScanVerdict.NO_FLAGS -> "No flags"
        ScanVerdict.SUSPICIOUS -> "Suspicious"
        ScanVerdict.DANGEROUS -> "Dangerous: don't open this file"
        ScanVerdict.UNKNOWN -> "Unknown"
    }

    fun linkVerdictTitle(v: ScanVerdict): String = when (v) {
        ScanVerdict.NO_FLAGS -> "No flags"
        ScanVerdict.SUSPICIOUS -> "Suspicious"
        ScanVerdict.DANGEROUS -> "Dangerous: don't download this file"
        ScanVerdict.UNKNOWN -> "Unknown"
    }

    fun sendNote(services: List<ScanService>): String {
        val names = services.map { it.label }.joinToString(" and ")
        return "The web address will be sent to $names. Some links contain personal or one-time codes."
    }

    fun hasError(result: ScanResult, error: ScanError): Boolean =
        result.services.any { it.error == error }

    fun hasError(result: ScanResult): Boolean =
        result.services.any { it.error != null }

    fun anyUnknown(result: ScanResult): Boolean =
        result.services.any { it.verdict == ScanVerdict.UNKNOWN && it.error == null }
}
