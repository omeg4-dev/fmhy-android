package io.github.omeg4dev.fmhy.ui.check

import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import io.github.omeg4dev.fmhy.data.safety.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerdictTextTest {

    @Test
    fun testVerdictTitles() {
        assertEquals("Official FMHY site", verdictTitle(SafetyStatus.FMHY_SITE))
        assertEquals("Starred on FMHY", verdictTitle(SafetyStatus.STARRED))
        assertEquals("Listed on FMHY", verdictTitle(SafetyStatus.LISTED))
        assertEquals("This domain is on FMHY", verdictTitle(SafetyStatus.DOMAIN_LISTED))
        assertEquals("Potentially unsafe", verdictTitle(SafetyStatus.POTENTIALLY_UNSAFE))
        assertEquals("Unsafe", verdictTitle(SafetyStatus.UNSAFE))
        assertEquals("Search engine", verdictTitle(SafetyStatus.SEARCH_ENGINE))
        assertEquals("Not on FMHY", verdictTitle(SafetyStatus.NO_DATA))
        assertEquals("Not a link", verdictTitle(SafetyStatus.INVALID))
    }

    @Test
    fun testVerdictBodiesWithoutReason() {
        assertEquals("This site belongs to FMHY.", verdictBody(SafetyStatus.FMHY_SITE))
        assertEquals("FMHY recommends this site.", verdictBody(SafetyStatus.STARRED))
        assertEquals("This site is listed on FMHY.", verdictBody(SafetyStatus.LISTED))
        assertEquals("FMHY lists other pages on example.com, not this exact link.", verdictBody(SafetyStatus.DOMAIN_LISTED, host = "example.com"))
        assertEquals("FMHY lists this site as not recommended or potentially unsafe.", verdictBody(SafetyStatus.POTENTIALLY_UNSAFE))
        assertEquals("FMHY warns against this site.", verdictBody(SafetyStatus.UNSAFE))
        assertEquals("Search engines aren't rated.", verdictBody(SafetyStatus.SEARCH_ENGINE))
        assertEquals("FMHY doesn't list this site. That doesn't make it unsafe \u2014 or safe.", verdictBody(SafetyStatus.NO_DATA))
        assertEquals("Enter a web address like example.com.", verdictBody(SafetyStatus.INVALID))
    }

    @Test
    fun testVerdictReasonShownWhenPresent() {
        val unsafeBody = verdictBody(SafetyStatus.UNSAFE, "Known malware distributor")
        assertTrue(unsafeBody.contains("FMHY warns against this site."))
        assertTrue(unsafeBody.contains("Reason: Known malware distributor"))

        val potUnsafeBody = verdictBody(SafetyStatus.POTENTIALLY_UNSAFE, "Adware detected")
        assertTrue(potUnsafeBody.contains("FMHY lists this site as not recommended or potentially unsafe."))
        assertTrue(potUnsafeBody.contains("Reason: Adware detected"))

        // When reason already has prefix "Reason:"
        val alreadyFormatted = verdictBody(SafetyStatus.UNSAFE, "Reason: Phishing")
        assertEquals("FMHY warns against this site. Reason: Phishing", alreadyFormatted)
    }

    @Test
    fun testMirrorAndExtraLinksSuffix() {
        val mirrorStarred = verdictBody(SafetyStatus.STARRED, via = Via.MIRROR, matchedEntryName = "AppName")
        assertEquals("FMHY recommends this site (as a mirror of AppName).", mirrorStarred)

        val extraListed = verdictBody(SafetyStatus.LISTED, via = Via.EXTRA, matchedEntryName = "AppName")
        assertEquals("This site is listed on FMHY (as an extra link of AppName).", extraListed)
    }

    @Test
    fun testSharedHostNoDataText() {
        val sharedBody = verdictBody(SafetyStatus.NO_DATA, host = "github.com", sharedHostListedCount = 12)
        assertEquals("github.com hosts many projects. FMHY lists 12 of them, but not this one.", sharedBody)
    }
}
