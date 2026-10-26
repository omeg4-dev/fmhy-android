package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.data.CheckPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckPolicyTest {

    private fun sampleItem(verdict: ScanVerdict? = null): DownloadItem {
        val check = verdict?.let {
            ScanResult(
                kind = ScanKind.FILE,
                sha256 = "dummy-sha",
                services = listOf(
                    ServiceResult(
                        service = ScanService.VIRUSTOTAL,
                        verdict = it,
                    ),
                ),
                checkedAt = 1000L,
            )
        }
        return DownloadItem(
            id = 1L,
            url = "https://example.com/file",
            fileName = "file.bin",
            check = check,
        )
    }

    @Test
    fun testAfterDownloadSpecificCases() {
        val defaultPrefs = CheckPrefs()

        // default prefs + x.apk -> (false, false, true)
        assertEquals(
            AfterDownload(hold = false, checkNow = false, keyPrompt = true),
            CheckPolicy.afterDownload("x.apk", defaultPrefs),
        )

        // default + x.pdf -> (false, false, false)
        assertEquals(
            AfterDownload(hold = false, checkNow = false, keyPrompt = false),
            CheckPolicy.afterDownload("x.pdf", defaultPrefs),
        )

        val vtKeyPrefs = CheckPrefs(virusTotalOn = true, virusTotalKey = "valid-key")

        // VT key + x.apk -> (false, true, false)
        assertEquals(
            AfterDownload(hold = false, checkNow = true, keyPrompt = false),
            CheckPolicy.afterDownload("x.apk", vtKeyPrefs),
        )

        // VT key + x.pdf -> (false, false, false)
        assertEquals(
            AfterDownload(hold = false, checkNow = false, keyPrompt = false),
            CheckPolicy.afterDownload("x.pdf", vtKeyPrefs),
        )

        // VT key + autoCheckRisky=false + x.apk -> no check: (false, false, false)
        val vtKeyNoAutoPrefs = vtKeyPrefs.copy(autoCheckRisky = false)
        assertEquals(
            AfterDownload(hold = false, checkNow = false, keyPrompt = false),
            CheckPolicy.afterDownload("x.apk", vtKeyNoAutoPrefs),
        )

        // hold + no key -> (true, false, true)
        val holdNoKeyPrefs = CheckPrefs(holdUntilChecked = true)
        assertEquals(
            AfterDownload(hold = true, checkNow = false, keyPrompt = true),
            CheckPolicy.afterDownload("x.pdf", holdNoKeyPrefs),
        )
        assertEquals(
            AfterDownload(hold = true, checkNow = false, keyPrompt = true),
            CheckPolicy.afterDownload("x.apk", holdNoKeyPrefs),
        )

        // hold + no key + keyPromptShown -> (true, false, false)
        val holdPromptShownPrefs = holdNoKeyPrefs.copy(keyPromptShown = true)
        assertEquals(
            AfterDownload(hold = true, checkNow = false, keyPrompt = false),
            CheckPolicy.afterDownload("x.pdf", holdPromptShownPrefs),
        )

        // hold + key + x.pdf -> (true, true, false)
        val holdKeyPrefs = vtKeyPrefs.copy(holdUntilChecked = true)
        assertEquals(
            AfterDownload(hold = true, checkNow = true, keyPrompt = false),
            CheckPolicy.afterDownload("x.pdf", holdKeyPrefs),
        )

        // service on but blank key counts as not ready
        val blankKeyPrefs = CheckPrefs(virusTotalOn = true, virusTotalKey = "   ")
        assertEquals(
            AfterDownload(hold = false, checkNow = false, keyPrompt = true),
            CheckPolicy.afterDownload("x.apk", blankKeyPrefs),
        )
    }

    @Test
    fun testAfterDownloadTruthTable() {
        val holds = listOf(false, true)
        val readies = listOf(false, true)
        val autoCheckRiskies = listOf(false, true)
        val fileNames = listOf("app.apk" to true, "doc.pdf" to false)
        val keyPromptShowns = listOf(false, true)

        for (hold in holds) {
            for (ready in readies) {
                for (autoCheckRisky in autoCheckRiskies) {
                    for ((fileName, risky) in fileNames) {
                        for (keyPromptShown in keyPromptShowns) {
                            val prefs = CheckPrefs(
                                virusTotalOn = ready,
                                virusTotalKey = if (ready) "key" else "",
                                holdUntilChecked = hold,
                                autoCheckRisky = autoCheckRisky,
                                keyPromptShown = keyPromptShown,
                            )

                            val expectedHold = hold
                            val expectedCheckNow = ready && (hold || (autoCheckRisky && risky))
                            val expectedKeyPrompt = !ready && !keyPromptShown && ((autoCheckRisky && risky) || hold)

                            val result = CheckPolicy.afterDownload(fileName, prefs)
                            assertEquals(
                                "Truth table mismatch for hold=$hold, ready=$ready, autoCheckRisky=$autoCheckRisky, risky=$risky, keyPromptShown=$keyPromptShown",
                                AfterDownload(expectedHold, expectedCheckNow, expectedKeyPrompt),
                                result,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testRestoredStatus() {
        assertEquals(DownloadStatus.WAITING, CheckPolicy.restoredStatus(DownloadStatus.WAITING))
        assertEquals(DownloadStatus.WAITING_WIFI, CheckPolicy.restoredStatus(DownloadStatus.WAITING_WIFI))
        assertEquals(DownloadStatus.WAITING, CheckPolicy.restoredStatus(DownloadStatus.RUNNING))
        assertEquals(DownloadStatus.PAUSED, CheckPolicy.restoredStatus(DownloadStatus.PAUSED))
        assertEquals(DownloadStatus.FAILED, CheckPolicy.restoredStatus(DownloadStatus.FAILED))
        assertEquals(DownloadStatus.AWAITING_DECISION, CheckPolicy.restoredStatus(DownloadStatus.CHECKING))
        assertEquals(DownloadStatus.AWAITING_DECISION, CheckPolicy.restoredStatus(DownloadStatus.AWAITING_DECISION))
        assertEquals(DownloadStatus.FINISHED, CheckPolicy.restoredStatus(DownloadStatus.FINISHED))
    }

    @Test
    fun testNeedsExtraConfirm() {
        val itemNoCheck = sampleItem(verdict = null)
        assertFalse(CheckPolicy.needsExtraConfirm(itemNoCheck))

        val itemNoFlags = sampleItem(verdict = ScanVerdict.NO_FLAGS)
        assertFalse(CheckPolicy.needsExtraConfirm(itemNoFlags))

        val itemSuspicious = sampleItem(verdict = ScanVerdict.SUSPICIOUS)
        assertFalse(CheckPolicy.needsExtraConfirm(itemSuspicious))

        val itemUnknown = sampleItem(verdict = ScanVerdict.UNKNOWN)
        assertFalse(CheckPolicy.needsExtraConfirm(itemUnknown))

        val itemDangerous = sampleItem(verdict = ScanVerdict.DANGEROUS)
        assertTrue(CheckPolicy.needsExtraConfirm(itemDangerous))
    }
}
