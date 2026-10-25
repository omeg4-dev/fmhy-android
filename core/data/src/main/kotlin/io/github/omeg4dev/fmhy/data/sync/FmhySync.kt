package io.github.omeg4dev.fmhy.data.sync

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.omeg4dev.fmhy.data.DATASET_SCHEMA
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.parse.Base64Map
import io.github.omeg4dev.fmhy.data.parse.FmhyParser
import io.github.omeg4dev.fmhy.data.parse.ParsedSection
import io.github.omeg4dev.fmhy.data.parse.SectionInfo
import io.github.omeg4dev.fmhy.data.parse.Sections
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

data class Sources(
    val primary: String = "https://raw.githubusercontent.com/fmhy/edit/main/",
    val fallback: String = "https://cdn.jsdelivr.net/gh/fmhy/edit@main/",
    val rentry: String = "https://rentry.co/FMHYB64",
    val unsafeDomains: String = "https://raw.githubusercontent.com/fmhy/FMHYFilterlist/main/filterlist-domains.txt",
    val unsafeReasons: String = "https://raw.githubusercontent.com/fmhy/FMHYFilterlist/main/filterlists-reasons.json",
    val siteList: String = "https://raw.githubusercontent.com/fmhy/FMHYFilterlist/main/sitelist.txt",
    val siteListPlus: String = "https://raw.githubusercontent.com/fmhy/FMHYFilterlist/main/sitelist-plus.txt",
    val fmhySites: String = "https://raw.githubusercontent.com/fmhy/FMHY-SafeGuard/main/fmhy-filterlist.txt",
)

