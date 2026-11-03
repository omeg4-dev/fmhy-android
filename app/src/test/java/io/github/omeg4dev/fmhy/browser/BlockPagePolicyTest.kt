package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockPagePolicyTest {

    @Test
    fun blockPageKeepsTabUrlWhenWebViewReportsAboutBlank() {
        // Regression: loadDataWithBaseURL(null, ...) reports about:blank to doUpdateVisitedHistory;
        // the tab must keep the blocked address instead of turning into an empty tab.
        assertTrue(BlockPagePolicy.keepsTabUrl(showsBlockPage = true, reportedUrl = "about:blank"))
    }

    @Test
    fun blockPageKeepsTabUrlForDataUrls() {
        assertTrue(BlockPagePolicy.keepsTabUrl(showsBlockPage = true, reportedUrl = "data:text/html;charset=utf-8,<html>"))
    }

    @Test
    fun blockPageStillFollowsRealNavigation() {
        // Leaving the block page for a real site (e.g. back to the previous page) updates the URL.
        assertFalse(BlockPagePolicy.keepsTabUrl(showsBlockPage = true, reportedUrl = "https://example.com/a"))
    }

    @Test
    fun normalPagesAreNeverHeld() {
        assertFalse(BlockPagePolicy.keepsTabUrl(showsBlockPage = false, reportedUrl = "about:blank"))
        assertFalse(BlockPagePolicy.keepsTabUrl(showsBlockPage = false, reportedUrl = "https://example.com"))
    }

    @Test
    fun generatedPagesHaveNoRealOrigin() {
        assertTrue(BlockPagePolicy.isGeneratedPage(null))
        assertTrue(BlockPagePolicy.isGeneratedPage("about:blank"))
        assertTrue(BlockPagePolicy.isGeneratedPage("data:text/html;charset=utf-8,x"))
    }

    @Test
    fun realSitesAreNotGeneratedPages() {
        // A real site must not be able to call the block-page bridge actions.
        assertFalse(BlockPagePolicy.isGeneratedPage("https://evil.example/"))
    }
}
