package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import io.github.omeg4dev.fmhy.data.safety.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InterstitialMergeTest {

    private fun verdict(status: SafetyStatus, url: String, host: String, reason: String? = null): Verdict {
        return Verdict(
            status = status,
            input = url,
            normalizedUrl = url,
            host = host,
            reason = reason,
            matches = emptyList(),
        )
    }

    @Test
    fun testUnsafeAndFilterRule() {
        val host = "unsafe.com"
        val url = "https://unsafe.com/page"
        val v = verdict(SafetyStatus.UNSAFE, url, host, "Fake clone - https://i.imgur.com/abc.png")
        val state = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = v,
            unsafeWarning = true,
            potentiallyUnsafeWarning = true,
            hostAllowed = false,
            filterRule = "||unsafe.com^",
        )
        assertNotNull(state)
        assertEquals(listOf(WarningReason.UNSAFE, WarningReason.FILTER_BLOCK), state!!.reasons)
        assertEquals("Fake clone", state.note)
        assertEquals("||unsafe.com^", state.filterRule)
        assertEquals(host, state.host)
        assertEquals(url, state.url)
    }

    @Test
    fun testUnsafeNoFilter() {
        val host = "unsafe.com"
        val url = "https://unsafe.com/page"
        val v = verdict(SafetyStatus.UNSAFE, url, host, "Malicious")
        val state = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = v,
            unsafeWarning = true,
            potentiallyUnsafeWarning = true,
            hostAllowed = false,
            filterRule = null,
        )
        assertNotNull(state)
        assertEquals(listOf(WarningReason.UNSAFE), state!!.reasons)
        assertEquals("Malicious", state.note)
        assertNull(state.filterRule)
    }

    @Test
    fun testFilterOnlyListed() {
        val host = "listed.com"
        val url = "https://listed.com/page"
        val v = verdict(SafetyStatus.LISTED, url, host, "Listed site")
        val state = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = v,
            unsafeWarning = true,
            potentiallyUnsafeWarning = true,
            hostAllowed = false,
            filterRule = "||listed.com^",
        )
        assertNotNull(state)
        assertEquals(listOf(WarningReason.FILTER_BLOCK), state!!.reasons)
        assertNull(state.note)
        assertEquals("||listed.com^", state.filterRule)
    }

    @Test
    fun testVerdictNull() {
        val host = "example.com"
        val url = "https://example.com"
        val withFilter = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = null,
            unsafeWarning = true,
            potentiallyUnsafeWarning = true,
            hostAllowed = false,
            filterRule = "||example.com^",
        )
        assertNotNull(withFilter)
        assertEquals(listOf(WarningReason.FILTER_BLOCK), withFilter!!.reasons)
        assertNull(withFilter.note)
        assertEquals("||example.com^", withFilter.filterRule)

        val noFilter = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = null,
            unsafeWarning = true,
            potentiallyUnsafeWarning = true,
            hostAllowed = false,
            filterRule = null,
        )
        assertNull(noFilter)
    }

    @Test
    fun testHostAllowed() {
        val host = "unsafe.com"
        val url = "https://unsafe.com"
        val v = verdict(SafetyStatus.UNSAFE, url, host, "Unsafe reason")

        val noFilter = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = v,
            unsafeWarning = true,
            potentiallyUnsafeWarning = true,
            hostAllowed = true,
            filterRule = null,
        )
        assertNull(noFilter)

        val withFilter = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = v,
            unsafeWarning = true,
            potentiallyUnsafeWarning = true,
            hostAllowed = true,
            filterRule = "||unsafe.com^",
        )
        assertNotNull(withFilter)
        assertEquals(listOf(WarningReason.FILTER_BLOCK), withFilter!!.reasons)
        assertNull(withFilter.note)
        assertEquals("||unsafe.com^", withFilter.filterRule)
    }

    @Test
    fun testUnsafeWarningPrefDisabled() {
        val host = "unsafe.com"
        val url = "https://unsafe.com"
        val v = verdict(SafetyStatus.UNSAFE, url, host, "Unsafe reason")

        val noFilter = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = v,
            unsafeWarning = false,
            potentiallyUnsafeWarning = true,
            hostAllowed = false,
            filterRule = null,
        )
        assertNull(noFilter)

        val withFilter = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = v,
            unsafeWarning = false,
            potentiallyUnsafeWarning = true,
            hostAllowed = false,
            filterRule = "||unsafe.com^",
        )
        assertNotNull(withFilter)
        assertEquals(listOf(WarningReason.FILTER_BLOCK), withFilter!!.reasons)
        assertNull(withFilter.note)
        assertEquals("||unsafe.com^", withFilter.filterRule)
    }

    @Test
    fun testPotentiallyUnsafe() {
        val host = "maybe.com"
        val url = "https://maybe.com"
        val v = verdict(SafetyStatus.POTENTIALLY_UNSAFE, url, host, "Caution")

        val prefOn = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = v,
            unsafeWarning = true,
            potentiallyUnsafeWarning = true,
            hostAllowed = false,
            filterRule = null,
        )
        assertNotNull(prefOn)
        assertEquals(listOf(WarningReason.POTENTIALLY_UNSAFE), prefOn!!.reasons)
        assertEquals("Caution", prefOn.note)

        val prefOff = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = v,
            unsafeWarning = true,
            potentiallyUnsafeWarning = false,
            hostAllowed = false,
            filterRule = null,
        )
        assertNull(prefOff)
    }

    @Test
    fun testNoteStripsRawLinks() {
        val host = "unsafe.com"
        val url = "https://unsafe.com"

        val v1 = verdict(SafetyStatus.UNSAFE, url, host, "Fake clone - https://i.imgur.com/abc.png")
        val state1 = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = v1,
            unsafeWarning = true,
            potentiallyUnsafeWarning = true,
            hostAllowed = false,
            filterRule = null,
        )
        assertNotNull(state1)
        assertEquals("Fake clone", state1!!.note)

        val v2 = verdict(SafetyStatus.UNSAFE, url, host, "https://i.imgur.com/abc.png")
        val state2 = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = v2,
            unsafeWarning = true,
            potentiallyUnsafeWarning = true,
            hostAllowed = false,
            filterRule = null,
        )
        assertNotNull(state2)
        assertNull(state2!!.note)
    }

    @Test
    fun testLookalikeNoDataVerdict() {
        val host = "nxfilte.org"
        val url = "https://nxfilte.org/path"
        val entry = Entry(
            id = "e1",
            num = 1,
            sectionId = "sec",
            headingId = null,
            ord = 1,
            name = "NxFilter",
            url = "https://nxfilter.org",
            originalUrl = null,
            description = "",
            tags = emptyList(),
            headingPath = "Tools",
            tier = Tier.NORMAL,
            isInlineMore = false,
            isBase64 = false,
            mirrors = emptyList(),
            extraLinks = emptyList(),
            firstSeen = 0L,
            lastChanged = 0L,
            starredAt = null,
        )
        val v = Verdict(
            status = SafetyStatus.NO_DATA,
            input = url,
            normalizedUrl = url,
            host = host,
            reason = null,
            matches = emptyList(),
            lookalikes = listOf(entry),
        )
        val state = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = v,
            unsafeWarning = true,
            potentiallyUnsafeWarning = true,
            hostAllowed = false,
            filterRule = null,
        )
        assertNotNull(state)
        assertEquals(listOf(WarningReason.LOOKALIKE), state!!.reasons)
        assertNull(state.note)

        val stateDisabled = InterstitialMerge.combine(
            host = host,
            url = url,
            verdict = v,
            unsafeWarning = true,
            potentiallyUnsafeWarning = false,
            hostAllowed = false,
            filterRule = null,
        )
        assertNull(stateDisabled)
    }

    @Test
    fun testSentence() {
        assertEquals("FMHY lists this site as unsafe", InterstitialMerge.sentence(WarningReason.UNSAFE))
        assertEquals("FMHY flags this site as potentially unsafe", InterstitialMerge.sentence(WarningReason.POTENTIALLY_UNSAFE))
        assertEquals("An ad-blocking filter blocks this site", InterstitialMerge.sentence(WarningReason.FILTER_BLOCK))
        assertEquals("looks like a fake copy of a site FMHY lists", InterstitialMerge.sentence(WarningReason.LOOKALIKE))
    }
}
