package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoSpeedTest {
    @Test
    fun keyIsPrefixedAndLowercased() {
        assertEquals("video_speed:example.com", videoSpeedKey("Example.COM"))
        assertEquals("video_speed:", VIDEO_SPEED_KEY_PREFIX)
    }

    @Test
    fun storedValueRoundTrips() {
        for (s in PLAYBACK_SPEEDS) assertEquals(s, parseStoredSpeed(formatStoredSpeed(s)))
        assertEquals("1.5", formatStoredSpeed(1.5f))
    }

    @Test
    fun badStoredValuesAreIgnored() {
        assertNull(parseStoredSpeed(null))
        assertNull(parseStoredSpeed("fast"))
        assertNull(parseStoredSpeed("0"))
        assertNull(parseStoredSpeed("500"))
    }

    @Test
    fun labelsAreReadable() {
        assertEquals("1×", speedLabel(1f))
        assertEquals("1.25×", speedLabel(1.25f))
        assertEquals("0.5×", speedLabel(0.5f))
    }
}
