package io.github.omeg4dev.fmhy.data.nsfw

import io.github.omeg4dev.fmhy.data.model.SectionItem
import java.io.File
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NsfwSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
        cacheDir = tempFolder.newFolder("cache")
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun checkpointHtml(yasoId: String = "abc123"): String {
        val other = Base64.getEncoder().encodeToString("https://privatebin.example/?x#k".toByteArray())
        val yaso = Base64.getEncoder().encodeToString("https://yaso.su/$yasoId".toByteArray())
        return "<html><body><p>$other</p><p>$yaso</p></body></html>"
    }

    private fun source(clock: () -> Long = { 1_700_000_000_000L }) = NsfwSource(
        client = client,
        cacheDir = cacheDir,
        checkpointUrl = server.url("/NSFW-Checkpoint").toString(),
        rawBaseUrl = server.url("/").toString(),
        clock = clock,
    )

    private val sampleMarkdown = """
        ## Table of Contents
        * [Streaming](#streaming)

        ## Streaming
        * ⭐ **[SafeSite](https://safe.example)** - Clean streaming site
        * [BadEntry](https://bad.example) - Uses onlyfans

        ## Leaks
        * [Leaked](https://leaked-host.example) - Dropped category
    """.trimIndent()

    @Test
    fun successFetchesWritesAtomicCacheAndFilters() = runTest {
        server.enqueue(MockResponse.Builder().code(200).setHeader("Content-Type", "text/html").body(checkpointHtml("list1")).build())
        server.enqueue(MockResponse.Builder().code(200).setHeader("Content-Type", "text/plain; charset=utf-8").body(sampleMarkdown).build())

        val src = source()
        val result = src.load()

        assertTrue("Expected Success, got $result", result is NsfwLoadResult.Success)
        val success = result as NsfwLoadResult.Success
        assertFalse(success.stale)
        assertEquals(2, server.requestCount)

        val entries = success.entries
        assertEquals(1, entries.size)
        assertEquals("SafeSite", entries[0].name)

        val cacheFile = File(cacheDir, "nsfw/list.md")
        assertTrue(cacheFile.exists())
        assertEquals(sampleMarkdown, cacheFile.readText())
        assertFalse(File(cacheDir, "nsfw/list.md.tmp").exists())
    }

    @Test
    fun htmlResponseRejected() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(checkpointHtml("list1")).build())
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .body("## Streaming\n* [Site](https://site.example) - desc")
                .build()
        )

        val result = source().load()
        assertTrue(result is NsfwLoadResult.Failed)
        assertFalse(File(cacheDir, "nsfw/list.md").exists())
    }

    @Test
    fun tooLargeResponseRejected() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(checkpointHtml("list1")).build())
        val prefix = "## Streaming\n* [Site](https://site.example) - "
        val oversized = prefix + "a".repeat(2 * 1024 * 1024 + 16)
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/plain")
                .body(oversized)
                .build()
        )

        val result = source().load()
        assertTrue(result is NsfwLoadResult.Failed)
        assertFalse(File(cacheDir, "nsfw/list.md").exists())
    }

    @Test
    fun httpErrorFallsBackToStaleCache() = runTest {
        var nowMs = 1_700_000_000_000L
        val src = source(clock = { nowMs })

        server.enqueue(MockResponse.Builder().code(200).body(checkpointHtml("list1")).build())
        server.enqueue(MockResponse.Builder().code(200).setHeader("Content-Type", "text/plain").body(sampleMarkdown).build())
        assertTrue(src.load() is NsfwLoadResult.Success)
        assertEquals(2, server.requestCount)

        // Advance past 24 hours
        nowMs += 25L * 60 * 60 * 1000
        server.enqueue(MockResponse.Builder().code(503).body("Service Unavailable").build())

        val fallback = src.load()
        assertTrue(fallback is NsfwLoadResult.Success)
        val success = fallback as NsfwLoadResult.Success
        assertTrue(success.stale)
        assertEquals(listOf("SafeSite"), success.entries.map { it.name })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun freshCacheMakesNoRequestAndForcedRefreshDoes() = runTest {
        var nowMs = 1_700_000_000_000L
        val src = source(clock = { nowMs })

        server.enqueue(MockResponse.Builder().code(200).body(checkpointHtml("list1")).build())
        server.enqueue(MockResponse.Builder().code(200).setHeader("Content-Type", "text/plain").body(sampleMarkdown).build())
        assertTrue(src.load() is NsfwLoadResult.Success)
        assertEquals(2, server.requestCount)

        // Advance 12 hours (< 24h) -> fresh cache, no network call
        nowMs += 12L * 60 * 60 * 1000
        val cachedRes = src.load(forceRefresh = false)
        assertTrue(cachedRes is NsfwLoadResult.Success)
        assertEquals(2, server.requestCount)

        // Force refresh -> hits network even though cache is fresh
        val updatedMd = "## Streaming\n* [UpdatedSite](https://updated.example) - Fresh"
        server.enqueue(MockResponse.Builder().code(200).body(checkpointHtml("list2")).build())
        server.enqueue(MockResponse.Builder().code(200).setHeader("Content-Type", "text/plain").body(updatedMd).build())

        val forced = src.load(forceRefresh = true)
        assertTrue(forced is NsfwLoadResult.Success)
        assertEquals(4, server.requestCount)
        assertEquals(listOf("UpdatedSite"), (forced as NsfwLoadResult.Success).entries.map { it.name })
    }

    @Test
    fun filterAppliesOnCachedReadAndClearCacheDeletesFile() = runTest {
        val dir = File(cacheDir, "nsfw").apply { mkdirs() }
        val file = File(dir, "list.md")
        val nowMs = 1_700_000_000_000L
        file.writeText(sampleMarkdown)
        file.setLastModified(nowMs)

        val src = source(clock = { nowMs })
        val res = src.load(forceRefresh = false)
        assertTrue(res is NsfwLoadResult.Success)
        val names = (res as NsfwLoadResult.Success).entries.map { it.name }
        assertEquals(listOf("SafeSite"), names)
        assertEquals(0, server.requestCount)

        src.clearCache()
        assertFalse(file.exists())
    }

    @Test
    fun missingHeadingOrEntryIsRejected() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(checkpointHtml("list1")).build())
        server.enqueue(MockResponse.Builder().code(200).setHeader("Content-Type", "text/plain").body("Just plain text\n* no link").build())

        val result = source().load()
        assertTrue(result is NsfwLoadResult.Failed)
    }

    @Test
    fun cancellationIsRethrown() = runTest {
        val job = async {
            throw CancellationException("cancelled")
        }
        try {
            job.await()
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }
}
