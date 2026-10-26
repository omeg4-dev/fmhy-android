package io.github.omeg4dev.fmhy.browser.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.user.DownloadRecord
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.work.NetworkKind
import io.github.omeg4dev.fmhy.work.NetworkMonitor
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

sealed interface DownloadEvent {
    data object KeyPromptNeeded : DownloadEvent
    data class RedirectWarning(val id: Long, val fromHost: String, val flag: HopFlag) : DownloadEvent
}

data class NewDownload(
    val url: String,
    val fileName: String,
    val mimeType: String = "",
    val totalBytes: Long = -1,
    val sourceHost: String = "",
    val referer: String? = null,
    val userAgent: String? = null,
    val isPrivate: Boolean = false,
    val allowMobile: Boolean = false,
    val fmhy: DownloadSourceStatus? = null,
)

class DownloadController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val userStore: UserStore,
    baseClient: OkHttpClient,
    private val networkMonitor: NetworkMonitor,
    private val settings: SettingsRepository,
    private val checks: DownloadChecks,
    private val storage: DownloadStorage = DownloadStorage(context, settings),
    private val clock: () -> Long = System::currentTimeMillis,
    private val hopChecker: HopChecker = HopChecker { null },
) {
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    val activeSummary: StateFlow<ActiveSummary?> = items
        .map(::activeSummary)
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    // Replayed so a prompt raised while no screen is collecting is still shown later.
    private val _events = MutableSharedFlow<DownloadEvent>(replay = 1, extraBufferCapacity = 4)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    private val checkJobs = ConcurrentHashMap<Long, Job>()

    private val client: OkHttpClient = baseClient.newBuilder()
        .cache(null)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val allowedHosts = ConcurrentHashMap<Long, MutableSet<String>>()
    private val pendingFlags = ConcurrentHashMap<Long, HopFlag>()

    private val privateIdCounter = AtomicLong(-1L)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val rescheduleMutex = Mutex()

    private var wifiOnly = false
    private var network: NetworkKind = NetworkKind.NONE

    private val itemComparator = compareByDescending<DownloadItem> { it.createdAt }.thenByDescending { it.id }

    fun start() {
        scope.launch {
            combine(
                networkMonitor.kind,
                settings.dataUsagePrefs.map { it.downloadWifiOnly }.distinctUntilChanged(),
            ) { net, wifi ->
                Pair(net, wifi)
            }.collect { (net, wifi) ->
                network = net
                wifiOnly = wifi
                reschedule()
            }
        }
        scope.launch {
            restore()
        }
    }

    private suspend fun restore() {
        val records = withContext(Dispatchers.IO) {
            try {
                userStore.downloads()
            } catch (_: Throwable) {
                emptyList()
            }
        }
        val changedRows = mutableListOf<DownloadItem>()
        val loadedItems = records.map { record ->
            val item = record.toItem()
            val mappedStatus = CheckPolicy.restoredStatus(item.status)
            if (mappedStatus != item.status) {
                val updated = item.copy(status = mappedStatus)
                changedRows.add(updated)
                updated
            } else {
                item
            }
        }.sortedWith(itemComparator)

        _items.value = loadedItems

        val keepIds = loadedItems.filter { it.status != DownloadStatus.FINISHED }.map { it.id }.toSet()
        storage.cleanOrphanParts(keepIds)

        for (changed in changedRows) {
            persist(changed)
        }

        reschedule()
        ensureService()

        for (item in loadedItems) {
            if (!item.isPrivate && item.check?.isRunning == true) {
                val check = item.check
                val id = item.id
                val fileName = item.fileName
                val job = scope.launch(Dispatchers.IO) {
                    try {
                        val final = checks.awaitPending(check)
                        val updated = edit(id) { it.copy(check = final) }
                        updated?.let { persist(it) }
                        checks.notifyAnalysisDone(notificationId(id), fileName, final)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                    } finally {
                        checkJobs.remove(id)
                    }
                }
                checkJobs[id] = job
            }
        }
    }

    suspend fun targetLabel(): String = storage.targetLabel()

    suspend fun enqueue(req: NewDownload): Long {
        val createdAt = clock()
        val folderLabel = storage.targetLabel()
        val id = if (req.isPrivate) {
            privateIdCounter.getAndDecrement()
        } else {
            withContext(Dispatchers.IO) {
                try {
                    userStore.insertDownload(
                        DownloadRecord(
                            url = req.url,
                            fileName = req.fileName,
                            mimeType = req.mimeType,
                            sourceHost = req.sourceHost,
                            referer = req.referer,
                            userAgent = req.userAgent,
                            totalBytes = req.totalBytes,
                            downloadedBytes = 0L,
                            status = DownloadStatus.WAITING.name,
                            resumable = true,
                            restartNote = false,
                            allowMobile = req.allowMobile,
                            error = null,
                            fileUri = null,
                            folderLabel = folderLabel,
                            etag = null,
                            lastModified = null,
                            createdAt = createdAt,
                            finishedAt = null,
                            fmhyJson = req.fmhy?.toJson(),
                        )
                    )
                } catch (_: Throwable) {
                    clock()
                }
            }
        }

        val item = DownloadItem(
            id = id,
            url = req.url,
            fileName = req.fileName,
            mimeType = req.mimeType,
            sourceHost = req.sourceHost,
            referer = req.referer,
            userAgent = req.userAgent,
            totalBytes = req.totalBytes,
            downloadedBytes = 0L,
            status = DownloadStatus.WAITING,
            resumable = true,
            restartNote = false,
            allowMobile = req.allowMobile,
            isPrivate = req.isPrivate,
            error = null,
            fileUri = null,
            folderLabel = folderLabel,
            etag = null,
            lastModified = null,
            createdAt = createdAt,
            finishedAt = null,
            speedBps = null,
            etaSeconds = null,
            fmhy = req.fmhy,
        )

        _items.update { list ->
            (listOf(item) + list).sortedWith(itemComparator)
        }

        reschedule()
        ensureService()
        return id
    }

    private fun reschedule() {
        scope.launch {
            rescheduleMutex.withLock {
                val transitions = DownloadQueue.transitions(_items.value, wifiOnly, network)
                for (t in transitions) {
                    if (t.to == DownloadStatus.RUNNING) {
                        val newItem = edit(t.id) { it.copy(status = DownloadStatus.RUNNING) } ?: continue
                        persist(newItem)
                        jobs[t.id]?.cancel()
                        jobs[t.id] = scope.launch(Dispatchers.IO) {
                            transfer(t.id)
                        }
                    } else {
                        jobs.remove(t.id)?.cancel()
                        val newItem = edit(t.id) { it.copy(status = t.to, speedBps = null, etaSeconds = null) } ?: continue
                        persist(newItem)
                    }
                }
                ensureService()
            }
        }
    }

    fun pause(id: Long) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (!item.status.isActive) return
        jobs.remove(id)?.cancel()
        val paused = edit(id) { it.copy(status = DownloadStatus.PAUSED, speedBps = null, etaSeconds = null) }
        paused?.let { persist(it) }
        reschedule()
    }

    fun resume(id: Long) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadStatus.PAUSED) return
        val waiting = edit(id) { it.copy(status = DownloadStatus.WAITING) }
        waiting?.let { persist(it) }
        reschedule()
    }

    fun continueAfterRedirectWarning(id: Long) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadStatus.AWAITING_DECISION) return
        val flag = pendingFlags.remove(id)
        if (flag != null) {
            allowedHosts.computeIfAbsent(id) { ConcurrentHashMap.newKeySet() }.add(flag.host)
        }
        val waiting = edit(id) { it.copy(status = DownloadStatus.WAITING) }
        waiting?.let { persist(it) }
        reschedule()
    }

    fun cancel(id: Long) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        checkJobs.remove(id)?.cancel()
        jobs.remove(id)?.cancel()
        allowedHosts.remove(id)
        pendingFlags.remove(id)
        storage.partFile(id).delete()
        _items.update { list -> list.filterNot { it.id == id } }
        if (!item.isPrivate) {
            scope.launch(Dispatchers.IO) {
                try {
                    userStore.deleteDownload(id)
                } catch (_: Throwable) {}
            }
        }
        reschedule()
        ensureService()
    }

    fun retry(id: Long) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadStatus.FAILED) return
        val retried = DownloadQueue.retry(item)
        if (retried.downloadedBytes == 0L && item.downloadedBytes > 0L) {
            storage.partFile(id).delete()
        }
        edit(id) { retried }
        persist(retried)
        reschedule()
    }

    fun pauseAll() {
        val active = _items.value.filter { it.status.isActive }
        for (item in active) {
            jobs.remove(item.id)?.cancel()
            val paused = edit(item.id) { it.copy(status = DownloadStatus.PAUSED, speedBps = null, etaSeconds = null) }
            paused?.let { persist(it) }
        }
        ensureService()
    }

    fun cancelAll() {
        val active = _items.value.filter { it.status.isActive }
        for (item in active) {
            checkJobs.remove(item.id)?.cancel()
            jobs.remove(item.id)?.cancel()
            allowedHosts.remove(item.id)
            pendingFlags.remove(item.id)
            storage.partFile(item.id).delete()
            _items.update { list -> list.filterNot { it.id == item.id } }
            if (!item.isPrivate) {
                scope.launch(Dispatchers.IO) {
                    try {
                        userStore.deleteDownload(item.id)
                    } catch (_: Throwable) {}
                }
            }
        }
        reschedule()
        ensureService()
    }

    fun removeFromList(id: Long) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        checkJobs.remove(id)?.cancel()
        allowedHosts.remove(id)
        pendingFlags.remove(id)
        _items.update { list -> list.filterNot { it.id == id } }
        if (!item.isPrivate) {
            scope.launch(Dispatchers.IO) {
                try {
                    userStore.deleteDownload(id)
                } catch (_: Throwable) {}
            }
        }
    }

    fun clearFinished() {
        _items.update { list -> list.filterNot { it.status == DownloadStatus.FINISHED } }
        scope.launch(Dispatchers.IO) {
            try {
                userStore.deleteDownloadsWithStatus(DownloadStatus.FINISHED.name)
            } catch (_: Throwable) {}
        }
    }

    fun deleteFile(id: Long) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadStatus.FINISHED) return
        checkJobs.remove(id)?.cancel()
        val uri = item.fileUri
        if (uri != null) {
            val deleted = storage.delete(uri)
            if (!deleted) {
                _notices.tryEmit("Couldn't delete the file")
                return
            }
        }
        removeFromList(id)
    }

    /** Call once the key prompt was shown: it is never shown again and the replayed event is dropped. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun keyPromptShown() {
        _events.resetReplayCache()
        scope.launch {
            try {
                settings.updateCheckPrefs { it.copy(keyPromptShown = true) }
            } catch (_: Throwable) {}
        }
    }

    /** FINISHED entry: fingerprint the saved file and look it up (never uploads). */
    fun checkFile(id: Long, services: List<ScanService>) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadStatus.FINISHED) return
        if (item.checking) return
        val uriStr = item.fileUri
        if (uriStr.isNullOrBlank()) {
            _notices.tryEmit("File not found")
            return
        }
        val uri = try {
            Uri.parse(uriStr)
        } catch (_: Exception) {
            _notices.tryEmit("File not found")
            return
        }
        edit(id) { it.copy(checking = true) }
        val job = scope.launch(Dispatchers.IO) {
            try {
                val source = CheckSource.OfUri(context, uri, item.fileName, item.totalBytes)
                val sha = checks.fingerprint(source)
                val result = checks.checkFile(sha, services)
                edit(id) { it.copy(check = result, checking = false) }?.let { persist(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FileNotFoundException) {
                edit(id) { it.copy(checking = false) }
                _notices.tryEmit("File not found")
            } catch (e: Exception) {
                edit(id) { it.copy(checking = false) }
                _notices.tryEmit("Couldn't check the file")
            } finally {
                checkJobs.remove(id)
                edit(id) { if (it.checking) it.copy(checking = false) else it }
            }
        }
        checkJobs[id] = job
    }

    /** AWAITING_DECISION entry: fingerprint the held part file and look it up again. */
    fun recheckHeld(id: Long, services: List<ScanService>) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadStatus.AWAITING_DECISION) return
        runHeldCheck(id, services, transitionToAwaitingDecision = false)
    }

    private fun runHeldCheck(id: Long, services: List<ScanService>, transitionToAwaitingDecision: Boolean) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.checking) return
        val part = storage.partFile(id)
        if (!part.exists()) {
            if (transitionToAwaitingDecision) {
                edit(id) { it.copy(status = DownloadStatus.AWAITING_DECISION, checking = false) }?.let { persist(it) }
            }
            _notices.tryEmit("File not found")
            return
        }
        edit(id) { it.copy(checking = true) }
        val job = scope.launch(Dispatchers.IO) {
            try {
                val source = CheckSource.OfFile(part, item.fileName)
                val sha = checks.fingerprint(source)
                val result = checks.checkFile(sha, services)
                val targetStatus = if (transitionToAwaitingDecision) DownloadStatus.AWAITING_DECISION else null
                edit(id) { current ->
                    current.copy(
                        status = targetStatus ?: current.status,
                        check = result,
                        checking = false,
                    )
                }?.let { persist(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FileNotFoundException) {
                val targetStatus = if (transitionToAwaitingDecision) DownloadStatus.AWAITING_DECISION else null
                edit(id) { current ->
                    current.copy(
                        status = targetStatus ?: current.status,
                        checking = false,
                    )
                }?.let { persist(it) }
                _notices.tryEmit("File not found")
            } catch (e: Exception) {
                val targetStatus = if (transitionToAwaitingDecision) DownloadStatus.AWAITING_DECISION else null
                edit(id) { current ->
                    current.copy(
                        status = targetStatus ?: current.status,
                        checking = false,
                    )
                }?.let { persist(it) }
                _notices.tryEmit("Couldn't check the file")
            } finally {
                checkJobs.remove(id)
                edit(id) { if (it.checking) it.copy(checking = false) else it }
            }
        }
        checkJobs[id] = job
    }

    /** Fingerprint only (for "open the website" without a key). Returns the stored sha256 if the entry has one. */
    suspend fun fingerprintOf(id: Long): String? = withContext(Dispatchers.IO) {
        val item = _items.value.firstOrNull { it.id == id } ?: return@withContext null
        item.check?.sha256?.let { return@withContext it }
        val source = when (item.status) {
            DownloadStatus.FINISHED -> {
                val uriStr = item.fileUri ?: return@withContext null
                val uri = try { Uri.parse(uriStr) } catch (_: Exception) { return@withContext null }
                CheckSource.OfUri(context, uri, item.fileName, item.totalBytes)
            }
            DownloadStatus.AWAITING_DECISION, DownloadStatus.CHECKING -> {
                val part = storage.partFile(id)
                if (!part.exists()) return@withContext null
                CheckSource.OfFile(part, item.fileName)
            }
            else -> return@withContext null
        }
        try {
            checks.fingerprint(source)
        } catch (_: Exception) {
            null
        }
    }

    /** Upload after the user agreed; FINISHED (saved file) or AWAITING_DECISION (part file). Then waits for the analysis in the background and notifies. */
    fun upload(id: Long, service: ScanService) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadStatus.FINISHED && item.status != DownloadStatus.AWAITING_DECISION) return
        val source = when (item.status) {
            DownloadStatus.FINISHED -> {
                val uriStr = item.fileUri
                if (uriStr.isNullOrBlank()) {
                    _notices.tryEmit("File not found")
                    return
                }
                val uri = try { Uri.parse(uriStr) } catch (_: Exception) {
                    _notices.tryEmit("File not found")
                    return
                }
                CheckSource.OfUri(context, uri, item.fileName, item.totalBytes)
            }
            DownloadStatus.AWAITING_DECISION -> {
                val part = storage.partFile(id)
                if (!part.exists()) {
                    _notices.tryEmit("File not found")
                    return
                }
                CheckSource.OfFile(part, item.fileName)
            }
            else -> return
        }
        val job = scope.launch(Dispatchers.IO) {
            try {
                val sha = item.check?.sha256 ?: checks.fingerprint(source)
                val result = checks.upload(source, sha, service, item.check)
                val updated = edit(id) { it.copy(check = result) }
                updated?.let { persist(it) }
                if (result.isRunning) {
                    val final = checks.awaitPending(result)
                    val finalUpdated = edit(id) { it.copy(check = final) }
                    finalUpdated?.let { persist(it) }
                    val notifName = if (item.isPrivate) null else item.fileName
                    checks.notifyAnalysisDone(notificationId(id), notifName, final)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FileNotFoundException) {
                _notices.tryEmit("File not found")
            } catch (e: Exception) {
                _notices.tryEmit("Couldn't check the file")
            } finally {
                checkJobs.remove(id)
            }
        }
        checkJobs[id] = job
    }

    /** AWAITING_DECISION → move the held file to the download folder → FINISHED (keeps `check`). */
    fun keep(id: Long) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadStatus.AWAITING_DECISION) return
        val part = storage.partFile(id)
        if (!part.exists()) {
            _notices.tryEmit("File not found")
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val saved = storage.finalize(part, item.fileName, item.mimeType, onFallback = ::onFolderFallback)
                val finishedItem = edit(id) {
                    it.copy(
                        status = DownloadStatus.FINISHED,
                        fileUri = saved.uri,
                        fileName = saved.displayName,
                        totalBytes = it.downloadedBytes,
                        finishedAt = clock(),
                        speedBps = null,
                        etaSeconds = null,
                        error = null,
                    )
                } ?: return@launch
                persist(finishedItem)
            } catch (e: IOException) {
                _notices.tryEmit("Couldn't save the file")
            }
        }
    }

    /** AWAITING_DECISION or CHECKING → delete the held part file and remove the entry. */
    fun discard(id: Long) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadStatus.AWAITING_DECISION && item.status != DownloadStatus.CHECKING) return
        checkJobs.remove(id)?.cancel()
        jobs.remove(id)?.cancel()
        storage.partFile(id).delete()
        _items.update { list -> list.filterNot { it.id == id } }
        if (!item.isPrivate) {
            scope.launch(Dispatchers.IO) {
                try {
                    userStore.deleteDownload(id)
                } catch (_: Throwable) {}
            }
        }
        reschedule()
        ensureService()
    }

    private fun notificationId(id: Long): Int = 7600 + (abs(id) % 1000).toInt()

    fun fileExists(item: DownloadItem): Boolean = item.fileUri?.let(storage::exists) == true

    fun openIntent(item: DownloadItem): Intent? {
        val uriStr = item.fileUri ?: return null
        val uri = Uri.parse(uriStr)
        val mime = item.mimeType.ifBlank { context.contentResolver.getType(uri) } ?: "*/*"
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun shareIntent(item: DownloadItem): Intent? {
        val uriStr = item.fileUri ?: return null
        val uri = Uri.parse(uriStr)
        val mime = item.mimeType.ifBlank { context.contentResolver.getType(uri) } ?: "*/*"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun installIntent(item: DownloadItem): Intent? {
        val uriStr = item.fileUri ?: return null
        val uri = Uri.parse(uriStr)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun ensureService() {
        if (_items.value.any { it.status.isActive }) {
            DownloadService.start(context)
        }
    }

    private fun edit(id: Long, transform: (DownloadItem) -> DownloadItem): DownloadItem? {
        var updatedItem: DownloadItem? = null
        _items.update { list ->
            list.map { item ->
                if (item.id == id) {
                    val transformed = transform(item)
                    updatedItem = transformed
                    transformed
                } else {
                    item
                }
            }.sortedWith(itemComparator)
        }
        return updatedItem
    }

    private fun persist(item: DownloadItem) {
        if (item.isPrivate) return
        scope.launch(Dispatchers.IO) {
            try {
                userStore.updateDownload(item.toRecord())
            } catch (_: Throwable) {}
        }
    }

    private fun onFolderFallback() {
        scope.launch {
            try {
                val prefs = settings.downloadPrefs.first()
                if (!prefs.folderFallbackNotified) {
                    settings.updateDownloadPrefs { it.copy(folderFallbackNotified = true) }
                    _notices.emit("Download folder isn't available. Saving to Downloads instead.")
                }
            } catch (_: Throwable) {}
        }
    }

    private suspend fun transfer(id: Long) {
        var attempt = 0
        var lastHttpError: String? = null

        while (attempt <= 3) {
            val currentItem = _items.value.firstOrNull { it.id == id } ?: return
            if (currentItem.status != DownloadStatus.RUNNING) return

            val part = storage.partFile(id)
            val offset = if (currentItem.resumable && part.exists()) part.length() else 0L
            if (offset == 0L && part.exists()) {
                part.delete()
            }

            var currentUrlString = currentItem.url
            val parsedUrl = currentUrlString.toHttpUrlOrNull()
            if (parsedUrl == null) {
                val failedItem = edit(id) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        error = "Invalid URL",
                        speedBps = null,
                        etaSeconds = null,
                    )
                }
                failedItem?.let { persist(it) }
                jobs.remove(id)
                reschedule()
                return
            }
            var currentHttpUrl: HttpUrl = parsedUrl

            var currentCall: Call? = null
            val job = coroutineContext.job
            val handle = job.invokeOnCompletion {
                currentCall?.cancel()
            }

            var shouldRetry = false
            var finalizePart = false
            var updatedItem = currentItem

            try {
                var hops = 0
                var finalResponse: Response? = null

                while (true) {
                    val cookies = try {
                        android.webkit.CookieManager.getInstance().getCookie(currentUrlString)
                    } catch (_: Throwable) {
                        null
                    }

                    val headers = DownloadRequest.headers(
                        userAgent = currentItem.userAgent,
                        cookies = cookies,
                        referer = currentItem.referer,
                        offset = offset,
                        etag = currentItem.etag,
                        lastModified = currentItem.lastModified,
                    )

                    val requestBuilder = Request.Builder().url(currentHttpUrl)
                    for ((k, v) in headers) {
                        requestBuilder.header(k, v)
                    }
                    val request = requestBuilder.build()
                    val call = client.newCall(request)
                    currentCall = call

                    val resp = call.execute()
                    val nextUrl = RedirectPath.nextUrl(currentHttpUrl, resp.header("Location"), resp.code)
                    if (nextUrl != null) {
                        resp.close()
                        hops++
                        if (hops > RedirectPath.MAX_HOPS) {
                            val failedItem = edit(id) {
                                it.copy(
                                    status = DownloadStatus.FAILED,
                                    error = "Too many redirects",
                                    speedBps = null,
                                    etaSeconds = null,
                                )
                            }
                            failedItem?.let { persist(it) }
                            jobs.remove(id)
                            reschedule()
                            return
                        }

                        if (RedirectPath.isInsecureDowngrade(currentHttpUrl, nextUrl)) {
                            val failedItem = edit(id) {
                                it.copy(
                                    status = DownloadStatus.FAILED,
                                    error = RedirectPath.INSECURE_REDIRECT_MESSAGE,
                                    speedBps = null,
                                    etaSeconds = null,
                                )
                            }
                            failedItem?.let { persist(it) }
                            jobs.remove(id)
                            reschedule()
                            return
                        }

                        val flag = hopChecker.check(nextUrl.toString())
                        val allowed = allowedHosts[id] ?: emptySet()
                        if (flag != null && !allowed.contains(flag.host)) {
                            pendingFlags[id] = flag
                            val heldItem = edit(id) {
                                it.copy(
                                    status = DownloadStatus.AWAITING_DECISION,
                                    speedBps = null,
                                    etaSeconds = null,
                                )
                            }
                            heldItem?.let { persist(it) }
                            jobs.remove(id)
                            reschedule()
                            _events.tryEmit(
                                DownloadEvent.RedirectWarning(
                                    id = id,
                                    fromHost = currentHttpUrl.host,
                                    flag = flag,
                                )
                            )
                            return
                        }

                        currentHttpUrl = nextUrl
                        currentUrlString = nextUrl.toString()
                        continue
                    } else {
                        finalResponse = resp
                        break
                    }
                }

                val response = checkNotNull(finalResponse)
                response.use { resp ->
                    val code = resp.code
                    if (code == 416 && offset > 0L && currentItem.totalBytes == offset) {
                        updatedItem = edit(id) { it.copy(downloadedBytes = offset) } ?: return
                        finalizePart = true
                    } else if (code in setOf(408, 429) || code in 500..599) {
                        lastHttpError = "HTTP $code"
                        shouldRetry = true
                    } else if (code !in 200..299) {
                        val failedItem = edit(id) {
                            it.copy(
                                status = DownloadStatus.FAILED,
                                error = "HTTP $code",
                                speedBps = null,
                                etaSeconds = null,
                            )
                        }
                        failedItem?.let { persist(it) }
                        jobs.remove(id)
                        reschedule()
                        return
                    } else {
                        val contentRange = resp.header("Content-Range")
                        val rangeStart = DownloadRequest.parseContentRangeStart(contentRange)
                        val decision = DownloadRequest.resumeDecision(offset, code, rangeStart)
                        val acceptRanges = resp.header("Accept-Ranges")

                        val isAppend = decision == ResumeDecision.APPEND
                        val actualOffset = if (isAppend) {
                            offset
                        } else {
                            if (part.exists()) part.delete()
                            0L
                        }

                        val resumable = if (isAppend) {
                            true
                        } else {
                            code == 206 || "bytes".equals(acceptRanges, ignoreCase = true)
                        }

                        val body = resp.body ?: throw IOException("Empty response body")
                        val bodyLength = body.contentLength()
                        val totalBytes = if (code == 206) {
                            DownloadRequest.parseContentRangeTotal(contentRange)
                                ?: if (bodyLength >= 0) actualOffset + bodyLength else -1L
                        } else {
                            if (bodyLength >= 0) bodyLength else -1L
                        }

                        val etag = resp.header("ETag") ?: currentItem.etag
                        val lastModified = resp.header("Last-Modified") ?: currentItem.lastModified
                        val rawContentType = resp.header("Content-Type")
                        val cleanMime = if (!rawContentType.isNullOrBlank()) {
                            rawContentType.substringBefore(';').trim()
                        } else null
                        val newMimeType = if (currentItem.mimeType.isBlank() && !cleanMime.isNullOrBlank()) {
                            cleanMime
                        } else currentItem.mimeType

                        var candidateFileName = currentItem.fileName
                        val contentDisposition = resp.header("Content-Disposition")
                        if (actualOffset == 0L &&
                            contentDisposition != null &&
                            contentDisposition.contains("filename", ignoreCase = true) &&
                            currentItem.fileName == DownloadRequest.fileName(currentItem.url, null, currentItem.mimeType)
                        ) {
                            candidateFileName = DownloadRequest.fileName(currentItem.url, contentDisposition, newMimeType)
                        }

                        updatedItem = edit(id) {
                            it.copy(
                                fileName = candidateFileName,
                                mimeType = newMimeType,
                                totalBytes = totalBytes,
                                resumable = resumable,
                                etag = etag,
                                lastModified = lastModified,
                                downloadedBytes = actualOffset,
                            )
                        } ?: return
                        persist(updatedItem)

                        val speedMeter = SpeedMeter()
                        var downloaded = actualOffset
                        speedMeter.sample(downloaded, clock())

                        var lastUiUpdateMs = clock()
                        var lastPersistMs = clock()

                        val buffer = ByteArray(64 * 1024)
                        val sourceStream = body.byteStream()
                        val fos = FileOutputStream(part, actualOffset > 0L)
                        try {
                            fos.use { out ->
                                var read: Int
                                while (sourceStream.read(buffer).also { read = it } != -1) {
                                    coroutineContext.ensureActive()
                                    out.write(buffer, 0, read)
                                    downloaded += read
                                    val now = clock()

                                    if (now - lastUiUpdateMs >= 500L) {
                                        speedMeter.sample(downloaded, now)
                                        val speed = speedMeter.bytesPerSecond
                                        val eta = if (totalBytes > 0) speedMeter.etaSeconds(totalBytes - downloaded) else null
                                        lastUiUpdateMs = now

                                        updatedItem = edit(id) {
                                            it.copy(
                                                downloadedBytes = downloaded,
                                                speedBps = speed,
                                                etaSeconds = eta,
                                            )
                                        } ?: return

                                        if (now - lastPersistMs >= 2000L) {
                                            lastPersistMs = now
                                            persist(updatedItem)
                                        }
                                    }
                                }
                                out.flush()
                            }
                        } finally {
                            try { sourceStream.close() } catch (_: Throwable) {}
                        }

                        if (totalBytes > 0 && downloaded < totalBytes) {
                            throw IOException("Connection lost")
                        }

                        updatedItem = edit(id) { it.copy(downloadedBytes = downloaded) } ?: return
                        finalizePart = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // A pause/cancel cancels the call, which surfaces as an IOException: never overwrite its status.
                coroutineContext.ensureActive()
                if (networkMonitor.kind.value == NetworkKind.NONE) {
                    val waitingItem = edit(id) {
                        it.copy(
                            status = DownloadStatus.WAITING,
                            speedBps = null,
                            etaSeconds = null,
                        )
                    }
                    waitingItem?.let { persist(it) }
                    jobs.remove(id)
                    return
                } else {
                    lastHttpError = "Connection lost"
                    shouldRetry = true
                }
            } finally {
                handle.dispose()
            }

            if (finalizePart) {
                coroutineContext.ensureActive()
                val after = CheckPolicy.afterDownload(updatedItem.fileName, checks.prefs.value)
                if (after.keyPrompt) {
                    _events.tryEmit(DownloadEvent.KeyPromptNeeded)
                }

                if (after.hold) {
                    val initialStatus = if (after.checkNow) DownloadStatus.CHECKING else DownloadStatus.AWAITING_DECISION
                    val heldItem = edit(id) {
                        it.copy(
                            status = initialStatus,
                            totalBytes = it.downloadedBytes,
                            finishedAt = clock(),
                            speedBps = null,
                            etaSeconds = null,
                            error = null,
                        )
                    } ?: return
                    persist(heldItem)
                    jobs.remove(id)
                    reschedule()
                    DownloadService.notifyFinished(context, heldItem)
                    if (after.checkNow) {
                        runHeldCheck(id, checks.prefs.value.readyServices, transitionToAwaitingDecision = true)
                    }
                    return
                }

                try {
                    val saved = storage.finalize(part, updatedItem.fileName, updatedItem.mimeType, onFallback = ::onFolderFallback)
                    val finishedItem = edit(id) {
                        it.copy(
                            status = DownloadStatus.FINISHED,
                            fileUri = saved.uri,
                            fileName = saved.displayName,
                            totalBytes = it.downloadedBytes,
                            finishedAt = clock(),
                            speedBps = null,
                            etaSeconds = null,
                            error = null,
                        )
                    } ?: return
                    persist(finishedItem)
                    DownloadService.notifyFinished(context, finishedItem)
                    jobs.remove(id)
                    reschedule()
                    if (after.checkNow) {
                        checkFile(id, checks.prefs.value.readyServices)
                    }
                    return
                } catch (e: IOException) {
                    val failedItem = edit(id) {
                        it.copy(
                            status = DownloadStatus.FAILED,
                            error = "Couldn't save the file",
                            speedBps = null,
                            etaSeconds = null,
                        )
                    }
                    failedItem?.let { persist(it) }
                    jobs.remove(id)
                    reschedule()
                    return
                }
            }

            if (shouldRetry) {
                attempt++
                if (attempt <= 3) {
                    val delayMs = when (attempt) {
                        1 -> 2000L
                        2 -> 4000L
                        else -> 8000L
                    }
                    delay(delayMs)
                } else {
                    val errorMsg = lastHttpError ?: "Connection lost"
                    val failedItem = edit(id) {
                        it.copy(
                            status = DownloadStatus.FAILED,
                            error = errorMsg,
                            speedBps = null,
                            etaSeconds = null,
                        )
                    }
                    failedItem?.let { persist(it) }
                    jobs.remove(id)
                    reschedule()
                    return
                }
            }
        }
    }
}
