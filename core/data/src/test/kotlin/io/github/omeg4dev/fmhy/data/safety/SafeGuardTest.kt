package io.github.omeg4dev.fmhy.data.safety

import io.github.omeg4dev.fmhy.data.DatasetStore
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class SafeGuardTest {

    companion object {
        private lateinit var dbFile: File
        private lateinit var realStore: DatasetStore
        private lateinit var safeGuard: SafeGuard

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val snapshotFile = sequenceOf(
                File("app/src/main/assets/dataset/snapshot.gzbin"),
                File("../app/src/main/assets/dataset/snapshot.gzbin"),
                File("../../app/src/main/assets/dataset/snapshot.gzbin")
            ).firstOrNull { it.exists() } ?: error("snapshot.gzbin not found")

            dbFile = File.createTempFile("snapshot_test", ".db")
            dbFile.deleteOnExit()
            GZIPInputStream(snapshotFile.inputStream()).use { gzIn ->
                dbFile.outputStream().use { out ->
                    gzIn.copyTo(out)
                }
            }

            realStore = DatasetStore(dbFile)
            safeGuard = SafeGuard(realStore)
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            if (::dbFile.isInitialized) {
                dbFile.delete()
            }
        }
    }

    private data class TestCase(
        val input: String,
        val expectedStatus: SafetyStatus,
        val note: String,
    )

    private fun loadCases(): List<TestCase> {
        val stream = SafeGuardTest::class.java.getResourceAsStream("/safety/cases.tsv")
            ?: error("Missing /safety/cases.tsv resource")
        val lines = stream.bufferedReader().readLines()
        return lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val parts = line.split('\t')
            assertTrue("Expected 3 tab-separated columns in line: $line", parts.size >= 3)
            val input = parts[0]
            val status = SafetyStatus.valueOf(parts[1])
            val note = parts[2]
            TestCase(input, status, note)
        }
    }

    @Test
    fun testCasesFromFixture() = runTest {
        val cases = loadCases()
        assertTrue("Fixture must contain at least 40 cases, got ${cases.size}", cases.size >= 40)

        var totalTimeNs = 0L

        for (case in cases) {
            val start = System.nanoTime()
            val verdict = safeGuard.check(case.input)
            val elapsed = System.nanoTime() - start
            totalTimeNs += elapsed

            val elapsedMs = elapsed / 1_000_000.0
            assertEquals(
                "Failed case '${case.input}' (${case.note}): elapsed=${"%.2f".format(elapsedMs)}ms, verdict=$verdict",
                case.expectedStatus,
                verdict.status
            )
        }

        val avgTimeMs = (totalTimeNs / cases.size) / 1_000_000.0
        println("SafeGuardTest: ${cases.size} cases passed. Average check time: %.2f ms".format(avgTimeMs))
        assertTrue("Average check time should be well under 50 ms (got %.2f ms)".format(avgTimeMs), avgTimeMs < 50.0)
    }
}
