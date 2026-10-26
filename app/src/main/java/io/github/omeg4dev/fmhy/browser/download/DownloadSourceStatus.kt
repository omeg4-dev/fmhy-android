package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import io.github.omeg4dev.fmhy.data.safety.Verdict
import io.github.omeg4dev.fmhy.data.safety.Via
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SourceKind {
    STARRED,
    LISTED,
    NOT_LISTED,
    UNSAFE,
    LOOKALIKE,
}

data class DownloadSourceStatus(
    val kind: SourceKind,
    val isMirror: Boolean,
    val fileHost: String,
    val pageHost: String?,
    val differs: Boolean,
    val fileKind: SourceKind,
    val pageKind: SourceKind?,
    val entryId: String?,
    val wikiDateMs: Long?,
    val lookalikeOf: String?,
) {
    fun toJson(): String {
        val dto = DownloadSourceStatusDto(
            kind = kind.name,
            isMirror = isMirror,
            fileHost = fileHost,
            pageHost = pageHost,
            differs = differs,
            fileKind = fileKind.name,
            pageKind = pageKind?.name,
            entryId = entryId,
            wikiDateMs = wikiDateMs,
            lookalikeOf = lookalikeOf,
        )
        return jsonCodec.encodeToString(DownloadSourceStatusDto.serializer(), dto)
    }

    companion object {
        private val jsonCodec = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        private fun normalizeHost(host: String): String =
            host.trim().lowercase().removePrefix("www.")

        private fun mapVerdictToKind(verdict: Verdict): SourceKind = when (verdict.status) {
            SafetyStatus.STARRED -> SourceKind.STARRED
            SafetyStatus.LISTED,
            SafetyStatus.DOMAIN_LISTED,
            SafetyStatus.FMHY_SITE -> SourceKind.LISTED
            SafetyStatus.UNSAFE,
            SafetyStatus.POTENTIALLY_UNSAFE -> SourceKind.UNSAFE
            SafetyStatus.NO_DATA -> if (verdict.lookalikes.isNotEmpty()) SourceKind.LOOKALIKE else SourceKind.NOT_LISTED
            SafetyStatus.SEARCH_ENGINE,
            SafetyStatus.INVALID -> SourceKind.NOT_LISTED
        }

        private fun extractHost(url: String): String? {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if (!trimmed.contains("://")) "https://$trimmed" else trimmed
            return try {
                val u = URI(withScheme)
                (u.host ?: withScheme.substringAfter("://").substringBefore('/').substringBefore(':').substringBefore('?'))
                    .trim()
                    .lowercase()
                    .removePrefix("www.")
            } catch (_: Exception) {
                withScheme.substringAfter("://").substringBefore('/').substringBefore(':').substringBefore('?')
                    .trim()
                    .lowercase()
                    .removePrefix("www.")
            }.ifEmpty { null }
        }

        fun from(
            fileVerdict: Verdict,
            pageVerdict: Verdict?,
            fileHost: String,
            pageHost: String?,
            wikiDateMs: Long?,
        ): DownloadSourceStatus {
            val fileKind = mapVerdictToKind(fileVerdict)
            val pageKind = pageVerdict?.let(::mapVerdictToKind)

            val differs = pageHost != null && normalizeHost(pageHost) != normalizeHost(fileHost)

            val combinedKind = when {
                fileKind == SourceKind.UNSAFE || pageKind == SourceKind.UNSAFE -> SourceKind.UNSAFE
                fileKind == SourceKind.LOOKALIKE || pageKind == SourceKind.LOOKALIKE -> SourceKind.LOOKALIKE
                else -> fileKind
            }

            val fileSideDecides = fileKind == SourceKind.STARRED || fileKind == SourceKind.LISTED
            val pageSideDecides = !fileSideDecides && (pageKind == SourceKind.STARRED || pageKind == SourceKind.LISTED)

            val entryId = when {
                fileSideDecides -> fileVerdict.matches.firstOrNull()?.id
                pageSideDecides -> pageVerdict.matches.firstOrNull()?.id
                else -> null
            }

            val isMirror = when {
                fileSideDecides -> fileVerdict.via == Via.MIRROR
                pageSideDecides -> pageVerdict.via == Via.MIRROR
                else -> false
            }

            val lookalikeEntry = when {
                fileKind == SourceKind.LOOKALIKE -> fileVerdict.lookalikes.firstOrNull()
                pageKind == SourceKind.LOOKALIKE -> pageVerdict.lookalikes.firstOrNull()
                else -> null
            }
            val lookalikeOf = lookalikeEntry?.let { entry ->
                entry.name.ifBlank { extractHost(entry.url) ?: entry.url }
            }

            return DownloadSourceStatus(
                kind = combinedKind,
                isMirror = isMirror,
                fileHost = fileHost,
                pageHost = pageHost,
                differs = differs,
                fileKind = fileKind,
                pageKind = pageKind,
                entryId = entryId,
                wikiDateMs = wikiDateMs,
                lookalikeOf = lookalikeOf,
            )
        }

        fun fromJson(json: String?): DownloadSourceStatus? {
            if (json.isNullOrBlank()) return null
            return try {
                val dto = jsonCodec.decodeFromString(DownloadSourceStatusDto.serializer(), json)
                val kind = SourceKind.valueOf(dto.kind)
                val fileKind = SourceKind.valueOf(dto.fileKind)
                val pageKind = dto.pageKind?.let { SourceKind.valueOf(it) }
                DownloadSourceStatus(
                    kind = kind,
                    isMirror = dto.isMirror,
                    fileHost = dto.fileHost,
                    pageHost = dto.pageHost,
                    differs = dto.differs,
                    fileKind = fileKind,
                    pageKind = pageKind,
                    entryId = dto.entryId,
                    wikiDateMs = dto.wikiDateMs,
                    lookalikeOf = dto.lookalikeOf,
                )
            } catch (_: Throwable) {
                null
            }
        }
    }
}

@Serializable
private data class DownloadSourceStatusDto(
    val kind: String,
    val isMirror: Boolean,
    val fileHost: String,
    val pageHost: String? = null,
    val differs: Boolean,
    val fileKind: String,
    val pageKind: String? = null,
    val entryId: String? = null,
    val wikiDateMs: Long? = null,
    val lookalikeOf: String? = null,
)

object DownloadSourceText {
    fun title(kind: SourceKind): String = when (kind) {
        SourceKind.STARRED -> "Starred on FMHY"
        SourceKind.LISTED -> "Listed on FMHY"
        SourceKind.NOT_LISTED -> "Not listed on FMHY"
        SourceKind.UNSAFE -> "Listed as unsafe by FMHY"
        SourceKind.LOOKALIKE -> "Known fake lookalike of a listed site"
    }

    const val mirrorSuffix: String = " (mirror of a listed site)"

    fun differsLine(pageHost: String, fileHost: String): String =
        "The file comes from $fileHost, not from the page ($pageHost)."

    fun dateLabel(wikiDateMs: Long?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (wikiDateMs == null) return "FMHY data date unknown"
        val zdt = Instant.ofEpochMilli(wikiDateMs).atZone(zone)
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        return "FMHY data from ${formatter.format(zdt)}"
    }

    const val REMINDER: String =
        "FMHY status only says where a site is listed. It does not mean a file is safe."

    fun badge(kind: SourceKind): String = when (kind) {
        SourceKind.STARRED -> "Starred"
        SourceKind.LISTED -> "Listed"
        SourceKind.NOT_LISTED -> "Not listed"
        SourceKind.UNSAFE -> "Unsafe"
        SourceKind.LOOKALIKE -> "Lookalike"
    }
}
