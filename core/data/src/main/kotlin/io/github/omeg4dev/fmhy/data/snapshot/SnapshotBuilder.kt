package io.github.omeg4dev.fmhy.data.snapshot

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.parse.Base64Map
import io.github.omeg4dev.fmhy.data.parse.FmhyParser
import io.github.omeg4dev.fmhy.data.parse.Sections
import io.github.omeg4dev.fmhy.data.parse.UnsafeList
import io.github.omeg4dev.fmhy.data.sync.FileState
import io.github.omeg4dev.fmhy.data.sync.SyncState
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SnapshotBuilder {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @JvmStatic
    fun main(args: Array<String>) {
        var docsDir: File? = null
        var rentryFile: File? = null
        var unsafeFile: File? = null
        var reasonsFile: File? = null
        var outDir: File? = null

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--docs" -> docsDir = File(args[++i])
                "--rentry" -> rentryFile = File(args[++i])
                "--unsafe" -> unsafeFile = File(args[++i])
                "--reasons" -> reasonsFile = File(args[++i])
                "--out" -> outDir = File(args[++i])
                else -> error("Unknown argument: ${args[i]}")
            }
            i++
        }

        checkNotNull(docsDir) { "Missing --docs argument" }
        checkNotNull(rentryFile) { "Missing --rentry argument" }
        checkNotNull(unsafeFile) { "Missing --unsafe argument" }
        checkNotNull(reasonsFile) { "Missing --reasons argument" }
        checkNotNull(outDir) { "Missing --out argument" }

        val startNano = System.nanoTime()
        build(docsDir, rentryFile, unsafeFile, reasonsFile, outDir)
        val elapsedSec = (System.nanoTime() - startNano) / 1_000_000_000.0
        println("Completed in %.2f s".format(elapsedSec))
    }

    fun build(
        docsDir: File,
        rentryFile: File,
        unsafeFile: File,
        reasonsFile: File,
        outDir: File,
    ) {
        val constantsFile = File(docsDir, ".vitepress/transformer/constants.ts")
        check(constantsFile.exists()) { "constants.ts not found at: ${constantsFile.absolutePath}" }
        check(rentryFile.exists()) { "rentry file not found at: ${rentryFile.absolutePath}" }
        check(unsafeFile.exists()) { "unsafe domains file not found at: ${unsafeFile.absolutePath}" }
        check(reasonsFile.exists()) { "unsafe reasons file not found at: ${reasonsFile.absolutePath}" }

        val constantsText = constantsFile.readText(Charsets.UTF_8)
        val sections = Sections.parse(constantsText).filter { File(docsDir, it.file).exists() }

        val rentryHtml = rentryFile.readText(Charsets.UTF_8)
        val b64Map = Base64Map(rentryHtml)

        val unsafeDomainsText = unsafeFile.readText(Charsets.UTF_8)
        val unsafeReasonsText = reasonsFile.readText(Charsets.UTF_8)
        val unsafeList = UnsafeList.parse(unsafeDomainsText, unsafeReasonsText)

        val parsedSections = sections.map { sec ->
            val mdFile = File(docsDir, sec.file)
            val markdown = mdFile.readText(Charsets.UTF_8)
            FmhyParser.parseSection(sec, markdown, b64Map)
        }

        outDir.mkdirs()
        val tempDb = File(outDir, "temp-snapshot.db")
        if (tempDb.exists()) tempDb.delete()

        val conn = BundledSQLiteDriver().open(tempDb.absolutePath)
        var totalEntries = 0
        try {
            DatasetStore.executeSqlScript(conn, DatasetStore.loadSchemaSql())
            conn.execSQL("PRAGMA journal_mode = OFF;")
            conn.execSQL("PRAGMA synchronous = OFF;")
            conn.execSQL("PRAGMA foreign_keys = OFF;")
            conn.execSQL("BEGIN TRANSACTION;")

            val now = System.currentTimeMillis() / 1000L

            // 1. Insert sections
            val sqlSection = "INSERT INTO sections (id, title, description, file, ord) VALUES (?, ?, ?, ?, ?)"
            conn.prepare(sqlSection).use { stmt ->
                for (s in sections) {
                    stmt.bindText(1, s.id)
                    stmt.bindText(2, s.title)
                    stmt.bindText(3, s.description)
                    stmt.bindText(4, s.file)
                    stmt.bindLong(5, s.ord.toLong())
                    stmt.step()
                    stmt.reset()
                }
            }

            // 2. Insert headings
            val sqlHeading = "INSERT INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES (?, ?, ?, ?, ?, ?, ?)"
            conn.prepare(sqlHeading).use { stmt ->
                for (p in parsedSections) {
                    for (h in p.headings) {
                        stmt.bindText(1, h.id)
                        stmt.bindText(2, h.sectionId)
                        if (h.parentId != null) stmt.bindText(3, h.parentId) else stmt.bindNull(3)
                        stmt.bindLong(4, h.level.toLong())
                        stmt.bindText(5, h.title)
                        stmt.bindText(6, h.anchor)
                        stmt.bindLong(7, h.ord.toLong())
                        stmt.step()
                        stmt.reset()
                    }
                }
            }

            // 3. Insert notes
            val sqlNote = "INSERT INTO notes (id, section_id, heading_id, ord, text_md) VALUES (?, ?, ?, ?, ?)"
            conn.prepare(sqlNote).use { stmt ->
                for (p in parsedSections) {
                    for (n in p.notes) {
                        stmt.bindText(1, n.id)
                        stmt.bindText(2, n.sectionId)
                        if (n.headingId != null) stmt.bindText(3, n.headingId) else stmt.bindNull(3)
                        stmt.bindLong(4, n.ord.toLong())
                        stmt.bindText(5, n.textMd)
                        stmt.step()
                        stmt.reset()
                    }
                }
            }

            // 4. Insert entries (nums 1..N in section/doc order)
            var currentNum = 1L
            val sqlEntry = """
                INSERT INTO entries (
                    num, id, section_id, heading_id, ord, name, url, original_url,
                    description, tags, heading_path, tier, is_inline_more, is_base64,
                    mirrors, extra_links, first_seen, last_changed, starred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            conn.prepare(sqlEntry).use { stmt ->
                for (p in parsedSections) {
                    for (e in p.entries) {
                        val num = currentNum++
                        totalEntries++
                        val starredAt = if (e.tier >= 1) now else null

                        stmt.bindLong(1, num)
                        stmt.bindText(2, e.id)
                        stmt.bindText(3, e.sectionId)
                        if (e.headingId != null) stmt.bindText(4, e.headingId) else stmt.bindNull(4)
                        stmt.bindLong(5, e.ord.toLong())
                        stmt.bindText(6, e.name)
                        stmt.bindText(7, e.url)
                        if (e.originalUrl != null) stmt.bindText(8, e.originalUrl) else stmt.bindNull(8)
                        stmt.bindText(9, e.description)
                        stmt.bindText(10, e.tags)
                        stmt.bindText(11, e.headingPath)
                        stmt.bindLong(12, e.tier.toLong())
                        stmt.bindLong(13, if (e.isInlineMore) 1L else 0L)
                        stmt.bindLong(14, if (e.isBase64) 1L else 0L)
                        stmt.bindText(15, json.encodeToString(e.mirrors))
                        stmt.bindText(16, json.encodeToString(e.extraLinks))
                        stmt.bindLong(17, now)
                        stmt.bindLong(18, now)
                        if (starredAt != null) stmt.bindLong(19, starredAt) else stmt.bindNull(19)
                        stmt.step()
                        stmt.reset()
                    }
                }
            }

            // 5. Insert unsafe domains
            val sqlUnsafe = "INSERT INTO unsafe_domains (domain, reason) VALUES (?, ?)"
            conn.prepare(sqlUnsafe).use { stmt ->
                for ((domain, reason) in unsafeList) {
                    stmt.bindText(1, domain)
                    stmt.bindText(2, reason)
                    stmt.step()
                    stmt.reset()
                }
            }

            // 6. Insert meta
            val sqlMeta = "INSERT INTO meta (key, value) VALUES (?, ?)"
            conn.prepare(sqlMeta).use { stmt ->
                val metaRows = listOf(
                    "schema" to "1",
                    "version" to "1",
                    "created" to now.toString(),
                    "last_sync" to now.toString(),
                    "source" to "bundled"
                )
                for ((k, v) in metaRows) {
                    stmt.bindText(1, k)
                    stmt.bindText(2, v)
                    stmt.step()
                    stmt.reset()
                }
            }

            conn.execSQL("COMMIT;")

            // FTS optimize + VACUUM
            conn.execSQL("INSERT INTO fts_words(fts_words) VALUES('optimize');")
            conn.execSQL("INSERT INTO fts_tri(fts_tri) VALUES('optimize');")
            conn.execSQL("VACUUM;")
        } finally {
            conn.close()
        }

        val rawSize = tempDb.length()

        // Write snapshot.gzbin (gzip level 9)
        val gzbinFile = File(outDir, "snapshot.gzbin")
        if (gzbinFile.exists()) gzbinFile.delete()

        gzbinFile.outputStream().buffered().use { fos ->
            val gzos = GzipLevel9OutputStream(fos)
            gzos.buffered().use { gzOut ->
                tempDb.inputStream().buffered().use { dbIn ->
                    dbIn.copyTo(gzOut)
                }
            }
        }
        val gzSize = gzbinFile.length()

        // Build snapshot-state.json
        val fileStates = mutableMapOf<String, FileState>()
        fileStates["docs/.vitepress/transformer/constants.ts"] = FileState(etag = null, sha256 = sha256(constantsFile))
        fileStates["rentry"] = FileState(etag = null, sha256 = sha256(rentryFile))
        fileStates["filterlist-domains.txt"] = FileState(etag = null, sha256 = sha256(unsafeFile))
        fileStates["filterlists-reasons.json"] = FileState(etag = null, sha256 = sha256(reasonsFile))

        for (s in sections) {
            val f = File(docsDir, s.file)
            fileStates["docs/${s.file}"] = FileState(etag = null, sha256 = sha256(f))
        }

        val now = System.currentTimeMillis() / 1000L
        val syncState = SyncState(
            // Never checked: the first launch must sync, however recently the APK was built.
            lastCheck = 0L,
            lastSuccess = now,
            source = "bundled",
            files = fileStates,
            version = 1L
        )

        val stateFile = File(outDir, "snapshot-state.json")
        stateFile.writeText(json.encodeToString(syncState))

        tempDb.delete()

        println("SnapshotBuilder: $totalEntries entries, ${sections.size} sections, raw size: $rawSize bytes, gz size: $gzSize bytes")
    }

    private class GzipLevel9OutputStream(out: OutputStream) : GZIPOutputStream(out) {
        init {
            def.setLevel(Deflater.BEST_COMPRESSION)
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
