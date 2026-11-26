package io.github.omeg4dev.fmhy.data.nsfw

import io.github.omeg4dev.fmhy.data.parse.Base64Map
import io.github.omeg4dev.fmhy.data.parse.FmhyParser
import io.github.omeg4dev.fmhy.data.parse.SectionInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Fetches, validates, caches, and filters FMHY's NSFW Markdown list on demand.
 * Never persists filtered or parsed rows into SQLite; caches raw Markdown atomically
 * under `<cacheDir>/nsfw/list.md` and applies [NsfwFilter] on every read.
 */
open class NsfwSource(
    private val client: OkHttpClient,
    private val cacheDir: File,
    private val checkpointUrl: String = NSFW_CHECKPOINT_URL,
    private val rawBaseUrl: String = NSFW_RAW_BASE_URL,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val nsfwCacheDir: File get() = File(cacheDir, CACHE_SUBDIR)
    private val cacheFile: File get() = File(nsfwCacheDir, CACHE_FILENAME)
    private val tempCacheFile: File get() = File(nsfwCacheDir, "$CACHE_FILENAME.tmp")

    open suspend fun load(
        forceRefresh: Boolean = false,
        allowNetwork: Boolean = true,
    ): NsfwLoadResult = withContext(ioDispatcher) {
        mutex.withLock {
            val existingRaw = readValidCacheText()
            val cacheAgeMs = if (existingRaw != null && cacheFile.exists()) {
                clock() - cacheFile.lastModified()
            } else {
                Long.MAX_VALUE
            }
            val isFresh = existingRaw != null && cacheAgeMs in 0 until CACHE_TTL_MS

            if (!forceRefresh && existingRaw != null && isFresh) {
                return@withLock buildSuccess(existingRaw, stale = false)
            }

            if (!allowNetwork) {
                return@withLock if (!forceRefresh && existingRaw != null) {
                    buildSuccess(existingRaw, stale = true)
                } else {
                    NsfwLoadResult.BlockedByWifi
                }
            }

            try {
                val downloadedRaw = fetchValidatedRawMarkdown()
                writeCacheAtomically(downloadedRaw)
                buildSuccess(downloadedRaw, stale = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (!forceRefresh && existingRaw != null) {
                    buildSuccess(existingRaw, stale = true)
                } else {
                    NsfwLoadResult.Failed(e.message)
                }
            } catch (e: InvalidNsfwPayloadException) {
                if (!forceRefresh && existingRaw != null) {
                    buildSuccess(existingRaw, stale = true)
                } else {
                    NsfwLoadResult.Failed(e.message)
                }
            }
        }
    }

    /** Reads and filters the local cache if present, without touching the network. */
    open suspend fun readCached(): NsfwLoadResult.Success? = withContext(ioDispatcher) {
        readValidCacheText()?.let { buildSuccess(it, stale = true) }
    }

    /** Removes any cached raw list from disk on [ioDispatcher]. */
    suspend fun clearCacheOnIo() = withContext(ioDispatcher) {
        clearCache()
    }

    /** Removes any cached raw list from disk. */
    open fun clearCache() {
        cacheFile.delete()
        tempCacheFile.delete()
    }

    private fun buildSuccess(rawMarkdown: String, stale: Boolean): NsfwLoadResult.Success {
        val filtered = NsfwFilter.filter(rawMarkdown)
        val built = NsfwSectionBuilder.build(filtered)
        return NsfwLoadResult.Success(
            headings = built.headings,
            items = built.items,
            entries = built.entries,
            stale = stale,
        )
    }

    private fun readValidCacheText(): String? {
        val file = cacheFile
        if (!file.isFile || file.length() > MAX_BODY_BYTES) return null
        return try {
            val raw = file.readText(Charsets.UTF_8)
            if (isValidRawMarkdown(raw)) raw else null
        } catch (_: IOException) {
            null
        }
    }

    private fun writeCacheAtomically(rawMarkdown: String) {
        val dir = nsfwCacheDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val tmp = tempCacheFile
        val target = cacheFile
        try {
            tmp.writeText(rawMarkdown, Charsets.UTF_8)
            val now = clock()
            tmp.setLastModified(now)
            if (!tmp.renameTo(target)) {
                target.writeText(rawMarkdown, Charsets.UTF_8)
                target.setLastModified(now)
            } else {
                target.setLastModified(now)
            }
        } finally {
            if (tmp.exists()) {
                tmp.delete()
            }
        }
    }

    private suspend fun fetchValidatedRawMarkdown(): String {
        val checkpointHtml = executeGet(url = checkpointUrl) { resp -> readCappedBody(resp) }

        val rawUrl = NsfwCheckpoint.extractYasoRawUrl(checkpointHtml, rawBaseUrl)
            ?: throw InvalidNsfwPayloadException("Missing yaso.su mirror in checkpoint page")

        val rawMarkdown = executeGet(url = rawUrl) { resp ->
            val contentTypeHeader = resp.header("Content-Type") ?: resp.body.contentType()?.toString()
            if (isHtmlContentType(contentTypeHeader)) {
                throw InvalidNsfwPayloadException("Unexpected HTML response for NSFW list")
            }
            val declaredLen = resp.header("Content-Length")?.toLongOrNull() ?: resp.body.contentLength()
            if (declaredLen > MAX_BODY_BYTES) {
                throw InvalidNsfwPayloadException("Response exceeds 2 MB limit")
            }
            readCappedBody(resp)
        }

        if (!isValidRawMarkdown(rawMarkdown)) {
            throw InvalidNsfwPayloadException("NSFW markdown missing required heading or entry")
        }
        return rawMarkdown
    }

    private suspend fun <T> executeGet(
        url: String,
        block: (Response) -> T,
    ): T {
        val call = client.newCall(Request.Builder().url(url).get().build())
        val job = kotlin.coroutines.coroutineContext[Job]
        val cancelHandle = job?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        return try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code}")
                }
                block(resp)
            }
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun readCappedBody(resp: Response): String {
        val stream = resp.body.byteStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val out = ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            total += read
            if (total > MAX_BODY_BYTES) {
                throw InvalidNsfwPayloadException("Response body exceeds 2 MB limit")
            }
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun isHtmlContentType(contentType: String?): Boolean {
        val normalized = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return normalized == "text/html" ||
            normalized == "application/xhtml+xml" ||
            normalized.contains("html")
    }

    private fun isValidRawMarkdown(markdown: String): Boolean {
        if (markdown.isBlank()) return false
        val hasLevel2Heading = markdown.lineSequence().any { it.startsWith("## ") }
        if (!hasLevel2Heading) return false
        val parsed = FmhyParser.parseSection(
            section = VALIDATION_SECTION_INFO,
            markdown = markdown,
            b64 = Base64Map(""),
        )
        return parsed.entries.isNotEmpty()
    }

    private class InvalidNsfwPayloadException(message: String) : Exception(message)

    companion object {
        private const val CACHE_SUBDIR = "nsfw"
        private const val CACHE_FILENAME = "list.md"
        private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
        private const val MAX_BODY_BYTES = 2L * 1024L * 1024L
        private val VALIDATION_SECTION_INFO = SectionInfo(
            id = NSFW_SECTION_ID,
            title = NSFW_SECTION_TITLE,
            description = "",
            file = "nsfw.md",
            ord = Int.MAX_VALUE,
        )
    }
}
