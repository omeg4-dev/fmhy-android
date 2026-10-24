package io.github.omeg4dev.fmhy.data.sync

import io.github.omeg4dev.fmhy.data.parse.SectionInfo
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Request
import okhttp3.Response

internal fun sanitizePath(path: String): String =
    path.replace(Regex("""[^a-zA-Z0-9._-]"""), "_")

internal sealed interface FetchResult {
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

internal fun FmhySync.fetchFile(
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

internal suspend fun FmhySync.fetchControlFiles(
    state: SyncState,
    constantsPath: String,
    constantsPrimaryUrl: String,
    constantsFallbackUrl: String,
    rentryPath: String,
    unsafeDomainsPath: String,
    unsafeReasonsPath: String,
    siteListPath: String,
    siteListPlusPath: String,
    fmhySitesPath: String,
    primarySuccessCount: AtomicInteger,
    fallbackSuccessCount: AtomicInteger,
): List<FetchResult> {
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
    return fetchResults
}

internal data class ControlFiles(
    val constantsText: String,
    val constantsSha: String,
    val constantsEtag: String?,
    val rentryText: String,
    val rentrySha: String,
    val rentryEtag: String?,
    val unsafeDomainsText: String,
    val unsafeDomainsSha: String,
    val unsafeDomainsEtag: String?,
    val unsafeReasonsText: String,
    val unsafeReasonsSha: String,
    val unsafeReasonsEtag: String?,
    val siteListText: String,
    val siteListSha: String,
    val siteListEtag: String?,
    val siteListPlusText: String,
    val siteListPlusSha: String,
    val siteListPlusEtag: String?,
    val fmhySitesText: String,
    val fmhySitesSha: String,
    val fmhySitesEtag: String?,
    val rentryChanged: Boolean,
    val unsafeChanged: Boolean,
    val siteListChanged: Boolean,
    val siteListPlusChanged: Boolean,
    val fmhySitesChanged: Boolean,
    val siteListsChanged: Boolean,
)

internal fun FmhySync.deriveControlFiles(
    state: SyncState,
    constantsRes: FetchResult,
    rentryRes: FetchResult,
    unsafeDomainsRes: FetchResult,
    unsafeReasonsRes: FetchResult,
    siteListRes: FetchResult,
    siteListPlusRes: FetchResult,
    fmhySitesRes: FetchResult,
    constantsPath: String,
    rentryPath: String,
    unsafeDomainsPath: String,
    unsafeReasonsPath: String,
    siteListPath: String,
    siteListPlusPath: String,
    fmhySitesPath: String,
): ControlFiles {
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
    return ControlFiles(
        constantsText = constantsText,
        constantsSha = constantsSha,
        constantsEtag = constantsEtag,
        rentryText = rentryText,
        rentrySha = rentrySha,
        rentryEtag = rentryEtag,
        unsafeDomainsText = unsafeDomainsText,
        unsafeDomainsSha = unsafeDomainsSha,
        unsafeDomainsEtag = unsafeDomainsEtag,
        unsafeReasonsText = unsafeReasonsText,
        unsafeReasonsSha = unsafeReasonsSha,
        unsafeReasonsEtag = unsafeReasonsEtag,
        siteListText = siteListText,
        siteListSha = siteListSha,
        siteListEtag = siteListEtag,
        siteListPlusText = siteListPlusText,
        siteListPlusSha = siteListPlusSha,
        siteListPlusEtag = siteListPlusEtag,
        fmhySitesText = fmhySitesText,
        fmhySitesSha = fmhySitesSha,
        fmhySitesEtag = fmhySitesEtag,
        rentryChanged = rentryChanged,
        unsafeChanged = unsafeChanged,
        siteListChanged = siteListChanged,
        siteListPlusChanged = siteListPlusChanged,
        fmhySitesChanged = fmhySitesChanged,
        siteListsChanged = siteListsChanged,
    )
}

internal suspend fun FmhySync.fetchSectionFiles(
    sectionCandidates: List<SectionInfo>,
    state: SyncState,
    primarySuccessCount: AtomicInteger,
    fallbackSuccessCount: AtomicInteger,
): List<Pair<SectionInfo, FetchResult>> {
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
    return sectionFetches
}

internal fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))

internal fun sha256(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(bytes).joinToString("") { "%02x".format(it) }
}
