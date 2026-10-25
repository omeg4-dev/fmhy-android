package io.github.omeg4dev.fmhy.ui.shell

import io.github.omeg4dev.fmhy.browser.adblock.AdblockStatus
import io.github.omeg4dev.fmhy.browser.adblock.FailedList
import io.github.omeg4dev.fmhy.data.adblock.FilterListProgress
import io.github.omeg4dev.fmhy.data.sync.SyncProgress
import io.github.omeg4dev.fmhy.data.sync.SyncResult
import io.github.omeg4dev.fmhy.di.SyncStatus
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatusNoticesTest {

    private class Rig(
        val testScope: TestScope,
        consumed: Boolean = false,
        adblock: Boolean = true,
        var wikiLastUpdated: Long? = 1_700_000_000_000L,
        var adblockLastUpdated: Long? = 1_700_000_000_000L,
    ) {
        val sync = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
        val adblockIn = MutableStateFlow(true to AdblockStatus())
        var consumed = consumed
        val cancelledKinds = mutableListOf<JobKind>()
        val retriedKinds = mutableListOf<JobKind>()

        val notices = StatusNotices(
            scope = testScope.backgroundScope,
            syncStatus = sync,
            adblockInputs = if (adblock) adblockIn else null,
            isSyncBannerConsumed = { this.consumed },
            markSyncBannerConsumed = { this.consumed = true },
            cancel = { cancelledKinds.add(it) },
            retry = { retriedKinds.add(it) },
            lastUpdated = { kind ->
                when (kind) {
                    JobKind.ADBLOCK -> adblockLastUpdated
                    JobKind.WIKI -> wikiLastUpdated
                }
            },
            clock = { testScope.testScheduler.currentTime },
        )
    }

    private fun TestScope.rig(
        consumed: Boolean = false,
        adblock: Boolean = true,
        wikiLastUpdated: Long? = 1_700_000_000_000L,
        adblockLastUpdated: Long? = 1_700_000_000_000L,
    ): Rig {
        val r = Rig(this, consumed, adblock, wikiLastUpdated, adblockLastUpdated)
        runCurrent()
        return r
    }

    @Test
    fun fastBackgroundSyncUpToDateLineStaysNullThroughout() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.sync.value = SyncStatus.Syncing(progress = null, userStarted = false)
        advanceTimeBy(500)
        runCurrent()
        assertNull(r.notices.line.value)

        r.sync.value = SyncStatus.Finished(SyncResult.UpToDate(100))
        runCurrent()
        assertNull(r.notices.line.value)

        advanceTimeBy(4000)
        runCurrent()
        assertNull(r.notices.line.value)
    }

    @Test
    fun slowSyncRunningAfter1000msWithCheckingForUpdates() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.sync.value = SyncStatus.Syncing(progress = SyncProgress.Checking, userStarted = false)
        advanceTimeBy(999)
        runCurrent()
        assertNull(r.notices.line.value)

        advanceTimeBy(1)
        runCurrent()
        val line = r.notices.line.value
        assertTrue(line is ProgressLineUiState.Running)
        assertEquals("Checking for updates", (line as ProgressLineUiState.Running).text)
    }

    @Test
    fun filesProgressShowsRatioAndPercent() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.sync.value = SyncStatus.Syncing(progress = SyncProgress.Files(3, 10, 1000L, false), userStarted = true)
        runCurrent()
        val line = r.notices.line.value
        assertTrue(line is ProgressLineUiState.Running)
        assertEquals("Updating the wiki (3 of 10 files) · 28%", (line as ProgressLineUiState.Running).text)
    }

    @Test
    fun userStartedVisibleAt0ms() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.sync.value = SyncStatus.Syncing(progress = null, userStarted = true)
        runCurrent()
        val line = r.notices.line.value
        assertNotNull(line)
        assertTrue(line is ProgressLineUiState.Running)
    }

    @Test
    fun doneAutoHidesAfter3000ms() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.sync.value = SyncStatus.Syncing(progress = null, userStarted = true)
        runCurrent()
        r.sync.value = SyncStatus.Finished(SyncResult.UpToDate(100))
        runCurrent()
        val line = r.notices.line.value
        assertTrue(line is ProgressLineUiState.Done)
        assertEquals("Wiki is up to date", (line as ProgressLineUiState.Done).text)

        advanceTimeBy(2999)
        runCurrent()
        assertNotNull(r.notices.line.value)

        advanceTimeBy(2)
        runCurrent()
        assertNull(r.notices.line.value)
    }

    @Test
    fun failedBackgroundWikiProblemNonStickyHidesAfter6000msWithDateText() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.wikiLastUpdated = Instant.parse("2026-10-12T10:00:00Z").toEpochMilli()
        r.sync.value = SyncStatus.Syncing(progress = null, userStarted = false)
        runCurrent()
        r.sync.value = SyncStatus.Finished(SyncResult.Failed("Network error", 100))
        runCurrent()
        val line = r.notices.line.value
        assertTrue(line is ProgressLineUiState.Problem)
        val prob = line as ProgressLineUiState.Problem
        assertTrue(prob.text.contains("Couldn't update the wiki. Still using the data from"))
        assertFalse(prob.sticky)

        advanceTimeBy(5999)
        runCurrent()
        assertNotNull(r.notices.line.value)

        advanceTimeBy(2)
        runCurrent()
        assertNull(r.notices.line.value)
    }

    @Test
    fun adblockRunWithFailedListsShowsProblemWithDetails() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        val listTime = Instant.parse("2026-10-12T10:00:00Z").toEpochMilli()
        val failed = listOf(FailedList("EasyList", listTime))
        r.adblockIn.value = true to AdblockStatus(updating = true, userStarted = true)
        runCurrent()
        r.adblockIn.value = true to AdblockStatus(updating = false, ready = true, failedLists = failed, totalLists = 5, userStarted = true)
        runCurrent()
        val line = r.notices.line.value
        assertTrue(line is ProgressLineUiState.Problem)
        val prob = line as ProgressLineUiState.Problem
        assertEquals("4 of 5 ad-block lists updated", prob.text)
        assertTrue(prob.details.any { it.contains("Not updated: EasyList") })
        assertEquals(setOf(JobKind.ADBLOCK), prob.retry)
    }

    @Test
    fun adblockCancelledShowsUpdateCancelled() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.adblockIn.value = true to AdblockStatus(updating = true, userStarted = true)
        runCurrent()
        r.adblockIn.value = true to AdblockStatus(updating = false, cancelled = true)
        runCurrent()
        val line = r.notices.line.value
        assertTrue(line is ProgressLineUiState.Done)
        assertEquals("Update cancelled", (line as ProgressLineUiState.Done).text)
    }

    @Test
    fun bothJobsRunningCombinedTextAndJobsHas2Rows() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.sync.value = SyncStatus.Syncing(progress = SyncProgress.Files(10, 40, 1000L, false), userStarted = true)
        r.adblockIn.value = true to AdblockStatus(updating = true, step = FilterListProgress.Downloading(1, 5), userStarted = true)
        runCurrent()
        val line = r.notices.line.value
        assertTrue(line is ProgressLineUiState.Running)
        val running = line as ProgressLineUiState.Running
        assertTrue(running.text.startsWith("Updating ad-block lists and wiki"))
        assertEquals(2, r.notices.jobs.value.size)
        assertEquals(2, running.jobs.size)
    }

    @Test
    fun stallAfter30sAndKeepWaitingClearsIt() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.sync.value = SyncStatus.Syncing(progress = SyncProgress.Checking, userStarted = true)
        runCurrent()
        assertFalse((r.notices.line.value as ProgressLineUiState.Running).stalled)

        advanceTimeBy(30_000)
        runCurrent()
        val stalledLine = r.notices.line.value as ProgressLineUiState.Running
        assertTrue(stalledLine.stalled)
        assertEquals("Taking longer than usual", stalledLine.text)

        r.notices.keepWaiting()
        runCurrent()
        val clearedLine = r.notices.line.value as ProgressLineUiState.Running
        assertFalse(clearedLine.stalled)
        assertEquals("Checking for updates", clearedLine.text)
    }

    @Test
    fun cancelCallsFakeForEveryRunningKind() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.sync.value = SyncStatus.Syncing(progress = null, userStarted = true)
        r.adblockIn.value = true to AdblockStatus(updating = true, userStarted = true)
        runCurrent()
        r.notices.cancel()
        assertEquals(listOf(JobKind.ADBLOCK, JobKind.WIKI), r.cancelledKinds.sorted())
    }

    @Test
    fun retryCallsFakeForProblemKinds() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.sync.value = SyncStatus.Syncing(progress = null, userStarted = true)
        runCurrent()
        r.sync.value = SyncStatus.Finished(SyncResult.Failed("network error", 100))
        runCurrent()
        assertTrue(r.notices.line.value is ProgressLineUiState.Problem)

        r.notices.retry()
        assertEquals(listOf(JobKind.WIKI), r.retriedKinds)
        assertNull(r.notices.line.value)
    }

    @Test
    fun settingOffAdblockIgnored() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.adblockIn.value = false to AdblockStatus(updating = true, userStarted = true)
        runCurrent()
        assertNull(r.notices.line.value)
        assertTrue(r.notices.jobs.value.isEmpty())
    }

    @Test
    fun consumedRule() = runTest(UnconfinedTestDispatcher()) {
        val r = rig(consumed = true)
        r.sync.value = SyncStatus.Syncing(progress = null, userStarted = true)
        runCurrent()
        r.sync.value = SyncStatus.Finished(SyncResult.UpToDate(100))
        runCurrent()
        assertNull(r.notices.line.value)
    }
}
