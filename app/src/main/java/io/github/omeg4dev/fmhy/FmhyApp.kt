package io.github.omeg4dev.fmhy

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
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

        ProcessLifecycleOwner.get().lifecycleScope.launch {
            try {
                container.sync(force = false)
            } catch (e: Exception) {
                Log.e("FmhyApp", "Launch sync failed: ${e.message}", e)
            }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                container.appScope.launch {
                    container.settingsRepository.setLastVisitTs(System.currentTimeMillis() / 1000)
                }
            }
        })
    }
}
