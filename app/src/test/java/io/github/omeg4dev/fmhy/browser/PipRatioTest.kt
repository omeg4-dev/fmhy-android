package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipRatioTest {
    @Test
    fun fullHdIsSixteenByNine() {
        assertEquals(Ratio(16, 9), pipRatio(1920, 1080))
    }

    @Test
    fun tooTallIsClampedToOneByTwoPointThreeNine() {
        val r = pipRatio(400, 2000)
        assertEquals(Ratio(100, 239), r)
        assertTrue(r.value >= 1f / 2.39f - 0.0001f)
    }

    @Test
    fun tooWideIsClampedToTwoPointThreeNineByOne() {
        assertEquals(Ratio(239, 100), pipRatio(3000, 1000))
    }

    @Test
    fun unknownFallsBackToSixteenByNine() {
        assertEquals(Ratio(16, 9), pipRatio(0, 0))
        assertEquals(Ratio(16, 9), pipRatio(-5, 100))
    }

    @Test
    fun portraitInsideRangeIsKept() {
        assertEquals(Ratio(9, 16), pipRatio(1080, 1920))
    }
}
