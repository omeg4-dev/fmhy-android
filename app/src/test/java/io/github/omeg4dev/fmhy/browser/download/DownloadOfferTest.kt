package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.browser.InterstitialState
import io.github.omeg4dev.fmhy.browser.WarningReason
import io.github.omeg4dev.fmhy.work.NetworkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadOfferTest {

    @Test
    fun `risky extension with warn off is not risky and does not force confirmation`() {
        val now = 100_000L
        val offer = buildOffer(
            url = "https://example.com/app.apk",
            fileName = "app.apk",
            totalBytes = 1024,
            mimeType = "application/vnd.android.package-archive",
            host = "example.com",
            referer = null,
            userAgent = null,
            isPrivate = false,
            warnRiskyDownloads = false,
            downloadWifiOnly = false,
            networkKind = NetworkKind.WIFI,
            folderLabel = "Downloads",
            interstitial = null,
            askBeforeDownload = false,
            lastTouchAt = now - 1000L,
            nowMs = now,
        )

        assertFalse(offer.isRisky)
        assertFalse(offer.needsConfirmation)
    }

    @Test
    fun `wifi only on mobile results in waitsForWifi and needsConfirmation`() {
        val now = 100_000L
        val offer = buildOffer(
            url = "https://example.com/file.txt",
            fileName = "file.txt",
            totalBytes = 2048,
            mimeType = "text/plain",
            host = "example.com",
            referer = null,
            userAgent = null,
            isPrivate = false,
            warnRiskyDownloads = true,
            downloadWifiOnly = true,
            networkKind = NetworkKind.MOBILE,
            folderLabel = "Downloads",
            interstitial = null,
            askBeforeDownload = false,
            lastTouchAt = now - 1000L,
            nowMs = now,
        )

        assertTrue(offer.waitsForWifi)
        assertTrue(offer.needsConfirmation)
    }

    @Test
    fun `recent touch with ask off and not risky results in no confirmation`() {
        val now = 100_000L
        val offer = buildOffer(
            url = "https://example.com/photo.jpg",
            fileName = "photo.jpg",
            totalBytes = 5000,
            mimeType = "image/jpeg",
            host = "example.com",
            referer = "https://example.com",
            userAgent = null,
            isPrivate = false,
            warnRiskyDownloads = true,
            downloadWifiOnly = false,
            networkKind = NetworkKind.WIFI,
            folderLabel = "Downloads",
            interstitial = null,
            askBeforeDownload = false,
            lastTouchAt = now - 2000L, // within 5s window
            nowMs = now,
        )

        assertFalse(offer.isRisky)
        assertFalse(offer.waitsForWifi)
        assertFalse(offer.needsConfirmation)
    }

    @Test
    fun `no touch results in confirmation`() {
        val now = 100_000L
        val offer = buildOffer(
            url = "https://example.com/photo.jpg",
            fileName = "photo.jpg",
            totalBytes = 5000,
            mimeType = "image/jpeg",
            host = "example.com",
            referer = null,
            userAgent = null,
            isPrivate = false,
            warnRiskyDownloads = true,
            downloadWifiOnly = false,
            networkKind = NetworkKind.WIFI,
            folderLabel = "Downloads",
            interstitial = null,
            askBeforeDownload = false,
            lastTouchAt = 0L,
            nowMs = now,
        )

        assertTrue(offer.needsConfirmation)
    }

    @Test
    fun `interstitial present is retained even when no confirmation needed`() {
        val now = 100_000L
        val interstitial = InterstitialState(
            host = "example.com",
            url = "https://example.com/file.bin",
            reasons = listOf(WarningReason.UNSAFE),
            note = "Known unsafe host",
            filterRule = null,
        )
        val offer = buildOffer(
            url = "https://example.com/file.bin",
            fileName = "file.bin",
            totalBytes = 100,
            mimeType = "application/octet-stream",
            host = "example.com",
            referer = null,
            userAgent = null,
            isPrivate = false,
            warnRiskyDownloads = false,
            downloadWifiOnly = false,
            networkKind = NetworkKind.WIFI,
            folderLabel = "Downloads",
            interstitial = interstitial,
            askBeforeDownload = false,
            lastTouchAt = now - 1000L,
            nowMs = now,
        )

        assertNotNull(offer.interstitial)
        assertEquals(interstitial, offer.interstitial)
        assertFalse(offer.needsConfirmation)
    }

    @Test
    fun `sizeText helper formatting`() {
        assertNull(DownloadOfferText.sizeText(-1L))
        assertNull(DownloadOfferText.sizeText(0L))
        assertEquals("100 B", DownloadOfferText.sizeText(100L))
        assertEquals("1.0 KB", DownloadOfferText.sizeText(1024L))
    }

    @Test
    fun `started message formatting`() {
        assertEquals("Downloading test.apk", DownloadOfferText.started("test.apk", waits = false))
        assertEquals("Waiting for Wi-Fi: test.apk", DownloadOfferText.started("test.apk", waits = true))
    }

    @Test
    fun `buildOffer defaults sourceStatus to null`() {
        val now = 100_000L
        val offer = buildOffer(
            url = "https://example.com/file.bin",
            fileName = "file.bin",
            totalBytes = 100,
            mimeType = "application/octet-stream",
            host = "example.com",
            referer = null,
            userAgent = null,
            isPrivate = false,
            warnRiskyDownloads = false,
            downloadWifiOnly = false,
            networkKind = NetworkKind.WIFI,
            folderLabel = "Downloads",
            interstitial = null,
            askBeforeDownload = false,
            lastTouchAt = now - 1000L,
            nowMs = now,
        )
        assertNull(offer.sourceStatus)
    }

    @Test
    fun `buildOffer carries sourceStatus when provided`() {
        val now = 100_000L
        val status = DownloadSourceStatus(
            kind = SourceKind.STARRED,
            isMirror = false,
            fileHost = "example.com",
            pageHost = null,
            differs = false,
            fileKind = SourceKind.STARRED,
            pageKind = null,
            entryId = "entry-1",
            wikiDateMs = 12345L,
            lookalikeOf = null,
        )
        val offer = buildOffer(
            url = "https://example.com/file.bin",
            fileName = "file.bin",
            totalBytes = 100,
            mimeType = "application/octet-stream",
            host = "example.com",
            referer = null,
            userAgent = null,
            isPrivate = false,
            warnRiskyDownloads = false,
            downloadWifiOnly = false,
            networkKind = NetworkKind.WIFI,
            folderLabel = "Downloads",
            interstitial = null,
            askBeforeDownload = false,
            lastTouchAt = now - 1000L,
            nowMs = now,
            sourceStatus = status,
        )
        assertNotNull(offer.sourceStatus)
        assertEquals(status, offer.sourceStatus)
    }
}
