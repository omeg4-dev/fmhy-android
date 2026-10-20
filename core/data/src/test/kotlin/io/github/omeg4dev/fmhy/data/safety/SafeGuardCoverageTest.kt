package io.github.omeg4dev.fmhy.data.safety

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DatasetStore
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test

class SafeGuardCoverageTest {

    @Test
    fun testCoverageSample() = runTest {
        val originalDb = File("/home/omega/Projects/fmhy-android/.cache/check.db")
        assumeTrue("check.db not found", originalDb.exists())

        val tempDb = File("/tmp/safeguard_check_coverage.db")
        Files.copy(originalDb.toPath(), tempDb.toPath(), StandardCopyOption.REPLACE_EXISTING)
        tempDb.deleteOnExit()

        val conn = BundledSQLiteDriver().open(tempDb.absolutePath)
        try {
            val columns = mutableSetOf<String>()
            conn.prepare("PRAGMA table_info('entries')").use { stmt ->
                while (stmt.step()) {
                    columns.add(stmt.getText(1))
                }
            }
            if (!columns.contains("kind")) {
                conn.execSQL("ALTER TABLE entries ADD COLUMN kind INTEGER NOT NULL DEFAULT 0;")
            }
            if (!columns.contains("line")) {
                conn.execSQL("ALTER TABLE entries ADD COLUMN line INTEGER NOT NULL DEFAULT 0;")
            }
        } finally {
            conn.close()
        }

        val store = DatasetStore(tempDb)
        val safeGuard = SafeGuard(store)

        val allEntries = store.sections().flatMap { store.entriesForSection(it.id) }

        val nonSharedEntries = allEntries.filter { entry ->
            val host = extractHost(entry.url)
            host != null && !SafeGuard.isSharedResourceHost(host)
        }

        // Seeded random sample of 300 non-shared-host entries
        val rng = Random(42L)
        val shuffled = nonSharedEntries.shuffled(rng)
        val sample = shuffled.take(300)

        var noDataA = 0
        var noDataB = 0
        var noDataC = 0
        var noDataD = 0
        var mirrorCount = 0

        for (entry in sample) {
            val host = extractHost(entry.url) ?: continue

            // (a) entry url
            val vA = safeGuard.check(entry.url)
            if (vA.status == SafetyStatus.NO_DATA) noDataA++

            // (b) https://<host>
            val vB = safeGuard.check("https://$host")
            if (vB.status == SafetyStatus.NO_DATA) noDataB++

            // (c) https://www.<host>
            val vC = safeGuard.check("https://www.$host")
            if (vC.status == SafetyStatus.NO_DATA) noDataC++

            // (d) first mirror if any
            val firstMirror = entry.mirrors.firstOrNull()?.url
            if (!firstMirror.isNullOrBlank()) {
                mirrorCount++
                val vD = safeGuard.check(firstMirror)
                if (vD.status == SafetyStatus.NO_DATA) noDataD++
            }
        }

        val rateA = noDataA.toDouble() / sample.size
        val rateB = noDataB.toDouble() / sample.size
        val rateC = noDataC.toDouble() / sample.size
        val rateD = if (mirrorCount > 0) noDataD.toDouble() / mirrorCount else 0.0

        println("==================================================================")
        println("SafeGuard Coverage (sample size: ${sample.size}, mirrors: $mirrorCount)")
        println("------------------------------------------------------------------")
        println("| Variant                  | NO_DATA Count | Total | NO_DATA Rate |")
        println("|--------------------------|---------------|-------|--------------|")
        println("| (a) entry url            | %-13d | %-5d | %-12s |".format(noDataA, sample.size, "%.1f%%".format(rateA * 100)))
        println("| (b) https://<host>       | %-13d | %-5d | %-12s |".format(noDataB, sample.size, "%.1f%%".format(rateB * 100)))
        println("| (c) https://www.<host>   | %-13d | %-5d | %-12s |".format(noDataC, sample.size, "%.1f%%".format(rateC * 100)))
        println("| (d) first mirror         | %-13d | %-5d | %-12s |".format(noDataD, mirrorCount, "%.1f%%".format(rateD * 100)))
        println("==================================================================")
        // Assert NO_DATA rate < 2 % for (a),(c),(d) and < 5 % for (b)
        org.junit.Assert.assertTrue("NO_DATA rate for (a) entry url should be < 2%, got ${rateA * 100}%", rateA < 0.02)
        org.junit.Assert.assertTrue("NO_DATA rate for (b) https://<host> should be < 5%, got ${rateB * 100}%", rateB < 0.05)
        org.junit.Assert.assertTrue("NO_DATA rate for (c) https://www.<host> should be < 2%, got ${rateC * 100}%", rateC < 0.02)
        org.junit.Assert.assertTrue("NO_DATA rate for (d) first mirror should be < 2%, got ${rateD * 100}%", rateD < 0.02)
    }

    private fun extractHost(url: String): String? {
        val clean = url.trim()
        val rawHost = try {
            val u = if (!clean.contains("://")) "https://$clean" else clean
            URI(u).host
        } catch (_: Exception) {
            null
        } ?: clean.substringAfter("://").substringBefore('/').substringBefore(':').substringBefore('?')
        return rawHost.lowercase().removePrefix("www.")
    }
}
