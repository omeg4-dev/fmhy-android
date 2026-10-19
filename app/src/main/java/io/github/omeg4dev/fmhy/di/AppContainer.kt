package io.github.omeg4dev.fmhy.di

import android.content.Context
import android.util.Log
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.search.SearchEngine
import io.github.omeg4dev.fmhy.data.sync.FmhySync
import io.github.omeg4dev.fmhy.data.sync.SyncResult
import io.github.omeg4dev.fmhy.data.BookmarksRepository
import io.github.omeg4dev.fmhy.data.bookmarksDataStore
import io.github.omeg4dev.fmhy.data.link.CrossLinks
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
    data object Syncing : SyncStatus
    data class Finished(val result: SyncResult) : SyncStatus
}

interface AppContainer {
    val context: Context
    val appScope: CoroutineScope
    val okHttpClient: OkHttpClient
    val datasetStore: DatasetStore
    val fmhySync: FmhySync
    val searchEngine: SearchEngine
    val settingsRepository: SettingsRepository
    val bookmarks: BookmarksRepository
    val crossLinks: StateFlow<CrossLinks?>
    val isInitialized: StateFlow<Boolean>
    val syncStatus: StateFlow<SyncStatus>
    val snackbarEvents: SharedFlow<String>
    val isSyncBannerConsumed: Boolean

    fun markSyncBannerConsumed()
    fun showSnackbar(message: String)
    fun initialize()
    suspend fun sync(force: Boolean = false): SyncResult
}

class DefaultAppContainer(override val context: Context) : AppContainer {

    override val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val bookmarks: BookmarksRepository by lazy {
        BookmarksRepository(context.bookmarksDataStore)
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

    override val searchEngine: SearchEngine by lazy {
        SearchEngine(datasetStore)
    }

    override val settingsRepository: SettingsRepository by lazy {
        io.github.omeg4dev.fmhy.data.DefaultSettingsRepository(context)
    }

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

    private val syncMutex = Mutex()

    override suspend fun sync(force: Boolean): SyncResult {
        isInitialized.first { it }
        return syncMutex.withLock {
            _isSyncBannerConsumed = false
            _syncStatus.value = SyncStatus.Syncing
            val result = try {
                fmhySync.sync(force = force)
            } catch (e: Exception) {
                SyncResult.Failed(e.message ?: "Unknown error", 0L)
            }
            _syncStatus.value = SyncStatus.Finished(result)
            result
        }
    }

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    override val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    override fun showSnackbar(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    override fun initialize() {
        appScope.launch(Dispatchers.IO) {
            try {
                if (!dbFile.exists() || !datasetStore.isSchemaCurrent()) {
                    datasetStore.close()
                    installSnapshotIfAvailable()
                }
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
