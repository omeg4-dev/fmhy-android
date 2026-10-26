package io.github.omeg4dev.fmhy.browser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRequestTest {

    @Test
    fun testHeadersAllFields() {
        val h = DownloadRequest.headers(
            userAgent = "Mozilla/5.0",
            cookies = "sid=123",
            referer = "https://example.com/page#section",
            offset = 500,
            etag = "\"abc\"",
            lastModified = "Wed, 21 Oct 2025 07:28:00 GMT",
        )
        assertEquals("Mozilla/5.0", h["User-Agent"])
        assertEquals("sid=123", h["Cookie"])
        assertEquals("https://example.com/page", h["Referer"])
        assertEquals("bytes=500-", h["Range"])
        assertEquals("\"abc\"", h["If-Range"])
    }

    @Test
    fun testHeadersBlankUaAndCookieOmitted() {
        val h = DownloadRequest.headers(
            userAgent = "   ",
            cookies = "",
            referer = "http://example.com/a",
            offset = 0,
            etag = null,
            lastModified = null,
        )
        assertFalse(h.containsKey("User-Agent"))
        assertFalse(h.containsKey("Cookie"))
        assertEquals("http://example.com/a", h["Referer"])
    }

    @Test
    fun testHeadersRefererInvalidOmitted() {
        for (ref in listOf("javascript:alert(1)", "about:blank", "not a uri :// !!", "ftp://example.com/file")) {
            val h = DownloadRequest.headers("UA", null, ref, 0, null, null)
            assertFalse("Expected referer '$ref' to be omitted", h.containsKey("Referer"))
        }
    }

    @Test
    fun testHeadersRefererFragmentStripped() {
        val h = DownloadRequest.headers("UA", null, "https://example.com/path?query=1#hash", 0, null, null)
        assertEquals("https://example.com/path?query=1", h["Referer"])
    }

    @Test
    fun testHeadersOffsetZeroNoRange() {
        val h = DownloadRequest.headers("UA", null, null, 0, "\"etag\"", "yesterday")
        assertFalse(h.containsKey("Range"))
        assertFalse(h.containsKey("If-Range"))
    }

    @Test
    fun testHeadersOffsetWithEtag() {
        val h = DownloadRequest.headers(null, null, null, 500, "\"etag123\"", "yesterday")
        assertEquals("bytes=500-", h["Range"])
        assertEquals("\"etag123\"", h["If-Range"])
    }

    @Test
    fun testHeadersOffsetWithLastModifiedWhenEtagBlank() {
        val h = DownloadRequest.headers(null, null, null, 1024, "   ", "Sun, 01 Jan 2026 00:00:00 GMT")
        assertEquals("bytes=1024-", h["Range"])
        assertEquals("Sun, 01 Jan 2026 00:00:00 GMT", h["If-Range"])
    }

    @Test
    fun testHeadersOffsetBothBlankNoIfRange() {
        val h = DownloadRequest.headers(null, null, null, 256, "", null)
        assertEquals("bytes=256-", h["Range"])
        assertFalse(h.containsKey("If-Range"))
    }

    @Test
    fun testHeadersKeyOrdering() {
        val h = DownloadRequest.headers("UA", "cookie", "https://x.org/", 10, "\"etag\"", null)
        val keys = h.keys.toList()
        assertEquals(listOf("User-Agent", "Cookie", "Referer", "Range", "If-Range"), keys)
    }

    @Test
    fun testFileNameContentDispositionRfc5987Utf8() {
        val cd = "attachment; filename*=UTF-8''na%C3%AFve%20file.pdf"
        val name = DownloadRequest.fileName("https://x.org/get", cd, null)
        assertEquals("naïve file.pdf", name)
    }

    @Test
    fun testFileNameContentDispositionStarWinsOverNormal() {
        val cd = "attachment; filename*=UTF-8''na%C3%AFve%20file.pdf; filename=\"fallback.pdf\""
        val name = DownloadRequest.fileName("https://x.org/get", cd, null)
        assertEquals("naïve file.pdf", name)
    }

    @Test
    fun testFileNameContentDispositionQuoted() {
        val cd = "attachment; filename=\"My Document.pdf\"; size=123"
        val name = DownloadRequest.fileName("https://x.org/download", cd, null)
        assertEquals("My Document.pdf", name)
    }

    @Test
    fun testFileNameContentDispositionUnquotedWithSizeParam() {
        val cd = "attachment; filename=document.pdf; size=1"
        val name = DownloadRequest.fileName("https://x.org/download", cd, null)
        assertEquals("document.pdf", name)
    }

    @Test
    fun testFileNameFromUrlPathSegment() {
        val url = "https://x.org/a/b/My%20App.apk?x=1#y"
        val name = DownloadRequest.fileName(url, null, null)
        assertEquals("My App.apk", name)
    }

    @Test
    fun testFileNameUrlPlusSignPreserved() {
        val url = "https://x.org/path/file+name+test.txt"
        val name = DownloadRequest.fileName(url, null, null)
        assertEquals("file+name+test.txt", name)
    }

    @Test
    fun testFileNameUrlNoSegmentFallsBackToDownload() {
        val urls = listOf("https://x.org", "https://x.org/", "https://x.org/?foo=bar")
        for (u in urls) {
            val name = DownloadRequest.fileName(u, null, null)
            assertEquals("download", name)
        }
    }

    @Test
    fun testFileNameMimeExtensionAppendedWhenNoExt() {
        val name = DownloadRequest.fileName("https://x.org/get", null, "application/pdf")
        assertEquals("get.pdf", name)
    }

    @Test
    fun testFileNameMimeWithCharsetIgnored() {
        val name = DownloadRequest.fileName("https://x.org/notes", null, "text/plain; charset=utf-8")
        assertEquals("notes.txt", name)
    }

    @Test
    fun testFileNameMimeOctetStreamAddsNothing() {
        val name = DownloadRequest.fileName("https://x.org/binary", null, "application/octet-stream")
        assertEquals("binary", name)
    }

    @Test
    fun testFileNameSanitizesInvalidChars() {
        val cd = "attachment; filename=\"a/b:c?.zip\""
        val name = DownloadRequest.fileName("https://x.org/download", cd, null)
        assertEquals("a_b_c_.zip", name)
    }

    @Test
    fun testFileNameSanitizeTrimsLeadingAndTrailingDots() {
        val name = DownloadRequest.fileName("https://x.org/...test...txt...", null, null)
        assertEquals("test...txt", name)
    }

    @Test
    fun testFileNameLongNameCappedAt120KeepingExt() {
        val longBase = "A".repeat(300)
        val name = DownloadRequest.fileName("https://x.org/$longBase.apk", null, null)
        assertEquals(120, name.length)
        assertTrue(name.endsWith(".apk"))
        assertEquals("A".repeat(116) + ".apk", name)
    }

    @Test
    fun testUniqueNameFreeName() {
        val res = DownloadRequest.uniqueName("hello.txt") { false }
        assertEquals("hello.txt", res)
    }

    @Test
    fun testUniqueNameCollisions() {
        val existing = setOf("a.zip", "a (1).zip")
        val res = DownloadRequest.uniqueName("a.zip") { it in existing }
        assertEquals("a (2).zip", res)
    }

    @Test
    fun testUniqueNameTarGzDoubleExtension() {
        val existing = setOf("a.tar.gz")
        val res = DownloadRequest.uniqueName("a.tar.gz") { it in existing }
        assertEquals("a (1).tar.gz", res)
    }

    @Test
    fun testUniqueNameLeadingDotOnlyNoExt() {
        val existing = setOf(".bashrc")
        val res = DownloadRequest.uniqueName(".bashrc") { it in existing }
        assertEquals(".bashrc (1)", res)
    }

    @Test
    fun testUniqueNameNoDotNoExt() {
        val existing = setOf("README")
        val res = DownloadRequest.uniqueName("README") { it in existing }
        assertEquals("README (1)", res)
    }

    @Test
    fun testResumeDecisionBranches() {
        // offset == 0 -> RESTART
        assertEquals(ResumeDecision.RESTART, DownloadRequest.resumeDecision(0, 200, null))
        assertEquals(ResumeDecision.RESTART, DownloadRequest.resumeDecision(0, 206, 0))

        // 206 with start == offset -> APPEND
        assertEquals(ResumeDecision.APPEND, DownloadRequest.resumeDecision(500, 206, 500))

        // 416 -> DONE
        assertEquals(ResumeDecision.DONE, DownloadRequest.resumeDecision(500, 416, null))

        // 206 with mismatched start -> RESTART
        assertEquals(ResumeDecision.RESTART, DownloadRequest.resumeDecision(500, 206, 100))

        // 200 with offset > 0 -> RESTART
        assertEquals(ResumeDecision.RESTART, DownloadRequest.resumeDecision(500, 200, null))

        // 500 -> RESTART
        assertEquals(ResumeDecision.RESTART, DownloadRequest.resumeDecision(500, 500, null))
    }

    @Test
    fun testParseContentRangeStartAndTotal() {
        assertEquals(100L, DownloadRequest.parseContentRangeStart("bytes 100-199/200"))
        assertEquals(200L, DownloadRequest.parseContentRangeTotal("bytes 100-199/200"))

        assertNull(DownloadRequest.parseContentRangeStart("bytes */200"))
        assertEquals(200L, DownloadRequest.parseContentRangeTotal("bytes */200"))

        assertEquals(100L, DownloadRequest.parseContentRangeStart("bytes 100-199/*"))
        assertNull(DownloadRequest.parseContentRangeTotal("bytes 100-199/*"))

        assertNull(DownloadRequest.parseContentRangeStart("/*"))
        assertNull(DownloadRequest.parseContentRangeTotal("/*"))

        assertNull(DownloadRequest.parseContentRangeStart("junk"))
        assertNull(DownloadRequest.parseContentRangeTotal("junk"))

        assertNull(DownloadRequest.parseContentRangeStart(null))
        assertNull(DownloadRequest.parseContentRangeTotal(null))
    }

    @Test
    fun testHostOf() {
        assertEquals("example.com", DownloadRequest.hostOf("https://example.com/foo"))
        assertEquals("www.example.com", DownloadRequest.hostOf("http://www.Example.com:8080/a"))
        assertEquals("", DownloadRequest.hostOf("not a url"))
        assertEquals("", DownloadRequest.hostOf("example.com/foo"))
    }

    @Test
    fun testIsHttpUrl() {
        assertTrue(DownloadRequest.isHttpUrl("https://example.com"))
        assertTrue(DownloadRequest.isHttpUrl("http://localhost:8080/path"))
        assertFalse(DownloadRequest.isHttpUrl("ftp://files.org/test"))
        assertFalse(DownloadRequest.isHttpUrl("example.com"))
        assertFalse(DownloadRequest.isHttpUrl("https://"))
        assertFalse(DownloadRequest.isHttpUrl("   "))
    }
}
