package io.github.omeg4dev.fmhy.nav

import io.github.omeg4dev.fmhy.data.Appearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarStyleTest {

    @Test
    fun fromName_invalidReturnsClassic() {
        assertEquals(BottomBarStyle.CLASSIC, BottomBarStyle.fromName(null))
        assertEquals(BottomBarStyle.CLASSIC, BottomBarStyle.fromName(""))
        assertEquals(BottomBarStyle.CLASSIC, BottomBarStyle.fromName("junk"))
        assertEquals(BottomBarStyle.CLASSIC, BottomBarStyle.fromName("unknown_style"))
    }

    @Test
    fun fromName_roundTripsEachValidName() {
        for (style in BottomBarStyle.entries) {
            assertEquals(style, BottomBarStyle.fromName(style.name))
        }
    }

    @Test
    fun metricsPerStyle() {
        val classic = BottomBarStyle.CLASSIC.metrics()
        assertEquals(0, classic.topCornerDp)
        assertFalse(classic.floating)
        assertEquals(0, classic.horizontalMarginDp)
        assertEquals(0, classic.bottomMarginDp)
        assertNull(classic.maxWidthDp)
        assertEquals(80, classic.heightDp)
        assertTrue(classic.alwaysShowLabel)
        assertEquals(0, classic.shadowDp)

        val roundedTop = BottomBarStyle.ROUNDED_TOP.metrics()
        assertEquals(24, roundedTop.topCornerDp)
        assertFalse(roundedTop.floating)
        assertEquals(0, roundedTop.horizontalMarginDp)
        assertEquals(0, roundedTop.bottomMarginDp)
        assertNull(roundedTop.maxWidthDp)
        assertEquals(80, roundedTop.heightDp)
        assertTrue(roundedTop.alwaysShowLabel)
        assertEquals(0, roundedTop.shadowDp)

        val floating = BottomBarStyle.FLOATING.metrics()
        assertEquals(32, floating.topCornerDp)
        assertTrue(floating.floating)
        assertEquals(16, floating.horizontalMarginDp)
        assertEquals(8, floating.bottomMarginDp)
        assertEquals(480, floating.maxWidthDp)
        assertEquals(72, floating.heightDp)
        assertTrue(floating.alwaysShowLabel)
        assertEquals(6, floating.shadowDp)

        val selectedOnly = BottomBarStyle.SELECTED_LABEL_ONLY.metrics()
        assertEquals(0, selectedOnly.topCornerDp)
        assertFalse(selectedOnly.floating)
        assertEquals(0, selectedOnly.horizontalMarginDp)
        assertEquals(0, selectedOnly.bottomMarginDp)
        assertNull(selectedOnly.maxWidthDp)
        assertEquals(64, selectedOnly.heightDp)
        assertFalse(selectedOnly.alwaysShowLabel)
        assertEquals(0, selectedOnly.shadowDp)
    }

    @Test
    fun contentPaddingDp() {
        val classicMetrics = BottomBarStyle.CLASSIC.metrics()
        assertEquals(80, classicMetrics.contentPaddingDp())

        val roundedTopMetrics = BottomBarStyle.ROUNDED_TOP.metrics()
        assertEquals(80, roundedTopMetrics.contentPaddingDp())

        val floatingMetrics = BottomBarStyle.FLOATING.metrics()
        assertEquals(88, floatingMetrics.contentPaddingDp())
        assertTrue(floatingMetrics.contentPaddingDp() > floatingMetrics.heightDp)

        val selectedOnlyMetrics = BottomBarStyle.SELECTED_LABEL_ONLY.metrics()
        assertEquals(64, selectedOnlyMetrics.contentPaddingDp())
    }

    @Test
    fun defaultAppearanceBottomBarStyle() {
        assertEquals(BottomBarStyle.CLASSIC, Appearance().bottomBarStyle)
    }
}
