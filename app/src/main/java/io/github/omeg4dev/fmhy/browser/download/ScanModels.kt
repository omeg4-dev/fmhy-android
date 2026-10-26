package io.github.omeg4dev.fmhy.browser.download

import java.io.InputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ScanService(val label: String) {
    VIRUSTOTAL("VirusTotal"),
    HYBRID_ANALYSIS("Hybrid Analysis"),
}

enum class ScanVerdict(val label: String, val rank: Int) {
    NO_FLAGS("No flags", 1),
    SUSPICIOUS("Suspicious", 2),
    DANGEROUS("Dangerous", 3),
    UNKNOWN("Unknown", 0),
}

enum class ScanError {
    BAD_KEY,
    LIMIT,
    OFFLINE,
    TOO_BIG,
    OTHER,
}

enum class ScanKind {
    FILE,
    LINK,
}

data class ServiceResult(
    val service: ScanService,
    val verdict: ScanVerdict,
    val flagged: Int? = null,
    val engines: Int? = null,
    val detail: String? = null,
    val scannedAt: Long? = null,
    val reportUrl: String? = null,
    val error: ScanError? = null,
)

data class PendingAnalysis(
    val service: ScanService,
    val analysisId: String,
    val startedAt: Long,
)

data class ScanResult(
    val kind: ScanKind,
    val sha256: String?,
    val services: List<ServiceResult>,
    val checkedAt: Long,
    val pending: List<PendingAnalysis> = emptyList(),
) {
    val verdict: ScanVerdict get() = ScanVerdicts.combine(services)
    val isRunning: Boolean get() = pending.isNotEmpty()

    fun toJson(): String {
        val dto = ScanResultDto(
            kind = kind.name,
            sha256 = sha256,
            services = services.map { s ->
                ServiceResultDto(
                    service = s.service.name,
                    verdict = s.verdict.name,
                    flagged = s.flagged,
                    engines = s.engines,
                    detail = s.detail,
                    scannedAt = s.scannedAt,
                    reportUrl = s.reportUrl,
                    error = s.error?.name,
                )
            },
            checkedAt = checkedAt,
            pending = pending.map { p ->
                PendingAnalysisDto(
                    service = p.service.name,
                    analysisId = p.analysisId,
                    startedAt = p.startedAt,
                )
            },
        )
        return jsonCodec.encodeToString(ScanResultDto.serializer(), dto)
    }

    companion object {
        private val jsonCodec = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        fun fromJson(json: String?): ScanResult? {
            if (json.isNullOrBlank()) return null
            return try {
                val dto = jsonCodec.decodeFromString(ScanResultDto.serializer(), json)
                val kind = try {
                    ScanKind.valueOf(dto.kind)
                } catch (_: Exception) {
                    return null
                }
                val validServices = dto.services.mapNotNull { s ->
                    val service = try {
                        ScanService.valueOf(s.service)
                    } catch (_: Exception) {
                        return@mapNotNull null
                    }
                    val verdict = try {
                        ScanVerdict.valueOf(s.verdict)
                    } catch (_: Exception) {
                        return@mapNotNull null
                    }
                    val error = s.error?.let {
                        try {
                            ScanError.valueOf(it)
                        } catch (_: Exception) {
                            return@mapNotNull null
                        }
                    }
                    ServiceResult(
                        service = service,
                        verdict = verdict,
                        flagged = s.flagged,
                        engines = s.engines,
                        detail = s.detail,
                        scannedAt = s.scannedAt,
                        reportUrl = s.reportUrl,
                        error = error,
                    )
                }
                val validPending = dto.pending.mapNotNull { p ->
                    val service = try {
                        ScanService.valueOf(p.service)
                    } catch (_: Exception) {
                        return@mapNotNull null
                    }
                    PendingAnalysis(
                        service = service,
                        analysisId = p.analysisId,
                        startedAt = p.startedAt,
                    )
                }
                ScanResult(
                    kind = kind,
                    sha256 = dto.sha256,
                    services = validServices,
                    checkedAt = dto.checkedAt,
                    pending = validPending,
                )
            } catch (_: Throwable) {
                null
            }
        }
    }
}

@Serializable
private data class ScanResultDto(
    val kind: String,
    val sha256: String? = null,
    val services: List<ServiceResultDto> = emptyList(),
    val checkedAt: Long,
    val pending: List<PendingAnalysisDto> = emptyList(),
)

@Serializable
private data class ServiceResultDto(
    val service: String,
    val verdict: String,
    val flagged: Int? = null,
    val engines: Int? = null,
    val detail: String? = null,
    val scannedAt: Long? = null,
    val reportUrl: String? = null,
    val error: String? = null,
)

@Serializable
private data class PendingAnalysisDto(
    val service: String,
    val analysisId: String,
    val startedAt: Long,
)

object ScanText {
    const val REMINDER = "“No flags” is not a guarantee, and “unknown” does not mean safe."

    fun engines(flagged: Int, engines: Int) = "$flagged of $engines engines flagged this"

    fun error(service: ScanService, e: ScanError): String = when (e) {
        ScanError.BAD_KEY -> "The ${service.label} key isn't valid. Check it in settings or open the ${service.label} website instead."
        ScanError.LIMIT -> "Your free ${service.label} key has reached its usage limit. Try again later or open the ${service.label} website instead."
        ScanError.OFFLINE -> "Couldn't reach ${service.label}. Check your connection."
        ScanError.TOO_BIG -> "This file is too big to upload to ${service.label}. Open the ${service.label} website instead."
        ScanError.OTHER -> "${service.label} couldn't check this right now."
    }

    const val UNKNOWN_FILE = "Not known to this service yet."
}

interface ScanClient {
    val service: ScanService
    val maxUploadBytes: Long
    suspend fun lookupFile(sha256: String): ServiceResult
    suspend fun lookupLink(url: String): ServiceResult
    suspend fun upload(fileName: String, size: Long, open: () -> InputStream): PendingAnalysis
    suspend fun poll(pending: PendingAnalysis, sha256: String?): ServiceResult?
}

class ScanException(val error: ScanError) : Exception(error.name)
