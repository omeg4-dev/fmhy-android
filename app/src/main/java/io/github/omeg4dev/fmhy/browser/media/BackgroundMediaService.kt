package io.github.omeg4dev.fmhy.browser.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.browser.BrowserActivity
import io.github.omeg4dev.fmhy.browser.TabManager
import io.github.omeg4dev.fmhy.browser.TabState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps a browser tab's audio/video going after the user leaves the app or locks the screen, and
 * shows a media notification (pause/play, close) backed by a platform MediaSession.
 *
 * The playing tab is [TabManager.keepAliveTabId]; its state comes from the same `mediaEvent` reports
 * the in-app browser already uses.
 */
class BackgroundMediaService : Service() {

    private lateinit var session: MediaSession
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val idle = IdleTimeout()
    private var stopping = false
    private var lastMetaKey: String? = null

    private val tabManager: TabManager
        get() = (application as FmhyApp).container.tabManager

    private val idleCheck = Runnable { refresh() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSession(this, "FmhyBackgroundPlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = control(play = true)
                override fun onPause() = control(play = false)
                override fun onStop() = close()
            })
            isActive = true
        }
        // A foreground service must call startForeground promptly, even if it is about to stop.
        val tab = currentTab()
        if (tab != null) idle.onPlayingChanged(tab.isVideoPlaying)
        goForeground(buildNotification(tab))
        if (tab == null) {
            finish()
            return
        }
        updateSession(tab)
        scope.launch {
            tabManager.tabs.collect { refresh() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> control(play = true)
            ACTION_PAUSE -> control(play = false)
            ACTION_CLOSE -> close()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(idleCheck)
        scope.cancel()
        try {
            session.isActive = false
            session.release()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun currentTab(): TabState? {
        val id = tabManager.keepAliveTabId ?: return null
        return tabManager.tabs.value.firstOrNull { it.id == id }
    }

    private fun control(play: Boolean) {
        val id = tabManager.keepAliveTabId ?: return
        tabManager.controlMedia(id, play)
    }

    /** Pauses the media, hands the tab back to the normal pause path and stops. */
    private fun close() {
        if (stopping) return
        tabManager.endBackgroundPlayback()
        finish()
    }

    private fun finish() {
        if (stopping) return
        stopping = true
        handler.removeCallbacks(idleCheck)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun refresh() {
        if (stopping) return
        val tab = currentTab()
        if (tab == null || tab.crashed) {
            // Closed, crashed or no longer kept alive (the activity came back).
            tabManager.endBackgroundPlayback()
            finish()
            return
        }
        idle.onPlayingChanged(tab.isVideoPlaying)
        if (idle.isExpired()) {
            close()
            return
        }
        handler.removeCallbacks(idleCheck)
        idle.remainingMs()?.let { handler.postDelayed(idleCheck, it + 50L) }
        updateSession(tab)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(tab))
    }

    private fun hostOf(tab: TabState): String =
        try { Uri.parse(tab.url).host.orEmpty().removePrefix("www.") } catch (_: Exception) { "" }

    private fun updateSession(tab: TabState) {
        val title = tab.title.ifBlank { hostOf(tab) }
        val host = hostOf(tab)
        val key = "$title|$host"
        if (key != lastMetaKey) {
            lastMetaKey = key
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, host)
                    .build(),
            )
        }
        val state = if (tab.isVideoPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP,
                )
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (tab.isVideoPlaying) 1f else 0f)
                .build(),
        )
    }

    private fun serviceIntent(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this,
        code,
        Intent(this, BackgroundMediaService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(tab: TabState?): Notification {
        val playing = tab?.isVideoPlaying == true
        val title = tab?.title?.ifBlank { null } ?: tab?.let { hostOf(it) } ?: getString(R.string.app_name)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggle = if (playing) {
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Pause", serviceIntent(ACTION_PAUSE, 1),
            ).build()
        } else {
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_play), "Play", serviceIntent(ACTION_PLAY, 2),
            ).build()
        }
        val closeAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Close", serviceIntent(ACTION_CLOSE, 3),
        ).build()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(tab?.let { hostOf(it) })
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(toggle)
            .addAction(closeAction)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1),
            )
            .build()
    }

    private fun goForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Background playback", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        const val CHANNEL_ID = "background_playback"
        const val NOTIFICATION_ID = 7021
        const val ACTION_PLAY = "io.github.omeg4dev.fmhy.media.PLAY"
        const val ACTION_PAUSE = "io.github.omeg4dev.fmhy.media.PAUSE"
        const val ACTION_CLOSE = "io.github.omeg4dev.fmhy.media.CLOSE"

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, BackgroundMediaService::class.java))
            } catch (_: Exception) {
                // Not allowed to start a foreground service right now; playback simply pauses as before.
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, BackgroundMediaService::class.java))
            } catch (_: Exception) {}
        }
    }
}
