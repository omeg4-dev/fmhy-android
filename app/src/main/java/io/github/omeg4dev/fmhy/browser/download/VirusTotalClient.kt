package io.github.omeg4dev.fmhy.browser.download

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

class VirusTotalClient(
    baseClient: OkHttpClient,
    private val apiKey: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ScanClient {

    override val service: ScanService = ScanService.VIRUSTOTAL
    override val maxUploadBytes: Long = 650L * 1024 * 1024

    private val client: OkHttpClient = baseClient.newBuilder()
        .cache(null)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val rateMutex = Mutex()
    private var lastRequestTime = 0L

    private suspend fun waitRateLimit() {
        rateMutex.withLock {
            val now = clock()
            val elapsed = now - lastRequestTime
            if (lastRequestTime > 0L && elapsed < 15_000L) {
                delay(15_000L - elapsed)
            }
            lastRequestTime = clock()
        }
    }

    private fun newRequestBuilder(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("x-apikey", apiKey().trim())
            .header("Accept", "application/json")
    }

    override suspend fun lookupFile(sha256: String): ServiceResult = withContext(Dispatchers.IO) {
        val reportUrl = ScanLinks.fileReport(ScanService.VIRUSTOTAL, sha256)
        try {
            waitRateLimit()
            val request = newRequestBuilder("https://www.virustotal.com/api/v3/files/$sha256")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 404) {
                    return@withContext ServiceResult(
                        service = ScanService.VIRUSTOTAL,
                        verdict = ScanVerdict.UNKNOWN,
                        detail = ScanText.UNKNOWN_FILE,
                        reportUrl = reportUrl,
                        error = null,
                    )
                }
                val error = ScanParsers.errorFor(response.code)
                if (error != null) {
                    return@withContext ServiceResult(
                        service = ScanService.VIRUSTOTAL,
                        verdict = ScanVerdict.UNKNOWN,
                        reportUrl = reportUrl,
                        error = error,
                    )
                }
                ScanParsers.vtReport(body, reportUrl) ?: ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.UNKNOWN,
                    reportUrl = reportUrl,
                    error = ScanError.OTHER,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            ServiceResult(
                service = ScanService.VIRUSTOTAL,
                verdict = ScanVerdict.UNKNOWN,
                reportUrl = reportUrl,
                error = ScanError.OFFLINE,
            )
        }
    }

    override suspend fun lookupLink(url: String): ServiceResult = withContext(Dispatchers.IO) {
        val reportUrl = ScanLinks.linkReport(ScanService.VIRUSTOTAL, url)
        try {
            waitRateLimit()
            val id = ScanLinks.vtUrlId(url)
            val request = newRequestBuilder("https://www.virustotal.com/api/v3/urls/$id")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 404) {
                    return@withContext ServiceResult(
                        service = ScanService.VIRUSTOTAL,
                        verdict = ScanVerdict.UNKNOWN,
                        detail = "Nobody has scanned this link yet.",
                        reportUrl = reportUrl,
                        error = null,
                    )
                }
                val error = ScanParsers.errorFor(response.code)
                if (error != null) {
                    return@withContext ServiceResult(
                        service = ScanService.VIRUSTOTAL,
                        verdict = ScanVerdict.UNKNOWN,
                        reportUrl = reportUrl,
                        error = error,
                    )
                }
                ScanParsers.vtReport(body, reportUrl) ?: ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.UNKNOWN,
                    reportUrl = reportUrl,
                    error = ScanError.OTHER,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            ServiceResult(
                service = ScanService.VIRUSTOTAL,
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
            val uploadTargetUrl: String
            if (size <= 32L * 1024 * 1024) {
                uploadTargetUrl = "https://www.virustotal.com/api/v3/files"
            } else {
                waitRateLimit()
                val urlReq = newRequestBuilder("https://www.virustotal.com/api/v3/files/upload_url")
                    .get()
                    .build()
                val urlBody = client.newCall(urlReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val err = ScanParsers.errorFor(resp.code)
                    if (err != null) throw ScanException(err)
                    body
                }
                uploadTargetUrl = ScanParsers.vtUploadUrl(urlBody)
                    ?: throw ScanException(ScanError.OTHER)
            }

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
                .addFormDataPart("file", fileName, fileBody)
                .build()

            waitRateLimit()
            val postReq = newRequestBuilder(uploadTargetUrl)
                .post(multipartBody)
                .build()

            val analysisId = client.newCall(postReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val err = ScanParsers.errorFor(resp.code)
                if (err != null) throw ScanException(err)
                ScanParsers.vtAnalysisId(body) ?: throw ScanException(ScanError.OTHER)
            }

            PendingAnalysis(ScanService.VIRUSTOTAL, analysisId, clock())
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
        val reportUrl = sha256?.let { ScanLinks.fileReport(ScanService.VIRUSTOTAL, it) }
            ?: "https://www.virustotal.com/gui/home/upload"
        try {
            waitRateLimit()
            val request = newRequestBuilder("https://www.virustotal.com/api/v3/analyses/${pending.analysisId}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val error = ScanParsers.errorFor(response.code)
                if (error != null) {
                    return@withContext ServiceResult(
                        service = ScanService.VIRUSTOTAL,
                        verdict = ScanVerdict.UNKNOWN,
                        reportUrl = reportUrl,
                        error = error,
                    )
                }
                ScanParsers.vtAnalysis(body, reportUrl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            ServiceResult(
                service = ScanService.VIRUSTOTAL,
                verdict = ScanVerdict.UNKNOWN,
                reportUrl = reportUrl,
                error = ScanError.OFFLINE,
            )
        }
    }
}
