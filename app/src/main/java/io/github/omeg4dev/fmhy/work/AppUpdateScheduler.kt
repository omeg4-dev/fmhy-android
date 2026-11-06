package io.github.omeg4dev.fmhy.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.omeg4dev.fmhy.BuildConfig
import io.github.omeg4dev.fmhy.data.UpdatePrefs
import java.util.concurrent.TimeUnit

object AppUpdateScheduler {
    const val WORK_NAME = "fmhy-app-update"

    fun apply(context: Context, prefs: UpdatePrefs, appDataWifiOnly: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!BuildConfig.UPDATER_ENABLED || !prefs.autoCheck || !prefs.notify) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val networkType = if (appDataWifiOnly) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(
            repeatInterval = prefs.interval.hours,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
