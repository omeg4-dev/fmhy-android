package io.github.omeg4dev.fmhy.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class RowTapActionTest {

    @Test
    fun defaultGesturesTapOpensSiteAndHoldOpensMenu() {
        val gestures = rowGestures(swapTapHold = false)
        assertEquals(RowAction.OPEN_SITE, gestures.onTap)
        assertEquals(RowAction.OPEN_MENU, gestures.onHold)
    }

    @Test
    fun swappedGesturesTapOpensMenuAndHoldOpensSite() {
        val gestures = rowGestures(swapTapHold = true)
        assertEquals(RowAction.OPEN_MENU, gestures.onTap)
        assertEquals(RowAction.OPEN_SITE, gestures.onHold)
    }

    @Test
    fun showInSectionGesturesDefault() {
        val gestures = rowGestures(swapTapHold = false, siteAction = RowAction.SHOW_IN_SECTION)
        assertEquals(RowAction.SHOW_IN_SECTION, gestures.onTap)
        assertEquals(RowAction.OPEN_MENU, gestures.onHold)
    }

    @Test
    fun showInSectionGesturesSwapped() {
        val gestures = rowGestures(swapTapHold = true, siteAction = RowAction.SHOW_IN_SECTION)
        assertEquals(RowAction.OPEN_MENU, gestures.onTap)
        assertEquals(RowAction.SHOW_IN_SECTION, gestures.onHold)
    }
}
