package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSeekTest {
    @Test
    fun zonesAreThirds() {
        assertEquals(-1, seekZone(10f, 900))
        assertEquals(0, seekZone(450f, 900))
        assertEquals(1, seekZone(800f, 900))
        assertEquals(0, seekZone(10f, 0))
    }

    @Test
    fun repeatedSeeksOnOneSideAccumulate() {
        var now = 1000L
        val acc = SeekAccumulator { now }
        assertFalse(acc.isActive())
        assertEquals(10, acc.register(1))
        now += 600
        assertTrue(acc.isActive())
        assertEquals(20, acc.register(1))
        now += 600
        assertEquals(30, acc.register(1))
    }

    @Test
    fun pauseOrOtherSideStartsOver() {
        var now = 1000L
        val acc = SeekAccumulator { now }
        acc.register(1)
        now += 1500
        assertFalse(acc.isActive())
        assertEquals(10, acc.register(1))
        now += 200
        assertEquals(-10, acc.register(-1))
    }

    @Test
    fun labelsUseSignAndSeconds() {
        assertEquals("+20 s", seekLabel(20))
        assertEquals("−10 s", seekLabel(-10))
    }

    @Test
    fun seekScriptTargetsFullscreenVideoFirst() {
        val js = VideoJs.seekCall(-10)
        assertTrue(js.contains("document.fullscreenElement"))
        assertTrue(js.endsWith("(-10);"))
    }
}
