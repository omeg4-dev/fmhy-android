package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.browser.media.BACKGROUND_IDLE_TIMEOUT_MS
import io.github.omeg4dev.fmhy.browser.media.IdleTimeout
import io.github.omeg4dev.fmhy.browser.media.shouldKeepPlaying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPlaybackTest {
    @Test
    fun keepsPlayingOnlyWhenEnabledPlayingAndNotFinishing() {
        assertTrue(shouldKeepPlaying(prefOn = true, isPlaying = true, isFinishing = false))
        assertFalse(shouldKeepPlaying(prefOn = false, isPlaying = true, isFinishing = false))
        assertFalse(shouldKeepPlaying(prefOn = true, isPlaying = false, isFinishing = false))
        assertFalse(shouldKeepPlaying(prefOn = true, isPlaying = true, isFinishing = true))
    }

    @Test
    fun idleTimeoutExpiresAfterTenMinutesPaused() {
        var now = 0L
        val idle = IdleTimeout(clock = { now })
        assertEquals(10 * 60 * 1000L, BACKGROUND_IDLE_TIMEOUT_MS)
        idle.onPlayingChanged(true)
        now += BACKGROUND_IDLE_TIMEOUT_MS * 3
        assertFalse(idle.isExpired())
        assertNull(idle.remainingMs())

        idle.onPlayingChanged(false)
        now += BACKGROUND_IDLE_TIMEOUT_MS - 1
        assertFalse(idle.isExpired())
        assertEquals(1L, idle.remainingMs())
        now += 1
        assertTrue(idle.isExpired())
    }

    @Test
    fun repeatedPausedReportsDoNotRestartTheClock() {
        var now = 0L
        val idle = IdleTimeout(timeoutMs = 1000, clock = { now })
        idle.onPlayingChanged(false)
        now += 800
        idle.onPlayingChanged(false)
        now += 200
        assertTrue(idle.isExpired())
    }

    @Test
    fun playingAgainResetsTheClock() {
        var now = 0L
        val idle = IdleTimeout(timeoutMs = 1000, clock = { now })
        idle.onPlayingChanged(false)
        now += 900
        idle.onPlayingChanged(true)
        now += 5000
        assertFalse(idle.isExpired())
        idle.onPlayingChanged(false)
        now += 999
        assertFalse(idle.isExpired())
    }
}
