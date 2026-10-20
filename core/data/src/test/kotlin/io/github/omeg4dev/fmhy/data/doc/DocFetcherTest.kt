package io.github.omeg4dev.fmhy.data.doc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DocFetcherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun fetch_firstTime_savesToCacheAndReturnsSuccess() = runBlocking {
        val docsDir = tempFolder.newFolder("docs")
        var fetchCount = 0
        var requestedUrl: String? = null
        var sentEtag: String? = null

        val fetcher = DocFetcher(
            docsDir = docsDir,
            clock = { 1000L },
            fetchFunction = { url, ifNoneMatch ->
                fetchCount++
                requestedUrl = url
                sentEtag = ifNoneMatch
                FetchResponse.Success(
                    body = "---\ntitle: Guide\n---\n# Hello World",
                    etag = "\"v1\"",
                )
            }
        )

        val result = fetcher.get("beginners-guide.md")
        assertTrue(result is DocResult.Success)
        val success = result as DocResult.Success
        assertFalse(success.stale)
        assertEquals("# Hello World", success.text)
        assertEquals("Guide", success.frontMatter["title"])
        assertEquals(1, fetchCount)
        assertTrue(requestedUrl!!.contains("docs/beginners-guide.md"))
        assertEquals(null, sentEtag)

        // Verify file written to disk
        val cacheFile = File(docsDir, "beginners-guide.md")
        assertTrue(cacheFile.exists())
    }

    @Test
    fun cache_hit_withinMaxAge_doesNotHitNetwork() = runBlocking {
        val docsDir = tempFolder.newFolder("docs")
        var currentTime = 1000L
        var fetchCount = 0

        val fetcher = DocFetcher(
            docsDir = docsDir,
            clock = { currentTime },
            fetchFunction = { _, _ ->
                fetchCount++
                FetchResponse.Success("# Cached Content", "\"etag1\"")
            }
        )

        // First call populates cache
        fetcher.get("guide.md", maxAgeSec = 600)
        assertEquals(1, fetchCount)

        // Advance clock by 300 seconds (< 600)
        currentTime += 300
        val result = fetcher.get("guide.md", maxAgeSec = 600)
        assertTrue(result is DocResult.Success)
        assertEquals("# Cached Content", (result as DocResult.Success).text)
        assertFalse(result.stale)
        // No network call made
        assertEquals(1, fetchCount)
    }

    @Test
    fun etag_notModified_updatesFetchedAtAndReturnsCached() = runBlocking {
        val docsDir = tempFolder.newFolder("docs")
        var currentTime = 1000L
        var fetchCount = 0
        var sentEtag: String? = null

        val fetcher = DocFetcher(
            docsDir = docsDir,
            clock = { currentTime },
            fetchFunction = { _, ifNoneMatch ->
                fetchCount++
                sentEtag = ifNoneMatch
                if (ifNoneMatch == "\"etag1\"") {
                    FetchResponse.NotModified
                } else {
                    FetchResponse.Success("# Original Content", "\"etag1\"")
                }
            }
        )

        // Initial fetch
        fetcher.get("guide.md", maxAgeSec = 100)
        assertEquals(1, fetchCount)

        // Expire cache
        currentTime += 200
        val result = fetcher.get("guide.md", maxAgeSec = 100)
        assertEquals(2, fetchCount)
        assertEquals("\"etag1\"", sentEtag)
        assertTrue(result is DocResult.Success)
        val success = result as DocResult.Success
        assertFalse(success.stale)
        assertEquals("# Original Content", success.text)
    }

    @Test
    fun offline_stale_fallback_whenCachedCopyExists() = runBlocking {
        val docsDir = tempFolder.newFolder("docs")
        var currentTime = 1000L
        var shouldFail = false

        val fetcher = DocFetcher(
            docsDir = docsDir,
            clock = { currentTime },
            fetchFunction = { _, _ ->
                if (shouldFail) {
                    throw IOException("No internet connection")
                } else {
                    FetchResponse.Success("# Content Before Going Offline", "\"etag-init\"")
                }
            }
        )

        // Initial fetch works
        fetcher.get("offline-test.md", maxAgeSec = 100)

        // Expire and simulate going offline
        currentTime += 500
        shouldFail = true

        val result = fetcher.get("offline-test.md", maxAgeSec = 100)
        assertTrue(result is DocResult.Success)
        val success = result as DocResult.Success
        assertTrue("Expected stale = true when offline", success.stale)
        assertEquals("# Content Before Going Offline", success.text)
    }

    @Test
    fun offline_error_whenNoCacheExists() = runBlocking {
        val docsDir = tempFolder.newFolder("docs")

        val fetcher = DocFetcher(
            docsDir = docsDir,
            fetchFunction = { _, _ ->
                throw IOException("No internet connection")
            }
        )

        val result = fetcher.get("non-existent.md")
        assertTrue(result is DocResult.Error)
        val error = result as DocResult.Error
        assertTrue(error.message.contains("No internet connection") || error.message.contains("Failed to fetch"))
    }

    @Test
    fun fallbackUrl_triedWhenPrimaryFails() = runBlocking {
        val docsDir = tempFolder.newFolder("docs")
        val calls = mutableListOf<String>()

        val fetcher = DocFetcher(
            docsDir = docsDir,
            fetchFunction = { url, _ ->
                calls.add(url)
                if (url.contains("raw.githubusercontent.com")) {
                    throw IOException("GitHub raw is down")
                } else {
                    FetchResponse.Success("# Rescued by jsDelivr", "\"jsd-1\"")
                }
            }
        )

        val result = fetcher.get("fallback-test.md")
        assertTrue(result is DocResult.Success)
        val success = result as DocResult.Success
        assertEquals("# Rescued by jsDelivr", success.text)
        assertEquals(2, calls.size)
        assertTrue(calls[0].contains("raw.githubusercontent.com"))
        assertTrue(calls[1].contains("cdn.jsdelivr.net"))
    }
}
