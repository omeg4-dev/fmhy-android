package io.github.omeg4dev.fmhy.ui.shell

import io.github.omeg4dev.fmhy.browser.adblock.FailedList
import io.github.omeg4dev.fmhy.data.adblock.FilterListProgress
import io.github.omeg4dev.fmhy.data.sync.SyncProgress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min

enum class JobKind { ADBLOCK, WIKI }

/** [index] = position in [stepNames]; [fraction] = whole-job fraction at this point; [measured] false → moving bar, no number. */
data class JobStep(val index: Int, val label: String, val fraction: Float, val measured: Boolean)

fun stepNames(kind: JobKind): List<String> = when (kind) {
    JobKind.ADBLOCK -> listOf("Downloading ad-block lists", "Preparing the rules", "Saving")
    JobKind.WIKI -> listOf("Checking for updates", "Updating the wiki", "Reading the changes", "Saving")
}

fun jobTitle(kind: JobKind): String = when (kind) {
    JobKind.ADBLOCK -> "Ad-block lists"
    JobKind.WIKI -> "Wiki"
}

fun adblockStep(p: FilterListProgress): JobStep = when (p) {
    is FilterListProgress.Downloading -> {
        val total = p.total
        val done = p.done
        val displayDone = min(done + 1, total)
        val ratio = if (total == 0) 1f else done.toFloat() / total
        val fraction = 0.70f * ratio
        JobStep(
            index = 0,
            label = "Downloading ad-block lists ($displayDone of $total)",
            fraction = fraction,
            measured = true,
        )
    }
    FilterListProgress.Preparing -> JobStep(
        index = 1,
        label = "Preparing the rules",
        fraction = 0.70f,
        measured = false,
    )
    FilterListProgress.Saving -> JobStep(
        index = 2,
        label = "Saving",
        fraction = 0.90f,
        measured = false,
    )
}

fun wikiStep(p: SyncProgress): JobStep = when (p) {
    SyncProgress.Checking -> JobStep(
        index = 0,
        label = "Checking for updates",
        fraction = 0.00f,
        measured = false,
    )
    is SyncProgress.Files -> {
        val ratio = if (p.total == 0) 1f else p.done.toFloat() / p.total
        val fraction = 0.10f + 0.60f * ratio
        val extra = if (p.firstDownload) " · ${formatBytes(p.bytes)}" else ""
        JobStep(
            index = 1,
            label = "Updating the wiki (${p.done} of ${p.total} files)$extra",
            fraction = fraction,
            measured = true,
        )
    }
    is SyncProgress.Reading -> {
        val ratio = if (p.total == 0) 1f else p.done.toFloat() / p.total
        val fraction = 0.70f + 0.20f * ratio
        JobStep(
            index = 2,
            label = "Reading the changes",
            fraction = fraction,
            measured = true,
        )
    }
    SyncProgress.Saving -> JobStep(
        index = 3,
        label = "Saving",
        fraction = 0.90f,
        measured = false,
    )
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
}

