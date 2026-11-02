package io.github.omeg4dev.fmhy.work

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IpLookupTest {

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

    // 1. Parser tests
    @Test
    fun parser_validJsonWithExtraFields() {
        val json = """
            {
                "ip": "1.2.3.4",
                "country": "Germany",
                "city": "Cologne",
                "mullvad_exit_ip": false,
                "blacklisted": {"blacklisted": false},
                "organization": "Mullvad"
            }
        """.trimIndent()
        val parsed = IpInfoParser.parse(json)
        assertEquals(IpInfo(ip = "1.2.3.4", country = "Germany"), parsed)
    }

    @Test
    fun parser_missingCountryDefaultsToUnknown() {
        val json = """{"ip": "1.2.3.4"}"""
        val parsed = IpInfoParser.parse(json)
        assertEquals(IpInfo(ip = "1.2.3.4", country = "Unknown"), parsed)
    }

    @Test
    fun parser_malformedReturnsNull() {
        assertNull(IpInfoParser.parse("not json"))
        assertNull(IpInfoParser.parse("{bad json}"))
    }

    @Test
    fun parser_emptyObjectReturnsNull() {
        assertNull(IpInfoParser.parse("{}"))
    }

    @Test
    fun parser_blankOrMissingIpReturnsNull() {
        assertNull(IpInfoParser.parse("""{"ip": "   ", "country": "Germany"}"""))
        assertNull(IpInfoParser.parse("""{"ip": "", "country": "Germany"}"""))
        assertNull(IpInfoParser.parse("""{"country": "Germany"}"""))
    }

    // 2. Repository against MockWebServer
    @Test
    fun repository_success200ReturnsValue() = runTest {
        val url = server.url("/json").toString()
        val lookup = IpLookup(client, url)
        val networkMonitor = NetworkMonitor(context = null)
        val vpnMonitor = VpnMonitor(context = null)
        val repo = IpInfoRepository(lookup, networkMonitor, vpnMonitor)

        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"ip":"1.2.3.4","country":"Germany"}""")
                .build()
        )

        val result = repo.get(enabled = true)
        assertTrue(result is IpResult.Value)
        assertEquals(IpInfo("1.2.3.4", "Germany"), (result as IpResult.Value).info)
    }

    @Test
    fun repository_http500ReturnsUnavailable() = runTest {
        val url = server.url("/json").toString()
        val lookup = IpLookup(client, url)
        val networkMonitor = NetworkMonitor(context = null)
        val vpnMonitor = VpnMonitor(context = null)
        val repo = IpInfoRepository(lookup, networkMonitor, vpnMonitor)

        server.enqueue(
            MockResponse.Builder()
                .code(500)
                .body("Internal Server Error")
                .build()
        )

        val result = repo.get(enabled = true)
        assertEquals(IpResult.Unavailable, result)
    }

    @Test
    fun repository_malformedBodyReturnsUnavailable() = runTest {
        val url = server.url("/json").toString()
        val lookup = IpLookup(client, url)
        val networkMonitor = NetworkMonitor(context = null)
        val vpnMonitor = VpnMonitor(context = null)
        val repo = IpInfoRepository(lookup, networkMonitor, vpnMonitor)

        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("invalid-non-json-content")
                .build()
        )

        val result = repo.get(enabled = true)
        assertEquals(IpResult.Unavailable, result)
    }

    @Test
    fun repository_serverShutDownReturnsUnavailable() = runTest {
        val url = server.url("/json").toString()
        val lookup = IpLookup(client, url)
        val networkMonitor = NetworkMonitor(context = null)
        val vpnMonitor = VpnMonitor(context = null)
        val repo = IpInfoRepository(lookup, networkMonitor, vpnMonitor)

        server.close()

        val result = repo.get(enabled = true)
        assertEquals(IpResult.Unavailable, result)
    }

    // 3. Cache tests
    @Test
    fun repository_cacheWithinTtlAndExpiryAndFailureNotCached() = runTest {
        val url = server.url("/json").toString()
        val lookup = IpLookup(client, url)
        val networkMonitor = NetworkMonitor(context = null)
        val vpnMonitor = VpnMonitor(context = null)

        var currentTime = 1_000_000L
        val ttlMs = 5 * 60_000L
        val repo = IpInfoRepository(
            lookup = lookup,
            networkMonitor = networkMonitor,
            vpnMonitor = vpnMonitor,
            clock = { currentTime },
            ttlMs = ttlMs,
        )

        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"ip":"1.2.3.4","country":"Germany"}""")
                .build()
        )

        // First call fetches from server
        val res1 = repo.get(enabled = true)
        assertEquals(IpResult.Value(IpInfo("1.2.3.4", "Germany")), res1)
        assertEquals(1, server.requestCount)

        // Advance time within TTL (e.g. 2 minutes)
        currentTime += 2 * 60_000L
        val res2 = repo.get(enabled = true)
        assertEquals(IpResult.Value(IpInfo("1.2.3.4", "Germany")), res2)
        assertEquals(1, server.requestCount)

        // Advance time past 5 minutes (e.g. 4 more minutes, total +6 min)
        currentTime += 4 * 60_000L
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"ip":"5.6.7.8","country":"France"}""")
                .build()
        )
        val res3 = repo.get(enabled = true)
        assertEquals(IpResult.Value(IpInfo("5.6.7.8", "France")), res3)
        assertEquals(2, server.requestCount)

        // Test failure is not cached (next call retries)
        currentTime += 6 * 60_000L // expire cache
        server.enqueue(
            MockResponse.Builder()
                .code(500)
                .body("Internal Server Error")
                .build()
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"ip":"9.10.11.12","country":"Spain"}""")
                .build()
        )

        val failResult = repo.get(enabled = true)
        assertEquals(IpResult.Unavailable, failResult)
        assertEquals(3, server.requestCount)

        val retryResult = repo.get(enabled = true)
        assertEquals(IpResult.Value(IpInfo("9.10.11.12", "Spain")), retryResult)
        assertEquals(4, server.requestCount)
    }

    // 4. Cache dropped on change
    @Test
    fun repository_cacheDroppedOnStateChange() = runTest {
        val url = server.url("/json").toString()
        val lookup = IpLookup(client, url)
        val networkMonitor = NetworkMonitor(context = null, initialKind = NetworkKind.NONE)
        val vpnMonitor = VpnMonitor(context = null, initial = VpnState.INACTIVE)

        var currentTime = 10_000L
        val repo = IpInfoRepository(
            lookup = lookup,
            networkMonitor = networkMonitor,
            vpnMonitor = vpnMonitor,
            clock = { currentTime },
        )

        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"ip":"1.2.3.4","country":"Germany"}""")
                .build()
        )

        val res1 = repo.get(enabled = true)
        assertEquals(IpResult.Value(IpInfo("1.2.3.4", "Germany")), res1)
        assertEquals(1, server.requestCount)

        // Change VPN state -> cache should drop and fetch anew
        vpnMonitor.setStateForTesting(VpnState.ACTIVE)
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"ip":"2.3.4.5","country":"Sweden"}""")
                .build()
        )

        val res2 = repo.get(enabled = true)
        assertEquals(IpResult.Value(IpInfo("2.3.4.5", "Sweden")), res2)
        assertEquals(2, server.requestCount)

        // Change Network kind -> cache should drop and fetch anew
        networkMonitor.setKindForTesting(NetworkKind.WIFI)
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"ip":"3.4.5.6","country":"Netherlands"}""")
                .build()
        )

        val res3 = repo.get(enabled = true)
        assertEquals(IpResult.Value(IpInfo("3.4.5.6", "Netherlands")), res3)
        assertEquals(3, server.requestCount)
    }

    // 5. get(enabled = false) -> Disabled and server.requestCount == 0, also when cached value exists
    @Test
    fun repository_getDisabledMakesNoRequests() = runTest {
        val url = server.url("/json").toString()
        val lookup = IpLookup(client, url)
        val networkMonitor = NetworkMonitor(context = null)
        val vpnMonitor = VpnMonitor(context = null)
        val repo = IpInfoRepository(lookup, networkMonitor, vpnMonitor)

        // Initial call with enabled = false
        val initialDisabled = repo.get(enabled = false)
        assertEquals(IpResult.Disabled, initialDisabled)
        assertEquals(0, server.requestCount)

        // Call with enabled = true -> makes request and caches
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"ip":"1.2.3.4","country":"Germany"}""")
                .build()
        )
        val enabledResult = repo.get(enabled = true)
        assertEquals(IpResult.Value(IpInfo("1.2.3.4", "Germany")), enabledResult)
        assertEquals(1, server.requestCount)

        // Call with enabled = false when cached value exists -> returns Disabled without server request
        val subsequentDisabled = repo.get(enabled = false)
        assertEquals(IpResult.Disabled, subsequentDisabled)
        assertEquals(1, server.requestCount)
    }
}
