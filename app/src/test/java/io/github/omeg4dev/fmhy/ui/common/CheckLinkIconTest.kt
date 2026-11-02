package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckLinkIconTest {

    @Test
    fun testCheckLinkIcon() {
        assertEquals("CheckLink", CheckLinkIcon.name)
        assertEquals(24.dp, CheckLinkIcon.defaultWidth)
        assertEquals(24.dp, CheckLinkIcon.defaultHeight)
        assertEquals(24f, CheckLinkIcon.viewportWidth)
        assertEquals(24f, CheckLinkIcon.viewportHeight)
        assertTrue(CheckLinkIcon === CheckLinkIcon)
        assertSame(CheckLinkIcon, CheckLinkIcon)
    }
}
