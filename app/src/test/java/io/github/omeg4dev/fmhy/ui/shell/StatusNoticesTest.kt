package io.github.omeg4dev.fmhy.ui.shell

import io.github.omeg4dev.fmhy.browser.adblock.AdblockStatus
import io.github.omeg4dev.fmhy.data.sync.SyncResult
import io.github.omeg4dev.fmhy.di.SyncStatus
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

    private class Rig(scope: TestScope, consumed: Boolean = false, adblock: Boolean = true) {
        val sync = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
        val adblockIn = MutableStateFlow(true to AdblockStatus())
        var consumed = consumed
        var downloads = 0
        val notices = StatusNotices(
            scope = scope.backgroundScope,
            syncStatus = sync,
            adblockInputs = if (adblock) adblockIn else null,
            isSyncBannerConsumed = { this.consumed },
            markSyncBannerConsumed = { this.consumed = true },
            startFilterDownload = { downloads++ },
        )
    }

    private fun TestScope.rig(consumed: Boolean = false, adblock: Boolean = true): Rig {
        val r = Rig(this, consumed, adblock)
        runCurrent()
        return r
    }

    // ---- sync ----

    @Test
    fun fastSyncDoesNotFlickerSyncingPill() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.sync.value = SyncStatus.Syncing
        advanceTimeBy(200); runCurrent()
        assertNull(r.notices.sync.value)

        r.sync.value = SyncStatus.Finished(SyncResult.UpToDate(200))
        runCurrent()
        val b = r.notices.sync.value
        assertEquals("Synced in background · up to date", b?.text)
        assertFalse(b!!.isLoading)
        assertFalse(b.isError)
    }

    @Test
    fun slowSyncShowsSyncingPillAfter600ms() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.sync.value = SyncStatus.Syncing
        advanceTimeBy(590); runCurrent()
        assertNull(r.notices.sync.value)
        advanceTimeBy(20); runCurrent()
        assertEquals("Syncing…", r.notices.sync.value?.text)
        assertTrue(r.notices.sync.value!!.isLoading)

        r.sync.value = SyncStatus.Finished(SyncResult.Updated(added = 12, removed = 3, changedSections = 1, tookMs = 1200))
        runCurrent()
        assertEquals("Synced in background · 12 new · 3 removed", r.notices.sync.value?.text)
    }

    @Test
    fun resultAutoHidesAfter4SecondsAndIsConsumed() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.sync.value = SyncStatus.Finished(SyncResult.UpToDate(30))
        runCurrent()
        assertNotNull(r.notices.sync.value)
        assertFalse(r.consumed)

        advanceTimeBy(3900); runCurrent()
        assertNotNull(r.notices.sync.value)
        advanceTimeBy(200); runCurrent()
        assertNull(r.notices.sync.value)
        assertTrue(r.consumed)
    }

    @Test
    fun tapDismissesImmediately() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.sync.value = SyncStatus.Finished(SyncResult.UpToDate(30))
        runCurrent()
        r.notices.dismissSync()
        assertNull(r.notices.sync.value)
        assertTrue(r.consumed)
    }

    @Test
    fun consumedBannerIsNotShownAgain() = runTest(UnconfinedTestDispatcher()) {
        val r = rig(consumed = true)
        r.sync.value = SyncStatus.Finished(SyncResult.UpToDate(30))
        runCurrent()
        assertNull(r.notices.sync.value)
    }

    @Test
    fun failedShowsOfflineAndSkippedShowsNothing() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.sync.value = SyncStatus.Finished(SyncResult.Failed("timeout", 50))
        runCurrent()
        assertEquals("Offline — showing saved data", r.notices.sync.value?.text)
        assertTrue(r.notices.sync.value!!.isError)

        r.sync.value = SyncStatus.Finished(SyncResult.Skipped)
        runCurrent()
        assertNull(r.notices.sync.value)
    }

    @Test
    fun updatedTexts() {
        fun t(a: Int, rm: Int) = syncBannerFor(SyncResult.Updated(added = a, removed = rm, changedSections = 1, tookMs = 10))?.text
        assertEquals("Synced in background · 4 new", t(4, 0))
        assertEquals("Synced in background · 2 removed", t(0, 2))
        assertEquals("Synced in background · updated", t(0, 0))
        assertNull(syncBannerFor(SyncResult.Skipped))
    }

    // ---- ad-block ----

    @Test
    fun adblockStartsHidden() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        assertNull(r.notices.adblock.value)
    }

    @Test
    fun adblockDownloadThenReadyClearsAfter2s() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.adblockIn.value = true to AdblockStatus(updating = true, progress = 0.4f)
        runCurrent()
        assertEquals(AdblockSetupUiState.Downloading(0.4f), r.notices.adblock.value)

        r.adblockIn.value = true to AdblockStatus(ready = true)
        runCurrent()
        assertEquals(AdblockSetupUiState.Ready, r.notices.adblock.value)
        advanceTimeBy(1900); runCurrent()
        assertEquals(AdblockSetupUiState.Ready, r.notices.adblock.value)
        advanceTimeBy(200); runCurrent()
        assertNull(r.notices.adblock.value)
    }

    @Test
    fun adblockFailureThenRetry() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.adblockIn.value = true to AdblockStatus(updating = true)
        runCurrent()
        r.adblockIn.value = true to AdblockStatus(error = "net")
        runCurrent()
        assertEquals(AdblockSetupUiState.Failed, r.notices.adblock.value)

        r.notices.retryAdblock()
        assertEquals(AdblockSetupUiState.Downloading(null), r.notices.adblock.value)
        assertEquals(1, r.downloads)
    }

    @Test
    fun adblockHiddenWhenSettingOff() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.adblockIn.value = true to AdblockStatus(updating = true)
        runCurrent()
        r.adblockIn.value = false to AdblockStatus(updating = true)
        runCurrent()
        assertNull(r.notices.adblock.value)
    }

    @Test
    fun nextAdblockStateRules() {
        assertNull(nextAdblockState(null, enabled = false, status = AdblockStatus(updating = true)))
        assertEquals(AdblockSetupUiState.Failed, nextAdblockState(null, true, AdblockStatus(error = "x", ready = false)))
        assertNull(nextAdblockState(null, true, AdblockStatus(ready = true)))
    }
}
