package io.github.omeg4dev.fmhy.data.sync

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DATASET_SCHEMA
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.parse.Base64Map
import io.github.omeg4dev.fmhy.data.parse.EntryRow
import io.github.omeg4dev.fmhy.data.parse.FmhyParser
import io.github.omeg4dev.fmhy.data.parse.ParsedSection
import io.github.omeg4dev.fmhy.data.parse.SectionInfo
import io.github.omeg4dev.fmhy.data.parse.Sections
import io.github.omeg4dev.fmhy.data.parse.SiteLists
import io.github.omeg4dev.fmhy.data.parse.UnsafeList
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

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
    private val store: DatasetStore,
    private val dir: File,
    private val client: OkHttpClient,
    private val sources: Sources = Sources(),
    private val clock: () -> Long,
    private val log: (String) -> Unit,
    private val minIntervalSec: Long = 600L,
    private val parser: (SectionInfo, String, Base64Map) -> ParsedSection = FmhyParser::parseSection,
) {
    private val json = Json {
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

    private fun sanitizePath(path: String): String =
        path.replace(Regex("""[^a-zA-Z0-9._-]"""), "_")

    private sealed interface FetchResult {
        data class NotModified(val path: String, val etag: String?, val host: String) : FetchResult
        data class Content(
            val path: String,
            val body: String,
            val etag: String?,
            val sha256: String,
            val host: String,
        ) : FetchResult
        data class NotFound(val path: String) : FetchResult
        data class Error(val path: String, val message: String) : FetchResult
    }

    private fun fetchFile(
        path: String,
        urlPrimary: String,
        urlFallback: String?,
        etag: String?,
        customUserAgent: String? = null,
        primaryCount: AtomicInteger,
        fallbackCount: AtomicInteger,
    ): FetchResult {
        val rawFile = File(rawDir, sanitizePath(path))
        val sendEtag = etag != null && rawFile.exists()

        var response: Response? = null
        var usedHost = "primary"
        var tryFallback = false

        try {
            val req = Request.Builder().url(urlPrimary).apply {
                if (sendEtag) header("If-None-Match", etag)
                if (customUserAgent != null) header("User-Agent", customUserAgent)
            }.build()
            val resp = client.newCall(req).execute()
            if (resp.code == 403 || resp.code == 429 || resp.code in 500..599) {
                resp.close()
                tryFallback = true
            } else {
                response = resp
            }
        } catch (_: IOException) {
            tryFallback = true
        }

        if (tryFallback && urlFallback != null) {
            try {
                val req = Request.Builder().url(urlFallback).apply {
                    if (sendEtag) header("If-None-Match", etag)
                    if (customUserAgent != null) header("User-Agent", customUserAgent)
                }.build()
                response = client.newCall(req).execute()
                usedHost = "fallback"
            } catch (e: Exception) {
                return FetchResult.Error(path, "Fallback request failed: ${e.message}")
            }
        }

        val resp = response ?: return FetchResult.Error(path, "Primary request failed without fallback")

        return resp.use { r ->
            if (r.code == 404) {
                FetchResult.NotFound(path)
            } else if (r.code == 304) {
                if (usedHost == "primary") primaryCount.incrementAndGet() else fallbackCount.incrementAndGet()
                FetchResult.NotModified(path, etag, usedHost)
            } else if (r.isSuccessful) {
                if (usedHost == "primary") primaryCount.incrementAndGet() else fallbackCount.incrementAndGet()
                val body = r.body.string()
                val newEtag = r.header("ETag") ?: etag
                val bodySha = sha256(body)
                rawDir.mkdirs()
                rawFile.writeText(body)
                FetchResult.Content(path, body, newEtag, bodySha, usedHost)
            } else {
                FetchResult.Error(path, "HTTP ${r.code}")
            }
        }
    }

    suspend fun sync(force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
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

            val fetchResults = coroutineScope {
                val dConst = async {
                    fetchFile(
                        path = constantsPath,
                        urlPrimary = constantsPrimaryUrl,
                        urlFallback = constantsFallbackUrl,
                        etag = state.files[constantsPath]?.etag,
                        primaryCount = primarySuccessCount,
                        fallbackCount = fallbackSuccessCount,
                    )
                }
                val dRentry = async {
                    fetchFile(
                        path = rentryPath,
                        urlPrimary = sources.rentry,
                        urlFallback = null,
                        etag = state.files[rentryPath]?.etag,
                        customUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        primaryCount = primarySuccessCount,
                        fallbackCount = fallbackSuccessCount,
                    )
                }
                val dUnsafeDomains = async {
                    fetchFile(
                        path = unsafeDomainsPath,
                        urlPrimary = sources.unsafeDomains,
                        urlFallback = null,
                        etag = state.files[unsafeDomainsPath]?.etag,
                        primaryCount = primarySuccessCount,
                        fallbackCount = fallbackSuccessCount,
                    )
                }
                val dUnsafeReasons = async {
                    fetchFile(
                        path = unsafeReasonsPath,
                        urlPrimary = sources.unsafeReasons,
                        urlFallback = null,
                        etag = state.files[unsafeReasonsPath]?.etag,
                        primaryCount = primarySuccessCount,
                        fallbackCount = fallbackSuccessCount,
                    )
                }
                val dSiteList = async {
                    fetchFile(
                        path = siteListPath,
                        urlPrimary = sources.siteList,
                        urlFallback = null,
                        etag = state.files[siteListPath]?.etag,
                        primaryCount = primarySuccessCount,
                        fallbackCount = fallbackSuccessCount,
                    )
                }
                val dSiteListPlus = async {
                    fetchFile(
                        path = siteListPlusPath,
                        urlPrimary = sources.siteListPlus,
                        urlFallback = null,
                        etag = state.files[siteListPlusPath]?.etag,
                        primaryCount = primarySuccessCount,
                        fallbackCount = fallbackSuccessCount,
                    )
                }
                val dFmhySites = async {
                    fetchFile(
                        path = fmhySitesPath,
                        urlPrimary = sources.fmhySites,
                        urlFallback = null,
                        etag = state.files[fmhySitesPath]?.etag,
                        primaryCount = primarySuccessCount,
                        fallbackCount = fallbackSuccessCount,
                    )
                }
                listOf(
                    dConst.await(),
                    dRentry.await(),
                    dUnsafeDomains.await(),
                    dUnsafeReasons.await(),
                    dSiteList.await(),
                    dSiteListPlus.await(),
                    dFmhySites.await()
                )
            }

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

            val constantsText = when (constantsRes) {
                is FetchResult.Content -> constantsRes.body
                is FetchResult.NotModified -> File(rawDir, sanitizePath(constantsPath)).readText()
                else -> ""
            }
            val constantsSha = when (constantsRes) {
                is FetchResult.Content -> constantsRes.sha256
                else -> state.files[constantsPath]?.sha256 ?: sha256(constantsText)
            }
            val constantsEtag = when (constantsRes) {
                is FetchResult.Content -> constantsRes.etag
                is FetchResult.NotModified -> constantsRes.etag
                else -> null
            }

            val rentryText = when (rentryRes) {
                is FetchResult.Content -> rentryRes.body
                is FetchResult.NotModified -> File(rawDir, sanitizePath(rentryPath)).readText()
                else -> ""
            }
            val rentrySha = when (rentryRes) {
                is FetchResult.Content -> rentryRes.sha256
                else -> state.files[rentryPath]?.sha256 ?: sha256(rentryText)
            }
            val rentryEtag = when (rentryRes) {
                is FetchResult.Content -> rentryRes.etag
                is FetchResult.NotModified -> rentryRes.etag
                else -> null
            }

            val unsafeDomainsText = when (unsafeDomainsRes) {
                is FetchResult.Content -> unsafeDomainsRes.body
                is FetchResult.NotModified -> File(rawDir, sanitizePath(unsafeDomainsPath)).readText()
                else -> ""
            }
            val unsafeDomainsSha = when (unsafeDomainsRes) {
                is FetchResult.Content -> unsafeDomainsRes.sha256
                else -> state.files[unsafeDomainsPath]?.sha256 ?: sha256(unsafeDomainsText)
            }
            val unsafeDomainsEtag = when (unsafeDomainsRes) {
                is FetchResult.Content -> unsafeDomainsRes.etag
                is FetchResult.NotModified -> unsafeDomainsRes.etag
                else -> null
            }

            val unsafeReasonsText = when (unsafeReasonsRes) {
                is FetchResult.Content -> unsafeReasonsRes.body
                is FetchResult.NotModified -> File(rawDir, sanitizePath(unsafeReasonsPath)).readText()
                else -> ""
            }
            val unsafeReasonsSha = when (unsafeReasonsRes) {
                is FetchResult.Content -> unsafeReasonsRes.sha256
                else -> state.files[unsafeReasonsPath]?.sha256 ?: sha256(unsafeReasonsText)
            }
            val unsafeReasonsEtag = when (unsafeReasonsRes) {
                is FetchResult.Content -> unsafeReasonsRes.etag
                is FetchResult.NotModified -> unsafeReasonsRes.etag
                else -> null
            }

            val siteListText = when (siteListRes) {
                is FetchResult.Content -> siteListRes.body
                is FetchResult.NotModified -> File(rawDir, sanitizePath(siteListPath)).takeIf { it.exists() }?.readText() ?: ""
                else -> ""
            }
            val siteListSha = when (siteListRes) {
                is FetchResult.Content -> siteListRes.sha256
                else -> state.files[siteListPath]?.sha256 ?: sha256(siteListText)
            }
            val siteListEtag = when (siteListRes) {
                is FetchResult.Content -> siteListRes.etag
                is FetchResult.NotModified -> siteListRes.etag
                else -> state.files[siteListPath]?.etag
            }

            val siteListPlusText = when (siteListPlusRes) {
                is FetchResult.Content -> siteListPlusRes.body
                is FetchResult.NotModified -> File(rawDir, sanitizePath(siteListPlusPath)).takeIf { it.exists() }?.readText() ?: ""
                else -> ""
            }
            val siteListPlusSha = when (siteListPlusRes) {
                is FetchResult.Content -> siteListPlusRes.sha256
                else -> state.files[siteListPlusPath]?.sha256 ?: sha256(siteListPlusText)
            }
            val siteListPlusEtag = when (siteListPlusRes) {
                is FetchResult.Content -> siteListPlusRes.etag
                is FetchResult.NotModified -> siteListPlusRes.etag
                else -> state.files[siteListPlusPath]?.etag
            }

            val fmhySitesText = when (fmhySitesRes) {
                is FetchResult.Content -> fmhySitesRes.body
                is FetchResult.NotModified -> File(rawDir, sanitizePath(fmhySitesPath)).takeIf { it.exists() }?.readText() ?: ""
                else -> ""
            }
            val fmhySitesSha = when (fmhySitesRes) {
                is FetchResult.Content -> fmhySitesRes.sha256
                else -> state.files[fmhySitesPath]?.sha256 ?: sha256(fmhySitesText)
            }
            val fmhySitesEtag = when (fmhySitesRes) {
                is FetchResult.Content -> fmhySitesRes.etag
                is FetchResult.NotModified -> fmhySitesRes.etag
                else -> state.files[fmhySitesPath]?.etag
            }

            val rentryChanged = (rentryRes is FetchResult.Content) &&
                (rentrySha != state.files[rentryPath]?.sha256)

            val unsafeChanged = ((unsafeDomainsRes is FetchResult.Content) &&
                (unsafeDomainsSha != state.files[unsafeDomainsPath]?.sha256)) ||
                ((unsafeReasonsRes is FetchResult.Content) &&
                (unsafeReasonsSha != state.files[unsafeReasonsPath]?.sha256))

            val siteListChanged = (siteListRes is FetchResult.Content) &&
                (siteListSha != state.files[siteListPath]?.sha256)
            val siteListPlusChanged = (siteListPlusRes is FetchResult.Content) &&
                (siteListPlusSha != state.files[siteListPlusPath]?.sha256)
            val fmhySitesChanged = (fmhySitesRes is FetchResult.Content) &&
                (fmhySitesSha != state.files[fmhySitesPath]?.sha256)
            val siteListsChanged = siteListChanged || siteListPlusChanged || fmhySitesChanged

            // 3. Section list = Sections.parse(constants)
            val sectionCandidates = Sections.parse(constantsText)

            // 4. Fetch every section file in parallel
            val sectionFetches = coroutineScope {
                sectionCandidates.map { sec ->
                    val path = "docs/${sec.file}"
                    val urlPrimary = "${sources.primary.trimEnd('/')}/$path"
                    val urlFallback = "${sources.fallback.trimEnd('/')}/$path"
                    val etag = state.files[path]?.etag
                    async {
                        sec to fetchFile(
                            path = path,
                            urlPrimary = urlPrimary,
                            urlFallback = urlFallback,
                            etag = etag,
                            primaryCount = primarySuccessCount,
                            fallbackCount = fallbackSuccessCount,
                        )
                    }
                }.awaitAll()
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
                val parseResults = coroutineScope {
                    sectionsToParse.map { (sec, markdown) ->
                        async(Dispatchers.Default) {
                            try {
                                val parsed = parser(sec, markdown, b64Map)
                                sec.id to parsed
                            } catch (e: Exception) {
                                log("FmhySync: parse exception for ${sec.id}: ${e.message}")
                                sec.id to null
                            }
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
            var totalAdded = 0
            var totalRemoved = 0
            var totalUpdated = 0
            var changedHeadingsOrNotes = false

            data class EntryDiff(
                val sectionId: String,
                val vanished: List<Entry>,
                val inserted: List<EntryRow>,
                val updated: List<Pair<Entry, EntryRow>>,
                val starredOldEntries: List<Entry>, // old entries where tier goes 0 -> >= 1
                val lineOnlyUpdated: List<Pair<Entry, EntryRow>> = emptyList(),
            )

            val sectionDiffs = mutableListOf<EntryDiff>()

            for (parsedSec in parsedSections) {
                val secId = parsedSec.section.id
                val existingEntries = store.entriesForSection(secId).associateBy { it.id }
                val newEntries = parsedSec.entries
                val newEntriesById = newEntries.associateBy { it.id }

                val vanished = existingEntries.values.filter { it.id !in newEntriesById }
                val inserted = newEntries.filter { it.id !in existingEntries }
                val toUpdate = mutableListOf<Pair<Entry, EntryRow>>()
                val lineOnlyUpdate = mutableListOf<Pair<Entry, EntryRow>>()
                val starred = mutableListOf<Entry>()

                for (newRow in newEntries) {
                    val old = existingEntries[newRow.id] ?: continue
                    val newMirrorsJson = json.encodeToString(newRow.mirrors)
                    val oldMirrorsJson = json.encodeToString(old.mirrors)
                    val newExtraJson = json.encodeToString(newRow.extraLinks)
                    val oldExtraJson = json.encodeToString(old.extraLinks)
                    val oldTagsStr = old.tags.joinToString("|")

                    val changed = old.headingId != newRow.headingId ||
                        old.ord != newRow.ord ||
                        old.name != newRow.name ||
                        old.url != newRow.url ||
                        old.originalUrl != newRow.originalUrl ||
                        old.description != newRow.description ||
                        oldTagsStr != newRow.tags ||
                        old.headingPath != newRow.headingPath ||
                        old.tier.value != newRow.tier ||
                        old.kind.value != newRow.kind ||
                        old.isInlineMore != newRow.isInlineMore ||
                        old.isBase64 != newRow.isBase64 ||
                        oldMirrorsJson != newMirrorsJson ||
                        oldExtraJson != newExtraJson

                    val lineChanged = old.line != newRow.line

                    if (changed) {
                        toUpdate.add(old to newRow)
                        if (old.tier.value == 0 && newRow.tier >= 1) {
                            starred.add(old)
                        }
                    } else if (lineChanged) {
                        lineOnlyUpdate.add(old to newRow)
                    }
                }

                totalAdded += inserted.size
                totalRemoved += vanished.size
                totalUpdated += toUpdate.size
                if (parsedSec.headings.isNotEmpty() || parsedSec.notes.isNotEmpty()) {
                    changedHeadingsOrNotes = true
                }

                sectionDiffs.add(EntryDiff(secId, vanished, inserted, toUpdate, starred, lineOnlyUpdate))
            }

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
                store.withTransaction { conn ->
                    // A. Absent sections: delete their rows
                    for (absent in absentSections) {
                        val absentEntries = absentSectionEntries[absent] ?: emptyList()
                        for (e in absentEntries) {
                            conn.prepare("INSERT OR IGNORE INTO changes (ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (?, 'removed', ?, ?, ?, ?, '', '')").use { stmt ->
                                stmt.bindLong(1, now)
                                stmt.bindText(2, e.id)
                                stmt.bindText(3, e.sectionId)
                                stmt.bindText(4, e.name)
                                stmt.bindText(5, e.url)
                                stmt.step()
                            }
                        }
                        conn.prepare("DELETE FROM entries WHERE section_id = ?").use { stmt ->
                            stmt.bindText(1, absent.id)
                            stmt.step()
                        }
                        conn.prepare("DELETE FROM headings WHERE section_id = ?").use { stmt ->
                            stmt.bindText(1, absent.id)
                            stmt.step()
                        }
                        conn.prepare("DELETE FROM notes WHERE section_id = ?").use { stmt ->
                            stmt.bindText(1, absent.id)
                            stmt.step()
                        }
                        conn.prepare("DELETE FROM sections WHERE id = ?").use { stmt ->
                            stmt.bindText(1, absent.id)
                            stmt.step()
                        }
                    }

                    // B. Per changed section
                    val parsedMap = parsedSections.associateBy { it.section.id }
                    for (diff in sectionDiffs) {
                        val secId = diff.sectionId
                        val parsedSec = parsedMap[secId] ?: continue

                        // Replace headings
                        conn.prepare("DELETE FROM headings WHERE section_id = ?").use { stmt ->
                            stmt.bindText(1, secId)
                            stmt.step()
                        }
                        val sqlHeading = "INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES (?, ?, ?, ?, ?, ?, ?)"
                        conn.prepare(sqlHeading).use { stmt ->
                            for (h in parsedSec.headings) {
                                stmt.bindText(1, h.id)
                                stmt.bindText(2, h.sectionId)
                                if (h.parentId != null) stmt.bindText(3, h.parentId) else stmt.bindNull(3)
                                stmt.bindLong(4, h.level.toLong())
                                stmt.bindText(5, h.title)
                                stmt.bindText(6, h.anchor)
                                stmt.bindLong(7, h.ord.toLong())
                                stmt.step()
                                stmt.reset()
                            }
                        }

                        // Replace notes
                        conn.prepare("DELETE FROM notes WHERE section_id = ?").use { stmt ->
                            stmt.bindText(1, secId)
                            stmt.step()
                        }
                        val sqlNote = "INSERT INTO notes (id, section_id, heading_id, ord, text_md) VALUES (?, ?, ?, ?, ?)"
                        conn.prepare(sqlNote).use { stmt ->
                            for (n in parsedSec.notes) {
                                stmt.bindText(1, n.id)
                                stmt.bindText(2, n.sectionId)
                                if (n.headingId != null) stmt.bindText(3, n.headingId) else stmt.bindNull(3)
                                stmt.bindLong(4, n.ord.toLong())
                                stmt.bindText(5, n.textMd)
                                stmt.step()
                                stmt.reset()
                            }
                        }

                        // Vanished entries: delete and record 'removed' in changes
                        conn.prepare("DELETE FROM entries WHERE id = ?").use { delStmt ->
                            conn.prepare("INSERT OR IGNORE INTO changes (ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (?, 'removed', ?, ?, ?, ?, '', '')").use { chStmt ->
                                for (v in diff.vanished) {
                                    delStmt.bindText(1, v.id)
                                    delStmt.step()
                                    delStmt.reset()

                                    chStmt.bindLong(1, now)
                                    chStmt.bindText(2, v.id)
                                    chStmt.bindText(3, v.sectionId)
                                    chStmt.bindText(4, v.name)
                                    chStmt.bindText(5, v.url)
                                    chStmt.step()
                                    chStmt.reset()
                                }
                            }
                        }

                        // Inserted entries: insert and record 'added' in changes
                        val sqlInsertEntry = """
                            INSERT INTO entries (
                                num, id, section_id, heading_id, ord, name, url, original_url,
                                description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
                                mirrors, extra_links, first_seen, last_changed, starred_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent()
                        conn.prepare(sqlInsertEntry).use { insStmt ->
                            conn.prepare("INSERT OR IGNORE INTO changes (ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (?, 'added', ?, ?, ?, ?, '', '')").use { chStmt ->
                                for (ins in diff.inserted) {
                                    val num = ++nextNum
                                    val starredAt = if (ins.tier >= 1) now else null

                                    insStmt.bindLong(1, num)
                                    insStmt.bindText(2, ins.id)
                                    insStmt.bindText(3, ins.sectionId)
                                    if (ins.headingId != null) insStmt.bindText(4, ins.headingId) else insStmt.bindNull(4)
                                    insStmt.bindLong(5, ins.ord.toLong())
                                    insStmt.bindText(6, ins.name)
                                    insStmt.bindText(7, ins.url)
                                    if (ins.originalUrl != null) insStmt.bindText(8, ins.originalUrl) else insStmt.bindNull(8)
                                    insStmt.bindText(9, ins.description)
                                    insStmt.bindText(10, ins.tags)
                                    insStmt.bindText(11, ins.headingPath)
                                    insStmt.bindLong(12, ins.tier.toLong())
                                    insStmt.bindLong(13, ins.kind.toLong())
                                    insStmt.bindLong(14, ins.line.toLong())
                                    insStmt.bindLong(15, if (ins.isInlineMore) 1L else 0L)
                                    insStmt.bindLong(16, if (ins.isBase64) 1L else 0L)
                                    insStmt.bindText(17, json.encodeToString(ins.mirrors))
                                    insStmt.bindText(18, json.encodeToString(ins.extraLinks))
                                    insStmt.bindLong(19, now)
                                    insStmt.bindLong(20, now)
                                    if (starredAt != null) insStmt.bindLong(21, starredAt) else insStmt.bindNull(21)
                                    insStmt.step()
                                    insStmt.reset()

                                    chStmt.bindLong(1, now)
                                    chStmt.bindText(2, ins.id)
                                    chStmt.bindText(3, ins.sectionId)
                                    chStmt.bindText(4, ins.name)
                                    chStmt.bindText(5, ins.url)
                                    chStmt.step()
                                    chStmt.reset()
                                }
                            }
                        }

                        // Updated entries: update fields and record 'starred' if tier 0 -> >= 1
                        val sqlUpdateEntry = """
                            UPDATE entries SET
                                section_id = ?, heading_id = ?, ord = ?, name = ?, url = ?,
                                original_url = ?, description = ?, tags = ?, heading_path = ?,
                                tier = ?, kind = ?, line = ?, is_inline_more = ?, is_base64 = ?, mirrors = ?,
                                extra_links = ?, last_changed = ?, starred_at = ?
                            WHERE id = ?
                        """.trimIndent()
                        conn.prepare(sqlUpdateEntry).use { upStmt ->
                            conn.prepare("INSERT OR IGNORE INTO changes (ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (?, 'starred', ?, ?, ?, ?, '', '')").use { chStmt ->
                                for ((old, row) in diff.updated) {
                                    val starredAt = if (old.tier.value == 0 && row.tier >= 1) {
                                        now
                                    } else if (row.tier == 0) {
                                        null
                                    } else {
                                        old.starredAt ?: now
                                    }

                                    upStmt.bindText(1, row.sectionId)
                                    if (row.headingId != null) upStmt.bindText(2, row.headingId) else upStmt.bindNull(2)
                                    upStmt.bindLong(3, row.ord.toLong())
                                    upStmt.bindText(4, row.name)
                                    upStmt.bindText(5, row.url)
                                    if (row.originalUrl != null) upStmt.bindText(6, row.originalUrl) else upStmt.bindNull(6)
                                    upStmt.bindText(7, row.description)
                                    upStmt.bindText(8, row.tags)
                                    upStmt.bindText(9, row.headingPath)
                                    upStmt.bindLong(10, row.tier.toLong())
                                    upStmt.bindLong(11, row.kind.toLong())
                                    upStmt.bindLong(12, row.line.toLong())
                                    upStmt.bindLong(13, if (row.isInlineMore) 1L else 0L)
                                    upStmt.bindLong(14, if (row.isBase64) 1L else 0L)
                                    upStmt.bindText(15, json.encodeToString(row.mirrors))
                                    upStmt.bindText(16, json.encodeToString(row.extraLinks))
                                    upStmt.bindLong(17, now)
                                    if (starredAt != null) upStmt.bindLong(18, starredAt) else upStmt.bindNull(18)
                                    upStmt.bindText(19, row.id)
                                    upStmt.step()
                                    upStmt.reset()

                                    if (old.tier.value == 0 && row.tier >= 1) {
                                        chStmt.bindLong(1, now)
                                        chStmt.bindText(2, row.id)
                                        chStmt.bindText(3, row.sectionId)
                                        chStmt.bindText(4, row.name)
                                        chStmt.bindText(5, row.url)
                                        chStmt.step()
                                        chStmt.reset()
                                    }
                                }
                            }
                        }

                        // Line-only updated entries: update line silently without recording change or updating last_changed
                        if (diff.lineOnlyUpdated.isNotEmpty()) {
                            conn.prepare("UPDATE entries SET line = ? WHERE id = ?").use { stmt ->
                                for ((_, row) in diff.lineOnlyUpdated) {
                                    stmt.bindLong(1, row.line.toLong())
                                    stmt.bindText(2, row.id)
                                    stmt.step()
                                    stmt.reset()
                                }
                            }
                        }
                    }

                    // C. Update sections table
                    val sqlUpsertSection = """
                        INSERT INTO sections (id, title, description, file, ord)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            title = excluded.title,
                            description = excluded.description,
                            file = excluded.file,
                            ord = excluded.ord
                    """.trimIndent()
                    conn.prepare(sqlUpsertSection).use { stmt ->
                        for ((sec, _) in validSections) {
                            stmt.bindText(1, sec.id)
                            stmt.bindText(2, sec.title)
                            stmt.bindText(3, sec.description)
                            stmt.bindText(4, sec.file)
                            stmt.bindLong(5, sec.ord.toLong())
                            stmt.step()
                            stmt.reset()
                        }
                    }

                    // D. Replace unsafe_domains if those files changed
                    if (unsafeChanged) {
                        conn.execSQL("DELETE FROM unsafe_domains;")
                        val unsafeList = UnsafeList.parse(unsafeDomainsText, unsafeReasonsText)
                        conn.prepare("INSERT INTO unsafe_domains (domain, reason) VALUES (?, ?)").use { stmt ->
                            for ((domain, reason) in unsafeList) {
                                stmt.bindText(1, domain)
                                stmt.bindText(2, reason)
                                stmt.step()
                                stmt.reset()
                            }
                        }
                    }

                    // D2. Replace site_lists if those files changed
                    if (siteListsChanged) {
                        val reasonsMap = SiteLists.parseReasons(unsafeReasonsText)

                        if (siteListChanged) {
                            conn.execSQL("DELETE FROM site_lists WHERE list = 'unsafe';")
                            val unsafeKeys = mutableSetOf<String>()
                            conn.prepare("INSERT OR REPLACE INTO site_lists (key, list, reason) VALUES (?, 'unsafe', ?)").use { stmt ->
                                for (k in SiteLists.parseList(siteListText)) {
                                    unsafeKeys.add(k)
                                    val host = k.substringBefore('/')
                                    val r = reasonsMap[k] ?: reasonsMap[host] ?: reasonsMap["www.$host"] ?: ""
                                    stmt.bindText(1, k)
                                    stmt.bindText(2, r)
                                    stmt.step()
                                    stmt.reset()
                                }
                                for ((k, r) in reasonsMap) {
                                    val norm = SiteLists.normalizeListKey(k) ?: continue
                                    if (k.contains('/')) {
                                        stmt.bindText(1, norm)
                                        stmt.bindText(2, r)
                                        stmt.step()
                                        stmt.reset()
                                    }
                                }
                            }
                        }

                        if (siteListPlusChanged) {
                            conn.execSQL("DELETE FROM site_lists WHERE list = 'potentially_unsafe';")
                            conn.prepare("INSERT OR REPLACE INTO site_lists (key, list, reason) VALUES (?, 'potentially_unsafe', ?)").use { stmt ->
                                for (k in SiteLists.parseList(siteListPlusText)) {
                                    val host = k.substringBefore('/')
                                    val r = reasonsMap[k] ?: reasonsMap[host] ?: reasonsMap["www.$host"] ?: ""
                                    stmt.bindText(1, k)
                                    stmt.bindText(2, r)
                                    stmt.step()
                                    stmt.reset()
                                }
                            }
                        }

                        if (fmhySitesChanged) {
                            conn.execSQL("DELETE FROM site_lists WHERE list = 'fmhy';")
                            conn.prepare("INSERT OR REPLACE INTO site_lists (key, list, reason) VALUES (?, 'fmhy', '')").use { stmt ->
                                for (k in SiteLists.parseList(fmhySitesText)) {
                                    stmt.bindText(1, k)
                                    stmt.step()
                                    stmt.reset()
                                }
                            }
                        }
                    }

                    // E. Meta: version + 1 if anything changed, last_sync, source
                    val newVer = currentVer + 1
                    conn.prepare("INSERT OR REPLACE INTO meta (key, value) VALUES ('version', ?)").use { stmt ->
                        stmt.bindText(1, newVer.toString())
                        stmt.step()
                    }
                    conn.prepare("INSERT OR REPLACE INTO meta (key, value) VALUES ('last_sync', ?)").use { stmt ->
                        stmt.bindText(1, now.toString())
                        stmt.step()
                    }
                    conn.prepare("INSERT OR REPLACE INTO meta (key, value) VALUES ('source', ?)").use { stmt ->
                        stmt.bindText(1, sourceHost)
                        stmt.step()
                    }
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

    private fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
