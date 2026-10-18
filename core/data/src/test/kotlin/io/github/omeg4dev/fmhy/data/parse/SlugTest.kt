package io.github.omeg4dev.fmhy.data.parse

import org.junit.Assert.assertEquals
import org.junit.Test

class SlugTest {

    @Test
    fun testEncodeDecode() {
        assertEquals("encode-decode", Slug.slugify("Encode / Decode"))
    }

    @Test
    fun testArrowLink() {
        assertEquals("android-streaming", Slug.slugify("▷ Android Streaming"))
    }

    @Test
    fun testSimple() {
        assertEquals("video-streaming", Slug.slugify("Video Streaming"))
    }

    @Test
    fun testLeadingEmoji() {
        assertEquals("top-sites", Slug.slugify("🌟 Top Sites"))
    }

    @Test
    fun testStarEmoji() {
        assertEquals("starred", Slug.slugify("⭐ Starred"))
    }

    @Test
    fun testDigitPrefix() {
        assertEquals("_4k-video", Slug.slugify("4K Video"))
    }

    @Test
    fun testSpecialChars() {
        assertEquals("c-programming", Slug.slugify("C++ Programming"))
    }

    @Test
    fun testMultipleMarkers() {
        assertEquals("section-name", Slug.slugify("► Section Name"))
    }

    @Test
    fun testUnicodeNormalize() {
        assertEquals("hello-world", Slug.slugify("Héllo Wörld"))
    }

    @Test
    fun testAnchorTrackerNoDuplicate() {
        val t = AnchorTracker()
        assertEquals("foo", t.add("foo"))
        assertEquals("bar", t.add("bar"))
    }

    @Test
    fun testAnchorTrackerDuplicateGetsSuffix() {
        val t = AnchorTracker()
        assertEquals("foo", t.add("foo"))
        assertEquals("foo-1", t.add("foo"))
        assertEquals("foo-2", t.add("foo"))
    }
}
