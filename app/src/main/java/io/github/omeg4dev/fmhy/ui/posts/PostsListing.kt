package io.github.omeg4dev.fmhy.ui.posts

import io.github.omeg4dev.fmhy.data.doc.DocCleaner
import io.github.omeg4dev.fmhy.data.doc.DocFetcher
import io.github.omeg4dev.fmhy.ui.common.MarkdownBlock
import io.github.omeg4dev.fmhy.ui.common.parseMarkdownBlocks
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class CachedPost(
    val path: String,
    val title: String,
    val dateText: String,
    val dateEpoch: Long,
)

@Serializable
data class PostsListingData(
    val fetchedAt: Long,
    val posts: List<CachedPost>,
)

data class PostItem(
    val path: String,
    val title: String,
    val dateText: String,
    val dateEpoch: Long,
    val excerpt: String = "",
)

object PostsListing {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun readCache(cacheFile: File): PostsListingData? {
        if (!cacheFile.exists()) return null
        return try {
            json.decodeFromString<PostsListingData>(cacheFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun writeCache(cacheFile: File, data: PostsListingData) {
        try {
            cacheFile.writeText(json.encodeToString(PostsListingData.serializer(), data))
        } catch (_: Exception) {}
    }

    fun extractExcerptFromCachedDoc(file: File): String {
        if (!file.exists()) return ""
        return try {
            val raw = file.readText()
            val cleaned = DocCleaner.clean(raw)
            val blocks = parseMarkdownBlocks(cleaned)
            val firstPara = blocks.filterIsInstance<MarkdownBlock.Paragraph>().firstOrNull()?.text.orEmpty()
            firstPara.trim()
        } catch (_: Exception) {
            ""
        }
    }

    /** Cache file for the post file names; bumped when the listing source changed (old caches held a stale list). */
    const val CACHE_FILE_NAME = "posts-listing-v2.json"

    /**
     * Post file names under `docs/posts/`. GitHub's contents API first: jsDelivr's flat listing of `@main` is
     * served from a long-lived cache and stopped at May 2025. jsDelivr stays as the fallback (rate limits).
     */
    suspend fun fetchListingFromNetwork(client: OkHttpClient): List<String> =
        try {
            fetchFromGitHub(client)
        } catch (e: Exception) {
            fetchFromJsDelivr(client)
        }

    private fun fetchFromGitHub(client: OkHttpClient): List<String> {
        val request = Request.Builder()
            .url("https://api.github.com/repos/fmhy/edit/contents/docs/posts?ref=main")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.message}")
            resp.body.string()
        }
        return parseGitHubListing(body).ifEmpty { throw Exception("Empty listing from GitHub") }
    }

    internal fun parseGitHubListing(body: String): List<String> =
        json.parseToJsonElement(body).jsonArray.mapNotNull { item ->
            val obj = item.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val type = obj["type"]?.jsonPrimitive?.content
            name.takeIf { type == "file" && it.endsWith(".md", ignoreCase = true) }
        }

    private fun fetchFromJsDelivr(client: OkHttpClient): List<String> {
        val url = "https://data.jsdelivr.com/v1/packages/gh/fmhy/edit@main?structure=flat"
        val request = Request.Builder().url(url).build()

        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.message}")
            resp.body.string()
        }

        val jsonElement = json.parseToJsonElement(body)
        val filesArray = jsonElement.jsonObject["files"]?.jsonArray
            ?: throw Exception("Invalid response structure from jsDelivr")

        val postFiles = mutableListOf<String>()
        for (fileObj in filesArray) {
            val name = fileObj.jsonObject["name"]?.jsonPrimitive?.content ?: continue
            if (name.startsWith("/docs/posts/") && name.endsWith(".md", ignoreCase = true)) {
                val fileName = name.removePrefix("/docs/posts/")
                if ('/' !in fileName) {
                    postFiles.add(fileName)
                }
            }
        }
        return postFiles
    }

    fun resolvePostItem(fileName: String, filesDir: File): PostItem {
        val relPath = "posts/$fileName"
        val baseName = fileName.removeSuffix(".md")

        val cachedDocFile = File(File(filesDir, "docs"), DocFetcher.sanitisePath(relPath))
        var frontMatter: Map<String, String> = emptyMap()
        if (cachedDocFile.exists()) {
            try {
                frontMatter = DocCleaner.extractFrontMatter(cachedDocFile.readText())
            } catch (_: Exception) {}
        }

        val fmTitle = frontMatter["title"]?.trim()
        val fmDate = frontMatter["date"]?.trim()

        val parsedDate = parseDate(baseName, fmDate)
        val title = fmTitle?.ifBlank { null } ?: formatDefaultTitle(baseName)
        val dateText = formatDateText(baseName, fmDate, parsedDate)
        val excerpt = extractExcerptFromCachedDoc(cachedDocFile)

        return PostItem(
            path = relPath,
            title = title,
            dateText = dateText,
            dateEpoch = parsedDate,
            excerpt = excerpt,
        )
    }

    private val MONTH_MAP = mapOf(
        "jan" to 1, "january" to 1,
        "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "may" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8,
        "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12,
    )

    private val MONTH_NAMES = arrayOf(
        "", "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    private val MONTH_FILE_REGEX = Regex(
        """^(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)[-_](\d{4})$""",
        RegexOption.IGNORE_CASE
    )

    fun parseDate(fileName: String, frontMatterDate: String?): Long {
        if (!frontMatterDate.isNullOrBlank()) {
            try {
                val localDate = LocalDate.parse(frontMatterDate.trim())
                return localDate.toEpochDay() * 86400L
            } catch (_: Exception) {}
        }

        val match = MONTH_FILE_REGEX.matchEntire(fileName.trim())
        if (match != null) {
            val monthStr = match.groupValues[1].lowercase()
            val month = MONTH_MAP[monthStr] ?: 1
            val year = match.groupValues[2].toIntOrNull() ?: 2026
            return LocalDate.of(year, month, 1).toEpochDay() * 86400L
        }

        return 0L
    }

    fun formatDefaultTitle(fileName: String): String {
        val match = MONTH_FILE_REGEX.matchEntire(fileName.trim())
        if (match != null) {
            val monthStr = match.groupValues[1].lowercase()
            val monthNum = MONTH_MAP[monthStr] ?: 1
            val monthName = MONTH_NAMES.getOrElse(monthNum) { "Monthly" }
            val year = match.groupValues[2]
            return "Monthly Updates [$monthName $year]"
        }

        return fileName.split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    fun formatDateText(fileName: String, frontMatterDate: String?, dateEpoch: Long): String {
        if (!frontMatterDate.isNullOrBlank()) {
            return frontMatterDate
        }
        val match = MONTH_FILE_REGEX.matchEntire(fileName.trim())
        if (match != null) {
            val monthStr = match.groupValues[1].lowercase()
            val monthNum = MONTH_MAP[monthStr] ?: 1
            val monthName = MONTH_NAMES.getOrElse(monthNum) { "" }
            val year = match.groupValues[2]
            return "$monthName $year"
        }
        if (dateEpoch > 0) {
            val ld = LocalDate.ofEpochDay(dateEpoch / 86400L)
            return ld.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
        }
        return ""
    }
}
