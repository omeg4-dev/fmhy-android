package io.github.omeg4dev.fmhy.data.tracker

import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.parse.FmhyParser
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface TrackerResult {
    data class Updated(val inserted: Int) : TrackerResult
    data object UpToDate : TrackerResult
    data class Failed(val message: String) : TrackerResult
}

@Serializable
data class TrackerLink(
    val link: String,
    val source: String,
    val type: String,
    val relatedLink: String? = null,
)

@Serializable
data class TrackerCommit(
    val sha: String,
    val date: String,
    val message: String,
    val added: List<TrackerLink> = emptyList(),
    val removed: List<TrackerLink> = emptyList(),
)

@Serializable
private data class TrackerPayload(
    val month: String? = null,
    val generatedAt: String? = null,
    val totalCommits: Int? = null,
    val commits: List<TrackerCommit> = emptyList(),
)

@Serializable
private data class TrackerState(
    val etag: String? = null,
    val lastCheck: Long = 0L,
    val lastSuccess: Long = 0L,
    /** Version of the classification rules that produced the stored rows (see [TrackerSync.RULES_VERSION]). */
    val rulesVersion: Int = 0,
)


class TrackerSync(
    private val store: DatasetStore,
    private val dir: File,
    private val client: OkHttpClient,
    private val url: String = "https://fmhy-tracker.pages.dev/monthly_commits.json",
    private val clock: () -> Long,
    private val log: (String) -> Unit,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val stateFile = File(dir, "tracker-state.json")

    private fun loadState(): TrackerState {
        if (!stateFile.exists()) return TrackerState()
        return try {
            json.decodeFromString<TrackerState>(stateFile.readText())
        } catch (e: Exception) {
            log("TrackerSync: failed to load state: ${e.message}")
            TrackerState()
        }
    }

    private fun saveState(state: TrackerState) {
        try {
            dir.mkdirs()
            stateFile.writeText(json.encodeToString(state))
        } catch (e: Exception) {
            log("TrackerSync: failed to save state: ${e.message}")
        }
    }

    internal fun parse(jsonString: String): List<TrackerCommit> {
        return try {
            json.decodeFromString<TrackerPayload>(jsonString).commits
        } catch (_: Exception) {
            // Also attempt direct list decoding in case payload is array of commits
            try {
                json.decodeFromString<List<TrackerCommit>>(jsonString)
            } catch (e: Exception) {
                log("TrackerSync: json parse error: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun sync(force: Boolean = false): TrackerResult = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            val loaded = loadState()
            val now = clock()
            // Rows from older rules (e.g. an EDITED row for every tracker update) are replaced once.
            val reclassify = loaded.rulesVersion < RULES_VERSION
            val state = if (reclassify) loaded.copy(etag = null) else loaded

            if (!force && !reclassify && state.lastCheck > 0L && (now - state.lastCheck < 3600L)) {
                log("TrackerSync: skipped (checked ${now - state.lastCheck} s ago)")
                return@withContext TrackerResult.UpToDate
            }

            val reqBuilder = Request.Builder().url(url)
            if (state.etag != null) {
                reqBuilder.header("If-None-Match", state.etag)
            }
            val response = client.newCall(reqBuilder.build()).execute()
            response.use { resp ->
                if (resp.code == 304) {
                    saveState(state.copy(lastCheck = now))
                    log("TrackerSync: 304 Not Modified")
                    return@withContext TrackerResult.UpToDate
                }
                if (!resp.isSuccessful) {
                    saveState(state.copy(lastCheck = now))
                    log("TrackerSync: HTTP error ${resp.code}")
                    return@withContext TrackerResult.Failed("HTTP ${resp.code}")
                }
                val body = resp.body.string()
                val newEtag = resp.header("ETag") ?: state.etag
                val commits = parse(body)
                if (reclassify) dropOutdatedRows()
                val inserted = applyCommits(commits)
                saveState(TrackerState(etag = newEtag, lastCheck = now, lastSuccess = now, rulesVersion = RULES_VERSION))
                log("TrackerSync: updated ($inserted rows inserted)")
                return@withContext TrackerResult.Updated(inserted)
            }
        } catch (e: Throwable) {
            log("TrackerSync: sync error: ${e.message}")
            return@withContext TrackerResult.Failed(e.message ?: "Unknown error")
        }
    }

    val lastUnknownSources = mutableSetOf<String>()

    /** Only the tracker ever wrote EDITED rows (the bundled history has none), so they can all go; the
     * tracker month re-inserts them with the current rules. */
    private suspend fun dropOutdatedRows() = store.withTransaction { conn ->
        conn.prepare("DELETE FROM changes WHERE kind = 'edited'").use { it.step() }
        // 0.5.0's own sync wrote rows without a commit id (a link swap or move became remove + add).
        conn.prepare("DELETE FROM changes WHERE commit_sha = ''").use { it.step() }
    }

    internal suspend fun applyCommits(commits: List<TrackerCommit>): Int = store.withTransaction { conn ->
        lastUnknownSources.clear()
        if (commits.isEmpty()) return@withTransaction 0

        // 1. Build sections map
        val sectionMap = mutableMapOf<String, String>()
        conn.prepare("SELECT id, file FROM sections").use { stmt ->
            while (stmt.step()) {
                val sid = stmt.getText(0)
                val file = stmt.getText(1)
                sectionMap[sid] = sid
                sectionMap[sid.lowercase()] = sid
                sectionMap[file] = sid
                sectionMap[file.removeSuffix(".md")] = sid
                sectionMap["docs/$file"] = sid
            }
        }

        // 2. Build entry lookups
        val normUrlToEntryId = mutableMapOf<String, String>()
        val sectionAndNameToEntryId = mutableMapOf<Pair<String, String>, String>()
        conn.prepare("SELECT id, section_id, name, url FROM entries").use { stmt ->
            while (stmt.step()) {
                val eid = stmt.getText(0)
                val secId = stmt.getText(1)
                val name = stmt.getText(2)
                val u = stmt.getText(3)
                val normU = DatasetStore.normalizeEntryUrl(u)
                if (!normUrlToEntryId.containsKey(normU)) {
                    normUrlToEntryId[normU] = eid
                }
                sectionAndNameToEntryId[secId to name.trim().lowercase()] = eid
            }
        }

        fun resolveEntryId(normUrl: String, sectionId: String, name: String): String {
            return normUrlToEntryId[normUrl]
                ?: sectionAndNameToEntryId[sectionId to name.trim().lowercase()]
                ?: ("tracker:" + sha1(normUrl).take(16))
        }

        // 3. Count before
        val countBefore = conn.prepare("SELECT count(*) FROM changes").use { stmt ->
            if (stmt.step()) stmt.getLong(0) else 0L
        }

        var nextId = conn.prepare("SELECT COALESCE(MAX(id), 0) FROM changes").use { stmt ->
            if (stmt.step()) stmt.getLong(0) else 0L
        }

        var linesWithoutLinkCount = 0
        var unknownSourceCount = 0

        val sortedCommits = commits.sortedBy { it.date }

        data class ParsedItem(
            val commit: TrackerCommit,
            val commitTs: Long,
            val item: TrackerLink,
            val sectionId: String,
            val linkInfo: ParsedLink,
            val normUrl: String,
            val entryId: String,
        )

        val newItems = mutableListOf<ParsedItem>()
        val removedItems = mutableListOf<ParsedItem>()
        val updatedItems = mutableListOf<ParsedItem>()

        for (commit in sortedCommits) {
            val commitTs = try {
                Instant.parse(commit.date).epochSecond
            } catch (_: Exception) {
                0L
            }

            for (item in commit.added) {
                val sectionId = sectionMap[item.source]
                if (sectionId == null) {
                    unknownSourceCount++
                    lastUnknownSources.add(item.source)
                    continue
                }
                val linkInfo = parseLinkLine(item.link)
                if (linkInfo == null) {
                    linesWithoutLinkCount++
                    continue
                }
                val normUrl = DatasetStore.normalizeEntryUrl(linkInfo.url)
                val entryId = resolveEntryId(normUrl, sectionId, linkInfo.name)
                val parsed = ParsedItem(commit, commitTs, item, sectionId, linkInfo, normUrl, entryId)
                when (item.type) {
                    "new" -> newItems.add(parsed)
                    "updated" -> updatedItems.add(parsed)
                }
            }

            for (item in commit.removed) {
                val sectionId = sectionMap[item.source]
                if (sectionId == null) {
                    unknownSourceCount++
                    lastUnknownSources.add(item.source)
                    continue
                }
                val linkInfo = parseLinkLine(item.link)
                if (linkInfo == null) {
                    linesWithoutLinkCount++
                    continue
                }
                val normUrl = DatasetStore.normalizeEntryUrl(linkInfo.url)
                val entryId = resolveEntryId(normUrl, sectionId, linkInfo.name)
                val parsed = ParsedItem(commit, commitTs, item, sectionId, linkInfo, normUrl, entryId)
                removedItems.add(parsed)
            }
        }

        // Pair removed items with new items across all commits of the fetched file
        data class ItemPair(
            val rem: ParsedItem,
            val new: ParsedItem,
            val isMove: Boolean,
        )

        val pairedNew = mutableSetOf<Int>()
        val pairedRemoved = mutableSetOf<Int>()
        val pairs = mutableListOf<ItemPair>()

        for ((remIdx, rem) in removedItems.withIndex()) {
            val newIdx = newItems.indices.firstOrNull { idx ->
                idx !in pairedNew &&
                newItems[idx].normUrl == rem.normUrl &&
                newItems[idx].commit.date >= rem.commit.date
            }
            if (newIdx != null) {
                pairedRemoved.add(remIdx)
                pairedNew.add(newIdx)
                val nw = newItems[newIdx]
                val isMove = rem.item.source != nw.item.source
                pairs.add(ItemPair(rem, nw, isMove))
            }
        }

        // Same page, same name, new address: a link swap done as remove + add (e.g. CMVTV).
        for ((remIdx, rem) in removedItems.withIndex()) {
            if (remIdx in pairedRemoved) continue
            val remName = ChangeClassifier.collapseWhitespace(rem.linkInfo.name).lowercase()
            val newIdx = newItems.indices.firstOrNull { idx ->
                idx !in pairedNew &&
                newItems[idx].item.source == rem.item.source &&
                ChangeClassifier.collapseWhitespace(newItems[idx].linkInfo.name).lowercase() == remName &&
                newItems[idx].commit.date >= rem.commit.date
            }
            if (newIdx != null) {
                pairedRemoved.add(remIdx)
                pairedNew.add(newIdx)
                pairs.add(ItemPair(rem, newItems[newIdx], isMove = false))
            }
        }

        // Earlier syncs may already have inserted the plain added/removed rows:
        // delete those rows for both shas in the same transaction
        if (pairs.isNotEmpty()) {
            val sqlDelete = "DELETE FROM changes WHERE commit_sha = ? AND kind IN ('added', 'removed') AND url = ?"
            conn.prepare(sqlDelete).use { delStmt ->
                for (pair in pairs) {
                    delStmt.bindText(1, pair.rem.commit.sha)
                    delStmt.bindText(2, pair.rem.linkInfo.url)
                    delStmt.step()
                    delStmt.reset()

                    delStmt.bindText(1, pair.new.commit.sha)
                    delStmt.bindText(2, pair.new.linkInfo.url)
                    delStmt.step()
                    delStmt.reset()
                }
            }
        }

        data class ChangeToInsert(
            val ts: Long,
            val kind: ChangeKind,
            val entryId: String,
            val sectionId: String,
            val name: String,
            val url: String,
            val detail: String,
            val sha: String,
        )

        val changesToInsert = mutableListOf<ChangeToInsert>()

        // 1. Pairs (moves and same-source re-adds)
        for (pair in pairs) {
            if (pair.isMove) {
                // Emit ONE moved row (ts / commit_sha of the new commit, section_id = new section, detail = old section id)
                changesToInsert.add(
                    ChangeToInsert(
                        ts = pair.new.commitTs,
                        kind = ChangeKind.MOVED,
                        entryId = pair.new.entryId,
                        sectionId = pair.new.sectionId,
                        name = pair.new.linkInfo.name,
                        url = pair.new.linkInfo.url,
                        detail = pair.rem.sectionId,
                        sha = pair.new.commit.sha,
                    )
                )
                // If the lines also differ, additionally emit the classifier kinds for (removed line -> new line)
                if (pair.rem.item.link != pair.new.item.link) {
                    val diff = ChangeClassifier.classify(pair.rem.item.link, pair.new.item.link)
                    for (k in diff.kinds) {
                        val detail = when (k) {
                            ChangeKind.RENAMED -> diff.oldName ?: pair.rem.linkInfo.name
                            ChangeKind.URL_CHANGED -> diff.oldUrl ?: pair.rem.linkInfo.url
                            ChangeKind.EDITED -> ChangeClassifier.editedDetail(pair.rem.item.link, pair.new.item.link)
                            else -> ""
                        }
                        changesToInsert.add(
                            ChangeToInsert(
                                ts = pair.new.commitTs,
                                kind = k,
                                entryId = pair.new.entryId,
                                sectionId = pair.new.sectionId,
                                name = pair.new.linkInfo.name,
                                url = pair.new.linkInfo.url,
                                detail = detail,
                                sha = pair.new.commit.sha,
                            )
                        )
                    }
                }
            } else {
                // Same-source re-adds: if lines identical, emit nothing (reorder);
                // otherwise emit classifier kinds for (removed line -> new line)
                if (pair.rem.item.link != pair.new.item.link) {
                    val diff = ChangeClassifier.classify(pair.rem.item.link, pair.new.item.link)
                    for (k in diff.kinds) {
                        val detail = when (k) {
                            ChangeKind.RENAMED -> diff.oldName ?: pair.rem.linkInfo.name
                            ChangeKind.URL_CHANGED -> diff.oldUrl ?: pair.rem.linkInfo.url
                            ChangeKind.EDITED -> ChangeClassifier.editedDetail(pair.rem.item.link, pair.new.item.link)
                            else -> ""
                        }
                        changesToInsert.add(
                            ChangeToInsert(
                                ts = pair.new.commitTs,
                                kind = k,
                                entryId = pair.new.entryId,
                                sectionId = pair.new.sectionId,
                                name = pair.new.linkInfo.name,
                                url = pair.new.linkInfo.url,
                                detail = detail,
                                sha = pair.new.commit.sha,
                            )
                        )
                    }
                }
            }
        }

        // 2. Unpaired new items
        for ((idx, nw) in newItems.withIndex()) {
            if (idx !in pairedNew) {
                changesToInsert.add(
                    ChangeToInsert(
                        ts = nw.commitTs,
                        kind = ChangeKind.ADDED,
                        entryId = nw.entryId,
                        sectionId = nw.sectionId,
                        name = nw.linkInfo.name,
                        url = nw.linkInfo.url,
                        detail = "",
                        sha = nw.commit.sha,
                    )
                )
            }
        }

        // 3. Updated items: one row per kind from ChangeClassifier
        for (upd in updatedItems) {
            val related = upd.item.relatedLink ?: ""
            val diff = ChangeClassifier.classify(related, upd.item.link)
            for (k in diff.kinds) {
                val detail = when (k) {
                    ChangeKind.RENAMED -> diff.oldName ?: ""
                    ChangeKind.URL_CHANGED -> diff.oldUrl ?: ""
                    ChangeKind.EDITED -> ChangeClassifier.editedDetail(related, upd.item.link)
                    else -> ""
                }
                changesToInsert.add(
                    ChangeToInsert(
                        ts = upd.commitTs,
                        kind = k,
                        entryId = upd.entryId,
                        sectionId = upd.sectionId,
                        name = upd.linkInfo.name,
                        url = upd.linkInfo.url,
                        detail = detail,
                        sha = upd.commit.sha,
                    )
                )
            }
        }

        // 4. Unpaired removed items
        for ((idx, rem) in removedItems.withIndex()) {
            if (idx !in pairedRemoved) {
                changesToInsert.add(
                    ChangeToInsert(
                        ts = rem.commitTs,
                        kind = ChangeKind.REMOVED,
                        entryId = rem.entryId,
                        sectionId = rem.sectionId,
                        name = rem.linkInfo.name,
                        url = rem.linkInfo.url,
                        detail = "",
                        sha = rem.commit.sha,
                    )
                )
            }
        }

        val sqlInsert = "INSERT OR IGNORE INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        conn.prepare(sqlInsert).use { stmt ->
            for (c in changesToInsert.sortedBy { it.ts }) {
                stmt.bindLong(1, ++nextId)
                stmt.bindLong(2, c.ts)
                stmt.bindText(3, c.kind.value)
                stmt.bindText(4, c.entryId)
                stmt.bindText(5, c.sectionId)
                stmt.bindText(6, c.name)
                stmt.bindText(7, c.url)
                stmt.bindText(8, c.detail)
                stmt.bindText(9, c.sha)
                stmt.step()
                stmt.reset()
            }
        }

        if (linesWithoutLinkCount > 0) {
            log("TrackerSync: skipped $linesWithoutLinkCount lines without links")
        }
        if (unknownSourceCount > 0) {
            log("TrackerSync: skipped $unknownSourceCount items with unknown sources: $lastUnknownSources")
        }

        val countAfter = conn.prepare("SELECT count(*) FROM changes").use { stmt ->
            if (stmt.step()) stmt.getLong(0) else 0L
        }

        (countAfter - countBefore).toInt()
    }

    companion object {
        /** Bump when the classification of tracker items changes, so stored rows get rebuilt. */
        const val RULES_VERSION = 5

        internal fun parseLinkLine(line: String): ParsedLink? =
            ChangeClassifier.parseLinkLine(line)

        private fun sha1(input: String): String {
            val md = MessageDigest.getInstance("SHA-1")
            return md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}
