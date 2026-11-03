package io.github.omeg4dev.fmhy.data.tracker

import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.DatasetStoreTest
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistorySyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private var now = 1_800_000_000L

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun gz(text: String): Buffer {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(text.toByteArray()) }
        return Buffer().write(bos.toByteArray())
    }

    private fun feed(schema: Int = 1, generatedAt: Long = now, rows: String): String =
        """{"schema":$schema,"generated_at":$generatedAt,"source_commit":"abc","days":120,
            "fields":["ts","kind","section_id","name","url","detail","commit_sha"],"changes":[$rows]}"""

    private val rowA = """[1000,"added","audio","FreeMusic","https://freemusic.example/","","c1"]"""
    private val rowB = """[2000,"removed","audio","Gone","https://gone.example","","c2"]"""

    private suspend fun store(): DatasetStore {
        val s = DatasetStoreTest.createSampleDb(tempFolder.newFile("h.db"))
        s.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('audio','Audio','','audio.md',1)")
            conn.execSQL(
                "INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, " +
                    "heading_path, tier, kind, line, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) " +
                    "VALUES (1,'entry-fm','audio',NULL,1,'FreeMusic','https://freemusic.example',NULL,'','','',0,0,0,0,0,'[]','[]',1,1,NULL)",
            )
        }
        return s
    }

    private fun sync(s: DatasetStore, dir: java.io.File = tempFolder.newFolder()) =
        HistorySync(s, dir, client, url = server.url("/changes.json.gz").toString(), clock = { now }, log = {})

    @Test
    fun mergesRowsKeepsFeedTimestampsAndResolvesEntryIds() = runTest {
        val s = store()
        server.enqueue(MockResponse.Builder().body(gz(feed(rows = "$rowA,$rowB"))).addHeader("ETag", "\"v1\"").build())
        val r = sync(s).sync(force = true)
        assertEquals(HistoryResult.Updated(inserted = 2, generatedAt = now), r)

        val changes = s.recentChanges(sinceTs = 0, kinds = emptySet())
        assertEquals(2, changes.size)
        val added = changes.first { it.url.contains("freemusic") }
        assertEquals(1000L, added.ts)               // real commit time, not sync time
        assertEquals("entry-fm", added.entryId)     // resolved against the app's own entry
        assertTrue(changes.first { it.url.contains("gone") }.entryId.startsWith("feed:"))
    }

    @Test
    fun mergingTheSameFeedTwiceAddsNothing() = runTest {
        val s = store()
        val dir = tempFolder.newFolder()
        repeat(2) { server.enqueue(MockResponse.Builder().body(gz(feed(rows = "$rowA,$rowB"))).build()) }
        assertEquals(2, (sync(s, dir).sync(force = true) as HistoryResult.Updated).inserted)
        assertEquals(0, (sync(s, dir).sync(force = true) as HistoryResult.Updated).inserted)
        assertEquals(2, s.recentChanges(sinceTs = 0, kinds = emptySet()).size)
    }

    @Test
    fun notModifiedKeepsRowsAndReportsLastGeneratedAt() = runTest {
        val s = store()
        val dir = tempFolder.newFolder()
        server.enqueue(MockResponse.Builder().body(gz(feed(rows = rowA))).addHeader("ETag", "\"v1\"").build())
        sync(s, dir).sync(force = true)
        server.enqueue(MockResponse.Builder().code(304).build())
        now += 7200
        assertEquals(HistoryResult.UpToDate(generatedAt = 1_800_000_000L), sync(s, dir).sync(force = true))
        server.takeRequest()
        assertEquals("\"v1\"", server.takeRequest().headers["If-None-Match"])
    }

    @Test
    fun throttlesToOneCheckPerHourUnlessForced() = runTest {
        val s = store()
        val dir = tempFolder.newFolder()
        server.enqueue(MockResponse.Builder().body(gz(feed(rows = rowA))).build())
        sync(s, dir).sync(force = true)
        now += 600
        assertEquals(HistoryResult.UpToDate(1_800_000_000L), sync(s, dir).sync(force = false))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun unknownSchemaHttpErrorsAndGarbageFailWithoutTouchingRows() = runTest {
        val s = store()
        server.enqueue(MockResponse.Builder().body(gz(feed(schema = 99, rows = rowA))).build())
        assertTrue(sync(s).sync(force = true) is HistoryResult.Failed)
        server.enqueue(MockResponse.Builder().code(404).build())
        assertTrue(sync(s).sync(force = true) is HistoryResult.Failed)
        server.enqueue(MockResponse.Builder().body("not gzip").build())
        assertTrue(sync(s).sync(force = true) is HistoryResult.Failed)
        assertEquals(0, s.recentChanges(sinceTs = 0, kinds = emptySet()).size)
    }
}
