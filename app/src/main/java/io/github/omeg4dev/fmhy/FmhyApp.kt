package io.github.omeg4dev.fmhy

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.omeg4dev.fmhy.data.sync.SyncResult
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.di.DefaultAppContainer
import kotlinx.coroutines.launch

class FmhyApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
        }

        container = DefaultAppContainer(this)
        container.initialize()

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.appScope.launch {
                    try {
                        val result = container.datasetSync.sync(force = false)
                        when (result) {
                            is SyncResult.Updated -> {
                                if (result.added > 0) {
                                    val msg = "${result.added} new sites"
                                    container.showSnackbar(msg)
                                }
                            }
                            is SyncResult.Replaced -> {
                                container.showSnackbar("Dataset updated")
                            }
                            else -> {}
                        }
                    } catch (e: Exception) {
                        Log.e("DatasetSync", "Auto-update sync failed: ${e.message}", e)
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                container.appScope.launch {
                    container.settingsRepository.setLastVisitTs(System.currentTimeMillis() / 1000)
                }
            }
        })
    }
}
