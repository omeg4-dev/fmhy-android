package io.github.omeg4dev.fmhy.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserBarItemTest {
    @Test
    fun badgeHiddenAtZero() {
        assertNull(browserBadgeText(0))
        assertNull(browserBadgeText(-1))
    }

    @Test
    fun badgeShowsCountUpToNineThenCaps() {
        assertEquals("1", browserBadgeText(1))
        assertEquals("9", browserBadgeText(9))
        assertEquals("9+", browserBadgeText(10))
        assertEquals("9+", browserBadgeText(42))
    }
}
