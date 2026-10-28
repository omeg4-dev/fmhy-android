package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Link
import io.github.omeg4dev.fmhy.data.model.Tier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LookalikeAllowTest {

    private fun entry(url: String, mirrors: List<String> = emptyList()) = Entry(
        id = url,
        num = 1,
        sectionId = "sec",
        headingId = null,
        ord = 1,
        name = url,
        url = url,
        originalUrl = null,
        description = "",
        tags = emptyList(),
        headingPath = "Tools",
        tier = Tier.NORMAL,
        isInlineMore = false,
        isBase64 = false,
        mirrors = mirrors.mapIndexed { i, m -> Link("${i + 1}", m) },
        extraLinks = emptyList(),
        firstSeen = 0L,
        lastChanged = 0L,
        starredAt = null,
    )

    // Real redirects: dropdrive.org -> dropdrive.rip, hydrahd.ws -> hydrahd.gd (listed with mirror hydrahd.com)
    @Test
    fun redirectToSiblingDomainOfListedStartIsAllowed() {
        assertTrue(LookalikeAllow.isStartSite(listOf(entry("https://dropdrive.org/")), "dropdrive.org"))
        assertTrue(LookalikeAllow.isStartSite(listOf(entry("https://dropdrive.org/")), "www.dropdrive.org"))
    }

    @Test
    fun mirrorOfStartCountsAsStartSite() {
        val hydra = entry("https://hydrahd.ws/", mirrors = listOf("https://hydrahd.com/"))
        assertTrue(LookalikeAllow.isStartSite(listOf(hydra), "hydrahd.com"))
    }

    @Test
    fun startOnSubdomainOfListedDomainIsAllowed() {
        assertTrue(LookalikeAllow.isStartSite(listOf(entry("https://dropdrive.org/")), "files.dropdrive.org"))
    }

    @Test
    fun unrelatedLookalikeStaysFlagged() {
        assertFalse(LookalikeAllow.isStartSite(listOf(entry("https://dropdrive.org/")), "evil-site.com"))
        assertFalse(LookalikeAllow.isStartSite(listOf(entry("https://dropdrive.org/")), "dropdrive.rip"))
    }

    @Test
    fun lookalikeOfAnotherListedSiteStaysFlaggedEvenIfStartIsListed() {
        val both = listOf(entry("https://dropdrive.org/"), entry("https://dropdrive-clone.net/"))
        assertFalse(LookalikeAllow.isStartSite(both, "dropdrive.org"))
    }

    @Test
    fun missingStartOrLookalikesStaysFlagged() {
        assertFalse(LookalikeAllow.isStartSite(listOf(entry("https://dropdrive.org/")), null))
        assertFalse(LookalikeAllow.isStartSite(listOf(entry("https://dropdrive.org/")), ""))
        assertFalse(LookalikeAllow.isStartSite(emptyList(), "dropdrive.org"))
    }
}
