package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoVpnPageTest {

    @Test
    fun `html contains required texts, method names, accent and bridge name`() {
        val accent = "#7C5CFF"
        val bridge = "_fmhyTestBridge123"
        val html = NoVpnPage.html(
            isDark = false,
            isBlack = false,
            accentHex = accent,
            surfaceHex = "#FFFFFF",
            onSurfaceHex = "#1F1F1F",
            bridgeName = bridge,
        )

        assertTrue(html.contains("No VPN active"))
        assertTrue(html.contains("Open VPN settings"))
        assertTrue(html.contains("Load anyway once"))
        assertTrue(html.contains("loadWithoutVpnOnce"))
        assertTrue(html.contains("openVpnSettings"))
        assertTrue(html.contains(accent))
        assertTrue(html.contains(bridge))
    }

    @Test
    fun `html differs between light, dark, and black backgrounds`() {
        val lightHtml = NoVpnPage.html(
            isDark = false,
            isBlack = false,
            accentHex = "#7C5CFF",
            surfaceHex = "#FFFFFF",
            onSurfaceHex = "#1F1F1F",
        )
        val darkHtml = NoVpnPage.html(
            isDark = true,
            isBlack = false,
            accentHex = "#7C5CFF",
            surfaceHex = "#1E1E1E",
            onSurfaceHex = "#E0E0E0",
        )
        val blackHtml = NoVpnPage.html(
            isDark = true,
            isBlack = true,
            accentHex = "#7C5CFF",
            surfaceHex = "#000000",
            onSurfaceHex = "#FFFFFF",
        )

        assertNotEquals(lightHtml, darkHtml)
        assertNotEquals(darkHtml, blackHtml)
        assertNotEquals(lightHtml, blackHtml)
    }
}
