package io.github.omeg4dev.fmhy.browser.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.browser.BrowserActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val controller: DownloadController
        get() = (application as FmhyApp).container.downloads

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW),
        )

        val items = controller.items.value
        val initialContent = DownloadNotificationText.ongoing(items)
            ?: DownloadNotificationText.Content(
                title = "Downloads",
                text = "Starting…",
                percent = null,
                showProgress = true,
                single = true,
            )

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(initialContent, items), type)

        var lastUpdateMs = System.currentTimeMillis()
        var pendingJob: Job? = null

        scope.launch {
            controller.items.collect { currentItems ->
                val ongoing = DownloadNotificationText.ongoing(currentItems)
                if (ongoing == null) {
                    pendingJob?.cancel()
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }

                val now = System.currentTimeMillis()
                val elapsed = now - lastUpdateMs
                if (elapsed >= 1000L) {
                    pendingJob?.cancel()
                    lastUpdateMs = now
                    nm?.notify(NOTIFICATION_ID, buildNotification(ongoing, currentItems))
                } else {
                    if (pendingJob == null || !pendingJob!!.isActive) {
                        pendingJob = launch {
                            delay(1000L - elapsed)
                            lastUpdateMs = System.currentTimeMillis()
                            val latestItems = controller.items.value
                            val latestOngoing = DownloadNotificationText.ongoing(latestItems)
                            if (latestOngoing != null) {
                                nm?.notify(NOTIFICATION_ID, buildNotification(latestOngoing, latestItems))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                val id = intent.getLongExtra(EXTRA_ID, Long.MIN_VALUE)
                if (id != Long.MIN_VALUE) controller.pause(id)
            }
            ACTION_CANCEL -> {
                val id = intent.getLongExtra(EXTRA_ID, Long.MIN_VALUE)
                if (id != Long.MIN_VALUE) controller.cancel(id)
            }
            ACTION_PAUSE_ALL -> controller.pauseAll()
            ACTION_CANCEL_ALL -> controller.cancelAll()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        controller.pauseAll()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(content: DownloadNotificationText.Content, items: List<DownloadItem>): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, BrowserActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(EXTRA_OPEN_DOWNLOADS, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(openIntent)

        if (content.showProgress) {
            builder.setProgress(100, content.percent ?: 0, content.percent == null)
        }

        if (content.single) {
            val singleItem = items.firstOrNull { it.status.isActive }
            if (singleItem != null) {
                val pauseIntent = PendingIntent.getService(
                    this,
                    1,
                    Intent(this, DownloadService::class.java)
                        .setAction(ACTION_PAUSE)
                        .putExtra(EXTRA_ID, singleItem.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val cancelIntent = PendingIntent.getService(
                    this,
                    2,
                    Intent(this, DownloadService::class.java)
                        .setAction(ACTION_CANCEL)
                        .putExtra(EXTRA_ID, singleItem.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val pauseAction = Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "Pause",
                    pauseIntent,
                ).build()
                val cancelAction = Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Cancel",
                    cancelIntent,
                ).build()
                builder.addAction(pauseAction)
                builder.addAction(cancelAction)
            }
        } else {
            val pauseAllIntent = PendingIntent.getService(
                this,
                3,
                Intent(this, DownloadService::class.java).setAction(ACTION_PAUSE_ALL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val cancelAllIntent = PendingIntent.getService(
                this,
                4,
                Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL_ALL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val pauseAllAction = Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                "Pause all",
                pauseAllIntent,
            ).build()
            val cancelAllAction = Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Cancel all",
                cancelAllIntent,
            ).build()
            builder.addAction(pauseAllAction)
            builder.addAction(cancelAllAction)
        }

        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 7031
        const val EXTRA_OPEN_DOWNLOADS = "io.github.omeg4dev.fmhy.extra.OPEN_DOWNLOADS"
        const val EXTRA_ID = "io.github.omeg4dev.fmhy.download.EXTRA_ID"
        const val ACTION_PAUSE = "io.github.omeg4dev.fmhy.download.PAUSE"
        const val ACTION_CANCEL = "io.github.omeg4dev.fmhy.download.CANCEL"
        const val ACTION_PAUSE_ALL = "io.github.omeg4dev.fmhy.download.PAUSE_ALL"
        const val ACTION_CANCEL_ALL = "io.github.omeg4dev.fmhy.download.CANCEL_ALL"

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, DownloadService::class.java))
            } catch (_: Exception) {}
        }

        fun notifyFinished(context: Context, item: DownloadItem) {
            try {
                val nm = context.getSystemService(NotificationManager::class.java) ?: return
                if (!nm.areNotificationsEnabled()) return
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW),
                )
                val notifId = 7200 + (abs(item.id) % 1000).toInt()
                val title = DownloadNotificationText.finishedTitle(item)
                val text = DownloadNotificationText.finishedText(item)
                val open = PendingIntent.getActivity(
                    context,
                    notifId,
                    Intent(context, BrowserActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .putExtra(EXTRA_OPEN_DOWNLOADS, true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val builder = Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(title)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                if (text != null) {
                    builder.setContentText(text)
                }
                nm.notify(notifId, builder.build())
            } catch (_: SecurityException) {
            } catch (_: Throwable) {}
        }
    }
}
