package io.github.omeg4dev.fmhy.di

import android.content.Context
import android.util.Log
import io.github.omeg4dev.fmhy.BuildConfig
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.doc.DocFetcher
import io.github.omeg4dev.fmhy.data.search.SearchEngine
import io.github.omeg4dev.fmhy.data.sync.FmhySync
import io.github.omeg4dev.fmhy.data.sync.SyncProgress
import io.github.omeg4dev.fmhy.data.sync.SyncResult
import io.github.omeg4dev.fmhy.data.tracker.TrackerSync
import io.github.omeg4dev.fmhy.data.BookmarksRepository
import io.github.omeg4dev.fmhy.data.bookmarksDataStore
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.safety.SafeGuard
import io.github.omeg4dev.fmhy.data.user.UserStore
import androidx.navigation3.runtime.NavKey
import io.github.omeg4dev.fmhy.report.CrashReportStore
import io.github.omeg4dev.fmhy.report.ReportRequest
import io.github.omeg4dev.fmhy.browser.BrowserHooks
import io.github.omeg4dev.fmhy.browser.DefaultBrowserHooks
import io.github.omeg4dev.fmhy.browser.TabManager
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.DefaultAdblockUpdater
import io.github.omeg4dev.fmhy.browser.adblock.ScriptletLibrary
import io.github.omeg4dev.fmhy.data.adblock.FilterListUpdater
import io.github.omeg4dev.fmhy.data.adblock.OkHttpListFetcher
import io.github.omeg4dev.fmhy.data.OpenLinksWith
import io.github.omeg4dev.fmhy.work.NetworkMonitor
import io.github.omeg4dev.fmhy.ui.shell.JobKind
import io.github.omeg4dev.fmhy.ui.shell.SYNC_CANCELLED
import io.github.omeg4dev.fmhy.ui.shell.StatusNotices
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cache
import okhttp3.OkHttpClient

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data class Syncing(val progress: SyncProgress? = null, val userStarted: Boolean = false) : SyncStatus
    data class Finished(val result: SyncResult) : SyncStatus
}

interface OnboardingActions {
    fun startFilterDownload() {}
}

interface AppContainer : OnboardingActions {
    val context: Context
    val appScope: CoroutineScope
    val okHttpClient: OkHttpClient
    val datasetStore: DatasetStore
    val userStore: UserStore
    val fmhySync: FmhySync
    val trackerSync: TrackerSync
        get() = error("trackerSync not provided")
    var debugTrackerUrl: String?
        get() = null
        set(_) {}
    val searchEngine: SearchEngine
    val settingsRepository: SettingsRepository
    val bookmarks: BookmarksRepository
    val docFetcher: DocFetcher
    val crossLinks: StateFlow<CrossLinks?>
    val safeGuard: SafeGuard
    val isInitialized: StateFlow<Boolean>
    val syncStatus: StateFlow<SyncStatus>
    val snackbarEvents: SharedFlow<String>
    val navigationEvents: SharedFlow<NavKey>
        get() = kotlinx.coroutines.flow.MutableSharedFlow()
    val isSyncBannerConsumed: Boolean
    val adblockService: AdblockService get() = error("Not supported")
    val tabManager: TabManager get() = error("Not supported")
    val browserHooks: BrowserHooks get() = error("Not supported")
    val statusNotices: StatusNotices get() = error("Not supported")
    val networkMonitor: NetworkMonitor get() = error("Not supported")
    val updateController: io.github.omeg4dev.fmhy.update.UpdateController get() = io.github.omeg4dev.fmhy.update.NoOpUpdateController
    val firstWikiNeedsMobilePermission: MutableStateFlow<Boolean> get() = MutableStateFlow(false)
    val isSyncWaitingForWifi: MutableStateFlow<Boolean> get() = MutableStateFlow(false)
    val hasBuiltInWiki: Boolean get() = false
    val hasLocalWiki: Boolean get() = false
    val crashReportStore: CrashReportStore get() = error("crashReportStore not provided")
    val reportRequest: MutableStateFlow<ReportRequest?> get() = MutableStateFlow(null)

