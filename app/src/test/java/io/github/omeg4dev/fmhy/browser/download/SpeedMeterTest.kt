package io.github.omeg4dev.fmhy.browser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedMeterTest {

    @Test
    fun testUnknownBeforeSecondSample() {
        val meter = SpeedMeter()
        assertNull(meter.bytesPerSecond)
        meter.sample(0, 1000)
        assertNull(meter.bytesPerSecond)
    }

    @Test
    fun testSamplesLessThan250msApartIgnored() {
        val meter = SpeedMeter()
        meter.sample(0, 1000)
        // 100ms later -> ignored
        meter.sample(500, 1100)
        assertNull(meter.bytesPerSecond)

        // 1000ms later from initial sample (2000 - 1000 = 1000ms >= 250ms)
        meter.sample(1048576, 2000)
        val speed = meter.bytesPerSecond
        assertNotNull(speed)
        assertEquals(1048576L, speed)
    }

    @Test
    fun testSteady1MBpsConvergesNear1048576() {
        val meter = SpeedMeter(alpha = 0.3)
        var t = 0L
        var bytes = 0L
        meter.sample(bytes, t)

        for (i in 1..10) {
            t += 1000L
            bytes += 1048576L
            meter.sample(bytes, t)
        }

        val speed = meter.bytesPerSecond
        assertNotNull(speed)
        assertTrue("Speed $speed should be near 1048576", speed!! in 1048570L..1048580L)
    }

    @Test
    fun testEtaRoundingUp() {
        val meter = SpeedMeter()
        meter.sample(0, 0)
        meter.sample(1000, 1000) // speed = 1000 B/s

        assertEquals(1L, meter.etaSeconds(1000))
        assertEquals(2L, meter.etaSeconds(1001)) // 1001 / 1000 = 1.001 -> rounds up to 2
        assertEquals(1L, meter.etaSeconds(1)) // 1 / 1000 = 0.001 -> rounds up to 1
        assertEquals(0L, meter.etaSeconds(0))
        assertNull(meter.etaSeconds(-5))
    }

    @Test
    fun testReset() {
        val meter = SpeedMeter()
        meter.sample(0, 0)
        meter.sample(1000, 1000)
        assertNotNull(meter.bytesPerSecond)

        meter.reset()
        assertNull(meter.bytesPerSecond)
        assertNull(meter.etaSeconds(500))

        // After reset, needs two new samples to report speed
        meter.sample(1000, 2000)
        assertNull(meter.bytesPerSecond)
        meter.sample(2000, 3000)
        assertEquals(1000L, meter.bytesPerSecond)
    }
}
