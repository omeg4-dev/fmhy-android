package io.github.omeg4dev.fmhy.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.github.omeg4dev.fmhy.nav.BottomBarStyle
import io.github.omeg4dev.fmhy.ui.home.HomeBlock
import io.github.omeg4dev.fmhy.ui.home.HomeLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBackupTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createRepo(scope: TestScope): Pair<DefaultSettingsRepository, DataStore<Preferences>> {
        val file = tmpFolder.newFile("settings_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { file },
        )
        return DefaultSettingsRepository(dataStore) to dataStore
    }

    @Test
    fun everyKeyInSettingsKeysIsInExactlyOneSet() {
        val declaredKeys = SettingsKeys::class.java.declaredFields
            .filter { Preferences.Key::class.java.isAssignableFrom(it.type) }
            .map { field ->
                field.isAccessible = true
                field.get(SettingsKeys) as Preferences.Key<*>
            }

        assertTrue("Expected SettingsKeys to declare keys", declaredKeys.isNotEmpty())

        val backedUp = SettingsBackup.backedUpKeys
        val excluded = SettingsBackup.excludedKeys

        val overlap = backedUp.intersect(excluded)
        assertTrue("Keys in both backedUp and excluded: ${overlap.map { it.name }}", overlap.isEmpty())

        val allCovered = backedUp + excluded
        val missing = declaredKeys.filter { it !in allCovered }
        assertTrue("Keys missing from SettingsBackup: ${missing.map { it.name }}", missing.isEmpty())

        val extra = allCovered.filter { it !in declaredKeys }
        assertTrue("Keys not declared in SettingsKeys: ${extra.map { it.name }}", extra.isEmpty())
    }

    @Test
    fun roundTripAcrossRepositoriesCoversAllSupportedTypes() = runTest {
        val (source, _) = createRepo(this)
        val (target, _) = createRepo(this)

        // Appearance: Boolean, Int, Float, String
        source.setThemeMode(ThemeMode.DARK)
        source.setColorSource(ColorSource.MATERIAL_YOU)
        source.setPureBlack(true)
        source.setAccentSeed(0xFF123456.toInt())
        source.setSectionLayout(SectionLayout.CATEGORIES)
        source.setTextScale(1.25f)
        source.setSectionOrder(SectionOrder.ALPHABETICAL)
        source.setShowRandomPick(false)
        source.setStarredOnly(true)
        source.setShowIndexes(false)
        source.setHapticFeedback(false)
        source.setWebsiteStyle(true)
        source.setAppIcon(AppIcon.UWU)
        source.setBottomBarStyle(BottomBarStyle.FLOATING)

        // Home, Browser, Blocking (including Set<String> and String rules), Notifications, Data
        source.updateHome {
            it.copy(
                blocks = HomeLayout.default().withEnabled(HomeBlock.PINNED, true),
                newBadges = false,
                searchExact = true,
                swipeToBookmark = true,
                swapTapHold = true,
                searchTapTarget = SearchTapTarget.SECTION,
                swipeBetweenScreens = false,
                websiteCategoryNames = false,
                showNsfwLink = true,
            )
        }
        source.updateBrowser {
            it.copy(
                openLinksWith = OpenLinksWith.EXTERNAL,
                maxTabs = 20,
                restoreTabs = true,
                searchEngine = SearchEngine.BRAVE,
                toolbarPosition = ToolbarPosition.BOTTOM,
                browserButton = BrowserButtonStyle.TOP_BAR,
                bubbleSide = BubbleSide.LEFT,
                bubbleY = 0.25f,
                tabsView = TabsView.GRID,
                javascript = false,
                fullscreenOrientation = FullscreenOrientation.LANDSCAPE,
                autoplay = AutoplayMode.ALLOW,
                defaultSpeed = 1.5f,
            )
        }
        source.updateBlocking {
            it.copy(
                adblock = false,
                enabledLists = setOf("easylist", "peter-lowe"),
                customRules = "||ads.example.com^\n@@||good.example.com^",
                allowlist = setOf("trusted.example", "portal.org"),
                showBlockedCount = false,
            )
        }
        source.updateNotifications {
            it.copy(
                enabled = true,
                notifyFollowed = false,
                interval = UpdateInterval.WEEKLY,
            )
        }
        source.updateDataUsage { it.copy(downloadWifiOnly = true, appDataWifiOnly = true) }
        source.updateData { it.copy(syncOnLaunch = false, keepHistory = false) }
        source.setReportOfferCrashes(false)

        val exported = source.exportSettings()
        target.restoreSettings(exported)

        assertEquals(source.appearance.first(), target.appearance.first())
        assertEquals(source.homePrefs.first(), target.homePrefs.first())
        assertEquals(source.browserPrefs.first(), target.browserPrefs.first())
        assertEquals(source.blockingPrefs.first(), target.blockingPrefs.first())
        assertEquals(source.notificationPrefs.first(), target.notificationPrefs.first())
        assertEquals(source.dataUsagePrefs.first(), target.dataUsagePrefs.first())
        assertEquals(source.dataPrefs.first(), target.dataPrefs.first())
        assertEquals(source.reportOfferCrashes.first(), target.reportOfferCrashes.first())
    }

    @Test
    fun targetSettingNeverStoredOnSourceResetsToDefault() = runTest {
        val (source, _) = createRepo(this)
        val (target, _) = createRepo(this)

        // Modify target settings that source never touches
        target.setPureBlack(true)
        target.setWebsiteStyle(true)
        target.updateBrowser { it.copy(maxTabs = 20, defaultSpeed = 2.0f) }
        target.updateBlocking { it.copy(allowlist = setOf("example.com")) }

        // Source only changes themeMode
        source.setThemeMode(ThemeMode.LIGHT)

        val exported = source.exportSettings()
        assertFalse(exported.containsKey(SettingsKeys.KEY_PURE_BLACK.name))

        target.restoreSettings(exported)

        assertEquals(ThemeMode.LIGHT, target.appearance.first().themeMode)
        assertFalse(target.appearance.first().pureBlack)
        assertFalse(target.appearance.first().websiteStyle)
        assertEquals(BrowserPrefs().maxTabs, target.browserPrefs.first().maxTabs)
        assertEquals(BrowserPrefs().defaultSpeed, target.browserPrefs.first().defaultSpeed, 0.0001f)
        assertEquals(emptySet<String>(), target.blockingPrefs.first().allowlist)
    }

    @Test
    fun excludedKeysNeverExportedAndSurviveRestoreAndIgnoredIfPresentInFile() = runTest {
        val (source, _) = createRepo(this)
        val (target, _) = createRepo(this)

        source.updateCheckPrefs {
            it.copy(
                virusTotalOn = true,
                virusTotalKey = "source-secret-vt",
                hybridOn = true,
                hybridKey = "source-secret-ha",
                keyPromptShown = true,
                explainShown = true,
            )
        }
        source.updateDownloadPrefs {
            it.copy(
                folderUri = "content://source/folder",
                folderFallbackNotified = true,
            )
        }
        source.setLastVisitTs(123456L)
        source.addRecentSearch("fmhy")
        source.setOnboardingVersion(5)

        val exported = source.exportSettings()
        assertFalse(exported.containsKey(SettingsKeys.KEY_CHECK_VT_KEY.name))
        assertFalse(exported.containsKey(SettingsKeys.KEY_CHECK_HA_KEY.name))
        assertFalse(exported.containsKey(SettingsKeys.KEY_DOWNLOAD_FOLDER_URI.name))
        assertFalse(exported.containsKey(SettingsKeys.KEY_DOWNLOAD_FOLDER_FALLBACK_NOTIFIED.name))
        assertFalse(exported.containsKey(SettingsKeys.KEY_LAST_VISIT_TS.name))
        assertFalse(exported.containsKey(SettingsKeys.KEY_RECENT_SEARCHES.name))
        assertFalse(exported.containsKey(SettingsKeys.KEY_ONBOARDING_VERSION.name))
        assertFalse(exported.toString().contains("source-secret-vt"))
        assertFalse(exported.toString().contains("source-secret-ha"))

        // Target has its own secrets and download folder
        target.updateCheckPrefs {
            it.copy(
                virusTotalKey = "target-secret-vt",
                hybridKey = "target-secret-ha",
            )
        }
        target.updateDownloadPrefs {
            it.copy(folderUri = "content://target/folder")
        }
        target.setOnboardingVersion(9)

        target.restoreSettings(exported)

        assertTrue(target.checkPrefs.first().virusTotalOn)
        assertEquals("target-secret-vt", target.checkPrefs.first().virusTotalKey)
        assertEquals("target-secret-ha", target.checkPrefs.first().hybridKey)
        assertEquals("content://target/folder", target.downloadPrefs.first().folderUri)
        assertEquals(9, target.onboardingVersion.first())

        // File explicitly forged with excluded keys must also ignore them
        val forged = buildJsonObject {
            put(SettingsKeys.KEY_CHECK_VT_KEY.name, "forged-vt")
            put(SettingsKeys.KEY_CHECK_HA_KEY.name, "forged-ha")
            put(SettingsKeys.KEY_DOWNLOAD_FOLDER_URI.name, "content://forged")
            put(SettingsKeys.KEY_ONBOARDING_VERSION.name, 999)
            put(SettingsKeys.KEY_PURE_BLACK.name, true)
        }
        target.restoreSettings(forged)

        assertEquals("target-secret-vt", target.checkPrefs.first().virusTotalKey)
        assertEquals("target-secret-ha", target.checkPrefs.first().hybridKey)
        assertEquals("content://target/folder", target.downloadPrefs.first().folderUri)
        assertEquals(9, target.onboardingVersion.first())
        assertTrue(target.appearance.first().pureBlack)
    }

    @Test
    fun wrongTypedAndUnknownEntriesAreIgnoredWithoutException() = runTest {
        val (target, rawStore) = createRepo(this)

        val malformed = buildJsonObject {
            // Quoted string where boolean / int / float expected
            put(SettingsKeys.KEY_PURE_BLACK.name, "true")
            put(SettingsKeys.KEY_BROWSER_MAX_TABS.name, "15")
            put(SettingsKeys.KEY_BROWSER_DEFAULT_SPEED.name, "1.5")
            // Wrong primitive types
            put(SettingsKeys.KEY_TEXT_SCALE.name, true)
            put(SettingsKeys.KEY_THEME_MODE.name, 42)
            put(SettingsKeys.KEY_ACCENT_SEED.name, 12.5)
            // Wrong array / element types for Set<String>
            put(SettingsKeys.KEY_BLOCKING_ENABLED_LISTS.name, JsonArray(listOf(JsonPrimitive("easylist"), JsonPrimitive(123))))
            put(SettingsKeys.KEY_BLOCKING_ALLOWLIST.name, "example.com")
            // Overflow float (Infinity)
            put(SettingsKeys.KEY_BROWSER_BUBBLE_Y.name, 1e50)
            // Unknown key
            put("unknown_future_key", "whatever")
            // Valid key alongside invalid ones
            put(SettingsKeys.KEY_WEBSITE_STYLE.name, true)
        }

        target.restoreSettings(malformed)

        assertFalse(target.appearance.first().pureBlack)
        assertEquals(BrowserPrefs().maxTabs, target.browserPrefs.first().maxTabs)
        assertEquals(BrowserPrefs().defaultSpeed, target.browserPrefs.first().defaultSpeed, 0.0001f)
        assertEquals(Appearance().textScale, target.appearance.first().textScale, 0.0001f)
        assertEquals(ThemeMode.SYSTEM, target.appearance.first().themeMode)
        assertEquals(BlockingPrefs().enabledLists, target.blockingPrefs.first().enabledLists)
        assertEquals(emptySet<String>(), target.blockingPrefs.first().allowlist)
        assertEquals(BrowserPrefs().bubbleY, target.browserPrefs.first().bubbleY, 0.0001f)
        assertTrue(target.appearance.first().websiteStyle)
        assertFalse(rawStore.data.first().asMap().keys.any { it.name == "unknown_future_key" })
    }

    @Test
    fun outOfRangeNumbersAreClamped() = runTest {
        val (target, _) = createRepo(this)

        val tooLow = buildJsonObject {
            put(SettingsKeys.KEY_BROWSER_MAX_TABS.name, -5)
            put(SettingsKeys.KEY_BROWSER_DEFAULT_SPEED.name, 0.1f)
            put(SettingsKeys.KEY_TEXT_SCALE.name, 0.2f)
            put(SettingsKeys.KEY_BROWSER_BUBBLE_Y.name, -1.5f)
        }
        target.restoreSettings(tooLow)

        assertEquals(5, target.browserPrefs.first().maxTabs)
        assertEquals(0.5f, target.browserPrefs.first().defaultSpeed, 0.0001f)
        assertEquals(0.85f, target.appearance.first().textScale, 0.0001f)
        assertEquals(0f, target.browserPrefs.first().bubbleY, 0.0001f)

        val tooHigh = buildJsonObject {
            put(SettingsKeys.KEY_BROWSER_MAX_TABS.name, 999)
            put(SettingsKeys.KEY_BROWSER_DEFAULT_SPEED.name, 10.0f)
            put(SettingsKeys.KEY_TEXT_SCALE.name, 5.0f)
            put(SettingsKeys.KEY_BROWSER_BUBBLE_Y.name, 3.0f)
        }
        target.restoreSettings(tooHigh)

        assertEquals(20, target.browserPrefs.first().maxTabs)
        assertEquals(2.0f, target.browserPrefs.first().defaultSpeed, 0.0001f)
        assertEquals(1.30f, target.appearance.first().textScale, 0.0001f)
        assertEquals(1f, target.browserPrefs.first().bubbleY, 0.0001f)
    }
}
