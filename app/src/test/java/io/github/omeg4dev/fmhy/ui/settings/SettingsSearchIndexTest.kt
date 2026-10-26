package io.github.omeg4dev.fmhy.ui.settings

import io.github.omeg4dev.fmhy.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchIndexTest {

    @Test
    fun blankQueryReturnsEmpty() {
        assertTrue(SettingsSearchIndex.search("").isEmpty())
        assertTrue(SettingsSearchIndex.search("   ").isEmpty())
        assertTrue(SettingsSearchIndex.search("\t\n").isEmpty())
    }

    @Test
    fun nonsenseQueryReturnsEmpty() {
        val results = SettingsSearchIndex.search("zzzzqx")
        assertTrue("Nonsense query should return empty list", results.isEmpty())
    }

    @Test
    fun nameMatchReturnsBlockPopupsFirst() {
        val results = SettingsSearchIndex.search("block pop")
        assertFalse(results.isEmpty())
        assertEquals("protection.block_popups", results.first().entry.id)
    }

    @Test
    fun descriptionMatchReturnsCorrectSetting() {
        // "suppress" appears only in the description of privacyHideCookieBanners
        val results = SettingsSearchIndex.search("suppress")
        assertFalse(results.isEmpty())
        assertEquals("privacy.hide_cookie_banners", results.first().entry.id)
    }

    @Test
    fun keywordMatchesForEveryQueryInSpec() {
        // "popup" -> Block pop-ups (first or top 3)
        val popupResults = SettingsSearchIndex.search("popup")
        assertTrue(popupResults.take(3).any { it.entry.id == "protection.block_popups" })

        // "popups" -> Block pop-ups (first or top 3)
        val popupsResults = SettingsSearchIndex.search("popups")
        assertTrue(popupsResults.take(3).any { it.entry.id == "protection.block_popups" })

        // "dark" -> Theme and Dark pages in dark theme in top 3
        val darkResults = SettingsSearchIndex.search("dark")
        val top3DarkIds = darkResults.take(3).map { it.entry.id }
        assertTrue("Theme must be in top 3 for 'dark'", "appearance.theme" in top3DarkIds)
        assertTrue("Dark pages in dark theme must be in top 3 for 'dark'", "browser.force_dark" in top3DarkIds)

        // "wifi" and "wi-fi" -> the three Wi-Fi-only switches
        val wifiSwitches = setOf(
            "data_usage.download_wifi_only",
            "data_usage.browse_wifi_only",
            "data_usage.app_data_wifi_only",
        )
        val wifiResults = SettingsSearchIndex.search("wifi")
        assertEquals(wifiSwitches, wifiResults.take(3).map { it.entry.id }.toSet())

        val hyphenWifiResults = SettingsSearchIndex.search("wi-fi")
        assertEquals(wifiSwitches, hyphenWifiResults.take(3).map { it.entry.id }.toSet())

        // "cookies" -> Block third-party cookies and Hide cookie banners
        val cookiesResults = SettingsSearchIndex.search("cookies")
        val top3CookiesIds = cookiesResults.take(3).map { it.entry.id }
        assertTrue("Block third-party cookies must be in top 3 for 'cookies'", "privacy.block_third_party_cookies" in top3CookiesIds)
        assertTrue("Hide cookie banners must be in top 3 for 'cookies'", "privacy.hide_cookie_banners" in top3CookiesIds)

        // "ads" -> Block ads and trackers
        val adsResults = SettingsSearchIndex.search("ads")
        assertTrue(adsResults.take(3).any { it.entry.id == "ad_blocking.block_ads_and_trackers" })

        // "history" -> Keep history of opened sites
        val historyResults = SettingsSearchIndex.search("history")
        assertTrue(historyResults.take(3).any { it.entry.id == "privacy.keep_history" })

        // "backup" -> Export backup
        val backupResults = SettingsSearchIndex.search("backup")
        assertTrue(backupResults.take(3).any { it.entry.id == "backup.export_backup" })

        // "update" -> Check for updates
        val updateResults = SettingsSearchIndex.search("update")
        assertTrue(updateResults.take(3).any { it.entry.id == "app_updates.check_now" })

        // "redirect" -> Block redirects without a tap
        val redirectResults = SettingsSearchIndex.search("redirect")
        assertTrue(redirectResults.take(3).any { it.entry.id == "protection.block_redirects" })

        // "zoom" -> Allow zoom on every site
        val zoomResults = SettingsSearchIndex.search("zoom")
        assertTrue(zoomResults.take(3).any { it.entry.id == "browser.force_zoom" })

        // "notification" -> Notifications
        val notifResults = SettingsSearchIndex.search("notification")
        assertTrue(notifResults.take(3).any { it.entry.id == "notifications.enabled" })
    }

    @Test
    fun typoMatchesFindExpectedSettings() {
        // "notifcations" -> Notifications
        val notifResults = SettingsSearchIndex.search("notifcations")
        assertFalse("Typos like 'notifcations' must find results", notifResults.isEmpty())
        assertEquals("notifications.enabled", notifResults.first().entry.id)

        // "javascrpt" -> JavaScript
        val jsResults = SettingsSearchIndex.search("javascrpt")
        assertFalse("Typos like 'javascrpt' must find results", jsResults.isEmpty())
        assertEquals("browser.javascript", jsResults.first().entry.id)

        // "poup" -> Block pop-ups
        val popupResults = SettingsSearchIndex.search("poup")
        assertFalse("Typos like 'poup' must find results", popupResults.isEmpty())
        assertEquals("protection.block_popups", popupResults.first().entry.id)
    }

    @Test
    fun advancedItemsFound() {
        val jsResults = SettingsSearchIndex.search("javascript")
        assertFalse(jsResults.isEmpty())
        val jsEntry = jsResults.firstOrNull { it.entry.id == "browser.javascript" }
        assertTrue("JavaScript entry found", jsEntry != null)
        assertTrue("JavaScript entry is advanced", jsEntry!!.entry.advanced)

        val rulesResults = SettingsSearchIndex.search("custom rules")
        assertFalse(rulesResults.isEmpty())
        val rulesEntry = rulesResults.firstOrNull { it.entry.id == "ad_blocking.custom_rules" }
        assertTrue("Custom rules entry found", rulesEntry != null)
        assertTrue("Custom rules entry is advanced", rulesEntry!!.entry.advanced)
    }

    @Test
    fun resultPathFormatting() {
        val popupResults = SettingsSearchIndex.search("Block pop-ups")
        val popupMatch = popupResults.first { it.entry.id == "protection.block_popups" }
        assertEquals("Safety & privacy › Protection", popupMatch.path)

        val jsResults = SettingsSearchIndex.search("javascript")
        val jsMatch = jsResults.first { it.entry.id == "browser.javascript" }
        assertEquals("Browsing › Browser", jsMatch.path)
    }

    @Test
    fun excludePagesHidesAppUpdatesEntries() {
        val normalResults = SettingsSearchIndex.search("update")
        assertTrue(normalResults.any { it.entry.page == SettingsPage.APP_UPDATES })

        val filteredResults = SettingsSearchIndex.search(
            query = "update",
            excludePages = setOf(SettingsPage.APP_UPDATES),
        )
        assertFalse(filteredResults.any { it.entry.page == SettingsPage.APP_UPDATES })
    }

    @Test
    fun multiTokenAndFilter() {
        val results = SettingsSearchIndex.search("block redirects")
        assertFalse(results.isEmpty())
        assertEquals("protection.block_redirects", results.first().entry.id)
        assertNotEquals(
            "Block pop-ups must not be returned first for 'block redirects'",
            "protection.block_popups",
            results.first().entry.id,
        )
        assertFalse(
            "Block pop-ups must not be present when 'redirects' does not match it",
            results.any { it.entry.id == "protection.block_popups" },
        )
    }

    @Test
    fun titleMatchBeatsDescriptionOnlyMatch() {
        val results = SettingsSearchIndex.search("tabs")
        assertFalse(results.isEmpty())
        val maxTabsIndex = results.indexOfFirst { it.entry.id == "browser.max_tabs" }
        val openLinksWithIndex = results.indexOfFirst { it.entry.id == "browser.open_links_with" }
        assertTrue("browser.max_tabs found", maxTabsIndex >= 0)
        assertTrue("browser.open_links_with found", openLinksWithIndex >= 0)
        assertTrue(
            "Title match (max_tabs) must appear before description-only match (open_links_with)",
            maxTabsIndex < openLinksWithIndex,
        )
    }

    @Test
    fun scrollRevealStateBehaviors() {
        var notifiedState = false
        val state = ScrollRevealState(threshold = 12f, initialRevealed = false) {
            notifiedState = it
        }

        assertFalse(state.isRevealed)

        // Small scroll up does not reveal yet
        state.onScroll(5f)
        assertFalse(state.isRevealed)
        assertFalse(notifiedState)

        // Reaching threshold reveals
        state.onScroll(8f) // accumulated 13f >= 12f
        assertTrue(state.isRevealed)
        assertTrue(notifiedState)

        // Reversing direction resets accumulator before hiding
        state.onScroll(-4f)
        assertTrue(state.isRevealed) // Still revealed, below -12f threshold

        // Reaching threshold downwards hides
        state.onScroll(-9f) // accumulated -13f <= -12f
        assertFalse(state.isRevealed)
        assertFalse(notifiedState)

        // Direct reveal and hide
        state.reveal()
        assertTrue(state.isRevealed)
        state.hide()
        assertFalse(state.isRevealed)
    }
}
