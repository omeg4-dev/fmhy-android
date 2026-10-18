package io.github.omeg4dev.fmhy.di

import android.content.Context
import android.util.Log
import io.github.omeg4dev.fmhy.AppConfig
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.search.SearchEngine
import io.github.omeg4dev.fmhy.data.sync.DatasetSync
import io.github.omeg4dev.fmhy.data.sync.Manifest
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient

interface AppContainer {
    val context: Context
    val appScope: CoroutineScope
    val okHttpClient: OkHttpClient
    val datasetStore: DatasetStore
    val datasetSync: DatasetSync
    val searchEngine: SearchEngine
    val settingsRepository: SettingsRepository
    val isInitialized: StateFlow<Boolean>
    val snackbarEvents: SharedFlow<String>

    fun showSnackbar(message: String)
    fun initialize()
}

class DefaultAppContainer(override val context: Context) : AppContainer {

    override val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    override val datasetSync: DatasetSync by lazy {
        DatasetSync(
            store = datasetStore,
            dir = datasetDir,
            baseUrl = AppConfig.DATA_BASE_URL,
            client = okHttpClient,
            clock = { System.currentTimeMillis() / 1000 },
            log = { msg -> Log.d("DatasetSync", msg) },
            minIntervalSec = 900L,
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

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    override val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    override fun showSnackbar(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    override fun initialize() {
        appScope.launch(Dispatchers.IO) {
            try {
                if (!dbFile.exists()) {
                    installSnapshotIfAvailable()
                }
            } catch (e: Exception) {
                Log.e("DatasetSync", "Initialization error: ${e.message}", e)
            } finally {
                _isInitialized.value = true
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun installSnapshotIfAvailable() {
        val assetManager = context.assets
        val datasetAssets = try {
            assetManager.list("dataset")?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        if (datasetAssets.contains(SNAPSHOT_ASSET)) {
            val manifestVer = if (datasetAssets.contains("manifest.json")) {
                try {
                    assetManager.open("dataset/manifest.json").use { stream ->
                        val text = stream.bufferedReader().readText()
                        val m = json.decodeFromString<Manifest>(text)
                        m.version
                    }
                } catch (_: Exception) {
                    1L
                }
            } else {
                1L
            }

            try {
                assetManager.open("dataset/$SNAPSHOT_ASSET").use { stream ->
                    datasetSync.installFromSnapshot(stream, manifestVer)
                }
            } catch (e: Exception) {
                Log.e("DatasetSync", "Failed to install asset snapshot: ${e.message}", e)
            }
        }
    }
}

/** Bundled gzip snapshot. Not named *.gz: the asset packager silently un-gzips those and drops the
 *  extension, so the file would never be found under that name. */
private const val SNAPSHOT_ASSET = "snapshot.gzbin"
