package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlInputTest {

    @Test
    fun testUrlsWithScheme() {
        val engine = SearchEngine.DUCKDUCKGO
        assertEquals("https://fmhy.net", UrlInput.resolve("https://fmhy.net", engine))
        assertEquals("http://example.org/path?k=v", UrlInput.resolve("http://example.org/path?k=v", engine))
        assertEquals("ftp://ftp.example.com", UrlInput.resolve("ftp://ftp.example.com", engine))
    }

    @Test
    fun testHostsWithoutScheme() {
        val engine = SearchEngine.DUCKDUCKGO
        assertEquals("https://fmhy.net", UrlInput.resolve("fmhy.net", engine))
        assertEquals("https://sub.domain.example.com/page", UrlInput.resolve("sub.domain.example.com/page", engine))
    }

    @Test
    fun testIps() {
        val engine = SearchEngine.DUCKDUCKGO
        assertEquals("https://127.0.0.1", UrlInput.resolve("127.0.0.1", engine))
        assertEquals("https://192.168.1.1:8080/admin", UrlInput.resolve("192.168.1.1:8080/admin", engine))
    }

    @Test
    fun testLocalhost() {
        val engine = SearchEngine.DUCKDUCKGO
        assertEquals("http://localhost:8000", UrlInput.resolve("localhost:8000", engine))
        assertEquals("http://localhost", UrlInput.resolve("localhost", engine))
    }

    @Test
    fun testAboutBlank() {
        val engine = SearchEngine.DUCKDUCKGO
        assertEquals("about:blank", UrlInput.resolve("about:blank", engine))
    }

    @Test
    fun testSearchText() {
        val engine = SearchEngine.DUCKDUCKGO
        assertEquals("https://duckduckgo.com/?q=fmhy+android", UrlInput.resolve("fmhy android", engine))
        assertEquals("https://duckduckgo.com/?q=reddit", UrlInput.resolve("reddit", engine))
        assertEquals("https://duckduckgo.com/?q=what+is+this%3F", UrlInput.resolve("what is this?", engine))
    }
}
