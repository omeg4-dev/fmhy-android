package io.github.omeg4dev.fmhy.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSwipeTest {

    private val width = 1000f
    private val min = 72f
    // threshold = max(0.25 * 1000, 72) = 250f

    @Test
    fun nextAndPreviousTabInLtr() {
        // Drag end-to-start (negative in LTR) moves to next tab
        val nextFromBrowse = screenSwipeTarget(
            dragPx = -300f,
            widthPx = width,
            minPx = min,
            current = Tab.BROWSE,
            rtl = false,
            enabled = true,
        )
        assertEquals(Tab.SEARCH, nextFromBrowse)

        val nextFromSearch = screenSwipeTarget(
            dragPx = -300f,
            widthPx = width,
            minPx = min,
            current = Tab.SEARCH,
            rtl = false,
            enabled = true,
        )
        assertEquals(Tab.SAVED, nextFromSearch)

        // Drag start-to-end (positive in LTR) moves to previous tab
        val prevFromSaved = screenSwipeTarget(
            dragPx = 300f,
            widthPx = width,
            minPx = min,
            current = Tab.SAVED,
            rtl = false,
            enabled = true,
        )
        assertEquals(Tab.SEARCH, prevFromSaved)

        val prevFromSearch = screenSwipeTarget(
            dragPx = 300f,
            widthPx = width,
            minPx = min,
            current = Tab.SEARCH,
            rtl = false,
            enabled = true,
        )
        assertEquals(Tab.BROWSE, prevFromSearch)
    }

    @Test
    fun noWrapAtEnds() {
        // At BROWSE (first), dragging right (prev) returns null (no wrap)
        val prevAtBrowse = screenSwipeTarget(
            dragPx = 300f,
            widthPx = width,
            minPx = min,
            current = Tab.BROWSE,
            rtl = false,
            enabled = true,
        )
        assertNull(prevAtBrowse)

        // At SAVED (last), dragging left (next) returns null (no wrap)
        val nextAtSaved = screenSwipeTarget(
            dragPx = -300f,
            widthPx = width,
            minPx = min,
            current = Tab.SAVED,
            rtl = false,
            enabled = true,
        )
        assertNull(nextAtSaved)
    }

    @Test
    fun underThreshold() {
        // Drag magnitude < threshold (250f) returns null
        assertNull(
            screenSwipeTarget(
                dragPx = -150f,
                widthPx = width,
                minPx = min,
                current = Tab.SEARCH,
                rtl = false,
                enabled = true,
            )
        )
        assertNull(
            screenSwipeTarget(
                dragPx = 150f,
                widthPx = width,
                minPx = min,
                current = Tab.SEARCH,
                rtl = false,
                enabled = true,
            )
        )
        assertNull(
            screenSwipeTarget(
                dragPx = 0f,
                widthPx = width,
                minPx = min,
                current = Tab.SEARCH,
                rtl = false,
                enabled = true,
            )
        )
        // Check with minPx when width is small (0.25 * 100 = 25 < min 72 -> threshold = 72)
        assertNull(
            screenSwipeTarget(
                dragPx = 50f,
                widthPx = 100f,
                minPx = min,
                current = Tab.SEARCH,
                rtl = false,
                enabled = true,
            )
        )
    }

    @Test
    fun disabled() {
        // When enabled = false, always returns null even with large drag
        val result = screenSwipeTarget(
            dragPx = -500f,
            widthPx = width,
            minPx = min,
            current = Tab.BROWSE,
            rtl = false,
            enabled = false,
        )
        assertNull(result)

        val prevResult = screenSwipeTarget(
            dragPx = 500f,
            widthPx = width,
            minPx = min,
            current = Tab.SAVED,
            rtl = false,
            enabled = false,
        )
        assertNull(prevResult)
    }

    @Test
    fun rtlMirroring() {
        // In RTL, end-to-start is left-to-right (positive dragPx) -> next
        val nextRtl = screenSwipeTarget(
            dragPx = 300f,
            widthPx = width,
            minPx = min,
            current = Tab.BROWSE,
            rtl = true,
            enabled = true,
        )
        assertEquals(Tab.SEARCH, nextRtl)

        // In RTL, start-to-end is right-to-left (negative dragPx) -> prev
        val prevRtl = screenSwipeTarget(
            dragPx = -300f,
            widthPx = width,
            minPx = min,
            current = Tab.SAVED,
            rtl = true,
            enabled = true,
        )
        assertEquals(Tab.SEARCH, prevRtl)

        // No wrap in RTL:
        // At BROWSE, prev (negative drag) -> null
        assertNull(
            screenSwipeTarget(
                dragPx = -300f,
                widthPx = width,
                minPx = min,
                current = Tab.BROWSE,
                rtl = true,
                enabled = true,
            )
        )
        // At SAVED, next (positive drag) -> null
        assertNull(
            screenSwipeTarget(
                dragPx = 300f,
                widthPx = width,
                minPx = min,
                current = Tab.SAVED,
                rtl = true,
                enabled = true,
            )
        )
    }

    @Test
    fun angleRuleHorizontalIntent() {
        // abs(dx) > 2 * abs(dy)
        assertTrue(isHorizontalIntent(dx = 21f, dy = 10f))
        assertFalse(isHorizontalIntent(dx = 20f, dy = 10f))
        assertFalse(isHorizontalIntent(dx = 19f, dy = 10f))

        // Negative values
        assertTrue(isHorizontalIntent(dx = -21f, dy = 10f))
        assertTrue(isHorizontalIntent(dx = 21f, dy = -10f))
        assertTrue(isHorizontalIntent(dx = -21f, dy = -10f))
        assertFalse(isHorizontalIntent(dx = -20f, dy = 10f))

        // Zeroes
        assertFalse(isHorizontalIntent(dx = 0f, dy = 0f))
        assertTrue(isHorizontalIntent(dx = 1f, dy = 0f))
        assertFalse(isHorizontalIntent(dx = 0f, dy = 1f))
    }
}
