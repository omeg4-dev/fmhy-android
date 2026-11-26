package io.github.omeg4dev.fmhy.net

import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.ProxyMode
import io.github.omeg4dev.fmhy.data.ProxyType
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ProxyOkHttpTest {

    private lateinit var originServer: MockWebServer
    private lateinit var proxyServer: MockWebServer

    @Before
    fun setUp() {
        originServer = MockWebServer()
        proxyServer = MockWebServer()
        originServer.start()
        proxyServer.start()
    }

    @After
    fun tearDown() {
        originServer.close()
        proxyServer.close()
    }

    private fun buildClient(pm: ProxyManager): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .proxySelector(buildProxySelector(pm))
        .addInterceptor(buildProxyAppInterceptor(pm))
        .addNetworkInterceptor(buildProxyNetworkInterceptor(pm))
        .build()

    private fun httpProxyPrefs(port: Int) = ProxyEndpoint.resolve(
        ProtectionPrefs(
            proxyMode = ProxyMode.CUSTOM,
            proxyType = ProxyType.HTTP,
            proxyHost = "127.0.0.1",
            proxyPort = port,
        )
    )!!

    @Test
    fun httpProxySetRoutesRequestThroughProxyAndNotDirectToOrigin() {
        val flow = MutableStateFlow(ProxyState(httpProxyPrefs(proxyServer.port)))
        val pm = ProxyManager(
            states = flow,
            webProxyController = object : WebProxyController {
                override val supported = true
                override fun apply(rule: String, onDone: () -> Unit) = onDone()
                override fun clear(onDone: () -> Unit) = onDone()
            },
            runOnMain = { it() },
            isMainThread = { false },
        )
        pm.start()
        try {
            proxyServer.enqueue(MockResponse.Builder().code(200).body("via-proxy").build())
            val client = buildClient(pm)
            val targetUrl = originServer.url("/hello").toString()
            val body = client.newCall(Request.Builder().url(targetUrl).build()).execute().use { resp ->
                assertEquals(200, resp.code)
                resp.body.string()
            }
            assertEquals("via-proxy", body)
            assertEquals(1, proxyServer.requestCount)
            assertEquals(0, originServer.requestCount)
        } finally {
            pm.close()
        }
    }

    @Test
    fun proxyDownThrowsProxyIOExceptionWithLabelAndOriginReceivesZeroRequests() {
        val unusedPort = ServerSocket(0).use { it.localPort }
        val endpoint = httpProxyPrefs(unusedPort)
        val flow = MutableStateFlow(ProxyState(endpoint))
        val pm = ProxyManager(
            states = flow,
            webProxyController = object : WebProxyController {
                override val supported = true
                override fun apply(rule: String, onDone: () -> Unit) = onDone()
                override fun clear(onDone: () -> Unit) = onDone()
            },
            runOnMain = { it() },
            isMainThread = { false },
        )
        pm.start()
        try {
            val client = buildClient(pm)
            val targetUrl = originServer.url("/secret").toString()
            try {
                client.newCall(Request.Builder().url(targetUrl).build()).execute().close()
                fail("Expected ProxyIOException")
            } catch (e: ProxyIOException) {
                assertTrue(
                    "Expected label '${endpoint.label}' in '${e.message}'",
                    e.message!!.contains(endpoint.label),
                )
            }
            assertEquals(0, originServer.requestCount)
        } finally {
            pm.close()
        }
    }

    @Test
    fun switchingOffToProxyDoesNotReusePooledDirectConnection() {
        val flow = MutableStateFlow(ProxyState(null))
        lateinit var client: OkHttpClient
        val pm = ProxyManager(
            states = flow,
            webProxyController = object : WebProxyController {
                override val supported = true
                override fun apply(rule: String, onDone: () -> Unit) = onDone()
                override fun clear(onDone: () -> Unit) = onDone()
            },
            onNetworkReset = {
                client.dispatcher.cancelAll()
                client.connectionPool.evictAll()
            },
            runOnMain = { it() },
            isMainThread = { false },
        )
        client = buildClient(pm)
        pm.start()
        try {
            originServer.enqueue(MockResponse.Builder().code(200).body("direct").build())
            val targetUrl = originServer.url("/test").toString()
            val firstBody = client.newCall(Request.Builder().url(targetUrl).build()).execute().use { it.body.string() }
            assertEquals("direct", firstBody)
            assertEquals(1, originServer.requestCount)
            assertEquals(0, proxyServer.requestCount)

            // Switch Off -> HTTP proxy
            val proxyEp = httpProxyPrefs(proxyServer.port)
            proxyServer.enqueue(MockResponse.Builder().code(200).body("proxied").build())
            flow.value = ProxyState(proxyEp)

            // Wait until ProxyManager publishes the new endpoint
            val deadline = System.currentTimeMillis() + 2000L
            while (pm.currentEndpoint() != proxyEp && System.currentTimeMillis() < deadline) {
                Thread.sleep(5)
            }
            assertEquals(proxyEp, pm.currentEndpoint())

            val secondBody = client.newCall(Request.Builder().url(targetUrl).build()).execute().use { it.body.string() }
            assertEquals("proxied", secondBody)
            assertEquals(1, originServer.requestCount)
            assertEquals(1, proxyServer.requestCount)
        } finally {
            pm.close()
        }
    }

    @Test
    fun networkInterceptorRejectsStalePooledConnectionEvenWithoutEvict() {
        val flow = MutableStateFlow(ProxyState(null))
        val pm = ProxyManager(
            states = flow,
            webProxyController = object : WebProxyController {
                override val supported = true
                override fun apply(rule: String, onDone: () -> Unit) = onDone()
                override fun clear(onDone: () -> Unit) = onDone()
            },
            // Intentionally do NOT evict connection pool here to test the network interceptor guard!
            onNetworkReset = {},
            runOnMain = { it() },
            isMainThread = { false },
        )
        val client = buildClient(pm).newBuilder().retryOnConnectionFailure(false).build()
        pm.start()
        try {
            originServer.enqueue(MockResponse.Builder().code(200).body("direct").build())
            originServer.enqueue(MockResponse.Builder().code(200).body("should-not-reach").build())
            val targetUrl = originServer.url("/pooled").toString()
            client.newCall(Request.Builder().url(targetUrl).build()).execute().use {
                assertEquals("direct", it.body.string())
            }
            assertEquals(1, originServer.requestCount)

            val proxyEp = httpProxyPrefs(proxyServer.port)
            flow.value = ProxyState(proxyEp)
            val deadline = System.currentTimeMillis() + 2000L
            while (pm.currentEndpoint() != proxyEp && System.currentTimeMillis() < deadline) {
                Thread.sleep(5)
            }
            proxyServer.enqueue(MockResponse.Builder().code(200).body("via-proxy").build())

            try {
                val resp = client.newCall(Request.Builder().url(targetUrl).build()).execute()
                // Either network interceptor blocks the reused pooled connection (throwing ProxyIOException)
                // OR OkHttp opens a fresh proxied route; either way originServer must NOT get a 2nd request!
                resp.use { assertEquals("via-proxy", it.body.string()) }
            } catch (_: ProxyIOException) {
                // Guard caught the pooled direct connection and closed it before sending!
            }
            assertEquals(1, originServer.requestCount)
        } finally {
            pm.close()
        }
    }

    @Test
    fun appInterceptorRaisesShortTimeoutsAndPreservesLongerTimeoutsAndInterruptedException() {
        val flow = MutableStateFlow(ProxyState(httpProxyPrefs(proxyServer.port)))
        val pm = ProxyManager(
            states = flow,
            webProxyController = object : WebProxyController {
                override val supported = true
                override fun apply(rule: String, onDone: () -> Unit) = onDone()
                override fun clear(onDone: () -> Unit) = onDone()
            },
            runOnMain = { it() },
            isMainThread = { false },
        )
        pm.start()
        try {
            val seenConnect = AtomicInteger(0)
            val seenRead = AtomicInteger(0)
            val base = buildClient(pm).newBuilder()
                .addInterceptor { chain ->
                    seenConnect.set(chain.connectTimeoutMillis())
                    seenRead.set(chain.readTimeoutMillis())
                    throw InterruptedIOException("timeout")
                }
                .build()

            try {
                base.newCall(Request.Builder().url(originServer.url("/").toString()).build()).execute()
                fail("Expected InterruptedIOException")
            } catch (e: InterruptedIOException) {
                assertTrue(e !is ProxyIOException)
            }
            assertTrue(seenConnect.get() >= 30_000)
            assertTrue(seenRead.get() >= 60_000)

            val longClient = base.newBuilder()
                .connectTimeout(45, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
            try {
                longClient.newCall(Request.Builder().url(originServer.url("/").toString()).build()).execute()
            } catch (_: InterruptedIOException) {}
            assertEquals(45_000, seenConnect.get())
            assertEquals(120_000, seenRead.get())
        } finally {
            pm.close()
        }
    }
}
