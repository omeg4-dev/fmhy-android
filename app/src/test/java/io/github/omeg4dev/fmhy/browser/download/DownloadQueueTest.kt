package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.data.user.DownloadRecord
import io.github.omeg4dev.fmhy.work.NetworkKind
import io.github.omeg4dev.fmhy.work.NetworkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueTest {

    private fun makeItem(
        id: Long,
        createdAt: Long,
        status: DownloadStatus,
        allowMobile: Boolean = false,
        resumable: Boolean = true,
        downloadedBytes: Long = 0,
        totalBytes: Long = 1000,
        error: String? = null,
    ): DownloadItem {
        return DownloadItem(
            id = id,
            url = "https://example.com/$id",
            fileName = "file$id",
            status = status,
            createdAt = createdAt,
            allowMobile = allowMobile,
            resumable = resumable,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            error = error,
        )
    }

    @Test
    fun testFourWaitingOnWifiFirstThreeStart() {
        val items = listOf(
            makeItem(1, 100, DownloadStatus.WAITING),
            makeItem(2, 200, DownloadStatus.WAITING),
            makeItem(3, 300, DownloadStatus.WAITING),
            makeItem(4, 400, DownloadStatus.WAITING),
        )
        val tr = DownloadQueue.transitions(items, wifiOnly = true, network = NetworkKind.WIFI)
        assertEquals(
            listOf(
                Transition(1, DownloadStatus.RUNNING),
                Transition(2, DownloadStatus.RUNNING),
                Transition(3, DownloadStatus.RUNNING),
            ),
            tr,
        )
    }

    @Test
    fun testThreeRunningOneWaitingFinishingOneStartsWaiting() {
        val runningItems = listOf(
            makeItem(1, 100, DownloadStatus.RUNNING),
            makeItem(2, 200, DownloadStatus.RUNNING),
            makeItem(3, 300, DownloadStatus.RUNNING),
            makeItem(4, 400, DownloadStatus.WAITING),
        )
        // Full slots: item 4 stays WAITING (no transition)
        val trFull = DownloadQueue.transitions(runningItems, wifiOnly = true, network = NetworkKind.WIFI)
        assertTrue(trFull.isEmpty())

        // One item finishes
        val afterFinished = listOf(
            makeItem(1, 100, DownloadStatus.FINISHED),
            makeItem(2, 200, DownloadStatus.RUNNING),
            makeItem(3, 300, DownloadStatus.RUNNING),
            makeItem(4, 400, DownloadStatus.WAITING),
        )
        val trAfter = DownloadQueue.transitions(afterFinished, wifiOnly = true, network = NetworkKind.WIFI)
        assertEquals(listOf(Transition(4, DownloadStatus.RUNNING)), trAfter)
    }

    @Test
    fun testWifiLostMidDownloadTransitionToWaitingWifiAndBack() {
        val items = listOf(
            makeItem(1, 100, DownloadStatus.WAITING),
            makeItem(2, 200, DownloadStatus.RUNNING),
        )
        // Wi-Fi lost -> mobile
        val trMobile = DownloadQueue.transitions(items, wifiOnly = true, network = NetworkKind.MOBILE)
        assertEquals(
            listOf(
                Transition(1, DownloadStatus.WAITING_WIFI),
                Transition(2, DownloadStatus.WAITING_WIFI),
            ),
            trMobile,
        )

        // Same items now in WAITING_WIFI state, Wi-Fi returns
        val wifiWaitingItems = listOf(
            makeItem(1, 100, DownloadStatus.WAITING_WIFI),
            makeItem(2, 200, DownloadStatus.WAITING_WIFI),
        )
        val trWifiReturns = DownloadQueue.transitions(wifiWaitingItems, wifiOnly = true, network = NetworkKind.WIFI)
        assertEquals(
            listOf(
                Transition(1, DownloadStatus.RUNNING),
                Transition(2, DownloadStatus.RUNNING),
            ),
            trWifiReturns,
        )
    }

    @Test
    fun testWifiOnlyWithMobileAllowed() {
        val items = listOf(
            makeItem(1, 100, DownloadStatus.WAITING, allowMobile = true),
            makeItem(2, 200, DownloadStatus.RUNNING, allowMobile = true),
        )
        val tr = DownloadQueue.transitions(items, wifiOnly = true, network = NetworkKind.MOBILE)
        // item 1 can start, item 2 stays running
        assertEquals(listOf(Transition(1, DownloadStatus.RUNNING)), tr)
    }

    @Test
    fun testNetworkNoneTransitionsToWaitingAndBackOnline() {
        val items = listOf(
            makeItem(1, 100, DownloadStatus.RUNNING),
            makeItem(2, 200, DownloadStatus.WAITING_WIFI),
        )
        // Offline: both become WAITING
        val trOffline = DownloadQueue.transitions(items, wifiOnly = true, network = NetworkKind.NONE)
        assertEquals(
            listOf(
                Transition(1, DownloadStatus.WAITING),
                Transition(2, DownloadStatus.WAITING),
            ),
            trOffline,
        )

        // Back online on Wi-Fi
        val waitingItems = listOf(
            makeItem(1, 100, DownloadStatus.WAITING),
            makeItem(2, 200, DownloadStatus.WAITING),
        )
        val trOnline = DownloadQueue.transitions(waitingItems, wifiOnly = true, network = NetworkKind.WIFI)
        assertEquals(
            listOf(
                Transition(1, DownloadStatus.RUNNING),
                Transition(2, DownloadStatus.RUNNING),
            ),
            trOnline,
        )
    }

    @Test
    fun testWifiOnlyOffOnMobileRuns() {
        val items = listOf(
            makeItem(1, 100, DownloadStatus.WAITING, allowMobile = false),
        )
        val tr = DownloadQueue.transitions(items, wifiOnly = false, network = NetworkKind.MOBILE)
        assertEquals(listOf(Transition(1, DownloadStatus.RUNNING)), tr)
    }

    @Test
    fun testMeteredWifiCountsAsMobile() {
        val meteredNetwork = NetworkPolicy.kindOf(isWifiOrEthernet = true, isConnected = true, isMetered = true)
        assertEquals(NetworkKind.MOBILE, meteredNetwork)

        val items = listOf(makeItem(1, 100, DownloadStatus.WAITING))
        val tr = DownloadQueue.transitions(items, wifiOnly = true, network = meteredNetwork)
        assertEquals(listOf(Transition(1, DownloadStatus.WAITING_WIFI)), tr)
    }

    @Test
    fun testInactiveStatusesNeverTransitionOrTakeSlots() {
        val items = listOf(
            makeItem(1, 100, DownloadStatus.PAUSED),
            makeItem(2, 200, DownloadStatus.FAILED),
            makeItem(3, 300, DownloadStatus.FINISHED),
            makeItem(4, 400, DownloadStatus.WAITING),
        )
        val tr = DownloadQueue.transitions(items, wifiOnly = true, network = NetworkKind.WIFI)
        // Item 4 starts running; items 1, 2, 3 never transition and do not consume running slots
        assertEquals(listOf(Transition(4, DownloadStatus.RUNNING)), tr)
    }

    @Test
    fun testRetryNonResumable() {
        val original = makeItem(1, 100, DownloadStatus.FAILED, resumable = false, downloadedBytes = 500, error = "Network err")
        val retried = DownloadQueue.retry(original)
        assertEquals(DownloadStatus.WAITING, retried.status)
        assertEquals(0L, retried.downloadedBytes)
        assertTrue(retried.restartNote)
        assertNull(retried.error)
        assertNull(retried.speedBps)
        assertNull(retried.etaSeconds)
    }

    @Test
    fun testRetryResumable() {
        val original = makeItem(1, 100, DownloadStatus.FAILED, resumable = true, downloadedBytes = 500, error = "Timeout")
        val retried = DownloadQueue.retry(original)
        assertEquals(DownloadStatus.WAITING, retried.status)
        assertEquals(500L, retried.downloadedBytes)
        assertFalse(retried.restartNote)
        assertNull(retried.error)
        assertNull(retried.speedBps)
        assertNull(retried.etaSeconds)
    }

    @Test
    fun testActiveSummary() {
        // No active items
        val noActive = listOf(makeItem(1, 100, DownloadStatus.PAUSED), makeItem(2, 200, DownloadStatus.FINISHED))
        assertNull(activeSummary(noActive))

        // Any active total unknown
        val withUnknownTotal = listOf(
            makeItem(1, 100, DownloadStatus.RUNNING, downloadedBytes = 50, totalBytes = 100),
            makeItem(2, 200, DownloadStatus.WAITING, downloadedBytes = 0, totalBytes = -1),
        )
        val summaryUnknown = activeSummary(withUnknownTotal)
        assertEquals(2, summaryUnknown?.count)
        assertNull(summaryUnknown?.fraction)

        // All active totals known
        val allKnown = listOf(
            makeItem(1, 100, DownloadStatus.RUNNING, downloadedBytes = 250, totalBytes = 1000),
            makeItem(2, 200, DownloadStatus.RUNNING, downloadedBytes = 250, totalBytes = 1000),
            makeItem(3, 300, DownloadStatus.FINISHED, downloadedBytes = 500, totalBytes = 500),
        )
        val summaryKnown = activeSummary(allKnown)
        assertEquals(2, summaryKnown?.count)
        // 500 / 2000 = 0.25f
        assertEquals(0.25f, summaryKnown?.fraction ?: 0f, 0.001f)
    }

    @Test
    fun testToRecordToItemRoundTrip() {
        val item = DownloadItem(
            id = 42L,
            url = "https://example.com/item.apk",
            fileName = "item.apk",
            mimeType = "application/vnd.android.package-archive",
            sourceHost = "example.com",
            referer = "https://example.com",
            userAgent = "UA",
            totalBytes = 2048L,
            downloadedBytes = 1024L,
            status = DownloadStatus.RUNNING,
            resumable = true,
            restartNote = false,
            allowMobile = true,
            isPrivate = true, // transient; drops on toRecord
            error = null,
            fileUri = "content://path",
            folderLabel = "Downloads",
            etag = "\"etag\"",
            lastModified = "last-mod",
            createdAt = 123456L,
            finishedAt = null,
            speedBps = 100000L, // transient
            etaSeconds = 5L, // transient
        )
        val rec = item.toRecord()
        assertEquals(item.id, rec.id)
        assertEquals(item.url, rec.url)
        assertEquals(item.fileName, rec.fileName)
        assertEquals("RUNNING", rec.status)
        assertEquals(item.createdAt, rec.createdAt)

        val back = rec.toItem()
        assertEquals(item.id, back.id)
        assertEquals(item.status, back.status)
        assertFalse(back.isPrivate)
        assertNull(back.speedBps)
        assertNull(back.etaSeconds)
        assertTrue(back.isApk)
        assertEquals(0.5f, back.fraction ?: 0f, 0.001f)
    }

    @Test
    fun testToItemUnknownStatusMapsToFailed() {
        val rec = DownloadRecord(
            url = "https://example.com/test",
            fileName = "test",
            status = "SOME_UNKNOWN_STATE",
            createdAt = 100,
        )
        val item = rec.toItem()
        assertEquals(DownloadStatus.FAILED, item.status)
    }

    @Test
    fun testDownloadPolicyNeedsConfirmation() {
        val now = 10_000L
        val recentTouch = 9_000L // 1s ago <= 5s window

        // None triggered -> false
        assertFalse(DownloadPolicy.needsConfirmation(false, false, false, recentTouch, now))

        // Each trigger alone -> true
        assertTrue(DownloadPolicy.needsConfirmation(true, false, false, recentTouch, now))
        assertTrue(DownloadPolicy.needsConfirmation(false, true, false, recentTouch, now))
        assertTrue(DownloadPolicy.needsConfirmation(false, false, true, recentTouch, now))
        assertTrue(DownloadPolicy.needsConfirmation(false, false, false, 0L, now))
        assertTrue(DownloadPolicy.needsConfirmation(false, false, false, -1L, now))
        assertTrue(DownloadPolicy.needsConfirmation(false, false, false, now - 6_000L, now))
    }

    @Test
    fun testDownloadPolicyFormatEta() {
        assertEquals("10 s left", DownloadPolicy.formatEta(10))
        assertEquals("59 s left", DownloadPolicy.formatEta(59))
        assertEquals("1 min left", DownloadPolicy.formatEta(60))
        assertEquals("2 min left", DownloadPolicy.formatEta(61))
        assertEquals("59 min left", DownloadPolicy.formatEta(3540))
        assertEquals("1 h left", DownloadPolicy.formatEta(3600))
        assertEquals("1 h 1 min left", DownloadPolicy.formatEta(3660))
        assertEquals("2 h left", DownloadPolicy.formatEta(7200))
        assertEquals("2 h 5 min left", DownloadPolicy.formatEta(7500))
    }

    @Test
    fun testDownloadPolicyFormatSpeedAndBytes() {
        assertEquals("0 B", DownloadPolicy.formatBytes(0))
        assertEquals("512 B", DownloadPolicy.formatBytes(512))
        assertEquals("1.5 KB", DownloadPolicy.formatBytes(1536))
        assertEquals("12.0 MB", DownloadPolicy.formatBytes(12 * 1024 * 1024))
        assertEquals("1.0 GB", DownloadPolicy.formatBytes(1024L * 1024L * 1024L))

        assertEquals("512 B/s", DownloadPolicy.formatSpeed(512))
        assertEquals("800.0 KB/s", DownloadPolicy.formatSpeed(800 * 1024))
        assertEquals("2.5 MB/s", DownloadPolicy.formatSpeed((2.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun testDownloadPolicyProgressLine() {
        val full = DownloadPolicy.progressLine(
            downloaded = (1.2 * 1024 * 1024).toLong(),
            total = (5.0 * 1024 * 1024).toLong(),
            speedBps = 800 * 1024,
            etaSeconds = 10,
        )
        assertEquals("1.2 MB of 5.0 MB · 800.0 KB/s · 10 s left", full)

        val unknownTotal = DownloadPolicy.progressLine(
            downloaded = (1.2 * 1024 * 1024).toLong(),
            total = -1,
            speedBps = 800 * 1024,
            etaSeconds = null,
        )
        assertEquals("1.2 MB · 800.0 KB/s", unknownTotal)

        val sizeOnly = DownloadPolicy.progressLine(
            downloaded = (1.2 * 1024 * 1024).toLong(),
            total = -1,
            speedBps = null,
            etaSeconds = null,
        )
        assertEquals("1.2 MB", sizeOnly)
    }
}
