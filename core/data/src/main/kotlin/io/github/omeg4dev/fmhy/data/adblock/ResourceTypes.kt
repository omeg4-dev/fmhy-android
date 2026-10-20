package io.github.omeg4dev.fmhy.data.adblock

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ResourceTypes {
    /** Best guess for a WebView request. */
    fun infer(
        url: String,
        isMainFrame: Boolean,
        acceptHeader: String?,
        isFrameNavigation: Boolean = false
    ): ResourceType {
        if (isMainFrame) return ResourceType.DOCUMENT
        if (isFrameNavigation) return ResourceType.SUBDOCUMENT

        val lowerUrl = url.lowercase()
        if (lowerUrl.startsWith("ws://") || lowerUrl.startsWith("wss://")) {
            return ResourceType.WEBSOCKET
        }

        val path = try {
            val parsed = lowerUrl.toHttpUrlOrNull()
            parsed?.encodedPath ?: lowerUrl.substringBefore('?').substringBefore('#')
        } catch (_: Exception) {
            lowerUrl.substringBefore('?').substringBefore('#')
        }

        val ext = path.substringAfterLast('.', "")
        if (ext.isNotEmpty() && ext.length <= 5) {
            when (ext) {
                "js", "mjs" -> return ResourceType.SCRIPT
                "css" -> return ResourceType.STYLESHEET
                "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "bmp", "avif" -> return ResourceType.IMAGE
                "mp4", "webm", "ogg", "mp3", "wav", "m4a", "aac", "flac" -> return ResourceType.MEDIA
                "woff", "woff2", "ttf", "otf", "eot" -> return ResourceType.FONT
            }
        }

        if (acceptHeader != null) {
            val accept = acceptHeader.lowercase()
            when {
                accept.contains("text/css") -> return ResourceType.STYLESHEET
                accept.contains("text/html") || accept.contains("application/xhtml+xml") -> return ResourceType.SUBDOCUMENT
                accept.contains("image/") -> return ResourceType.IMAGE
                accept.contains("audio/") || accept.contains("video/") -> return ResourceType.MEDIA
                accept.contains("font/") || accept.contains("application/font") -> return ResourceType.FONT
                accept.contains("javascript") || accept.contains("ecmascript") -> return ResourceType.SCRIPT
                accept.contains("application/json") || accept.contains("application/xml") || accept.contains("text/xml") -> return ResourceType.XHR
            }
        }

        return ResourceType.OTHER
    }
}
