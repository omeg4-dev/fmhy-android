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
)

internal data class ParsedLink(
    val name: String,
    val url: String,
    val tier: Int,
    val description: String,
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
            val state = loadState()
            val now = clock()

            if (!force && state.lastCheck > 0L && (now - state.lastCheck < 3600L)) {
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
                val inserted = applyCommits(commits)
                saveState(TrackerState(etag = newEtag, lastCheck = now, lastSuccess = now))
                log("TrackerSync: updated ($inserted rows inserted)")
                return@withContext TrackerResult.Updated(inserted)
            }
        } catch (e: Throwable) {
            log("TrackerSync: sync error: ${e.message}")
            return@withContext TrackerResult.Failed(e.message ?: "Unknown error")
        }
    }

    val lastUnknownSources = mutableSetOf<String>()

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

        // 3. Count before
        val countBefore = conn.prepare("SELECT count(*) FROM changes").use { stmt ->
            if (stmt.step()) stmt.getLong(0) else 0L
        }

        var nextId = conn.prepare("SELECT COALESCE(MAX(id), 0) FROM changes").use { stmt ->
            if (stmt.step()) stmt.getLong(0) else 0L
        }

        var linesWithoutLinkCount = 0
        var unknownSourceCount = 0

        val sqlInsert = "INSERT OR IGNORE INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        conn.prepare(sqlInsert).use { stmt ->
            // Process commits in chronological order (oldest to newest)
            val sortedCommits = commits.sortedBy { it.date }

            for (commit in sortedCommits) {
                val commitTs = try {
                    Instant.parse(commit.date).epochSecond
                } catch (_: Exception) {
                    0L
                }
                val sha = commit.sha

                // Added items
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
                    val entryId = normUrlToEntryId[normUrl]
                        ?: sectionAndNameToEntryId[sectionId to linkInfo.name.trim().lowercase()]
                        ?: ("tracker:" + sha1(normUrl).take(16))

                    when (item.type) {
                        "new" -> {
                            stmt.bindLong(1, ++nextId)
                            stmt.bindLong(2, commitTs)
                            stmt.bindText(3, ChangeKind.ADDED.value)
                            stmt.bindText(4, entryId)
                            stmt.bindText(5, sectionId)
                            stmt.bindText(6, linkInfo.name)
                            stmt.bindText(7, linkInfo.url)
                            stmt.bindText(8, "")
                            stmt.bindText(9, sha)
                            stmt.step()
                            stmt.reset()
                        }
                        "updated" -> {
                            val related = item.relatedLink ?: ""
                            stmt.bindLong(1, ++nextId)
                            stmt.bindLong(2, commitTs)
                            stmt.bindText(3, ChangeKind.EDITED.value)
                            stmt.bindText(4, entryId)
                            stmt.bindText(5, sectionId)
                            stmt.bindText(6, linkInfo.name)
                            stmt.bindText(7, linkInfo.url)
                            stmt.bindText(8, related)
                            stmt.bindText(9, sha)
                            stmt.step()
                            stmt.reset()

                            if (related.isNotEmpty()) {
                                val oldInfo = parseLinkLine(related)
                                val oldStarred = (oldInfo?.tier ?: FmhyParser.detectTierAndKind(related.trimStart().removePrefix("* ").removePrefix("- ")).first) >= 1
                                val newStarred = linkInfo.tier >= 1

                                if (!oldStarred && newStarred) {
                                    stmt.bindLong(1, ++nextId)
                                    stmt.bindLong(2, commitTs)
                                    stmt.bindText(3, ChangeKind.STARRED.value)
                                    stmt.bindText(4, entryId)
                                    stmt.bindText(5, sectionId)
                                    stmt.bindText(6, linkInfo.name)
                                    stmt.bindText(7, linkInfo.url)
                                    stmt.bindText(8, "")
                                    stmt.bindText(9, sha)
                                    stmt.step()
                                    stmt.reset()
                                } else if (oldStarred && !newStarred) {
                                    stmt.bindLong(1, ++nextId)
                                    stmt.bindLong(2, commitTs)
                                    stmt.bindText(3, ChangeKind.UNSTARRED.value)
                                    stmt.bindText(4, entryId)
                                    stmt.bindText(5, sectionId)
                                    stmt.bindText(6, linkInfo.name)
                                    stmt.bindText(7, linkInfo.url)
                                    stmt.bindText(8, "")
                                    stmt.bindText(9, sha)
                                    stmt.step()
                                    stmt.reset()
                                }

                                if (oldInfo != null && oldInfo.url != linkInfo.url) {
                                    stmt.bindLong(1, ++nextId)
                                    stmt.bindLong(2, commitTs)
                                    stmt.bindText(3, ChangeKind.URL_CHANGED.value)
                                    stmt.bindText(4, entryId)
                                    stmt.bindText(5, sectionId)
                                    stmt.bindText(6, linkInfo.name)
                                    stmt.bindText(7, linkInfo.url)
                                    stmt.bindText(8, oldInfo.url)
                                    stmt.bindText(9, sha)
                                    stmt.step()
                                    stmt.reset()
                                }
                            }
                        }
                    }
                }

                // Removed items
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
                    val entryId = normUrlToEntryId[normUrl]
                        ?: sectionAndNameToEntryId[sectionId to linkInfo.name.trim().lowercase()]
                        ?: ("tracker:" + sha1(normUrl).take(16))

                    stmt.bindLong(1, ++nextId)
                    stmt.bindLong(2, commitTs)
                    stmt.bindText(3, ChangeKind.REMOVED.value)
                    stmt.bindText(4, entryId)
                    stmt.bindText(5, sectionId)
                    stmt.bindText(6, linkInfo.name)
                    stmt.bindText(7, linkInfo.url)
                    stmt.bindText(8, "")
                    stmt.bindText(9, sha)
                    stmt.step()
                    stmt.reset()
                }
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
        internal fun parseLinkLine(line: String): ParsedLink? {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return null
            val stripped = trimmed.removePrefix("* ").removePrefix("- ").trim()
            val (tier, kind, rest) = FmhyParser.detectTierAndKind(stripped)
            val (head, desc) = FmhyParser.splitHeadDesc(rest)
            val headLinks = FmhyParser.extractInlineLinks(head)
            val descLinks = if (headLinks.isEmpty()) FmhyParser.extractInlineLinks(desc) else emptyList()
            val firstLink = headLinks.firstOrNull() ?: descLinks.firstOrNull() ?: return null

            val (_, nonLinkDesc) = FmhyParser.splitHeadDesc(desc)
            val description = if (nonLinkDesc.isNotEmpty()) {
                FmhyParser.markdownToPlain(nonLinkDesc).trim()
            } else if (desc.isNotEmpty() && headLinks.isNotEmpty()) {
                FmhyParser.markdownToPlain(desc).trim()
            } else ""

            return ParsedLink(
                name = firstLink.label,
                url = firstLink.url,
                tier = tier,
                description = description,
            )
        }

        private fun sha1(input: String): String {
            val md = MessageDigest.getInstance("SHA-1")
            return md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}
