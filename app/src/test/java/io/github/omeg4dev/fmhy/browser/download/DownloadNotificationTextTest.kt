package io.github.omeg4dev.fmhy.browser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNotificationTextTest {

    private fun sampleItem(
        id: Long = 1L,
        fileName: String = "example.zip",
        status: DownloadStatus = DownloadStatus.RUNNING,
        totalBytes: Long = 1000L,
        downloadedBytes: Long = 500L,
        speedBps: Long? = 1024L,
        etaSeconds: Long? = 30L,
        isPrivate: Boolean = false,
    ) = DownloadItem(
        id = id,
        url = "https://example.com/example.zip",
        fileName = fileName,
        status = status,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        speedBps = speedBps,
        etaSeconds = etaSeconds,
        isPrivate = isPrivate,
    )

    @Test
    fun testNoActiveItemsReturnsNull() {
        val items = listOf(
            sampleItem(status = DownloadStatus.FINISHED),
            sampleItem(status = DownloadStatus.FAILED),
            sampleItem(status = DownloadStatus.PAUSED),
        )
        assertNull(DownloadNotificationText.ongoing(items))
    }

    @Test
    fun testSingleRunningKnownTotal() {
        val item = sampleItem(
            fileName = "movie.mp4",
            totalBytes = 2_000_000L,
            downloadedBytes = 1_000_000L,
            speedBps = 100 * 1024L,
            etaSeconds = 10L,
        )
        val content = DownloadNotificationText.ongoing(listOf(item))
        assertNotNull(content)
        assertEquals("movie.mp4", content!!.title)
        assertEquals("50% · 100.0 KB/s · 10 s left", content.text)
        assertEquals(50, content.percent)
        assertTrue(content.showProgress)
        assertTrue(content.single)
    }

    @Test
    fun testSingleRunningUnknownTotal() {
        val item = sampleItem(
            fileName = "stream.bin",
            totalBytes = -1L,
            downloadedBytes = 512 * 1024L,
            speedBps = 64 * 1024L,
            etaSeconds = null,
        )
        val content = DownloadNotificationText.ongoing(listOf(item))
        assertNotNull(content)
        assertEquals("stream.bin", content!!.title)
        assertEquals("512.0 KB · 64.0 KB/s", content.text)
        assertNull(content.percent)
        assertTrue(content.showProgress)
        assertTrue(content.single)
    }

    @Test
    fun testPrivateSingleRunningHidesFileName() {
        val item = sampleItem(
            fileName = "secret_file.pdf",
            isPrivate = true,
            totalBytes = 1000L,
            downloadedBytes = 250L,
            speedBps = null,
            etaSeconds = null,
        )
        val content = DownloadNotificationText.ongoing(listOf(item))
        assertNotNull(content)
        assertEquals("Private download", content!!.title)
        assertFalse(content.title.contains("secret_file"))
        assertFalse(content.text.contains("secret_file"))
        assertEquals("25%", content.text)
        assertEquals(25, content.percent)
        assertTrue(content.showProgress)
        assertTrue(content.single)
    }

    @Test
    fun testSingleWaitingForWifi() {
        val item = sampleItem(
            fileName = "huge_game.iso",
            status = DownloadStatus.WAITING_WIFI,
            totalBytes = 10_000_000L,
            downloadedBytes = 0L,
        )
        val content = DownloadNotificationText.ongoing(listOf(item))
        assertNotNull(content)
        assertEquals("huge_game.iso", content!!.title)
        assertEquals("Waiting for Wi-Fi", content.text)
        assertNull(content.percent)
        assertFalse(content.showProgress)
        assertTrue(content.single)
    }

    @Test
    fun testMultipleRunningKnownTotal() {
        val item1 = sampleItem(id = 1L, status = DownloadStatus.RUNNING, totalBytes = 1000L, downloadedBytes = 500L)
        val item2 = sampleItem(id = 2L, status = DownloadStatus.RUNNING, totalBytes = 1000L, downloadedBytes = 500L)
        val content = DownloadNotificationText.ongoing(listOf(item1, item2))
        assertNotNull(content)
        assertEquals("2 downloads", content!!.title)
        assertEquals("50% · 2 downloading", content.text)
        assertEquals(50, content.percent)
        assertTrue(content.showProgress)
        assertFalse(content.single)
    }

    @Test
    fun testMultipleRunningUnknownTotal() {
        val item1 = sampleItem(id = 1L, status = DownloadStatus.RUNNING, totalBytes = -1L, downloadedBytes = 500L)
        val item2 = sampleItem(id = 2L, status = DownloadStatus.RUNNING, totalBytes = 1000L, downloadedBytes = 500L)
        val content = DownloadNotificationText.ongoing(listOf(item1, item2))
        assertNotNull(content)
        assertEquals("2 downloads", content!!.title)
        assertEquals("2 downloading", content.text)
        assertNull(content.percent)
        assertTrue(content.showProgress)
        assertFalse(content.single)
    }

    @Test
    fun testMultipleWaitingWithWaitingWifi() {
        val item1 = sampleItem(id = 1L, status = DownloadStatus.WAITING_WIFI)
        val item2 = sampleItem(id = 2L, status = DownloadStatus.WAITING)
        val content = DownloadNotificationText.ongoing(listOf(item1, item2))
        assertNotNull(content)
        assertEquals("2 downloads", content!!.title)
        assertEquals("Waiting for Wi-Fi", content.text)
        assertNull(content.percent)
        assertFalse(content.showProgress)
        assertFalse(content.single)
    }

    @Test
    fun testMultipleWaitingNoneWifi() {
        val item1 = sampleItem(id = 1L, status = DownloadStatus.WAITING)
        val item2 = sampleItem(id = 2L, status = DownloadStatus.WAITING)
        val content = DownloadNotificationText.ongoing(listOf(item1, item2))
        assertNotNull(content)
        assertEquals("2 downloads", content!!.title)
        assertEquals("Waiting", content.text)
        assertNull(content.percent)
        assertFalse(content.showProgress)
        assertFalse(content.single)
    }

    @Test
    fun testFinishedPrivateAndNormal() {
        val normal = sampleItem(fileName = "archive.tar", isPrivate = false)
        assertEquals("Download finished", DownloadNotificationText.finishedTitle(normal))
        assertEquals("archive.tar", DownloadNotificationText.finishedText(normal))

        val priv = sampleItem(fileName = "archive.tar", isPrivate = true)
        assertEquals("Private download finished", DownloadNotificationText.finishedTitle(priv))
        assertNull(DownloadNotificationText.finishedText(priv))
    }

    @Test
    fun testDecisionNeededNormalAndPrivate() {
        val awaitingNormal = sampleItem(fileName = "setup.apk", status = DownloadStatus.AWAITING_DECISION, isPrivate = false)
        assertEquals("Download waiting for your decision", DownloadNotificationText.finishedTitle(awaitingNormal))
        assertEquals("setup.apk", DownloadNotificationText.finishedText(awaitingNormal))

        val awaitingPrivate = sampleItem(fileName = "setup.apk", status = DownloadStatus.AWAITING_DECISION, isPrivate = true)
        assertEquals("Download waiting for your decision", DownloadNotificationText.finishedTitle(awaitingPrivate))
        assertNull(DownloadNotificationText.finishedText(awaitingPrivate))

        val checkingNormal = sampleItem(fileName = "setup.apk", status = DownloadStatus.CHECKING, isPrivate = false)
        assertEquals("Download waiting for your decision", DownloadNotificationText.finishedTitle(checkingNormal))
        assertEquals("setup.apk", DownloadNotificationText.finishedText(checkingNormal))

        val checkingPrivate = sampleItem(fileName = "setup.apk", status = DownloadStatus.CHECKING, isPrivate = true)
        assertEquals("Download waiting for your decision", DownloadNotificationText.finishedTitle(checkingPrivate))
        assertNull(DownloadNotificationText.finishedText(checkingPrivate))
    }
}
