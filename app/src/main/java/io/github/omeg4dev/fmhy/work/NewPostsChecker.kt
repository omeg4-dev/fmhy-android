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
import io.github.omeg4dev.fmhy.MainActivity
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.ui.posts.CachedPost
import io.github.omeg4dev.fmhy.ui.posts.PostsListing
import io.github.omeg4dev.fmhy.ui.posts.PostsListingData
import io.github.omeg4dev.fmhy.ui.posts.PostsReadState
import io.github.omeg4dev.fmhy.ui.posts.newPostsNotificationText
import io.github.omeg4dev.fmhy.ui.posts.newUnreadPosts
import io.github.omeg4dev.fmhy.ui.posts.shouldCheckPosts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/** Looks for a new monthly post (at most once per 24 h) and posts one notification for it. */
object NewPostsChecker {
    const val CHANNEL_ID = "new_posts"
    const val NOTIFICATION_ID = 1002
    const val KEY_LAST_CHECK = "posts_last_check"

    /** Returns the posts a notification was built for (empty when none). */
    suspend fun run(context: Context, container: AppContainer): List<CachedPost> {
        val prefs = container.settingsRepository.notificationPrefs.first()
        if (!prefs.notifyNewPosts) return emptyList()

        val nowSec = System.currentTimeMillis() / 1000
        val last = container.userStore.get(KEY_LAST_CHECK)?.toLongOrNull()
        if (!shouldCheckPosts(last, nowSec)) return emptyList()

        val filesDir = context.filesDir
        val cacheFile = File(filesDir, "posts-listing.json")
        val previous = withContext(Dispatchers.IO) { PostsListing.readCache(cacheFile) }
        val known = previous?.posts?.map { it.path }?.toSet()

        val fileNames = withContext(Dispatchers.IO) { PostsListing.fetchListingFromNetwork(container.okHttpClient) }
        val resolved = withContext(Dispatchers.IO) {
            fileNames.map { PostsListing.resolvePostItem(it, filesDir) }.sortedByDescending { it.dateEpoch }
        }
        val listed = resolved.map { CachedPost(it.path, it.title, it.dateText, it.dateEpoch) }
        withContext(Dispatchers.IO) { PostsListing.writeCache(cacheFile, PostsListingData(nowSec, listed)) }
        container.userStore.put(KEY_LAST_CHECK, nowSec.toString())

        // First run: the key is created with every listed post as read, so nothing counts as new.
        val hadReadState = PostsReadState.getReadPaths(container.userStore) != null
        val read = PostsReadState.initializeIfFirstRun(container.userStore, listed.map { it.path })
        val newPosts = newUnreadPosts(listed, known, if (hadReadState) read else null)
        val (title, text) = newPostsNotificationText(newPosts) ?: return emptyList()
        post(context, title, text)
        return newPosts
    }

    private fun post(context: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "New posts", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open", "posts")
        }
        val pending = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notif)
    }
}
