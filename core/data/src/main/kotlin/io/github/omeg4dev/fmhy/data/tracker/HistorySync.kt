package io.github.omeg4dev.fmhy.data.tracker

import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface HistoryResult {
    data class Updated(val inserted: Int, val generatedAt: Long) : HistoryResult
    data class UpToDate(val generatedAt: Long) : HistoryResult
    data class Failed(val message: String) : HistoryResult
}

@Serializable
private data class FeedPayload(
    val schema: Int = 0,
    val generated_at: Long = 0L,
    val fields: List<String> = emptyList(),
    val changes: List<JsonArray> = emptyList(),
)

@Serializable
private data class HistoryState(
    val etag: String? = null,
    val lastCheck: Long = 0L,
    val generatedAt: Long = 0L,
)

/**
 * Merges the project's own change-history feed (`changes.json.gz`, built from the fmhy/edit git history by
 * the pipeline's `feed` command) into the `changes` table. Rows carry their real commit time, so
 * existing installs get history too and it does not depend on when the app was installed. Rows are
 * identified by (commit_sha, kind, url), so merging the same feed again adds nothing.
 */
class HistorySync(
    private val store: DatasetStore,
    private val dir: File,
    private val client: OkHttpClient,
    private val url: String = DEFAULT_URL,
    private val clock: () -> Long,
    private val log: (String) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val stateFile = File(dir, "history-state.json")

    private fun loadState(): HistoryState = try {
        if (stateFile.exists()) json.decodeFromString<HistoryState>(stateFile.readText()) else HistoryState()
    } catch (e: Exception) {
        log("HistorySync: failed to load state: ${e.message}")
        HistoryState()
    }

    private fun saveState(state: HistoryState) {
        try {
            dir.mkdirs()
            stateFile.writeText(json.encodeToString(state))
        } catch (e: Exception) {
            log("HistorySync: failed to save state: ${e.message}")
        }
    }

    suspend fun sync(force: Boolean = false): HistoryResult = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            val state = loadState()
            val now = clock()
            if (!force && state.lastCheck > 0L && now - state.lastCheck < CHECK_INTERVAL_SEC) {
                return@withContext HistoryResult.UpToDate(state.generatedAt)
            }

            val request = Request.Builder().url(url).apply {
                // Only trust the ETag while we still hold the rows it described.
                if (state.etag != null && state.generatedAt > 0L) header("If-None-Match", state.etag)
            }.build()
            client.newCall(request).execute().use { resp ->
                if (resp.code == 304) {
                    saveState(state.copy(lastCheck = now))
                    return@withContext HistoryResult.UpToDate(state.generatedAt)
                }
                if (!resp.isSuccessful) {
                    return@withContext HistoryResult.Failed("HTTP ${resp.code}")
                }
                val bytes = resp.body.bytes()
                val payload = parse(bytes) ?: return@withContext HistoryResult.Failed("unreadable feed")
                if (payload.schema != SUPPORTED_SCHEMA) {
                    return@withContext HistoryResult.Failed("unsupported feed schema ${payload.schema}")
                }
                val inserted = merge(payload)
                saveState(HistoryState(etag = resp.header("ETag"), lastCheck = now, generatedAt = payload.generated_at))
                log("HistorySync: merged feed from ${payload.generated_at} ($inserted rows inserted)")
                HistoryResult.Updated(inserted, payload.generated_at)
            }
        } catch (e: Throwable) {
            log("HistorySync: error: ${e.message}")
            HistoryResult.Failed(e.message ?: "Unknown error")
        }
    }

    private fun parse(bytes: ByteArray): FeedPayload? = try {
        val stream: InputStream = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(bytes.inputStream())
        } else {
            bytes.inputStream()
        }
        json.decodeFromString<FeedPayload>(stream.bufferedReader().use { it.readText() })
    } catch (e: Exception) {
        log("HistorySync: parse error: ${e.message}")
        null
    }

    private suspend fun merge(payload: FeedPayload): Int = store.withTransaction { conn ->
        val idx = payload.fields.withIndex().associate { it.value to it.index }
        val required = listOf("ts", "kind", "section_id", "name", "url", "detail", "commit_sha")
        if (!idx.keys.containsAll(required)) return@withTransaction 0

        val sections = mutableSetOf<String>()
        conn.prepare("SELECT id FROM sections").use { st -> while (st.step()) sections.add(st.getText(0)) }
        val byNormUrl = mutableMapOf<String, String>()
        val bySectionName = mutableMapOf<Pair<String, String>, String>()
        conn.prepare("SELECT id, section_id, name, url FROM entries").use { st ->
            while (st.step()) {
                val id = st.getText(0)
                byNormUrl.putIfAbsent(DatasetStore.normalizeEntryUrl(st.getText(3)), id)
                bySectionName[st.getText(1) to st.getText(2).trim().lowercase()] = id
            }
        }
        val kinds = ChangeKind.entries.map { it.value }.toSet()

        val before = conn.prepare("SELECT count(*) FROM changes").use { if (it.step()) it.getLong(0) else 0L }
        conn.prepare(
            "INSERT OR IGNORE INTO changes (ts, kind, entry_id, section_id, name, url, detail, commit_sha) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        ).use { ins ->
            for (row in payload.changes) {
                fun text(f: String) = (row.getOrNull(idx.getValue(f)) as? JsonPrimitive)?.content ?: ""
                val kind = text("kind")
                val section = text("section_id")
                val url = text("url")
                if (kind !in kinds || section !in sections || url.isEmpty()) continue
                val ts = (row.getOrNull(idx.getValue("ts")) as? JsonPrimitive)?.long ?: continue
                val name = text("name")
                val norm = DatasetStore.normalizeEntryUrl(url)
                val entryId = byNormUrl[norm]
                    ?: bySectionName[section to name.trim().lowercase()]
                    ?: ("feed:" + sha1(norm).take(16))
                ins.bindLong(1, ts)
                ins.bindText(2, kind)
                ins.bindText(3, entryId)
                ins.bindText(4, section)
                ins.bindText(5, name)
                ins.bindText(6, url)
                ins.bindText(7, text("detail"))
                ins.bindText(8, text("commit_sha"))
                ins.step()
                ins.reset()
            }
        }
        val after = conn.prepare("SELECT count(*) FROM changes").use { if (it.step()) it.getLong(0) else 0L }
        (after - before).toInt()
    }

    companion object {
        /** Published by the pipeline's scheduled feed job (GitHub Pages of this repository). */
        const val DEFAULT_URL = "https://omeg4-dev.github.io/fmhy-android/history/changes.json.gz"
        const val SUPPORTED_SCHEMA = 1
        const val CHECK_INTERVAL_SEC = 3600L

        private fun sha1(input: String): String =
            MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
