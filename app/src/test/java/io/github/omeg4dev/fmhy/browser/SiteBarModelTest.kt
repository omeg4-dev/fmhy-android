package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import io.github.omeg4dev.fmhy.data.safety.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SiteBarModelTest {

    private fun createEntry(
        id: String,
        name: String,
        headingPath: String,
        tier: Tier = Tier.NORMAL,
    ): Entry = Entry(
        id = id,
        num = 1,
        sectionId = "video",
        headingId = "video/streaming",
        ord = 0,
        name = name,
        url = "https://example.com",
        originalUrl = null,
        description = "Test description",
        tags = emptyList(),
        headingPath = headingPath,
        tier = tier,
        isInlineMore = false,
        isBase64 = false,
        mirrors = emptyList(),
        extraLinks = emptyList(),
        firstSeen = 0L,
        lastChanged = 0L,
        starredAt = if (tier == Tier.STAR) 100L else null,
    )

    @Test
    fun testStarredVerdict() {
        val entry = createEntry("e1", "StarSite", "Video › Streaming", Tier.STAR)
        val verdict = Verdict(
            status = SafetyStatus.STARRED,
            input = "https://starsite.com",
            normalizedUrl = "https://starsite.com",
            host = "starsite.com",
            reason = null,
            matches = listOf(entry),
        )

        val model = SiteBarModel.from(verdict, isRedirectedFromEntry = false)
        assertNotNull(model)
        assertEquals(SiteBarKind.STARRED, model!!.kind)
        assertEquals("Starred on FMHY · Video › Streaming", model.text)
    }

    @Test
    fun testListedVerdict() {
        val entry = createEntry("e2", "ListedSite", "Section › Category", Tier.NORMAL)
        val verdict = Verdict(
            status = SafetyStatus.LISTED,
            input = "https://listedsite.com",
            normalizedUrl = "https://listedsite.com",
            host = "listedsite.com",
            reason = null,
            matches = listOf(entry),
        )

        val model = SiteBarModel.from(verdict, isRedirectedFromEntry = false)
        assertNotNull(model)
        assertEquals(SiteBarKind.LISTED, model!!.kind)
        assertEquals("On FMHY · Section › Category", model.text)
    }

    @Test
    fun testDomainListedVerdict() {
        val entry = createEntry("e-domain", "DomainSite", "Section › Category", Tier.NORMAL)
        val verdict = Verdict(
            status = SafetyStatus.DOMAIN_LISTED,
            input = "https://domainsite.com",
            normalizedUrl = "https://domainsite.com",
            host = "domainsite.com",
            reason = null,
            matches = listOf(entry),
        )

        val model = SiteBarModel.from(verdict, isRedirectedFromEntry = false)
        assertNotNull(model)
        assertEquals(SiteBarKind.DOMAIN_LISTED, model!!.kind)
        assertEquals("Domain on FMHY · not this page", model.text)
    }

    @Test
    fun testFmhySiteVerdict() {
        val verdict = Verdict(
            status = SafetyStatus.FMHY_SITE,
            input = "https://fmhy.net",
            normalizedUrl = "https://fmhy.net",
            host = "fmhy.net",
            reason = null,
            matches = emptyList(),
        )

        val model = SiteBarModel.from(verdict, isRedirectedFromEntry = false)
        assertNotNull(model)
        assertEquals(SiteBarKind.FMHY_SITE, model!!.kind)
        assertEquals("Official FMHY site", model.text)
    }

    @Test
    fun testPotentiallyUnsafeVerdict() {
        val verdict = Verdict(
            status = SafetyStatus.POTENTIALLY_UNSAFE,
            input = "https://shady.com",
            normalizedUrl = "https://shady.com",
            host = "shady.com",
            reason = "Frequent malvertising",
            matches = emptyList(),
        )

        val model = SiteBarModel.from(verdict, isRedirectedFromEntry = false)
        assertNotNull(model)
        assertEquals(SiteBarKind.POTENTIALLY_UNSAFE, model!!.kind)
        assertEquals("Potentially unsafe on FMHY", model.text)
    }

    @Test
    fun testUnsafeVerdict() {
        val verdict = Verdict(
            status = SafetyStatus.UNSAFE,
            input = "https://malware.com",
            normalizedUrl = "https://malware.com",
            host = "malware.com",
            reason = "Known malware distributor",
            matches = emptyList(),
        )

        val model = SiteBarModel.from(verdict, isRedirectedFromEntry = false)
        assertNotNull(model)
        assertEquals(SiteBarKind.UNSAFE, model!!.kind)
        assertEquals("Unsafe on FMHY", model.text)
    }

    @Test
    fun testNoDataAfterRedirectShowsNotOnFmhy() {
        val verdict = Verdict(
            status = SafetyStatus.NO_DATA,
            input = "https://unknown.com",
            normalizedUrl = "https://unknown.com",
            host = "unknown.com",
            reason = null,
            matches = emptyList(),
        )

        val modelAfterRedirect = SiteBarModel.from(verdict, isRedirectedFromEntry = true)
        assertNotNull(modelAfterRedirect)
        assertEquals(SiteBarKind.NOT_ON_FMHY, modelAfterRedirect!!.kind)
        assertEquals("Not on FMHY", modelAfterRedirect.text)

        val modelDirect = SiteBarModel.from(verdict, isRedirectedFromEntry = false)
        assertNull(modelDirect)
    }

    @Test
    fun testDismissedReturnsNull() {
        val entry = createEntry("e1", "StarSite", "Video › Streaming", Tier.STAR)
        val verdict = Verdict(
            status = SafetyStatus.STARRED,
            input = "https://starsite.com",
            normalizedUrl = "https://starsite.com",
            host = "starsite.com",
            reason = null,
            matches = listOf(entry),
        )

        val model = SiteBarModel.from(verdict, isRedirectedFromEntry = false, isDismissed = true)
        assertNull(model)
    }
}
