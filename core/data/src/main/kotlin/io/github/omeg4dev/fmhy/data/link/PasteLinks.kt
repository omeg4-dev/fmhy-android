package io.github.omeg4dev.fmhy.data.link

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * FMHY hides some links twice: the rentry base64 link decodes to a pastebin page whose raw text is
 * base64 again (one or more URLs, one per line, sometimes encoded more than once).
 */
object PasteLinks {
    private val PASTEBIN = Regex("""^https?://(?:www\.)?pastebin\.com/(?:raw/)?([A-Za-z0-9]{4,16})/?$""")

    /** The raw-text URL of a pastebin link, or null if [url] is not one. */
    fun rawUrl(url: String): String? =
        PASTEBIN.find(url.trim())?.let { "https://pastebin.com/raw/${it.groupValues[1]}" }

    /** The http(s) URLs hidden in a paste's text (base64, up to 3 levels deep); empty if none. */
    fun decode(text: String): List<String> {
        var current = text.trim()
        repeat(4) {
            val urls = urlsIn(current)
            if (urls.isNotEmpty()) return urls
            val bytes = try {
                Base64.getMimeDecoder().decode(current)
            } catch (_: IllegalArgumentException) {
                return emptyList()
            }
            if (bytes.isEmpty()) return emptyList()
            current = String(bytes, Charsets.UTF_8).trim()
        }
        return emptyList()
    }

    private fun urlsIn(text: String): List<String> =
        text.lines()
            .map { it.trim() }
            .filter { (it.startsWith("https://") || it.startsWith("http://")) && ' ' !in it }
            .distinct()
}

/** Outcome of [PasteResolver.resolve]. */
sealed interface PasteResult {
    /** The paste hides these links (one or more). */
    data class Links(val urls: List<String>) : PasteResult
    /** Not a pastebin link, or the paste was loaded but hides no links: use the URL itself. */
    data object NotHidden : PasteResult
    /** The paste could not be loaded (no connection, timeout, HTTP error, block page) after all attempts. */
    data object Failed : PasteResult
}

/**
 * Fetches and decodes pastebin links on demand with retries; results are cached in [cacheFile]
 * (pastes rarely change).
 */
class PasteResolver(
    private val client: OkHttpClient,
    private val cacheFile: File,
    private val retryDelaysMs: List<Long> = listOf(1_000L, 2_500L),
    private val attemptTimeoutMs: Long = 8_000L,
) {
    private val derivedClient: OkHttpClient by lazy {
        client.newBuilder().callTimeout(attemptTimeoutMs, TimeUnit.MILLISECONDS).build()
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val cache: MutableMap<String, List<String>> by lazy {
        try {
            json.decodeFromString<Map<String, List<String>>>(cacheFile.readText()).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    /** Resolves [url] to its hidden links, retrying on transient network or server errors. */
    suspend fun resolve(url: String): PasteResult = withContext(Dispatchers.IO) {
        val raw = PasteLinks.rawUrl(url) ?: return@withContext PasteResult.NotHidden
        synchronized(cache) { cache[raw] }?.let { return@withContext PasteResult.Links(it) }

        val attempts = retryDelaysMs.size + 1
        for (attempt in 0 until attempts) {
            if (attempt > 0) {
                delay(retryDelaysMs[attempt - 1])
            }
            try {
                val shouldRetry = derivedClient.newCall(Request.Builder().url(raw).build()).execute().use { resp ->
                    if (resp.code == 404 || resp.code == 410) {
                        return@withContext PasteResult.Failed
                    }
                    if (!resp.isSuccessful) {
                        return@use true
                    }
                    val isHtml = resp.body.contentType()?.subtype.equals("html", ignoreCase = true)
                    if (isHtml) {
                        return@use true
                    }
                    val text = resp.body.string()
                    val urls = PasteLinks.decode(text)
                    if (urls.isNotEmpty()) {
                        synchronized(cache) {
                            cache[raw] = urls
                            try {
                                cacheFile.writeText(json.encodeToString(cache.toMap()))
                            } catch (_: Exception) {
                                // the cache is only an optimisation
                            }
                        }
                        return@withContext PasteResult.Links(urls)
                    } else {
                        return@withContext PasteResult.NotHidden
                    }
                }
                if (shouldRetry) {
                    continue
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is IOException) {
                    continue
                }
                return@withContext PasteResult.Failed
            }
        }
        PasteResult.Failed
    }
}
