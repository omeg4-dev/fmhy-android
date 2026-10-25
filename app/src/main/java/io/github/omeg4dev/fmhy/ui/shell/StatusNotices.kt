package io.github.omeg4dev.fmhy.ui.shell

import io.github.omeg4dev.fmhy.browser.adblock.AdblockStatus
import io.github.omeg4dev.fmhy.data.sync.SyncResult
import io.github.omeg4dev.fmhy.di.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StatusNotices(
    private val scope: CoroutineScope,
    syncStatus: Flow<SyncStatus>,
    adblockInputs: Flow<Pair<Boolean, AdblockStatus>>?,
    private val isSyncBannerConsumed: () -> Boolean,
    private val markSyncBannerConsumed: () -> Unit,
    private val cancel: (JobKind) -> Unit,
    private val retry: (JobKind) -> Unit,
    private val lastUpdated: (JobKind) -> Long?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // Inputs arrive on several threads (app scope on Dispatchers.Default, taps on Main): every
    // read/write of the tracker and the result state happens under this lock.
    private val lock = Any()
    private val tracker = JobTracker()

    private val _line = MutableStateFlow<ProgressLineUiState?>(null)
    val line: StateFlow<ProgressLineUiState?> = _line.asStateFlow()

    private val _jobs = MutableStateFlow<List<JobRowUi>>(emptyList())
    val jobs: StateFlow<List<JobRowUi>> = _jobs.asStateFlow()

    private val finishedResults = mutableListOf<ProgressLineUiState>()

    private var tickerJob: Job? = null
    private var autoHideJob: Job? = null

    private var previousUpdating: Boolean? = null

    init {
        scope.launch {
            syncStatus.collect(::handleSyncStatus)
        }
        if (adblockInputs != null) {
            scope.launch {
                adblockInputs.collect { (enabled, status) ->
                    handleAdblock(enabled, status)
                }
            }
        }
    }

    private fun handleSyncStatus(status: SyncStatus) = synchronized(lock) {
        when (status) {
            is SyncStatus.Idle -> Unit
            is SyncStatus.Syncing -> {
                startJob(JobKind.WIKI, status.userStarted)
                if (status.progress != null) {
                    tracker.update(JobKind.WIKI, wikiStep(status.progress), clock())
                }
                recomputeLine()
            }
            is SyncStatus.Finished -> {
                val job = tracker.finish(JobKind.WIKI)
                if (job != null) {
                    val result = computeWikiResult(job, status.result)
                    if (result != null) {
                        finishedResults.add(result)
                    }
                    checkAllFinished()
                }
            }
        }
    }

    private fun handleAdblock(enabled: Boolean, status: AdblockStatus): Unit = synchronized(lock) {
        if (!enabled) {
            if (previousUpdating == true) {
                tracker.finish(JobKind.ADBLOCK)
                recomputeLine()
            }
            previousUpdating = false
            return
        }

        val prev = previousUpdating
        if (prev == null) {
            if (!status.updating) {
                previousUpdating = false
                return
            }
            previousUpdating = true
            startJob(JobKind.ADBLOCK, status.userStarted)
            if (status.step != null) {
                tracker.update(JobKind.ADBLOCK, adblockStep(status.step), clock())
            }
            recomputeLine()
            return
        }

        if (!prev && status.updating) {
            previousUpdating = true
            startJob(JobKind.ADBLOCK, status.userStarted)
            if (status.step != null) {
                tracker.update(JobKind.ADBLOCK, adblockStep(status.step), clock())
            }
            recomputeLine()
            return
        }

        if (prev && status.updating) {
            if (status.step != null) {
                tracker.update(JobKind.ADBLOCK, adblockStep(status.step), clock())
                recomputeLine()
            }
            return
        }

        if (prev && !status.updating) {
            previousUpdating = false
            val job = tracker.finish(JobKind.ADBLOCK)
            if (job != null) {
                val result = computeAdblockResult(job, status)
                if (result != null) {
                    finishedResults.add(result)
                }
                checkAllFinished()
            }
        }
    }

    private fun startJob(kind: JobKind, userStarted: Boolean) {
        if (tracker.running().isEmpty()) {
            autoHideJob?.cancel()
            autoHideJob = null
            finishedResults.clear()
            _line.value = null
        }
        tracker.start(kind, userStarted, clock())
        ensureTicker()
    }

    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive && tracker.running().isNotEmpty()) {
                delay(1000L)
                synchronized(lock) { recomputeLine() }
            }
        }
    }

    private fun recomputeLine() {
        _jobs.value = tracker.runningUi()
        if (tracker.running().isNotEmpty()) {
            _line.value = tracker.line(clock())
        }
    }

    private fun checkAllFinished() {
        if (tracker.running().isNotEmpty()) {
            recomputeLine()
        } else {
            tickerJob?.cancel()
            tickerJob = null
            _jobs.value = emptyList()
            if (finishedResults.isEmpty()) {
                _line.value = null
            } else {
                val problems = finishedResults.filterIsInstance<ProgressLineUiState.Problem>()
                if (problems.isNotEmpty()) {
                    val firstProblem = problems.first()
                    val allDetails = problems.flatMap { it.details }
                    val allRetry = problems.flatMap { it.retry }.toSet()
                    val anySticky = problems.any { it.sticky }
                    val finalProblem = ProgressLineUiState.Problem(
                        text = firstProblem.text,
                        details = allDetails,
                        retry = allRetry,
                        sticky = anySticky,
                    )
                    _line.value = finalProblem
                    if (!finalProblem.sticky) {
                        launchAutoHide(PROBLEM_MS)
                    }
                } else {
                    val dones = finishedResults.filterIsInstance<ProgressLineUiState.Done>()
                    val combinedText = dones.joinToString(" · ") { it.text }
                    _line.value = ProgressLineUiState.Done(combinedText)
                    launchAutoHide(DONE_MS)
                }
                finishedResults.clear()
            }
        }
    }

    private fun launchAutoHide(delayMs: Long) {
        autoHideJob?.cancel()
        autoHideJob = scope.launch {
            delay(delayMs)
            synchronized(lock) {
                _line.value = null
                markSyncBannerConsumed()
            }
        }
    }

    private fun computeWikiResult(job: RunningJob, result: SyncResult): ProgressLineUiState? {
        if (isSyncBannerConsumed()) return null
        return when (result) {
            is SyncResult.Skipped -> null
            is SyncResult.UpToDate -> {
                if (!job.wasVisible) null else wikiUpToDateResult()
            }
            is SyncResult.Updated -> {
                if (!job.wasVisible && (result.added == 0 && result.removed == 0)) {
                    null
                } else {
                    wikiUpdatedResult(result.added, result.removed)
                }
            }
            is SyncResult.Failed -> {
                if (result.reason == SYNC_CANCELLED) {
                    if (!job.wasVisible) null else wikiCancelledResult()
                } else {
                    val lastSuccess = lastUpdated(JobKind.WIKI)
                    wikiFailedResult(
                        reason = result.reason,
                        lastSuccess = lastSuccess,
                        userStarted = job.userStarted,
                        nowMs = clock(),
                    )
                }
            }
        }
    }

    private fun computeAdblockResult(job: RunningJob, status: AdblockStatus): ProgressLineUiState? {
        if (status.cancelled) {
            return if (!job.wasVisible) null else adblockCancelledResult()
        }
        if (status.error != null) {
            val lastUp = lastUpdated(JobKind.ADBLOCK)
            return adblockFailedResult(
                ready = status.ready,
                lastUpdated = lastUp,
                userStarted = job.userStarted,
                nowMs = clock(),
            )
        }
        if (status.failedLists.isNotEmpty()) {
            val total = if (status.totalLists > 0) status.totalLists else status.failedLists.size
            return adblockPartialResult(
                total = total,
                failedLists = status.failedLists,
                userStarted = job.userStarted,
                nowMs = clock(),
            )
        }
        return if (!job.wasVisible) null else adblockSuccessResult()
    }

    fun keepWaiting() = synchronized(lock) {
        tracker.keepWaiting(clock())
        recomputeLine()
    }

    fun cancel() {
        val kinds = synchronized(lock) { tracker.running().map { it.kind } }
        kinds.forEach { cancel(it) }
    }

    fun cancel(kind: JobKind) {
        cancel.invoke(kind)
    }

    fun retry() {
        val current = _line.value
        if (current is ProgressLineUiState.Problem) {
            val kinds = current.retry
            dismiss()
            kinds.forEach { retry(it) }
        }
    }

    fun dismiss(): Unit = synchronized(lock) {
        autoHideJob?.cancel()
        autoHideJob = null
        _line.value = null
        markSyncBannerConsumed()
    }

    fun lastUpdated(kind: JobKind): Long? = lastUpdated.invoke(kind)

    companion object {
        const val SHOW_DELAY_MS = 1000L
        const val STALL_MS = 30_000L
        const val DONE_MS = 3000L
        const val PROBLEM_MS = 6000L
    }
}
