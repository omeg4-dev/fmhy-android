package io.github.omeg4dev.fmhy.browser.download

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

data class VideoSource(val url: String, val mimeType: String, val label: String)

object VideoSources {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Parses the JSON array sent by the page script; returns downloadable single-file videos only. */
    fun parse(json: String, pageUrl: String): List<VideoSource> {
        val array = try {
            jsonParser.parseToJsonElement(json).jsonArray
        } catch (_: Throwable) {
            return emptyList()
        }

        val pageUri = try {
            URI(pageUrl)
        } catch (_: Throwable) {
            null
        }

        val results = mutableListOf<VideoSource>()
        val seen = mutableSetOf<String>()

        for (element in array) {
            val obj = try {
                element.jsonObject
            } catch (_: Throwable) {
                continue
            }

            val rawSrc = try {
                obj["src"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.trim().orEmpty()
            } catch (_: Throwable) {
                ""
            }
            if (rawSrc.isEmpty()) continue

            val resolvedUrl = try {
                if (pageUri != null) {
                    pageUri.resolve(rawSrc).toString()
                } else {
                    URI(rawSrc).toString()
                }
            } catch (_: Throwable) {
                null
            } ?: continue

            val rawType = try {
                obj["type"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            } catch (_: Throwable) {
                null
            }

            val mimeType = if (!rawType.isNullOrBlank()) {
                rawType.substringBefore(';').trim()
            } else {
                mimeFromExtension(resolvedUrl)
            }

            if (!isSingleFile(resolvedUrl, mimeType)) continue

            val dedupeKey = resolvedUrl.substringBefore('#')
            if (!seen.add(dedupeKey)) continue

            val w = try {
                obj["w"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull ?: 0
            } catch (_: Throwable) {
                0
            }
            val h = try {
                obj["h"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull ?: 0
            } catch (_: Throwable) {
                0
            }
            val q = try {
                obj["q"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
            } catch (_: Throwable) {
                null
            }

            val lbl = label(width = w, height = h, quality = q, mimeType = mimeType, url = resolvedUrl)
            results.add(VideoSource(url = resolvedUrl, mimeType = mimeType, label = lbl))

            if (results.size >= 20) break
        }

        return results
    }

    fun isSingleFile(url: String, mimeType: String): Boolean {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return false
        }

        val path = url.substringBefore('?').substringBefore('#').lowercase()
        val streamedExts = listOf(".m3u8", ".m3u", ".mpd", ".ism", ".f4m")
        if (streamedExts.any { path.endsWith(it) }) {
            return false
        }

        val cleanMime = mimeType.substringBefore(';').trim().lowercase()
        val streamedMimes = setOf(
            "application/x-mpegurl",
            "application/vnd.apple.mpegurl",
            "application/dash+xml",
        )
        if (cleanMime in streamedMimes) {
            return false
        }

        return true
    }

    fun label(width: Int, height: Int, quality: String?, mimeType: String, url: String): String {
        val qualityPart = if (!quality.isNullOrBlank()) {
            val trimmed = quality.trim()
            if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() }) {
                "${trimmed}p"
            } else {
                trimmed
            }
        } else if (height > 0) {
            "${height}p"
        } else {
            null
        }

        val path = url.substringBefore('?').substringBefore('#')
        val lastSegment = path.substringAfterLast('/')
        val ext = if (lastSegment.contains('.')) lastSegment.substringAfterLast('.').uppercase() else ""
        val formatPart = if (ext.isNotEmpty()) {
            ext
        } else {
            val cleanMime = mimeType.substringBefore(';').trim()
            if (cleanMime.contains('/')) {
                val subtype = cleanMime.substringAfter('/').uppercase()
                if (subtype.isNotEmpty()) subtype else null
            } else {
                null
            }
        }

        val parts = listOfNotNull(qualityPart, formatPart)
        return if (parts.isEmpty()) "Video" else parts.joinToString(" · ")
    }

    private fun mimeFromExtension(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val lastSegment = path.substringAfterLast('/')
        val ext = if (lastSegment.contains('.')) lastSegment.substringAfterLast('.').lowercase() else ""
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "ogv", "ogg" -> "video/ogg"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            else -> "video/mp4"
        }
    }

    /** Top-page videos first, then those reported by frames; deduped by URL without fragment, max 20. */
    fun merge(top: List<VideoSource>, frames: Collection<List<VideoSource>>): List<VideoSource> =
        (top + frames.flatten()).distinctBy { it.url.substringBefore('#') }.take(20)
}
