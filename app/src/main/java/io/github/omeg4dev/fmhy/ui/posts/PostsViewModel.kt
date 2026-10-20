package io.github.omeg4dev.fmhy.ui.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.doc.DocCleaner
import io.github.omeg4dev.fmhy.data.doc.DocFetcher
import io.github.omeg4dev.fmhy.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
)

data class PostsUiState(
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val posts: List<PostItem> = emptyList(),
    val error: String? = null,
)

class PostsViewModel(
    private val container: AppContainer,
    private val client: OkHttpClient = container.okHttpClient,
    private val cacheDir: File = container.context.filesDir,
    private val docFetcher: DocFetcher = container.docFetcher,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostsUiState())
    val uiState: StateFlow<PostsUiState> = _uiState.asStateFlow()

    private val cacheFile = File(cacheDir, "posts-listing.json")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    init {
        loadPosts()
    }

    fun refresh() {
        loadPosts(force = true)
    }

    fun loadPosts(force: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val now = clock()

            // 1. Try reading cache
            val cachedData = withContext(Dispatchers.IO) {
                readCache()
            }

            if (!force && cachedData != null && (now - cachedData.fetchedAt < 86400L)) {
                _uiState.value = PostsUiState(
                    isLoading = false,
                    isOffline = false,
                    posts = cachedData.posts.map { it.toItem() },
                )
                return@launch
            }

            // 2. Fetch listing from jsDelivr
            try {
                val fetchedPosts = withContext(Dispatchers.IO) {
                    fetchListingFromNetwork()
                }

                // Cache the fresh listing
                withContext(Dispatchers.IO) {
                    val toCache = PostsListingData(
                        fetchedAt = now,
                        posts = fetchedPosts.map {
                            CachedPost(it.path, it.title, it.dateText, it.dateEpoch)
                        },
                    )
                    cacheFile.writeText(json.encodeToString(PostsListingData.serializer(), toCache))
                }

                _uiState.value = PostsUiState(
                    isLoading = false,
                    isOffline = false,
                    posts = fetchedPosts,
                )
            } catch (e: Exception) {
                // Network failed
                if (cachedData != null) {
                    _uiState.value = PostsUiState(
                        isLoading = false,
                        isOffline = true,
                        posts = cachedData.posts.map { it.toItem() },
                    )
                } else {
                    _uiState.value = PostsUiState(
                        isLoading = false,
                        isOffline = true,
                        error = "Could not load posts. Please check your internet connection.",
                    )
                }
            }
        }
    }

    private fun readCache(): PostsListingData? {
        if (!cacheFile.exists()) return null
        return try {
            json.decodeFromString<PostsListingData>(cacheFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchListingFromNetwork(): List<PostItem> {
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
            // filter for /docs/posts/*.md
            if (name.startsWith("/docs/posts/") && name.endsWith(".md", ignoreCase = true)) {
                val fileName = name.removePrefix("/docs/posts/")
                if ('/' !in fileName) {
                    postFiles.add(fileName)
                }
            }
        }

        // Process each post file
        val items = postFiles.map { fileName ->
            resolvePostItem(fileName)
        }

        // Newest first
        return items.sortedByDescending { it.dateEpoch }
    }

    private fun resolvePostItem(fileName: String): PostItem {
        val relPath = "posts/$fileName"
        val baseName = fileName.removeSuffix(".md")

        // Check if docFetcher already has cached file
        val cachedDocFile = File(File(container.context.filesDir, "docs"), DocFetcher.sanitisePath(relPath))
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

        return PostItem(
            path = relPath,
            title = title,
            dateText = dateText,
            dateEpoch = parsedDate,
        )
    }

    private fun CachedPost.toItem() = PostItem(path, title, dateText, dateEpoch)

    companion object {
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
}
