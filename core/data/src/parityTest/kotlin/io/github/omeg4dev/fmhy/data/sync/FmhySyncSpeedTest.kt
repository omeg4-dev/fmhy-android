package io.github.omeg4dev.fmhy.data.sync

import io.github.omeg4dev.fmhy.data.DatasetStoreTest
import java.io.File
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FmhySyncSpeedTest {

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

    @Test
    fun testFullFirstSyncSpeedUnder3Seconds() = runTest {
        val cacheInputsDir = File(".cache/parity/inputs")
        val cacheDir = File(".cache")
        assertTrue("cache inputs dir must exist", cacheInputsDir.exists())

        val constantsFile = File(cacheInputsDir, "constants.ts")
        val rentryFile = File(cacheInputsDir, "rentry-FMHYB64.html")
        val unsafeDomainsFile = File(cacheDir, "filterlist-domains.txt")
        val unsafeReasonsFile = File(cacheDir, "filterlists-reasons.json")

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val file = when (path) {
                    "/docs/.vitepress/transformer/constants.ts" -> constantsFile
                    "/rentry" -> rentryFile
                    "/unsafe-domains.txt" -> unsafeDomainsFile
                    "/unsafe-reasons.json" -> unsafeReasonsFile
                    else -> {
                        if (path.startsWith("/docs/")) {
                            val fname = path.removePrefix("/docs/")
                            File(cacheInputsDir, fname)
                        } else {
                            null
                        }
                    }
                }

                if (file == null || !file.exists()) {
                    return MockResponse.Builder().code(404).build()
                }

                return MockResponse.Builder()
                    .code(200)
                    .body(file.readText(Charsets.UTF_8))
                    .build()
            }
        }

        val syncDir = tempFolder.newFolder("speed_sync")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val baseUrl = server.url("/").toString().trimEnd('/')
        val sources = Sources(
            primary = "$baseUrl/",
            fallback = "$baseUrl/fallback/",
            rentry = "$baseUrl/rentry",
            unsafeDomains = "$baseUrl/unsafe-domains.txt",
            unsafeReasons = "$baseUrl/unsafe-reasons.json",
        )

        var loggedLine = ""
        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = sources,
            clock = { 1000L },
            log = { loggedLine = it },
        )

        val startNano = System.nanoTime()
        val result = sync.sync(force = true)
        val elapsedMs = (System.nanoTime() - startNano) / 1_000_000

        println("\n=== FMHY SYNC SPEED TEST ===")
        println("Elapsed time: $elapsedMs ms")
        println("Log output:   $loggedLine")

        assertTrue("Result should be Updated, was $result", result is SyncResult.Updated)
        val updated = result as SyncResult.Updated
        assertEquals("Total sections should be 23", 23, updated.changedSections)
        assertEquals("Failed sections should be empty", emptyList<String>(), updated.failedSections)
        assertEquals("Total entries should be 21718", 21718, updated.added)

        val totalInDb = store.entryCount()
        assertEquals(21718, totalInDb)

        println("Entries in DB: $totalInDb")
        println("Sync speed verification: ${elapsedMs}ms < 3000ms")

        assertTrue("Full first sync must complete in < 3 s (was ${elapsedMs}ms)", elapsedMs < 3000)

        store.close()
    }
}
