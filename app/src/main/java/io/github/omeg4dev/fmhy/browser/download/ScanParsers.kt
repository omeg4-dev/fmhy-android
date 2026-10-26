package io.github.omeg4dev.fmhy.browser.download

import java.time.OffsetDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object ScanParsers {
    private val json = Json { ignoreUnknownKeys = true }

    fun errorFor(httpCode: Int): ScanError? = when (httpCode) {
        401, 403 -> ScanError.BAD_KEY
        429, 204 -> ScanError.LIMIT
        413 -> ScanError.TOO_BIG
        404 -> null
        in 200..299 -> null
        else -> ScanError.OTHER
    }

    fun vtReport(body: String, reportUrl: String): ServiceResult? {
        return try {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return null
            val data = root["data"] as? JsonObject ?: return null
            val attributes = data["attributes"] as? JsonObject ?: return null
            val stats = attributes["last_analysis_stats"] as? JsonObject ?: return null

            val malicious = stats["malicious"]?.jsonPrimitive?.intOrNull ?: 0
            val suspicious = stats["suspicious"]?.jsonPrimitive?.intOrNull ?: 0
            val undetected = stats["undetected"]?.jsonPrimitive?.intOrNull ?: 0
            val harmless = stats["harmless"]?.jsonPrimitive?.intOrNull ?: 0

            val engines = malicious + suspicious + undetected + harmless
            val flagged = malicious + suspicious
            val verdict = ScanVerdicts.fromCounts(malicious, suspicious, engines)

            val lastAnalysisDateSec = attributes["last_analysis_date"]?.jsonPrimitive?.longOrNull
            val scannedAt = lastAnalysisDateSec?.let { it * 1000L }

            ServiceResult(
                service = ScanService.VIRUSTOTAL,
                verdict = verdict,
                flagged = flagged,
                engines = engines,
                detail = null,
                scannedAt = scannedAt,
                reportUrl = reportUrl,
                error = null,
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun vtAnalysisId(body: String): String? {
        return try {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return null
            val data = root["data"] as? JsonObject ?: return null
            data["id"]?.jsonPrimitive?.contentOrNull
        } catch (_: Throwable) {
            null
        }
    }

    fun vtUploadUrl(body: String): String? {
        return try {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return null
            root["data"]?.jsonPrimitive?.contentOrNull
        } catch (_: Throwable) {
            null
        }
    }

    fun vtAnalysis(body: String, reportUrl: String): ServiceResult? {
        return try {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return null
            val data = root["data"] as? JsonObject ?: return null
            val attributes = data["attributes"] as? JsonObject ?: return null
            val status = attributes["status"]?.jsonPrimitive?.contentOrNull
            if (status != "completed") return null

            val stats = attributes["stats"] as? JsonObject ?: return null
            val malicious = stats["malicious"]?.jsonPrimitive?.intOrNull ?: 0
            val suspicious = stats["suspicious"]?.jsonPrimitive?.intOrNull ?: 0
            val undetected = stats["undetected"]?.jsonPrimitive?.intOrNull ?: 0
            val harmless = stats["harmless"]?.jsonPrimitive?.intOrNull ?: 0

            val engines = malicious + suspicious + undetected + harmless
            val flagged = malicious + suspicious
            val verdict = ScanVerdicts.fromCounts(malicious, suspicious, engines)

            val dateSec = attributes["date"]?.jsonPrimitive?.longOrNull
            val scannedAt = dateSec?.let { it * 1000L }

            ServiceResult(
                service = ScanService.VIRUSTOTAL,
                verdict = verdict,
                flagged = flagged,
                engines = engines,
                detail = null,
                scannedAt = scannedAt,
                reportUrl = reportUrl,
                error = null,
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun haOverview(body: String, reportUrl: String): ServiceResult? {
        return try {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return null
            val rawVerdict = root["verdict"]?.jsonPrimitive?.contentOrNull
            val verdict = ScanVerdicts.fromHybridVerdict(rawVerdict)

            val threatScore = root["threat_score"]?.jsonPrimitive?.intOrNull
            val detail = threatScore?.let { "Threat score $it of 100" }

            val rawDate = root["last_multi_scan"]?.jsonPrimitive?.contentOrNull
                ?: root["analysis_start_time"]?.jsonPrimitive?.contentOrNull
            val scannedAt = rawDate?.let { dateStr ->
                try {
                    OffsetDateTime.parse(dateStr).toInstant().toEpochMilli()
                } catch (_: Throwable) {
                    null
                }
            }

            ServiceResult(
                service = ScanService.HYBRID_ANALYSIS,
                verdict = verdict,
                flagged = null,
                engines = null,
                detail = detail,
                scannedAt = scannedAt,
                reportUrl = reportUrl,
                error = null,
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun haQuickScanId(body: String): String? {
        return try {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return null
            root["id"]?.jsonPrimitive?.contentOrNull
        } catch (_: Throwable) {
            null
        }
    }

    fun haQuickScan(body: String, reportUrl: String): ServiceResult? {
        return try {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return null
            val finished = root["finished"]?.jsonPrimitive?.booleanOrNull ?: false
            if (!finished) return null

            val scanners = root["scanners"]?.jsonArray ?: return null

            var malicious = 0
            var suspicious = 0
            var engines = 0

            for (elem in scanners) {
                val scannerObj = elem as? JsonObject ?: continue
                val status = scannerObj["status"]?.jsonPrimitive?.contentOrNull?.lowercase()?.trim() ?: continue
                when (status) {
                    "malicious" -> {
                        malicious++
                        engines++
                    }
                    "suspicious" -> {
                        suspicious++
                        engines++
                    }
                    "clean", "no-classification" -> {
                        engines++
                    }
                }
            }

            val flagged = malicious + suspicious
            val verdict = ScanVerdicts.fromCounts(malicious, suspicious, engines)

            ServiceResult(
                service = ScanService.HYBRID_ANALYSIS,
                verdict = verdict,
                flagged = flagged,
                engines = engines,
                detail = null,
                scannedAt = null,
                reportUrl = reportUrl,
                error = null,
            )
        } catch (_: Throwable) {
            null
        }
    }
}
