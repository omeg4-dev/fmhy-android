package io.github.omeg4dev.fmhy.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisitTrackerTest {

    @Test
    fun testFirstStart_newVisitWithPreviousStored() {
        val decision = nextVisit(
            storedStart = 1000L,
            nowSec = 2000L,
            lastBackgroundSec = null,
            hasStartedBefore = false,
        )
        assertTrue(decision is VisitDecision.NewVisit)
        val newVisit = decision as VisitDecision.NewVisit
        assertEquals(1000L, newVisit.cutoffTs)
        assertEquals(2000L, newVisit.newVisitStartTs)
    }

    @Test
    fun testStoredZero_cutoffZero() {
        val decision = nextVisit(
            storedStart = 0L,
            nowSec = 2000L,
            lastBackgroundSec = null,
            hasStartedBefore = false,
        )
        assertTrue(decision is VisitDecision.NewVisit)
        val newVisit = decision as VisitDecision.NewVisit
        assertEquals(0L, newVisit.cutoffTs)
        assertEquals(2000L, newVisit.newVisitStartTs)
    }

    @Test
    fun testBackgroundLessThan30Min_sameVisit() {
        val lastBackground = 2000L
        val nowSec = lastBackground + 15 * 60 // 15 min later
        val decision = nextVisit(
            storedStart = 1000L,
            nowSec = nowSec,
            lastBackgroundSec = lastBackground,
            hasStartedBefore = true,
        )
        assertEquals(VisitDecision.ContinueVisit, decision)
    }

    @Test
    fun testBackgroundMoreThan30Min_newVisit() {
        val lastBackground = 2000L
        val nowSec = lastBackground + 31 * 60 // 31 min later (> 30 min)
        val decision = nextVisit(
            storedStart = 2000L,
            nowSec = nowSec,
            lastBackgroundSec = lastBackground,
            hasStartedBefore = true,
        )
        assertTrue(decision is VisitDecision.NewVisit)
        val newVisit = decision as VisitDecision.NewVisit
        assertEquals(2000L, newVisit.cutoffTs)
        assertEquals(nowSec, newVisit.newVisitStartTs)
    }

    @Test
    fun testCutoffStaysFrozenAcrossRepeatedCallsInOneVisit() = runTest {
        val testScope = TestScope(testScheduler)
        val fakeSettings = TestSettingsRepository(initialLastVisit = 1000L)
        var currentTime = 2000L
        val tracker = DefaultVisitTracker(
            settingsRepository = fakeSettings,
            scope = testScope,
            clock = { currentTime },
        )

        assertEquals(0L, tracker.visitCutoffTs.value)

        // First start
        tracker.recordStart(currentTime)
        assertEquals(1000L, tracker.visitCutoffTs.value)
        assertEquals(2000L, fakeSettings.storedLastVisit)

        // Repeated start in same session
        currentTime = 2050L
        tracker.recordStart(currentTime)
        assertEquals(1000L, tracker.visitCutoffTs.value)
        assertEquals(2000L, fakeSettings.storedLastVisit) // not re-persisted

        // Background for 10 minutes (< 30 min)
        tracker.recordStop(2100L)
        currentTime = 2100L + 10 * 60
        tracker.recordStart(currentTime)
        assertEquals(1000L, tracker.visitCutoffTs.value)
        assertEquals(2000L, fakeSettings.storedLastVisit)

        // Background for 35 minutes (> 30 min) -> New visit!
        val bgTime = currentTime + 60
        tracker.recordStop(bgTime)
        currentTime = bgTime + 35 * 60
        tracker.recordStart(currentTime)
        // Previous visit was 2000L, so cutoff is now frozen at 2000L
        assertEquals(2000L, tracker.visitCutoffTs.value)
        assertEquals(currentTime, fakeSettings.storedLastVisit)
    }

    private class TestSettingsRepository(initialLastVisit: Long) : SettingsRepository {
        var storedLastVisit: Long = initialLastVisit
        private val _lastVisit = MutableStateFlow(initialLastVisit)

        override val lastVisitTs: Flow<Long> = _lastVisit.asStateFlow()
        override suspend fun setLastVisitTs(ts: Long) {
            storedLastVisit = ts
            _lastVisit.value = ts
        }

        override val recentSearches: Flow<List<String>> get() = error("Not needed")
        override suspend fun addRecentSearch(query: String) {}
        override suspend fun removeRecentSearch(query: String) {}
        override suspend fun clearRecentSearches() {}

        override val appearance: Flow<Appearance> get() = error("Not needed")
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

        override val homePrefs: Flow<HomePrefs> get() = error("Not needed")
        override suspend fun updateHome(f: (HomePrefs) -> HomePrefs) {}

        override val browserPrefs: Flow<BrowserPrefs> get() = error("Not needed")
        override suspend fun updateBrowser(f: (BrowserPrefs) -> BrowserPrefs) {}

        override val blockingPrefs: Flow<BlockingPrefs> get() = error("Not needed")
        override suspend fun updateBlocking(f: (BlockingPrefs) -> BlockingPrefs) {}

        override val protectionPrefs: Flow<ProtectionPrefs> get() = error("Not needed")
        override suspend fun updateProtection(f: (ProtectionPrefs) -> ProtectionPrefs) {}

        override val privacyPrefs: Flow<PrivacyPrefs> get() = error("Not needed")
        override suspend fun updatePrivacy(f: (PrivacyPrefs) -> PrivacyPrefs) {}

        override val notificationPrefs: Flow<NotificationPrefs> get() = error("Not needed")
        override suspend fun updateNotifications(f: (NotificationPrefs) -> NotificationPrefs) {}

        override val dataPrefs: Flow<DataPrefs> get() = error("Not needed")
        override suspend fun updateData(f: (DataPrefs) -> DataPrefs) {}

        override val onboardingVersion: Flow<Int> get() = error("Not needed")
        override suspend fun setOnboardingVersion(v: Int) {}
    }
}
