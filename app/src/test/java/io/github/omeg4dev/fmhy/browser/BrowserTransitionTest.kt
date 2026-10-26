package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTransitionTest {

    @Test
    fun chooseOpenStyleNullBoundsReturnsRiseWhenAnimationsOn() {
        val style = chooseOpenStyle(originBounds = null, animationsOn = true)
        assertEquals(OpenStyle.RISE, style)
    }

    @Test
    fun chooseOpenStyleEmptyBoundsReturnsRiseWhenAnimationsOn() {
        val zeroBounds = OriginBounds(0, 0, 0, 0)
        assertEquals(OpenStyle.RISE, chooseOpenStyle(zeroBounds, animationsOn = true))

        val zeroWidth = OriginBounds(10, 10, 10, 50)
        assertEquals(OpenStyle.RISE, chooseOpenStyle(zeroWidth, animationsOn = true))

        val zeroHeight = OriginBounds(10, 10, 50, 10)
        assertEquals(OpenStyle.RISE, chooseOpenStyle(zeroHeight, animationsOn = true))

        val inverted = OriginBounds(50, 50, 10, 10)
        assertEquals(OpenStyle.RISE, chooseOpenStyle(inverted, animationsOn = true))
    }

    @Test
    fun chooseOpenStyleValidBoundsReturnsGrowWhenAnimationsOn() {
        val bounds = OriginBounds(10, 20, 110, 220)
        assertEquals(OpenStyle.GROW_FROM_BOUNDS, chooseOpenStyle(bounds, animationsOn = true))
    }

    @Test
    fun chooseOpenStyleAnimationsOffReturnsCrossfadeRegardless() {
        assertEquals(OpenStyle.CROSSFADE, chooseOpenStyle(originBounds = null, animationsOn = false))
        assertEquals(OpenStyle.CROSSFADE, chooseOpenStyle(OriginBounds(0, 0, 0, 0), animationsOn = false))
        assertEquals(OpenStyle.CROSSFADE, chooseOpenStyle(OriginBounds(10, 20, 110, 220), animationsOn = false))
    }

    @Test
    fun usePredictiveBackReturnsTrueOnlyForReturnToFmhyOnSdk34Plus() {
        assertTrue(usePredictiveBack(BackAction.RETURN_TO_FMHY, sdk = 34))
        assertTrue(usePredictiveBack(BackAction.RETURN_TO_FMHY, sdk = 35))
        assertFalse(usePredictiveBack(BackAction.RETURN_TO_FMHY, sdk = 33))
        assertFalse(usePredictiveBack(BackAction.RETURN_TO_FMHY, sdk = 26))

        // Other actions return false regardless of SDK
        for (action in BackAction.entries) {
            if (action != BackAction.RETURN_TO_FMHY) {
                assertFalse(usePredictiveBack(action, sdk = 34))
                assertFalse(usePredictiveBack(action, sdk = 33))
            }
        }
    }
}
