package io.github.omeg4dev.fmhy.browser

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Conflates and throttles tab flow emissions so background threads (such as ad-block callbacks)
 * and rapid progress ticks do not flood Jetpack Compose with recompositions.
 *
 * Structural changes (open, close, select, restore) bypass the throttle via [emitImmediate].
 */
class TabUpdateThrottler(
    val intervalMs: Long = 100L,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val postDelayed: (delayMs: Long, action: () -> Unit) -> Unit,
    private val emit: () -> Unit,
) {
    private val hasPending = AtomicBoolean(false)
    private val isScheduled = AtomicBoolean(false)
    @Volatile private var lastEmitTime: Long = -intervalMs

    fun emitImmediate() {
        hasPending.set(false)
        isScheduled.set(false)
        lastEmitTime = timeProvider()
        emit()
    }

    fun requestThrottled() {
        hasPending.set(true)
        scheduleIfNeeded()
    }

    private fun scheduleIfNeeded() {
        if (!isScheduled.compareAndSet(false, true)) return

        val now = timeProvider()
        val elapsed = now - lastEmitTime
        val delayMs = if (elapsed >= intervalMs) 0L else intervalMs - elapsed

        postDelayed(delayMs) {
            isScheduled.set(false)
            if (hasPending.compareAndSet(true, false)) {
                lastEmitTime = timeProvider()
                emit()
            }
        }
    }
}
