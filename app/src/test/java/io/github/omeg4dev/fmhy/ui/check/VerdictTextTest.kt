package io.github.omeg4dev.fmhy.ui.check

import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerdictTextTest {

    @Test
    fun testVerdictTitles() {
        assertEquals("Official FMHY site", verdictTitle(SafetyStatus.FMHY_SITE))
        assertEquals("Starred on FMHY", verdictTitle(SafetyStatus.STARRED))
        assertEquals("Listed on FMHY", verdictTitle(SafetyStatus.LISTED))
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
        assertEquals("FMHY lists this site as not recommended or potentially unsafe.", verdictBody(SafetyStatus.POTENTIALLY_UNSAFE))
        assertEquals("FMHY warns against this site.", verdictBody(SafetyStatus.UNSAFE))
        assertEquals("Search engines aren't rated.", verdictBody(SafetyStatus.SEARCH_ENGINE))
        assertEquals("FMHY has no information about this site. That doesn't mean it's safe.", verdictBody(SafetyStatus.NO_DATA))
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
}