    fun openReport(request: ReportRequest) {
        reportRequest.value = request
    }
    fun cancelSync() {}
    fun cancelAdblockUpdate() {}
    fun markSyncBannerConsumed()
    fun showSnackbar(message: String)
    fun pushNavigation(key: NavKey) {}
    fun initialize()
    suspend fun sync(force: Boolean = false): SyncResult
}

class DefaultAppContainer(
    override val context: Context,
    crashStore: CrashReportStore? = null,
) : AppContainer {

    override val crashReportStore: CrashReportStore = crashStore ?: CrashReportStore(
        file = File(context.filesDir, "crash_report.json"),
        enabled = { context.getSharedPreferences("fmhy_crash", Context.MODE_PRIVATE).getBoolean("report_offer_crashes", true) }
    )

    override val reportRequest = MutableStateFlow<ReportRequest?>(null)

    override fun openReport(request: ReportRequest) {
        reportRequest.value = request
    }

    override val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val userDir by lazy {
        File(context.filesDir, "user").apply { mkdirs() }
    }
    private val userDbFile by lazy {
        File(userDir, "user.db")
    }

    override val userStore: UserStore by lazy {
        UserStore(userDbFile)
    }

    override val bookmarks: BookmarksRepository by lazy {
        BookmarksRepository(
            userStore = userStore,
            legacyDataStore = context.bookmarksDataStore,
        )
    }

    override val browserHooks: BrowserHooks by lazy {
        DefaultBrowserHooks(datasetStore = datasetStore)
    }

    private val adblockDir by lazy {
        File(context.filesDir, "filters")
    }

    private val filterListUpdater by lazy {
        FilterListUpdater(
            dir = adblockDir,
            fetcher = OkHttpListFetcher(okHttpClient),
            clock = { System.currentTimeMillis() },
            log = { msg -> Log.d("FmhyAdblock", msg) }
        )
    }

    private val scriptletLibrary by lazy {
        ScriptletLibrary(context)
    }

    override val adblockService: AdblockService by lazy {
        AdblockService(
            settingsRepository = settingsRepository,
            updater = DefaultAdblockUpdater(filterListUpdater),
            scriptletLibrary = scriptletLibrary,
            coroutineScope = appScope,
            networkMonitor = networkMonitor,
        )
    }

    override val tabManager: TabManager by lazy {
        TabManager(
            appContext = context.applicationContext,
            settingsRepository = settingsRepository,
            userStore = userStore,
            adblockService = adblockService,
            scriptletLibrary = scriptletLibrary,
            browserHooks = browserHooks,
            appScope = appScope,
            networkMonitor = networkMonitor,
        )
    }

    private val docsDir by lazy { File(context.filesDir, "docs").apply { mkdirs() } }

    override val docFetcher: DocFetcher by lazy {
        DocFetcher(
            docsDir = docsDir,
            client = okHttpClient,
        )
    }

    override val crossLinks: StateFlow<CrossLinks?> by lazy {
        datasetStore.changes
            .map { if (datasetStore.isAvailable) CrossLinks(datasetStore.allHeadings()) else null }
            .flowOn(Dispatchers.IO)
            .stateIn(appScope, SharingStarted.Eagerly, null)
    }

    override val okHttpClient: OkHttpClient by lazy {
        val cacheDir = File(context.cacheDir, "http")
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .cache(Cache(cacheDir, 5L * 1024 * 1024))
            .build()
    }

    private val datasetDir by lazy { File(context.filesDir, "dataset") }
    private val dbFile by lazy { File(datasetDir, "current.db") }

    override val datasetStore: DatasetStore by lazy {
        DatasetStore(dbFile)
    }

    override val safeGuard: SafeGuard by lazy {
        SafeGuard(datasetStore)
    }

    override val fmhySync: FmhySync by lazy {
        FmhySync(
            store = datasetStore,
            dir = datasetDir,
            client = okHttpClient,
            clock = { System.currentTimeMillis() / 1000 },
            log = { msg -> Log.d("FmhySync", msg) },
            minIntervalSec = 600L,
        )
    }

    @Volatile
    override var debugTrackerUrl: String? = null

    override val trackerSync: TrackerSync
        get() {
            val overrideUrl = if (BuildConfig.DEBUG) debugTrackerUrl else null
            return TrackerSync(
                store = datasetStore,
                dir = datasetDir,
                client = okHttpClient,
                url = overrideUrl ?: "https://fmhy-tracker.pages.dev/monthly_commits.json",
                clock = { System.currentTimeMillis() / 1000 },
                log = { msg -> Log.d("TrackerSync", msg) },
            )
        }

    override val searchEngine: SearchEngine by lazy {
        SearchEngine(datasetStore)
    }

    override val settingsRepository: SettingsRepository by lazy {
        io.github.omeg4dev.fmhy.data.DefaultSettingsRepository(context)
    }

    override val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(context)
    }

    override val updateController by lazy {
        io.github.omeg4dev.fmhy.update.createUpdateController(context, appScope, okHttpClient, settingsRepository, networkMonitor)
    }

    override val firstWikiNeedsMobilePermission = MutableStateFlow(false)
    override val isSyncWaitingForWifi = MutableStateFlow(false)

    override val hasBuiltInWiki: Boolean by lazy {
        try {
            val list = context.assets.list("dataset")?.toSet() ?: emptySet()
            list.contains(SNAPSHOT_ASSET) && list.contains(SNAPSHOT_STATE_ASSET)
        } catch (_: Exception) {
            false
        }
    }

    override val hasLocalWiki: Boolean
        get() = datasetStore.isAvailable

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    @Volatile
    private var _isSyncBannerConsumed: Boolean = false
    override val isSyncBannerConsumed: Boolean get() = _isSyncBannerConsumed

    override fun markSyncBannerConsumed() {
        _isSyncBannerConsumed = true
    }

    @Volatile
    private var activeSync: Deferred<SyncResult>? = null

    override fun cancelSync() {
        activeSync?.cancel()
    }

    override fun cancelAdblockUpdate() {
        adblockService.cancelUpdate()
    }

    override val statusNotices: StatusNotices by lazy {
        StatusNotices(
            scope = appScope,
            syncStatus = syncStatus,
            adblockInputs = combine(
                settingsRepository.browserPrefs,
                settingsRepository.blockingPrefs,
                adblockService.status,
            ) { browser, blocking, status ->
                (browser.openLinksWith == OpenLinksWith.IN_APP && blocking.adblock) to status
            },
            isSyncBannerConsumed = { isSyncBannerConsumed },
            markSyncBannerConsumed = ::markSyncBannerConsumed,
            cancel = { kind ->
                when (kind) {
                    JobKind.ADBLOCK -> cancelAdblockUpdate()
                    JobKind.WIKI -> cancelSync()
                }
            },
            retry = { kind ->
                when (kind) {
                    JobKind.ADBLOCK -> startFilterDownload()
                    JobKind.WIKI -> {
                        appScope.launch { sync(force = true) }
                    }
                }
            },
            lastUpdated = { kind ->
                when (kind) {
                    JobKind.ADBLOCK -> adblockService.status.value.lastUpdated
                    JobKind.WIKI -> fmhySync.loadState().lastSuccess.takeIf { it > 0 }?.times(1000L)
                }
            },
            clock = System::currentTimeMillis,
        )
    }

    private val syncMutex = Mutex()

    override suspend fun sync(force: Boolean): SyncResult {
        isInitialized.first { it }
        // Runs in the app scope: a caller that goes away (e.g. leaving Settings) must not leave the
        // status stuck at Syncing or release the mutex while the sync is still writing.
        return appScope.async(Dispatchers.IO) { syncMutex.withLock { runSync(force) } }.await()
    }

    private suspend fun runSync(force: Boolean): SyncResult {
        _isSyncBannerConsumed = false
        // force = true only comes from user actions (settings, pull to refresh, What's new).
        val userStarted = force
        _syncStatus.value = SyncStatus.Syncing(null, userStarted)
        // Separate job so cancelSync() stops only the wiki sync, not this function.
        val deferred = appScope.async(Dispatchers.IO) {
            fmhySync.sync(force) { p ->
                _syncStatus.value = SyncStatus.Syncing(p, userStarted)
            }
        }
        activeSync = deferred
        val result = try {
            deferred.await()
        } catch (e: CancellationException) {
            if (!deferred.isCancelled) throw e
            SyncResult.Failed(SYNC_CANCELLED, 0L)
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: "Unknown error", 0L)
        } finally {
            activeSync = null
        }
        _syncStatus.value = SyncStatus.Finished(result)

        if (result !is SyncResult.Failed) {
            try {
                val prefs = settingsRepository.notificationPrefs.first()
                val overrideUrl = if (BuildConfig.DEBUG) debugTrackerUrl else null
                if (prefs.useTracker || overrideUrl != null) {
                    val currentTracker = trackerSync
                    currentTracker.sync(force = force || overrideUrl != null)
                }
            } catch (e: Exception) {
                Log.w("AppContainer", "TrackerSync failed: ${e.message}", e)
            } finally {
                if (BuildConfig.DEBUG) {
                    debugTrackerUrl = null
                }
            }
        }
        return result
    }

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    override val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    override fun showSnackbar(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    private val _navigationEvents = MutableSharedFlow<NavKey>(extraBufferCapacity = 5)
    override val navigationEvents: SharedFlow<NavKey> = _navigationEvents.asSharedFlow()

    override fun pushNavigation(key: NavKey) {
        _navigationEvents.tryEmit(key)
    }

    override fun startFilterDownload() {
        appScope.launch(Dispatchers.IO) {
            runCatching { adblockService.updateNow(userStarted = true) }
                .onFailure { Log.w("FmhyAdblock", "Filter list download failed", it) }
        }
    }

    /** 0.5.0's onboarding saved section follows without a title; fill them in from the dataset. */
    private suspend fun repairFollowTitles() {
        val blank = userStore.follows().filter { it.title.isBlank() && it.headingId.isEmpty() }
        if (blank.isEmpty()) return
        val titles = datasetStore.sections().associate { it.id to it.title }
        for (f in blank) {
            titles[f.sectionId]?.let { userStore.follow(f.sectionId, "", it) }
        }
    }

    override fun initialize() {
        appScope.launch(Dispatchers.IO) {
            try {
                bookmarks.migrateLegacyIfNeeded()
                if (!dbFile.exists() || !datasetStore.isSchemaCurrent()) {
                    datasetStore.close()
                    installSnapshotIfAvailable()
                }
                adblockService.initialize()
                repairFollowTitles()
            } catch (e: Exception) {
                Log.e("FmhySync", "Initialization error: ${e.message}", e)
            } finally {
                _isInitialized.value = true
            }
        }
    }

    private suspend fun installSnapshotIfAvailable() {
        val assetManager = context.assets
        val datasetAssets = try {
            assetManager.list("dataset")?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        if (datasetAssets.contains(SNAPSHOT_ASSET) && datasetAssets.contains(SNAPSHOT_STATE_ASSET)) {
            try {
                assetManager.open("dataset/$SNAPSHOT_ASSET").use { gzStream ->
                    assetManager.open("dataset/$SNAPSHOT_STATE_ASSET").use { stateStream ->
                        fmhySync.installSnapshot(gzStream, stateStream)
                    }
                }
            } catch (e: Exception) {
                Log.e("FmhySync", "Failed to install asset snapshot: ${e.message}", e)
            }
        }
    }
}

/** Bundled gzip snapshot. Not named *.gz: the asset packager silently un-gzips those and drops the
 *  extension, so the file would never be found under that name. */
private const val SNAPSHOT_ASSET = "snapshot.gzbin"
private const val SNAPSHOT_STATE_ASSET = "snapshot-state.json"
