package io.github.omeg4dev.fmhy.ui.common

import android.os.Build
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.BlockingPrefs
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.DataPrefs
import io.github.omeg4dev.fmhy.data.HomePrefs
import io.github.omeg4dev.fmhy.data.NotificationPrefs
import io.github.omeg4dev.fmhy.data.PrivacyPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.SectionOrder
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.AppIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowNotifyPromptTest {

    private class TestSettingsRepository : SettingsRepository {
        var promptShown: Boolean = false
        var notifications: NotificationPrefs = NotificationPrefs()

        override val lastVisitTs: Flow<Long> get() = error("Not needed")
        override suspend fun setLastVisitTs(ts: Long) {}
        override val recentSearches: Flow<List<String>> get() = error("Not needed")
        override suspend fun addRecentSearch(query: String) {}
        override suspend fun removeRecentSearch(query: String) {}
        override suspend fun clearRecentSearches() {}

        private val _appearance = MutableStateFlow(Appearance())
        override val appearance: Flow<Appearance> = _appearance.asStateFlow()
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setColorSource(source: ColorSource) {}
        override suspend fun setPureBlack(enabled: Boolean) {}
        override suspend fun setAccentSeed(argb: Int) {}
        override suspend fun setSectionLayout(layout: SectionLayout) {}
        override suspend fun setTextScale(scale: Float) {}
        override suspend fun setSectionOrder(order: SectionOrder) {}
        override suspend fun setShowRandomPick(show: Boolean) {}
        override suspend fun setStarredOnly(starredOnly: Boolean) {}
        override suspend fun setShowIndexes(showIndexes: Boolean) {}
        override suspend fun setHapticFeedback(enabled: Boolean) {}
        override suspend fun setWebsiteStyle(enabled: Boolean) {}
        override suspend fun setAppIcon(icon: AppIcon) {}
        override suspend fun setBottomBarStyle(style: io.github.omeg4dev.fmhy.nav.BottomBarStyle) {}

        private val _home = MutableStateFlow(HomePrefs())
        override val homePrefs: Flow<HomePrefs> = _home.asStateFlow()
        override suspend fun updateHome(f: (HomePrefs) -> HomePrefs) {}

        private val _browser = MutableStateFlow(BrowserPrefs())
        override val browserPrefs: Flow<BrowserPrefs> = _browser.asStateFlow()
        override suspend fun updateBrowser(f: (BrowserPrefs) -> BrowserPrefs) {}

        private val _blocking = MutableStateFlow(BlockingPrefs())
        override val blockingPrefs: Flow<BlockingPrefs> = _blocking.asStateFlow()
        override suspend fun updateBlocking(f: (BlockingPrefs) -> BlockingPrefs) {}

        private val _protection = MutableStateFlow(ProtectionPrefs())
        override val protectionPrefs: Flow<ProtectionPrefs> = _protection.asStateFlow()
        override suspend fun updateProtection(f: (ProtectionPrefs) -> ProtectionPrefs) {}

        private val _privacy = MutableStateFlow(PrivacyPrefs())
        override val privacyPrefs: Flow<PrivacyPrefs> = _privacy.asStateFlow()
        override suspend fun updatePrivacy(f: (PrivacyPrefs) -> PrivacyPrefs) {}

        private val _notifications = MutableStateFlow(NotificationPrefs())
        override val notificationPrefs: Flow<NotificationPrefs> = _notifications.asStateFlow()
        override suspend fun updateNotifications(f: (NotificationPrefs) -> NotificationPrefs) {
            notifications = f(notifications)
            _notifications.value = notifications
        }

        override val followNotifyPromptShown: Flow<Boolean>
            get() = MutableStateFlow(promptShown).asStateFlow()

        override suspend fun setFollowNotifyPromptShown(shown: Boolean) {
            promptShown = shown
        }

        private val _data = MutableStateFlow(DataPrefs())
        override val dataPrefs: Flow<DataPrefs> = _data.asStateFlow()
        override suspend fun updateData(f: (DataPrefs) -> DataPrefs) {}

        override val onboardingVersion: Flow<Int> get() = MutableStateFlow(0).asStateFlow()
        override suspend fun setOnboardingVersion(v: Int) {}
    }

    @Test
    fun testPromptStateCheckAndTriggerShowsWhenUnshownAndDisabled() {
        val state = FollowNotifyPromptState(
            promptShown = false,
            notificationPrefs = NotificationPrefs(enabled = false, notifyFollowed = true),
        )
        state.checkAndTrigger()
        assertTrue(state.showPrompt)
    }

    @Test
    fun testPromptStateCheckAndTriggerDoesNotShowWhenAlreadyShown() {
        val state = FollowNotifyPromptState(
            promptShown = true,
            notificationPrefs = NotificationPrefs(enabled = false, notifyFollowed = true),
        )
        state.checkAndTrigger()
        assertFalse(state.showPrompt)
    }

    @Test
    fun testPromptStateCheckAndTriggerDoesNotShowWhenAlreadyEnabledAndFollowing() {
        val state = FollowNotifyPromptState(
            promptShown = false,
            notificationPrefs = NotificationPrefs(enabled = true, notifyFollowed = true),
        )
        state.checkAndTrigger()
        assertFalse(state.showPrompt)
    }

    @Test
    fun testPromptStateCheckAndTriggerShowsWhenEnabledWithoutFollowed() {
        val state = FollowNotifyPromptState(
            promptShown = false,
            notificationPrefs = NotificationPrefs(enabled = true, notifyFollowed = false),
        )
        state.checkAndTrigger()
        assertTrue(state.showPrompt)
    }

    @Test
    fun testConfirmOnAndroid13WithoutPermissionDoesNotDismissImmediately() = runTest {
        val repo = TestSettingsRepository()
        var requestPermissionCalled = false
        var dismissCalled = false

        handleFollowPromptConfirm(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            hasNotificationPermission = false,
            settingsRepository = repo,
            requestPermission = { requestPermissionCalled = true },
            onDismiss = { dismissCalled = true },
        )

        assertTrue("Settings flag should be persisted", repo.promptShown)
        assertTrue("Permission request should be launched", requestPermissionCalled)
        assertFalse(
            "Prompt must NOT dismiss immediately so permission launcher stays alive in composition",
            dismissCalled
        )
        assertFalse("Notifications should not be enabled yet", repo.notifications.enabled)
    }

    @Test
    fun testConfirmOnAndroid13WithPermissionDismissesImmediatelyAndEnables() = runTest {
        val repo = TestSettingsRepository()
        var requestPermissionCalled = false
        var dismissCalled = false

        handleFollowPromptConfirm(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            hasNotificationPermission = true,
            settingsRepository = repo,
            requestPermission = { requestPermissionCalled = true },
            onDismiss = { dismissCalled = true },
        )

        assertTrue(repo.promptShown)
        assertFalse(requestPermissionCalled)
        assertTrue(dismissCalled)
        assertTrue(repo.notifications.enabled)
        assertTrue(repo.notifications.notifyFollowed)
    }

    @Test
    fun testConfirmOnPreAndroid13DismissesImmediatelyAndEnables() = runTest {
        val repo = TestSettingsRepository()
        var requestPermissionCalled = false
        var dismissCalled = false

        handleFollowPromptConfirm(
            sdkInt = Build.VERSION_CODES.S_V2,
            hasNotificationPermission = false,
            settingsRepository = repo,
            requestPermission = { requestPermissionCalled = true },
            onDismiss = { dismissCalled = true },
        )

        assertTrue(repo.promptShown)
        assertFalse(requestPermissionCalled)
        assertTrue(dismissCalled)
        assertTrue(repo.notifications.enabled)
        assertTrue(repo.notifications.notifyFollowed)
    }

    @Test
    fun testPermissionGrantedEnablesNotificationsAndDismisses() = runTest {
        val repo = TestSettingsRepository()
        var dismissCalled = false

        handleFollowPromptPermissionResult(
            isGranted = true,
            settingsRepository = repo,
            onDismiss = { dismissCalled = true },
        )

        assertTrue(dismissCalled)
        assertTrue(repo.notifications.enabled)
        assertTrue(repo.notifications.notifyFollowed)
    }

    @Test
    fun testPermissionDeniedDoesNotEnableNotificationsAndDismisses() = runTest {
        val repo = TestSettingsRepository()
        repo.notifications = NotificationPrefs(enabled = false, notifyFollowed = false)
        var dismissCalled = false

        handleFollowPromptPermissionResult(
            isGranted = false,
            settingsRepository = repo,
            onDismiss = { dismissCalled = true },
        )

        assertTrue("Should dismiss prompt after permission result", dismissCalled)
        assertFalse("Notifications should remain disabled", repo.notifications.enabled)
        assertFalse("notifyFollowed should not be enabled when denied", repo.notifications.notifyFollowed)
    }
}
