package io.github.omeg4dev.fmhy.ui.check

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlExtractTest {

    @Test
    fun testUrlExtractWithSurroundingText() {
        val input = "check this https://x.com/a lol"
        assertEquals("https://x.com/a", extractFirstUrl(input))
    }

    @Test
    fun testPlainHostExtract() {
        val input = "example.com"
        assertEquals("example.com", extractFirstUrl(input))
    }

    @Test
    fun testMultipleUrlsExtractsFirst() {
        val input = "Here is one https://first.com/a and here is another https://second.com/b"
        assertEquals("https://first.com/a", extractFirstUrl(input))
    }

    @Test
    fun testGarbageReturnsTrimmedText() {
        val input = "   garbage   "
        assertEquals("garbage", extractFirstUrl(input))
    }

    @Test
    fun testUrlWithTrailingPunctuation() {
        val input = "Check this site: https://x.com/a."
        assertEquals("https://x.com/a", extractFirstUrl(input))
    }
}
