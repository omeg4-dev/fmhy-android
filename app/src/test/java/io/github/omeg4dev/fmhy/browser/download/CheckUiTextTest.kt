package io.github.omeg4dev.fmhy.browser.download

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckUiTextTest {

    private val zone = ZoneId.of("UTC")
    private val nowMs = LocalDateTime.of(2026, 10, 26, 12, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun testLabelNormal() {
        val checkedAt = LocalDateTime.of(2026, 10, 20, 10, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val result = ScanResult(
            kind = ScanKind.FILE,
            sha256 = "abc",
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.NO_FLAGS,
                )
            ),
            checkedAt = checkedAt,
        )
        val expectedDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            .format(LocalDateTime.of(2026, 10, 20, 10, 0).atZone(zone))
        assertEquals("VirusTotal · $expectedDate · No flags", CheckUiText.label(result, nowMs, zone))
    }

    @Test
    fun testLabelTwoServices() {
        val checkedAt = LocalDateTime.of(2026, 10, 25, 10, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val result = ScanResult(
            kind = ScanKind.FILE,
            sha256 = "abc",
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.NO_FLAGS,
                ),
                ServiceResult(
                    service = ScanService.HYBRID_ANALYSIS,
                    verdict = ScanVerdict.SUSPICIOUS,
                )
            ),
            checkedAt = checkedAt,
        )
        val expectedDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            .format(LocalDateTime.of(2026, 10, 25, 10, 0).atZone(zone))
        assertEquals("VirusTotal + Hybrid Analysis · $expectedDate · Suspicious", CheckUiText.label(result, nowMs, zone))
    }

    @Test
    fun testLabelErrorsOnly() {
        val checkedAt = LocalDateTime.of(2026, 10, 25, 10, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val result = ScanResult(
            kind = ScanKind.FILE,
            sha256 = "abc",
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.UNKNOWN,
                    error = ScanError.BAD_KEY,
                ),
                ServiceResult(
                    service = ScanService.HYBRID_ANALYSIS,
                    verdict = ScanVerdict.UNKNOWN,
                    error = ScanError.OFFLINE,
                )
            ),
            checkedAt = checkedAt,
        )
        val expectedDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            .format(LocalDateTime.of(2026, 10, 25, 10, 0).atZone(zone))
        assertEquals("VirusTotal · $expectedDate · Unknown", CheckUiText.label(result, nowMs, zone))
    }

    @Test
    fun testLabelRunning() {
        val result = ScanResult(
            kind = ScanKind.FILE,
            sha256 = "abc",
            services = emptyList(),
            checkedAt = nowMs,
            pending = listOf(
                PendingAnalysis(
                    service = ScanService.VIRUSTOTAL,
                    analysisId = "123",
                    startedAt = nowMs,
                )
            ),
        )
        assertEquals("VirusTotal · analysis running", CheckUiText.label(result, nowMs, zone))
    }

    @Test
    fun testLabelOtherYear() {
        val checkedAt = LocalDateTime.of(2025, 5, 15, 10, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val result = ScanResult(
            kind = ScanKind.FILE,
            sha256 = "abc",
            services = listOf(
                ServiceResult(
                    service = ScanService.HYBRID_ANALYSIS,
                    verdict = ScanVerdict.DANGEROUS,
                )
            ),
            checkedAt = checkedAt,
        )
        val expectedDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
            .format(LocalDateTime.of(2025, 5, 15, 10, 0).atZone(zone))
        assertEquals("Hybrid Analysis · $expectedDate · Dangerous", CheckUiText.label(result, nowMs, zone))
    }

    @Test
    fun testServiceLineBranches() {
        // Error branch
        val errorRes = ServiceResult(
            service = ScanService.VIRUSTOTAL,
            verdict = ScanVerdict.UNKNOWN,
            error = ScanError.LIMIT,
        )
        assertEquals(ScanText.error(ScanService.VIRUSTOTAL, ScanError.LIMIT), CheckUiText.serviceLine(errorRes))

        // Engines branch
        val enginesRes = ServiceResult(
            service = ScanService.VIRUSTOTAL,
            verdict = ScanVerdict.SUSPICIOUS,
            flagged = 3,
            engines = 70,
        )
        assertEquals(ScanText.engines(3, 70), CheckUiText.serviceLine(enginesRes))

        // Detail branch
        val detailRes = ServiceResult(
            service = ScanService.HYBRID_ANALYSIS,
            verdict = ScanVerdict.NO_FLAGS,
            detail = "whitelisted",
        )
        assertEquals("whitelisted", CheckUiText.serviceLine(detailRes))

        // Fallback branch
        val unknownRes = ServiceResult(
            service = ScanService.VIRUSTOTAL,
            verdict = ScanVerdict.UNKNOWN,
        )
        assertEquals(ScanText.UNKNOWN_FILE, CheckUiText.serviceLine(unknownRes))
    }

    @Test
    fun testSendNote() {
        assertEquals(
            "The web address will be sent to VirusTotal. Some links contain personal or one-time codes.",
            CheckUiText.sendNote(listOf(ScanService.VIRUSTOTAL)),
        )
        assertEquals(
            "The web address will be sent to VirusTotal and Hybrid Analysis. Some links contain personal or one-time codes.",
            CheckUiText.sendNote(listOf(ScanService.VIRUSTOTAL, ScanService.HYBRID_ANALYSIS)),
        )
    }

    @Test
    fun testVerdictTitles() {
        assertEquals("No flags", CheckUiText.verdictTitle(ScanVerdict.NO_FLAGS))
        assertEquals("Suspicious", CheckUiText.verdictTitle(ScanVerdict.SUSPICIOUS))
        assertEquals("Dangerous: don't open this file", CheckUiText.verdictTitle(ScanVerdict.DANGEROUS))
        assertEquals("Unknown", CheckUiText.verdictTitle(ScanVerdict.UNKNOWN))

        assertEquals("No flags", CheckUiText.linkVerdictTitle(ScanVerdict.NO_FLAGS))
        assertEquals("Suspicious", CheckUiText.linkVerdictTitle(ScanVerdict.SUSPICIOUS))
        assertEquals("Dangerous: don't download this file", CheckUiText.linkVerdictTitle(ScanVerdict.DANGEROUS))
        assertEquals("Unknown", CheckUiText.linkVerdictTitle(ScanVerdict.UNKNOWN))
    }

    @Test
    fun testHasErrorAndAnyUnknown() {
        val resultWithLimit = ScanResult(
            kind = ScanKind.FILE,
            sha256 = "123",
            services = listOf(
                ServiceResult(service = ScanService.VIRUSTOTAL, verdict = ScanVerdict.UNKNOWN, error = ScanError.LIMIT),
                ServiceResult(service = ScanService.HYBRID_ANALYSIS, verdict = ScanVerdict.UNKNOWN, error = null),
            ),
            checkedAt = nowMs,
        )
        assertTrue(CheckUiText.hasError(resultWithLimit, ScanError.LIMIT))
        assertFalse(CheckUiText.hasError(resultWithLimit, ScanError.BAD_KEY))
        assertTrue(CheckUiText.hasError(resultWithLimit))
        assertTrue(CheckUiText.anyUnknown(resultWithLimit))

        val cleanResult = ScanResult(
            kind = ScanKind.FILE,
            sha256 = "123",
            services = listOf(
                ServiceResult(service = ScanService.VIRUSTOTAL, verdict = ScanVerdict.NO_FLAGS),
            ),
            checkedAt = nowMs,
        )
        assertFalse(CheckUiText.hasError(cleanResult, ScanError.LIMIT))
        assertFalse(CheckUiText.hasError(cleanResult))
        assertFalse(CheckUiText.anyUnknown(cleanResult))
    }
}
