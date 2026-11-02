package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.work.VpnState

/**
 * Pure policy object for "Disable browsing without VPN" gating.
 * Determines whether a URL load should be blocked and replaced with the No VPN message page.
 */
object VpnGate {

    /**
     * Returns true if loading [url] should be blocked when browsing is restricted to VPN connections.
     *
     * Returns false when:
     * - [blockWithoutVpn] is false
     * - [showVpnState] is false
     * - [vpn] is not [VpnState.INACTIVE]
     * - [tabOverride] is true (user explicitly allowed loading without VPN once for this tab)
     * - [url] is not a web URL (start pages, `about:blank`, `data:`, `file:`, or internal schemes)
     */
    fun shouldBlock(
        url: String,
        blockWithoutVpn: Boolean,
        showVpnState: Boolean,
        vpn: VpnState,
        tabOverride: Boolean,
    ): Boolean {
        if (!blockWithoutVpn || !showVpnState || tabOverride) return false
        if (vpn != VpnState.INACTIVE) return false
        if (EmptyTabPolicy.isStartUrl(url)) return false
        val isHttp = url.startsWith("http://", ignoreCase = true)
        val isHttps = url.startsWith("https://", ignoreCase = true)
        if (!isHttp && !isHttps) return false
        return true
    }
}
