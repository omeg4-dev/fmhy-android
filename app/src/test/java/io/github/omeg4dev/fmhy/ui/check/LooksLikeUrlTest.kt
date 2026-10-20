package io.github.omeg4dev.fmhy.ui.check

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LooksLikeUrlTest {

    @Test
    fun testValidUrlsAndHosts() {
        assertTrue(looksLikeUrl("example.com"))
        assertTrue(looksLikeUrl("https://example.com"))
        assertTrue(looksLikeUrl("http://sub.domain.org/path?param=1#anchor"))
        assertTrue(looksLikeUrl("x.com/a"))
        assertTrue(looksLikeUrl("localhost"))
        assertTrue(looksLikeUrl("http://localhost:8080"))
    }

    @Test
    fun testNonUrls() {
        assertFalse(looksLikeUrl("stremio"))
        assertFalse(looksLikeUrl("search query with spaces"))
        assertFalse(looksLikeUrl("in:video anime"))
        assertFalse(looksLikeUrl("is:starred"))
        assertFalse(looksLikeUrl(""))
        assertFalse(looksLikeUrl("   "))
        assertFalse(looksLikeUrl("."))
        assertFalse(looksLikeUrl(".."))
    }
}
