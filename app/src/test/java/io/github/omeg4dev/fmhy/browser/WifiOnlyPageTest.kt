package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiOnlyPageTest {

    @Test
    fun `html contains exact heading and button labels`() {
        val html = WifiOnlyPage.html(
            isDark = false,
            isBlack = false,
            accentHex = "#7C5CFF",
            surfaceHex = "#FFFFFF",
            onSurfaceHex = "#1F1F1F",
        )

        assertTrue(html.contains("Browsing is set to Wi-Fi only."))
        assertTrue(html.contains("Use mobile data this time"))
        assertTrue(html.contains("Open settings"))
    }

    @Test
    fun `html contains viewport meta and no external resources`() {
        val html = WifiOnlyPage.html(
            isDark = true,
            isBlack = false,
            accentHex = "#7C5CFF",
            surfaceHex = "#1E1E1E",
            onSurfaceHex = "#E0E0E0",
        )

        assertTrue(html.contains("""<meta name="viewport""""))
        assertTrue(html.contains("width=device-width"))
        assertTrue(!html.contains("http://"))
        assertTrue(!html.contains("https://"))
    }

    @Test
    fun `html differs between light, dark, and black backgrounds`() {
        val lightHtml = WifiOnlyPage.html(
            isDark = false,
            isBlack = false,
            accentHex = "#7C5CFF",
            surfaceHex = "#FFFFFF",
            onSurfaceHex = "#1F1F1F",
        )
        val darkHtml = WifiOnlyPage.html(
            isDark = true,
            isBlack = false,
            accentHex = "#7C5CFF",
            surfaceHex = "#1E1E1E",
            onSurfaceHex = "#E0E0E0",
        )
        val blackHtml = WifiOnlyPage.html(
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

    @Test
    fun `html contains JS calls to bridge methods`() {
        val html = WifiOnlyPage.html(
            isDark = false,
            isBlack = false,
            accentHex = "#7C5CFF",
            surfaceHex = "#FFFFFF",
            onSurfaceHex = "#1F1F1F",
        )

        assertTrue(html.contains("useMobileDataThisTime"))
        assertTrue(html.contains("openDataUsageSettings"))
    }
}
