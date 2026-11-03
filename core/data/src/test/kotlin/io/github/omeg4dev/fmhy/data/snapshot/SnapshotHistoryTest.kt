package io.github.omeg4dev.fmhy.data.snapshot

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SnapshotHistoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val now = 1_800_000_000L
    private val day = 86_400L

    private fun buildSnapshot(history: File?): File {
        val docsDir = tempFolder.newFolder("docs")
        val vpDir = File(docsDir, ".vitepress/transformer").apply { mkdirs() }
        File(vpDir, "constants.ts").writeText(
            "export const sections = { 'audio.md': { title: 'Audio', description: 'Audio' } }",
        )
        File(docsDir, "audio.md").writeText("# Audio\n## Streaming\n* [FreeMusic](https://freemusic.example) - Music\n")
        val rentry = tempFolder.newFile("rentry.html").apply { writeText("<html></html>") }
        val unsafe = tempFolder.newFile("unsafe.txt").apply { writeText("bad.com\n") }
        val reasons = tempFolder.newFile("reasons.json").apply { writeText("{}") }
        val outDir = tempFolder.newFolder("out")
        SnapshotBuilder.build(
            docsDir = docsDir,
            rentryFile = rentry,
            unsafeFile = unsafe,
            reasonsFile = reasons,
            outDir = outDir,
            historyFile = history,
            nowSec = now,
        )
        val db = File(outDir, "snapshot.db")
        GZIPInputStream(File(outDir, "snapshot.gzbin").inputStream().buffered()).use { i ->
            db.outputStream().buffered().use { o -> i.copyTo(o) }
        }
        return db
    }

    /** change_log rows: (ts, kind, url, commit). One bulk commit of 500 rows, one normal one of 3. */
    private fun historyDb(): File {
        val f = tempFolder.newFile("history.sqlite")
        val conn = BundledSQLiteDriver().open(f.absolutePath)
        try {
            conn.execSQL(
                "CREATE TABLE change_log (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, kind TEXT NOT NULL, " +
                    "entry_id TEXT NOT NULL, section_id TEXT NOT NULL, name TEXT NOT NULL, url TEXT NOT NULL, " +
                    "detail TEXT NOT NULL DEFAULT '', commit_sha TEXT NOT NULL)",
            )
            fun row(ts: Long, kind: String, url: String, sha: String) = conn.execSQL(
                "INSERT INTO change_log(ts, kind, entry_id, section_id, name, url, detail, commit_sha) " +
                    "VALUES ($ts, '$kind', 'e', 'audio', 'N', '$url', '', '$sha')",
            )
            row(now - 200 * day, "added", "https://old.example", "old")          // outside the window
            row(now - 10 * day, "added", "https://a.example", "c1")
            row(now - 10 * day, "removed", "https://b.example", "c1")
            row(now - 2 * day, "starred", "https://c.example", "c2")
            for (i in 1..500) row(now - 5 * day, "added", "https://bulk.example/$i", "bulk") // restructure commit
        } finally {
            conn.close()
        }
        return f
    }

    private fun count(db: File, sql: String): Long {
        val conn = BundledSQLiteDriver().open(db.absolutePath)
        try {
            return conn.prepare(sql).use { stmt -> check(stmt.step()); stmt.getLong(0) }
        } finally {
            conn.close()
        }
    }

    @Test
    fun withoutHistoryTheChangesTableStaysEmpty() {
        val db = buildSnapshot(null)
        assertEquals(0L, count(db, "SELECT COUNT(*) FROM changes"))
    }

    @Test
    fun historyKeepsRecentRowsAndDropsOldAndBulkRestructureCommits() {
        val db = buildSnapshot(historyDb())
        assertEquals(3L, count(db, "SELECT COUNT(*) FROM changes"))
        assertEquals(0L, count(db, "SELECT COUNT(*) FROM changes WHERE ts < ${now - SnapshotBuilder.HISTORY_DAYS * day}"))
        assertEquals(0L, count(db, "SELECT COUNT(*) FROM changes WHERE commit_sha = 'bulk'"))
        assertTrue(count(db, "SELECT COUNT(*) FROM changes WHERE commit_sha = 'c1'") == 2L)
        assertFalse(count(db, "SELECT COUNT(*) FROM changes WHERE kind = 'starred'") == 0L)
    }
}
