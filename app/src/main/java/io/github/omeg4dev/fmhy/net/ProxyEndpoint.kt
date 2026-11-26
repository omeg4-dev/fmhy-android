package io.github.omeg4dev.fmhy.net

import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.ProxyMode
import io.github.omeg4dev.fmhy.data.ProxyType
import java.net.InetSocketAddress
import java.net.Proxy

/** Validated, resolved proxy endpoint for OkHttp and WebView. */
data class ProxyEndpoint(
    val type: ProxyType,
    val host: String,
    val port: Int,
    val isOrbot: Boolean = false,
    val invalid: Boolean = false,
) {
    val webViewRule: String
        get() {
            val scheme = if (type == ProxyType.SOCKS5) "socks" else "http"
            return "$scheme://${formatHostPort(host, port)}"
        }

    val label: String
        get() = when {
            isOrbot -> "Orbot ($ORBOT_HOST:$ORBOT_PORT)"
            type == ProxyType.SOCKS5 -> "SOCKS5 proxy ${formatHostPort(host, port)}"
            else -> "HTTP proxy ${formatHostPort(host, port)}"
        }

    val summary: String
        get() = when {
            isOrbot -> "Orbot (Tor) · $ORBOT_HOST:$ORBOT_PORT"
            invalid -> "${type.name} · invalid (blocked)"
            else -> "${type.name} · ${formatHostPort(host, port)}"
        }

    val javaProxy: Proxy
        get() {
            val proxyType = if (type == ProxyType.SOCKS5) Proxy.Type.SOCKS else Proxy.Type.HTTP
            return Proxy(proxyType, InetSocketAddress.createUnresolved(host, port))
        }

    companion object {
        const val ORBOT_HOST = "127.0.0.1"
        const val ORBOT_PORT = 9050
        private const val BLACKHOLE_HOST = "127.0.0.1"
        private const val BLACKHOLE_PORT = 1
        private const val MAX_HOST_LENGTH = 253
        private const val MAX_PORT = 65535

        fun resolve(prefs: ProtectionPrefs): ProxyEndpoint? = when (prefs.proxyMode) {
            ProxyMode.OFF -> null
            ProxyMode.ORBOT -> ProxyEndpoint(
                type = ProxyType.SOCKS5,
                host = ORBOT_HOST,
                port = ORBOT_PORT,
                isOrbot = true,
                invalid = false,
            )
            ProxyMode.CUSTOM -> {
                val normalizedHost = normalizeHost(prefs.proxyHost)
                if (normalizedHost != null && prefs.proxyPort in 1..MAX_PORT) {
                    ProxyEndpoint(
                        type = prefs.proxyType,
                        host = normalizedHost,
                        port = prefs.proxyPort,
                        isOrbot = false,
                        invalid = false,
                    )
                } else {
                    ProxyEndpoint(
                        type = prefs.proxyType,
                        host = BLACKHOLE_HOST,
                        port = BLACKHOLE_PORT,
                        isOrbot = false,
                        invalid = true,
                    )
                }
            }
        }

        fun formatHostPort(host: String, port: Int): String {
            val formattedHost = if (':' in host) "[$host]" else host
            return "$formattedHost:$port"
        }

        fun validateHost(raw: String): String? {
            val normalized = normalizeHost(raw)
            return if (normalized != null) null else hostError(raw)
        }

        fun hostError(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return "Enter a host"
            if (trimmed.length > MAX_HOST_LENGTH) return "Host is too long"
            if (trimmed.any { it.isWhitespace() }) return "Host cannot contain spaces"
            if (trimmed.contains("://") || trimmed.any { it in "/@?#" }) {
                return "Enter only a host name or IP address"
            }
            if (normalizeHost(trimmed) == null) {
                return "Invalid host name or IP address (do not include :port)"
            }
            return null
        }

        fun portError(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return "Enter a port"
            val port = trimmed.toIntOrNull() ?: return "Port must be 1–65535"
            if (port !in 1..MAX_PORT) return "Port must be 1–65535"
            return null
        }

        /**
         * Validates and normalizes a proxy host without performing DNS lookups.
         * Returns the unbracketed host string, or null if invalid.
         */
        fun normalizeHost(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_HOST_LENGTH) return null
            if (trimmed.any { it.isWhitespace() }) return null
            if (trimmed.contains("://") || trimmed.any { it in "/@?#" }) return null

            if (trimmed.startsWith('[') || trimmed.endsWith(']')) {
                if (!trimmed.startsWith('[') || !trimmed.endsWith(']')) return null
                val inner = trimmed.substring(1, trimmed.length - 1)
                if (inner.isEmpty() || '[' in inner || ']' in inner) return null
                return if (isValidIpv6(inner)) inner else null
            }

            if (':' in trimmed) {
                return if (isValidIpv6(trimmed)) trimmed else null
            }

            if (isValidIpv4(trimmed)) return trimmed
            return if (isValidHostname(trimmed)) trimmed else null
        }

        private fun isValidIpv4(host: String): Boolean {
            val parts = host.split('.')
            if (parts.size != 4) return false
            return parts.all { part ->
                if (part.isEmpty() || part.length > 3) return@all false
                if (!part.all { it in '0'..'9' }) return@all false
                if (part.length > 1 && part[0] == '0') return@all false
                val value = part.toIntOrNull() ?: return@all false
                value in 0..255
            }
        }

        private fun isValidHostname(host: String): Boolean {
            if (host.length > MAX_HOST_LENGTH) return false
            val labels = host.split('.')
            if (labels.any { it.isEmpty() || it.length > 63 }) return false
            val validLabels = labels.all { label ->
                !label.startsWith('-') &&
                    !label.endsWith('-') &&
                    label.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }
            }
            if (!validLabels) return false
            // A trailing all-numeric label indicates a malformed IPv4 literal, not a DNS name.
            if (labels.last().all { it in '0'..'9' }) return false
            return true
        }

        private fun isValidIpv6(raw: String): Boolean {
            if (raw.isEmpty() || '%' in raw) return false
            val doubleColonCount = countOccurrences(raw, "::")
            if (doubleColonCount > 1) return false

            if (doubleColonCount == 1) {
                val halves = raw.split("::", limit = 2)
                val left = if (halves[0].isEmpty()) emptyList() else halves[0].split(':')
                val right = if (halves[1].isEmpty()) emptyList() else halves[1].split(':')
                val (rightHex, ipv4Groups) = parseIpv6Tail(right) ?: return false
                if (left.any { !isHexGroup(it) } || rightHex.any { !isHexGroup(it) }) return false
                val totalGroups = left.size + rightHex.size + ipv4Groups
                return totalGroups < 8
            }

            val parts = raw.split(':')
            val (hexParts, ipv4Groups) = parseIpv6Tail(parts) ?: return false
            if (hexParts.any { !isHexGroup(it) }) return false
            return hexParts.size + ipv4Groups == 8
        }

        private fun parseIpv6Tail(groups: List<String>): Pair<List<String>, Int>? {
            if (groups.isEmpty()) return emptyList<String>() to 0
            val last = groups.last()
            return if ('.' in last) {
                if (!isValidIpv4(last)) null else groups.dropLast(1) to 2
            } else {
                groups to 0
            }
        }

        private fun isHexGroup(group: String): Boolean =
            group.length in 1..4 && group.all {
                it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
            }

        private fun countOccurrences(text: String, needle: String): Int {
            var count = 0
            var index = 0
            while (true) {
                val found = text.indexOf(needle, index)
                if (found < 0) return count
                count++
                index = found + needle.length
            }
        }
    }
}
