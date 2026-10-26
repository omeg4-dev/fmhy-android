package io.github.omeg4dev.fmhy.browser.download

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

class HybridAnalysisClient(
    baseClient: OkHttpClient,
    private val apiKey: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ScanClient {

    override val service: ScanService = ScanService.HYBRID_ANALYSIS
    override val maxUploadBytes: Long = 100L * 1024 * 1024

    private val client: OkHttpClient = baseClient.newBuilder()
        .cache(null)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun newRequestBuilder(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("api-key", apiKey().trim())
            .header("User-Agent", "Falcon Sandbox")
            .header("Accept", "application/json")
    }

    override suspend fun lookupFile(sha256: String): ServiceResult = withContext(Dispatchers.IO) {
        val reportUrl = ScanLinks.fileReport(ScanService.HYBRID_ANALYSIS, sha256)
        try {
            val request = newRequestBuilder("https://hybrid-analysis.com/api/v2/overview/$sha256")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 404) {
                    return@withContext ServiceResult(
                        service = ScanService.HYBRID_ANALYSIS,
                        verdict = ScanVerdict.UNKNOWN,
                        detail = ScanText.UNKNOWN_FILE,
                        reportUrl = reportUrl,
                        error = null,
                    )
                }
                val error = ScanParsers.errorFor(response.code)
                if (error != null) {
                    return@withContext ServiceResult(
                        service = ScanService.HYBRID_ANALYSIS,
                        verdict = ScanVerdict.UNKNOWN,
                        reportUrl = reportUrl,
                        error = error,
                    )
                }
                ScanParsers.haOverview(body, reportUrl) ?: ServiceResult(
                    service = ScanService.HYBRID_ANALYSIS,
                    verdict = ScanVerdict.UNKNOWN,
                    reportUrl = reportUrl,
                    error = ScanError.OTHER,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            ServiceResult(
                service = ScanService.HYBRID_ANALYSIS,
                verdict = ScanVerdict.UNKNOWN,
                reportUrl = reportUrl,
                error = ScanError.OFFLINE,
            )
        }
    }

    override suspend fun lookupLink(url: String): ServiceResult = withContext(Dispatchers.IO) {
        val reportUrl = ScanLinks.linkReport(ScanService.HYBRID_ANALYSIS, url)
        try {
            val formBody = FormBody.Builder()
                .add("url", url)
                .add("scan_type", "all")
                .build()

            val postRequest = newRequestBuilder("https://hybrid-analysis.com/api/v2/quick-scan/url")
                .post(formBody)
                .build()

            val scanId = client.newCall(postRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val error = ScanParsers.errorFor(response.code)
                if (error != null) {
                    return@withContext ServiceResult(
                        service = ScanService.HYBRID_ANALYSIS,
                        verdict = ScanVerdict.UNKNOWN,
                        reportUrl = reportUrl,
                        error = error,
                    )
                }
                ScanParsers.haQuickScanId(body)
            }

            if (scanId == null) {
                return@withContext ServiceResult(
                    service = ScanService.HYBRID_ANALYSIS,
                    verdict = ScanVerdict.UNKNOWN,
                    reportUrl = reportUrl,
                    error = ScanError.OTHER,
                )
            }

            val startTime = clock()
            while (clock() - startTime < 60_000L) {
                delay(5_000L)
                val pollReq = newRequestBuilder("https://hybrid-analysis.com/api/v2/quick-scan/$scanId")
                    .get()
                    .build()

                val result = client.newCall(pollReq).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val error = ScanParsers.errorFor(response.code)
                    if (error != null) {
                        return@withContext ServiceResult(
                            service = ScanService.HYBRID_ANALYSIS,
                            verdict = ScanVerdict.UNKNOWN,
                            reportUrl = reportUrl,
                            error = error,
                        )
                    }
                    ScanParsers.haQuickScan(body, reportUrl)
                }
                if (result != null) {
                    return@withContext result
                }
            }

            ServiceResult(
                service = ScanService.HYBRID_ANALYSIS,
                verdict = ScanVerdict.UNKNOWN,
                detail = "Still being checked. Try again in a few minutes.",
                reportUrl = reportUrl,
                error = null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            ServiceResult(
                service = ScanService.HYBRID_ANALYSIS,
                verdict = ScanVerdict.UNKNOWN,
                reportUrl = reportUrl,
                error = ScanError.OFFLINE,
            )
        }
    }

    override suspend fun upload(
        fileName: String,
        size: Long,
        open: () -> InputStream,
    ): PendingAnalysis = withContext(Dispatchers.IO) {
        if (size > maxUploadBytes) {
            throw ScanException(ScanError.TOO_BIG)
        }

        try {
            val fileBody = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength(): Long = size
                override fun writeTo(sink: BufferedSink) {
                    open().use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            sink.write(buffer, 0, read)
                        }
                    }
                }
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("scan_type", "all")
                .addFormDataPart("file", fileName, fileBody)
                .build()

            val request = newRequestBuilder("https://hybrid-analysis.com/api/v2/quick-scan/file")
                .post(multipartBody)
                .build()

            val analysisId = client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val err = ScanParsers.errorFor(resp.code)
                if (err != null) throw ScanException(err)
                ScanParsers.haQuickScanId(body) ?: throw ScanException(ScanError.OTHER)
            }

            PendingAnalysis(ScanService.HYBRID_ANALYSIS, analysisId, clock())
        } catch (e: CancellationException) {
            throw e
        } catch (e: ScanException) {
            throw e
        } catch (_: IOException) {
            throw ScanException(ScanError.OFFLINE)
        } catch (_: Throwable) {
            throw ScanException(ScanError.OTHER)
        }
    }

    override suspend fun poll(
        pending: PendingAnalysis,
        sha256: String?,
    ): ServiceResult? = withContext(Dispatchers.IO) {
        val reportUrl = sha256?.let { ScanLinks.fileReport(ScanService.HYBRID_ANALYSIS, it) }
            ?: "https://hybrid-analysis.com/"
        try {
            val request = newRequestBuilder("https://hybrid-analysis.com/api/v2/quick-scan/${pending.analysisId}")
                .get()
                .build()

            val scanResult = client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val error = ScanParsers.errorFor(response.code)
                if (error != null) {
                    return@withContext ServiceResult(
                        service = ScanService.HYBRID_ANALYSIS,
                        verdict = ScanVerdict.UNKNOWN,
                        reportUrl = reportUrl,
                        error = error,
                    )
                }
                ScanParsers.haQuickScan(body, reportUrl)
            }

            if (scanResult != null && sha256 != null) {
                val fileRes = lookupFile(sha256)
                if (fileRes.verdict != ScanVerdict.UNKNOWN) {
                    return@withContext fileRes
                }
            }
            scanResult
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            ServiceResult(
                service = ScanService.HYBRID_ANALYSIS,
                verdict = ScanVerdict.UNKNOWN,
                reportUrl = reportUrl,
                error = ScanError.OFFLINE,
            )
        }
    }
}
