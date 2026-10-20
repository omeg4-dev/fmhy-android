package io.github.omeg4dev.fmhy.data.snapshot

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SnapshotBuilderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testBuildSnapshotYieldsSchema4AndNonEmptySiteLists() {
        val docsDir = tempFolder.newFolder("docs")
        val vpDir = File(docsDir, ".vitepress/transformer").apply { mkdirs() }
        File(vpDir, "constants.ts").writeText(
            """
            export const sections = {
              'audio.md': {
                title: 'Audio',
                description: 'Audio streaming and tools'
              }
            }
            """.trimIndent()
        )
        File(docsDir, "audio.md").writeText(
            """
            # Audio
            ## Streaming
            * [FreeMusic](https://freemusic.example) - Listen to music
            """.trimIndent()
        )

        val rentryFile = tempFolder.newFile("rentry.html").apply {
            writeText("<html><body>FMHY</body></html>")
        }
        val unsafeFile = tempFolder.newFile("filterlist-domains.txt").apply {
            writeText("bad-domain.com\n")
        }
        val reasonsFile = tempFolder.newFile("filterlists-reasons.json").apply {
            writeText("""{"bad-domain.com": "malware", "unsafe-site.org": "phishing"}""")
        }
        val sitelistFile = tempFolder.newFile("sitelist.txt").apply {
            writeText("unsafe-site.org\n")
        }
        val sitelistPlusFile = tempFolder.newFile("sitelist-plus.txt").apply {
            writeText("suspect-site.net\n")
        }
        val fmhySitesFile = tempFolder.newFile("fmhy-filterlist.txt").apply {
            writeText("fmhy.net\nwiki.fmhy.net\n")
        }
        val outDir = tempFolder.newFolder("out")

        SnapshotBuilder.main(
            arrayOf(
                "--docs", docsDir.absolutePath,
                "--rentry", rentryFile.absolutePath,
                "--unsafe", unsafeFile.absolutePath,
                "--reasons", reasonsFile.absolutePath,
                "--sitelist", sitelistFile.absolutePath,
                "--sitelist-plus", sitelistPlusFile.absolutePath,
                "--fmhy-sites", fmhySitesFile.absolutePath,
                "--out", outDir.absolutePath
            )
        )

        val gzbin = File(outDir, "snapshot.gzbin")
        assertTrue(gzbin.exists())
        val stateFile = File(outDir, "snapshot-state.json")
        assertTrue(stateFile.exists())

        // Decompress snapshot.gzbin to test sqlite db
        val decompressedDb = File(outDir, "test-decompressed.db")
        GZIPInputStream(gzbin.inputStream().buffered()).use { input ->
            decompressedDb.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }

        val conn = BundledSQLiteDriver().open(decompressedDb.absolutePath)
        try {
            // Check PRAGMA user_version = 4
            conn.prepare("PRAGMA user_version").use { stmt ->
                assertTrue(stmt.step())
                val userVersion = stmt.getLong(0)
                assertEquals(4L, userVersion)
            }

            // Check site_lists has entries
            val counts = mutableMapOf<String, Long>()
            conn.prepare("SELECT list, COUNT(*) FROM site_lists GROUP BY list").use { stmt ->
                while (stmt.step()) {
                    val listName = stmt.getText(0)
                    val count = stmt.getLong(1)
                    counts[listName] = count
                }
            }

            assertTrue(counts.containsKey("unsafe"))
            assertTrue(counts.containsKey("potentially_unsafe"))
            assertTrue(counts.containsKey("fmhy"))
            assertTrue(counts.getValue("unsafe") > 0)
            assertTrue(counts.getValue("potentially_unsafe") > 0)
            assertTrue(counts.getValue("fmhy") > 0)

            // Check reasons attached
            conn.prepare("SELECT reason FROM site_lists WHERE key = 'unsafe-site.org' AND list = 'unsafe'").use { stmt ->
                assertTrue(stmt.step())
                assertEquals("phishing", stmt.getText(0))
            }
        } finally {
            conn.close()
        }

        // Check snapshot-state.json
        val stateJson = Json.parseToJsonElement(stateFile.readText()).jsonObject
        val fileStates = checkNotNull(stateJson["files"]?.jsonObject)
        assertTrue(fileStates.containsKey("sitelist.txt"))
        assertTrue(fileStates.containsKey("sitelist-plus.txt"))
        assertTrue(fileStates.containsKey("fmhy-filterlist.txt"))
    }
}
