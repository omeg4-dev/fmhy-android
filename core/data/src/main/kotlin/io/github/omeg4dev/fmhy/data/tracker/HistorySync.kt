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
    val days: Int = 0,
    val fields: List<String> = emptyList(),
    val changes: List<JsonArray> = emptyList(),
)

@Serializable
private data class HistoryState(
    val etag: String? = null,
    val lastCheck: Long = 0L,
    val generatedAt: Long = 0L,
    /** Which reconcile rules the rows in the store have been through; older state must re-merge in full. */
    val reconciled: Int = 0,
)

/**
 * Merges the project's own change-history feed (`changes.json.gz`, built from the fmhy/edit git history by
 * the pipeline's `feed` command) into the `changes` table. Rows carry their real commit time, so
 * existing installs get history too and it does not depend on when the app was installed. Rows are
 * identified by (commit_sha, kind, url), so merging the same feed again adds nothing.
 *
 * Inside the window the feed covers (its `days`, up to its newest commit) it is the source of truth for
 * everything except `edited` rows, which only the tracker can produce: rows from the bundled snapshot or the
 * tracker that the feed does not contain are dropped, and so are sync-stamped rows the feed now explains.
 * Without that, an install whose bundled snapshot was built from a stale history database counts changes
 * that never happened, and a change seen by the wiki sync counts again once the feed catches up.
 */
