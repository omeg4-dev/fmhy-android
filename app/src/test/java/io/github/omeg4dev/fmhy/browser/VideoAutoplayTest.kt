package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.AutoplayMode
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAutoplayTest {
    @Test
    fun defaultIsMutedOnly() {
        assertEquals(AutoplayMode.ALLOW_MUTED, BrowserPrefs().autoplay)
    }

    @Test
    fun onlyBlockRequiresAGestureInTheWebView() {
        assertTrue(autoplayNeedsGesture(AutoplayMode.BLOCK))
        assertFalse(autoplayNeedsGesture(AutoplayMode.ALLOW_MUTED))
        assertFalse(autoplayNeedsGesture(AutoplayMode.ALLOW))
    }

    @Test
    fun pageGuardOnlyRunsForMutedMode() {
        assertEquals("muted", autoplayScriptMode(AutoplayMode.ALLOW_MUTED))
        assertEquals("any", autoplayScriptMode(AutoplayMode.ALLOW))
        assertEquals("any", autoplayScriptMode(AutoplayMode.BLOCK))
    }

    @Test
    fun labelsMatchTheSpec() {
        assertEquals(
            listOf("Never", "Muted only", "Always"),
            AutoplayMode.entries.map { it.label },
        )
    }
}
