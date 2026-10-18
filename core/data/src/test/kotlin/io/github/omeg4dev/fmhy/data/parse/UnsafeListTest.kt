package io.github.omeg4dev.fmhy.data.parse

import org.junit.Assert.assertEquals
import org.junit.Test

class UnsafeListTest {

    @Test
    fun testParseDomainsAndReasons() {
        val domainsTxt = """
            # Filterlist domains
            ! Comment line
            malicious.com
            scam.net
            MALICIOUS.COM
            another.org
        """.trimIndent()

        val reasonsJson = """
            {
              "malicious.com": "Distributes malware",
              "scam.net": "Phishing site"
            }
        """.trimIndent()

        val result = UnsafeList.parse(domainsTxt, reasonsJson)
        assertEquals(3, result.size)
        assertEquals("malicious.com" to "Distributes malware", result[0])
        assertEquals("scam.net" to "Phishing site", result[1])
        assertEquals("another.org" to "", result[2])
    }
}