class HistorySync(
    private val store: DatasetStore,
    private val dir: File,
    private val client: OkHttpClient,
    private val url: String = DEFAULT_URL,
    private val clock: () -> Long,
    private val log: (String) -> Unit,
    private val maxBodyBytes: Long = MAX_BODY_BYTES,
    private val maxJsonBytes: Long = MAX_JSON_BYTES,
    private val reconcileMinRows: Int = RECONCILE_MIN_ROWS,
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
            if (!force && state.lastCheck > 0L && state.reconciled == RECONCILE_VERSION &&
                now - state.lastCheck < CHECK_INTERVAL_SEC
            ) {
                return@withContext HistoryResult.UpToDate(state.generatedAt)
            }

            val request = Request.Builder().url(url).apply {
                // Only trust the ETag while we still hold the rows it described.
                // A state written before the current reconcile rules must fetch the body again so the rules run.
                if (state.etag != null && state.generatedAt > 0L && state.reconciled == RECONCILE_VERSION) {
                    header("If-None-Match", state.etag)
                }
            }.build()
            client.newCall(request).execute().use { resp ->
                if (resp.code == 304) {
                    saveState(state.copy(lastCheck = now))
                    return@withContext HistoryResult.UpToDate(state.generatedAt)
                }
                if (!resp.isSuccessful) {
                    return@withContext HistoryResult.Failed("HTTP ${resp.code}")
                }
                val declared = resp.body.contentLength()
                if (declared > maxBodyBytes) return@withContext HistoryResult.Failed("feed too large ($declared bytes)")
                val bytes = resp.body.byteStream().readLimited(maxBodyBytes)
                    ?: return@withContext HistoryResult.Failed("feed too large")
                val payload = parse(bytes) ?: return@withContext HistoryResult.Failed("unreadable feed")
                if (payload.schema != SUPPORTED_SCHEMA) {
                    return@withContext HistoryResult.Failed("unsupported feed schema ${payload.schema}")
                }
                // A timestamp from the future would pass for "fresh" indefinitely; allow a little clock skew only.
                if (payload.generated_at > now + MAX_CLOCK_SKEW_SEC) {
                    return@withContext HistoryResult.Failed("feed has a future timestamp (${payload.generated_at})")
                }
                if (payload.changes.size > MAX_ROWS) {
                    return@withContext HistoryResult.Failed("feed has too many rows (${payload.changes.size})")
                }
                val inserted = merge(payload)
                saveState(
                    HistoryState(
                        etag = resp.header("ETag"),
                        lastCheck = now,
                        generatedAt = payload.generated_at,
                        reconciled = RECONCILE_VERSION,
                    ),
                )
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
        // Bounded: a few KB of gzip must not be able to expand to gigabytes in memory.
        val raw = stream.use { it.readLimited(maxJsonBytes) }
        if (raw == null) {
            log("HistorySync: feed expands beyond $maxJsonBytes bytes")
            null
        } else {
            json.decodeFromString<FeedPayload>(raw.toString(Charsets.UTF_8))
        }
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

        // What the feed says happened, for the reconcile step below.
        val feedKeys = HashSet<String>(payload.changes.size * 2)
        val feedKindUrls = HashSet<Pair<String, String>>()
        val feedReplacedUrls = HashSet<String>() // old address of a url_changed row
        var newestTs = Long.MIN_VALUE

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
                if (kind !in kinds || url.isEmpty()) continue
                val ts = (row.getOrNull(idx.getValue("ts")) as? JsonPrimitive)?.long ?: continue
                val norm = DatasetStore.normalizeEntryUrl(url)
                feedKeys.add(changeKey(text("commit_sha"), kind, url))
                feedKindUrls.add(kind to norm)
                if (kind == ChangeKind.URL_CHANGED.value && text("detail").isNotEmpty()) {
                    feedReplacedUrls.add(DatasetStore.normalizeEntryUrl(text("detail")))
                }
                if (ts > newestTs) newestTs = ts
                if (section !in sections) continue
                val name = text("name")
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
        val dropped = reconcile(conn, payload, feedKeys, feedKindUrls, feedReplacedUrls, newestTs)
        if (dropped > 0) log("HistorySync: dropped $dropped rows the feed does not confirm")
        (after - before).toInt()
    }

    /**
     * Drops rows inside the feed's window that the feed contradicts or already explains. `edited` rows are
     * never touched (the feed has none), and a feed too small to trust changes nothing.
     */
    private fun reconcile(
        conn: androidx.sqlite.SQLiteConnection,
        payload: FeedPayload,
        feedKeys: Set<String>,
        feedKindUrls: Set<Pair<String, String>>,
        feedReplacedUrls: Set<String>,
        newestTs: Long,
    ): Int {
        if (payload.days <= 0 || payload.changes.size < reconcileMinRows || newestTs == Long.MIN_VALUE) return 0
        val since = payload.generated_at - payload.days * 86_400L
        val stale = ArrayList<Long>()
        conn.prepare("SELECT id, ts, kind, url, commit_sha FROM changes WHERE kind != ? AND ts >= ?").use { st ->
            st.bindText(1, ChangeKind.EDITED.value)
            st.bindLong(2, since)
            while (st.step()) {
                val ts = st.getLong(1)
                val kind = st.getText(2)
                val url = st.getText(3)
                val sha = st.getText(4)
                val isStale = if (sha.startsWith(SYNC_SHA_PREFIX)) {
                    // Seen by the wiki sync, stamped with the sync time: redundant once the feed has the change.
                    val norm = DatasetStore.normalizeEntryUrl(url)
                    (kind to norm) in feedKindUrls ||
                        (kind == ChangeKind.REMOVED.value && norm in feedReplacedUrls)
                } else {
                    ts <= newestTs && changeKey(sha, kind, url) !in feedKeys
                }
                if (isStale) stale.add(st.getLong(0))
            }
        }
        if (stale.isEmpty()) return 0
        conn.prepare("DELETE FROM changes WHERE id = ?").use { del ->
            for (id in stale) {
                del.bindLong(1, id)
                del.step()
                del.reset()
            }
        }
        return stale.size
    }

    private fun changeKey(sha: String, kind: String, url: String) = "$sha\u0000$kind\u0000$url"

    /** Reads at most [limit] bytes; null when the stream has more. */
    private fun InputStream.readLimited(limit: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > limit) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        /** The real feed is ~150 KB compressed / ~1.5 MB of JSON; these leave room to grow, not to abuse. */
        const val MAX_BODY_BYTES = 10L * 1024 * 1024
        const val MAX_JSON_BYTES = 50L * 1024 * 1024
        const val MAX_ROWS = 200_000
        const val MAX_CLOCK_SKEW_SEC = 3600L
        /** Published by the pipeline's scheduled feed job (GitHub Pages of this repository). */
        const val DEFAULT_URL = "https://omeg4-dev.github.io/fmhy-android/history/changes.json.gz"
        const val SUPPORTED_SCHEMA = 1
        const val CHECK_INTERVAL_SEC = 3600L
        /** Bump when the reconcile rules change so installs re-merge the feed once instead of waiting for a new one. */
        const val RECONCILE_VERSION = 1
        /** A feed with fewer rows than this is never allowed to delete anything. */
        const val RECONCILE_MIN_ROWS = 50
        /** commit_sha prefix FmhySync stamps on the changes it detects itself. */
        const val SYNC_SHA_PREFIX = "sync:"

        private fun sha1(input: String): String =
            MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
