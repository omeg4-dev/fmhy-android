package io.github.omeg4dev.fmhy.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.MainActivity
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import kotlinx.coroutines.flow.first

class UpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FmhyApp ?: return Result.failure()
        UpdateChecker.run(applicationContext, app.container)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = UpdateChecker.CHANNEL_ID
    }
}

/** Result of one update check: the notification that was (or would be) shown, or null for "No changes". */
data class UpdateOutcome(val model: NotificationModel?) {
    val summary: String
        get() = model?.let { m ->
            if (m.changeCount == 1) "1 change: ${m.title}" else "${m.changeCount} changes: ${m.title}"
        } ?: "No changes"
}

object UpdateChecker {
    const val CHANNEL_ID = "updates"
    const val NOTIFICATION_ID = 1001

    /** Sync (which also runs the tracker when enabled), look for followed/saved changes, notify, store the timestamp. */
    suspend fun run(context: Context, container: AppContainer): UpdateOutcome {
        val prefs = try {
            container.settingsRepository.notificationPrefs.first()
        } catch (_: Exception) {
            null
        }

        // container.sync() runs the tracker afterwards when NotificationPrefs.useTracker is on.
        try {
            container.sync(force = false)
        } catch (_: Exception) {}

        val nowSec = System.currentTimeMillis() / 1000
        val lastTsStr = try {
            container.userStore.get("notify_last_ts")
        } catch (_: Exception) {
            null
        }
        val lastTs = lastTsStr?.toLongOrNull()
        val effectiveLastTs = UpdateWorkerLogic.resolveLastTs(lastTs, nowSec)

        val follows = try {
            container.userStore.follows()
        } catch (_: Exception) {
            emptyList()
        }
        val bookmarks = try {
            container.userStore.bookmarks()
        } catch (_: Exception) {
            emptyList()
        }

        val sectionTitles = try {
            container.datasetStore.sections().associate { it.id to it.title }
        } catch (_: Exception) {
            emptyMap()
        }

        val hierarchy = try {
            val allH = container.datasetStore.allHeadings()
            val parentToChildren = allH.groupBy { it.parentId }
            val map = mutableMapOf<String, Set<String>>()
            for (h in allH) {
                val descendants = mutableSetOf<String>()
                val queue = ArrayDeque<String>()
                queue.add(h.id)
                while (queue.isNotEmpty()) {
                    val cur = queue.removeFirst()
                    if (descendants.add(cur)) {
                        parentToChildren[cur]?.forEach { queue.add(it.id) }
                    }
                }
                map[h.id] = descendants
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }

        // 2. Followed changes: {ADDED, STARRED} in followed sections/categories
        val followedChanges = if (prefs?.notifyFollowed != false && follows.isNotEmpty()) {
            try {
                val sectionFollows = follows.filter { it.headingId.isEmpty() }.map { it.sectionId }.toSet()
                val categoryFollowHeadingIds = follows.filter { it.headingId.isNotEmpty() }.flatMap { f ->
                    hierarchy[f.headingId] ?: setOf(f.headingId)
                }.toSet()

                val changes = container.datasetStore.recentChanges(
                    sinceTs = effectiveLastTs,
                    kinds = setOf(ChangeKind.ADDED, ChangeKind.STARRED),
                    sectionIds = sectionFollows.ifEmpty { null },
                    headingIds = categoryFollowHeadingIds.ifEmpty { null },
                )
                UpdateWorkerLogic.filterFollowedChanges(changes, follows, hierarchy)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // 3. Saved changes: {STARRED, UNSTARRED, REMOVED, URL_CHANGED} matching bookmarks
        val savedChanges = if (prefs?.watchSaved != false && bookmarks.isNotEmpty()) {
            try {
                val recentAll = container.datasetStore.recentChanges(
                    sinceTs = effectiveLastTs,
                    kinds = setOf(
                        ChangeKind.STARRED,
                        ChangeKind.UNSTARRED,
                        ChangeKind.REMOVED,
                        ChangeKind.URL_CHANGED,
                    ),
                    limit = 500,
                )
                UpdateWorkerLogic.filterSavedChanges(recentAll, bookmarks, watchSaved = true)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val model = UpdateWorkerLogic.buildNotification(
            followedChanges = followedChanges,
            savedChanges = savedChanges,
            sectionTitles = sectionTitles,
        )

        if (model != null) {
            postNotification(context, model)
        }

        try {
            container.userStore.put("notify_last_ts", nowSec.toString())
        } catch (_: Exception) {}

        return UpdateOutcome(model)
    }

    fun postNotification(context: Context, model: NotificationModel) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "FMHY updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open", "whats_new")
                putExtra("filter", model.openFilter)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val inboxStyle = NotificationCompat.InboxStyle()
            for (line in model.inboxLines) {
                inboxStyle.addLine(line)
            }

            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(model.title)
                .setContentText(model.text)
                .setStyle(inboxStyle)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notif)
        }
}
