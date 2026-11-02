package io.github.omeg4dev.fmhy.browser.download

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckChipsTest {

    private val defaultMaxUpload: (ScanService) -> Long = { 32 * 1024 * 1024L }

    private fun makeResult(
        services: List<ServiceResult> = emptyList(),
        pending: List<PendingAnalysis> = emptyList(),
    ): ScanResult = ScanResult(
        kind = ScanKind.FILE,
        sha256 = "dummy_sha256",
        services = services,
        checkedAt = 1000L,
        pending = pending,
    )

    @Test
    fun testNoFlagsWithEngines() {
        val result = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.NO_FLAGS,
                    flagged = 0,
                    engines = 72,
                )
            )
        )
        val chips = CheckChips.build(
            result = result,
            readyServices = emptyList(),
            fileSize = 1024L,
            maxUploadBytes = defaultMaxUpload,
        )
        assertEquals(1, chips.size)
        val chip = chips[0]
        assertEquals(ScanService.VIRUSTOTAL, chip.service)
        assertEquals(CheckChipIcon.SHIELD_CHECK, chip.icon)
        assertEquals(CheckChipTone.OK, chip.tone)
        assertEquals("0/72", chip.text)
        assertEquals(CheckChipAction.SHOW_RESULT, chip.action)
    }

    @Test
    fun testSuspiciousWithEngines() {
        val result = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.SUSPICIOUS,
                    flagged = 3,
                    engines = 70,
                )
            )
        )
        val chips = CheckChips.build(
            result = result,
            readyServices = emptyList(),
            fileSize = 1024L,
            maxUploadBytes = defaultMaxUpload,
        )
        assertEquals(1, chips.size)
        val chip = chips[0]
        assertEquals(ScanService.VIRUSTOTAL, chip.service)
        assertEquals(CheckChipIcon.WARNING, chip.icon)
        assertEquals(CheckChipTone.WARN, chip.tone)
        assertEquals("3/70", chip.text)
        assertEquals(CheckChipAction.SHOW_RESULT, chip.action)
    }

    @Test
    fun testDangerousWithEngines() {
        val result = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.DANGEROUS,
                    flagged = 12,
                    engines = 70,
                )
            )
        )
        val chips = CheckChips.build(
            result = result,
            readyServices = emptyList(),
            fileSize = 1024L,
            maxUploadBytes = defaultMaxUpload,
        )
        assertEquals(1, chips.size)
        val chip = chips[0]
        assertEquals(ScanService.VIRUSTOTAL, chip.service)
        assertEquals(CheckChipIcon.SHIELD_X, chip.icon)
        assertEquals(CheckChipTone.DANGER, chip.tone)
        assertEquals("12/70", chip.text)
        assertEquals(CheckChipAction.SHOW_RESULT, chip.action)
    }

    @Test
    fun testNoFlagsWithoutEngines() {
        val result = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.HYBRID_ANALYSIS,
                    verdict = ScanVerdict.NO_FLAGS,
                    flagged = null,
                    engines = null,
                )
            )
        )
        val chips = CheckChips.build(
            result = result,
            readyServices = emptyList(),
            fileSize = 1024L,
            maxUploadBytes = defaultMaxUpload,
        )
        assertEquals(1, chips.size)
        val chip = chips[0]
        assertEquals(ScanService.HYBRID_ANALYSIS, chip.service)
        assertEquals(CheckChipIcon.SHIELD_CHECK, chip.icon)
        assertEquals(CheckChipTone.OK, chip.tone)
        assertEquals("No flags", chip.text)
        assertEquals(CheckChipAction.SHOW_RESULT, chip.action)
    }

    @Test
    fun testUnknownServiceReadySmallFile() {
        val result = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.UNKNOWN,
                )
            )
        )
        val chips = CheckChips.build(
            result = result,
            readyServices = listOf(ScanService.VIRUSTOTAL),
            fileSize = 1024L,
            maxUploadBytes = { 32 * 1024 * 1024L },
        )
        assertEquals(1, chips.size)
        val chip = chips[0]
        assertEquals(ScanService.VIRUSTOTAL, chip.service)
        assertEquals(CheckChipIcon.SHIELD_UNKNOWN, chip.icon)
        assertEquals(CheckChipTone.NEUTRAL, chip.tone)
        assertEquals("Unknown · Upload", chip.text)
        assertEquals(CheckChipAction.UPLOAD, chip.action)
    }

    @Test
    fun testUnknownServiceReadyTooBig() {
        val result = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.UNKNOWN,
                )
            )
        )
        val chips = CheckChips.build(
            result = result,
            readyServices = listOf(ScanService.VIRUSTOTAL),
            fileSize = 50 * 1024 * 1024L,
            maxUploadBytes = { 32 * 1024 * 1024L },
        )
        assertEquals(1, chips.size)
        val chip = chips[0]
        assertEquals(ScanService.VIRUSTOTAL, chip.service)
        assertEquals(CheckChipIcon.SHIELD_UNKNOWN, chip.icon)
        assertEquals(CheckChipTone.NEUTRAL, chip.tone)
        assertEquals("Unknown · too big", chip.text)
        assertEquals(CheckChipAction.TOO_BIG, chip.action)
    }

    @Test
    fun testUnknownServiceNotReady() {
        val result = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.UNKNOWN,
                )
            )
        )
        val chips = CheckChips.build(
            result = result,
            readyServices = emptyList(),
            fileSize = 1024L,
            maxUploadBytes = defaultMaxUpload,
        )
        assertEquals(1, chips.size)
        val chip = chips[0]
        assertEquals(ScanService.VIRUSTOTAL, chip.service)
        assertEquals(CheckChipIcon.SHIELD_UNKNOWN, chip.icon)
        assertEquals(CheckChipTone.NEUTRAL, chip.tone)
        assertEquals("Unknown", chip.text)
        assertEquals(CheckChipAction.SHOW_RESULT, chip.action)
    }

    @Test
    fun testUnknownWithOfflineError() {
        val result = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.UNKNOWN,
                    error = ScanError.OFFLINE,
                )
            )
        )
        val chips = CheckChips.build(
            result = result,
            readyServices = listOf(ScanService.VIRUSTOTAL),
            fileSize = 1024L,
            maxUploadBytes = defaultMaxUpload,
        )
        assertEquals(1, chips.size)
        val chip = chips[0]
        assertEquals(ScanService.VIRUSTOTAL, chip.service)
        assertEquals(CheckChipIcon.CLOUD_OFF, chip.icon)
        assertEquals(CheckChipTone.NEUTRAL, chip.tone)
        assertEquals("Retry", chip.text)
        assertEquals(CheckChipAction.RETRY, chip.action)
    }

    @Test
    fun testOtherErrors() {
        val badKeyResult = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.UNKNOWN,
                    error = ScanError.BAD_KEY,
                )
            )
        )
        val badKeyChips = CheckChips.build(badKeyResult, listOf(ScanService.VIRUSTOTAL), 100L, defaultMaxUpload)
        assertEquals("Key problem", badKeyChips[0].text)
        assertEquals(CheckChipIcon.CLOUD_OFF, badKeyChips[0].icon)
        assertEquals(CheckChipTone.NEUTRAL, badKeyChips[0].tone)
        assertEquals(CheckChipAction.SHOW_RESULT, badKeyChips[0].action)

        val limitResult = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.UNKNOWN,
                    error = ScanError.LIMIT,
                )
            )
        )
        val limitChips = CheckChips.build(limitResult, listOf(ScanService.VIRUSTOTAL), 100L, defaultMaxUpload)
        assertEquals("Limit reached", limitChips[0].text)
        assertEquals(CheckChipIcon.CLOUD_OFF, limitChips[0].icon)
        assertEquals(CheckChipTone.NEUTRAL, limitChips[0].tone)
        assertEquals(CheckChipAction.SHOW_RESULT, limitChips[0].action)

        val tooBigResult = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.UNKNOWN,
                    error = ScanError.TOO_BIG,
                )
            )
        )
        val tooBigChips = CheckChips.build(tooBigResult, listOf(ScanService.VIRUSTOTAL), 100L, defaultMaxUpload)
        assertEquals("Too big", tooBigChips[0].text)
        assertEquals(CheckChipIcon.CLOUD_OFF, tooBigChips[0].icon)
        assertEquals(CheckChipTone.NEUTRAL, tooBigChips[0].tone)
        assertEquals(CheckChipAction.SHOW_RESULT, tooBigChips[0].action)

        val otherResult = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.UNKNOWN,
                    error = ScanError.OTHER,
                )
            )
        )
        val otherChips = CheckChips.build(otherResult, listOf(ScanService.VIRUSTOTAL), 100L, defaultMaxUpload)
        assertEquals("Retry", otherChips[0].text)
        assertEquals(CheckChipIcon.CLOUD_OFF, otherChips[0].icon)
        assertEquals(CheckChipTone.NEUTRAL, otherChips[0].tone)
        assertEquals(CheckChipAction.RETRY, otherChips[0].action)
    }

    @Test
    fun testPendingAnalysisRunningOverridesStoredResult() {
        val result = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.UNKNOWN,
                )
            ),
            pending = listOf(
                PendingAnalysis(
                    service = ScanService.VIRUSTOTAL,
                    analysisId = "analysis_1",
                    startedAt = 500L,
                )
            )
        )
        val chips = CheckChips.build(
            result = result,
            readyServices = listOf(ScanService.VIRUSTOTAL),
            fileSize = 1024L,
            maxUploadBytes = defaultMaxUpload,
        )
        assertEquals(1, chips.size)
        val chip = chips[0]
        assertEquals(ScanService.VIRUSTOTAL, chip.service)
        assertEquals(CheckChipIcon.SPINNER, chip.icon)
        assertEquals(CheckChipTone.RUNNING, chip.tone)
        assertEquals("Analysis running", chip.text)
        assertEquals(CheckChipAction.SHOW_RESULT, chip.action)
    }

    @Test
    fun testTwoServicesOrderAndPendingAppended() {
        val result = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.NO_FLAGS,
                    flagged = 0,
                    engines = 70,
                )
            ),
            pending = listOf(
                PendingAnalysis(
                    service = ScanService.HYBRID_ANALYSIS,
                    analysisId = "analysis_2",
                    startedAt = 500L,
                )
            )
        )
        val chips = CheckChips.build(
            result = result,
            readyServices = listOf(ScanService.VIRUSTOTAL, ScanService.HYBRID_ANALYSIS),
            fileSize = 1024L,
            maxUploadBytes = defaultMaxUpload,
        )
        assertEquals(2, chips.size)
        assertEquals(ScanService.VIRUSTOTAL, chips[0].service)
        assertEquals("0/70", chips[0].text)
        assertEquals(CheckChipTone.OK, chips[0].tone)
        assertEquals(CheckChipAction.SHOW_RESULT, chips[0].action)

        assertEquals(ScanService.HYBRID_ANALYSIS, chips[1].service)
        assertEquals("Analysis running", chips[1].text)
        assertEquals(CheckChipTone.RUNNING, chips[1].tone)
        assertEquals(CheckChipIcon.SPINNER, chips[1].icon)
        assertEquals(CheckChipAction.SHOW_RESULT, chips[1].action)
    }

    @Test
    fun testFileSizeZeroWithReadyUnknownIsUpload() {
        val result = makeResult(
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.UNKNOWN,
                )
            )
        )
        val chips = CheckChips.build(
            result = result,
            readyServices = listOf(ScanService.VIRUSTOTAL),
            fileSize = 0L,
            maxUploadBytes = { 10L },
        )
        assertEquals(1, chips.size)
        val chip = chips[0]
        assertEquals(ScanService.VIRUSTOTAL, chip.service)
        assertEquals(CheckChipIcon.SHIELD_UNKNOWN, chip.icon)
        assertEquals(CheckChipTone.NEUTRAL, chip.tone)
        assertEquals("Unknown · Upload", chip.text)
        assertEquals(CheckChipAction.UPLOAD, chip.action)
    }
}
