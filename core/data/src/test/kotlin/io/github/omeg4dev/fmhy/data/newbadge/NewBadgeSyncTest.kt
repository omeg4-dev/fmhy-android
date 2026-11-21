package io.github.omeg4dev.fmhy.data.newbadge

import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.DatasetStoreTest
import io.github.omeg4dev.fmhy.data.sync.FileState
import io.github.omeg4dev.fmhy.data.sync.SyncState
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NewBadgeSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
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

    private suspend fun createStore(sections: List<Pair<String, String>> = listOf("audio" to "audio.md")): DatasetStore {
        val dbFile = tempFolder.newFile("test-${System.nanoTime()}.db")
        val store = DatasetStoreTest.createSampleDb(dbFile)
        store.withConnection { conn ->
            sections.forEachIndexed { idx, (id, file) ->
                conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('$id', '$id', '', '$file', ${idx + 1})")
            }
        }
        return store
    }

    private fun writeSyncState(dir: File, files: Map<String, String>) {
        val syncState = SyncState(
            files = files.mapValues { FileState(sha256 = it.value) },
        )
        File(dir, "sync-state.json").writeText(json.encodeToString(syncState))
    }

    private fun badgeHtml(url: String, date: String): String =
        """<ul><li class="new"><a href="$url">Link</a><button class="new-badge" aria-label="New, added $date">New</button></li></ul>"""

    private fun sync(
        store: DatasetStore,
        dir: File,
        badgeStore: NewBadgeStore,
        maxBody: Long = NewBadgeSync.DEFAULT_MAX_BODY_BYTES,
    ) = NewBadgeSync(
        store = store,
        dir = dir,
        badgeStore = badgeStore,
        client = client,
        baseUrl = server.url("/").toString(),
        clock = { now },
        log = {},
        maxBodyBytes = maxBody,
    )

    @Test
    fun emptySections_returnsFailedNoSections() = runTest {
        val dir = tempFolder.newFolder()
        val emptyStore = DatasetStoreTest.createSampleDb(tempFolder.newFile("empty.db"))
        val badgeStore = NewBadgeStore(dir)
        val s = sync(emptyStore, dir, badgeStore)

        val result = s.sync(force = true)
        assertEquals(NewBadgeResult.Failed("no sections"), result)
    }

    @Test
    fun throttle_returnsSkipped_whenWithin1800Seconds() = runTest {
        val dir = tempFolder.newFolder()
        val store = createStore(listOf("audio" to "audio.md"))
        val badgeStore = NewBadgeStore(dir)
        writeSyncState(dir, mapOf("docs/audio.md" to "sha1"))
        server.enqueue(MockResponse.Builder().body(badgeHtml("https://example.com/site", "2026-11-20")).build())

        val s = sync(store, dir, badgeStore)
        val r1 = s.sync(force = false)
        assertTrue(r1 is NewBadgeResult.Updated)

        // Second call 100 seconds later without force
        now += 100L
        val r2 = s.sync(force = false)
        assertEquals(NewBadgeResult.Skipped, r2)
        assertEquals(1, server.requestCount)

        // With force = true
        val r3 = s.sync(force = true)
        assertEquals(NewBadgeResult.UpToDate, r3)
    }

    @Test
    fun mdShaTrigger_unchangedMd_noRequest() = runTest {
        val dir = tempFolder.newFolder()
        val store = createStore(listOf("audio" to "audio.md"))
        val badgeStore = NewBadgeStore(dir)
        writeSyncState(dir, mapOf("docs/audio.md" to "sha1"))

        server.enqueue(MockResponse.Builder().body(badgeHtml("https://example.com/site", "2026-11-20")).build())
        val s = sync(store, dir, badgeStore)
        val r1 = s.sync(force = true)
        assertTrue(r1 is NewBadgeResult.Updated)
        assertEquals(1, server.requestCount)

        // Next sync: mdSha in sync-state.json unchanged ("sha1")
        now += 2000L
        val r2 = s.sync(force = true)
        assertEquals(NewBadgeResult.UpToDate, r2)
        // No new request enqueued or made to server
        assertEquals(1, server.requestCount)
    }

    @Test
    fun mdShaTrigger_changedMd_fetchesPage() = runTest {
        val dir = tempFolder.newFolder()
        val store = createStore(listOf("audio" to "audio.md"))
        val badgeStore = NewBadgeStore(dir)
        writeSyncState(dir, mapOf("docs/audio.md" to "sha1"))

        server.enqueue(MockResponse.Builder().body(badgeHtml("https://example.com/old", "2026-11-19")).build())
        val s = sync(store, dir, badgeStore)
        s.sync(force = true)

        // Change mdSha in sync-state.json
        writeSyncState(dir, mapOf("docs/audio.md" to "sha2"))
        server.enqueue(MockResponse.Builder().body(badgeHtml("https://example.com/new", "2026-11-20")).build())

        now += 2000L
        val r2 = s.sync(force = true)
        assertTrue(r2 is NewBadgeResult.Updated)
        assertEquals(1, (r2 as NewBadgeResult.Updated).pages)
        assertEquals(1, r2.marks)

        val state = badgeStore.loadState()
        assertEquals("sha2", state.pages["audio"]?.mdSha)
        assertEquals("example.com/new", state.pages["audio"]?.marks?.firstOrNull()?.u)
    }

    @Test
    fun dirtyRetryRule_sameBodyAfterMdChangeRetriedUpTo3TimesThenClean() = runTest {
        val dir = tempFolder.newFolder()
        val store = createStore(listOf("audio" to "audio.md"))
        val badgeStore = NewBadgeStore(dir)
        val sameHtml = badgeHtml("https://example.com/item", "2026-11-18")

        // Initial fetch with sha1
        writeSyncState(dir, mapOf("docs/audio.md" to "sha1"))
        server.enqueue(MockResponse.Builder().body(sameHtml).build())
        val s = sync(store, dir, badgeStore)
        s.sync(force = true)
        assertEquals(0, badgeStore.loadState().pages["audio"]?.dirty)

        // md changes to sha2, but server serves the SAME html body
        writeSyncState(dir, mapOf("docs/audio.md" to "sha2"))

        // Attempt 1: dirty becomes 2
        server.enqueue(MockResponse.Builder().body(sameHtml).build())
        now += 2000L
        s.sync(force = true)
        var state = badgeStore.loadState()
        assertEquals(2, state.pages["audio"]?.dirty)
        assertEquals("sha2", state.pages["audio"]?.mdSha)

        // Attempt 2: dirty becomes 1
        server.enqueue(MockResponse.Builder().body(sameHtml).build())
        now += 2000L
        s.sync(force = true)
        state = badgeStore.loadState()
        assertEquals(1, state.pages["audio"]?.dirty)

        // Attempt 3: dirty becomes 0 (clean!)
        server.enqueue(MockResponse.Builder().body(sameHtml).build())
        now += 2000L
        s.sync(force = true)
        state = badgeStore.loadState()
        assertEquals(0, state.pages["audio"]?.dirty)

        // Attempt 4: clean, no request!
        now += 2000L
        val r4 = s.sync(force = true)
        assertEquals(NewBadgeResult.UpToDate, r4)
    }

    @Test
    fun dirtyRetryRule_bodyChangesDuringRetry_becomesCleanImmediately() = runTest {
        val dir = tempFolder.newFolder()
        val store = createStore(listOf("audio" to "audio.md"))
        val badgeStore = NewBadgeStore(dir)
        val initialHtml = badgeHtml("https://example.com/initial", "2026-11-17")

        writeSyncState(dir, mapOf("docs/audio.md" to "sha1"))
        server.enqueue(MockResponse.Builder().body(initialHtml).build())
        val s = sync(store, dir, badgeStore)
        s.sync(force = true)

        // md changes to sha2, server returns same body -> dirty = 2
        writeSyncState(dir, mapOf("docs/audio.md" to "sha2"))
        server.enqueue(MockResponse.Builder().body(initialHtml).build())
        now += 2000L
        s.sync(force = true)
        assertEquals(2, badgeStore.loadState().pages["audio"]?.dirty)

        // Next fetch: site deploys, body changes!
        val newHtml = badgeHtml("https://example.com/updated", "2026-11-20")
        server.enqueue(MockResponse.Builder().body(newHtml).build())
        now += 2000L
        s.sync(force = true)
        val state = badgeStore.loadState()
        assertEquals(0, state.pages["audio"]?.dirty)
        assertEquals("example.com/updated", state.pages["audio"]?.marks?.firstOrNull()?.u)
    }

    @Test
    fun zeroMarksKeepsOldMarks() = runTest {
        val dir = tempFolder.newFolder()
        val store = createStore(listOf("audio" to "audio.md"))
        val badgeStore = NewBadgeStore(dir)
        writeSyncState(dir, mapOf("docs/audio.md" to "sha1"))

        // Initial fetch with 1 mark
        server.enqueue(MockResponse.Builder().body(badgeHtml("https://example.com/site", "2026-11-18")).build())
        val s = sync(store, dir, badgeStore)
        s.sync(force = true)
        assertEquals(1, badgeStore.loadState().pages["audio"]?.marks?.size)

        // Refetch: 200 OK with 0 marks (e.g. challenge page / layout change)
        writeSyncState(dir, mapOf("docs/audio.md" to "sha2"))
        server.enqueue(MockResponse.Builder().body("<html><body>No badges here</body></html>").build())
        now += 2000L
        val r2 = s.sync(force = true)
        assertTrue(r2 is NewBadgeResult.Updated)

        val state = badgeStore.loadState()
        assertEquals(1, state.pages["audio"]?.marks?.size)
        assertEquals("example.com/site", state.pages["audio"]?.marks?.first()?.u)
    }

    @Test
    fun httpError_keepsMarksAndOldMdSha() = runTest {
        val dir = tempFolder.newFolder()
        val store = createStore(listOf("audio" to "audio.md"))
        val badgeStore = NewBadgeStore(dir)
        writeSyncState(dir, mapOf("docs/audio.md" to "sha1"))

        server.enqueue(MockResponse.Builder().body(badgeHtml("https://example.com/site", "2026-11-18")).build())
        val s = sync(store, dir, badgeStore)
        s.sync(force = true)

        // md changes to sha2, but server returns 500
        writeSyncState(dir, mapOf("docs/audio.md" to "sha2"))
        server.enqueue(MockResponse.Builder().code(500).body("Internal Server Error").build())
        now += 2000L
        val r2 = s.sync(force = true)
        assertEquals(NewBadgeResult.Failed("all pages failed"), r2)

        // Old marks and old mdSha ("sha1") are kept
        val state = badgeStore.loadState()
        assertEquals("sha1", state.pages["audio"]?.mdSha)
        assertEquals(1, state.pages["audio"]?.marks?.size)
    }

    @Test
    fun oversizeBody_keepsMarksAndRetries() = runTest {
        val dir = tempFolder.newFolder()
        val store = createStore(listOf("audio" to "audio.md"))
        val badgeStore = NewBadgeStore(dir)
        writeSyncState(dir, mapOf("docs/audio.md" to "sha1"))

        // Limit is 100 bytes, response is 500 bytes
        val bigHtml = "<!--" + "x".repeat(500) + "-->"
        server.enqueue(MockResponse.Builder().body(bigHtml).build())
        val s = sync(store, dir, badgeStore, maxBody = 100L)
        val r = s.sync(force = true)

        assertEquals(NewBadgeResult.Failed("all pages failed"), r)
        assertFalse(badgeStore.loadState().pages.containsKey("audio"))
    }

    @Test
    fun oneFailingPageDoesNotBlockOthers() = runTest {
        val dir = tempFolder.newFolder()
        val store = createStore(listOf("audio" to "audio.md", "video" to "video.md"))
        val badgeStore = NewBadgeStore(dir)
        writeSyncState(dir, mapOf("docs/audio.md" to "shaA", "docs/video.md" to "shaV"))

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.url.encodedPath.contains("audio")) {
                    MockResponse.Builder().code(500).body("Server Error").build()
                } else {
                    MockResponse.Builder().body(badgeHtml("https://example.com/video1", "2026-11-20")).build()
                }
            }
        }

        val s = sync(store, dir, badgeStore)
        val r = s.sync(force = true)
        assertTrue(r is NewBadgeResult.Updated)
        assertEquals(1, (r as NewBadgeResult.Updated).pages)

        val state = badgeStore.loadState()
        assertFalse(state.pages.containsKey("audio"))
        assertTrue(state.pages.containsKey("video"))
        assertEquals("example.com/video1", state.pages["video"]?.marks?.firstOrNull()?.u)
    }

    @Test
    fun pageSetFollowsStoreSections_prunesObsoletePages() = runTest {
        val dir = tempFolder.newFolder()
        val store = createStore(listOf("audio" to "audio.md"))
        val badgeStore = NewBadgeStore(dir)

        // Seed state with obsolete page
        val initialState = NewBadgeState(
            version = 1,
            pages = mapOf(
                "obsolete" to PageState(marks = listOf(BadgeMark("example.com/obs", "2026-11-19"))),
            ),
        )
        badgeStore.save(initialState)

        writeSyncState(dir, mapOf("docs/audio.md" to "sha1"))
        server.enqueue(MockResponse.Builder().body(badgeHtml("https://example.com/audio", "2026-11-20")).build())

        val s = sync(store, dir, badgeStore)
        val r = s.sync(force = true)
        assertTrue(r is NewBadgeResult.Updated)

        val state = badgeStore.loadState()
        assertTrue(state.pages.containsKey("audio"))
        assertFalse(state.pages.containsKey("obsolete"))
    }

    @Test
    fun requestHeaders_includeUserAgentAndAccept() = runTest {
        val dir = tempFolder.newFolder()
        val store = createStore(listOf("audio" to "audio.md"))
        val badgeStore = NewBadgeStore(dir)
        server.enqueue(MockResponse.Builder().body(badgeHtml("https://example.com/site", "2026-11-20")).build())

        val s = sync(store, dir, badgeStore)
        s.sync(force = true)

        val req = server.takeRequest()
        assertEquals("Mozilla/5.0 (Android; FMHY) NewBadgeSync", req.headers["User-Agent"])
        assertEquals("text/html", req.headers["Accept"])
    }

    @Test
    fun concurrency_atMost4ParallelRequests() = runTest {
        val dir = tempFolder.newFolder()
        val sections = (1..8).map { "sec$it" to "sec$it.md" }
        val store = createStore(sections)
        val badgeStore = NewBadgeStore(dir)

        val activeRequests = AtomicInteger(0)
        val maxObserved = AtomicInteger(0)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val current = activeRequests.incrementAndGet()
                maxObserved.updateAndGet { maxOf(it, current) }
                Thread.sleep(50)
                activeRequests.decrementAndGet()
                return MockResponse.Builder().body(badgeHtml("https://example.com/test", "2026-11-20")).build()
            }
        }

        val s = sync(store, dir, badgeStore)
        val r = s.sync(force = true)
        assertTrue(r is NewBadgeResult.Updated)
        assertEquals(8, (r as NewBadgeResult.Updated).pages)
        assertTrue("Max observed concurrency was ${maxObserved.get()}", maxObserved.get() <= 4)
    }
}
