package io.github.omeg4dev.fmhy.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Information extracted from a GitHub release payload.
 *
 * @property version Normalized release tag name without a leading "v" or "V" prefix (e.g., "0.6.0").
 * @property notes Extracted release notes from the "What's new" section, or null if missing or empty.
 * @property apkUrl Download URL for the first APK asset found in the release.
 * @property apkSize File size in bytes of the APK asset, or 0 if absent.
 * @property pageUrl Web URL of the release on GitHub.
 */
data class ReleaseInfo(
    val version: String,
    val notes: String?,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String,
)

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("assets") val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
private data class GitHubAssetDto(
    @SerialName("name") val name: String? = null,
    @SerialName("size") val size: Long? = null,
    @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
)

/**
 * Parser and version comparison utilities for GitHub release payloads.
 */
object ReleaseParser {

    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val headingRegex = Regex("""^#+\s*what['’]?s\s+new\b.*$""", RegexOption.IGNORE_CASE)

    /**
     * Parses the JSON response body of a GitHub release endpoint.
     *
     * Returns null if the JSON is invalid, marked as draft or prerelease,
     * lacks a valid parseable version tag, or contains no APK asset with a valid download URL.
     */
    fun parse(json: String): ReleaseInfo? {
        return try {
            val dto = jsonParser.decodeFromString<GitHubReleaseDto>(json)
            if (dto.draft || dto.prerelease) return null

            val rawTag = dto.tagName?.trim() ?: return null
            if (rawTag.isEmpty() || parseVersion(rawTag) == null) return null

            val apkAsset = dto.assets.firstOrNull { asset ->
                asset.name?.endsWith(".apk", ignoreCase = true) == true
            } ?: return null

            val apkUrl = apkAsset.browserDownloadUrl
            if (apkUrl.isNullOrBlank()) return null

            val version = if (rawTag.startsWith("v", ignoreCase = true)) {
                rawTag.substring(1)
            } else {
                rawTag
            }

            ReleaseInfo(
                version = version,
                notes = extractNotes(dto.body),
                apkUrl = apkUrl,
                apkSize = apkAsset.size ?: 0L,
                pageUrl = dto.htmlUrl.orEmpty(),
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Parses a version string into a list of integers.
     *
     * Leading "v" or "V" prefixes are stripped, and pre-release or build metadata
     * (anything following the first '-' or '+') is ignored. Each dot-separated
     * component must consist entirely of digits. Returns null for empty or invalid strings.
     */
    fun parseVersion(raw: String): List<Int>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val strippedPrefix = if (trimmed.startsWith("v", ignoreCase = true)) {
            trimmed.substring(1)
        } else {
            trimmed
        }
        val core = strippedPrefix.substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null

        val parts = core.split('.')
        val result = ArrayList<Int>(parts.size)
        for (part in parts) {
            if (part.isEmpty() || !part.all { it.isDigit() }) {
                return null
            }
            val num = part.toIntOrNull() ?: return null
            result.add(num)
        }
        return result
    }

    /**
     * Compares two version strings numerically, treating missing components as 0 (e.g., "1.2" == "1.2.0").
     *
     * Returns a negative integer, zero, or a positive integer as [a] is less than,
     * equal to, or greater than [b]. If either input cannot be parsed, returns 0.
     */
    fun compareVersions(a: String, b: String): Int {
        val parsedA = parseVersion(a) ?: return 0
        val parsedB = parseVersion(b) ?: return 0
        val maxLen = maxOf(parsedA.size, parsedB.size)
        for (i in 0 until maxLen) {
            val partA = parsedA.getOrElse(i) { 0 }
            val partB = parsedB.getOrElse(i) { 0 }
            val cmp = partA.compareTo(partB)
            if (cmp != 0) return cmp
        }
        return 0
    }

    /**
     * Checks if [available] is strictly newer than [installed] and does not match [skipped].
     *
     * Version comparisons are semantic (e.g., "v0.6.0" matches "0.6.0").
     * Returns false if either [available] or [installed] is not parseable.
     */
    fun isNewer(available: String, installed: String, skipped: String?): Boolean {
        if (parseVersion(available) == null || parseVersion(installed) == null) {
            return false
        }
        if (compareVersions(available, installed) <= 0) {
            return false
        }
        if (!skipped.isNullOrBlank() && parseVersion(skipped) != null) {
            if (compareVersions(available, skipped) == 0) {
                return false
            }
        }
        return true
    }

    /**
     * Extracts bullet points under the "What's new" section of a markdown release body.
     *
     * Matches headings starting with any level of '#' followed by "What's new" or "Whats new"
     * (case-insensitive, supporting regular or curly apostrophes). Captures all lines up to
     * the next heading starting with '#' or the end of the text. Strips leading "- " and "* "
     * bullet prefixes, trims lines, drops blank lines, and joins remaining lines with "\n".
     *
     * Returns null if no such heading is found or if the section contains no non-empty content.
     */
    fun extractNotes(body: String?): String? {
        if (body == null) return null
        val lines = body.lines()
        var inSection = false
        val collected = mutableListOf<String>()

        for (line in lines) {
            val trimmedStart = line.trimStart()
            if (!inSection) {
                if (headingRegex.matches(trimmedStart)) {
                    inSection = true
                }
            } else {
                if (trimmedStart.startsWith("#")) {
                    break
                }
                val trimmed = line.trim()
                val cleaned = when {
                    trimmed.startsWith("- ") -> trimmed.removePrefix("- ").trim()
                    trimmed.startsWith("* ") -> trimmed.removePrefix("* ").trim()
                    else -> trimmed
                }
                if (cleaned.isNotEmpty()) {
                    collected.add(cleaned)
                }
            }
        }

        if (!inSection || collected.isEmpty()) return null
        return collected.joinToString("\n")
    }
}
