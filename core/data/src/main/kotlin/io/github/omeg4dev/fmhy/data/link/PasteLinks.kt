package io.github.omeg4dev.fmhy.data.link

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Base64

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

/** Fetches and decodes pastebin links on demand; results are cached in [cacheFile] (pastes rarely change). */
class PasteResolver(
    private val client: OkHttpClient,
    private val cacheFile: File,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache: MutableMap<String, List<String>> by lazy {
        try {
            json.decodeFromString<Map<String, List<String>>>(cacheFile.readText()).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    /** The URLs behind [url] if it is a pastebin link that hides links; null otherwise or on failure. */
    suspend fun resolve(url: String): List<String>? = withContext(Dispatchers.IO) {
        val raw = PasteLinks.rawUrl(url) ?: return@withContext null
        synchronized(cache) { cache[raw] }?.let { return@withContext it }
        val text = try {
            client.newCall(Request.Builder().url(raw).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body.string()
            }
        } catch (_: Exception) {
            return@withContext null
        }
        val urls = PasteLinks.decode(text).takeIf { it.isNotEmpty() } ?: return@withContext null
        synchronized(cache) {
            cache[raw] = urls
            try {
                cacheFile.writeText(json.encodeToString(cache.toMap()))
            } catch (_: Exception) {
                // the cache is only an optimisation
            }
        }
        urls
    }
}