open class FmhySync(
    internal val store: DatasetStore,
    private val dir: File,
    internal val client: OkHttpClient,
    internal val sources: Sources = Sources(),
    private val clock: () -> Long,
    private val log: (String) -> Unit,
    private val minIntervalSec: Long = 600L,
    private val parser: (SectionInfo, String, Base64Map) -> ParsedSection = FmhyParser::parseSection,
) {
    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val stateFile = File(dir, "sync-state.json")
    val rawDir = File(dir, "raw")

    open fun loadState(): SyncState {
        if (!stateFile.exists()) return SyncState()
        return try {
            json.decodeFromString<SyncState>(stateFile.readText())
        } catch (e: Exception) {
            log("FmhySync: failed to load state: ${e.message}")
            SyncState()
        }
    }

    private fun saveState(state: SyncState) {
        try {
            dir.mkdirs()
            stateFile.writeText(json.encodeToString(state))
        } catch (e: Exception) {
            log("FmhySync: failed to save state: ${e.message}")
        }
    }

    suspend fun sync(force: Boolean = false, onProgress: (SyncProgress) -> Unit = {}): SyncResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            dir.mkdirs()
            rawDir.mkdirs()
            val state = loadState()
            val now = clock()

            if (!force && (now - state.lastCheck < minIntervalSec)) {
                log("FmhySync: skipped (checked ${now - state.lastCheck} s ago)")
                return@withContext SyncResult.Skipped
            }

            if (!store.dbFile.exists()) {
                DatasetStore.initDatabase(store.dbFile)
                store.reopen()
            }

            // Ensure Dispatcher can handle parallel requests
            client.dispatcher.maxRequestsPerHost = maxOf(client.dispatcher.maxRequestsPerHost, 16)

            val primarySuccessCount = AtomicInteger(0)
            val fallbackSuccessCount = AtomicInteger(0)

            val constantsPath = "docs/.vitepress/transformer/constants.ts"
            val rentryPath = "rentry"
            val unsafeDomainsPath = "filterlist-domains.txt"
            val unsafeReasonsPath = "filterlists-reasons.json"
            val siteListPath = "sitelist.txt"
            val siteListPlusPath = "sitelist-plus.txt"
            val fmhySitesPath = "fmhy-filterlist.txt"

            // 2. Fetch constants.ts, rentry, unsafe files, and SafeGuard lists in parallel
            val constantsPrimaryUrl = "${sources.primary.trimEnd('/')}/$constantsPath"
            val constantsFallbackUrl = "${sources.fallback.trimEnd('/')}/$constantsPath"

            onProgress(SyncProgress.Checking)

            val fetchResults = fetchControlFiles(
                state,
                constantsPath,
                constantsPrimaryUrl,
                constantsFallbackUrl,
                rentryPath,
                unsafeDomainsPath,
                unsafeReasonsPath,
                siteListPath,
                siteListPlusPath,
                fmhySitesPath,
                primarySuccessCount,
                fallbackSuccessCount,
            )

            val constantsRes = fetchResults[0]
            val rentryRes = fetchResults[1]
            val unsafeDomainsRes = fetchResults[2]
            val unsafeReasonsRes = fetchResults[3]
            val siteListRes = fetchResults[4]
            val siteListPlusRes = fetchResults[5]
            val fmhySitesRes = fetchResults[6]

            if (constantsRes is FetchResult.Error) {
                val took = System.currentTimeMillis() - startMs
                log("FmhySync: failed: constants.ts error - ${constantsRes.message}")
                return@withContext SyncResult.Failed(constantsRes.message, took)
            }
            if (rentryRes is FetchResult.Error) {
                val took = System.currentTimeMillis() - startMs
                log("FmhySync: failed: rentry error - ${rentryRes.message}")
                return@withContext SyncResult.Failed(rentryRes.message, took)
            }
            if (unsafeDomainsRes is FetchResult.Error) {
                val took = System.currentTimeMillis() - startMs
                log("FmhySync: failed: unsafe domains error - ${unsafeDomainsRes.message}")
                return@withContext SyncResult.Failed(unsafeDomainsRes.message, took)
            }
            if (unsafeReasonsRes is FetchResult.Error) {
                val took = System.currentTimeMillis() - startMs
                log("FmhySync: failed: unsafe reasons error - ${unsafeReasonsRes.message}")
                return@withContext SyncResult.Failed(unsafeReasonsRes.message, took)
            }

            // Fail-soft logging for SafeGuard lists
            if (siteListRes is FetchResult.Error) {
                log("FmhySync: fail-soft error on $siteListPath: ${siteListRes.message}")
            }
            if (siteListPlusRes is FetchResult.Error) {
                log("FmhySync: fail-soft error on $siteListPlusPath: ${siteListPlusRes.message}")
            }
            if (fmhySitesRes is FetchResult.Error) {
                log("FmhySync: fail-soft error on $fmhySitesPath: ${fmhySitesRes.message}")
            }

            val controlFiles = deriveControlFiles(
                state,
                constantsRes,
                rentryRes,
                unsafeDomainsRes,
                unsafeReasonsRes,
                siteListRes,
                siteListPlusRes,
                fmhySitesRes,
                constantsPath,
                rentryPath,
                unsafeDomainsPath,
                unsafeReasonsPath,
                siteListPath,
                siteListPlusPath,
                fmhySitesPath,
            )
            val constantsText = controlFiles.constantsText
            val constantsSha = controlFiles.constantsSha
            val constantsEtag = controlFiles.constantsEtag
            val rentryText = controlFiles.rentryText
            val rentrySha = controlFiles.rentrySha
            val rentryEtag = controlFiles.rentryEtag
            val unsafeDomainsText = controlFiles.unsafeDomainsText
            val unsafeDomainsSha = controlFiles.unsafeDomainsSha
            val unsafeDomainsEtag = controlFiles.unsafeDomainsEtag
            val unsafeReasonsText = controlFiles.unsafeReasonsText
            val unsafeReasonsSha = controlFiles.unsafeReasonsSha
            val unsafeReasonsEtag = controlFiles.unsafeReasonsEtag
            val siteListText = controlFiles.siteListText
            val siteListSha = controlFiles.siteListSha
            val siteListEtag = controlFiles.siteListEtag
            val siteListPlusText = controlFiles.siteListPlusText
            val siteListPlusSha = controlFiles.siteListPlusSha
            val siteListPlusEtag = controlFiles.siteListPlusEtag
            val fmhySitesText = controlFiles.fmhySitesText
            val fmhySitesSha = controlFiles.fmhySitesSha
            val fmhySitesEtag = controlFiles.fmhySitesEtag
            val rentryChanged = controlFiles.rentryChanged
            val unsafeChanged = controlFiles.unsafeChanged
            val siteListChanged = controlFiles.siteListChanged
            val siteListPlusChanged = controlFiles.siteListPlusChanged
            val fmhySitesChanged = controlFiles.fmhySitesChanged
            val siteListsChanged = controlFiles.siteListsChanged

            // 3. Section list = Sections.parse(constants)
            val sectionCandidates = Sections.parse(constantsText)

            val firstDownload = store.sections().isEmpty()
            onProgress(SyncProgress.Files(0, sectionCandidates.size, 0L, firstDownload))

            // 4. Fetch every section file in parallel
            val sectionFetches = fetchSectionFiles(
                sectionCandidates,
                state,
                primarySuccessCount,
                fallbackSuccessCount,
            ) { done, bytes ->
                onProgress(SyncProgress.Files(done, sectionCandidates.size, bytes, firstDownload))
            }

            // Check if all failed with error
            val errors = sectionFetches.filter { it.second is FetchResult.Error }
            if (errors.size == sectionFetches.size && sectionFetches.isNotEmpty()) {
                val took = System.currentTimeMillis() - startMs
                val firstErr = (errors.first().second as FetchResult.Error).message
                log("FmhySync: failed: all section requests failed - $firstErr")
                return@withContext SyncResult.Failed(firstErr, took)
            }

            val validSections = mutableListOf<Pair<SectionInfo, FetchResult>>()
            for ((sec, res) in sectionFetches) {
                if (res is FetchResult.NotFound) {
                    // 404 -> section absent
                    continue
                }
                if (res is FetchResult.Error) {
                    // Network failure for some section
                    val took = System.currentTimeMillis() - startMs
                    log("FmhySync: failed: section ${sec.file} error - ${res.message}")
                    return@withContext SyncResult.Failed(res.message, took)
                }
                validSections.add(sec to res)
            }

            val existingSections = store.sections().associateBy { it.id }
            val validSectionIds = validSections.map { it.first.id }.toSet()
            val absentSections = existingSections.values.filter { it.id !in validSectionIds }

            // Determine which sections need re-parsing
            val sectionsToParse = mutableListOf<Pair<SectionInfo, String>>() // SectionInfo to markdown body
            val updatedFileStates = mutableMapOf<String, FileState>()

            updatedFileStates[constantsPath] = FileState(constantsEtag, constantsSha)
            updatedFileStates[rentryPath] = FileState(rentryEtag, rentrySha)
            updatedFileStates[unsafeDomainsPath] = FileState(unsafeDomainsEtag, unsafeDomainsSha)
            updatedFileStates[unsafeReasonsPath] = FileState(unsafeReasonsEtag, unsafeReasonsSha)

            if (siteListRes !is FetchResult.Error && siteListRes !is FetchResult.NotFound) {
                updatedFileStates[siteListPath] = FileState(siteListEtag, siteListSha)
            } else if (state.files.containsKey(siteListPath)) {
                updatedFileStates[siteListPath] = state.files[siteListPath]!!
            }

            if (siteListPlusRes !is FetchResult.Error && siteListPlusRes !is FetchResult.NotFound) {
                updatedFileStates[siteListPlusPath] = FileState(siteListPlusEtag, siteListPlusSha)
            } else if (state.files.containsKey(siteListPlusPath)) {
                updatedFileStates[siteListPlusPath] = state.files[siteListPlusPath]!!
            }

            if (fmhySitesRes !is FetchResult.Error && fmhySitesRes !is FetchResult.NotFound) {
                updatedFileStates[fmhySitesPath] = FileState(fmhySitesEtag, fmhySitesSha)
            } else if (state.files.containsKey(fmhySitesPath)) {
                updatedFileStates[fmhySitesPath] = state.files[fmhySitesPath]!!
            }

            for ((sec, res) in validSections) {
                val path = "docs/${sec.file}"
                val prevSha = state.files[path]?.sha256
                val rawFile = File(rawDir, sanitizePath(path))

                val (body, sha, etag) = when (res) {
                    is FetchResult.Content -> Triple(res.body, res.sha256, res.etag)
                    is FetchResult.NotModified -> Triple(rawFile.readText(), prevSha ?: "", res.etag)
                    else -> Triple("", "", null)
                }

                updatedFileStates[path] = FileState(etag, sha)

                val fileChanged = (res is FetchResult.Content) && (sha != prevSha)
                val isNew = existingSections[sec.id] == null
                val existingSec = existingSections[sec.id]
                val titleOrOrdChanged = existingSec != null &&
                    (existingSec.title != sec.title || existingSec.ord != sec.ord)

                val needsParse = fileChanged || rentryChanged || isNew || titleOrOrdChanged
                if (needsParse) {
                    sectionsToParse.add(sec to body)
                }
            }

            val sourceHost = if (fallbackSuccessCount.get() > primarySuccessCount.get()) {
                "fallback"
            } else {
                "primary"
            }

            // 6. Parse changed sections on Dispatchers.Default (in parallel)
            val b64Map = Base64Map(rentryText)
            val failedSections = mutableListOf<String>()
            val parsedSections = mutableListOf<ParsedSection>()

            if (sectionsToParse.isNotEmpty()) {
                val totalToParse = sectionsToParse.size
                onProgress(SyncProgress.Reading(0, totalToParse))
                val parseDoneCount = AtomicInteger(0)
                val parseLock = Any()
                val parseResults = coroutineScope {
                    sectionsToParse.map { (sec, markdown) ->
                        async(Dispatchers.Default) {
                            val res = try {
                                val parsed = parser(sec, markdown, b64Map)
                                sec.id to parsed
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                log("FmhySync: parse exception for ${sec.id}: ${e.message}")
                                sec.id to null
                            }
                            synchronized(parseLock) {
                                val done = parseDoneCount.incrementAndGet()
                                onProgress(SyncProgress.Reading(done, totalToParse))
                            }
                            res
                        }
                    }.awaitAll()
                }

                for ((secId, parsed) in parseResults) {
                    if (parsed != null) {
                        parsedSections.add(parsed)
                    } else {
                        failedSections.add(secId)
                    }
                }
            }

            // 7. Apply all changes in ONE DatasetStore transaction
            // Before taking transaction lock: diff entries per section
            val diffResult = computeSectionDiffs(parsedSections)
            val totalAdded = diffResult.totalAdded
            var totalRemoved = diffResult.totalRemoved
            val totalUpdated = diffResult.totalUpdated
            val changedHeadingsOrNotes = diffResult.changedHeadingsOrNotes
            val sectionDiffs = diffResult.sectionDiffs

            val absentSectionEntries = absentSections.associateWith { store.entriesForSection(it.id) }
            for (absent in absentSections) {
                val absentEntries = absentSectionEntries[absent] ?: emptyList()
                totalRemoved += absentEntries.size
            }

            val hasStructuralChanges = parsedSections.isNotEmpty() ||
                absentSections.isNotEmpty() ||
                unsafeChanged ||
                siteListsChanged ||
                totalAdded > 0 ||
                totalRemoved > 0 ||
                totalUpdated > 0

            var nextNum = store.maxNum()
            val currentVer = store.version()

            if (hasStructuralChanges) {
                coroutineContext.ensureActive()
                onProgress(SyncProgress.Saving)
                store.withTransaction { conn ->
                    val commitSha = "sync:$now"

                    // A. Absent sections: delete their rows
                    applyAbsentSections(conn, absentSections)

                    // B. Per changed section
                    nextNum = applySectionDiffs(conn, parsedSections, sectionDiffs, nextNum, now)

                    // C. Record changes into `changes` table per §3 & §4.3
                    val changesToRecord = classifyChanges(absentSections, absentSectionEntries, sectionDiffs)

                    insertChanges(conn, changesToRecord, now, commitSha)

                    // C. Update sections table
                    upsertSections(conn, validSections)

                    // D. Replace unsafe_domains if those files changed
                    replaceUnsafeDomains(conn, unsafeChanged, unsafeDomainsText, unsafeReasonsText)

                    // D2. Replace site_lists if those files changed
                    replaceSiteLists(
                        conn,
                        siteListsChanged,
                        siteListChanged,
                        siteListPlusChanged,
                        fmhySitesChanged,
                        unsafeReasonsText,
                        siteListText,
                        siteListPlusText,
                        fmhySitesText,
                    )

                    // E. Meta: version + 1 if anything changed, last_sync, source
                    writeMeta(conn, currentVer, now, sourceHost)
                }

                store.notifyChanged()

                val newVersion = store.version()
                val newState = SyncState(
                    lastCheck = now,
                    lastSuccess = now,
                    source = sourceHost,
                    files = updatedFileStates,
                    version = newVersion,
                )
                saveState(newState)

                val took = System.currentTimeMillis() - startMs
                log("FmhySync: +$totalAdded \u2212$totalRemoved in ${parsedSections.size} sections ($took ms, $sourceHost)")
                return@withContext SyncResult.Updated(
                    added = totalAdded,
                    removed = totalRemoved,
                    changedSections = parsedSections.size,
                    failedSections = failedSections,
                    tookMs = took,
                )
            } else {
                // Nothing changed in the DB
                val newState = SyncState(
                    lastCheck = now,
                    lastSuccess = now,
                    source = sourceHost,
                    files = updatedFileStates,
                    version = store.version(),
                )
                saveState(newState)

                val took = System.currentTimeMillis() - startMs
                log("FmhySync: up to date (${updatedFileStates.size} files, $took ms)")
                return@withContext SyncResult.UpToDate(took)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val took = System.currentTimeMillis() - startMs
            log("FmhySync: failed: ${e.message}")
            return@withContext SyncResult.Failed(e.message ?: "Unknown error", took)
        }
    }

    suspend fun installSnapshot(gzStream: InputStream, stateStream: InputStream) = withContext(Dispatchers.IO) {
        val currentDb = store.dbFile
        dir.mkdirs()
        val tmpDb = File(dir, "snapshot.tmp.db")
        try {
            tmpDb.delete()
            GZIPInputStream(gzStream).use { gzIn ->
                tmpDb.outputStream().use { out ->
                    gzIn.copyTo(out)
                }
            }

            var valid = false
            try {
                val conn = BundledSQLiteDriver().open(tmpDb.absolutePath)
                try {
                    var integrityOk = false
                    conn.prepare("PRAGMA integrity_check;").use { stmt ->
                        if (stmt.step() && stmt.getText(0).equals("ok", ignoreCase = true)) {
                            integrityOk = true
                        }
                    }
                    var schemaOk = false
                    conn.prepare("SELECT value FROM meta WHERE key = 'schema'").use { stmt ->
                        if (stmt.step() && stmt.getText(0) == DATASET_SCHEMA.toString()) {
                            schemaOk = true
                        }
                    }
                    valid = integrityOk && schemaOk
                } finally {
                    conn.close()
                }
            } catch (_: Exception) {
                valid = false
            }

            if (!valid) {
                tmpDb.delete()
                log("FmhySync: snapshot integrity/schema check failed")
                return@withContext
            }

            store.close()
            val wal = File(currentDb.parentFile, "${currentDb.name}-wal")
            val shm = File(currentDb.parentFile, "${currentDb.name}-shm")
            if (wal.exists()) wal.delete()
            if (shm.exists()) shm.delete()

            currentDb.parentFile?.mkdirs()
            Files.move(
                tmpDb.toPath(),
                currentDb.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )

            val stateText = stateStream.bufferedReader().readText()
            val snapshotState = json.decodeFromString<SyncState>(stateText)
            // lastCheck stays 0 so the sync right after first launch is not skipped.
            val seededState = snapshotState.copy(lastCheck = 0L)
            stateFile.writeText(json.encodeToString(seededState))

            store.reopen()
            store.notifyChanged()
            log("FmhySync: installed snapshot v${store.version()}")
        } catch (e: Exception) {
            tmpDb.delete()
            log("FmhySync: failed to install snapshot: ${e.message}")
        }
    }

}
