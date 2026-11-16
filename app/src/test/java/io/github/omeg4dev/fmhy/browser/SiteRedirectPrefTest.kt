package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteRedirectPrefTest {

    @Test
    fun testStoredNullGlobalTrue() {
        assertTrue(SiteRedirectPref.effective(stored = null, global = true))
    }

    @Test
    fun testStoredNullGlobalFalse() {
        assertFalse(SiteRedirectPref.effective(stored = null, global = false))
    }

    @Test
    fun testStoredTrueGlobalTrue() {
        assertTrue(SiteRedirectPref.effective(stored = true, global = true))
    }

    @Test
    fun testStoredTrueGlobalFalse() {
        assertTrue(SiteRedirectPref.effective(stored = true, global = false))
    }

    @Test
    fun testStoredFalseGlobalTrue() {
        assertFalse(SiteRedirectPref.effective(stored = false, global = true))
    }

    @Test
    fun testStoredFalseGlobalFalse() {
        assertFalse(SiteRedirectPref.effective(stored = false, global = false))
    }

    @Test
    fun testLabel() {
        assertEquals("redirects blocked", SiteRedirectPref.label(true))
        assertEquals("redirects allowed", SiteRedirectPref.label(false))
    }
}
