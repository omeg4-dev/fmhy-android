package io.github.omeg4dev.fmhy.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val NEW_VISIT_AFTER_BACKGROUND_SEC: Long = 30 * 60

sealed interface VisitDecision {
    data class NewVisit(val cutoffTs: Long, val newVisitStartTs: Long) : VisitDecision
    data object ContinueVisit : VisitDecision
}

fun nextVisit(
    storedStart: Long,
    nowSec: Long,
    lastBackgroundSec: Long?,
    hasStartedBefore: Boolean,
): VisitDecision {
    if (!hasStartedBefore) {
        return VisitDecision.NewVisit(cutoffTs = storedStart, newVisitStartTs = nowSec)
    }
    if (lastBackgroundSec != null && (nowSec - lastBackgroundSec) > NEW_VISIT_AFTER_BACKGROUND_SEC) {
        return VisitDecision.NewVisit(cutoffTs = storedStart, newVisitStartTs = nowSec)
    }
    return VisitDecision.ContinueVisit
}

interface VisitTracker {
    val visitCutoffTs: StateFlow<Long>
    fun onStart()
    fun onStop()
}

class DefaultVisitTracker(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) : VisitTracker {

    private val _visitCutoffTs = MutableStateFlow(0L)
    override val visitCutoffTs: StateFlow<Long> = _visitCutoffTs.asStateFlow()

    private var hasStartedBefore = false
    private var lastBackgroundSec: Long? = null
    private val mutex = Mutex()

    suspend fun recordStart(nowSec: Long = clock()) {
        mutex.withLock {
            val storedStart = settingsRepository.lastVisitTs.first()
            val decision = nextVisit(
                storedStart = storedStart,
                nowSec = nowSec,
                lastBackgroundSec = lastBackgroundSec,
                hasStartedBefore = hasStartedBefore,
            )
            hasStartedBefore = true
            when (decision) {
                is VisitDecision.NewVisit -> {
                    _visitCutoffTs.value = decision.cutoffTs
                    settingsRepository.setLastVisitTs(decision.newVisitStartTs)
                }
                is VisitDecision.ContinueVisit -> {
                    // Current visit continues; cutoff unchanged, nothing persisted
                }
            }
        }
    }

    fun recordStop(nowSec: Long = clock()) {
        lastBackgroundSec = nowSec
    }

    override fun onStart() {
        val now = clock()
        scope.launch { recordStart(now) }
    }

    override fun onStop() {
        recordStop(clock())
    }
}
