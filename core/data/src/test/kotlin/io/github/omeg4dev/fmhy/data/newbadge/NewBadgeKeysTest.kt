package io.github.omeg4dev.fmhy.data.newbadge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NewBadgeKeysTest {

    @Test
    fun urlKey_dropsScheme() {
        assertEquals("foo.bar", NewBadgeKeys.urlKey("https://foo.bar"))
        assertEquals("foo.bar", NewBadgeKeys.urlKey("http://foo.bar"))
    }

    @Test
    fun urlKey_dropsWww() {
        assertEquals("foo.bar/baz", NewBadgeKeys.urlKey("https://www.foo.bar/baz"))
        assertEquals("foo.bar", NewBadgeKeys.urlKey("http://www.foo.bar"))
    }

    @Test
    fun urlKey_dropsTrailingSlash() {
        assertEquals("foo.bar/baz", NewBadgeKeys.urlKey("https://foo.bar/baz/"))
        assertEquals("foo.bar", NewBadgeKeys.urlKey("https://foo.bar/"))
    }

    @Test
    fun urlKey_stripsTrackingParameters() {
        assertEquals("foo.bar", NewBadgeKeys.urlKey("https://foo.bar?ref=123&fbclid=456"))
        assertEquals("foo.bar/path", NewBadgeKeys.urlKey("https://foo.bar/path?ref=abc"))
    }

    @Test
    fun urlKey_keepsNonTrackingParameters() {
        assertEquals("foo.bar?search=test&page=2", NewBadgeKeys.urlKey("https://foo.bar?search=test&page=2"))
    }

    @Test
    fun urlKey_keepsFragment() {
        assertEquals("rentry.co/FMHYB64#wavernrs", NewBadgeKeys.urlKey("https://rentry.co/FMHYB64#wavernrs"))
        assertEquals("example.com/doc#section-1", NewBadgeKeys.urlKey("https://www.example.com/doc/#section-1"))
    }

    @Test
    fun urlKey_differentFragmentsProduceDifferentKeys() {
        val keyA = NewBadgeKeys.urlKey("https://rentry.co/FMHYB64#a")
        val keyB = NewBadgeKeys.urlKey("https://rentry.co/FMHYB64#b")
        assertNotEquals(keyA, keyB)
        assertEquals("rentry.co/FMHYB64#a", keyA)
        assertEquals("rentry.co/FMHYB64#b", keyB)
    }

    @Test
    fun pageKey_combinesSectionAndUrlKey() {
        assertEquals(
            "audio|example.com/site",
            NewBadgeKeys.pageKey("audio", "https://www.example.com/site/"),
        )
        assertEquals(
            "video|example.com/site",
            NewBadgeKeys.pageKey("video", "https://www.example.com/site/"),
        )
    }

    @Test
    fun pageKey_preservesFragment() {
        assertEquals(
            "audio|rentry.co/FMHYB64#wavernrs",
            NewBadgeKeys.pageKey("audio", "https://rentry.co/FMHYB64#wavernrs"),
        )
    }
}
