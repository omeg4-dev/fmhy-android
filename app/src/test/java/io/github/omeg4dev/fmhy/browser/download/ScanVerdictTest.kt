package io.github.omeg4dev.fmhy.browser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScanVerdictTest {

    @Test
    fun testFromCountsBoundariesAndBranches() {
        assertEquals(ScanVerdict.NO_FLAGS, ScanVerdicts.fromCounts(0, 0, 70))
        assertEquals(ScanVerdict.SUSPICIOUS, ScanVerdicts.fromCounts(1, 0, 70))
        assertEquals(ScanVerdict.SUSPICIOUS, ScanVerdicts.fromCounts(0, 1, 70))
        assertEquals(ScanVerdict.SUSPICIOUS, ScanVerdicts.fromCounts(2, 0, 70))
        assertEquals(ScanVerdict.DANGEROUS, ScanVerdicts.fromCounts(3, 0, 70))
        assertEquals(ScanVerdict.DANGEROUS, ScanVerdicts.fromCounts(10, 5, 70))
        assertEquals(ScanVerdict.UNKNOWN, ScanVerdicts.fromCounts(0, 0, 0))
        assertEquals(ScanVerdict.UNKNOWN, ScanVerdicts.fromCounts(5, 5, -1))
    }

    @Test
    fun testFromHybridVerdictCases() {
        assertEquals(ScanVerdict.UNKNOWN, ScanVerdicts.fromHybridVerdict(null))
        assertEquals(ScanVerdict.UNKNOWN, ScanVerdicts.fromHybridVerdict(""))
        assertEquals(ScanVerdict.UNKNOWN, ScanVerdicts.fromHybridVerdict("unexpected string"))
        assertEquals(ScanVerdict.DANGEROUS, ScanVerdicts.fromHybridVerdict("malicious"))
        assertEquals(ScanVerdict.DANGEROUS, ScanVerdicts.fromHybridVerdict("Malicious "))
        assertEquals(ScanVerdict.DANGEROUS, ScanVerdicts.fromHybridVerdict(" MALICIOUS\t"))
        assertEquals(ScanVerdict.SUSPICIOUS, ScanVerdicts.fromHybridVerdict("suspicious"))
        assertEquals(ScanVerdict.SUSPICIOUS, ScanVerdicts.fromHybridVerdict(" Suspicious "))
        assertEquals(ScanVerdict.NO_FLAGS, ScanVerdicts.fromHybridVerdict("no specific threat"))
        assertEquals(ScanVerdict.NO_FLAGS, ScanVerdicts.fromHybridVerdict("no-specific-threat"))
        assertEquals(ScanVerdict.NO_FLAGS, ScanVerdicts.fromHybridVerdict("whitelisted"))
        assertEquals(ScanVerdict.NO_FLAGS, ScanVerdicts.fromHybridVerdict("clean"))
        assertEquals(ScanVerdict.NO_FLAGS, ScanVerdicts.fromHybridVerdict(" Clean "))
    }

    @Test
    fun testCombine() {
        // empty -> UNKNOWN
        assertEquals(ScanVerdict.UNKNOWN, ScanVerdicts.combine(emptyList()))

        // all errors -> UNKNOWN
        val errorResult = ServiceResult(
            service = ScanService.VIRUSTOTAL,
            verdict = ScanVerdict.UNKNOWN,
            error = ScanError.BAD_KEY,
        )
        val errorResult2 = ServiceResult(
            service = ScanService.HYBRID_ANALYSIS,
            verdict = ScanVerdict.UNKNOWN,
            error = ScanError.OFFLINE,
        )
        assertEquals(ScanVerdict.UNKNOWN, ScanVerdicts.combine(listOf(errorResult, errorResult2)))

        // [NO_FLAGS, UNKNOWN] -> NO_FLAGS
        val noFlagsResult = ServiceResult(
            service = ScanService.VIRUSTOTAL,
            verdict = ScanVerdict.NO_FLAGS,
        )
        val unknownResult = ServiceResult(
            service = ScanService.HYBRID_ANALYSIS,
            verdict = ScanVerdict.UNKNOWN,
        )
        assertEquals(ScanVerdict.NO_FLAGS, ScanVerdicts.combine(listOf(noFlagsResult, unknownResult)))

        // [NO_FLAGS, DANGEROUS] -> DANGEROUS
        val dangerousResult = ServiceResult(
            service = ScanService.HYBRID_ANALYSIS,
            verdict = ScanVerdict.DANGEROUS,
        )
        assertEquals(ScanVerdict.DANGEROUS, ScanVerdicts.combine(listOf(noFlagsResult, dangerousResult)))

        // [SUSPICIOUS, error] -> SUSPICIOUS
        val suspiciousResult = ServiceResult(
            service = ScanService.VIRUSTOTAL,
            verdict = ScanVerdict.SUSPICIOUS,
        )
        assertEquals(ScanVerdict.SUSPICIOUS, ScanVerdicts.combine(listOf(suspiciousResult, errorResult)))
    }

    @Test
    fun unknownIsNeverSafe() {
        val unknownResult = ServiceResult(
            service = ScanService.VIRUSTOTAL,
            verdict = ScanVerdict.UNKNOWN,
        )
        assertNotEquals(ScanVerdict.NO_FLAGS, ScanVerdicts.combine(listOf(unknownResult)))
        assertEquals(ScanVerdict.UNKNOWN, ScanVerdicts.combine(listOf(unknownResult)))
    }
}
