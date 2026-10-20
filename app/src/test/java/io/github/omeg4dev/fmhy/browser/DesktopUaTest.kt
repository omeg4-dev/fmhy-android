package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopUaTest {

    @Test
    fun testTransformsMobileWebViewUserAgent() {
        val mobileUa = "Mozilla/5.0 (Linux; U; Android 14; en-us; Pixel 7 Build/UQ1A.240105.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.43 Mobile Safari/537.36"
        val desktopUa = DesktopUa.transform(mobileUa)

        assertFalse("Should not contain '; wv'", desktopUa.contains("; wv"))
        assertFalse("Should not contain 'Mobile'", desktopUa.contains("Mobile"))
        assertFalse("Should not contain 'Android 14;'", desktopUa.contains("Android 14;"))
        assertTrue("Should contain 'X11; Linux x86_64;'", desktopUa.contains("X11; Linux x86_64;"))
    }

    @Test
    fun testTransformsAndroidVersionVariations() {
        val uas = listOf(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36; wv",
            "Mozilla/5.0 (Linux; Android 13; SM-S908B; wv) AppleWebKit/537.36 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 14.1; Device; wv) Mobile"
        )
        for (ua in uas) {
            val transformed = DesktopUa.transform(ua)
            assertFalse(transformed.contains("; wv"))
            assertFalse(transformed.contains("Mobile"))
            assertFalse(transformed.contains(Regex("""Android\s+[^;]+;""")))
            assertTrue(transformed.contains("X11; Linux x86_64;"))
        }
    }
}
