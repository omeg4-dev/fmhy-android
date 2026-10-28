package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PipSourceRectTest {
    private val webView = intArrayOf(0, 250, 1080, 2300)

    @Test
    fun videoIsOffsetByTheWebViewPosition() {
        assertArrayEquals(intArrayOf(0, 850, 1080, 1450), pipSourceRect(webView, intArrayOf(0, 600, 1080, 1200)))
    }

    @Test
    fun videoPartlyScrolledOutIsCutToTheWebView() {
        assertArrayEquals(intArrayOf(0, 250, 1080, 700), pipSourceRect(webView, intArrayOf(0, -300, 1080, 450)))
    }

    @Test
    fun unknownOrOffscreenVideoFallsBackToTheWebView() {
        assertArrayEquals(webView, pipSourceRect(webView, null))
        assertArrayEquals(webView, pipSourceRect(webView, intArrayOf(0, 3000, 1080, 3600)))
    }
}
