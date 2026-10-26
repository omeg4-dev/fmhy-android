package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskyDownloadTest {

    @Test
    fun testAllRiskyExtensionsRecognized() {
        val extensions = listOf(
            "apk", "xapk", "apks", "exe", "msi", "bat", "cmd", "scr", "dmg", "pkg", "jar", "vbs", "ps1"
        )
        for (ext in extensions) {
            assertTrue("Expected $ext to be risky", RiskyDownload.isRiskyExtension("file.$ext"))
            assertTrue("Expected uppercase $ext to be risky", RiskyDownload.isRiskyExtension("file.${ext.uppercase()}"))
        }
    }

    @Test
    fun testSafeExtensionsNotRisky() {
        val safe = listOf("txt", "pdf", "zip", "tar.gz", "mp4", "mp3", "png", "jpg", "html", "epub", "iso")
        for (ext in safe) {
            assertFalse("Expected $ext to NOT be risky", RiskyDownload.isRiskyExtension("download.$ext"))
        }
    }

    @Test
    fun testCaseInsensitivity() {
        assertTrue(RiskyDownload.isRiskyExtension("Setup.EXE"))
        assertTrue(RiskyDownload.isRiskyExtension("app_release.Apk"))
        assertTrue(RiskyDownload.isRiskyExtension("script.BAT"))
        assertTrue(RiskyDownload.isRiskyExtension("INSTALLER.MSI"))
    }

    @Test
    fun testGuessFileNameAndUrlInputs() {
        // Inputs resembling URLUtil.guessFileName or full URLs with query parameters
        assertTrue(RiskyDownload.isRiskyExtension("https://example.com/downloads/setup.exe?token=xyz#dl"))
        assertTrue(RiskyDownload.isRiskyExtension("installer.apk."))
        assertTrue(RiskyDownload.isRiskyExtension("my-app.xapk   "))
        assertFalse(RiskyDownload.isRiskyExtension("https://example.com/api/get?file=exe"))
        assertFalse(RiskyDownload.isRiskyExtension("no_extension_file"))
        assertFalse(RiskyDownload.isRiskyExtension(""))
    }

    @Test
    fun testWarningMessage() {
        val msg = RiskyDownload.warningMessage("evil.com")
        assertEquals("This file can install software. Only download it if you trust evil.com.", msg)
    }

    @Test
    fun testAutoCheckTypeTrue() {
        assertTrue(RiskyDownload.isAutoCheckType("test.apk"))
        assertTrue(RiskyDownload.isAutoCheckType("script.sh"))
        assertTrue(RiskyDownload.isAutoCheckType("app.AppImage"))
    }

    @Test
    fun testAutoCheckTypeFalse() {
        assertFalse(RiskyDownload.isAutoCheckType("document.pdf"))
        assertFalse(RiskyDownload.isAutoCheckType("video.mp4"))
    }
}
