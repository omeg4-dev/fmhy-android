package io.github.omeg4dev.fmhy.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdsTest {

    @Test
    fun testWwwDropped() {
        assertEquals("https://example.com", Ids.normalizeUrl("https://www.example.com/"))
    }

    @Test
    fun testDefaultPortDropped() {
        assertEquals("https://example.com/path", Ids.normalizeUrl("https://example.com:443/path"))
        assertEquals("http://example.com/path", Ids.normalizeUrl("http://example.com:80/path"))
    }

    @Test
    fun testTrailingSlashDropped() {
        assertEquals("https://example.com/path", Ids.normalizeUrl("https://example.com/path/"))
    }

    @Test
    fun testRootSlashKept() {
        val r = Ids.normalizeUrl("https://example.com/")
        assertEquals("https://example.com", r)
    }

    @Test
    fun testUtmParamsDropped() {
        val r = Ids.normalizeUrl("https://example.com/page?utm_source=x&utm_medium=y&q=1")
        assertFalse(r.contains("utm_source"))
        assertTrue(r.contains("q=1"))
    }

    @Test
    fun testRefDropped() {
        val r = Ids.normalizeUrl("https://example.com/page?ref=abc&other=1")
        assertFalse(r.contains("ref"))
        assertTrue(r.contains("other=1"))
    }

    @Test
    fun testFbclidDropped() {
        val r = Ids.normalizeUrl("https://example.com/page?fbclid=abc")
        assertFalse(r.contains("fbclid"))
    }

    @Test
    fun testFragmentKept() {
        val r = Ids.normalizeUrl("https://example.com/page#section")
        assertTrue(r.contains("#section"))
    }

    @Test
    fun testLowercase() {
        val r = Ids.normalizeUrl("HTTPS://Example.COM/Path")
        assertTrue(r.startsWith("https://example.com"))
    }

    @Test
    fun testIdAssignerDeduplication() {
        val assigner = IdAssigner()
        val id1 = assigner.assign("https://example.com", "test")
        val id2 = assigner.assign("https://example.com", "test")
        val id3 = assigner.assign("https://example.com", "test")
        assertEquals(12, id1.length)
        assertEquals("$id1-2", id2)
        assertEquals("$id1-3", id3)
    }
}
