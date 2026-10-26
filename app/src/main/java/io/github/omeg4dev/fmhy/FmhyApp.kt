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
import io.github.omeg4dev.fmhy.work.NetworkPolicy
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
                container.isInitialized.first { it }
                val hasBuiltIn = container.hasBuiltInWiki
                val hasLocal = container.hasLocalWiki
                val dataUsage = container.settingsRepository.dataUsagePrefs.first()
                val currentKind = container.networkMonitor.kind.value

                if (NetworkPolicy.firstWikiNeedsAsk(
                    hasBuiltInWiki = hasBuiltIn,
                    hasLocalWiki = hasLocal,
                    switchOn = dataUsage.appDataWifiOnly,
                    kind = currentKind,
                )) {
                    container.firstWikiNeedsMobilePermission.value = true
                    return@launch
                }

                val allowedInitially = NetworkPolicy.allowed(
                    switchOn = dataUsage.appDataWifiOnly,
                    kind = currentKind,
                    overrideThisTime = false,
                )
                if (!allowedInitially) {
                    container.isSyncWaitingForWifi.value = true
                    combine(
                        container.settingsRepository.dataUsagePrefs,
                        container.networkMonitor.kind,
                    ) { prefs, kind ->
                        NetworkPolicy.allowed(
                            switchOn = prefs.appDataWifiOnly,
                            kind = kind,
                            overrideThisTime = false,
                        )
                    }.first { allowed -> allowed }
                    container.isSyncWaitingForWifi.value = false
                }

                container.sync(force = false)
            } catch (e: Exception) {
                Log.e("FmhyApp", "Launch sync failed: ${e.message}", e)
            }
        }

        ProcessLifecycleOwner.get().lifecycleScope.launch {
            try {
                combine(
                    container.settingsRepository.notificationPrefs,
                    container.settingsRepository.dataUsagePrefs,
                ) { notif, dataUsage ->
                    notif to dataUsage.appDataWifiOnly
                }.collect { (prefs, appDataWifiOnly) ->
                    io.github.omeg4dev.fmhy.work.UpdateScheduler.apply(this@FmhyApp, prefs, appDataWifiOnly)
                }
            } catch (e: Exception) {
                Log.e("FmhyApp", "Failed to schedule updates: ${e.message}", e)
            }
        }

        ProcessLifecycleOwner.get().lifecycleScope.launch {
            try {
                container.settingsRepository.appearance
                    .map { it.appIcon }
                    .distinctUntilChanged()
                    .collect { icon -> io.github.omeg4dev.fmhy.shortcuts.AppIconSwitcher.apply(this@FmhyApp, icon) }
            } catch (e: Exception) {
                Log.e("FmhyApp", "Failed to apply app icon: ${e.message}", e)
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
