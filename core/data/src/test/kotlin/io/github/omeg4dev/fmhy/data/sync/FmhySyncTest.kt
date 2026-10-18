package io.github.omeg4dev.fmhy.data.sync

import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.DatasetStoreTest
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.search.SearchEngine
import java.io.File
import java.security.MessageDigest
import io.github.omeg4dev.fmhy.data.parse.FmhyParser
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FmhySyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun sha256(str: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(str.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private val sampleConstants = """
        export const sections = {
          'audio.md': {
            title: 'Audio',
            description: 'Audio streaming and tools'
          },
          'video.md': {
            title: 'Video',
            description: 'Video streaming and tools'
          }
        }
    """.trimIndent()

    private val sampleRentry = """
        <h4 id="audio-enc"><code>aHR0cHM6Ly9hdWRpby1yZXNvbHZlZC5jb20=</code></h4>
    """.trimIndent() // decodes to https://audio-resolved.com

    private val sampleUnsafeDomains = """
        # Bad sites
        malware.example
    """.trimIndent()

    private val sampleUnsafeReasons = """
        {"malware.example": "Known malware distribution"}
    """.trimIndent()

    private val sampleAudioMd = """
        # Audio Streaming
        * [Audio Normal](https://audio-normal.com) - Normal audio streaming
        * [Audio Secret](https://rentry.co/FMHYB64#audio-enc) - Base64 resolved audio
    """.trimIndent()

    private val sampleVideoMd = """
        # Video Streaming
        * [Video Stream](https://video-stream.com) - Top video streaming
    """.trimIndent()

    private fun createSources(primaryPrefix: String = "", fallbackPrefix: String = ""): Sources {
        val baseUrl = server.url("/").toString().trimEnd('/')
        return Sources(
            primary = if (primaryPrefix.isEmpty()) "$baseUrl/" else "$baseUrl/$primaryPrefix/",
            fallback = if (fallbackPrefix.isEmpty()) "$baseUrl/fallback/" else "$baseUrl/$fallbackPrefix/",
            rentry = "$baseUrl/rentry",
            unsafeDomains = "$baseUrl/unsafe-domains.txt",
            unsafeReasons = "$baseUrl/unsafe-reasons.json",
        )
    }

    /**
     * 1. Empty state -> first sync downloads everything, DB has the entries;
     *    second sync (server returns 304 for all) -> UpToDate, DB version unchanged,
     *    0 rows written (check changes count and version).
     */
    @Test
    fun test1EmptyStateFirstSyncAndSecondSync304UpToDate() = runTest {
        val syncDir = tempFolder.newFolder("sync1")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val files = mutableMapOf(
            "/docs/.vitepress/transformer/constants.ts" to sampleConstants,
            "/rentry" to sampleRentry,
            "/unsafe-domains.txt" to sampleUnsafeDomains,
            "/unsafe-reasons.json" to sampleUnsafeReasons,
            "/docs/audio.md" to sampleAudioMd,
            "/docs/video.md" to sampleVideoMd,
        )

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val content = files[path] ?: return MockResponse.Builder().code(404).build()
                val etag = "\"${sha256(content)}\""
                val ifNoneMatch = request.headers["If-None-Match"]
                if (ifNoneMatch == etag) {
                    return MockResponse.Builder().code(304).build()
                }
                return MockResponse.Builder()
                    .code(200)
                    .setHeader("ETag", etag)
                    .body(content)
                    .build()
            }
        }

        var clockTime = 1000L
        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(),
            clock = { clockTime },
            log = {},
        )

        // First sync: downloads everything
        val res1 = sync.sync(force = true)
        assertTrue("First sync should be Updated, was $res1", res1 is SyncResult.Updated)
        val updated1 = res1 as SyncResult.Updated
        assertTrue(updated1.added >= 3)
        assertEquals(2, updated1.changedSections)
        assertEquals(emptyList<String>(), updated1.failedSections)

        val v1 = store.version()
        assertTrue(v1 > 1L)
        val changes1 = store.changes().size
        assertTrue(changes1 > 0)

        // Second sync: server returns 304 for all
        clockTime += 700L
        val res2 = sync.sync(force = false)
        assertTrue("Second sync should be UpToDate, was $res2", res2 is SyncResult.UpToDate)
        assertEquals(v1, store.version())
        assertEquals(changes1, store.changes().size)

        store.close()
    }

    /**
     * 2. One section file changes (one entry added, one removed, one starred) -> only that section touched;
     *    Updated(added=1, removed=1); unchanged entries keep num and first_seen; new entry findable via
     *    SearchEngine; removed one gone from FTS.
     */
    @Test
    fun test2OneSectionChangesAddRemoveStar() = runTest {
        val syncDir = tempFolder.newFolder("sync2")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val initialAudio = """
            # Audio
            * [Keep Site](https://keep.com) - Stays unchanged
            * [Remove Site](https://remove.com) - Will be removed
            * [Star Site](https://star.com) - Will be starred
        """.trimIndent()

        val files = mutableMapOf(
            "/docs/.vitepress/transformer/constants.ts" to sampleConstants,
            "/rentry" to sampleRentry,
            "/unsafe-domains.txt" to sampleUnsafeDomains,
            "/unsafe-reasons.json" to sampleUnsafeReasons,
            "/docs/audio.md" to initialAudio,
            "/docs/video.md" to sampleVideoMd,
        )

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val content = files[path] ?: return MockResponse.Builder().code(404).build()
                val etag = "\"${sha256(content)}\""
                if (request.headers["If-None-Match"] == etag) {
                    return MockResponse.Builder().code(304).build()
                }
                return MockResponse.Builder()
                    .code(200)
                    .setHeader("ETag", etag)
                    .body(content)
                    .build()
            }
        }

        var clockTime = 1000L
        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(),
            clock = { clockTime },
            log = {},
        )

        // Initial sync
        val r1 = sync.sync(force = true)
        assertTrue(r1 is SyncResult.Updated)

        val searchEngine = SearchEngine(store)
        val keepEntryBefore = store.entriesForSection("audio").first { it.name == "Keep Site" }
        val numBefore = keepEntryBefore.num
        val firstSeenBefore = keepEntryBefore.firstSeen

        // Update audio: Remove Site removed, Star Site starred, Added Site added
        val updatedAudio = """
            # Audio
            * [Keep Site](https://keep.com) - Stays unchanged
            * ⭐ **[Star Site](https://star.com)** - Will be starred
            * [Added Site](https://added.com) - Newly added site
        """.trimIndent()
        files["/docs/audio.md"] = updatedAudio

        clockTime += 700L
        val r2 = sync.sync(force = false)
        assertTrue("Should be Updated, was $r2", r2 is SyncResult.Updated)
        val updated2 = r2 as SyncResult.Updated
        assertEquals(1, updated2.added)
        assertEquals(1, updated2.removed)
        assertEquals(1, updated2.changedSections)

        // Unchanged entry keeps num and first_seen
        val keepEntryAfter = store.entriesForSection("audio").first { it.name == "Keep Site" }
        assertEquals(numBefore, keepEntryAfter.num)
        assertEquals(firstSeenBefore, keepEntryAfter.firstSeen)

        // Starred entry has tier >= 1 and starredAt
        val starEntryAfter = store.entriesForSection("audio").first { it.name == "Star Site" }
        assertEquals(Tier.STAR, starEntryAfter.tier)
        assertEquals(clockTime, starEntryAfter.starredAt)

        // Search engine verification
        val searchAdded = searchEngine.search("Added Site")
        assertEquals(1, searchAdded.total)

        val searchRemoved = searchEngine.search("Remove Site")
        assertEquals(0, searchRemoved.total)

        store.close()
    }

    /**
     * 3. Body 200 but identical sha (etag changed) -> no re-parse, UpToDate.
     */
    @Test
    fun test3Body200IdenticalShaIsUpToDate() = runTest {
        val syncDir = tempFolder.newFolder("sync3")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        var etagSuffix = "v1"
        val files = mapOf(
            "/docs/.vitepress/transformer/constants.ts" to sampleConstants,
            "/rentry" to sampleRentry,
            "/unsafe-domains.txt" to sampleUnsafeDomains,
            "/unsafe-reasons.json" to sampleUnsafeReasons,
            "/docs/audio.md" to sampleAudioMd,
            "/docs/video.md" to sampleVideoMd,
        )

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val content = files[path] ?: return MockResponse.Builder().code(404).build()
                // Never return 304, always 200 with changing etag
                return MockResponse.Builder()
                    .code(200)
                    .setHeader("ETag", "\"etag-$etagSuffix-${sha256(content)}\"")
                    .body(content)
                    .build()
            }
        }

        var clockTime = 1000L
        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(),
            clock = { clockTime },
            log = {},
        )

        val r1 = sync.sync(force = true)
        assertTrue(r1 is SyncResult.Updated)
        val v1 = store.version()

        // Change etag on server, but body bytes are unchanged
        etagSuffix = "v2"
        clockTime += 700L
        val r2 = sync.sync(force = false)
        assertTrue("Should be UpToDate because sha256 did not change, was $r2", r2 is SyncResult.UpToDate)
        assertEquals(v1, store.version())

        store.close()
    }

    /**
     * 4. Primary returns 500 for a path -> served from fallback; source recorded.
     */
    @Test
    fun test4PrimaryReturns500ServedFromFallback() = runTest {
        val syncDir = tempFolder.newFolder("sync4")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val files = mapOf(
            "/docs/.vitepress/transformer/constants.ts" to sampleConstants,
            "/rentry" to sampleRentry,
            "/unsafe-domains.txt" to sampleUnsafeDomains,
            "/unsafe-reasons.json" to sampleUnsafeReasons,
            "/docs/audio.md" to sampleAudioMd,
            "/docs/video.md" to sampleVideoMd,
        )

        // Primary fails on /docs/audio.md with 500; fallback serves it
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                if (path == "/docs/audio.md") {
                    return MockResponse.Builder().code(500).body("Internal Server Error").build()
                }
                if (path.startsWith("/fallback/docs/")) {
                    val subPath = path.removePrefix("/fallback")
                    val content = files[subPath] ?: return MockResponse.Builder().code(404).build()
                    return MockResponse.Builder().code(200).body(content).build()
                }
                val content = files[path] ?: return MockResponse.Builder().code(404).build()
                return MockResponse.Builder().code(200).body(content).build()
            }
        }

        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(),
            clock = { 1000L },
            log = {},
        )

        val r = sync.sync(force = true)
        assertTrue("Sync should succeed with fallback, was $r", r is SyncResult.Updated)
        val entries = store.entriesForSection("audio")
        assertTrue(entries.isNotEmpty())

        store.close()
    }

    /**
     * 5. Constants lists a missing file -> 404 -> section absent, no failure.
     */
    @Test
    fun test5ConstantsListsMissingFile404SectionAbsentNoFailure() = runTest {
        val syncDir = tempFolder.newFolder("sync5")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val constantsWithMissing = """
            export const sections = {
              'audio.md': {
                title: 'Audio',
                description: 'Audio tools'
              },
              'non-existent.md': {
                title: 'Non Existent',
                description: 'Does not exist'
              }
            }
        """.trimIndent()

        val files = mapOf(
            "/docs/.vitepress/transformer/constants.ts" to constantsWithMissing,
            "/rentry" to sampleRentry,
            "/unsafe-domains.txt" to sampleUnsafeDomains,
            "/unsafe-reasons.json" to sampleUnsafeReasons,
            "/docs/audio.md" to sampleAudioMd,
        )

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val content = files[path] ?: return MockResponse.Builder().code(404).build()
                return MockResponse.Builder().code(200).body(content).build()
            }
        }

        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(),
            clock = { 1000L },
            log = {},
        )

        val res = sync.sync(force = true)
        assertTrue("Should succeed despite 404 section, was $res", res is SyncResult.Updated)
        val sections = store.sections()
        assertEquals(1, sections.size)
        assertEquals("audio", sections[0].id)

        store.close()
    }

    /**
     * 6. Rentry changes while all .md are 304 -> base64 entry URLs update (re-parse from raw cache).
     */
    @Test
    fun test6RentryChangesWhileMdAre304ReParseFromRawCache() = runTest {
        val syncDir = tempFolder.newFolder("sync6")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        var rentryContent = """
            <h4 id="audio-enc"><code>aHR0cHM6Ly9vbGQtcmVzb2x2ZWQuY29t</code></h4>
        """.trimIndent() // https://old-resolved.com

        val files = mutableMapOf(
            "/docs/.vitepress/transformer/constants.ts" to sampleConstants,
            "/unsafe-domains.txt" to sampleUnsafeDomains,
            "/unsafe-reasons.json" to sampleUnsafeReasons,
            "/docs/audio.md" to sampleAudioMd,
            "/docs/video.md" to sampleVideoMd,
        )

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                if (path == "/rentry") {
                    val etag = "\"${sha256(rentryContent)}\""
                    if (request.headers["If-None-Match"] == etag) {
                        return MockResponse.Builder().code(304).build()
                    }
                    return MockResponse.Builder().code(200).setHeader("ETag", etag).body(rentryContent).build()
                }
                val content = files[path] ?: return MockResponse.Builder().code(404).build()
                val etag = "\"${sha256(content)}\""
                if (request.headers["If-None-Match"] == etag) {
                    return MockResponse.Builder().code(304).build()
                }
                return MockResponse.Builder().code(200).setHeader("ETag", etag).body(content).build()
            }
        }

        var clockTime = 1000L
        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(),
            clock = { clockTime },
            log = {},
        )

        val r1 = sync.sync(force = true)
        assertTrue(r1 is SyncResult.Updated)

        val secretEntry1 = store.entriesForSection("audio").first { it.name == "Audio Secret" }
        assertEquals("https://old-resolved.com", secretEntry1.url)

        // Rentry changes with new URL
        rentryContent = """
            <h4 id="audio-enc"><code>aHR0cHM6Ly9uZXctcmVzb2x2ZWQuY29t</code></h4>
        """.trimIndent() // https://new-resolved.com

        clockTime += 700L
        val r2 = sync.sync(force = false)
        assertTrue("Should be Updated after rentry changed, was $r2", r2 is SyncResult.Updated)

        val secretEntry2 = store.entriesForSection("audio").first { it.name == "Audio Secret" }
        assertEquals("https://new-resolved.com", secretEntry2.url)

        store.close()
    }

    /**
     * 7. All hosts down -> Failed, data intact, lastSuccess unchanged.
     */
    @Test
    fun test7AllHostsDownFailedDataIntactLastSuccessUnchanged() = runTest {
        val syncDir = tempFolder.newFolder("sync7")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val files = mapOf(
            "/docs/.vitepress/transformer/constants.ts" to sampleConstants,
            "/rentry" to sampleRentry,
            "/unsafe-domains.txt" to sampleUnsafeDomains,
            "/unsafe-reasons.json" to sampleUnsafeReasons,
            "/docs/audio.md" to sampleAudioMd,
            "/docs/video.md" to sampleVideoMd,
        )

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val content = files[path] ?: return MockResponse.Builder().code(404).build()
                return MockResponse.Builder().code(200).body(content).build()
            }
        }

        var clockTime = 1000L
        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(),
            clock = { clockTime },
            log = {},
        )

        val r1 = sync.sync(force = true)
        assertTrue(r1 is SyncResult.Updated)
        val v1 = store.version()
        val count1 = store.entriesForSection("audio").size

        // Shut down server
        server.close()

        clockTime += 700L
        val r2 = sync.sync(force = false)
        assertTrue("Should be Failed when hosts are down, was $r2", r2 is SyncResult.Failed)

        // Data is intact
        assertEquals(v1, store.version())
        assertEquals(count1, store.entriesForSection("audio").size)

        store.close()
    }

    /**
     * 8. Malformed/garbage markdown for one section -> other sections still update; failedSections lists it.
     */
    @Test
    fun test8MalformedMarkdownOneSectionFailsOtherUpdates() = runTest {
        val syncDir = tempFolder.newFolder("sync8")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val files = mutableMapOf(
            "/docs/.vitepress/transformer/constants.ts" to sampleConstants,
            "/rentry" to sampleRentry,
            "/unsafe-domains.txt" to sampleUnsafeDomains,
            "/unsafe-reasons.json" to sampleUnsafeReasons,
            "/docs/audio.md" to sampleAudioMd,
            "/docs/video.md" to sampleVideoMd,
        )

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val content = files[path] ?: return MockResponse.Builder().code(404).build()
                return MockResponse.Builder().code(200).body(content).build()
            }
        }

        var clockTime = 1000L
        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(),
            clock = { clockTime },
            log = {},
        )

        // Initial sync works for both
        val r1 = sync.sync(force = true)
        assertTrue(r1 is SyncResult.Updated)
        val initialAudioCount = store.entriesForSection("audio").size

        // Update: video gets new entry, audio has garbage markdown that causes parser failure
        files["/docs/video.md"] = sampleVideoMd + "\n* [Video 2](https://video2.com) - Another video"
        files["/docs/audio.md"] = "### Corrupted Garbage Markdown Content"

        val parserWithException = { sec: io.github.omeg4dev.fmhy.data.parse.SectionInfo, md: String, b64: io.github.omeg4dev.fmhy.data.parse.Base64Map ->
            if (sec.id == "audio") {
                throw IllegalStateException("Corrupted markdown stream for audio")
            } else {
                FmhyParser.parseSection(sec, md, b64)
            }
        }

        clockTime += 700L
        val syncWithFailingParser = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(),
            clock = { clockTime },
            log = {},
            parser = parserWithException,
        )

        // Force sync so audio is parsed again
        val r2 = syncWithFailingParser.sync(force = true)
        assertTrue("Result should still be Updated, was $r2", r2 is SyncResult.Updated)
        val updated = r2 as SyncResult.Updated
        assertTrue("failedSections must contain 'audio'", updated.failedSections.contains("audio"))
        assertTrue("Video section should have updated", updated.added >= 1)

        // Old audio rows are kept intact
        assertEquals(initialAudioCount, store.entriesForSection("audio").size)

        store.close()
    }
}
