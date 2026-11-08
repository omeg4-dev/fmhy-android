package io.github.omeg4dev.fmhy.data.sync

import io.github.omeg4dev.fmhy.data.DatasetStoreTest
import io.github.omeg4dev.fmhy.data.parse.Base64Map
import io.github.omeg4dev.fmhy.data.parse.FmhyParser
import io.github.omeg4dev.fmhy.data.parse.SectionInfo
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FmhySyncParseFailureTest {

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
        {
          "audio.md": {
            "title": "Audio",
            "description": "Audio streaming and tools"
          },
          "video.md": {
            "title": "Video",
            "description": "Video streaming and tools"
          }
        }
    """.trimIndent()

    private val sampleRentry = """
        <h4 id="audio-enc"><code>aHR0cHM6Ly9hdWRpby1yZXNvbHZlZC5jb20=</code></h4>
    """.trimIndent()

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
    """.trimIndent()

    private val sampleVideoMd = """
        # Video Streaming
        * [Video Stream](https://video-stream.com) - Top video streaming
    """.trimIndent()

    private fun createSources(): Sources {
        val baseUrl = server.url("/").toString().trimEnd('/')
        return Sources(
            primary = "$baseUrl/",
            fallback = "$baseUrl/fallback/",
            rentry = "$baseUrl/rentry",
            unsafeDomains = "$baseUrl/unsafe-domains.txt",
            unsafeReasons = "$baseUrl/unsafe-reasons.json",
        )
    }

    @Test
    fun testFailedSectionParseDoesNotRecordNewShaAndRetriesOnNextSync() = runTest {
        val syncDir = tempFolder.newFolder("sync_fail_retry")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val files = mutableMapOf(
            "/docs/.vitepress/transformer/headers.json" to sampleConstants,
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

        // 1. Initial sync works for both sections
        val r1 = sync.sync(force = true)
        assertTrue(r1 is SyncResult.Updated)
        val initialAudioSha = sync.loadState().files["docs/audio.md"]?.sha256
        assertEquals(sha256(sampleAudioMd), initialAudioSha)

        // 2. Upstream update: audio gets new content with new SHA
        val updatedAudioMd = sampleAudioMd + "\n* [Audio 2](https://audio2.com) - New audio streaming"
        val newAudioSha = sha256(updatedAudioMd)
        files["/docs/audio.md"] = updatedAudioMd

        // Sync with failing parser for audio
        val parserWithException = { sec: SectionInfo, md: String, b64: Base64Map ->
            if (sec.id == "audio") {
                throw IllegalStateException("Simulated parse exception for audio")
            } else {
                FmhyParser.parseSection(sec, md, b64)
            }
        }

        clockTime += 700L
        val syncFailing = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(),
            clock = { clockTime },
            log = {},
            parser = parserWithException,
        )

        val r2 = syncFailing.sync()
        // Saved state for docs/audio.md must NOT contain the new sha
        val stateAfterFailure = syncFailing.loadState()
        assertNotEquals("Saved state must NOT record the new SHA when parse fails", newAudioSha, stateAfterFailure.files["docs/audio.md"]?.sha256)
        assertEquals("Saved state must keep the previous SHA", initialAudioSha, stateAfterFailure.files["docs/audio.md"]?.sha256)

        // 3. Second sync with working parser: audio IS parsed and stored
        clockTime += 700L
        val syncWorking = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(),
            clock = { clockTime },
            log = {},
        )

        val r3 = syncWorking.sync()
        assertTrue("Second sync should be Updated, was $r3", r3 is SyncResult.Updated)
        val audioEntries = store.entriesForSection("audio")
        assertTrue(
            "Audio section must contain the new entry from second sync",
            audioEntries.any { it.url == "https://audio2.com" },
        )
        val stateAfterSuccess = syncWorking.loadState()
        assertEquals(newAudioSha, stateAfterSuccess.files["docs/audio.md"]?.sha256)

        store.close()
    }

    @Test
    fun testFailedSectionParseOnFirstSyncRemovesStateAndRetriesOnNextSync() = runTest {
        val syncDir = tempFolder.newFolder("sync_fail_first")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val files = mutableMapOf(
            "/docs/.vitepress/transformer/headers.json" to sampleConstants,
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

        val expectedAudioSha = sha256(sampleAudioMd)

        // First sync with failing parser for audio
        val parserWithException = { sec: SectionInfo, md: String, b64: Base64Map ->
            if (sec.id == "audio") {
                throw IllegalStateException("Simulated parse exception for audio")
            } else {
                FmhyParser.parseSection(sec, md, b64)
            }
        }

        var clockTime = 1000L
        val syncFailing = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(),
            clock = { clockTime },
            log = {},
            parser = parserWithException,
        )

        val r1 = syncFailing.sync(force = true)
        val stateAfterFailure = syncFailing.loadState()
        assertNull("Saved state must not have an entry for audio when first parse fails", stateAfterFailure.files["docs/audio.md"])

        // Second sync with working parser
        clockTime += 700L
        val syncWorking = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(),
            clock = { clockTime },
            log = {},
        )

        val r2 = syncWorking.sync()
        assertTrue("Second sync should be Updated, was $r2", r2 is SyncResult.Updated)
        val audioEntries = store.entriesForSection("audio")
        assertTrue("Audio entries should be populated", audioEntries.isNotEmpty())
        val stateAfterSuccess = syncWorking.loadState()
        assertEquals(expectedAudioSha, stateAfterSuccess.files["docs/audio.md"]?.sha256)

        store.close()
    }
}
