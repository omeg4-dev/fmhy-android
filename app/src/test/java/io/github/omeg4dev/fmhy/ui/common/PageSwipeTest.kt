package io.github.omeg4dev.fmhy.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageSwipeTest {

    private val width = 1000f
    private val min = 72f
    // threshold = max(0.25 * 1000, 72) = 250f

    @Test
    fun nextAndPreviousPageInLtr() {
        // Drag end-to-start (negative drag in LTR) moves to next subsection
        val next = pageSwipeTarget(
            dragPx = -300f,
            widthPx = width,
            minPx = min,
            current = 0,
            count = 3,
            rtl = false,
        )
        assertEquals(1, next)

        // Drag start-to-end (positive drag in LTR) moves to previous subsection
        val prev = pageSwipeTarget(
            dragPx = 300f,
            widthPx = width,
            minPx = min,
            current = 2,
            count = 3,
            rtl = false,
        )
        assertEquals(1, prev)
    }

    @Test
    fun nullAtEnds() {
        // At first page, swiping right (prev) returns null
        val prevAtStart = pageSwipeTarget(
            dragPx = 300f,
            widthPx = width,
            minPx = min,
            current = 0,
            count = 3,
            rtl = false,
        )
        assertNull(prevAtStart)

        // At last page, swiping left (next) returns null
        val nextAtEnd = pageSwipeTarget(
            dragPx = -300f,
            widthPx = width,
            minPx = min,
            current = 2,
            count = 3,
            rtl = false,
        )
        assertNull(nextAtEnd)
    }

    @Test
    fun nullUnderThreshold() {
        // Drag smaller than 250f threshold returns null
        val underLeft = pageSwipeTarget(
            dragPx = -150f,
            widthPx = width,
            minPx = min,
            current = 1,
            count = 3,
            rtl = false,
        )
        assertNull(underLeft)

        val underRight = pageSwipeTarget(
            dragPx = 150f,
            widthPx = width,
            minPx = min,
            current = 1,
            count = 3,
            rtl = false,
        )
        assertNull(underRight)

        val zero = pageSwipeTarget(
            dragPx = 0f,
            widthPx = width,
            minPx = min,
            current = 1,
            count = 3,
            rtl = false,
        )
        assertNull(zero)
    }

    @Test
    fun rtlFlip() {
        // In RTL, end-to-start is left-to-right (positive dragPx) -> next subsection
        val nextRtl = pageSwipeTarget(
            dragPx = 300f,
            widthPx = width,
            minPx = min,
            current = 0,
            count = 3,
            rtl = true,
        )
        assertEquals(1, nextRtl)

        // In RTL, start-to-end is right-to-left (negative dragPx) -> previous subsection
        val prevRtl = pageSwipeTarget(
            dragPx = -300f,
            widthPx = width,
            minPx = min,
            current = 1,
            count = 3,
            rtl = true,
        )
        assertEquals(0, prevRtl)

        // Ends in RTL
        val prevAtStartRtl = pageSwipeTarget(
            dragPx = -300f,
            widthPx = width,
            minPx = min,
            current = 0,
            count = 3,
            rtl = true,
        )
        assertNull(prevAtStartRtl)

        val nextAtEndRtl = pageSwipeTarget(
            dragPx = 300f,
            widthPx = width,
            minPx = min,
            current = 2,
            count = 3,
            rtl = true,
        )
        assertNull(nextAtEndRtl)
    }

    @Test
    fun narrowWidthUsesMinThreshold() {
        // 200 * 0.25 = 50 < 72, so threshold is 72
        val under = pageSwipeTarget(
            dragPx = -60f,
            widthPx = 200f,
            minPx = 72f,
            current = 0,
            count = 2,
            rtl = false,
        )
        assertNull(under)

        val past = pageSwipeTarget(
            dragPx = -80f,
            widthPx = 200f,
            minPx = 72f,
            current = 0,
            count = 2,
            rtl = false,
        )
        assertEquals(1, past)
    }
}
