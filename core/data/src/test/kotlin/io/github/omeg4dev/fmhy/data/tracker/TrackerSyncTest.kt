package io.github.omeg4dev.fmhy.data.tracker

import io.github.omeg4dev.fmhy.data.DATASET_SCHEMA
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.DatasetStoreTest
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import java.io.File
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TrackerSyncTest {

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

    private fun loadSampleJson(): String {
        val stream = TrackerSyncTest::class.java.getResourceAsStream("/tracker/monthly_commits_sample.json")
            ?: error("Missing /tracker/monthly_commits_sample.json resource")
        return stream.bufferedReader().use { it.readText() }
    }

    private suspend fun setupTestDatabase(store: DatasetStore) {
        store.withConnection { conn ->
            // Insert relevant sections
            val sections = listOf(
                "video" to "video.md",
                "social-media-tools" to "social-media-tools.md",
                "linux-macos" to "linux-macos.md",
                "non-english" to "non-english.md",
                "reading" to "reading.md",
                "misc" to "misc.md",
                "educational" to "educational.md",
                "mobile" to "mobile.md",
                "audio" to "audio.md",
                "gaming" to "gaming.md",
                "image-tools" to "image-tools.md",
                "downloading" to "downloading.md"
            )
            val sqlSec = "INSERT INTO sections (id, title, description, file, ord) VALUES (?, ?, '', ?, ?)"
            conn.prepare(sqlSec).use { stmt ->
                sections.forEachIndexed { i, (id, file) ->
                    stmt.bindText(1, id)
                    stmt.bindText(2, id)
                    stmt.bindText(3, file)
                    stmt.bindLong(4, (i + 1).toLong())
                    stmt.step()
                    stmt.reset()
                }
            }

            // Insert matching entries for testing joins
            val sqlEntry = """
                INSERT INTO entries (
                    num, id, section_id, heading_id, ord, name, url, original_url,
                    description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
                    mirrors, extra_links, first_seen, last_changed, starred_at
                ) VALUES (?, ?, ?, NULL, ?, ?, ?, NULL, '', '', '', ?, 0, 0, 0, 0, '[]', '[]', 1000, 1000, NULL)
            """.trimIndent()
            conn.prepare(sqlEntry).use { stmt ->
                // Entry 1: AnimeAV1 in non-english
                stmt.bindLong(1, 1L)
                stmt.bindText(2, "entry-animeav1")
                stmt.bindText(3, "non-english")
                stmt.bindLong(4, 1L)
                stmt.bindText(5, "AnimeAV1")
                stmt.bindText(6, "https://animeav1.com/")
                stmt.bindLong(7, 0L)
                stmt.step()
                stmt.reset()

                // Entry 2: BG Bye in image-tools
                stmt.bindLong(1, 2L)
                stmt.bindText(2, "entry-bgbye")
                stmt.bindText(3, "image-tools")
                stmt.bindLong(4, 2L)
                stmt.bindText(5, "BG Bye")
                stmt.bindText(6, "https://bgbye.io/")
                stmt.bindLong(7, 1L)
                stmt.step()
                stmt.reset()

                // Entry 3: M-Zone in video
                stmt.bindLong(1, 3L)
                stmt.bindText(2, "entry-mzone")
                stmt.bindText(3, "video")
                stmt.bindLong(4, 3L)
                stmt.bindText(5, "M-Zone")
                stmt.bindText(6, "https://m-zone.cc/")
                stmt.bindLong(7, 0L)
                stmt.step()
                stmt.reset()

                // Entry 4: GreyBox in video
                stmt.bindLong(1, 4L)
                stmt.bindText(2, "entry-greybox")
                stmt.bindText(3, "video")
                stmt.bindLong(4, 4L)
                stmt.bindText(5, "GreyBox")
                stmt.bindText(6, "https://greyboxtv.vercel.app/")
                stmt.bindLong(7, 0L)
                stmt.step()
                stmt.reset()
            }
        }
    }

    @Test
    fun testParseSampleCommits() {
        val json = loadSampleJson()
        val dummyStore = DatasetStoreTest.createSampleDb(tempFolder.newFile("dummy.db"))
        val tracker = TrackerSync(dummyStore, tempFolder.newFolder(), client, clock = { 0L }, log = {})
        val commits = tracker.parse(json)

        assertEquals(15, commits.size)
        assertNotNull(commits.first().sha)
        assertTrue(commits.any { it.added.isNotEmpty() })
        assertTrue(commits.any { it.removed.isNotEmpty() })
    }

    @Test
    fun testSyncWritesRowsWithJoinsAndExtras() = runTest {
        val dbFile = tempFolder.newFile("test.db")
        val store = DatasetStoreTest.createSampleDb(dbFile)
        setupTestDatabase(store)

        val sampleJson = loadSampleJson()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("ETag", "\"tag-123\"")
                .body(sampleJson)
                .build()
        )

        var clockTime = 1000L
        val logs = mutableListOf<String>()
        val tracker = TrackerSync(
            store = store,
            dir = tempFolder.newFolder(),
            client = client,
            url = server.url("/monthly_commits.json").toString(),
            clock = { clockTime },
            log = { logs.add(it) }
        )

        val result = tracker.sync()
        assertTrue("Expected Updated result, got: $result", result is TrackerResult.Updated)
        val inserted = (result as TrackerResult.Updated).inserted
        assertTrue("Should have inserted rows: $inserted", inserted > 0)

        // Verify kinds written
        store.withConnection { conn ->
            val kinds = mutableSetOf<String>()
            conn.prepare("SELECT DISTINCT kind FROM changes").use { stmt ->
                while (stmt.step()) kinds.add(stmt.getText(0))
            }
            assertTrue("Expected 'added'", kinds.contains("added"))
            assertTrue("Expected 'edited'", kinds.contains("edited"))
            assertTrue("Expected 'removed'", kinds.contains("removed"))
            assertTrue("Expected 'starred'", kinds.contains("starred"))
            assertTrue("Expected 'unstarred'", kinds.contains("unstarred"))
            assertTrue("Expected 'url_changed'", kinds.contains("url_changed"))

            // Verify entry join for AnimeAV1
            conn.prepare("SELECT entry_id FROM changes WHERE url LIKE '%animeav1.com%'").use { stmt ->
                assertTrue(stmt.step())
                assertEquals("entry-animeav1", stmt.getText(0))
            }

            // Verify url_changed detail contains old URL for M-Zone
            conn.prepare("SELECT detail FROM changes WHERE kind = 'url_changed' AND url LIKE '%m-zone.cc%'").use { stmt ->
                assertTrue(stmt.step())
                assertEquals("https://m-zone.org/", stmt.getText(0))
            }

            // Verify unstarred for BG Bye
            conn.prepare("SELECT entry_id FROM changes WHERE kind = 'unstarred' AND url LIKE '%bgbye.io%'").use { stmt ->
                assertTrue(stmt.step())
                assertEquals("entry-bgbye", stmt.getText(0))
            }

            // Verify unknown source 'beginners-guide' was not inserted
            conn.prepare("SELECT count(*) FROM changes WHERE section_id = 'beginners-guide'").use { stmt ->
                assertTrue(stmt.step())
                assertEquals(0L, stmt.getLong(0))
            }
        }

        // Test deduplication on second run:
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("ETag", "\"tag-123\"")
                .body(sampleJson)
                .build()
        )
        clockTime += 5000L
        val secondResult = tracker.sync(force = true)
        assertTrue("Second run should insert 0 rows", secondResult is TrackerResult.Updated)
        assertEquals(0, (secondResult as TrackerResult.Updated).inserted)

        // Test minimum interval: without force, returns UpToDate
        val intervalResult = tracker.sync(force = false)
        assertEquals(TrackerResult.UpToDate, intervalResult)

        // Test 304 Not Modified
        server.enqueue(MockResponse.Builder().code(304).build())
        val notModResult = tracker.sync(force = true)
        assertEquals(TrackerResult.UpToDate, notModResult)
    }

    @Test
    fun testPureStarChangeProducesExactlyOneStarredRow() = runTest {
        val dbFile = tempFolder.newFile("test_pure_star.db")
        val store = DatasetStoreTest.createSampleDb(dbFile)
        setupTestDatabase(store)

        val tracker = TrackerSync(
            store = store,
            dir = tempFolder.newFolder(),
            client = client,
            clock = { 1000L },
            log = {},
        )

        val commit = TrackerCommit(
            sha = "sha-star",
            date = "2026-03-01T10:00:00Z",
            message = "Star Streamo",
            added = listOf(
                TrackerLink(
                    link = "* ⭐ **[Streamo](https://streamo.pro/)** - Movies / TV / Anime",
                    source = "video",
                    type = "updated",
                    relatedLink = "* [Streamo](https://streamo.pro/) - Movies / TV / Anime",
                )
            ),
        )

        val inserted = tracker.applyCommits(listOf(commit))
        assertEquals(1, inserted)

        store.withConnection { conn ->
            val rows = mutableListOf<Pair<String, String>>()
            conn.prepare("SELECT kind, detail FROM changes WHERE url = 'https://streamo.pro/'").use { stmt ->
                while (stmt.step()) {
                    rows.add(stmt.getText(0) to stmt.getText(1))
                }
            }
            assertEquals(1, rows.size)
            assertEquals("starred", rows[0].first)
            assertEquals("", rows[0].second)
        }
    }

    @Test
    fun testMoveProducesOneMovedRowAndDeletesEarlierAddedRemoved() = runTest {
        val dbFile = tempFolder.newFile("test_move.db")
        val store = DatasetStoreTest.createSampleDb(dbFile)
        setupTestDatabase(store)

        val tracker = TrackerSync(
            store = store,
            dir = tempFolder.newFolder(),
            client = client,
            clock = { 1000L },
            log = {},
        )

        val commit1 = TrackerCommit(
            sha = "sha-rem",
            date = "2026-03-01T10:00:00Z",
            message = "Remove from reading",
            removed = listOf(
                TrackerLink(
                    link = "* [BookFinder](https://bookfinder.example.com/) - Book search",
                    source = "reading",
                    type = "removed",
                )
            ),
        )

        val commit2 = TrackerCommit(
            sha = "sha-add",
            date = "2026-03-01T12:00:00Z",
            message = "Add to video",
            added = listOf(
                TrackerLink(
                    link = "* [BookFinder](https://bookfinder.example.com/) - Book search",
                    source = "video",
                    type = "new",
                )
            ),
        )

        // Earlier sync only saw commit1: inserts 'removed' row
        val ins1 = tracker.applyCommits(listOf(commit1))
        assertEquals(1, ins1)
        store.withConnection { conn ->
            conn.prepare("SELECT kind FROM changes WHERE url = 'https://bookfinder.example.com/'").use { stmt ->
                assertTrue(stmt.step())
                assertEquals("removed", stmt.getText(0))
            }
        }

        // Later sync sees both commit1 and commit2: recognizes move, deletes 'removed' and emits 'moved'
        tracker.applyCommits(listOf(commit1, commit2))

        store.withConnection { conn ->
            val rows = mutableListOf<Triple<String, String, String>>()
            conn.prepare("SELECT kind, section_id, detail FROM changes WHERE url = 'https://bookfinder.example.com/'").use { stmt ->
                while (stmt.step()) {
                    rows.add(Triple(stmt.getText(0), stmt.getText(1), stmt.getText(2)))
                }
            }
            assertEquals(1, rows.size)
            assertEquals("moved", rows[0].first)
            assertEquals("video", rows[0].second)
            assertEquals("reading", rows[0].third)
        }
    }

    @Test
    fun testSameNameNewAddressIsOneUrlChangedRow() = runTest {
        val dbFile = tempFolder.newFile("test_swap.db")
        val store = DatasetStoreTest.createSampleDb(dbFile)
        setupTestDatabase(store)
        val tracker = TrackerSync(store = store, dir = tempFolder.newFolder(), client = client, clock = { 1000L }, log = {})

        val commit = TrackerCommit(
            sha = "sha-swap",
            date = "2026-03-02T10:00:00Z",
            message = "swap link",
            added = listOf(TrackerLink(link = "* [CMVTV](https://new.example.com/) - Live TV", source = "video", type = "new")),
            removed = listOf(TrackerLink(link = "* [CMVTV](https://old.example.com/) - Live TV", source = "video", type = "removed")),
        )
        tracker.applyCommits(listOf(commit))

        val kinds = mutableListOf<Pair<String, String>>()
        store.withConnection { conn ->
            conn.prepare("SELECT kind, detail FROM changes WHERE commit_sha = 'sha-swap'").use { stmt ->
                while (stmt.step()) kinds.add(stmt.getText(0) to stmt.getText(1))
            }
        }
        assertEquals(listOf("url_changed" to "https://old.example.com/"), kinds)
    }

    @Test
    fun testRealMonthlyCommitsData() = runTest {
        val realJsonFile = sequenceOf(
            File(".cache/ref/tracker/monthly_commits.json"),
            File("../.cache/ref/tracker/monthly_commits.json"),
            File("../../.cache/ref/tracker/monthly_commits.json")
        ).firstOrNull { it.exists() } ?: return@runTest

        val snapshotFile = sequenceOf(
            File("app/src/main/assets/dataset/snapshot.gzbin"),
            File("../app/src/main/assets/dataset/snapshot.gzbin"),
            File("../../app/src/main/assets/dataset/snapshot.gzbin")
        ).firstOrNull { it.exists() } ?: return@runTest

        val dbFile = tempFolder.newFile("real_snapshot_test.db")
        java.util.zip.GZIPInputStream(snapshotFile.inputStream()).use { gzIn ->
            dbFile.outputStream().use { out ->
                gzIn.copyTo(out)
            }
        }

        val realStore = DatasetStore(dbFile)
        val tracker = TrackerSync(
            store = realStore,
            dir = tempFolder.newFolder(),
            client = client,
            clock = { 0L },
            log = { println(it) }
        )

        val commits = tracker.parse(realJsonFile.readText())
        val inserted = tracker.applyCommits(commits)

        realStore.withConnection { conn ->
            val kindsCount = mutableMapOf<String, Long>()
            conn.prepare("SELECT kind, count(*) FROM changes GROUP BY kind ORDER BY kind").use { stmt ->
                while (stmt.step()) {
                    kindsCount[stmt.getText(0)] = stmt.getLong(1)
                }
            }

            var totalAdded = 0L
            var joinedAdded = 0L
            conn.prepare("SELECT count(*), count(case when entry_id NOT LIKE 'tracker:%' then 1 end) FROM changes WHERE kind = 'added'").use { stmt ->
                if (stmt.step()) {
                    totalAdded = stmt.getLong(0)
                    joinedAdded = stmt.getLong(1)
                }
            }
            val joinRate = if (totalAdded > 0) (joinedAdded.toDouble() / totalAdded) * 100.0 else 0.0

            println("\n=== REAL-DATA CHECK ===")
            println("Total commits parsed: ${commits.size}")
            println("Total rows inserted: $inserted")
            println("Rows inserted per kind: $kindsCount")
            println("Added rows: total=$totalAdded, joined=$joinedAdded, rate=${"%.2f".format(joinRate)}%")
            println("Unknown sources: ${tracker.lastUnknownSources}")
            println("=======================\n")

            assertTrue("Expected > 70% joined, got $joinRate%", joinRate > 70.0)
        }
    }
}
