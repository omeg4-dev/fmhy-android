package io.github.omeg4dev.fmhy.ui.shell

import io.github.omeg4dev.fmhy.browser.adblock.FailedList
import io.github.omeg4dev.fmhy.data.adblock.FilterListProgress
import io.github.omeg4dev.fmhy.data.sync.SyncProgress
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobProgressTest {

    // ---- Step Table ----

    @Test
    fun adblockDownloadingSteps() {
        // Downloading(done, total)
        val s0 = adblockStep(FilterListProgress.Downloading(0, 5))
        assertEquals(0, s0.index)
        assertEquals("Downloading ad-block lists (1 of 5)", s0.label)
        assertEquals(0.0f, s0.fraction, 0.001f)
        assertTrue(s0.measured)

        val s4 = adblockStep(FilterListProgress.Downloading(4, 5))
        assertEquals(0, s4.index)
        assertEquals("Downloading ad-block lists (5 of 5)", s4.label)
        assertEquals(0.56f, s4.fraction, 0.001f) // 0.70 * 4 / 5
        assertTrue(s4.measured)

        val s5 = adblockStep(FilterListProgress.Downloading(5, 5))
        assertEquals(0, s5.index)
        assertEquals("Downloading ad-block lists (5 of 5)", s5.label)
        assertEquals(0.70f, s5.fraction, 0.001f)
        assertTrue(s5.measured)

        // total 0 -> treat ratio as 1, min(done+1, total) = 0
        val sZero = adblockStep(FilterListProgress.Downloading(0, 0))
        assertEquals(0, sZero.index)
        assertEquals("Downloading ad-block lists (0 of 0)", sZero.label)
        assertEquals(0.70f, sZero.fraction, 0.001f)
        assertTrue(sZero.measured)
    }

    @Test
    fun adblockPreparingAndSavingSteps() {
        val prep = adblockStep(FilterListProgress.Preparing)
        assertEquals(1, prep.index)
        assertEquals("Preparing the rules", prep.label)
        assertEquals(0.70f, prep.fraction, 0.001f)
        assertFalse(prep.measured)

        val save = adblockStep(FilterListProgress.Saving)
        assertEquals(2, save.index)
        assertEquals("Saving", save.label)
        assertEquals(0.90f, save.fraction, 0.001f)
        assertFalse(save.measured)
    }

    @Test
    fun wikiSteps() {
        val check = wikiStep(SyncProgress.Checking)
        assertEquals(0, check.index)
        assertEquals("Checking for updates", check.label)
        assertEquals(0.00f, check.fraction, 0.001f)
        assertFalse(check.measured)

        // Files: done=3, total=10 -> 0.10 + 0.60 * 3/10 = 0.28
        val filesNotFirst = wikiStep(SyncProgress.Files(done = 3, total = 10, bytes = 100_000L, firstDownload = false))
        assertEquals(1, filesNotFirst.index)
        assertEquals("Updating the wiki (3 of 10 files)", filesNotFirst.label)
        assertEquals(0.28f, filesNotFirst.fraction, 0.001f)
        assertTrue(filesNotFirst.measured)

        // Files with firstDownload = true -> extra bytes
        val filesFirst = wikiStep(SyncProgress.Files(done = 12, total = 40, bytes = 348_160L, firstDownload = true))
        assertEquals("Updating the wiki (12 of 40 files) · 340 KB", filesFirst.label)
        assertEquals(0.10f + 0.60f * (12f / 40f), filesFirst.fraction, 0.001f)
        assertTrue(filesFirst.measured)

        // Files total = 0
        val filesZero = wikiStep(SyncProgress.Files(done = 0, total = 0, bytes = 0L, firstDownload = false))
        assertEquals("Updating the wiki (0 of 0 files)", filesZero.label)
        assertEquals(0.70f, filesZero.fraction, 0.001f)

        // Reading: done=3, total=10 -> 0.70 + 0.20 * 3/10 = 0.76
        val reading = wikiStep(SyncProgress.Reading(done = 3, total = 10))
        assertEquals(2, reading.index)
        assertEquals("Reading the changes", reading.label)
        assertEquals(0.76f, reading.fraction, 0.001f)
        assertTrue(reading.measured)

        // Reading total = 0
        val readingZero = wikiStep(SyncProgress.Reading(done = 0, total = 0))
        assertEquals("Reading the changes", readingZero.label)
        assertEquals(0.90f, readingZero.fraction, 0.001f)

        val save = wikiStep(SyncProgress.Saving)
        assertEquals(3, save.index)
        assertEquals("Saving", save.label)
        assertEquals(0.90f, save.fraction, 0.001f)
        assertFalse(save.measured)
    }

    // ---- Formatters ----

    @Test
    fun formatBytesOutputs() {
        assertEquals("512 B", formatBytes(512L))
        assertEquals("340 KB", formatBytes(340L * 1024L))
        val onePointTwoMb = (1.2 * 1024 * 1024).toLong()
        assertEquals("1.2 MB", formatBytes(onePointTwoMb))
    }

    @Test
    fun formatDataDateSameYearAndOtherYear() {
        val zone = ZoneId.of("UTC")
        val locale = Locale.UK
        // 2026-10-12 12:00:00 UTC
        val epochMs = Instant.parse("2026-10-12T12:00:00Z").toEpochMilli()
        val nowSameYear = Instant.parse("2026-11-01T12:00:00Z").toEpochMilli()
        val nowOtherYear = Instant.parse("2027-01-15T12:00:00Z").toEpochMilli()

        assertEquals("12 Oct", formatDataDate(epochMs, nowSameYear, zone, locale))
        assertEquals("12 Oct 2026", formatDataDate(epochMs, nowOtherYear, zone, locale))
    }

    // ---- JobTracker ----

    @Test
    fun trackerHiddenBeforeShowDelayAndVisibleAtOnceWhenUserStarted() {
        val tracker = JobTracker()
        tracker.start(JobKind.WIKI, userStarted = false, now = 0L)
        assertNull(tracker.line(now = 500L))
        assertNull(tracker.line(now = 999L))
        assertNotNull(tracker.line(now = 1000L))

        val trackerUser = JobTracker()
        trackerUser.start(JobKind.WIKI, userStarted = true, now = 0L)
        assertNotNull(trackerUser.line(now = 0L))
    }

    @Test
    fun trackerMonotonicAndCappedAt99() {
        val tracker = JobTracker()
        tracker.start(JobKind.ADBLOCK, userStarted = true, now = 0L)

        // Step at 0.50
        tracker.update(JobKind.ADBLOCK, JobStep(0, "Downloading ad-block lists (3 of 5)", 0.50f, true), now = 0L)
        val line1 = tracker.line(0L)
        assertEquals(0.50f, line1!!.fraction!!, 0.001f)

        // Lower step fraction does not lower shown fraction
        tracker.update(JobKind.ADBLOCK, JobStep(0, "Downloading ad-block lists (2 of 5)", 0.30f, true), now = 0L)
        val line2 = tracker.line(0L)
        assertEquals(0.50f, line2!!.fraction!!, 0.001f)

        // Second job joins at 0.00, shown fraction does not drop
        tracker.start(JobKind.WIKI, userStarted = true, now = 0L)
        tracker.update(JobKind.WIKI, JobStep(0, "Checking for updates", 0.00f, false), now = 0L)
        val line3 = tracker.line(0L)
        assertEquals(0.50f, line3!!.fraction!!, 0.001f)

        // Capped at 0.99
        tracker.update(JobKind.ADBLOCK, JobStep(2, "Saving", 1.00f, true), now = 0L)
        tracker.update(JobKind.WIKI, JobStep(3, "Saving", 1.00f, true), now = 0L)
        val line4 = tracker.line(0L)
        assertEquals(0.99f, line4!!.fraction!!, 0.001f)
    }

    @Test
    fun trackerIndeterminateOnlyWhenAllVisibleStepsUnmeasured() {
        val tracker = JobTracker()
        tracker.start(JobKind.WIKI, userStarted = true, now = 0L)
        tracker.update(JobKind.WIKI, JobStep(0, "Checking for updates", 0.00f, false), now = 0L)

        // Single unmeasured step -> fraction is null
        val line1 = tracker.line(0L)
        assertNull(line1!!.fraction)
        assertEquals("Checking for updates", line1.text)

        // ADBLOCK joins with measured step -> fraction is non-null
        tracker.start(JobKind.ADBLOCK, userStarted = true, now = 0L)
        tracker.update(JobKind.ADBLOCK, JobStep(0, "Downloading ad-block lists (1 of 5)", 0.20f, true), now = 0L)
        val line2 = tracker.line(0L)
        assertNotNull(line2!!.fraction)
    }

    @Test
    fun trackerCombinedTextWithTwoJobs() {
        val tracker = JobTracker()
        tracker.start(JobKind.ADBLOCK, userStarted = true, now = 0L)
        tracker.start(JobKind.WIKI, userStarted = true, now = 0L)
        tracker.update(JobKind.ADBLOCK, JobStep(0, "Downloading ad-block lists (2 of 5)", 0.40f, true), now = 0L)
        tracker.update(JobKind.WIKI, JobStep(1, "Updating the wiki (10 of 40 files)", 0.20f, true), now = 0L)

        val line = tracker.line(0L)
        assertEquals("Updating ad-block lists and wiki · 30%", line!!.text)
        assertEquals(2, line.jobs.size)
    }

    @Test
    fun trackerStallAt30sAndKeepWaitingClearsIt() {
        val tracker = JobTracker()
        tracker.start(JobKind.ADBLOCK, userStarted = true, now = 0L)
        tracker.update(JobKind.ADBLOCK, JobStep(0, "Downloading ad-block lists (1 of 5)", 0.10f, true), now = 0L)

        val lineBeforeStall = tracker.line(29_999L)
        assertFalse(lineBeforeStall!!.stalled)
        assertEquals("Downloading ad-block lists (1 of 5) · 10%", lineBeforeStall.text)

        val lineStalled = tracker.line(30_000L)
        assertTrue(lineStalled!!.stalled)
        assertEquals("Taking longer than usual", lineStalled.text)
        assertEquals("Taking longer than usual. Downloading ad-block lists (1 of 5) · 10%", lineStalled.a11y)

        // keepWaiting clears stall
        tracker.keepWaiting(now = 30_000L)
        val lineAfterKeepWaiting = tracker.line(30_000L)
        assertFalse(lineAfterKeepWaiting!!.stalled)
        assertEquals("Downloading ad-block lists (1 of 5) · 10%", lineAfterKeepWaiting.text)
    }

    // ---- Result Texts (§2.2) ----

    @Test
    fun adblockResultTexts() {
        // Success
        val succ = adblockSuccessResult()
        assertEquals("Ad-block lists updated", succ.text)

        // Partial
        val zone = ZoneId.of("UTC")
        val locale = Locale.UK
        val list1Time = Instant.parse("2026-10-12T10:00:00Z").toEpochMilli()
        val nowMs = Instant.parse("2026-10-15T10:00:00Z").toEpochMilli()
        val failed = listOf(
            FailedList("EasyList", list1Time),
            FailedList("Custom Filter", null)
        )
        val partial = adblockPartialResult(total = 5, failedLists = failed, userStarted = true, nowMs = nowMs, zone = zone, locale = locale)
        assertEquals("3 of 5 ad-block lists updated", partial.text)
        assertEquals(listOf(
            "Not updated: EasyList — still using the copy from 12 Oct",
            "Not updated: Custom Filter"
        ), partial.details)
        assertEquals(setOf(JobKind.ADBLOCK), partial.retry)
        assertTrue(partial.sticky)

        // Failed with ready and lastUpdated
        val lastUp = Instant.parse("2026-10-12T10:00:00Z").toEpochMilli()
        val failReady = adblockFailedResult(ready = true, lastUpdated = lastUp, userStarted = false, nowMs = nowMs, zone = zone, locale = locale)
        assertEquals("Couldn't update the ad-block lists. Still using the lists from 12 Oct.", failReady.text)
        assertFalse(failReady.sticky)

        // Failed with ready and null lastUpdated
        val failReadyNoDate = adblockFailedResult(ready = true, lastUpdated = null, userStarted = true, nowMs = nowMs, zone = zone, locale = locale)
        assertEquals("Couldn't update the ad-block lists.", failReadyNoDate.text)

        // Failed without ready (no engine)
        val failNoEngine = adblockFailedResult(ready = false, lastUpdated = null, userStarted = true, nowMs = nowMs, zone = zone, locale = locale)
        assertEquals("Couldn't download the ad-block lists.", failNoEngine.text)
    }

    @Test
    fun wikiResultTexts() {
        val upBoth = wikiUpdatedResult(added = 3, removed = 2)
        assertEquals("Wiki updated · 3 new · 2 removed", upBoth.text)

        val upAdded = wikiUpdatedResult(added = 3, removed = 0)
        assertEquals("Wiki updated · 3 new", upAdded.text)

        val upRemoved = wikiUpdatedResult(added = 0, removed = 2)
        assertEquals("Wiki updated · 2 removed", upRemoved.text)

        val upNeither = wikiUpdatedResult(added = 0, removed = 0)
        assertEquals("Wiki updated", upNeither.text)

        val upToDate = wikiUpToDateResult()
        assertEquals("Wiki is up to date", upToDate.text)

        val cancelled = wikiCancelledResult()
        assertEquals("Update cancelled", cancelled.text)

        val zone = ZoneId.of("UTC")
        val locale = Locale.UK
        val lastSuccess = Instant.parse("2026-10-12T10:00:00Z").toEpochMilli()
        val nowMs = Instant.parse("2026-10-15T10:00:00Z").toEpochMilli()

        // Cancelled via wikiFailedResult
        val failCancelled = wikiFailedResult(SYNC_CANCELLED, lastSuccess, userStarted = false, nowMs = nowMs, zone = zone, locale = locale)
        assertEquals(ProgressLineUiState.Done("Update cancelled"), failCancelled)

        // Failed with lastSuccess
        val failWithHistory = wikiFailedResult("network error", lastSuccess, userStarted = false, nowMs = nowMs, zone = zone, locale = locale)
        assertTrue(failWithHistory is ProgressLineUiState.Problem)
        val probHistory = failWithHistory as ProgressLineUiState.Problem
        assertEquals("Couldn't update the wiki. Still using the data from 12 Oct.", probHistory.text)
        assertEquals(setOf(JobKind.WIKI), probHistory.retry)
        assertFalse(probHistory.sticky)

        // Failed without lastSuccess
        val failNoHistory = wikiFailedResult("network error", lastSuccess = null, userStarted = true, nowMs = nowMs, zone = zone, locale = locale)
        assertTrue(failNoHistory is ProgressLineUiState.Problem)
        val probNoHistory = failNoHistory as ProgressLineUiState.Problem
        assertEquals("Couldn't download the wiki.", probNoHistory.text)
        assertTrue(probNoHistory.sticky)
    }
}
