package io.github.omeg4dev.fmhy.data.sync

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DATASET_SCHEMA
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.DatasetStoreTest
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FmhySyncCriticalNotesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

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

    private fun findFixtureDir(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "schema/fixtures/critical-notes").exists()) {
            val parent = dir.parentFile ?: error("Could not find schema/fixtures/critical-notes")
            dir = parent
        }
        return File(dir, "schema/fixtures/critical-notes")
    }

    private fun createSources(server: MockWebServer): Sources {
        val baseUrl = server.url("/").toString().trimEnd('/')
        return Sources(
            primary = "$baseUrl/primary/",
            fallback = "$baseUrl/fallback/",
            rentry = "$baseUrl/rentry",
            unsafeDomains = "$baseUrl/unsafe-domains.txt",
            unsafeReasons = "$baseUrl/unsafe-reasons.json",
        )
    }

    private val sampleHeadersJson = """
        {
          "audio.md": {
            "title": "Audio",
            "description": "Audio streaming and tools"
          }
        }
    """.trimIndent()

    private val sampleRentry = "<html><body>FMHY</body></html>"
    private val sampleUnsafeDomains = ""
    private val sampleUnsafeReasons = "{}"

    @Test
    fun add_change_remove_critical_note_via_sync() = runTest {
        val syncDir = tempFolder.newFolder("sync-add-change-remove")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val fixtureDir = findFixtureDir()
        val fixtureAudio = File(fixtureDir, "docs/audio.md").readText(Charsets.UTF_8)
        val fixtureBar = File(fixtureDir, "docs/.vitepress/notes/bar-note.md").readText(Charsets.UTF_8)
        val fixtureBaz = File(fixtureDir, "docs/.vitepress/notes/baz-critical.md").readText(Charsets.UTF_8)
        val fixtureFoo = File(fixtureDir, "docs/.vitepress/notes/foo-critical.md").readText(Charsets.UTF_8)
        val fixturePlain = File(fixtureDir, "docs/.vitepress/notes/plain-note.md").readText(Charsets.UTF_8)

        val files = mutableMapOf(
            "docs/.vitepress/transformer/headers.json" to sampleHeadersJson,
            "rentry" to sampleRentry,
            "filterlist-domains.txt" to sampleUnsafeDomains,
            "filterlists-reasons.json" to sampleUnsafeReasons,
            "docs/audio.md" to fixtureAudio,
            "docs/.vitepress/notes/foo-critical.md" to fixtureFoo,
            "docs/.vitepress/notes/bar-note.md" to "# Plain Bar\nNot critical yet.\n",
            "docs/.vitepress/notes/baz-critical.md" to "#### Baz Warning\nBaz warning.\n",
            "docs/.vitepress/notes/plain-note.md" to fixturePlain,
            "docs/.vitepress/notes/crlf-critical.md" to "# Crlf Warning\r\nline two\r\n",
        )

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val cleanPath = path.removePrefix("/primary/").removePrefix("/fallback/").removePrefix("/")
                val content = files[cleanPath] ?: return MockResponse.Builder().code(404).build()
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
            sources = createSources(server),
            clock = { clockTime },
            log = {},
        )

        // 1. Initial sync: new notes appear
        val res1 = sync.sync(force = true)
        assertTrue("First sync should be Updated", res1 is SyncResult.Updated)

        val foo = store.criticalNote("foo-critical")
        assertNotNull("foo-critical should exist", foo)
        assertEquals("Foo Warning", foo!!.title)
        assertEquals("73ec7e5dfce0", foo.hash)
        assertTrue(store.isCriticalNote("foo-critical"))

        val baz = store.criticalNote("baz-critical")
        assertNotNull("baz-critical should exist", baz)
        assertEquals("Baz Warning", baz!!.title)

        // bar-note is not critical initially
        assertNull("bar-note is not critical yet", store.criticalNote("bar-note"))

        // 2. Change: edited text changes row + hash
        files["docs/.vitepress/notes/foo-critical.md"] = "#### Foo Warning\nDo not use Foo at all! Forbidden.\n"
        clockTime += 500L
        val res2 = sync.sync(force = true)
        assertTrue("Second sync should be Updated", res2 is SyncResult.Updated)

        val fooEdited = store.criticalNote("foo-critical")
        assertNotNull(fooEdited)
        assertNotEquals("73ec7e5dfce0", fooEdited!!.hash)
        assertTrue(fooEdited.textMd.contains("Do not use Foo at all! Forbidden."))

        // 3. Remove: 404 removes it
        files.remove("docs/.vitepress/notes/foo-critical.md")
        clockTime += 500L
        val res3 = sync.sync(force = true)
        assertTrue("Third sync should be Updated", res3 is SyncResult.Updated)
        assertNull("foo-critical should be removed after 404", store.criticalNote("foo-critical"))
        assertFalse(store.isCriticalNote("foo-critical"))

        // 4. Flip a -critical note to critical: false
        files["docs/.vitepress/notes/baz-critical.md"] = "---\ncritical: false\n---\n# Baz\nOld warning withdrawn.\n"
        clockTime += 500L
        val res4 = sync.sync(force = true)
        assertTrue("Fourth sync should be Updated", res4 is SyncResult.Updated)
        assertNull("baz-critical should be removed after flip to false", store.criticalNote("baz-critical"))

        // 5. Flip a -note file to critical: true ONLY after 24h recheck window
        files["docs/.vitepress/notes/bar-note.md"] = fixtureBar // has critical: true front matter
        // 5a. Within 24h (e.g. 10 hours later): should NOT be re-checked
        clockTime += 10 * 3600L
        val res5a = sync.sync(force = true)
        assertNull("bar-note should not be checked before 24h", store.criticalNote("bar-note"))

        // 5b. After 24h window (e.g. 25 hours later): should be re-checked and flipped to true
        clockTime += 25 * 3600L
        val res5b = sync.sync(force = true)
        assertTrue("Sync after 24h should be Updated", res5b is SyncResult.Updated)
        val bar = store.criticalNote("bar-note")
        assertNotNull("bar-note should now be critical after 24h recheck", bar)
        assertEquals("Bar Danger", bar!!.title)
        assertEquals("0d09fd9ff28b", bar.hash)
    }

    @Test
    fun steady_state_sends_conditional_requests_only_for_critical_and_unknown() = runTest {
        val syncDir = tempFolder.newFolder("sync-steady-state")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val fixtureDir = findFixtureDir()
        val fixtureAudio = File(fixtureDir, "docs/audio.md").readText(Charsets.UTF_8) +
            "\n* [Unknown](https://unknown.example) - Unknown / [Warning](https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes/unknown-note.md)\n"

        val now = 100_000L

        // Populate DB with initial state matching snapshot install
        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('audio', 'Audio', 'Audio streaming', 'audio.md', 1);")
            conn.execSQL("INSERT INTO entries (num, id, section_id, ord, name, url, description, tags, heading_path, tier, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed) VALUES (1, 'e1', 'audio', 1, 'Entry 1', 'https://e1.com', '', '', '', 0, 0, 0, '[]', '[]', 1000, 1000);")
            conn.execSQL("INSERT INTO critical_notes (id, title, text_md, hash) VALUES ('foo-critical', 'Foo Warning', '#### Foo Warning\nDo not use Foo.', '73ec7e5dfce0');")
            conn.execSQL("INSERT INTO critical_notes (id, title, text_md, hash) VALUES ('bar-note', 'Bar Danger', '## Bar Danger\nBar steals data.', '0d09fd9ff28b');")
            conn.execSQL("INSERT INTO critical_notes (id, title, text_md, hash) VALUES ('crlf-critical', 'Crlf Warning', '# Crlf Warning\r\nline two', '4ce4dfa2880c');")
            conn.execSQL("INSERT INTO note_links (entry_id, note_id, section_id, line, ord) VALUES ('e1', 'foo-critical', 'audio', 3, 0);")
            conn.execSQL("INSERT INTO note_links (entry_id, note_id, section_id, line, ord) VALUES ('e2', 'bar-note', 'audio', 4, 0);")
            conn.execSQL("INSERT INTO note_links (entry_id, note_id, section_id, line, ord) VALUES ('e3', 'baz-critical', 'audio', 5, 0);")
            conn.execSQL("INSERT INTO note_links (entry_id, note_id, section_id, line, ord) VALUES ('e4', 'plain-note', 'audio', 6, 0);")
            conn.execSQL("INSERT INTO note_links (entry_id, note_id, section_id, line, ord) VALUES ('e5', 'crlf-critical', 'audio', 7, 0);")
            conn.execSQL("INSERT INTO note_links (entry_id, note_id, section_id, line, ord) VALUES ('e6', 'unknown-note', 'audio', 8, 0);")
        }

        // Write sync-state.json mimicking a fresh bundled install
        val fileStates = mapOf(
            "docs/.vitepress/transformer/headers.json" to FileState(etag = "\"h1\"", sha256 = sha256(sampleHeadersJson)),
            "rentry" to FileState(etag = "\"r1\"", sha256 = sha256(sampleRentry)),
            "filterlist-domains.txt" to FileState(etag = "\"d1\"", sha256 = sha256(sampleUnsafeDomains)),
            "filterlists-reasons.json" to FileState(etag = "\"rs1\"", sha256 = sha256(sampleUnsafeReasons)),
            "docs/audio.md" to FileState(etag = "\"a1\"", sha256 = sha256(fixtureAudio)),
            "docs/.vitepress/notes/foo-critical.md" to FileState(etag = "\"f1\"", sha256 = "sha-foo", checkedAt = now, critical = true),
            "docs/.vitepress/notes/bar-note.md" to FileState(etag = "\"b1\"", sha256 = "sha-bar", checkedAt = now, critical = true),
            "docs/.vitepress/notes/baz-critical.md" to FileState(etag = "\"bz1\"", sha256 = "sha-baz", checkedAt = now, critical = false),
            "docs/.vitepress/notes/plain-note.md" to FileState(etag = "\"p1\"", sha256 = "sha-plain", checkedAt = now, critical = false),
            "docs/.vitepress/notes/crlf-critical.md" to FileState(etag = "\"c1\"", sha256 = "sha-crlf", checkedAt = now, critical = true),
            // unknown-note has NO state entry
        )
        val syncState = SyncState(
            lastCheck = now,
            lastSuccess = now,
            source = "bundled",
            files = fileStates,
            version = 1L,
        )
        val stateFile = File(syncDir, "sync-state.json")
        syncDir.mkdirs()
        stateFile.writeText(json.encodeToString(syncState))

        // Also write raw files for sections so 304 can read raw
        val rawDir = File(syncDir, "raw").apply { mkdirs() }
        File(rawDir, sanitizePath("docs/audio.md")).writeText(fixtureAudio)

        val recordedNoteRequests = CopyOnWriteArrayList<String>()
        val controlFiles = mapOf(
            "docs/.vitepress/transformer/headers.json" to sampleHeadersJson,
            "rentry" to sampleRentry,
            "filterlist-domains.txt" to sampleUnsafeDomains,
            "filterlists-reasons.json" to sampleUnsafeReasons,
            "docs/audio.md" to fixtureAudio,
        )

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val cleanPath = path.removePrefix("/primary/").removePrefix("/fallback/").removePrefix("/")
                if (cleanPath.startsWith("docs/.vitepress/notes/")) {
                    recordedNoteRequests.add(cleanPath)
                }
                val ifNoneMatch = request.headers["If-None-Match"]
                if (ifNoneMatch != null) {
                    return MockResponse.Builder().code(304).build()
                }
                val content = controlFiles[cleanPath]
                if (content != null) {
                    return MockResponse.Builder().code(200).body(content).build()
                }
                return MockResponse.Builder().code(404).build()
            }
        }

        // Time is 1 hour after install (now + 3600 < now + 86400)
        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(server),
            clock = { now + 3600L },
            log = {},
        )

        sync.sync(force = true)

        // Verified expected note requests:
        // - foo-critical.md (critical by name) -> checked
        // - baz-critical.md (critical by name) -> checked
        // - crlf-critical.md (critical by name) -> checked
        // - bar-note.md (in stored critical_notes) -> checked
        // - unknown-note.md (no state entry) -> checked
        // - plain-note.md (critical=false, checkedAt=now, within 24h) -> NOT checked!
        assertTrue("foo-critical must be requested", recordedNoteRequests.contains("docs/.vitepress/notes/foo-critical.md"))
        assertTrue("baz-critical must be requested", recordedNoteRequests.contains("docs/.vitepress/notes/baz-critical.md"))
        assertTrue("crlf-critical must be requested", recordedNoteRequests.contains("docs/.vitepress/notes/crlf-critical.md"))
        assertTrue("bar-note must be requested", recordedNoteRequests.contains("docs/.vitepress/notes/bar-note.md"))
        assertTrue("unknown-note must be requested", recordedNoteRequests.contains("docs/.vitepress/notes/unknown-note.md"))
        assertFalse("plain-note MUST NOT be requested in steady state", recordedNoteRequests.contains("docs/.vitepress/notes/plain-note.md"))
        assertEquals(5, recordedNoteRequests.size)
    }

    @Test
    fun error_keeps_existing_rows() = runTest {
        val syncDir = tempFolder.newFolder("sync-error-keeps-rows")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val fixtureDir = findFixtureDir()
        val fixtureAudio = File(fixtureDir, "docs/audio.md").readText(Charsets.UTF_8)
        val fixtureFoo = File(fixtureDir, "docs/.vitepress/notes/foo-critical.md").readText(Charsets.UTF_8)

        val files = mutableMapOf(
            "docs/.vitepress/transformer/headers.json" to sampleHeadersJson,
            "rentry" to sampleRentry,
            "filterlist-domains.txt" to sampleUnsafeDomains,
            "filterlists-reasons.json" to sampleUnsafeReasons,
            "docs/audio.md" to fixtureAudio,
            "docs/.vitepress/notes/foo-critical.md" to fixtureFoo,
        )

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val cleanPath = path.removePrefix("/primary/").removePrefix("/fallback/").removePrefix("/")
                val content = files[cleanPath] ?: return MockResponse.Builder().code(404).build()
                return MockResponse.Builder().code(200).body(content).build()
            }
        }

        var clockTime = 1000L
        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(server),
            clock = { clockTime },
            log = {},
        )

        // 1. First sync succeeds and saves foo-critical
        sync.sync(force = true)
        val foo = store.criticalNote("foo-critical")
        assertNotNull("foo-critical should exist initially", foo)

        // 2. Both hosts fail on note request: 429 on primary, 500 on fallback
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                if (path.contains("foo-critical.md")) {
                    return if (path.startsWith("/primary/")) {
                        MockResponse.Builder().code(429).build()
                    } else {
                        MockResponse.Builder().code(500).build()
                    }
                }
                val cleanPath = path.removePrefix("/primary/").removePrefix("/fallback/").removePrefix("/")
                val content = files[cleanPath] ?: return MockResponse.Builder().code(404).build()
                return MockResponse.Builder().code(200).body(content).build()
            }
        }

        clockTime += 500L
        val res2 = sync.sync(force = true)
        // Sync should NOT fail with SyncResult.Failed due to note errors (fail-soft)
        assertFalse("Note errors must fail-soft and not fail the entire sync", res2 is SyncResult.Failed)

        // Existing row must be kept
        val fooAfterError = store.criticalNote("foo-critical")
        assertNotNull("foo-critical row must be kept intact after network errors", fooAfterError)
        assertEquals(foo!!.hash, fooAfterError!!.hash)

        // State for foo-critical must be preserved in sync-state.json
        val state = sync.loadState()
        assertTrue("foo-critical file state must be preserved", state.files.containsKey("docs/.vitepress/notes/foo-critical.md"))
    }

    @Test
    fun section_without_notes_makes_no_note_requests() = runTest {
        val syncDir = tempFolder.newFolder("sync-no-notes")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val sectionWithoutNotes = """
            # Video Streaming
            * [Video 1](https://video1.example) - Free video
            * [Video 2](https://video2.example) - Movie stream
        """.trimIndent()

        val files = mapOf(
            "docs/.vitepress/transformer/headers.json" to sampleHeadersJson,
            "rentry" to sampleRentry,
            "filterlist-domains.txt" to sampleUnsafeDomains,
            "filterlists-reasons.json" to sampleUnsafeReasons,
            "docs/audio.md" to sectionWithoutNotes,
        )

        val recordedNoteRequests = CopyOnWriteArrayList<String>()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val cleanPath = path.removePrefix("/primary/").removePrefix("/fallback/").removePrefix("/")
                if (cleanPath.startsWith("docs/.vitepress/notes/")) {
                    recordedNoteRequests.add(cleanPath)
                }
                val content = files[cleanPath] ?: return MockResponse.Builder().code(404).build()
                return MockResponse.Builder().code(200).body(content).build()
            }
        }

        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(server),
            clock = { 1000L },
            log = {},
        )

        val res = sync.sync(force = true)
        assertTrue("Sync should succeed", res is SyncResult.Updated)
        assertEquals("No note requests should be made when section has no note links", 0, recordedNoteRequests.size)
        assertTrue("critical_notes table should be empty", store.criticalNotes().isEmpty())
    }

    @Test
    fun fdroid_first_sync_fetches_all_linked_notes() = runTest {
        val syncDir = tempFolder.newFolder("sync-fdroid-first")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val fixtureDir = findFixtureDir()
        val fixtureAudio = File(fixtureDir, "docs/audio.md").readText(Charsets.UTF_8)
        val fixtureBar = File(fixtureDir, "docs/.vitepress/notes/bar-note.md").readText(Charsets.UTF_8)
        val fixtureBaz = File(fixtureDir, "docs/.vitepress/notes/baz-critical.md").readText(Charsets.UTF_8)
        val fixtureFoo = File(fixtureDir, "docs/.vitepress/notes/foo-critical.md").readText(Charsets.UTF_8)
        val fixturePlain = File(fixtureDir, "docs/.vitepress/notes/plain-note.md").readText(Charsets.UTF_8)

        val files = mapOf(
            "docs/.vitepress/transformer/headers.json" to sampleHeadersJson,
            "rentry" to sampleRentry,
            "filterlist-domains.txt" to sampleUnsafeDomains,
            "filterlists-reasons.json" to sampleUnsafeReasons,
            "docs/audio.md" to fixtureAudio,
            "docs/.vitepress/notes/foo-critical.md" to fixtureFoo,
            "docs/.vitepress/notes/bar-note.md" to fixtureBar,
            "docs/.vitepress/notes/baz-critical.md" to fixtureBaz,
            "docs/.vitepress/notes/plain-note.md" to fixturePlain,
            "docs/.vitepress/notes/crlf-critical.md" to "# Crlf Warning\r\nline two\r\n",
        )

        val recordedNoteRequests = CopyOnWriteArrayList<String>()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val cleanPath = path.removePrefix("/primary/").removePrefix("/fallback/").removePrefix("/")
                if (cleanPath.startsWith("docs/.vitepress/notes/")) {
                    recordedNoteRequests.add(cleanPath)
                }
                val content = files[cleanPath] ?: return MockResponse.Builder().code(404).build()
                return MockResponse.Builder().code(200).body(content).build()
            }
        }

        // F-Droid: no initial state file
        assertFalse(File(syncDir, "sync-state.json").exists())

        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(server),
            clock = { 1000L },
            log = {},
        )

        val res = sync.sync(force = true)
        assertTrue("F-Droid first sync should be Updated", res is SyncResult.Updated)

        // Every linked note id fetched once
        val expectedNotes = setOf(
            "docs/.vitepress/notes/foo-critical.md",
            "docs/.vitepress/notes/bar-note.md",
            "docs/.vitepress/notes/baz-critical.md",
            "docs/.vitepress/notes/plain-note.md",
            "docs/.vitepress/notes/crlf-critical.md",
        )
        assertEquals("Every linked note must be fetched exactly once", expectedNotes, recordedNoteRequests.toSet())
        assertEquals("Total note requests should be 5", 5, recordedNoteRequests.size)

        // Verify critical notes in DB: foo-critical, bar-note, crlf-critical
        val stored = store.criticalNotes().associateBy { it.id }
        assertEquals(3, stored.size)
        assertTrue(stored.containsKey("foo-critical"))
        assertTrue(stored.containsKey("bar-note"))
        assertTrue(stored.containsKey("crlf-critical"))
        assertFalse(stored.containsKey("baz-critical"))
        assertFalse(stored.containsKey("plain-note"))

        // State has all 5 notes
        val state = sync.loadState()
        for (notePath in expectedNotes) {
            assertTrue("State must include $notePath", state.files.containsKey(notePath))
        }
    }

    @Test
    fun old_state_json_without_new_fields_decodes() {
        val oldJson = """
            {
              "lastCheck": 100,
              "lastSuccess": 100,
              "source": "primary",
              "files": {
                "docs/audio.md": {
                  "etag": "\"123\"",
                  "sha256": "abc"
                }
              },
              "version": 1
            }
        """.trimIndent()

        val state = json.decodeFromString<SyncState>(oldJson)
        assertEquals(100L, state.lastCheck)
        assertEquals(100L, state.lastSuccess)
        val fileState = state.files["docs/audio.md"]
        assertNotNull(fileState)
        assertEquals("\"123\"", fileState!!.etag)
        assertEquals("abc", fileState.sha256)
        assertEquals(0L, fileState.checkedAt)
        assertNull(fileState.critical)
    }

    @Test
    fun unreferenced_critical_note_is_garbage_collected() = runTest {
        val syncDir = tempFolder.newFolder("sync-gc")
        val dbFile = File(syncDir, "current.db")
        val store = DatasetStoreTest.createSampleDb(dbFile, version = 1L)

        val fixtureDir = findFixtureDir()
        val fixtureAudio = File(fixtureDir, "docs/audio.md").readText(Charsets.UTF_8)
        val fixtureFoo = File(fixtureDir, "docs/.vitepress/notes/foo-critical.md").readText(Charsets.UTF_8)

        // Setup DB with foo-critical (linked) AND orphan-critical (unreferenced)
        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('audio', 'Audio', 'Audio', 'audio.md', 1);")
            conn.execSQL("INSERT INTO critical_notes (id, title, text_md, hash) VALUES ('foo-critical', 'Foo Warning', '#### Foo Warning\nDo not use Foo.', '73ec7e5dfce0');")
            conn.execSQL("INSERT INTO critical_notes (id, title, text_md, hash) VALUES ('orphan-critical', 'Orphan', 'Orphan text', 'orphan123456');")
            conn.execSQL("INSERT INTO note_links (entry_id, note_id, section_id, line, ord) VALUES ('e1', 'foo-critical', 'audio', 3, 0);")
        }

        assertTrue("orphan-critical exists initially", store.isCriticalNote("orphan-critical"))

        val files = mapOf(
            "docs/.vitepress/transformer/headers.json" to sampleHeadersJson,
            "rentry" to sampleRentry,
            "filterlist-domains.txt" to sampleUnsafeDomains,
            "filterlists-reasons.json" to sampleUnsafeReasons,
            "docs/audio.md" to fixtureAudio,
            "docs/.vitepress/notes/foo-critical.md" to fixtureFoo,
            "docs/.vitepress/notes/bar-note.md" to "---\ncritical: false\n---\n# Bar",
            "docs/.vitepress/notes/baz-critical.md" to "---\ncritical: false\n---\n# Baz",
            "docs/.vitepress/notes/plain-note.md" to "# Plain",
            "docs/.vitepress/notes/crlf-critical.md" to "---\ncritical: false\n---\n# Crlf",
        )

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val cleanPath = path.removePrefix("/primary/").removePrefix("/fallback/").removePrefix("/")
                val content = files[cleanPath] ?: return MockResponse.Builder().code(404).build()
                return MockResponse.Builder().code(200).body(content).build()
            }
        }

        val sync = FmhySync(
            store = store,
            dir = syncDir,
            client = client,
            sources = createSources(server),
            clock = { 1000L },
            log = {},
        )

        sync.sync(force = true)

        // orphan-critical must have been garbage collected
        assertNull("orphan-critical must be garbage collected", store.criticalNote("orphan-critical"))
        assertFalse(store.isCriticalNote("orphan-critical"))

        // foo-critical is still linked and exists
        assertNotNull("foo-critical remains", store.criticalNote("foo-critical"))
    }
}
