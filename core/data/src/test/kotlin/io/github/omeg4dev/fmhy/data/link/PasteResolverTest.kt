package io.github.omeg4dev.fmhy.data.link

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class PasteResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var cacheFile: File

    private val base64SingleUrl = Base64.getEncoder().encodeToString("https://example.com/test".toByteArray())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        cacheFile = File(tempFolder.root, "paste-links.json")
        client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val orig = chain.request().url
                val newUrl = orig.newBuilder()
                    .scheme("http")
                    .host(server.hostName)
                    .port(server.port)
                    .build()
                chain.proceed(chain.request().newBuilder().url(newUrl).build())
            }
            .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun createResolver(
        delays: List<Long> = listOf(0L, 0L),
        timeoutMs: Long = 8_000L,
        file: File = cacheFile,
        c: OkHttpClient = client,
    ): PasteResolver = PasteResolver(
        client = c,
        cacheFile = file,
        retryDelaysMs = delays,
        attemptTimeoutMs = timeoutMs,
    )

    @Test
    fun case1_retryOn503ThenSuccess() = runTest {
        val resolver = createResolver()
        server.enqueue(MockResponse.Builder().code(503).body("Service Unavailable").build())
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/plain")
                .body(base64SingleUrl)
                .build()
        )

        val result = resolver.resolve("https://pastebin.com/hvyWqiqc")
        assertEquals(PasteResult.Links(listOf("https://example.com/test")), result)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun case2_three503GivesFailed() = runTest {
        val resolver = createResolver()
        repeat(3) {
            server.enqueue(MockResponse.Builder().code(503).body("Service Unavailable").build())
        }

        val result = resolver.resolve("https://pastebin.com/hvyWqiqc")
        assertEquals(PasteResult.Failed, result)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun case3_404GivesFailedImmediatelyWithoutRetry() = runTest {
        val resolver = createResolver()
        server.enqueue(MockResponse.Builder().code(404).body("Not Found").build())

        val result = resolver.resolve("https://pastebin.com/hvyWqiqc")
        assertEquals(PasteResult.Failed, result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun case4_htmlBlockPageThenPlainBase64Retried() = runTest {
        val resolver = createResolver()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .body("<html><body>Just a moment... Cloudflare challenge</body></html>")
                .build()
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/plain")
                .body(base64SingleUrl)
                .build()
        )

        val result = resolver.resolve("https://pastebin.com/hvyWqiqc")
        assertEquals(PasteResult.Links(listOf("https://example.com/test")), result)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun case5_plainTextWithoutLinksGivesNotHidden() = runTest {
        val resolver = createResolver()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/plain")
                .body("Hello world, no links here!")
                .build()
        )

        val result = resolver.resolve("https://pastebin.com/hvyWqiqc")
        assertEquals(PasteResult.NotHidden, result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun case6_linksCachedAndReadOffline() = runTest {
        val resolver = createResolver()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/plain")
                .body(base64SingleUrl)
                .build()
        )

        val result1 = resolver.resolve("https://pastebin.com/hvyWqiqc")
        assertEquals(PasteResult.Links(listOf("https://example.com/test")), result1)
        assertEquals(1, server.requestCount)

        server.close()

        val offlineResolver = PasteResolver(
            client = OkHttpClient(),
            cacheFile = cacheFile,
            retryDelaysMs = listOf(0L, 0L),
        )
        val result2 = offlineResolver.resolve("https://pastebin.com/hvyWqiqc")
        assertEquals(PasteResult.Links(listOf("https://example.com/test")), result2)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun case7_nonPastebinUrlGivesNotHiddenWithoutNetwork() = runTest {
        val resolver = createResolver()
        val result = resolver.resolve("https://rentry.co/IPTV-Playlists")
        assertEquals(PasteResult.NotHidden, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun case8_socketFailureRetriedThenSuccess() = runTest {
        val resolver = createResolver()
        server.enqueue(
            MockResponse.Builder()
                .onResponseStart(SocketEffect.ShutdownConnection)
                .build()
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/plain")
                .body(base64SingleUrl)
                .build()
        )

        val result = resolver.resolve("https://pastebin.com/hvyWqiqc")
        assertEquals(PasteResult.Links(listOf("https://example.com/test")), result)
        assertEquals(2, server.requestCount)
    }
}
