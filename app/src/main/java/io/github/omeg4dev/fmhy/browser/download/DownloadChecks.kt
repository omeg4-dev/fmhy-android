package io.github.omeg4dev.fmhy.browser.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.omeg4dev.fmhy.browser.BrowserActivity
import io.github.omeg4dev.fmhy.data.CheckPrefs
import io.github.omeg4dev.fmhy.data.SettingsRepository
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

sealed interface CheckSource {
    val fileName: String
    val size: Long
    fun open(): InputStream

    data class OfFile(val file: File, override val fileName: String) : CheckSource {
        override val size: Long get() = file.length()
        override fun open(): InputStream = file.inputStream()
    }

    data class OfUri(
        val context: Context,
        val uri: Uri,
        override val fileName: String,
        override val size: Long,
    ) : CheckSource {
        override fun open(): InputStream = context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Cannot open stream for $uri")
    }
}

class DownloadChecks(
    private val context: Context,
    private val scope: CoroutineScope,
    baseClient: OkHttpClient,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val prefs: StateFlow<CheckPrefs> = settings.checkPrefs.stateIn(
        scope,
        SharingStarted.Eagerly,
        CheckPrefs(),
    )

    private val vtClient by lazy {
        VirusTotalClient(
            baseClient = baseClient,
            apiKey = { prefs.value.virusTotalKey },
            clock = clock,
        )
    }

    private val haClient by lazy {
        HybridAnalysisClient(
            baseClient = baseClient,
            apiKey = { prefs.value.hybridKey },
            clock = clock,
        )
    }

    fun client(service: ScanService): ScanClient = when (service) {
        ScanService.VIRUSTOTAL -> vtClient
        ScanService.HYBRID_ANALYSIS -> haClient
    }

    suspend fun checkLink(url: String, services: List<ScanService>): ScanResult {
        val results = mutableListOf<ServiceResult>()
        for (service in services) {
            results.add(guarded(service) { client(service).lookupLink(url) } ?: failed(service, ScanError.OTHER))
        }
        return ScanResult(
            kind = ScanKind.LINK,
            sha256 = null,
            services = results,
            checkedAt = clock(),
        )
    }

    suspend fun fingerprint(source: CheckSource, onProgress: (Long) -> Unit = {}): String = withContext(Dispatchers.IO) {
        source.open().use { stream ->
            FileHash.sha256(
                input = stream,
                totalBytes = source.size,
                onProgress = onProgress,
                isActive = { coroutineContext.isActive },
            )
        }
    }

    suspend fun checkFile(sha256: String, services: List<ScanService>): ScanResult {
        val results = mutableListOf<ServiceResult>()
        for (service in services) {
            results.add(guarded(service) { client(service).lookupFile(sha256) } ?: failed(service, ScanError.OTHER))
        }
        return ScanResult(
            kind = ScanKind.FILE,
            sha256 = sha256,
            services = results,
            checkedAt = clock(),
        )
    }

    suspend fun upload(
        source: CheckSource,
        sha256: String,
        service: ScanService,
        previous: ScanResult?,
    ): ScanResult {
        val existingServices = previous?.services?.filter { it.service != service } ?: emptyList()
        val existingPending = previous?.pending?.filter { it.service != service } ?: emptyList()
        val kind = previous?.kind ?: ScanKind.FILE

        return try {
            val pendingAnalysis = client(service).upload(
                fileName = source.fileName,
                size = source.size,
                open = source::open,
            )
            ScanResult(
                kind = kind,
                sha256 = sha256,
                services = existingServices,
                checkedAt = clock(),
                pending = existingPending + pendingAnalysis,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errResult = ServiceResult(
                service = service,
                verdict = ScanVerdict.UNKNOWN,
                error = errorOf(e),
            )
            ScanResult(
                kind = kind,
                sha256 = sha256,
                services = existingServices + errResult,
                checkedAt = clock(),
                pending = existingPending,
            )
        }
    }

    suspend fun awaitPending(result: ScanResult): ScanResult {
        val maxWaitMs = 20 * 60 * 1000L
        val start = clock()
        var current = result

        while (current.pending.isNotEmpty()) {
            val remainingPending = mutableListOf<PendingAnalysis>()
            val updatedServices = current.services.toMutableList()

            for (pending in current.pending) {
                if (clock() - start >= maxWaitMs) {
                    updatedServices.removeAll { it.service == pending.service }
                    updatedServices.add(
                        ServiceResult(
                            service = pending.service,
                            verdict = ScanVerdict.UNKNOWN,
                            detail = "Analysis is taking longer than usual. Check again later.",
                            reportUrl = current.sha256?.let { ScanLinks.fileReport(pending.service, it) },
                            error = null,
                        ),
                    )
                } else {
                    val pollResult = guarded(pending.service) { client(pending.service).poll(pending, current.sha256) }
                    if (pollResult != null) {
                        updatedServices.removeAll { it.service == pending.service }
                        updatedServices.add(pollResult)
                    } else {
                        remainingPending.add(pending)
                    }
                }
            }

            current = current.copy(
                services = updatedServices,
                checkedAt = clock(),
                pending = remainingPending,
            )

            if (current.pending.isNotEmpty()) {
                if (clock() - start >= maxWaitMs) {
                    continue
                }
                delay(30_000L)
            }
        }
        return current
    }

    /** Clients map HTTP and network failures themselves; anything else must not crash the app. */
    private suspend fun guarded(service: ScanService, call: suspend () -> ServiceResult?): ServiceResult? = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        failed(service, errorOf(e))
    }

    private fun failed(service: ScanService, error: ScanError) =
        ServiceResult(service = service, verdict = ScanVerdict.UNKNOWN, error = error)

    // A key with characters that can't go into an HTTP header makes OkHttp throw IllegalArgumentException.
    private fun errorOf(e: Exception): ScanError = when (e) {
        is ScanException -> e.error
        is IllegalArgumentException -> ScanError.BAD_KEY
        else -> ScanError.OTHER
    }

    fun notifyAnalysisDone(id: Int, fileName: String?, result: ScanResult) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (!nm.areNotificationsEnabled()) return
            nm.createNotificationChannel(
                NotificationChannel(
                    "file_checks",
                    "File checks",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            val open = PendingIntent.getActivity(
                context,
                id,
                Intent(context, BrowserActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra(DownloadService.EXTRA_OPEN_DOWNLOADS, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = Notification.Builder(context, "file_checks")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Analysis finished")
                .setContentText("${fileName ?: "File"}: ${result.verdict.label}")
                .setContentIntent(open)
                .setAutoCancel(true)
            nm.notify(id, builder.build())
        } catch (_: SecurityException) {
        } catch (_: Throwable) {
        }
    }

    fun trackInBackground(result: ScanResult, fileName: String?) {
        scope.launch {
            val finalResult = awaitPending(result)
            notifyAnalysisDone(7900, fileName, finalResult)
        }
    }
}
