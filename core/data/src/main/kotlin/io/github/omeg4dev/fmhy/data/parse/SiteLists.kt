package io.github.omeg4dev.fmhy.data.parse

import kotlinx.serialization.json.Json

object SiteLists {
    private val json = Json { ignoreUnknownKeys = true }

    fun normalizeListKey(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("!")) return null
        var clean = trimmed
        if (clean.contains("://")) {
            clean = clean.substringAfter("://")
        }
        clean = clean.substringBefore("#").substringBefore("?").trimEnd('/')
        if (clean.isEmpty()) return null

        val slashIdx = clean.indexOf('/')
        val hostPart = if (slashIdx != -1) clean.substring(0, slashIdx) else clean
        val pathPart = if (slashIdx != -1) clean.substring(slashIdx) else ""

        val normalizedHost = hostPart.lowercase().removePrefix("www.")
        if (normalizedHost.isEmpty()) return null
        val normalizedPath = pathPart.lowercase().trimEnd('/')
        return if (normalizedPath.isEmpty()) normalizedHost else "$normalizedHost$normalizedPath"
    }

    fun parseReasons(reasonsJson: String): Map<String, String> {
        return try {
            json.decodeFromString<Map<String, String>>(reasonsJson)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun parseList(content: String): List<String> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        for (line in content.lineSequence()) {
            val key = normalizeListKey(line) ?: continue
            if (seen.add(key)) {
                result.add(key)
            }
        }
        return result
    }
}
