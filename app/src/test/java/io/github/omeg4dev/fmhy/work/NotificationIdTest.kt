package io.github.omeg4dev.fmhy.work

import io.github.omeg4dev.fmhy.browser.download.DownloadService
import io.github.omeg4dev.fmhy.browser.media.BackgroundMediaService
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationIdTest {

    @Test
    fun allNotificationIdsAreDistinct() {
        val ids = listOf(
            "UpdateChecker" to UpdateChecker.NOTIFICATION_ID,
            "AppUpdateWorker" to AppUpdateWorker.NOTIFICATION_ID,
            "NewPostsChecker" to NewPostsChecker.NOTIFICATION_ID,
            "BackgroundMediaService" to BackgroundMediaService.NOTIFICATION_ID,
            "DownloadService" to DownloadService.NOTIFICATION_ID,
        )
        val duplicates = ids.groupBy { it.second }.filterValues { it.size > 1 }
        assertEquals(
            "Found duplicate notification IDs: $duplicates",
            emptyMap<Int, List<Pair<String, Int>>>(),
            duplicates,
        )
    }
}
