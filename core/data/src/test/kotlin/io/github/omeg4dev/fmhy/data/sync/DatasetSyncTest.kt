package io.github.omeg4dev.fmhy.data.sync

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.DatasetStoreTest
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.search.SearchEngine
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
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

class DatasetSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(bytes) }
        return baos.toByteArray()
    }

    @Test
    fun test304NotModified() = runTest {
        val syncDir = tempFolder.newFolder("sync_dir")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 40L)

        // Write existing sync state with etag
        val stateFile = File(syncDir, "sync-state.json")
        stateFile.writeText("""{"etag":"etag-123","lastCheck":1000,"version":40}""")

        server.enqueue(MockResponse.Builder().code(304).build())

        var logged = ""
        val sync = DatasetSync(
            store = store,
            dir = syncDir,
            baseUrl = server.url("/").toString(),
            client = client,
            clock = { 2000L }, // 2000 - 1000 = 1000 > 900
            log = { logged = it },
            minIntervalSec = 900L,
        )

        val result = sync.sync()
        assertEquals(SyncResult.NotModified, result)
        assertEquals(1, server.requestCount)
        val recordedReq = server.takeRequest()
        assertEquals("etag-123", recordedReq.headers["If-None-Match"])
        assertEquals(40L, store.version())
        assertTrue(logged.contains("304 not modified"))

        store.close()
    }

    @Test
    fun testIntervalSkipDoesZeroRequests() = runTest {
        val syncDir = tempFolder.newFolder("sync_skip")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 40L)

        val stateFile = File(syncDir, "sync-state.json")
        stateFile.writeText("""{"etag":"etag-123","lastCheck":1000,"version":40}""")

        val sync = DatasetSync(
            store = store,
            dir = syncDir,
            baseUrl = server.url("/").toString(),
            client = client,
            clock = { 1200L }, // 1200 - 1000 = 200 < 900
            log = {},
            minIntervalSec = 900L,
        )

        val result = sync.sync(force = false)
        assertEquals(SyncResult.Skipped, result)
        assertEquals(0, server.requestCount)

        store.close()
    }

    @Test
    fun test2StepDeltaChainAndFtsUpdates() = runTest {
        val syncDir = tempFolder.newFolder("sync_chain")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 40L)

        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('video', 'Video', '', 'video.md', 1);")
            conn.execSQL("""
                INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at)
                VALUES (1, 'e_old', 'video', NULL, 1, 'Old Movie Site', 'https://old.com', NULL, 'Will be deleted', '', 'Video', 0, 0, 0, '[]', '[]', 1000, 1000, NULL);
            """.trimIndent())
        }

        // Verify old entry exists in FTS
        val searchEngine = SearchEngine(store)
        val initialSearch = searchEngine.search("Old Movie Site")
        assertEquals(1, initialSearch.total)

        // Delta 40 -> 41: deletes e_old, appends change
        val delta40_41 = """
        {
            "schema": 1,
            "from": 40,
            "to": 41,
            "upsert": {},
            "delete": {
                "entries": ["e_old"]
            },
            "changes_append": [
                {
                    "id": 101, "ts": 1500, "kind": "removed", "entry_id": "e_old", "section_id": "video",
                    "name": "Old Movie Site", "url": "https://old.com", "detail": "", "commit_sha": "c1"
                }
            ],
            "meta": {"version": "41"}
        }
        """.trimIndent()
        val delta40_41Bytes = delta40_41.toByteArray(Charsets.UTF_8)
        val delta40_41Sha = sha256(delta40_41Bytes)

        // Delta 41 -> 42: adds e_new, appends change
        val delta41_42 = """
        {
            "schema": 1,
            "from": 41,
            "to": 42,
            "upsert": {
                "entries": [
                    {
                        "num": 2, "id": "e_new", "section_id": "video", "heading_id": null, "ord": 2,
                        "name": "New Streaming Gem", "url": "https://newgem.com", "original_url": null,
                        "description": "Best stream site", "tags": "", "heading_path": "Video", "tier": 1,
                        "is_inline_more": 0, "is_base64": 0, "mirrors": "[]", "extra_links": "[]",
                        "first_seen": 2000, "last_changed": 2000, "starred_at": 2000
                    }
                ]
            },
            "delete": {},
            "changes_append": [
                {
                    "id": 102, "ts": 2000, "kind": "added", "entry_id": "e_new", "section_id": "video",
                    "name": "New Streaming Gem", "url": "https://newgem.com", "detail": "", "commit_sha": "c2"
                }
            ],
            "meta": {"version": "42"}
        }
        """.trimIndent()
        val delta41_42Bytes = delta41_42.toByteArray(Charsets.UTF_8)
        val delta41_42Sha = sha256(delta41_42Bytes)

        val manifest = """
        {
            "schema": 1,
            "version": 42,
            "created": 2000,
            "full": {"file": "full-42.sqlite.gz", "sha256": "fake", "size": 100},
            "deltas": [
                {"from": 41, "to": 42, "file": "delta-41-42.json", "sha256": "$delta41_42Sha"},
                {"from": 40, "to": 41, "file": "delta-40-41.json", "sha256": "$delta40_41Sha"}
            ]
        }
        """.trimIndent()

        // Enqueue: 1. manifest, 2. delta 40-41, 3. delta 41-42
        server.enqueue(MockResponse.Builder().code(200).body(manifest).build())
        server.enqueue(MockResponse.Builder().code(200).body(delta40_41).build())
        server.enqueue(MockResponse.Builder().code(200).body(delta41_42).build())

        val sync = DatasetSync(
            store = store,
            dir = syncDir,
            baseUrl = server.url("/").toString(),
            client = client,
            clock = { 3000L },
            log = {},
        )

        val result = sync.sync(force = true)
        assertTrue("Result should be Updated, was $result", result is SyncResult.Updated)
        val updated = result as SyncResult.Updated
        assertEquals(40L, updated.from)
        assertEquals(42L, updated.to)
        assertEquals(1, updated.added) // 1 added row (e_new)

        // Version advanced to 42
        assertEquals(42L, store.version())

        // Deleted entry is GONE from FTS
        val searchOld = searchEngine.search("Old Movie Site")
        assertEquals(0, searchOld.total)

        // Added entry is FOUND in FTS
        val searchNew = searchEngine.search("Streaming Gem")
        assertEquals(1, searchNew.total)
        assertEquals("e_new", searchNew.groups[0].hits[0].entry.id)

        store.close()
    }

    @Test
    fun testBadSha256FailsAndKeepsData() = runTest {
        val syncDir = tempFolder.newFolder("sync_bad_sha")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 40L)

        val manifest = """
        {
            "schema": 1,
            "version": 41,
            "created": 2000,
            "full": {"file": "full.gz", "sha256": "fake"},
            "deltas": [
                {"from": 40, "to": 41, "file": "delta-40-41.json", "sha256": "incorrect_hash"}
            ]
        }
        """.trimIndent()

        server.enqueue(MockResponse.Builder().code(200).body(manifest).build())
        server.enqueue(MockResponse.Builder().code(200).body("""{"schema":1,"from":40,"to":41}""").build())

        val sync = DatasetSync(
            store = store,
            dir = syncDir,
            baseUrl = server.url("/").toString(),
            client = client,
            clock = { 3000L },
            log = {},
        )

        val result = sync.sync(force = true)
        assertTrue(result is SyncResult.Failed)
        assertEquals(40L, store.version())

        store.close()
    }

    @Test
    fun testChainGapFallsBackToFullDownloadAndSwap() = runTest {
        val syncDir = tempFolder.newFolder("sync_gap")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 38L)

        // Create a new full database v42
        val fullSourceDb = tempFolder.newFile("full_v42.db")
        val fullStore = DatasetStoreTest.createSampleDb(fullSourceDb, version = 42L)
        fullStore.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('full_sec', 'Full Section', '', 'full.md', 1);")
        }
        fullStore.close()

        val fullBytes = fullSourceDb.readBytes()
        val fullGz = gzip(fullBytes)
        val fullSha = sha256(fullGz)

        // Manifest has gap: version 42, but deltas only go from 40 to 42 (local is 38)
        val manifest = """
        {
            "schema": 1,
            "version": 42,
            "created": 2000,
            "full": {"file": "full-42.sqlite.gz", "sha256": "$fullSha"},
            "deltas": [
                {"from": 41, "to": 42, "file": "delta-41-42.json", "sha256": "abc"},
                {"from": 40, "to": 41, "file": "delta-40-41.json", "sha256": "def"}
            ]
        }
        """.trimIndent()

        server.enqueue(MockResponse.Builder().code(200).body(manifest).build())
        // Serves the full gz
        val fullResp = MockResponse.Builder()
            .code(200)
            .body(okio.Buffer().write(fullGz))
            .build()
        server.enqueue(fullResp)

        val sync = DatasetSync(
            store = store,
            dir = syncDir,
            baseUrl = server.url("/").toString(),
            client = client,
            clock = { 3000L },
            log = {},
        )

        val result = sync.sync(force = true)
        assertTrue("Result should be Replaced, was $result", result is SyncResult.Replaced)
        val replaced = result as SyncResult.Replaced
        assertEquals(38L, replaced.from)
        assertEquals(42L, replaced.to)

        // Store is updated and usable
        assertEquals(42L, store.version())
        val sections = store.sections()
        assertEquals(1, sections.size)
        assertEquals("full_sec", sections[0].id)

        store.close()
    }

    @Test
    fun testCorruptGzipFailsAndLeavesOldDbIntact() = runTest {
        val syncDir = tempFolder.newFolder("sync_corrupt")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 38L)

        val corruptGz = "not a valid gzip stream".toByteArray(Charsets.UTF_8)
        val corruptSha = sha256(corruptGz)

        val manifest = """
        {
            "schema": 1,
            "version": 42,
            "created": 2000,
            "full": {"file": "full-42.sqlite.gz", "sha256": "$corruptSha"},
            "deltas": []
        }
        """.trimIndent()

        server.enqueue(MockResponse.Builder().code(200).body(manifest).build())
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(corruptGz)).build())

        val sync = DatasetSync(
            store = store,
            dir = syncDir,
            baseUrl = server.url("/").toString(),
            client = client,
            clock = { 3000L },
            log = {},
        )

        val result = sync.sync(force = true)
        assertTrue(result is SyncResult.Failed)
        assertEquals(38L, store.version())

        store.close()
    }

    @Test
    fun testSchema2ManifestReturnsFailedSchema() = runTest {
        val syncDir = tempFolder.newFolder("sync_schema2")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val manifest = """
        {
            "schema": 2,
            "version": 2,
            "created": 2000,
            "full": {"file": "full-2.sqlite.gz", "sha256": "abc"},
            "deltas": []
        }
        """.trimIndent()

        server.enqueue(MockResponse.Builder().code(200).body(manifest).build())

        val sync = DatasetSync(
            store = store,
            dir = syncDir,
            baseUrl = server.url("/").toString(),
            client = client,
            clock = { 3000L },
            log = {},
        )

        val result = sync.sync(force = true)
        assertTrue(result is SyncResult.Failed)
        assertEquals("schema", (result as SyncResult.Failed).reason)

        store.close()
    }

    @Test
    fun testDeltaUpsertExistingIdKeepsNumAndPrunes() = runTest {
        val syncDir = tempFolder.newFolder("sync_upsert_keep_num")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 40L)

        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('tools', 'Tools', '', 'tools.md', 1);")
            conn.execSQL("""
                INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at)
                VALUES (999, 'existing_entry', 'tools', NULL, 1, 'Old Name', 'https://old.com', NULL, 'Old Desc', '', 'Tools', 0, 0, 0, '[]', '[]', 1000, 1000, NULL);
            """.trimIndent())
            // Change row older than 1500
            conn.execSQL("INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (1, 1000, 'added', 'e1', 'tools', 'Old', 'http', '', 'c1');")
            // Change row newer than 1500
            conn.execSQL("INSERT INTO changes (id, ts, kind, entry_id, section_id, name, url, detail, commit_sha) VALUES (2, 2000, 'added', 'e2', 'tools', 'New', 'http', '', 'c2');")
        }

        // Delta specifies upsert with a DIFFERENT num (888), but same id 'existing_entry'
        val delta = """
        {
            "schema": 1,
            "from": 40,
            "to": 41,
            "upsert": {
                "entries": [
                    {
                        "num": 888, "id": "existing_entry", "section_id": "tools", "heading_id": null, "ord": 1,
                        "name": "Updated Name", "url": "https://updated.com", "original_url": null,
                        "description": "Updated Desc", "tags": "", "heading_path": "Tools", "tier": 1,
                        "is_inline_more": 0, "is_base64": 0, "mirrors": "[]", "extra_links": "[]",
                        "first_seen": 1000, "last_changed": 1500, "starred_at": 1500
                    }
                ]
            },
            "delete": {},
            "changes_append": [],
            "changes_prune_before": 1500,
            "meta": {"version": "41"}
        }
        """.trimIndent()
        val deltaBytes = delta.toByteArray(Charsets.UTF_8)
        val deltaSha = sha256(deltaBytes)

        val manifest = """
        {
            "schema": 1,
            "version": 41,
            "created": 1500,
            "full": {"file": "full.gz", "sha256": "fake"},
            "deltas": [
                {"from": 40, "to": 41, "file": "delta-40-41.json", "sha256": "$deltaSha"}
            ]
        }
        """.trimIndent()

        server.enqueue(MockResponse.Builder().code(200).body(manifest).build())
        server.enqueue(MockResponse.Builder().code(200).body(delta).build())

        val sync = DatasetSync(
            store = store,
            dir = syncDir,
            baseUrl = server.url("/").toString(),
            client = client,
            clock = { 2000L },
            log = {},
        )

        val result = sync.sync(force = true)
        assertTrue(result is SyncResult.Updated)

        val entry = store.entry("existing_entry")
        assertNotNull(entry)
        assertEquals("Updated Name", entry?.name)
        // CRUCIAL: num must remain 999 (NOT 888)!
        assertEquals(999, entry?.num)

        // Pruning verified: change 1 (ts=1000) was deleted, change 2 (ts=2000) was kept
        val remainingChanges = store.changes()
        assertEquals(1, remainingChanges.size)
        assertEquals(2L, remainingChanges[0].id)

        store.close()
    }
}
