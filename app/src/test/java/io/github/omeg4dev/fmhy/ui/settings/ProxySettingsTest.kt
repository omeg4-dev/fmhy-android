package io.github.omeg4dev.fmhy.ui.settings

import io.github.omeg4dev.fmhy.browser.BrowserWebViewFactory
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.ProxyMode
import io.github.omeg4dev.fmhy.data.ProxyType
import io.github.omeg4dev.fmhy.data.user.SitePrefs
import io.github.omeg4dev.fmhy.net.ProxyEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySettingsTest {

    @Test
    fun summaryCoversAllModesAndUnsupportedStates() {
        assertEquals(
            "Off",
            proxyRowSummary(ProtectionPrefs(), webViewSupported = true, orbotInstalled = false),
        )
        assertEquals(
            "Orbot (Tor) · 127.0.0.1:9050 · JavaScript off",
            proxyRowSummary(
                ProtectionPrefs(proxyMode = ProxyMode.ORBOT),
                webViewSupported = true,
                orbotInstalled = true,
            ),
        )
        assertEquals(
            "Orbot (Tor) · 127.0.0.1:9050 · JavaScript on",
            proxyRowSummary(
                ProtectionPrefs(proxyMode = ProxyMode.ORBOT, proxyAllowJavaScript = true),
                webViewSupported = true,
                orbotInstalled = true,
            ),
        )
        assertEquals(
            "Orbot is not installed · JavaScript off",
            proxyRowSummary(
                ProtectionPrefs(proxyMode = ProxyMode.ORBOT),
                webViewSupported = true,
                orbotInstalled = false,
            ),
        )
        assertEquals(
            "SOCKS5 · proxy.local:1080 · JavaScript off",
            proxyRowSummary(
                ProtectionPrefs(
                    proxyMode = ProxyMode.CUSTOM,
                    proxyType = ProxyType.SOCKS5,
                    proxyHost = "proxy.local",
                    proxyPort = 1080,
                ),
                webViewSupported = true,
                orbotInstalled = false,
            ),
        )
        assertEquals(
            "HTTP · 127.0.0.1:8080 · JavaScript on",
            proxyRowSummary(
                ProtectionPrefs(
                    proxyMode = ProxyMode.CUSTOM,
                    proxyType = ProxyType.HTTP,
                    proxyHost = "127.0.0.1",
                    proxyPort = 8080,
                    proxyAllowJavaScript = true,
                ),
                webViewSupported = true,
                orbotInstalled = false,
            ),
        )
        assertEquals(
            "This phone's WebView cannot use a proxy",
            proxyRowSummary(
                ProtectionPrefs(proxyMode = ProxyMode.ORBOT),
                webViewSupported = false,
                orbotInstalled = true,
            ),
        )
    }

    @Test
    fun helperTextReflectsJavaScriptToggle() {
        assertEquals(
            "All tabs and app requests use the proxy. JavaScript is off, so some sites will not work. Links opened in other apps do not use it.",
            proxyDialogHelperText(ProxyMode.ORBOT, allowJavaScript = false),
        )
        assertEquals(
            "All tabs and app requests use the proxy. Web calls are blocked where possible. Links opened in other apps do not use it.",
            proxyDialogHelperText(ProxyMode.CUSTOM, allowJavaScript = true),
        )
    }

    @Test
    fun effectivePageJavaScriptCoversAllFourCases() {
        val endpoint = ProxyEndpoint.resolve(ProtectionPrefs(proxyMode = ProxyMode.ORBOT))!!

        // 1. State not loaded yet -> always false
        assertFalse(
            BrowserWebViewFactory.effectivePageJavaScript(
                proxyLoaded = false,
                proxyEndpoint = null,
                proxyAllowJavaScript = true,
                browserPrefs = BrowserPrefs(javascript = true),
                sitePrefs = SitePrefs(host = "example.com", javascript = true),
            )
        )

        // 2. Proxy off -> follows sitePrefs ?: browserPrefs
        assertTrue(
            BrowserWebViewFactory.effectivePageJavaScript(
                proxyLoaded = true,
                proxyEndpoint = null,
                proxyAllowJavaScript = false,
                browserPrefs = BrowserPrefs(javascript = true),
            )
        )
        assertFalse(
            BrowserWebViewFactory.effectivePageJavaScript(
                proxyLoaded = true,
                proxyEndpoint = null,
                proxyAllowJavaScript = true,
                browserPrefs = BrowserPrefs(javascript = false),
            )
        )
        assertTrue(
            BrowserWebViewFactory.effectivePageJavaScript(
                proxyLoaded = true,
                proxyEndpoint = null,
                proxyAllowJavaScript = false,
                browserPrefs = BrowserPrefs(javascript = false),
                sitePrefs = SitePrefs(host = "example.com", javascript = true),
            )
        )

        // 3. Proxy set, proxyAllowJavaScript = false -> always false
        assertFalse(
            BrowserWebViewFactory.effectivePageJavaScript(
                proxyLoaded = true,
                proxyEndpoint = endpoint,
                proxyAllowJavaScript = false,
                browserPrefs = BrowserPrefs(javascript = true),
                sitePrefs = SitePrefs(host = "example.com", javascript = true),
            )
        )

        // 4. Proxy set, proxyAllowJavaScript = true -> requires app/site choice too
        assertTrue(
            BrowserWebViewFactory.effectivePageJavaScript(
                proxyLoaded = true,
                proxyEndpoint = endpoint,
                proxyAllowJavaScript = true,
                browserPrefs = BrowserPrefs(javascript = true),
            )
        )
        assertFalse(
            BrowserWebViewFactory.effectivePageJavaScript(
                proxyLoaded = true,
                proxyEndpoint = endpoint,
                proxyAllowJavaScript = true,
                browserPrefs = BrowserPrefs(javascript = false),
            )
        )
        assertFalse(
            BrowserWebViewFactory.effectivePageJavaScript(
                proxyLoaded = true,
                proxyEndpoint = endpoint,
                proxyAllowJavaScript = true,
                browserPrefs = BrowserPrefs(javascript = true),
                sitePrefs = SitePrefs(host = "example.com", javascript = false),
            )
        )
    }

    @Test
    fun unsupportedWebViewDisablesRowOnlyWhenOff() {
        assertFalse(proxyRowEnabled(ProtectionPrefs(proxyMode = ProxyMode.OFF), webViewSupported = false))
        assertTrue(proxyRowEnabled(ProtectionPrefs(proxyMode = ProxyMode.ORBOT), webViewSupported = false))
        assertTrue(proxyRowEnabled(ProtectionPrefs(proxyMode = ProxyMode.CUSTOM), webViewSupported = false))
        assertTrue(proxyRowEnabled(ProtectionPrefs(proxyMode = ProxyMode.OFF), webViewSupported = true))
    }

    @Test
    fun safeBrowsingIsDisabledWhileProxied() {
        assertTrue(BrowserWebViewFactory.effectiveSafeBrowsing(ProtectionPrefs(safeBrowsing = true, proxyMode = ProxyMode.OFF)))
        assertFalse(BrowserWebViewFactory.effectiveSafeBrowsing(ProtectionPrefs(safeBrowsing = false, proxyMode = ProxyMode.OFF)))
        assertFalse(BrowserWebViewFactory.effectiveSafeBrowsing(ProtectionPrefs(safeBrowsing = true, proxyMode = ProxyMode.ORBOT)))
        assertFalse(BrowserWebViewFactory.effectiveSafeBrowsing(ProtectionPrefs(safeBrowsing = true, proxyMode = ProxyMode.CUSTOM)))
    }
}