fun formatDataDate(
    epochMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String {
    val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val nowDate = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val pattern = if (date.year == nowDate.year) "d MMM" else "d MMM yyyy"
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

const val SYNC_CANCELLED = "Cancelled"
const val SHOW_DELAY_MS = 1000L
const val STALL_MS = 30_000L
const val DONE_MS = 3000L
const val PROBLEM_MS = 6000L

sealed interface ProgressLineUiState {
    data class Running(
        val text: String,
        val a11y: String,
        val fraction: Float?,
        val stalled: Boolean,
        val jobs: List<JobRowUi>
    ) : ProgressLineUiState

    data class Done(val text: String) : ProgressLineUiState

    /** Failure or partial success; [details] are extra lines shown in the panel; [retry] = jobs to re-run. */
    data class Problem(
        val text: String,
        val details: List<String>,
        val retry: Set<JobKind>,
        val sticky: Boolean
    ) : ProgressLineUiState
}

/** One job for the panel and the settings rows. [fraction] null = unmeasured step. */
data class JobRowUi(
    val kind: JobKind,
    val title: String,
    val stepIndex: Int,
    val label: String,
    val fraction: Float?,
    val percent: Int
)

data class RunningJob(
    val kind: JobKind,
    val step: JobStep?,
    val fraction: Float,
    val userStarted: Boolean,
    val startedAt: Long,
    val lastMovedAt: Long,
    val wasVisible: Boolean
)

fun RunningJob.toJobRowUi(): JobRowUi {
    val title = jobTitle(kind)
    val stepIndex = step?.index ?: 0
    val label = step?.label ?: when (kind) {
        JobKind.ADBLOCK -> "Downloading ad-block lists"
        JobKind.WIKI -> "Checking for updates"
    }
    val measuredFraction = if (step != null && step.measured) fraction else null
    val percent = floor(fraction * 100).toInt()
    return JobRowUi(
        kind = kind,
        title = title,
        stepIndex = stepIndex,
        label = label,
        fraction = measuredFraction,
        percent = percent,
    )
}

class JobTracker {
    private val jobs = mutableMapOf<JobKind, RunningJob>()
    private var lastShown: Float = 0f

    fun start(kind: JobKind, userStarted: Boolean, now: Long) {
        val existing = jobs[kind]
        if (existing != null) {
            jobs[kind] = existing.copy(userStarted = existing.userStarted || userStarted)
        } else {
            jobs[kind] = RunningJob(
                kind = kind,
                step = null,
                fraction = 0f,
                userStarted = userStarted,
                startedAt = now,
                lastMovedAt = now,
                wasVisible = false,
            )
        }
    }

    fun update(kind: JobKind, step: JobStep, now: Long) {
        val existing = jobs[kind] ?: RunningJob(
            kind = kind,
            step = null,
            fraction = 0f,
            userStarted = false,
            startedAt = now,
            lastMovedAt = now,
            wasVisible = false,
        )
        val newFraction = maxOf(existing.fraction, step.fraction).coerceAtMost(0.99f)
        val changed = (existing.step?.label != step.label) || (existing.fraction != newFraction)
        val newLastMovedAt = if (changed) now else existing.lastMovedAt
        jobs[kind] = existing.copy(
            step = step,
            fraction = newFraction,
            lastMovedAt = newLastMovedAt,
        )
    }

    fun finish(kind: JobKind): RunningJob? {
        val removed = jobs.remove(kind)
        if (jobs.isEmpty()) {
            lastShown = 0f
        }
        return removed
    }

    fun keepWaiting(now: Long) {
        for ((k, job) in jobs) {
            jobs[k] = job.copy(lastMovedAt = now)
        }
    }

    fun running(): List<RunningJob> = listOfNotNull(jobs[JobKind.ADBLOCK], jobs[JobKind.WIKI])

    fun runningUi(): List<JobRowUi> = running().map { it.toJobRowUi() }

    fun line(now: Long): ProgressLineUiState.Running? {
        if (jobs.isEmpty()) return null

        for ((k, job) in jobs) {
            if (job.userStarted || now - job.startedAt >= SHOW_DELAY_MS) {
                if (!job.wasVisible) {
                    jobs[k] = job.copy(wasVisible = true)
                }
            }
        }

        val visibleJobs = running().filter { it.wasVisible }
        if (visibleJobs.isEmpty()) return null

        val mean = visibleJobs.map { it.fraction }.average().toFloat()
        val shownFraction = maxOf(lastShown, mean).coerceAtMost(0.99f)
        lastShown = shownFraction

        val allUnmeasured = visibleJobs.all { it.step == null || !it.step.measured }
        val uiFraction: Float? = if (allUnmeasured) null else shownFraction
        val percent = floor(shownFraction * 100).toInt()

        val normalText = if (visibleJobs.size == 1) {
            val job = visibleJobs.first()
            val step = job.step
            val label = step?.label ?: when (job.kind) {
                JobKind.ADBLOCK -> "Downloading ad-block lists"
                JobKind.WIKI -> "Checking for updates"
            }
            if (step != null && step.measured) {
                "$label · $percent%"
            } else {
                label
            }
        } else {
            if (uiFraction != null) {
                "Updating ad-block lists and wiki · $percent%"
            } else {
                "Updating ad-block lists and wiki"
            }
        }

        val maxLastMovedAt = visibleJobs.maxOf { it.lastMovedAt }
        val stalled = (now - maxLastMovedAt) >= STALL_MS

        val text = if (stalled) "Taking longer than usual" else normalText
        val a11y = if (stalled) "Taking longer than usual. $normalText" else normalText

        return ProgressLineUiState.Running(
            text = text,
            a11y = a11y,
            fraction = uiFraction,
            stalled = stalled,
            jobs = visibleJobs.map { it.toJobRowUi() },
        )
    }
}

fun failedListDetail(
    list: FailedList,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String {
    return if (list.lastFetchedAt != null && list.lastFetchedAt > 0) {
        "Not updated: ${list.title} — still using the copy from ${formatDataDate(list.lastFetchedAt, nowMs, zone, locale)}"
    } else {
        "Not updated: ${list.title}"
    }
}

fun adblockSuccessResult(): ProgressLineUiState.Done = ProgressLineUiState.Done("Ad-block lists updated")

fun adblockCancelledResult(): ProgressLineUiState.Done = ProgressLineUiState.Done("Update cancelled")

fun adblockPartialResult(
    total: Int,
    failedLists: List<FailedList>,
    userStarted: Boolean = false,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): ProgressLineUiState.Problem {
    val n = if (total > 0) total else failedLists.size
    val k = failedLists.size
    val text = "${n - k} of $n ad-block lists updated"
    val details = failedLists.map { failedListDetail(it, nowMs, zone, locale) }
    return ProgressLineUiState.Problem(
        text = text,
        details = details,
        retry = setOf(JobKind.ADBLOCK),
        sticky = userStarted,
    )
}

fun adblockFailedResult(
    ready: Boolean,
    lastUpdated: Long?,
    userStarted: Boolean = false,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): ProgressLineUiState.Problem {
    val text = if (ready) {
        if (lastUpdated != null && lastUpdated > 0) {
            "Couldn't update the ad-block lists. Still using the lists from ${formatDataDate(lastUpdated, nowMs, zone, locale)}."
        } else {
            "Couldn't update the ad-block lists."
        }
    } else {
        "Couldn't download the ad-block lists."
    }
    return ProgressLineUiState.Problem(
        text = text,
        details = emptyList(),
        retry = setOf(JobKind.ADBLOCK),
        sticky = userStarted,
    )
}

fun wikiUpdatedResult(added: Int, removed: Int): ProgressLineUiState.Done {
    val extra = buildString {
        if (added > 0) append(" · $added new")
        if (removed > 0) append(" · $removed removed")
    }
    return ProgressLineUiState.Done("Wiki updated$extra")
}

fun wikiUpToDateResult(): ProgressLineUiState.Done = ProgressLineUiState.Done("Wiki is up to date")

fun wikiCancelledResult(): ProgressLineUiState.Done = ProgressLineUiState.Done("Update cancelled")

fun wikiFailedResult(
    reason: String,
    lastSuccess: Long?,
    userStarted: Boolean = false,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): ProgressLineUiState {
    if (reason == SYNC_CANCELLED) {
        return wikiCancelledResult()
    }
    val text = if (lastSuccess != null && lastSuccess > 0) {
        "Couldn't update the wiki. Still using the data from ${formatDataDate(lastSuccess, nowMs, zone, locale)}."
    } else {
        "Couldn't download the wiki."
    }
    return ProgressLineUiState.Problem(
        text = text,
        details = emptyList(),
        retry = setOf(JobKind.WIKI),
        sticky = userStarted,
    )
}
