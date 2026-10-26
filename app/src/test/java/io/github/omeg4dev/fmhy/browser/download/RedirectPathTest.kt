package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.browser.WarningReason
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedirectPathTest {

    @Test
    fun testNextUrlRedirectCodesAbsolute() {
        val current = "https://example.com/start".toHttpUrl()
        val target = "https://redirected.org/file.bin"
        val expected = target.toHttpUrl()

        for (code in listOf(301, 302, 303, 307, 308)) {
            val next = RedirectPath.nextUrl(current, target, code)
            assertEquals("Code $code should follow absolute redirect", expected, next)
        }
    }

    @Test
    fun testNextUrlRedirectCodesRelative() {
        val current = "https://example.com/downloads/item".toHttpUrl()
        val expected = "https://example.com/downloads/dest.zip".toHttpUrl()

        for (code in listOf(301, 302, 303, 307, 308)) {
            val next = RedirectPath.nextUrl(current, "dest.zip", code)
            assertEquals("Code $code should resolve relative redirect", expected, next)
        }
    }

    @Test
    fun testNextUrlRelativeResolvesAgainstCurrentPath() {
        val currentWithSlash = "https://example.com/a/b/".toHttpUrl()
        assertEquals(
            "https://example.com/a/b/c".toHttpUrl(),
            RedirectPath.nextUrl(currentWithSlash, "c", 301)
        )

        val currentFile = "https://example.com/a/b/index.html".toHttpUrl()
        assertEquals(
            "https://example.com/a/b/next.html".toHttpUrl(),
            RedirectPath.nextUrl(currentFile, "next.html", 302)
        )
        assertEquals(
            "https://example.com/a/other".toHttpUrl(),
            RedirectPath.nextUrl(currentFile, "../other", 302)
        )
        assertEquals(
            "https://example.com/root.tar.gz".toHttpUrl(),
            RedirectPath.nextUrl(currentFile, "/root.tar.gz", 307)
        )
    }

    @Test
    fun testNextUrlNonRedirectCodeReturnsNull() {
        val current = "https://example.com/file".toHttpUrl()
        assertNull(RedirectPath.nextUrl(current, "https://example.com/next", 200))
        assertNull(RedirectPath.nextUrl(current, "https://example.com/next", 204))
        assertNull(RedirectPath.nextUrl(current, "https://example.com/next", 304))
        assertNull(RedirectPath.nextUrl(current, "https://example.com/next", 400))
        assertNull(RedirectPath.nextUrl(current, "https://example.com/next", 404))
        assertNull(RedirectPath.nextUrl(current, "https://example.com/next", 500))
    }

    @Test
    fun testNextUrlMissingLocationReturnsNull() {
        val current = "https://example.com/file".toHttpUrl()
        assertNull(RedirectPath.nextUrl(current, null, 301))
        assertNull(RedirectPath.nextUrl(current, "", 302))
        assertNull(RedirectPath.nextUrl(current, "   ", 307))
    }

    @Test
    fun testNextUrlFtpSchemeReturnsNull() {
        val current = "https://example.com/file".toHttpUrl()
        assertNull(RedirectPath.nextUrl(current, "ftp://ftp.example.com/file.iso", 301))
        assertNull(RedirectPath.nextUrl(current, "javascript:void(0)", 302))
    }

    @Test
    fun testFirstFlaggedHopChain() = runBlocking {
        val fakeFlags = mapOf(
            "https://clean1.com/a" to null,
            "https://allowed-bad.com/b" to HopFlag("allowed-bad.com", "https://allowed-bad.com/b", WarningReason.POTENTIALLY_UNSAFE),
            "https://unsafe.com/c" to HopFlag("unsafe.com", "https://unsafe.com/c", WarningReason.UNSAFE),
            "https://clone.com/d" to HopFlag("clone.com", "https://clone.com/d", WarningReason.LOOKALIKE),
        )

        val checker = HopChecker { url -> fakeFlags[url] }

        val urls = listOf(
            "https://clean1.com/a",
            "https://allowed-bad.com/b",
            "https://unsafe.com/c",
            "https://clone.com/d",
        )

        // Without allowed-bad.com allowed, it stops at the first flagged hop:
        val firstWithoutAllowed = RedirectPath.firstFlaggedHop(urls, checker, emptySet())
        assertNotNull(firstWithoutAllowed)
        assertEquals("allowed-bad.com", firstWithoutAllowed?.host)
        assertEquals(WarningReason.POTENTIALLY_UNSAFE, firstWithoutAllowed?.reason)

        // With allowed-bad.com allowed, it skips it and returns unsafe.com:
        val firstWithAllowed = RedirectPath.firstFlaggedHop(urls, checker, setOf("allowed-bad.com"))
        assertNotNull(firstWithAllowed)
        assertEquals("unsafe.com", firstWithAllowed?.host)
        assertEquals(WarningReason.UNSAFE, firstWithAllowed?.reason)

        // With all flagged hosts allowed, returns null:
        val allAllowed = RedirectPath.firstFlaggedHop(
            urls,
            checker,
            setOf("allowed-bad.com", "unsafe.com", "clone.com")
        )
        assertNull(allAllowed)
    }

    @Test
    fun testRedirectWarningTextSentences() {
        val unsafeFlag = HopFlag("evil.org", "https://evil.org/payload.exe", WarningReason.UNSAFE)
        assertEquals(
            "The download was redirected from legit.com to evil.org, which FMHY lists as unsafe.",
            RedirectWarningText.sentence("legit.com", unsafeFlag)
        )

        val potentiallyUnsafeFlag = HopFlag("sketchy.cc", "https://sketchy.cc/file.zip", WarningReason.POTENTIALLY_UNSAFE)
        assertEquals(
            "The download was redirected from legit.com to sketchy.cc, which FMHY flags as potentially unsafe.",
            RedirectWarningText.sentence("legit.com", potentiallyUnsafeFlag)
        )

        val lookalikeFlag = HopFlag("f4mhy.net", "https://f4mhy.net/app.apk", WarningReason.LOOKALIKE)
        assertEquals(
            "The download was redirected from legit.com to f4mhy.net, which looks like a fake copy of a site FMHY lists.",
            RedirectWarningText.sentence("legit.com", lookalikeFlag)
        )
    }

    @Test
    fun httpsToHttpIsInsecureDowngrade() {
        assertTrue(RedirectPath.isInsecureDowngrade("https://a.example/f".toHttpUrl(), "http://b.example/f".toHttpUrl()))
    }

    @Test
    fun httpToHttpsAndSameSchemeAreAllowed() {
        assertFalse(RedirectPath.isInsecureDowngrade("http://a.example/f".toHttpUrl(), "https://a.example/f".toHttpUrl()))
        assertFalse(RedirectPath.isInsecureDowngrade("http://a.example/f".toHttpUrl(), "http://b.example/f".toHttpUrl()))
        assertFalse(RedirectPath.isInsecureDowngrade("https://a.example/f".toHttpUrl(), "https://b.example/f".toHttpUrl()))
    }
}
