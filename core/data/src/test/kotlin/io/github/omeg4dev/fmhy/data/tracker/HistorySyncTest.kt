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

    private fun sync(
        s: DatasetStore,
        dir: java.io.File = tempFolder.newFolder(),
        maxBody: Long = HistorySync.MAX_BODY_BYTES,
        maxJson: Long = HistorySync.MAX_JSON_BYTES,
        reconcileMinRows: Int = HistorySync.RECONCILE_MIN_ROWS,
    ) = HistorySync(
        s, dir, client, url = server.url("/changes.json.gz").toString(), clock = { now }, log = {},
        maxBodyBytes = maxBody, maxJsonBytes = maxJson, reconcileMinRows = reconcileMinRows,
    )

    private suspend fun seed(s: DatasetStore, ts: Long, kind: String, url: String, sha: String, detail: String = "") =
        s.withConnection { conn ->
            conn.execSQL(
                "INSERT INTO changes (ts, kind, entry_id, section_id, name, url, detail, commit_sha) " +
                    "VALUES ($ts, '$kind', 'e-$url', 'audio', 'n', '$url', '$detail', '$sha')",
            )
        }

    private suspend fun urls(s: DatasetStore) =
        s.recentChanges(sinceTs = 0, kinds = emptySet(), limit = 100).map { it.url }.toSet()

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

    @Test
    fun rejectsOversizedDownloadsAndDecompressionBombs() = runTest {
        val s = store()
        // compressed body over the cap
        server.enqueue(MockResponse.Builder().body(gz(feed(rows = "$rowA,$rowB"))).build())
        assertTrue(sync(s, maxBody = 20).sync(force = true) is HistoryResult.Failed)
        // small download that expands past the JSON cap (a zip bomb in miniature)
        val bomb = feed(rows = (1..2000).joinToString(",") { """[1000,"added","audio","N$it","https://x.example/$it","","c"]""" })
        server.enqueue(MockResponse.Builder().body(gz(bomb)).build())
        val r = sync(s, maxJson = 10_000).sync(force = true)
        assertTrue(r is HistoryResult.Failed)
        assertEquals(0, s.recentChanges(sinceTs = 0, kinds = emptySet()).size)
    }

    @Test
    fun rejectsAFutureGeneratedAt() = runTest {
        val s = store()
        server.enqueue(MockResponse.Builder().body(gz(feed(generatedAt = now + 10 * 86_400L, rows = rowA))).build())
        val r = sync(s).sync(force = true)
        assertTrue(r is HistoryResult.Failed)
        assertEquals(0, s.recentChanges(sinceTs = 0, kinds = emptySet()).size)
        // a little clock skew is fine
        server.enqueue(MockResponse.Builder().body(gz(feed(generatedAt = now + 600, rows = rowA))).build())
        assertTrue(sync(s).sync(force = true) is HistoryResult.Updated)
    }

    // Inside the feed's window the feed wins: a row from the bundled snapshot that the feed does not
    // confirm (stale history database) must not be counted, but edited rows, rows newer than the feed
    // and rows older than its window stay.
    @Test
    fun rowsTheFeedDoesNotConfirmAreDroppedInsideItsWindow() = runTest {
        val s = store()
        val inWin = now - 5 * 86_400L
        seed(s, inWin, "added", "https://bogus.example/", "cBad")                 // stale snapshot row
        seed(s, inWin, "added", "https://real.example/", "cReal")                 // feed confirms it
        seed(s, inWin, "edited", "https://edited.example/", "cEdit")              // feed never has edited
        seed(s, now - 30 * 86_400L * 6, "added", "https://ancient.example/", "cOld") // before the window
        seed(s, now - 100L, "added", "https://newer.example/", "cNew")            // newer than the feed's newest commit
        val feedRows = """[$inWin,"added","audio","Real","https://real.example/","","cReal"],
                          [${inWin + 10},"removed","audio","Gone","https://gone.example/","","cGone"]"""
        server.enqueue(MockResponse.Builder().body(gz(feed(rows = feedRows))).build())
        sync(s, reconcileMinRows = 1).sync(force = true)
        assertEquals(
            setOf("https://real.example/", "https://gone.example/", "https://edited.example/",
                "https://ancient.example/", "https://newer.example/"),
            urls(s),
        )
    }

    @Test
    fun syncStampedRowsTheFeedExplainsAreDropped() = runTest {
        val s = store()
        val inWin = now - 2 * 86_400L
        seed(s, now - 100L, "removed", "https://old-address.example/", "sync:1")                   // really a url change
        seed(s, now - 100L, "added", "https://dup.example/", "sync:1")                             // same row as the feed
        seed(s, now - 100L, "removed", "https://only-sync.example/", "sync:1")                     // feed knows nothing of it
        seed(s, now - 100L, "edited", "https://edited-sync.example/", "sync:1")
        val feedRows = """[$inWin,"url_changed","audio","N","https://new-address.example/","https://old-address.example/","cU"],
                          [$inWin,"added","audio","Dup","https://dup.example/","","cD"]"""
        server.enqueue(MockResponse.Builder().body(gz(feed(rows = feedRows))).build())
        sync(s, reconcileMinRows = 1).sync(force = true)
        assertEquals(
            setOf("https://new-address.example/", "https://dup.example/", "https://only-sync.example/",
                "https://edited-sync.example/"),
            urls(s),
        )
    }

    @Test
    fun aTinyFeedNeverDeletesAnything() = runTest {
        val s = store()
        val inWin = now - 5 * 86_400L
        seed(s, inWin, "added", "https://kept.example/", "cKeep")
        server.enqueue(MockResponse.Builder().body(gz(feed(rows = """[$inWin,"added","audio","X","https://x.example/","","cX"]"""))).build())
        sync(s).sync(force = true) // default threshold: 50 rows
        assertTrue("https://kept.example/" in urls(s))
    }

    // An install updated from a build without the reconcile rules has an ETag and a recent check time:
    // it must still fetch the body and reconcile on its first sync, not wait for the feed to change.
    @Test
    fun stateFromBeforeTheReconcileRulesRefetchesAndReconciles() = runTest {
        val s = store()
        val dir = tempFolder.newFolder()
        java.io.File(dir, "history-state.json")
            .writeText("""{"etag":"\"v1\"","lastCheck":$now,"generatedAt":$now}""")
        val inWin = now - 5 * 86_400L
        seed(s, inWin, "added", "https://bogus.example/", "cBad")
        server.enqueue(MockResponse.Builder().body(gz(feed(rows = """[$inWin,"added","audio","R","https://real.example/","","cR"]"""))).build())
        // not forced and checked a minute ago: an up-to-date state would be throttled, this one must not be
        val r = sync(s, dir, reconcileMinRows = 1).sync(force = false)
        assertTrue(r is HistoryResult.Updated)
        assertEquals(null, server.takeRequest().headers["If-None-Match"])
        assertEquals(setOf("https://real.example/"), urls(s))
        // the state is now current: the next check is throttled and nothing is dropped again
        assertEquals(HistoryResult.UpToDate(now), sync(s, dir, reconcileMinRows = 1).sync(force = false))
    }
}
