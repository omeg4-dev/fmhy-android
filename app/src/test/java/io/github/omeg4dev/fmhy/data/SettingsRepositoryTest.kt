package io.github.omeg4dev.fmhy.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.omeg4dev.fmhy.data.adblock.FilterListCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createRepository(testScope: TestScope): Pair<DefaultSettingsRepository, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>> {
        val testFile = tmpFolder.newFile("test_settings_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { testFile },
        )
        return DefaultSettingsRepository(dataStore) to dataStore
    }

    @Test
    fun testHomePrefsRoundTrip() = runTest {
        val (repo, _) = createRepository(this)
        val expected = HomePrefs(personalHome = true, newBadgeDays = 14)
        repo.updateHome { expected }
        assertEquals(expected, repo.homePrefs.first())
    }

    @Test
    fun testBrowserPrefsRoundTrip() = runTest {
        val (repo, _) = createRepository(this)
        val expected = BrowserPrefs(
            openLinksWith = OpenLinksWith.CUSTOM_TAB,
            siteBar = false,
            deadSiteRescue = false,
            maxTabs = 20,
            restoreTabs = true,
            searchEngine = SearchEngine.STARTPAGE,
            javascript = false,
            forceDark = false,
            desktopByDefault = true,
            textZoomFollowsApp = false,
            videoAutoRotate = false,
            pictureInPicture = false,
            autoPip = false,
            keepScreenOn = false,
        )
        repo.updateBrowser { expected }
        assertEquals(expected, repo.browserPrefs.first())
    }

    @Test
    fun testBlockingPrefsRoundTrip() = runTest {
        val (repo, _) = createRepository(this)
        val expected = BlockingPrefs(
            adblock = false,
            enabledLists = setOf("easylist", "peter-lowe"),
            customRules = "||example.com^\n@@||allowed.com^",
            allowlist = setOf("trusted.com", "myportal.org"),
            showBlockedCount = false,
            elementHiding = false,
            scriptlets = false,
            autoUpdateLists = false,
        )
        repo.updateBlocking { expected }
        assertEquals(expected, repo.blockingPrefs.first())
    }

    @Test
    fun testProtectionPrefsRoundTrip() = runTest {
        val (repo, _) = createRepository(this)
        val expected = ProtectionPrefs(
            unsafeWarning = false,
            potentiallyUnsafeWarning = false,
            popupBlocker = false,
            redirectGuard = false,
            blockAppLinks = false,
            askBeforeDownload = false,
            warnRiskyDownloads = false,
            safeBrowsing = false,
        )
        repo.updateProtection { expected }
        assertEquals(expected, repo.protectionPrefs.first())
    }

    @Test
    fun testPrivacyPrefsRoundTrip() = runTest {
        val (repo, _) = createRepository(this)
        val expected = PrivacyPrefs(
            blockThirdPartyCookies = false,
            hideCookieBanners = true,
            clearDataOnExit = true,
            privateTabsByDefault = true,
            sendGpc = false,
        )
        repo.updatePrivacy { expected }
        assertEquals(expected, repo.privacyPrefs.first())
    }

    @Test
    fun testNotificationPrefsRoundTrip() = runTest {
        val (repo, _) = createRepository(this)
        val expected = NotificationPrefs(
            useTracker = false,
            enabled = true,
            notifyFollowed = false,
            watchSaved = false,
            interval = UpdateInterval.WEEKLY,
            wifiOnly = true,
        )
        repo.updateNotifications { expected }
        assertEquals(expected, repo.notificationPrefs.first())
    }

    @Test
    fun testDataPrefsRoundTrip() = runTest {
        val (repo, _) = createRepository(this)
        val expected = DataPrefs(
            syncOnLaunch = false,
            keepHistory = false,
        )
        repo.updateData { expected }
        assertEquals(expected, repo.dataPrefs.first())
    }

    @Test
    fun testOnboardingVersionRoundTrip() = runTest {
        val (repo, _) = createRepository(this)
        assertEquals(0, repo.onboardingVersion.first())
        repo.setOnboardingVersion(42)
        assertEquals(42, repo.onboardingVersion.first())
    }

    @Test
    fun testAppIconRoundTrip() = runTest {
        val (repo, _) = createRepository(this)
        assertEquals(AppIcon.DEFAULT, repo.appearance.first().appIcon)
        repo.setAppIcon(AppIcon.UWU)
        assertEquals(AppIcon.UWU, repo.appearance.first().appIcon)
    }

    @Test
    fun testMigration_whenOpenInExternalBrowserIsTrue_andOpenLinksWithAbsent_yieldsExternal() = runTest {
        val (repo, dataStore) = createRepository(this)
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("open_in_external_browser")] = true
        }
        assertEquals(OpenLinksWith.EXTERNAL, repo.browserPrefs.first().openLinksWith)
    }

    @Test
    fun testMigration_whenOpenInExternalBrowserIsFalse_andOpenLinksWithAbsent_yieldsInApp() = runTest {
        val (repo, dataStore) = createRepository(this)
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("open_in_external_browser")] = false
        }
        assertEquals(OpenLinksWith.IN_APP, repo.browserPrefs.first().openLinksWith)
    }

    @Test
    fun testMigration_whenBothAbsent_yieldsInApp() = runTest {
        val (repo, _) = createRepository(this)
        assertEquals(OpenLinksWith.IN_APP, repo.browserPrefs.first().openLinksWith)
    }

    @Test
    fun testMigration_leavesOldKeyInStorage() = runTest {
        val (repo, dataStore) = createRepository(this)
        val oldKey = booleanPreferencesKey("open_in_external_browser")
        dataStore.edit { prefs ->
            prefs[oldKey] = true
        }
        repo.updateBrowser { it.copy(siteBar = false) }
        val prefs = dataStore.data.first()
        assertTrue(prefs.contains(oldKey))
        assertEquals(true, prefs[oldKey])
    }

    @Test
    fun testCookieCoupling_updatePrivacyToTrue_addsCookieList() = runTest {
        val (repo, _) = createRepository(this)
        assertFalse(repo.blockingPrefs.first().enabledLists.contains(FilterListCatalog.COOKIE_LIST_ID))
        assertFalse(repo.privacyPrefs.first().hideCookieBanners)

        repo.updatePrivacy { it.copy(hideCookieBanners = true) }
        assertTrue(repo.privacyPrefs.first().hideCookieBanners)
        assertTrue(repo.blockingPrefs.first().enabledLists.contains(FilterListCatalog.COOKIE_LIST_ID))
    }

    @Test
    fun testCookieCoupling_updatePrivacyToFalse_removesCookieList() = runTest {
        val (repo, _) = createRepository(this)
        repo.updatePrivacy { it.copy(hideCookieBanners = true) }
        assertTrue(repo.blockingPrefs.first().enabledLists.contains(FilterListCatalog.COOKIE_LIST_ID))

        repo.updatePrivacy { it.copy(hideCookieBanners = false) }
        assertFalse(repo.privacyPrefs.first().hideCookieBanners)
        assertFalse(repo.blockingPrefs.first().enabledLists.contains(FilterListCatalog.COOKIE_LIST_ID))
    }

    @Test
    fun testCookieCoupling_updateBlockingAddCookieList_setsHideCookieBannersTrue() = runTest {
        val (repo, _) = createRepository(this)
        assertFalse(repo.privacyPrefs.first().hideCookieBanners)

        repo.updateBlocking { it.copy(enabledLists = it.enabledLists + FilterListCatalog.COOKIE_LIST_ID) }
        assertTrue(repo.privacyPrefs.first().hideCookieBanners)
        assertTrue(repo.blockingPrefs.first().enabledLists.contains(FilterListCatalog.COOKIE_LIST_ID))
    }

    @Test
    fun testCookieCoupling_updateBlockingRemoveCookieList_setsHideCookieBannersFalse() = runTest {
        val (repo, _) = createRepository(this)
        repo.updateBlocking { it.copy(enabledLists = it.enabledLists + FilterListCatalog.COOKIE_LIST_ID) }
        assertTrue(repo.privacyPrefs.first().hideCookieBanners)

        repo.updateBlocking { it.copy(enabledLists = it.enabledLists - FilterListCatalog.COOKIE_LIST_ID) }
        assertFalse(repo.privacyPrefs.first().hideCookieBanners)
        assertFalse(repo.blockingPrefs.first().enabledLists.contains(FilterListCatalog.COOKIE_LIST_ID))
    }

    @Test
    fun testUnknownEnumStringFallsBackToDefault() = runTest {
        val (repo, dataStore) = createRepository(this)
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("browser_open_links_with")] = "INVALID_CHOICE"
            prefs[stringPreferencesKey("browser_search_engine")] = "INVALID_ENGINE"
            prefs[stringPreferencesKey("notifications_interval")] = "INVALID_INTERVAL"
            prefs[stringPreferencesKey("app_icon")] = "INVALID_ICON"
        }
        val browser = repo.browserPrefs.first()
        assertEquals(OpenLinksWith.IN_APP, browser.openLinksWith)
        assertEquals(SearchEngine.DUCKDUCKGO, browser.searchEngine)

        val notif = repo.notificationPrefs.first()
        assertEquals(UpdateInterval.DAILY, notif.interval)

        val app = repo.appearance.first()
        assertEquals(AppIcon.DEFAULT, app.appIcon)
    }
}
