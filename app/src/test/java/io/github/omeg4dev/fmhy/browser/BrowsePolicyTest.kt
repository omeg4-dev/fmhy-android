package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.work.NetworkKind
import io.github.omeg4dev.fmhy.work.NetworkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowsePolicyTest {

    @Test
    fun `shouldBlock returns false when browseWifiOnly is false`() {
        assertFalse(
            BrowsePolicy.shouldBlock(
                url = "https://fmhy.net",
                browseWifiOnly = false,
                kind = NetworkKind.MOBILE,
                tabOverride = false,
            )
        )
    }

    @Test
    fun `shouldBlock returns false when kind is WIFI`() {
        assertFalse(
            BrowsePolicy.shouldBlock(
                url = "https://fmhy.net",
                browseWifiOnly = true,
                kind = NetworkKind.WIFI,
                tabOverride = false,
            )
        )
    }

    @Test
    fun `shouldBlock returns false when kind is NONE`() {
        // Keeps offline/failed-load behavior
        assertFalse(
            BrowsePolicy.shouldBlock(
                url = "https://fmhy.net",
                browseWifiOnly = true,
                kind = NetworkKind.NONE,
                tabOverride = false,
            )
        )
    }

    @Test
    fun `shouldBlock returns false when tabOverride is true`() {
        assertFalse(
            BrowsePolicy.shouldBlock(
                url = "https://fmhy.net",
                browseWifiOnly = true,
                kind = NetworkKind.MOBILE,
                tabOverride = true,
            )
        )
    }

    @Test
    fun `shouldBlock returns false for start screen urls`() {
        assertFalse(
            BrowsePolicy.shouldBlock(
                url = "about:blank",
                browseWifiOnly = true,
                kind = NetworkKind.MOBILE,
                tabOverride = false,
            )
        )
        assertFalse(
            BrowsePolicy.shouldBlock(
                url = "",
                browseWifiOnly = true,
                kind = NetworkKind.MOBILE,
                tabOverride = false,
            )
        )
        assertFalse(
            BrowsePolicy.shouldBlock(
                url = "   ",
                browseWifiOnly = true,
                kind = NetworkKind.MOBILE,
                tabOverride = false,
            )
        )
    }

    @Test
    fun `shouldBlock returns false for non-web urls`() {
        assertFalse(
            BrowsePolicy.shouldBlock(
                url = "data:text/html,<html></html>",
                browseWifiOnly = true,
                kind = NetworkKind.MOBILE,
                tabOverride = false,
            )
        )
        assertFalse(
            BrowsePolicy.shouldBlock(
                url = "file:///android_asset/dataset/snapshot.json",
                browseWifiOnly = true,
                kind = NetworkKind.MOBILE,
                tabOverride = false,
            )
        )
        assertFalse(
            BrowsePolicy.shouldBlock(
                url = "javascript:void(0)",
                browseWifiOnly = true,
                kind = NetworkKind.MOBILE,
                tabOverride = false,
            )
        )
    }

    @Test
    fun `shouldBlock returns true for https and http on mobile`() {
        assertTrue(
            BrowsePolicy.shouldBlock(
                url = "https://fmhy.net",
                browseWifiOnly = true,
                kind = NetworkKind.MOBILE,
                tabOverride = false,
            )
        )
        assertTrue(
            BrowsePolicy.shouldBlock(
                url = "http://example.com/test",
                browseWifiOnly = true,
                kind = NetworkKind.MOBILE,
                tabOverride = false,
            )
        )
        assertTrue(
            BrowsePolicy.shouldBlock(
                url = "HTTPS://FMHY.NET",
                browseWifiOnly = true,
                kind = NetworkKind.MOBILE,
                tabOverride = false,
            )
        )
    }

    @Test
    fun `shouldBlock returns true for metered Wi-Fi which resolves to MOBILE`() {
        val meteredWifiKind = NetworkPolicy.kindOf(
            isWifiOrEthernet = true,
            isConnected = true,
            isMetered = true,
        )
        assertEquals(NetworkKind.MOBILE, meteredWifiKind)
        assertTrue(
            BrowsePolicy.shouldBlock(
                url = "https://fmhy.net",
                browseWifiOnly = true,
                kind = meteredWifiKind,
                tabOverride = false,
            )
        )
    }
}
