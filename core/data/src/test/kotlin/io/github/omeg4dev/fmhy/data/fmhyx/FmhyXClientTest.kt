package io.github.omeg4dev.fmhy.data.fmhyx

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FmhyXClientTest {

    private lateinit var primary: MockWebServer
    private lateinit var mirror: MockWebServer
    private lateinit var client: FmhyXClient

    @Before
    fun setUp() {
        primary = MockWebServer()
        mirror = MockWebServer()
        primary.start()
        mirror.start()
        client = FmhyXClient(
            http = OkHttpClient(),
            baseUrls = listOf(primary.url("/").toString(), mirror.url("/").toString()),
        )
    }

    @After
    fun tearDown() {
        primary.close()
        mirror.close()
    }

    @Test
    fun invalidTokensFailLocallyWithoutRequestsAndWithoutLeakingToken() = runTest {
        val badTokens = listOf("", " ", "token with spaces", "tok\nen", "tok\r", "t".repeat(513), "tokün")
        for (bad in badTokens) {
            try {
                client.checkAccount(bad)
                fail("Expected Unauthorized for bad token")
            } catch (e: FmhyXException.Unauthorized) {
                assertFalse(e.message.orEmpty().contains(bad.ifEmpty { "<empty>" }))
                assertFalse(e.message.orEmpty().contains("Authorization"))
            }
        }
        assertEquals(0, primary.requestCount)
        assertEquals(0, mirror.requestCount)
    }

    @Test
    fun mirrorFallbackUsedOn5xxAndConnectFailureNotOn401Or429OrOther4xx() = runTest {
        // 5xx falls back to mirror
        primary.enqueue(MockResponse.Builder().code(503).body("down").build())
        mirror.enqueue(MockResponse.Builder().code(200).body("""{"created_at":1}""").build())
        client.checkAccount("valid_token")
        assertEquals(1, primary.requestCount)
        assertEquals(1, mirror.requestCount)

        // IOException / dropped socket falls back to mirror (use separate client without OkHttp's same-host socket retry, plus real closed-port connect failure)
        val noRetryClient = FmhyXClient(
            http = OkHttpClient.Builder().retryOnConnectionFailure(false).build(),
            baseUrls = listOf(primary.url("/").toString(), mirror.url("/").toString()),
        )
        primary.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        mirror.enqueue(MockResponse.Builder().code(200).body("""{"created_at":1}""").build())
        noRetryClient.checkAccount("valid_token")
        assertEquals(2, primary.requestCount)
        assertEquals(2, mirror.requestCount)

        // Real closed-port connect failure falls back to mirror with default OkHttpClient
        val deadServer = MockWebServer().apply { start() }
        val deadUrl = deadServer.url("/").toString()
        deadServer.close()
        val deadPrimaryClient = FmhyXClient(
            http = OkHttpClient(),
            baseUrls = listOf(deadUrl, mirror.url("/").toString()),
        )
        mirror.enqueue(MockResponse.Builder().code(200).body("""{"created_at":1}""").build())
        deadPrimaryClient.checkAccount("valid_token")
        assertEquals(3, mirror.requestCount)

        // 401 does NOT hit mirror
        primary.enqueue(MockResponse.Builder().code(401).body("unauth").build())
        try {
            client.checkAccount("secret_token_never_in_msg")
            fail("Expected Unauthorized")
        } catch (e: FmhyXException.Unauthorized) {
            assertFalse(e.message.orEmpty().contains("secret_token_never_in_msg"))
            assertFalse(e.message.orEmpty().contains("Authorization"))
        }
        assertEquals(3, primary.requestCount)
        assertEquals(3, mirror.requestCount)

        // 429 parses Retry-After and does NOT hit mirror
        primary.enqueue(MockResponse.Builder().code(429).setHeader("Retry-After", "42").build())
        try {
            client.checkAccount("valid_token")
            fail("Expected RateLimited")
        } catch (e: FmhyXException.RateLimited) {
            assertEquals(42L, e.retryAfterSeconds)
        }
        assertEquals(4, primary.requestCount)
        assertEquals(3, mirror.requestCount)

        // 429 with non-numeric Retry-After gives null
        primary.enqueue(MockResponse.Builder().code(429).setHeader("Retry-After", "invalid").build())
        try {
            client.checkAccount("valid_token")
            fail("Expected RateLimited")
        } catch (e: FmhyXException.RateLimited) {
            assertNull(e.retryAfterSeconds)
        }
        assertEquals(5, primary.requestCount)
        assertEquals(3, mirror.requestCount)

        // 400 does NOT hit mirror
        primary.enqueue(MockResponse.Builder().code(400).body("bad").build())
        try {
            client.checkAccount("valid_token")
            fail("Expected Server(400)")
        } catch (e: FmhyXException.Server) {
            assertEquals(400, e.code)
        }
        assertEquals(6, primary.requestCount)
        assertEquals(3, mirror.requestCount)
    }

    @Test
    fun redirectsAreNotFollowedAndNeverLeakAuthorization() = runTest {
        val evil = MockWebServer()
        evil.start()
        try {
            primary.enqueue(
                MockResponse.Builder()
                    .code(302)
                    .setHeader("Location", evil.url("/steal").toString())
                    .build()
            )
            try {
                client.checkAccount("my_secret_token")
                fail("Expected Server(302)")
            } catch (e: FmhyXException.Server) {
                assertEquals(302, e.code)
            }
            assertEquals(0, evil.requestCount)
            assertEquals(0, mirror.requestCount)
        } finally {
            evil.close()
        }
    }

    @Test
    fun deleteAccountTreats401OnMirrorRetryAsSuccessBut401OnPrimaryAsFailure() = runTest {
        // Primary 401 -> fails
        primary.enqueue(MockResponse.Builder().code(401).build())
        try {
            client.deleteAccount("valid_token")
            fail("Expected Unauthorized")
        } catch (_: FmhyXException.Unauthorized) {}
        assertEquals(0, mirror.requestCount)

        // Primary 500, mirror 401 -> treated as success (already deleted)
        primary.enqueue(MockResponse.Builder().code(500).build())
        mirror.enqueue(MockResponse.Builder().code(401).build())
        client.deleteAccount("valid_token")

        // Primary IO failure, mirror 401 -> treated as success
        primary.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        mirror.enqueue(MockResponse.Builder().code(401).build())
        client.deleteAccount("valid_token")
    }

    @Test
    fun offlineThrowsOfflineAnd413ThrowsLimitExceededBodySize() = runTest {
        primary.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        mirror.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        try {
            client.pull("valid_token")
            fail("Expected Offline")
        } catch (e: FmhyXException.Offline) {
            assertNotNull(e.cause)
        }

        primary.enqueue(MockResponse.Builder().code(413).build())
        try {
            client.push("valid_token", emptyList(), emptyList())
            fail("Expected LimitExceeded(BODY_SIZE)")
        } catch (e: FmhyXException.LimitExceeded) {
            assertEquals(SyncLimit.BODY_SIZE, e.limit)
        }
    }

    @Test
    fun pushBodyContainsOnlyNonEmptyFavoritesAndCollectionsNeverActivityOrDislikes() = runTest {
        val validResp = """{"favorites":[],"dislikes":[],"collections":[]}"""
        val fav = buildJsonObject { put("u", JsonPrimitive("https://a.example/")); put("at", JsonPrimitive(100L)) }
        val col = buildJsonObject {
            put("id", JsonPrimitive("c1"))
            put("name", JsonPrimitive("Col"))
            put("items", JsonArray(emptyList()))
            put("updated", JsonPrimitive(200L))
        }

        // Only favorites
        primary.enqueue(MockResponse.Builder().code(200).body(validResp).build())
        client.push("tok", favorites = listOf(fav), collections = emptyList())
        val req1 = primary.takeRequest()
        val obj1 = FmhyXClient.json.parseToJsonElement(req1.body!!.utf8()) as JsonObject
        assertEquals(setOf("favorites"), obj1.keys)
        assertEquals("Bearer tok", req1.headers["Authorization"])

        // Only collections
        primary.enqueue(MockResponse.Builder().code(200).body(validResp).build())
        client.push("tok", favorites = emptyList(), collections = listOf(col))
        val req2 = primary.takeRequest()
        val obj2 = FmhyXClient.json.parseToJsonElement(req2.body!!.utf8()) as JsonObject
        assertEquals(setOf("collections"), obj2.keys)

        // Both
        primary.enqueue(MockResponse.Builder().code(200).body(validResp).build())
        client.push("tok", favorites = listOf(fav), collections = listOf(col))
        val req3 = primary.takeRequest()
        val obj3 = FmhyXClient.json.parseToJsonElement(req3.body!!.utf8()) as JsonObject
        assertEquals(setOf("favorites", "collections"), obj3.keys)
    }

    @Test
    fun syncParsingValidatesRequiredFieldsDefaultsAndPreservesUnknownFields() = runTest {
        val goodJson = """
            {
              "favorites": [{"u": "https://a.example/", "unknown_f": 42}],
              "dislikes": [{"u": "https://bad.example/"}],
              "collections": [
                {"id": "c1", "name": "Live", "items": [{"u": "https://c.example/"}], "share": "sh1", "extra": true},
                {"id": "c2", "deleted": true}
              ]
            }
        """.trimIndent()
        primary.enqueue(MockResponse.Builder().code(200).body(goodJson).build())
        val parsed = client.pull("tok")
        assertEquals(1, parsed.favorites.size)
        assertEquals(JsonPrimitive(42), parsed.favorites[0]["unknown_f"])
        assertEquals(0L, parsed.favorites[0].longOrZero("at"))
        assertEquals(2, parsed.collections.size)
        assertEquals(JsonPrimitive(true), parsed.collections[0]["extra"])
        assertEquals(0L, parsed.collections[1].longOrZero("updated"))

        val malformedBodies = listOf(
            "",
            "<html>not json</html>",
            "{}",
            """{"favorites":[],"dislikes":[]}""",
            """{"favorites":[],"collections":[]}""",
            """{"dislikes":[],"collections":[]}""",
            """{"favorites":[{"at":1}],"dislikes":[],"collections":[]}""",
            """{"favorites":[{"u":123}],"dislikes":[],"collections":[]}""",
            """{"favorites":[{"u":"https://a.example/","at":"bad"}],"dislikes":[],"collections":[]}""",
            """{"favorites":[],"dislikes":[{"at":1}],"collections":[]}""",
            """{"favorites":[],"dislikes":[],"collections":[{"name":"NoId","items":[]}]}""",
            """{"favorites":[],"dislikes":[],"collections":[{"id":"c1","items":[]}]}""",
            """{"favorites":[],"dislikes":[],"collections":[{"id":"c1","name":"NoItems"}]}""",
            """{"favorites":[],"dislikes":[],"collections":[{"id":"c1","name":"BadItem","items":[{}]}]}""",
        )
        for (bad in malformedBodies) {
            primary.enqueue(MockResponse.Builder().code(200).body(bad).build())
            try {
                client.pull("tok")
                fail("Expected BadResponse for: $bad")
            } catch (_: FmhyXException.BadResponse) {
                // expected
            }
        }
        assertEquals(0, mirror.requestCount)
    }

    @Test
    fun cancellingCoroutineCancelsHttpCallAndRethrowsCancellationException() = runTest {
        val requestStarted = CountDownLatch(1)
        val releaseServer = CountDownLatch(1)
        primary.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                requestStarted.countDown()
                releaseServer.await(5, TimeUnit.SECONDS)
                return MockResponse.Builder().code(200).body("""{"created_at":1}""").build()
            }
        }

        val job = async(Dispatchers.IO) {
            client.checkAccount("valid_token")
        }
        assertTrue(requestStarted.await(5, TimeUnit.SECONDS))
        delay(50)
        val cancelledAt = System.nanoTime()
        job.cancel()
        job.join()
        val waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cancelledAt)
        releaseServer.countDown()
        assertTrue("cancel waited ${waitedMs}ms for the HTTP call", waitedMs < 2_000)

        try {
            job.await()
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // expected; must not be swallowed or wrapped as Offline
        }
    }
}
