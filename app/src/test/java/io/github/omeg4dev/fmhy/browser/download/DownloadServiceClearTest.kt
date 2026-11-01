package io.github.omeg4dev.fmhy.browser.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadServiceClearTest {

    @Test
    fun clearsEveryFinishedDownloadNotification() {
        val active = listOf(7201 to "downloads", 7650 to "downloads", 7900 to "file_checks")
        assertEquals(listOf(7201, 7650, 7900), DownloadService.finishedIdsToClear(active))
    }

    @Test
    fun keepsRunningDownloadForegroundNotification() {
        val active = listOf(DownloadService.NOTIFICATION_ID to "downloads", 7201 to "downloads")
        assertEquals(listOf(7201), DownloadService.finishedIdsToClear(active))
    }

    @Test
    fun ignoresOtherChannels() {
        val active = listOf(11 to "background_media", 7201 to "downloads")
        assertEquals(listOf(7201), DownloadService.finishedIdsToClear(active))
    }

    @Test
    fun emptyWhenNothingPosted() {
        assertEquals(emptyList<Int>(), DownloadService.finishedIdsToClear(emptyList()))
    }
}
