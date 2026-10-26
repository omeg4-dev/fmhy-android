package io.github.omeg4dev.fmhy.data.sync

import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.DatasetStoreTest
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.search.SearchEngine
import java.io.File
import java.security.MessageDigest
import io.github.omeg4dev.fmhy.data.parse.FmhyParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
        // first sync is a silent baseline: no added rows
        assertEquals(0, changes1)

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

    /**
     * 9. Change classification: rename, url change, unstar, move, description edit
     * each produce the right row with the §3 detail and commit_sha starting with "sync:".
     */
    @Test
    fun testChangeClassificationRenameUrlChangeUnstarMoveEdit() = runTest {
        val syncDir = tempFolder.newFolder("sync9")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val initialAudio = """
            # Audio Streaming
            * [Rename Site](https://rename.com) - Audio streaming
            * [UrlChange Site](https://old-url.com) - Url change test
            * ⭐ **[Unstar Site](https://unstar.com)** - Starred site
            * [Move Site](https://move.com) - Move site
            * [Edit Site](https://edit.com) - Old description
        """.trimIndent()

        val initialVideo = """
            # Video Streaming
            * [Video Site](https://video-stream.com) - Top video streaming
        """.trimIndent()

        val files = mutableMapOf(
            "/docs/.vitepress/transformer/constants.ts" to sampleConstants,
            "/rentry" to sampleRentry,
            "/unsafe-domains.txt" to sampleUnsafeDomains,
            "/unsafe-reasons.json" to sampleUnsafeReasons,
            "/docs/audio.md" to initialAudio,
            "/docs/video.md" to initialVideo,
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

        // 1. Initial sync
        val r1 = sync.sync(force = true)
        assertTrue(r1 is SyncResult.Updated)

        // 2. Prepare updates for next sync
        val updatedAudio = """
            # Audio Streaming
            * [Renamed Audio](https://rename.com) - Audio streaming
            * [UrlChange Site](https://new-url.com) - Url change test
            * [Unstar Site](https://unstar.com) - Starred site
            * [Edit Site](https://edit.com) - New description
        """.trimIndent()

        val updatedVideo = """
            # Video Streaming
            * [Video Site](https://video-stream.com) - Top video streaming
            * [Move Site](https://move.com) - Move site
        """.trimIndent()

        files["/docs/audio.md"] = updatedAudio
        files["/docs/video.md"] = updatedVideo

        clockTime = 2000L
        val r2 = sync.sync(force = false)
        assertTrue("Second sync should be Updated, was $r2", r2 is SyncResult.Updated)

        // 3. Verify changes table for ts = 2000L
        data class ChangeRow(
            val kind: String,
            val name: String,
            val url: String,
            val sectionId: String,
            val detail: String,
            val commitSha: String,
        )

        val changes = mutableListOf<ChangeRow>()
        store.withConnection { conn ->
            conn.prepare("SELECT kind, name, url, section_id, detail, commit_sha FROM changes WHERE ts = 2000").use { stmt ->
                while (stmt.step()) {
                    changes.add(
                        ChangeRow(
                            kind = stmt.getText(0),
                            name = stmt.getText(1),
                            url = stmt.getText(2),
                            sectionId = stmt.getText(3),
                            detail = stmt.getText(4),
                            commitSha = stmt.getText(5),
                        )
                    )
                }
            }
        }

        // All changes must have commit_sha = "sync:2000"
        for (c in changes) {
            assertTrue("commit_sha must start with 'sync:', was '${c.commitSha}'", c.commitSha.startsWith("sync:"))
            assertEquals("sync:2000", c.commitSha)
        }

        // Verify rename
        val renameChange = changes.firstOrNull { it.kind == "renamed" }
        assertNotNull("Expected renamed row", renameChange)
        assertEquals("Renamed Audio", renameChange!!.name)
        assertEquals("https://rename.com", renameChange.url)
        assertEquals("audio", renameChange.sectionId)
        assertEquals("Rename Site", renameChange.detail)

        // Verify url_changed
        val urlChange = changes.firstOrNull { it.kind == "url_changed" }
        assertNotNull("Expected url_changed row", urlChange)
        assertEquals("UrlChange Site", urlChange!!.name)
        assertEquals("https://new-url.com", urlChange.url)
        assertEquals("audio", urlChange.sectionId)
        assertEquals("https://old-url.com", urlChange.detail)

        // Verify unstarred
        val unstarChange = changes.firstOrNull { it.kind == "unstarred" }
        assertNotNull("Expected unstarred row", unstarChange)
        assertEquals("Unstar Site", unstarChange!!.name)
        assertEquals("https://unstar.com", unstarChange.url)
        assertEquals("audio", unstarChange.sectionId)
        assertEquals("", unstarChange.detail)

        // Verify moved
        val moveChange = changes.firstOrNull { it.kind == "moved" }
        assertNotNull("Expected moved row", moveChange)
        assertEquals("Move Site", moveChange!!.name)
        assertEquals("https://move.com", moveChange.url)
        assertEquals("video", moveChange.sectionId)
        assertEquals("audio", moveChange.detail)

        // Verify edited
        val editChange = changes.firstOrNull { it.kind == "edited" }
        assertNotNull("Expected edited row", editChange)
        assertEquals("Edit Site", editChange!!.name)
        assertEquals("https://edit.com", editChange.url)
        assertEquals("audio", editChange.sectionId)
        // detail = old line + "\n" + new line
        assertEquals("* [Edit Site](https://edit.com) - Old description\n* [Edit Site](https://edit.com) - New description", editChange.detail)

        // Verify that move and url_change are not also in added or removed
        val addedUrls = changes.filter { it.kind == "added" }.map { it.url }
        val removedUrls = changes.filter { it.kind == "removed" }.map { it.url }
        assertTrue("Move Site should not be in added", "https://move.com" !in addedUrls)
        assertTrue("Move Site should not be in removed", "https://move.com" !in removedUrls)
        assertTrue("UrlChange Site should not be in added", "https://new-url.com" !in addedUrls)
        assertTrue("UrlChange Site should not be in removed", "https://old-url.com" !in removedUrls)

        store.close()
    }

    @Test
    fun syncReportsStepsInOrder() = runTest {
        val syncDir = tempFolder.newFolder("sync_steps")
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
                return MockResponse.Builder()
                    .code(200)
                    .setHeader("ETag", etag)
                    .body(content)
                    .build()
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

        val events = mutableListOf<SyncProgress>()
        val result = sync.sync(force = true, onProgress = { events.add(it) })
        assertTrue("Expected Updated, got $result", result is SyncResult.Updated)

        assertTrue(events.isNotEmpty())
        assertEquals(SyncProgress.Checking, events.first())

        val filesEvents = events.filterIsInstance<SyncProgress.Files>()
        assertTrue(filesEvents.isNotEmpty())
        assertEquals(SyncProgress.Files(0, 2, 0L, true), filesEvents.first())
        assertTrue(filesEvents.all { it.firstDownload })
        for (i in 0 until filesEvents.size - 1) {
            assertTrue("Files done values must be non-decreasing", filesEvents[i].done <= filesEvents[i + 1].done)
        }
        val lastFiles = filesEvents.last()
        assertEquals(2, lastFiles.done)
        assertEquals(2, lastFiles.total)
        assertTrue("Downloaded bytes should be > 0", lastFiles.bytes > 0L)

        val readingEvents = events.filterIsInstance<SyncProgress.Reading>()
        assertTrue(readingEvents.isNotEmpty())
        assertEquals(SyncProgress.Reading(0, 2), readingEvents.first())
        for (i in 0 until readingEvents.size - 1) {
            assertTrue("Reading done values must be non-decreasing", readingEvents[i].done <= readingEvents[i + 1].done)
        }
        val lastReading = readingEvents.last()
        assertEquals(2, lastReading.done)
        assertEquals(2, lastReading.total)

        assertEquals(SyncProgress.Saving, events.last())
        store.close()
    }

    @Test
    fun secondSyncUpToDateHasNoSaving() = runTest {
        val syncDir = tempFolder.newFolder("sync_uptodate")
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

        // First sync
        val r1 = sync.sync(force = true)
        assertTrue(r1 is SyncResult.Updated)

        // Second sync (304 for all)
        clockTime += 700L
        val events2 = mutableListOf<SyncProgress>()
        val r2 = sync.sync(force = false, onProgress = { events2.add(it) })
        assertTrue("Second sync should be UpToDate, was $r2", r2 is SyncResult.UpToDate)

        assertTrue(events2.contains(SyncProgress.Checking))
        val filesEvents2 = events2.filterIsInstance<SyncProgress.Files>()
        assertTrue(filesEvents2.isNotEmpty())
        assertTrue(filesEvents2.all { !it.firstDownload })
        assertTrue(events2.none { it is SyncProgress.Saving })
        store.close()
    }

    @Test
    fun cancelPropagates() = runTest {
        val syncDir = tempFolder.newFolder("sync_cancel")
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
                return MockResponse.Builder()
                    .code(200)
                    .setHeader("ETag", etag)
                    .body(content)
                    .build()
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

        var syncJob: Job? = null
        var caughtCancellation = false
        var syncResult: SyncResult? = null

        syncJob = launch {
            try {
                syncResult = sync.sync(force = true) { progress ->
                    if (progress is SyncProgress.Files) {
                        syncJob?.cancel()
                    }
                }
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            }
        }

        syncJob.join()
        assertTrue(syncJob.isCancelled)
        assertTrue(caughtCancellation)
        assertNull(syncResult)
        store.close()
    }

    @Test
    fun testFirstSyncAfterSnapshotWritesNoAddedRows() = runTest {
        val syncDir = tempFolder.newFolder("sync_snapshot")
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
        val logs = mutableListOf<String>()
        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(),
            clock = { clockTime },
            log = { logs.add(it) },
        )

        // Seed initial store content (like a bundled snapshot)
        val initialRes = sync.sync(force = true)
        assertTrue(initialRes is SyncResult.Updated)
        // Bundled snapshot has an empty changes table
        store.withConnection { conn -> conn.execSQL("DELETE FROM changes;") }

        // Seed sync state as the snapshot would (source = "bundled", lastCheck = 0)
        val bundledState = SyncState(
            source = "bundled",
            lastCheck = 0L,
            lastSuccess = clockTime,
            files = mapOf(
                "docs/.vitepress/transformer/constants.ts" to FileState(sha256 = sha256(sampleConstants)),
                "rentry" to FileState(sha256 = sha256(sampleRentry)),
                "filterlist-domains.txt" to FileState(sha256 = sha256(sampleUnsafeDomains)),
                "filterlists-reasons.json" to FileState(sha256 = sha256(sampleUnsafeReasons)),
                "docs/audio.md" to FileState(sha256 = sha256(sampleAudioMd)),
                "docs/video.md" to FileState(sha256 = sha256(sampleVideoMd)),
            ),
            version = store.version(),
        )
        sync.saveState(bundledState)

        // Serve a live wiki with one extra entry vs the store
        val videoWithExtra = """
            # Video Streaming
            * [Video Stream](https://video-stream.com) - Top video streaming
            * [Extra Stream](https://extra-stream.com) - Extra video streaming
        """.trimIndent()
        files["/docs/video.md"] = videoWithExtra

        // First sync (baseline): store.changes().filter { it.kind == "added" } is empty
        clockTime += 700L
        val res1 = sync.sync(force = true)
        assertTrue("First sync should be Updated, was $res1", res1 is SyncResult.Updated)
        val addedChanges1 = store.changes().filter { it.kind == ChangeKind.ADDED }
        assertEquals("Baseline sync after snapshot should write no added rows", 0, addedChanges1.size)
        assertTrue(logs.any { it.startsWith("FmhySync: saving took") && it.contains("baseline=true") })

        // Second sync where server adds exactly one new entry: exactly ONE 'added' row, for that entry
        val videoWithSecondExtra = """
            # Video Streaming
            * [Video Stream](https://video-stream.com) - Top video streaming
            * [Extra Stream](https://extra-stream.com) - Extra video streaming
            * [Second Stream](https://second-stream.com) - Second new stream
        """.trimIndent()
        files["/docs/video.md"] = videoWithSecondExtra

        clockTime += 700L
        val res2 = sync.sync(force = true)
        assertTrue("Second sync should be Updated, was $res2", res2 is SyncResult.Updated)
        val addedChanges2 = store.changes().filter { it.kind == ChangeKind.ADDED }
        assertEquals(1, addedChanges2.size)
        assertEquals("Second Stream", addedChanges2[0].name)
        assertEquals("https://second-stream.com", addedChanges2[0].url)
        assertTrue(logs.any { it.startsWith("FmhySync: saving took") && it.contains("baseline=false") })

        store.close()
    }

    @Test
    fun testFirstSyncOnEmptyStoreWritesNoAddedRows() = runTest {
        val syncDir = tempFolder.newFolder("sync_empty_store")
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
                return MockResponse.Builder()
                    .code(200)
                    .setHeader("ETag", etag)
                    .body(content)
                    .build()
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

        // Store has no sections and default state (SyncState())
        assertTrue(store.sections().isEmpty())
        val res = sync.sync(force = true)
        assertTrue("Sync should be Updated, was $res", res is SyncResult.Updated)

        // After sync entries exist, zero 'added' rows
        assertTrue(store.sections().isNotEmpty())
        assertTrue(store.entriesForSection("audio").isNotEmpty())
        val addedRows = store.changes().filter { it.kind == ChangeKind.ADDED }
        assertEquals("Empty store first sync should write zero added rows", 0, addedRows.size)

        store.close()
    }
}
