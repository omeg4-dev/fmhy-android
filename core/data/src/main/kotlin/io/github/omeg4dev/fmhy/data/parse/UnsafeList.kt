package io.github.omeg4dev.fmhy.data.parse

import java.util.Locale
import kotlinx.serialization.json.Json

object UnsafeList {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(domainsTxt: String, reasonsJson: String): List<Pair<String, String>> {
        val reasons: Map<String, String> = try {
            json.decodeFromString<Map<String, String>>(reasonsJson)
        } catch (_: Exception) {
            emptyMap()
        }

        val seen = mutableSetOf<String>()
        val result = mutableListOf<Pair<String, String>>()

        for (line in domainsTxt.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue
            }
            val domain = trimmed.lowercase(Locale.ROOT)
            if (seen.add(domain)) {
                val reason = reasons[domain].orEmpty()
                result.add(domain to reason)
            }
        }

        return result
    }
}
