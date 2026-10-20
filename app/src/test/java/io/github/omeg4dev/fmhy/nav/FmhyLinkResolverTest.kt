package io.github.omeg4dev.fmhy.nav

import io.github.omeg4dev.fmhy.HomeNavKey
import io.github.omeg4dev.fmhy.SectionNavKey
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.model.Heading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FmhyLinkResolverTest {

    private val headings = listOf(
        Heading("h1", "video", null, 2, "Streaming Sites", "streaming-sites", 1),
        Heading("h2", "audio", null, 2, "Music Streaming", "music-streaming", 1),
    )
    private val crossLinks = CrossLinks(headings)

    @Test
    fun testHomePageResolvesToHome() {
        assertEquals(FmhyLinkDestination.Navigate(HomeNavKey), resolveFmhyUri("https://fmhy.net", crossLinks))
        assertEquals(FmhyLinkDestination.Navigate(HomeNavKey), resolveFmhyUri("https://fmhy.net/", crossLinks))
        assertEquals(FmhyLinkDestination.Navigate(HomeNavKey), resolveFmhyUri("https://www.fmhy.net", crossLinks))
        assertEquals(FmhyLinkDestination.Navigate(HomeNavKey), resolveFmhyUri("https://www.fmhy.net/", crossLinks))
    }

    @Test
    fun testKnownSectionResolvesToSectionNavKey() {
        val res = resolveFmhyUri("https://fmhy.net/video", crossLinks)
        assertTrue(res is FmhyLinkDestination.Navigate)
        val navKey = (res as FmhyLinkDestination.Navigate).navKey
        assertEquals(SectionNavKey("video", null), navKey)
    }

    @Test
    fun testKnownSectionWithAnchorResolvesWithAnchor() {
        val res = resolveFmhyUri("https://fmhy.net/video#streaming-sites", crossLinks)
        assertTrue(res is FmhyLinkDestination.Navigate)
        val navKey = (res as FmhyLinkDestination.Navigate).navKey
        assertEquals(SectionNavKey("video", "h1"), navKey)
    }

    @Test
    fun testUnknownPathOpensInBrowser() {
        val res = resolveFmhyUri("https://fmhy.net/nonexistent-path", crossLinks)
        assertEquals(FmhyLinkDestination.OpenBrowser("https://fmhy.net/nonexistent-path"), res)
    }

    @Test
    fun testExternalDomainIgnored() {
        assertNull(resolveFmhyUri("https://example.com/video", crossLinks))
        assertNull(resolveFmhyUri("https://google.com", crossLinks))
    }
}
