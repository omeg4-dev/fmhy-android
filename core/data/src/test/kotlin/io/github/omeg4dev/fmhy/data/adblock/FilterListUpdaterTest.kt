package io.github.omeg4dev.fmhy.data.adblock

import java.io.File
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilterListUpdaterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeListFetcher : ListFetcher {
        var outcome: (url: String, etag: String?) -> FetchOutcome = { _, _ ->
            FetchOutcome.Error("Not configured")
        }
        val fetchCalls = mutableListOf<Pair<String, String?>>()

        override fun fetch(url: String, etag: String?): FetchOutcome {
            fetchCalls.add(url to etag)
            return outcome(url, etag)
        }
    }

    @Test
    fun testUpdateWithContentAndCustomRules() = runBlocking {
        val dir = tempFolder.newFolder("adblock_test")
        val fetcher = FakeListFetcher()
        var currentTime = 1000000L
        val logs = mutableListOf<String>()

        val listId = "ublock-filters"
        val listContent = """
            ! Title: uBlock filters
            ! Expires: 2 days
            ||ad.example.com^
        """.trimIndent()

        fetcher.outcome = { _, _ ->
            FetchOutcome.Content(listContent, "etag-123")
        }

        val updater = FilterListUpdater(dir, fetcher, { currentTime }, { logs.add(it) })
        val engine = updater.update(
            enabled = setOf(listId),
            customRules = "||custom-ad.org^",
            force = false
        )

        assertEquals(1, fetcher.fetchCalls.size)
        val state = updater.states()[listId]
        assertNotNull(state)
        assertEquals("etag-123", state!!.etag)
        assertEquals(currentTime, state.fetchedAt)
        assertEquals(null, state.error)
        assertTrue(state.ruleLines >= 1)

        val listFile = File(dir, "lists/$listId.txt")
        assertTrue(listFile.exists())
        assertEquals(listContent, listFile.readText())

        // Test engine matches rules from list and custom rules
        val res1 = engine.match(FilterRequest("https://ad.example.com/banner.js", "https://news.com/", ResourceType.SCRIPT))
        assertTrue(res1 is MatchResult.Block)

        val res2 = engine.match(FilterRequest("https://custom-ad.org/banner.js", "https://news.com/", ResourceType.SCRIPT))
        assertTrue(res2 is MatchResult.Block)

        // Engine on disk
        val loadedEngine = updater.loadEngine()
        assertNotNull(loadedEngine)
        val res3 = loadedEngine!!.match(FilterRequest("https://ad.example.com/banner.js", "https://news.com/", ResourceType.SCRIPT))
        assertTrue(res3 is MatchResult.Block)
    }

    @Test
    fun testUpdateNotModifiedRefreshesExpiry() = runBlocking {
        val dir = tempFolder.newFolder("adblock_test_not_mod")
        val fetcher = FakeListFetcher()
        var currentTime = 1000L
        val logs = mutableListOf<String>()

        val listId = "ublock-filters"
        fetcher.outcome = { _, _ ->
            FetchOutcome.Content("! Expires: 12 hours\n||ads.org^\n", "etag-initial")
        }

        val updater = FilterListUpdater(dir, fetcher, { currentTime }, { logs.add(it) })
        updater.update(setOf(listId), customRules = "", force = false)

        val initialState = updater.states()[listId]!!
        val expectedInitialExpiry = 1000L + 12.hours.inWholeMilliseconds
        assertEquals(expectedInitialExpiry, initialState.expiresAt)

        // Advance time past expiry
        currentTime = expectedInitialExpiry + 5000L
        fetcher.outcome = { _, etag ->
            assertEquals("etag-initial", etag)
            FetchOutcome.NotModified
        }

        updater.update(setOf(listId), customRules = "", force = false)

        val refreshedState = updater.states()[listId]!!
        assertEquals(currentTime, refreshedState.fetchedAt)
        assertEquals(currentTime + 12.hours.inWholeMilliseconds, refreshedState.expiresAt)
        assertEquals(null, refreshedState.error)
        val file = File(dir, "lists/$listId.txt")
        assertTrue(file.exists())
    }

    @Test
    fun testUpdateErrorKeepsOldFile() = runBlocking {
        val dir = tempFolder.newFolder("adblock_test_error")
        val fetcher = FakeListFetcher()
        var currentTime = 1000L
        val logs = mutableListOf<String>()

        val listId = "ublock-filters"
        val originalContent = "||good-ad.com^\n"
        fetcher.outcome = { _, _ ->
            FetchOutcome.Content(originalContent, "etag-1")
        }

        val updater = FilterListUpdater(dir, fetcher, { currentTime }, { logs.add(it) })
        updater.update(setOf(listId), customRules = "", force = false)

        val listFile = File(dir, "lists/$listId.txt")
        assertTrue(listFile.exists())
        assertEquals(originalContent, listFile.readText())

        // Now fetcher fails with Error
        fetcher.outcome = { _, _ ->
            FetchOutcome.Error("Connection reset by peer")
        }

        val engine = updater.update(setOf(listId), customRules = "", force = true)

        // File MUST not be deleted
        assertTrue(listFile.exists())
        assertEquals(originalContent, listFile.readText())

        val state = updater.states()[listId]
        assertNotNull(state)
        assertEquals("Connection reset by peer", state!!.error)

        // Old rule is still blocked
        val res = engine.match(FilterRequest("https://good-ad.com/banner.js", "https://news.com/", ResourceType.SCRIPT))
        assertTrue(res is MatchResult.Block)
    }

    @Test
    fun testRebuildOnlyUsesLocalFiles() = runBlocking {
        val dir = tempFolder.newFolder("adblock_test_rebuild")
        val fetcher = FakeListFetcher()
        val updater = FilterListUpdater(dir, fetcher, { 1000L }, {})

        val listId = "ublock-filters"
        val listFile = File(dir, "lists/$listId.txt")
        listFile.parentFile.mkdirs()
        listFile.writeText("||offline-ad.net^\n")

        val engine = updater.rebuild(setOf(listId), customRules = "||custom-offline.net^")
        val res1 = engine.match(FilterRequest("https://offline-ad.net/a.js", "https://site.com/", ResourceType.SCRIPT))
        assertTrue(res1 is MatchResult.Block)

        val res2 = engine.match(FilterRequest("https://custom-offline.net/b.js", "https://site.com/", ResourceType.SCRIPT))
        assertTrue(res2 is MatchResult.Block)
    }

    @Test
    fun updateReportsStepsInOrder() = runBlocking {
        val dir = tempFolder.newFolder("adblock_steps")
        val fetcher = FakeListFetcher()
        fetcher.outcome = { _, _ -> FetchOutcome.Content("||example.com^\n", "etag-1") }
        val updater = FilterListUpdater(dir, fetcher, { 1000L }, {})
        val progressList = mutableListOf<FilterListProgress>()
        updater.update(
            enabled = linkedSetOf("ublock-filters", "ublock-badware"),
            customRules = "",
            force = false,
            onProgress = { progressList.add(it) }
        )
        val expected = listOf(
            FilterListProgress.Downloading(0, 2),
            FilterListProgress.Downloading(1, 2),
            FilterListProgress.Downloading(2, 2),
            FilterListProgress.Preparing,
            FilterListProgress.Saving
        )
        assertEquals(expected, progressList)
    }

    @Test
    fun updateWithNothingChangedSkipsPreparing() = runBlocking {
        val dir = tempFolder.newFolder("adblock_skips_prep")
        val fetcher = FakeListFetcher()
        fetcher.outcome = { _, _ -> FetchOutcome.Content("! Expires: 2 days\n||example.com^\n", "etag-1") }
        val updater = FilterListUpdater(dir, fetcher, { 1000L }, {})
        updater.update(
            enabled = linkedSetOf("ublock-filters", "ublock-badware"),
            customRules = "",
            force = false
        )
        val progressList = mutableListOf<FilterListProgress>()
        updater.update(
            enabled = linkedSetOf("ublock-filters", "ublock-badware"),
            customRules = "",
            force = false,
            onProgress = { progressList.add(it) }
        )
        assertEquals(FilterListProgress.Downloading(2, 2), progressList.last())
        assertTrue(progressList.none { it is FilterListProgress.Preparing })
        assertTrue(progressList.none { it is FilterListProgress.Saving })
    }

    @Test
    fun cancelDuringDownloadKeepsOldFiles() = runBlocking {
        val dir = tempFolder.newFolder("adblock_cancel")
        val fetcher = FakeListFetcher()
        fetcher.outcome = { _, _ -> FetchOutcome.Content("||old-rule.com^\n", "etag-old") }
        val updater = FilterListUpdater(dir, fetcher, { 1000L }, {})
        updater.update(
            enabled = linkedSetOf("ublock-filters", "ublock-badware"),
            customRules = "",
            force = false
        )
        val engineFile = File(dir, "engine.bin")
        val firstListFile = File(dir, "lists/ublock-filters.txt")
        assertTrue(engineFile.exists())
        assertTrue(firstListFile.exists())
        assertNotNull(updater.loadEngine())

        var job: Job? = null
        job = launch {
            updater.update(
                enabled = linkedSetOf("ublock-filters", "ublock-badware"),
                customRules = "",
                force = true,
                onProgress = { p ->
                    if (p == FilterListProgress.Downloading(1, 2)) {
                        job?.cancel()
                    }
                }
            )
        }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(engineFile.exists())
        assertTrue(firstListFile.exists())
        assertNotNull(updater.loadEngine())
    }
}
