package io.github.omeg4dev.fmhy.browser.media

import android.os.SystemClock

/** Paused background playback is given up after this long. */
const val BACKGROUND_IDLE_TIMEOUT_MS = 10 * 60 * 1000L

/** Whether leaving the browser should leave the current tab playing in the background. */
fun shouldKeepPlaying(prefOn: Boolean, isPlaying: Boolean, isFinishing: Boolean): Boolean =
    prefOn && isPlaying && !isFinishing

/**
 * Tracks how long background media has been paused. Playing resets the timer; the service closes
 * itself once [isExpired] turns true.
 */
class IdleTimeout(
    private val timeoutMs: Long = BACKGROUND_IDLE_TIMEOUT_MS,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private var pausedSince: Long? = null

    /** Feed every playing/paused report; only the first paused report starts the clock. */
    fun onPlayingChanged(playing: Boolean) {
        if (playing) {
            pausedSince = null
        } else if (pausedSince == null) {
            pausedSince = clock()
        }
    }

    fun isExpired(): Boolean {
        val since = pausedSince ?: return false
        return clock() - since >= timeoutMs
    }

    /** Milliseconds until [isExpired], or null while playing. */
    fun remainingMs(): Long? {
        val since = pausedSince ?: return null
        return (timeoutMs - (clock() - since)).coerceAtLeast(0L)
    }
}
