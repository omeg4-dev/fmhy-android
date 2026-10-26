package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageOffsetTest {

    @Test
    fun pageOffsetReturnsZeroWhenHidden() {
        assertEquals(0f, pageOffsetFor(barHidden = true, barHeightPx = 100f), 0.001f)
        assertEquals(0f, pageOffsetFor(barHidden = true, barHeightPx = 0f), 0.001f)
    }

    @Test
    fun pageOffsetReturnsBarHeightWhenShown() {
        assertEquals(100f, pageOffsetFor(barHidden = false, barHeightPx = 100f), 0.001f)
        assertEquals(0f, pageOffsetFor(barHidden = false, barHeightPx = 0f), 0.001f)
        assertEquals(0f, pageOffsetFor(barHidden = false, barHeightPx = -20f), 0.001f)
    }

    @Test
    fun compensationOnHideReturnsNegativeH() {
        // Hiding: oldTy = H (100f), newTy = 0f -> compensation is -100 (-H)
        assertEquals(-100, compensationFor(oldTy = 100f, newTy = 0f))
        assertEquals(-56, compensationFor(oldTy = 56.4f, newTy = 0f))
    }

    @Test
    fun compensationOnShowReturnsPositiveH() {
        // Showing: oldTy = 0f, newTy = H (100f) -> compensation is +100 (+H)
        assertEquals(100, compensationFor(oldTy = 0f, newTy = 100f))
        assertEquals(56, compensationFor(oldTy = 0f, newTy = 56.4f))
    }

    @Test
    fun compensationOnNoOpReturnsZero() {
        assertEquals(0, compensationFor(oldTy = 100f, newTy = 100f))
        assertEquals(0, compensationFor(oldTy = 0f, newTy = 0f))
    }

    @Test
    fun canHideTopBarChecksScrollPosition() {
        assertTrue(canHideTopBar(scrollY = 100, barHeightPx = 100f))
        assertTrue(canHideTopBar(scrollY = 150, barHeightPx = 100f))
        assertFalse(canHideTopBar(scrollY = 99, barHeightPx = 100f))
        assertFalse(canHideTopBar(scrollY = 0, barHeightPx = 100f))
        assertTrue(canHideTopBar(scrollY = 0, barHeightPx = 0f))
    }
}
