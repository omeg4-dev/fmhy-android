package io.github.omeg4dev.fmhy.net

import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.ProxyMode
import io.github.omeg4dev.fmhy.data.ProxyType
import java.net.InetSocketAddress
import java.net.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyEndpointTest {

    @Test
    fun resolveOffReturnsNull() {
        assertNull(ProxyEndpoint.resolve(ProtectionPrefs(proxyMode = ProxyMode.OFF)))
    }

    @Test
    fun resolveOrbotReturnsFixedLoopbackSocks5() {
        val ep = ProxyEndpoint.resolve(ProtectionPrefs(proxyMode = ProxyMode.ORBOT))
        assertNotNull(ep)
        ep!!
        assertEquals(ProxyType.SOCKS5, ep.type)
        assertEquals("127.0.0.1", ep.host)
        assertEquals(9050, ep.port)
        assertTrue(ep.isOrbot)
        assertFalse(ep.invalid)
        assertEquals("socks://127.0.0.1:9050", ep.webViewRule)
        assertEquals("Orbot (127.0.0.1:9050)", ep.label)
        assertEquals(
            Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", 9050)),
            ep.javaProxy,
        )
    }

    @Test
    fun resolveCustomValidHostnameIpv4AndIpv6() {
        val hostnameEp = ProxyEndpoint.resolve(
            ProtectionPrefs(
                proxyMode = ProxyMode.CUSTOM,
                proxyType = ProxyType.HTTP,
                proxyHost = "  proxy.example.com  ",
                proxyPort = 8080,
            )
        )!!
        assertFalse(hostnameEp.invalid)
        assertEquals("proxy.example.com", hostnameEp.host)
        assertEquals(8080, hostnameEp.port)
        assertEquals("http://proxy.example.com:8080", hostnameEp.webViewRule)
        assertEquals("HTTP proxy proxy.example.com:8080", hostnameEp.label)
        assertEquals(Proxy.Type.HTTP, hostnameEp.javaProxy.type())

        val ipv6Bracketed = ProxyEndpoint.resolve(
            ProtectionPrefs(
                proxyMode = ProxyMode.CUSTOM,
                proxyType = ProxyType.SOCKS5,
                proxyHost = "[::1]",
                proxyPort = 1080,
            )
        )!!
        assertFalse(ipv6Bracketed.invalid)
        assertEquals("::1", ipv6Bracketed.host)
        assertEquals("socks://[::1]:1080", ipv6Bracketed.webViewRule)
        assertEquals("SOCKS5 proxy [::1]:1080", ipv6Bracketed.label)

        val ipv6Bare = ProxyEndpoint.resolve(
            ProtectionPrefs(
                proxyMode = ProxyMode.CUSTOM,
                proxyType = ProxyType.HTTP,
                proxyHost = "2001:db8::1",
                proxyPort = 3128,
            )
        )!!
        assertFalse(ipv6Bare.invalid)
        assertEquals("2001:db8::1", ipv6Bare.host)
        assertEquals("http://[2001:db8::1]:3128", ipv6Bare.webViewRule)
        assertEquals("HTTP proxy [2001:db8::1]:3128", ipv6Bare.label)
    }

    @Test
    fun resolveCustomInvalidInputsProduceBlackhole() {
        val badHosts = listOf(
            "",
            "   ",
            "a".repeat(254),
            "bad host.com",
            "socks5://127.0.0.1",
            "http://proxy.com",
            "proxy.com/path",
            "user@proxy.com",
            "proxy.com?x=1",
            "proxy.com#frag",
            "proxy.com:8080",
            "127.0.0.1:9050",
            "[::1]:9050",
            "[::1",
            "::1]",
            "[example.com]",
            "256.1.1.1",
            "01.2.3.4",
            "999.999.999.999",
            "-bad.example",
            "bad-.example",
            "bad..example",
            "1::2::3",
            "::1%eth0",
        )
        for (host in badHosts) {
            val ep = ProxyEndpoint.resolve(
                ProtectionPrefs(
                    proxyMode = ProxyMode.CUSTOM,
                    proxyType = ProxyType.SOCKS5,
                    proxyHost = host,
                    proxyPort = 1080,
                )
            )
            assertNotNull("Expected blackhole for host='$host'", ep)
            assertTrue("Expected invalid=true for host='$host'", ep!!.invalid)
            assertEquals("127.0.0.1", ep.host)
            assertEquals(1, ep.port)
        }

        val badPorts = listOf(-1, 0, 65536, 100_000)
        for (port in badPorts) {
            val ep = ProxyEndpoint.resolve(
                ProtectionPrefs(
                    proxyMode = ProxyMode.CUSTOM,
                    proxyType = ProxyType.HTTP,
                    proxyHost = "127.0.0.1",
                    proxyPort = port,
                )
            )!!
            assertTrue("Expected invalid=true for port=$port", ep.invalid)
            assertEquals("127.0.0.1", ep.host)
            assertEquals(1, ep.port)
        }
    }

    @Test
    fun hostBoundaryLengthsAndIpv4MappedIpv6() {
        val label = "a".repeat(63)
        val maxHost = "$label.$label.$label.${"b".repeat(61)}"
        assertEquals(253, maxHost.length)
        val validMax = ProxyEndpoint.resolve(
            ProtectionPrefs(proxyMode = ProxyMode.CUSTOM, proxyHost = maxHost, proxyPort = 1080)
        )!!
        assertFalse(validMax.invalid)

        val ipv4Mapped = ProxyEndpoint.resolve(
            ProtectionPrefs(proxyMode = ProxyMode.CUSTOM, proxyHost = "::ffff:127.0.0.1", proxyPort = 1080)
        )!!
        assertFalse(ipv4Mapped.invalid)
        assertEquals("socks://[::ffff:127.0.0.1]:1080", ipv4Mapped.webViewRule)
    }
}
