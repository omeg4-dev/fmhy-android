package io.github.omeg4dev.fmhy.browser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScanParsersTest {

    @Test
    fun testErrorForTable() {
        assertEquals(ScanError.BAD_KEY, ScanParsers.errorFor(401))
        assertEquals(ScanError.BAD_KEY, ScanParsers.errorFor(403))
        assertEquals(ScanError.LIMIT, ScanParsers.errorFor(429))
        assertEquals(ScanError.LIMIT, ScanParsers.errorFor(204))
        assertEquals(ScanError.TOO_BIG, ScanParsers.errorFor(413))
        assertNull(ScanParsers.errorFor(404))
        assertNull(ScanParsers.errorFor(200))
        assertNull(ScanParsers.errorFor(201))
        assertNull(ScanParsers.errorFor(299))
        assertEquals(ScanError.OTHER, ScanParsers.errorFor(500))
        assertEquals(ScanError.OTHER, ScanParsers.errorFor(502))
        assertEquals(ScanError.OTHER, ScanParsers.errorFor(400))
    }

    @Test
    fun testVtReportCleanAndFlagged() {
        val cleanJson = """
            {
                "data": {
                    "attributes": {
                        "last_analysis_stats": {
                            "malicious": 0,
                            "suspicious": 0,
                            "undetected": 50,
                            "harmless": 20
                        },
                        "last_analysis_date": 1700000000
                    }
                }
            }
        """.trimIndent()
        val cleanRes = ScanParsers.vtReport(cleanJson, "https://report")
        assertNotNull(cleanRes)
        assertEquals(ScanVerdict.NO_FLAGS, cleanRes!!.verdict)
        assertEquals(0, cleanRes.flagged)
        assertEquals(70, cleanRes.engines)
        assertEquals(1700000000000L, cleanRes.scannedAt)
        assertEquals("https://report", cleanRes.reportUrl)

        val flaggedJson = """
            {
                "data": {
                    "attributes": {
                        "last_analysis_stats": {
                            "malicious": 5,
                            "suspicious": 0,
                            "undetected": 45,
                            "harmless": 20
                        },
                        "last_analysis_date": 1700000100
                    }
                }
            }
        """.trimIndent()
        val flaggedRes = ScanParsers.vtReport(flaggedJson, "https://report")
        assertNotNull(flaggedRes)
        assertEquals(ScanVerdict.DANGEROUS, flaggedRes!!.verdict)
        assertEquals(5, flaggedRes.flagged)
        assertEquals(70, flaggedRes.engines)
        assertEquals(1700000100000L, flaggedRes.scannedAt)
    }

    @Test
    fun testVtAnalysisQueuedAndCompleted() {
        val queuedJson = """
            {
                "data": {
                    "attributes": {
                        "status": "queued"
                    }
                }
            }
        """.trimIndent()
        assertNull(ScanParsers.vtAnalysis(queuedJson, "https://report"))

        val completedJson = """
            {
                "data": {
                    "attributes": {
                        "status": "completed",
                        "stats": {
                            "malicious": 1,
                            "suspicious": 1,
                            "undetected": 58,
                            "harmless": 10
                        },
                        "date": 1700000200
                    }
                }
            }
        """.trimIndent()
        val completedRes = ScanParsers.vtAnalysis(completedJson, "https://report")
        assertNotNull(completedRes)
        assertEquals(ScanVerdict.SUSPICIOUS, completedRes!!.verdict)
        assertEquals(2, completedRes.flagged)
        assertEquals(70, completedRes.engines)
        assertEquals(1700000200000L, completedRes.scannedAt)
        assertEquals("https://report", completedRes.reportUrl)
    }

    @Test
    fun testVtUploadIdAndUploadUrl() {
        val idJson = """
            {
                "data": {
                    "id": "u-analysis-id-xyz",
                    "type": "analysis"
                }
            }
        """.trimIndent()
        assertEquals("u-analysis-id-xyz", ScanParsers.vtAnalysisId(idJson))

        val urlJson = """
            {
                "data": "https://www.virustotal.com/api/v3/files/upload_url/abc123"
            }
        """.trimIndent()
        assertEquals("https://www.virustotal.com/api/v3/files/upload_url/abc123", ScanParsers.vtUploadUrl(urlJson))
    }

    @Test
    fun testHaOverviewCases() {
        val maliciousJson = """
            {
                "verdict": "malicious",
                "threat_score": 85,
                "last_multi_scan": "2026-10-01T12:00:00+00:00"
            }
        """.trimIndent()
        val malRes = ScanParsers.haOverview(maliciousJson, "https://report")
        assertNotNull(malRes)
        assertEquals(ScanVerdict.DANGEROUS, malRes!!.verdict)
        assertEquals("Threat score 85 of 100", malRes.detail)
        assertNotNull(malRes.scannedAt)

        val cleanJson = """
            {
                "verdict": "no specific threat",
                "threat_score": 0,
                "analysis_start_time": "2026-10-01T12:00:00Z"
            }
        """.trimIndent()
        val cleanRes = ScanParsers.haOverview(cleanJson, "https://report")
        assertNotNull(cleanRes)
        assertEquals(ScanVerdict.NO_FLAGS, cleanRes!!.verdict)
        assertEquals("Threat score 0 of 100", cleanRes.detail)

        val nullVerdictJson = """
            {
                "threat_score": null
            }
        """.trimIndent()
        val nullRes = ScanParsers.haOverview(nullVerdictJson, "https://report")
        assertNotNull(nullRes)
        assertEquals(ScanVerdict.UNKNOWN, nullRes!!.verdict)
        assertNull(nullRes.detail)
    }

    @Test
    fun testHaQuickScanUnfinishedAndFinished() {
        val idJson = """
            {
                "id": "quick-scan-id-456"
            }
        """.trimIndent()
        assertEquals("quick-scan-id-456", ScanParsers.haQuickScanId(idJson))

        val unfinishedJson = """
            {
                "finished": false,
                "scanners": []
            }
        """.trimIndent()
        assertNull(ScanParsers.haQuickScan(unfinishedJson, "https://report"))

        val finishedJson = """
            {
                "finished": true,
                "scanners": [
                    {"name": "s1", "status": "malicious"},
                    {"name": "s2", "status": "clean"},
                    {"name": "s3", "status": "clean"},
                    {"name": "s4", "status": "clean"},
                    {"name": "s5", "status": "clean"},
                    {"name": "s6", "status": "clean"},
                    {"name": "s7", "status": "clean"},
                    {"name": "s8", "status": "clean"},
                    {"name": "s9", "status": "clean"},
                    {"name": "s10", "status": "clean"}
                ]
            }
        """.trimIndent()
        val finishedRes = ScanParsers.haQuickScan(finishedJson, "https://report")
        assertNotNull(finishedRes)
        assertEquals(ScanVerdict.SUSPICIOUS, finishedRes!!.verdict)
        assertEquals(1, finishedRes.flagged)
        assertEquals(10, finishedRes.engines)
        assertNull(finishedRes.scannedAt)
    }

    @Test
    fun testMalformedJsonReturnsNullForEachParser() {
        val malformed = "not json {{"
        assertNull(ScanParsers.vtReport(malformed, "https://report"))
        assertNull(ScanParsers.vtAnalysisId(malformed))
        assertNull(ScanParsers.vtUploadUrl(malformed))
        assertNull(ScanParsers.vtAnalysis(malformed, "https://report"))
        assertNull(ScanParsers.haOverview(malformed, "https://report"))
        assertNull(ScanParsers.haQuickScanId(malformed))
        assertNull(ScanParsers.haQuickScan(malformed, "https://report"))

        val emptyObj = "{}"
        assertNull(ScanParsers.vtReport(emptyObj, "https://report"))
        assertNull(ScanParsers.vtAnalysisId(emptyObj))
        assertNull(ScanParsers.vtUploadUrl(emptyObj))
        assertNull(ScanParsers.vtAnalysis(emptyObj, "https://report"))
        assertNull(ScanParsers.haQuickScanId(emptyObj))
        assertNull(ScanParsers.haQuickScan(emptyObj, "https://report"))
    }
}
