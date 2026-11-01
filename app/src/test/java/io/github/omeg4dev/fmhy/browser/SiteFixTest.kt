package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteFixTest {
    @Test
    fun knownDetectorScriptsAreRecognised() {
        assertTrue(SiteFix.isDetectorUrl("https://cdn.example.com/js/fuckadblock.min.js"))
        assertTrue(SiteFix.isDetectorUrl("https://example.com/assets/BlockAdBlock.js?v=3"))
        assertTrue(SiteFix.isDetectorUrl("https://example.com/js/adblock-detect.js"))
        assertTrue(SiteFix.isDetectorUrl("https://example.com/anti-adblock/loader.js"))
    }

    @Test
    fun ordinaryUrlsAreNotDetectors() {
        assertFalse(SiteFix.isDetectorUrl("https://example.com/app.js"))
        assertFalse(SiteFix.isDetectorUrl("https://example.com/jquery.min.js"))
        // Only the path counts: a tracking query string must not turn a normal script into a "detector".
        assertFalse(SiteFix.isDetectorUrl("https://example.com/app.js?ref=adblock-detect"))
    }

    @Test
    fun scriptAsksTheBridgeBeforeDoingAnything() {
        val js = SiteFix.script("FmhyBridge")
        assertTrue(js.contains("window[\"FmhyBridge\"]"))
        assertTrue(js.contains("bridge.fixSite(window.location.href)"))
        // Guard comes before the first stub.
        assertTrue(js.indexOf("bridge.fixSite") < js.indexOf("window.adsbygoogle"))
    }
}
