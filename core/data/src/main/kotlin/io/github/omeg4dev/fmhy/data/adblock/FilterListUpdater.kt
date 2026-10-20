package io.github.omeg4dev.fmhy.data.adblock

import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

interface ListFetcher {
    fun fetch(url: String, etag: String?): FetchOutcome
}

sealed interface FetchOutcome {
    data class Content(val text: String, val etag: String?) : FetchOutcome
    data object NotModified : FetchOutcome
    data class Error(val message: String) : FetchOutcome
}

class OkHttpListFetcher(private val client: OkHttpClient) : ListFetcher {
    override fun fetch(url: String, etag: String?): FetchOutcome {
        return try {
            val reqBuilder = Request.Builder().url(url)
            if (etag != null) {
                reqBuilder.header("If-None-Match", etag)
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
                when (response.code) {
                    304 -> FetchOutcome.NotModified
                    in 200..299 -> {
                        val body = response.body.string()
                        val newEtag = response.header("ETag")
                        FetchOutcome.Content(body, newEtag)
                    }
                    else -> FetchOutcome.Error("HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            FetchOutcome.Error(e.message ?: e.toString())
        }
    }
}

@Serializable
data class ListState(
    val id: String,
    val etag: String?,
    val fetchedAt: Long,
    val expiresAt: Long,
    val ruleLines: Int,
    val bytes: Long,
    val error: String?
)

class FilterListUpdater(
    private val dir: File,
    private val fetcher: ListFetcher,
    private val clock: () -> Long,
    private val log: (String) -> Unit
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun states(): Map<String, ListState> {
        val stateFile = File(dir, "state.json")
        if (!stateFile.exists()) return emptyMap()
        return try {
            val content = stateFile.readText()
            json.decodeFromString<Map<String, ListState>>(content)
        } catch (e: Exception) {
            log("Failed to read state.json: ${e.message}")
            emptyMap()
        }
    }

    private fun saveStates(states: Map<String, ListState>) {
        val stateFile = File(dir, "state.json")
        val content = json.encodeToString(states)
        writeAtomically(stateFile, content.toByteArray(Charsets.UTF_8))
    }

    private fun writeAtomically(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.${UUID.randomUUID()}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
    }

    /**
     * Fetch every enabled list that is missing or expired (or all when force), try catalog URLs in order,
     * store dir/lists/<id>.txt, then rebuild dir/engine.bin from enabled lists + customRules.
     * Returns the new engine (or the existing one when nothing changed). Never deletes a good list file on fetch error.
     */
    suspend fun update(
        enabled: Set<String>,
        customRules: String,
        force: Boolean,
        /** Called with (lists handled, lists enabled) before each list and once more when all are handled. */
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): FilterEngine = withContext(Dispatchers.IO) {
        val currentStates = states().toMutableMap()
        var anyChanged = false
        val now = clock()

        for ((index, id) in enabled.withIndex()) {
            onProgress(index, enabled.size)
            val listDef = FilterListCatalog.byId(id)
            if (listDef == null) {
                log("Unknown list id in enabled set: $id")
                continue
            }

            val listFile = File(dir, "lists/$id.txt")
            val state = currentStates[id]
            val isExpired = state == null || now >= state.expiresAt
            val isMissing = !listFile.exists()
            val needsFetch = force || isMissing || isExpired

            if (!needsFetch) continue

            var fetchSuccess = false
            var lastError: String? = null

            for (url in listDef.urls) {
                log("Fetching $id from $url")
                val outcome = fetcher.fetch(url, if (force) null else state?.etag)
                when (outcome) {
                    is FetchOutcome.Content -> {
                        writeAtomically(listFile, outcome.text.toByteArray(Charsets.UTF_8))
                        val meta = FilterListMeta.parse(outcome.text)
                        val duration = (meta.expires ?: 4.days).coerceIn(12.hours, 14.days)
                        val expAt = now + duration.inWholeMilliseconds
                        val lines = outcome.text.lineSequence().count { line ->
                            val t = line.trim()
                            t.isNotEmpty() && !t.startsWith("!") && !t.startsWith("[") && t != "#" && !t.startsWith("# ")
                        }
                        val bytes = outcome.text.toByteArray(Charsets.UTF_8).size.toLong()

                        currentStates[id] = ListState(
                            id = id,
                            etag = outcome.etag,
                            fetchedAt = now,
                            expiresAt = expAt,
                            ruleLines = lines,
                            bytes = bytes,
                            error = null
                        )
                        anyChanged = true
                        fetchSuccess = true
                        break
                    }
                    is FetchOutcome.NotModified -> {
                        val meta = if (listFile.exists()) FilterListMeta.parse(listFile.readText()) else FilterListMeta(null, null)
                        val duration = (meta.expires ?: 4.days).coerceIn(12.hours, 14.days)
                        val expAt = now + duration.inWholeMilliseconds
                        val existingState = state ?: ListState(id, null, now, expAt, 0, 0, null)
                        currentStates[id] = existingState.copy(
                            fetchedAt = now,
                            expiresAt = expAt,
                            error = null
                        )
                        fetchSuccess = true
                        break
                    }
                    is FetchOutcome.Error -> {
                        lastError = outcome.message
                        log("Fetch error for $id at $url: ${outcome.message}")
                    }
                }
            }

            if (!fetchSuccess && lastError != null) {
                log("All fetch attempts failed for list $id: $lastError")
                if (state != null) {
                    currentStates[id] = state.copy(error = lastError)
                } else {
                    currentStates[id] = ListState(id, null, now, 0L, 0, 0L, lastError)
                }
            }
        }

        saveStates(currentStates)

        val engineFile = File(dir, "engine.bin")
        if (anyChanged || force || !engineFile.exists()) {
            rebuild(enabled, customRules)
        } else {
            loadEngine() ?: rebuild(enabled, customRules)
        }
    }

    /** Rebuild from local files only (e.g. after toggling a list or editing custom rules). */
    suspend fun rebuild(
        enabled: Set<String>,
        customRules: String
    ): FilterEngine = withContext(Dispatchers.IO) {
        val builder = FilterEngineBuilder()
        for (id in enabled) {
            val listFile = File(dir, "lists/$id.txt")
            if (listFile.exists()) {
                builder.addList(id, listFile.readText())
            } else {
                log("Enabled list $id missing on local disk at ${listFile.path}")
            }
        }
        if (customRules.isNotBlank()) {
            builder.addCustomRules(customRules)
        }
        val engine = builder.build()

        val engineFile = File(dir, "engine.bin")
        engineFile.parentFile?.mkdirs()
        val temp = File(engineFile.parentFile, "${engineFile.name}.${UUID.randomUUID()}.tmp")
        FileOutputStream(temp).use { out ->
            engine.writeTo(out)
        }
        if (!temp.renameTo(engineFile)) {
            engineFile.delete()
            if (!temp.renameTo(engineFile)) {
                temp.copyTo(engineFile, overwrite = true)
                temp.delete()
            }
        }
        engine
    }

    fun loadEngine(): FilterEngine? {
        val engineFile = File(dir, "engine.bin")
        if (!engineFile.exists()) return null
        return try {
            engineFile.inputStream().use { FilterEngine.readFrom(it) }
        } catch (e: Exception) {
            log("Failed to load engine from $engineFile: ${e.message}")
            null
        }
    }
}
