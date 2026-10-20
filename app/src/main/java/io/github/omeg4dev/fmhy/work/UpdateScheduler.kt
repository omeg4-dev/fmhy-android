package io.github.omeg4dev.fmhy.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.omeg4dev.fmhy.data.NotificationPrefs
import java.util.concurrent.TimeUnit

object UpdateScheduler {
    const val WORK_NAME = "fmhy-updates"

    fun apply(context: Context, prefs: NotificationPrefs) {
        val workManager = WorkManager.getInstance(context)
        if (!prefs.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val networkType = if (prefs.wifiOnly) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateWorker>(
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
