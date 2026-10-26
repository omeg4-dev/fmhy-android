package io.github.omeg4dev.fmhy.browser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanModelsTest {

    @Test
    fun testRoundTripWithEveryFieldSet() {
        val original = ScanResult(
            kind = ScanKind.FILE,
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.DANGEROUS,
                    flagged = 5,
                    engines = 70,
                    detail = "5 engines flagged this",
                    scannedAt = 1600000000000L,
                    reportUrl = "https://www.virustotal.com/gui/file/e3b0c4",
                    error = null,
                ),
                ServiceResult(
                    service = ScanService.HYBRID_ANALYSIS,
                    verdict = ScanVerdict.UNKNOWN,
                    flagged = 0,
                    engines = 0,
                    detail = null,
                    scannedAt = null,
                    reportUrl = null,
                    error = ScanError.BAD_KEY,
                ),
            ),
            checkedAt = 1700000000000L,
            pending = listOf(
                PendingAnalysis(
                    service = ScanService.HYBRID_ANALYSIS,
                    analysisId = "ha-analysis-123",
                    startedAt = 1699999900000L,
                ),
            ),
        )

        val json = original.toJson()
        val restored = ScanResult.fromJson(json)

        assertNotNull(restored)
        assertEquals(original, restored)
        assertEquals(ScanVerdict.DANGEROUS, restored!!.verdict)
        assertTrue(restored.isRunning)
    }

    @Test
    fun testRoundTripWithNulls() {
        val original = ScanResult(
            kind = ScanKind.LINK,
            sha256 = null,
            services = listOf(
                ServiceResult(
                    service = ScanService.VIRUSTOTAL,
                    verdict = ScanVerdict.NO_FLAGS,
                    flagged = null,
                    engines = null,
                    detail = null,
                    scannedAt = null,
                    reportUrl = null,
                    error = null,
                ),
            ),
            checkedAt = 123456789L,
            pending = emptyList(),
        )

        val json = original.toJson()
        val restored = ScanResult.fromJson(json)

        assertNotNull(restored)
        assertEquals(original, restored)
        assertNull(restored!!.sha256)
        assertEquals(ScanVerdict.NO_FLAGS, restored.verdict)
        assertFalse(restored.isRunning)
    }

    @Test
    fun testMalformedJsonReturnsNull() {
        assertNull(ScanResult.fromJson(null))
        assertNull(ScanResult.fromJson(""))
        assertNull(ScanResult.fromJson("   "))
        assertNull(ScanResult.fromJson("not-json"))
        assertNull(ScanResult.fromJson("{invalid json}"))
        assertNull(ScanResult.fromJson("123"))
        assertNull(ScanResult.fromJson("[]"))
    }

    @Test
    fun testUnknownEnumNamesDropped() {
        val jsonWithUnknowns = """
            {
                "kind": "FILE",
                "sha256": "abc",
                "services": [
                    {
                        "service": "FUTURE_SERVICE",
                        "verdict": "NO_FLAGS"
                    },
                    {
                        "service": "VIRUSTOTAL",
                        "verdict": "FUTURE_VERDICT"
                    },
                    {
                        "service": "HYBRID_ANALYSIS",
                        "verdict": "SUSPICIOUS",
                        "error": "FUTURE_ERROR"
                    },
                    {
                        "service": "HYBRID_ANALYSIS",
                        "verdict": "SUSPICIOUS",
                        "detail": "Threat score 50 of 100"
                    }
                ],
                "checkedAt": 1000,
                "pending": [
                    {
                        "service": "UNKNOWN_SERVICE",
                        "analysisId": "id-1",
                        "startedAt": 500
                    },
                    {
                        "service": "VIRUSTOTAL",
                        "analysisId": "id-2",
                        "startedAt": 600
                    }
                ]
            }
        """.trimIndent()

        val parsed = ScanResult.fromJson(jsonWithUnknowns)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.services.size)
        assertEquals(ScanService.HYBRID_ANALYSIS, parsed.services[0].service)
        assertEquals(ScanVerdict.SUSPICIOUS, parsed.services[0].verdict)
        assertEquals("Threat score 50 of 100", parsed.services[0].detail)

        assertEquals(1, parsed.pending.size)
        assertEquals(ScanService.VIRUSTOTAL, parsed.pending[0].service)
        assertEquals("id-2", parsed.pending[0].analysisId)
    }

    @Test
    fun testScanText() {
        assertEquals("“No flags” is not a guarantee, and “unknown” does not mean safe.", ScanText.REMINDER)
        assertEquals("3 of 70 engines flagged this", ScanText.engines(3, 70))
        assertEquals("Not known to this service yet.", ScanText.UNKNOWN_FILE)

        assertEquals(
            "The VirusTotal key isn't valid. Check it in settings or open the VirusTotal website instead.",
            ScanText.error(ScanService.VIRUSTOTAL, ScanError.BAD_KEY),
        )
        assertEquals(
            "Your free Hybrid Analysis key has reached its usage limit. Try again later or open the Hybrid Analysis website instead.",
            ScanText.error(ScanService.HYBRID_ANALYSIS, ScanError.LIMIT),
        )
        assertEquals(
            "Couldn't reach VirusTotal. Check your connection.",
            ScanText.error(ScanService.VIRUSTOTAL, ScanError.OFFLINE),
        )
        assertEquals(
            "This file is too big to upload to Hybrid Analysis. Open the Hybrid Analysis website instead.",
            ScanText.error(ScanService.HYBRID_ANALYSIS, ScanError.TOO_BIG),
        )
        assertEquals(
            "VirusTotal couldn't check this right now.",
            ScanText.error(ScanService.VIRUSTOTAL, ScanError.OTHER),
        )
    }
}
