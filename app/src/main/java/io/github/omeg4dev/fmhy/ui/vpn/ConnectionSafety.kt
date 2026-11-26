package io.github.omeg4dev.fmhy.ui.vpn

import io.github.omeg4dev.fmhy.work.VpnState

/** How safe the connection to the current page is, shown as the ring around the site icon. */
enum class ConnectionLevel { PROTECTED, ORBOT, ENCRYPTED, INSECURE }

object ConnectionSafety {
    fun isHttps(url: String): Boolean = url.startsWith("https://", ignoreCase = true)

    fun isHttp(url: String): Boolean = url.startsWith("http://", ignoreCase = true)

    /**
     * null for anything that is not a web page (blank tab, about:, file:, error pages).
     * [orbot] = the Orbot proxy is selected; it outranks the VPN state because page traffic goes through it.
     * [proxyFailed] = the tab shows the "cannot connect through the proxy" screen, so nothing is protected.
     */
    fun levelOf(
        url: String,
        vpn: VpnState,
        orbot: Boolean = false,
        proxyFailed: Boolean = false,
    ): ConnectionLevel? = when {
        orbot && proxyFailed -> null
        isHttps(url) -> when {
            orbot -> ConnectionLevel.ORBOT
            vpn == VpnState.ACTIVE -> ConnectionLevel.PROTECTED
            else -> ConnectionLevel.ENCRYPTED
        }
        isHttp(url) -> ConnectionLevel.INSECURE
        else -> null
    }

    fun title(level: ConnectionLevel): String = when (level) {
        ConnectionLevel.PROTECTED -> "Protected"
        ConnectionLevel.ORBOT -> "Protected by Orbot"
        ConnectionLevel.ENCRYPTED -> "Encrypted, no VPN"
        ConnectionLevel.INSECURE -> "Not secure"
    }

    fun detail(level: ConnectionLevel): String = when (level) {
        ConnectionLevel.PROTECTED -> "The page is encrypted (HTTPS) and your traffic goes through a VPN."
        ConnectionLevel.ORBOT -> "The page is encrypted (HTTPS) and goes through Orbot (Tor)."
        ConnectionLevel.ENCRYPTED -> "The page is encrypted (HTTPS), but the site sees your own IP address."
        ConnectionLevel.INSECURE -> "This page uses plain HTTP, so anyone on the network path can read or change it. A VPN does not fix that."
    }

    fun connectionText(url: String): String = if (isHttps(url)) "HTTPS, encrypted" else "HTTP, not encrypted"
}
