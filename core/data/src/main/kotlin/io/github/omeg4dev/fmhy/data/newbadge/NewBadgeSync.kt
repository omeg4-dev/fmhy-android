package io.github.omeg4dev.fmhy.data.newbadge

import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.sync.SyncState
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface NewBadgeResult {
    data class Updated(val pages: Int, val marks: Int) : NewBadgeResult
    data object UpToDate : NewBadgeResult
    data object Skipped : NewBadgeResult
    data class Failed(val message: String) : NewBadgeResult
}

@Suppress("InjectDispatcher", "TooGenericExceptionCaught")
class NewBadgeSync(
    private val store: DatasetStore,
    private val dir: File,
    private val badgeStore: NewBadgeStore,
    private val client: OkHttpClient,
    private val baseUrl: String = "https://fmhy.net/",
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val log: (String) -> Unit = {},
    private val maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun sync(force: Boolean = false): NewBadgeResult = withContext(Dispatchers.IO) {
        try {
            val now = clock()
            val state = badgeStore.loadState()

            // 2. Throttle
            if (!force && state.checkedAt > 0L && (now - state.checkedAt) < THROTTLE_SECONDS) {
                return@withContext NewBadgeResult.Skipped
            }

            // 3. Page ids from sections
            val sections = store.sections()
            if (sections.isEmpty()) {
                return@withContext NewBadgeResult.Failed("no sections")
            }
            val sectionIds = sections.map { it.id }.toSet()

            // 4. Read dataset/sync-state.json
            val syncState = loadSyncState()

            // 5. Pick pages to fetch
            val pagesToFetch = mutableListOf<String>()
            val mdShaByPage = mutableMapOf<String, String?>()

            for (sec in sections) {
                val pageId = sec.id
                val currentMdSha = syncState?.files?.get("docs/$pageId.md")?.sha256
                    ?: syncState?.files?.get("docs/$pageId")?.sha256
                    ?: syncState?.files?.get(pageId)?.sha256
                mdShaByPage[pageId] = currentMdSha

                val existingPage = state.pages[pageId]
                val needsFetch = when {
                    existingPage == null -> true
                    existingPage.dirty > 0 -> true
                    currentMdSha != null && existingPage.mdSha != currentMdSha -> true
                    else -> false
                }
                if (needsFetch) {
                    pagesToFetch.add(pageId)
                }
            }

            if (pagesToFetch.isEmpty()) {
                // Update checkedAt so subsequent calls throttle
                badgeStore.save(state.copy(checkedAt = now), validSections = sectionIds)
                return@withContext NewBadgeResult.UpToDate
            }

            // 6. Fetch concurrently
            val fetchClient = client.newBuilder().cache(null).build()
            val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
            val cleanBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            val fetchResults: List<FetchOutcome> = coroutineScope {
                pagesToFetch.map { pageId ->
                    async {
                        semaphore.withPermit {
                            fetchOnePage(
                                client = fetchClient,
                                pageId = pageId,
                                url = cleanBaseUrl + pageId,
                                currentMdSha = mdShaByPage[pageId],
                                existingPage = state.pages[pageId],
                                now = now,
                            )
                        }
                    }
                }.awaitAll()
            }

            // Process results
            val newPages = state.pages.toMutableMap()
            var successCount = 0
            var totalUpdatedMarks = 0

            for (outcome in fetchResults) {
                when (outcome) {
                    is FetchOutcome.Success -> {
                        successCount++
                        newPages[outcome.pageId] = outcome.pageState
                        totalUpdatedMarks += outcome.pageState.marks.size
                    }
                    is FetchOutcome.Failure -> {
                        log("NewBadgeSync: page ${outcome.pageId} failed: ${outcome.reason}")
                        // Keep old marks and old mdSha so it retries next sync
                    }
                }
            }

            if (successCount == 0) {
                // All attempted pages failed -> keep old list intact
                return@withContext NewBadgeResult.Failed("all pages failed")
            }

            val updatedState = state.copy(
                checkedAt = now,
                fetchedAt = now,
                pages = newPages,
            )
            badgeStore.save(updatedState, validSections = sectionIds)

            NewBadgeResult.Updated(pages = successCount, marks = totalUpdatedMarks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("NewBadgeSync: error: ${e.message}")
            NewBadgeResult.Failed(e.message ?: "Unknown error")
        }
    }

    private fun fetchOnePage(
        client: OkHttpClient,
        pageId: String,
        url: String,
        currentMdSha: String?,
        existingPage: PageState?,
        now: Long,
    ): FetchOutcome {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return FetchOutcome.Failure(pageId, "HTTP ${resp.code}")
                }
                val declared = resp.body.contentLength()
                if (declared > maxBodyBytes) {
                    return FetchOutcome.Failure(pageId, "oversize body declared: $declared bytes")
                }
                val bytes = resp.body.byteStream().use { it.readLimited(maxBodyBytes) }
                    ?: return FetchOutcome.Failure(pageId, "oversize body stream")

                val newBodySha = sha256(bytes)
                val html = bytes.toString(Charsets.UTF_8)
                val parseResult = NewBadgeParser.parse(html, baseUrl = url)

                val isBodyChanged = (existingPage == null || newBodySha != existingPage.bodySha)
                val newDirty = if (isBodyChanged) {
                    0
                } else {
                    if (existingPage.dirty > 0) {
                        existingPage.dirty - 1
                    } else if (currentMdSha != null && existingPage.mdSha != currentMdSha) {
                        2
                    } else {
                        0
                    }
                }

                val marks = if (parseResult.marks.isNotEmpty()) {
                    parseResult.marks
                        .map { mark ->
                            BadgeMark(
                                u = NewBadgeKeys.urlKey(mark.url),
                                d = mark.date.toString(),
                            )
                        }
                        .groupBy { it.u }
                        .mapValues { (_, group) -> group.maxBy { it.d } }
                        .values
                        .toList()
                } else {
                    val kept = existingPage?.marks ?: emptyList()
                    log("NewBadgeSync: page $pageId: no new-badge marks, kept ${kept.size}")
                    kept
                }

                val targetMdSha = currentMdSha ?: existingPage?.mdSha ?: ""

                val newPageState = PageState(
                    mdSha = targetMdSha,
                    bodySha = newBodySha,
                    dirty = newDirty,
                    fetchedAt = now,
                    marks = marks,
                )
                FetchOutcome.Success(pageId, newPageState)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FetchOutcome.Failure(pageId, e.message ?: "fetch error")
        }
    }

    private fun loadSyncState(): SyncState? {
        val file = File(dir, "sync-state.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<SyncState>(file.readText())
        } catch (e: Exception) {
            log("NewBadgeSync: failed to load sync-state.json: ${e.message}")
            null
        }
    }

    private fun InputStream.readLimited(limit: Long): ByteArray? {
        val out = ByteArrayOutputStream()
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

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }

    private sealed interface FetchOutcome {
        val pageId: String
        data class Success(override val pageId: String, val pageState: PageState) : FetchOutcome
        data class Failure(override val pageId: String, val reason: String) : FetchOutcome
    }

    companion object {
        const val DEFAULT_MAX_BODY_BYTES = 8L * 1024 * 1024
        const val THROTTLE_SECONDS = 1800L
        const val MAX_CONCURRENT_REQUESTS = 4
        const val USER_AGENT = "Mozilla/5.0 (Android; FMHY) NewBadgeSync"
    }
}
