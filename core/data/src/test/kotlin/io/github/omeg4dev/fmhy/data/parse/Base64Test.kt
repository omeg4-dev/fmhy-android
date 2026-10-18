package io.github.omeg4dev.fmhy.data.parse

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Base64Test {

    @Test
    fun testDirectUrl() {
        val url = "https://example.com/test"
        assertEquals(url, Base64Map.resolveB64String(url))
    }

    @Test
    fun testSingleDecode() {
        val url = "https://example.com/test"
        val encoded = Base64.getEncoder().encodeToString(url.toByteArray(Charsets.UTF_8))
        assertEquals(url, Base64Map.resolveB64String(encoded))
    }

    @Test
    fun testDoubleDecode() {
        val url = "https://example.com/double"
        val inner = Base64.getEncoder().encodeToString(url.toByteArray(Charsets.UTF_8))
        val outer = Base64.getEncoder().encodeToString(inner.toByteArray(Charsets.UTF_8))
        assertEquals(url, Base64Map.resolveB64String(outer))
    }

    @Test
    fun testTripleDecode() {
        val url = "https://example.com/triple"
        val level1 = Base64.getEncoder().encodeToString(url.toByteArray(Charsets.UTF_8))
        val level2 = Base64.getEncoder().encodeToString(level1.toByteArray(Charsets.UTF_8))
        val level3 = Base64.getEncoder().encodeToString(level2.toByteArray(Charsets.UTF_8))
        assertEquals(url, Base64Map.resolveB64String(level3))
    }

    @Test
    fun testUnresolvable() {
        assertNull(Base64Map.resolveB64String("not-base64-or-url!!!"))
    }

    @Test
    fun testResolverFromHtml() {
        val url = "https://example.com/resolved"
        val encoded = Base64.getEncoder().encodeToString(url.toByteArray(Charsets.UTF_8))
        val html = """
            <html><body>
            <h4 id="testanchor">Test</h4>
            <ul><li><code>$encoded</code></li></ul>
            </body></html>
        """.trimIndent()
        val resolver = Base64Map(html)
        val (resolvedUrl, origUrl, mirrors) = resolver.resolve("https://rentry.co/FMHYB64#testanchor", emptyList())
        assertEquals(url, resolvedUrl)
        assertEquals("https://rentry.co/FMHYB64#testanchor", origUrl)
        assertEquals(0, mirrors.size)
        assertEquals(1, resolver.resolvedAnchors)
    }

    @Test
    fun testUnknownAnchor() {
        val resolver = Base64Map("<html></html>")
        val (resolvedUrl, origUrl, _) = resolver.resolve("https://rentry.co/FMHYB64#unknown", emptyList())
        assertEquals("https://rentry.co/FMHYB64#unknown", resolvedUrl)
        assertNull(origUrl)
    }

    @Test
    fun testRentryOrgVariant() {
        val url = "https://example.com/org"
        val encoded = Base64.getEncoder().encodeToString(url.toByteArray(Charsets.UTF_8))
        val html = """<html><body><h4 id="orgtest">T</h4><ul><li><code>$encoded</code></li></ul></body></html>"""
        val resolver = Base64Map(html)
        val (resolvedUrl, origUrl, _) = resolver.resolve("https://rentry.org/FMHYB64#orgtest", emptyList())
        assertEquals(url, resolvedUrl)
        assertEquals("https://rentry.org/FMHYB64#orgtest", origUrl)
    }
}
