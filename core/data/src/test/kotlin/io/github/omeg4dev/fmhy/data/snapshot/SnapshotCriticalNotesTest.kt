package io.github.omeg4dev.fmhy.data.snapshot

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SnapshotCriticalNotesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun findFixtureDir(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "schema/fixtures/critical-notes").exists()) {
            val parent = dir.parentFile ?: error("Could not find schema/fixtures/critical-notes")
            dir = parent
        }
        return File(dir, "schema/fixtures/critical-notes")
    }

    @Test
    fun testBuildSnapshotFromFixture() {
        val fixtureDir = findFixtureDir()
        val expectedFile = File(fixtureDir, "expected.json")
        assertTrue(expectedFile.exists())
        val expectedJson = Json.parseToJsonElement(expectedFile.readText(Charsets.UTF_8)).jsonObject

        val docsDir = tempFolder.newFolder("docs")
        val transformerDir = File(docsDir, ".vitepress/transformer").apply { mkdirs() }
        File(transformerDir, "constants.ts").writeText(
            """
            export const sections = {
              'audio.md': {
                title: 'Audio',
                description: 'Audio streaming and tools'
              }
            }
            """.trimIndent()
        )

        val fixtureAudio = File(fixtureDir, "docs/audio.md")
        assertTrue(fixtureAudio.exists())
        File(docsDir, "audio.md").writeText(fixtureAudio.readText(Charsets.UTF_8))

        val notesDir = File(docsDir, ".vitepress/notes").apply { mkdirs() }
        val fixtureNotesDir = File(fixtureDir, "docs/.vitepress/notes")
        fixtureNotesDir.listFiles()?.forEach { file ->
            File(notesDir, file.name).writeBytes(file.readBytes())
        }

        // Add CRLF note from expected.json
        val crlfObj = expectedJson["crlf_note_file"]!!.jsonObject
        val crlfId = crlfObj["id"]!!.jsonPrimitive.content
        val crlfBytes = crlfObj["bytes_utf8"]!!.jsonPrimitive.content
        File(notesDir, "$crlfId.md").writeText(crlfBytes, Charsets.UTF_8)

        val rentryFile = tempFolder.newFile("rentry.html").apply {
            writeText("<html><body>FMHY</body></html>")
        }
        val unsafeFile = tempFolder.newFile("filterlist-domains.txt").apply {
            writeText("")
        }
        val reasonsFile = tempFolder.newFile("filterlists-reasons.json").apply {
            writeText("{}")
        }
        val outDir = tempFolder.newFolder("out")

        SnapshotBuilder.build(
            docsDir = docsDir,
            rentryFile = rentryFile,
            unsafeFile = unsafeFile,
            reasonsFile = reasonsFile,
            outDir = outDir,
        )

        val gzbin = File(outDir, "snapshot.gzbin")
        assertTrue(gzbin.exists())

        val decompressedDb = File(outDir, "test-decompressed.db")
        GZIPInputStream(gzbin.inputStream().buffered()).use { input ->
            decompressedDb.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }

        val conn = BundledSQLiteDriver().open(decompressedDb.absolutePath)
        try {
            // Check meta.schema == "5"
            conn.prepare("SELECT value FROM meta WHERE key = 'schema'").use { stmt ->
                assertTrue(stmt.step())
                assertEquals("5", stmt.getText(0))
            }

            // Check PRAGMA user_version = 5
            conn.prepare("PRAGMA user_version").use { stmt ->
                assertTrue(stmt.step())
                assertEquals(5L, stmt.getLong(0))
            }

            // Check 3 critical rows
            conn.prepare("SELECT count(*) FROM critical_notes").use { stmt ->
                assertTrue(stmt.step())
                assertEquals(3L, stmt.getLong(0))
            }

            val criticalIds = mutableSetOf<String>()
            conn.prepare("SELECT id FROM critical_notes ORDER BY id ASC").use { stmt ->
                while (stmt.step()) {
                    criticalIds.add(stmt.getText(0))
                }
            }
            assertEquals(setOf("bar-note", "crlf-critical", "foo-critical"), criticalIds)

            // Check 5 note_links rows
            conn.prepare("SELECT count(*) FROM note_links").use { stmt ->
                assertTrue(stmt.step())
                assertEquals(5L, stmt.getLong(0))
            }

            val linksByUrl = mutableMapOf<String, String>()
            conn.prepare(
                """
                SELECT e.url, l.note_id
                FROM note_links l
                JOIN entries e ON e.id = l.entry_id
                ORDER BY l.ord, e.ord
                """.trimIndent()
            ).use { stmt ->
                while (stmt.step()) {
                    linksByUrl[stmt.getText(0)] = stmt.getText(1)
                }
            }

            val expectedMap = expectedJson["note_links_note_ids_by_entry_url"]!!.jsonObject
            assertEquals(expectedMap.size, linksByUrl.size)
            for ((url, element) in expectedMap) {
                assertEquals(element.jsonPrimitive.content, linksByUrl[url])
            }
        } finally {
            conn.close()
        }
    }
}
