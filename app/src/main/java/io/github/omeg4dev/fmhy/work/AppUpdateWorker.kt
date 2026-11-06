package io.github.omeg4dev.fmhy.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.omeg4dev.fmhy.BuildConfig
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.MainActivity
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.update.ReleaseParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import okhttp3.Request

class AppUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 1002
    }

    override suspend fun doWork(): Result {
        if (!BuildConfig.UPDATER_ENABLED) return Result.success()

        val app = applicationContext as? FmhyApp ?: return Result.success()
        val container = app.container
        val prefs = container.settingsRepository.updatePrefs.first()
        if (!prefs.autoCheck) return Result.success()

        val client = container.okHttpClient.newBuilder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

        val url = "https://api.github.com/repos/${BuildConfig.RELEASE_REPO}/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .build()

        val now = System.currentTimeMillis()
        val release = try {
            client.newCall(request).execute().use { response ->
                container.settingsRepository.updateUpdatePrefs { it.copy(lastCheckMs = now) }
                if (!response.isSuccessful) {
                    return Result.success()
                }
                val body = response.body.string()
                ReleaseParser.parse(body) ?: return Result.success()
            }
        } catch (e: IOException) {
            Log.e("AppUpdateWorker", "Network error checking update: ${e.message}", e)
            return if (runAttemptCount < 3) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.e("AppUpdateWorker", "Error checking update: ${e.message}", e)
            return Result.success()
        }

        val decision = AppUpdateLogic.decide(
            release = release,
            installed = BuildConfig.VERSION_NAME,
            prefs = prefs,
            nowMs = now,
        )

        when (decision) {
            is AppUpdateDecision.NoUpdate -> {
                container.settingsRepository.updateUpdatePrefs { it.copy(knownAvailable = null) }
            }
            is AppUpdateDecision.UpdateKnown -> {
                container.settingsRepository.updateUpdatePrefs { it.copy(knownAvailable = decision.version) }
            }
            is AppUpdateDecision.Notify -> {
                container.settingsRepository.updateUpdatePrefs { it.copy(knownAvailable = decision.version) }
                val permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                if (prefs.notify && permissionGranted) {
                    postNotification(applicationContext, decision.title, decision.text)
                    container.settingsRepository.updateUpdatePrefs { it.copy(notifiedVersion = decision.version) }
                }
            }
        }

        return Result.success()
    }

    private fun postNotification(context: Context, title: String, text: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fmhy)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notif)
    }
}
