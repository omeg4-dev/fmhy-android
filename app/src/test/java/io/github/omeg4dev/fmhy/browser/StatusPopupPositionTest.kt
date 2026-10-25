package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusPopupPositionTest {

    // Common params used across tests
    private val margin = 16
    private val gap = 6
    private val caretHalf = 8
    private val popupW = 300
    private val popupH = 200
    private val windowW = 1080
    private val windowH = 2400

    // (a) Top bar, icon at the left edge → popup goes below, x == margin, caret over icon centre
    @Test
    fun topBar_iconAtLeft_popupBelowAtMargin() {
        // Anchor near the left edge
        val anchorLeft = 0
        val anchorRight = 64
        val anchorTop = 100
        val anchorBottom = 132

        val result = statusPopupPlacement(
            anchorLeft = anchorLeft, anchorTop = anchorTop,
            anchorRight = anchorRight, anchorBottom = anchorBottom,
            windowWidth = windowW, windowHeight = windowH,
            popupWidth = popupW, popupHeight = popupH,
            preferAbove = false, marginPx = margin, gapPx = gap, caretHalfPx = caretHalf,
        )

        assertEquals(false, result.above)
        assertEquals(margin, result.x)
        // caret should be over the icon centre relative to popup left
        val anchorCenter = (anchorLeft + anchorRight) / 2
        val expectedCaretX = (anchorCenter - result.x).coerceIn(caretHalf + 24, popupW - caretHalf - 24)
        assertEquals(expectedCaretX, result.caretX)
    }

    // (b) preferAbove (bottom bar) → popup goes above, y == anchorTop - gap - popupHeight
    @Test
    fun bottomBar_preferAbove_placeAboveIcon() {
        val anchorLeft = 400
        val anchorRight = 464
        val anchorTop = 2000
        val anchorBottom = 2032

        val result = statusPopupPlacement(
            anchorLeft = anchorLeft, anchorTop = anchorTop,
            anchorRight = anchorRight, anchorBottom = anchorBottom,
            windowWidth = windowW, windowHeight = windowH,
            popupWidth = popupW, popupHeight = popupH,
            preferAbove = true, marginPx = margin, gapPx = gap, caretHalfPx = caretHalf,
        )

        assertEquals(true, result.above)
        assertEquals(anchorTop - gap - popupH, result.y)
    }

    // (c) preferAbove but no room above → flip to below
    @Test
    fun preferAbove_noRoomAbove_flipToBelow() {
        // Anchor at the very top → no room above
        val anchorLeft = 400
        val anchorRight = 464
        val anchorTop = 10       // only 10 px from top → above = 10 - 6 - 200 = -196, not >= margin
        val anchorBottom = 42

        val result = statusPopupPlacement(
            anchorLeft = anchorLeft, anchorTop = anchorTop,
            anchorRight = anchorRight, anchorBottom = anchorBottom,
            windowWidth = windowW, windowHeight = windowH,
            popupWidth = popupW, popupHeight = popupH,
            preferAbove = true, marginPx = margin, gapPx = gap, caretHalfPx = caretHalf,
        )

        assertEquals(false, result.above)
        // y == anchorBottom + gap (possibly clamped, but fits here)
        assertEquals(anchorBottom + gap, result.y)
    }

    // (d) not preferAbove but no room below → flip to above
    @Test
    fun notPreferAbove_noRoomBelow_flipToAbove() {
        // Anchor near the bottom → no room below
        val anchorLeft = 400
        val anchorRight = 464
        val anchorTop = 2300
        val anchorBottom = 2332  // below + popupH = 2338 + 200 = 2538 > 2400 - 16 = 2384

        val result = statusPopupPlacement(
            anchorLeft = anchorLeft, anchorTop = anchorTop,
            anchorRight = anchorRight, anchorBottom = anchorBottom,
            windowWidth = windowW, windowHeight = windowH,
            popupWidth = popupW, popupHeight = popupH,
            preferAbove = false, marginPx = margin, gapPx = gap, caretHalfPx = caretHalf,
        )

        assertEquals(true, result.above)
        val expectedY = anchorTop - gap - popupH
        assertEquals(expectedY, result.y)
    }

    // (e) Icon at the right edge → x clamped so popup ends at windowWidth - margin
    @Test
    fun iconAtRightEdge_xClamped() {
        val anchorLeft = windowW - 64
        val anchorRight = windowW
        val anchorTop = 100
        val anchorBottom = 132

        val result = statusPopupPlacement(
            anchorLeft = anchorLeft, anchorTop = anchorTop,
            anchorRight = anchorRight, anchorBottom = anchorBottom,
            windowWidth = windowW, windowHeight = windowH,
            popupWidth = popupW, popupHeight = popupH,
            preferAbove = false, marginPx = margin, gapPx = gap, caretHalfPx = caretHalf,
        )

        val expectedX = windowW - margin - popupW
        assertEquals(expectedX, result.x)
    }

    // (f) caretX stays within the clamped range
    @Test
    fun caretX_staysInsideClampedRange() {
        // Use icon at right edge (so unclamped caretX would be large, then gets clamped)
        val anchorLeft = windowW - 64
        val anchorRight = windowW
        val anchorTop = 100
        val anchorBottom = 132

        val result = statusPopupPlacement(
            anchorLeft = anchorLeft, anchorTop = anchorTop,
            anchorRight = anchorRight, anchorBottom = anchorBottom,
            windowWidth = windowW, windowHeight = windowH,
            popupWidth = popupW, popupHeight = popupH,
            preferAbove = false, marginPx = margin, gapPx = gap, caretHalfPx = caretHalf,
        )

        assertTrue(result.caretX >= caretHalf + 24)
        assertTrue(result.caretX <= popupW - caretHalf - 24)
    }
}
