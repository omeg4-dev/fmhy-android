package io.github.omeg4dev.fmhy.data.doc

import io.github.omeg4dev.fmhy.data.sync.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

@Serializable
data class DocIndexEntry(
    val etag: String? = null,
    val fetchedAt: Long = 0L,
)

sealed interface FetchResponse {
    data class Success(val body: String, val etag: String?) : FetchResponse
    data object NotModified : FetchResponse
    data class Error(val statusCode: Int, val message: String) : FetchResponse
}

fun interface DocFetchFunction {
    suspend fun fetch(url: String, ifNoneMatch: String?): FetchResponse
}

class DocFetcher(
    private val docsDir: File,
    private val client: OkHttpClient? = null,
    private val sources: Sources = Sources(),
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val fetchFunction: DocFetchFunction? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val indexFile = File(docsDir, "index.json")

    init {
        docsDir.mkdirs()
    }

    companion object {
        fun normalisePath(path: String): String =
            path.trim()
                .trimStart('/')
                .removePrefix("docs/")
                .trimStart('/')

        fun sanitisePath(path: String): String =
            path.replace('/', '_').replace('\\', '_')
    }

    private fun readIndex(): MutableMap<String, DocIndexEntry> = synchronized(indexFile) {
        if (!indexFile.exists()) return mutableMapOf()
        try {
            json.decodeFromString<Map<String, DocIndexEntry>>(indexFile.readText()).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveIndex(index: Map<String, DocIndexEntry>) = synchronized(indexFile) {
        try {
            indexFile.writeText(json.encodeToString(index))
        } catch (_: Exception) {
            // Index saving failure is non-fatal
        }
    }

    suspend fun get(path: String, maxAgeSec: Long = 7 * 86400L): DocResult = withContext(Dispatchers.IO) {
        val cleanPath = normalisePath(path)
        val cacheFile = File(docsDir, sanitisePath(cleanPath))
        val index = readIndex()
        val cachedEntry = index[cleanPath]
        val now = clock()

        // 1. Check if fresh in cache
        if (cacheFile.exists() && cachedEntry != null && maxAgeSec > 0 && (now - cachedEntry.fetchedAt < maxAgeSec)) {
            val raw = try { cacheFile.readText() } catch (_: Exception) { null }
            if (raw != null) {
                return@withContext DocResult.Success(
                    text = DocCleaner.clean(raw),
                    stale = false,
                    rawText = raw,
                    frontMatter = DocCleaner.extractFrontMatter(raw),
                )
            }
        }

        // 2. Fetch with ETag from primary, then fallback
        val primaryUrl = "${sources.primary.trimEnd('/')}/docs/$cleanPath"
        val fallbackUrl = "${sources.fallback.trimEnd('/')}/docs/$cleanPath"
        val etag = cachedEntry?.etag

        var response: FetchResponse? = null
        var lastError: Exception? = null

        try {
            response = doFetch(primaryUrl, etag)
            if (response is FetchResponse.Error) {
                response = doFetch(fallbackUrl, etag)
            }
        } catch (e: Exception) {
            lastError = e
            try {
                response = doFetch(fallbackUrl, etag)
            } catch (fallbackEx: Exception) {
                lastError = fallbackEx
            }
        }

        when (response) {
            is FetchResponse.Success -> {
                try {
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeText(response.body)
                    index[cleanPath] = DocIndexEntry(etag = response.etag, fetchedAt = now)
                    saveIndex(index)
                    DocResult.Success(
                        text = DocCleaner.clean(response.body),
                        stale = false,
                        rawText = response.body,
                        frontMatter = DocCleaner.extractFrontMatter(response.body),
                    )
                } catch (e: Exception) {
                    DocResult.Success(
                        text = DocCleaner.clean(response.body),
                        stale = false,
                        rawText = response.body,
                        frontMatter = DocCleaner.extractFrontMatter(response.body),
                    )
                }
            }
            is FetchResponse.NotModified -> {
                index[cleanPath] = cachedEntry?.copy(fetchedAt = now) ?: DocIndexEntry(etag = etag, fetchedAt = now)
                saveIndex(index)
                if (cacheFile.exists()) {
                    val raw = cacheFile.readText()
                    DocResult.Success(
                        text = DocCleaner.clean(raw),
                        stale = false,
                        rawText = raw,
                        frontMatter = DocCleaner.extractFrontMatter(raw),
                    )
                } else {
                    // NotModified but cache missing: re-fetch without ETag
                    val freshResp = try {
                        doFetch(primaryUrl, null)
                    } catch (_: Exception) {
                        try { doFetch(fallbackUrl, null) } catch (_: Exception) { null }
                    }
                    if (freshResp is FetchResponse.Success) {
                        cacheFile.writeText(freshResp.body)
                        index[cleanPath] = DocIndexEntry(etag = freshResp.etag, fetchedAt = now)
                        saveIndex(index)
                        DocResult.Success(
                            text = DocCleaner.clean(freshResp.body),
                            stale = false,
                            rawText = freshResp.body,
                            frontMatter = DocCleaner.extractFrontMatter(freshResp.body),
                        )
                    } else {
                        DocResult.Error("Document cache missing for $cleanPath")
                    }
                }
            }
            else -> {
                // Network failed or HTTP error: check if cached copy exists
                if (cacheFile.exists()) {
                    try {
                        val raw = cacheFile.readText()
                        DocResult.Success(
                            text = DocCleaner.clean(raw),
                            stale = true,
                            rawText = raw,
                            frontMatter = DocCleaner.extractFrontMatter(raw),
                        )
                    } catch (e: Exception) {
                        DocResult.Error("Failed to read cached copy of $cleanPath", e)
                    }
                } else {
                    val msg = (response as? FetchResponse.Error)?.message
                        ?: lastError?.message
                        ?: "Failed to fetch $cleanPath"
                    DocResult.Error(msg, lastError)
                }
            }
        }
    }

    private suspend fun doFetch(url: String, ifNoneMatch: String?): FetchResponse {
        if (fetchFunction != null) {
            return fetchFunction.fetch(url, ifNoneMatch)
        }
        val okClient = client ?: OkHttpClient()
        val reqBuilder = Request.Builder().url(url)
        if (!ifNoneMatch.isNullOrBlank()) {
            reqBuilder.header("If-None-Match", ifNoneMatch)
        }
        return okClient.newCall(reqBuilder.build()).execute().use { resp ->
            when (resp.code) {
                304 -> FetchResponse.NotModified
                in 200..299 -> FetchResponse.Success(resp.body.string(), resp.header("ETag"))
                else -> FetchResponse.Error(resp.code, "HTTP ${resp.code}: ${resp.message}")
            }
        }
    }
}
