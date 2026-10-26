package io.github.omeg4dev.fmhy.update

import io.github.omeg4dev.fmhy.work.NetworkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {

    @Test
    fun shouldCheckOnStartup_timingAndConditions() {
        val now = 100_000_000L

        // Auto-check disabled: never checks
        assertFalse(UpdateCheckPolicy.shouldCheckOnStartup(autoCheck = false, lastCheckMs = 0L, nowMs = now))
        assertFalse(UpdateCheckPolicy.shouldCheckOnStartup(autoCheck = false, lastCheckMs = now - UpdateCheckPolicy.CHECK_INTERVAL_MS * 5, nowMs = now))
        assertFalse(UpdateCheckPolicy.shouldCheckOnStartup(autoCheck = false, lastCheckMs = now + 1000L, nowMs = now))

        // First run (lastCheck <= 0): due
        assertTrue(UpdateCheckPolicy.shouldCheckOnStartup(autoCheck = true, lastCheckMs = 0L, nowMs = now))
        assertTrue(UpdateCheckPolicy.shouldCheckOnStartup(autoCheck = true, lastCheckMs = -1L, nowMs = now))

        // Clock backwards: due
        assertTrue(UpdateCheckPolicy.shouldCheckOnStartup(autoCheck = true, lastCheckMs = now + 5000L, nowMs = now))

        // Exactly 24h: due
        val exactly24hAgo = now - UpdateCheckPolicy.CHECK_INTERVAL_MS
        assertTrue(UpdateCheckPolicy.shouldCheckOnStartup(autoCheck = true, lastCheckMs = exactly24hAgo, nowMs = now))

        // 23h 59m: not due
        val twentyThreeHours59mAgo = now - UpdateCheckPolicy.CHECK_INTERVAL_MS + 60_000L
        assertFalse(UpdateCheckPolicy.shouldCheckOnStartup(autoCheck = true, lastCheckMs = twentyThreeHours59mAgo, nowMs = now))

        // 25h: due
        val twentyFiveHoursAgo = now - UpdateCheckPolicy.CHECK_INTERVAL_MS - 3_600_000L
        assertTrue(UpdateCheckPolicy.shouldCheckOnStartup(autoCheck = true, lastCheckMs = twentyFiveHoursAgo, nowMs = now))
    }

    @Test
    fun laterUntil_addsThreeDays() {
        val now = 50_000_000L
        val expected = now + 3L * 24 * 60 * 60 * 1000
        assertEquals(expected, UpdateCheckPolicy.laterUntil(now))
        assertEquals(UpdateCheckPolicy.LATER_MS, 3L * 24 * 60 * 60 * 1000)
    }

    @Test
    fun shouldShowPrompt_evaluatesNewerSkippedAndLater() {
        val now = 200_000_000L
        val laterTime = now + 10_000L

        // Hidden before laterUntil
        assertFalse(UpdateCheckPolicy.shouldShowPrompt(
            available = "1.1.0",
            installed = "1.0.0",
            skipped = null,
            laterUntilMs = laterTime,
            nowMs = now,
        ))

        // Shown at laterUntil
        assertTrue(UpdateCheckPolicy.shouldShowPrompt(
            available = "1.1.0",
            installed = "1.0.0",
            skipped = null,
            laterUntilMs = now,
            nowMs = now,
        ))

        // Shown after laterUntil
        assertTrue(UpdateCheckPolicy.shouldShowPrompt(
            available = "1.1.0",
            installed = "1.0.0",
            skipped = null,
            laterUntilMs = now - 1000L,
            nowMs = now,
        ))

        // Hidden when skipped (exact or with v-prefix)
        assertFalse(UpdateCheckPolicy.shouldShowPrompt(
            available = "1.1.0",
            installed = "1.0.0",
            skipped = "1.1.0",
            laterUntilMs = now,
            nowMs = now,
        ))
        assertFalse(UpdateCheckPolicy.shouldShowPrompt(
            available = "1.1.0",
            installed = "1.0.0",
            skipped = "v1.1.0",
            laterUntilMs = now,
            nowMs = now,
        ))

        // Hidden when not newer (installed equal or newer)
        assertFalse(UpdateCheckPolicy.shouldShowPrompt(
            available = "1.0.0",
            installed = "1.0.0",
            skipped = null,
            laterUntilMs = now,
            nowMs = now,
        ))
        assertFalse(UpdateCheckPolicy.shouldShowPrompt(
            available = "0.9.0",
            installed = "1.0.0",
            skipped = null,
            laterUntilMs = now,
            nowMs = now,
        ))
    }

    @Test
    fun showSettingsMarker_ignoresLaterAndSkipped() {
        // Available is strictly newer -> true
        assertTrue(UpdateCheckPolicy.showSettingsMarker(available = "1.1.0", installed = "1.0.0"))
        assertTrue(UpdateCheckPolicy.showSettingsMarker(available = "v1.1.0", installed = "1.0.0"))

        // Null available -> false
        assertFalse(UpdateCheckPolicy.showSettingsMarker(available = null, installed = "1.0.0"))

        // Equal version -> false
        assertFalse(UpdateCheckPolicy.showSettingsMarker(available = "1.0.0", installed = "1.0.0"))
        assertFalse(UpdateCheckPolicy.showSettingsMarker(available = "v1.0.0", installed = "1.0"))

        // Installed is newer -> false
        assertFalse(UpdateCheckPolicy.showSettingsMarker(available = "0.9.0", installed = "1.0.0"))

        // Unparseable inputs -> false
        assertFalse(UpdateCheckPolicy.showSettingsMarker(available = "invalid", installed = "1.0.0"))
        assertFalse(UpdateCheckPolicy.showSettingsMarker(available = "1.0.0", installed = "invalid"))
    }

    @Test
    fun networkDecision_fullMatrix() {
        // --- MANUAL (override = false) ---
        // Switch ON
        assertEquals(
            UpdateCheckPolicy.NetworkDecision.PROCEED,
            UpdateCheckPolicy.networkDecision(manual = true, wifiOnlySwitch = true, kind = NetworkKind.WIFI),
        )
        assertEquals(
            UpdateCheckPolicy.NetworkDecision.ASK_MOBILE_DATA,
            UpdateCheckPolicy.networkDecision(manual = true, wifiOnlySwitch = true, kind = NetworkKind.MOBILE),
        )
        assertEquals(
            UpdateCheckPolicy.NetworkDecision.PROCEED,
            UpdateCheckPolicy.networkDecision(manual = true, wifiOnlySwitch = true, kind = NetworkKind.NONE),
        )

        // Switch OFF
        assertEquals(
            UpdateCheckPolicy.NetworkDecision.PROCEED,
            UpdateCheckPolicy.networkDecision(manual = true, wifiOnlySwitch = false, kind = NetworkKind.WIFI),
        )
        assertEquals(
            UpdateCheckPolicy.NetworkDecision.PROCEED,
            UpdateCheckPolicy.networkDecision(manual = true, wifiOnlySwitch = false, kind = NetworkKind.MOBILE),
        )
        assertEquals(
            UpdateCheckPolicy.NetworkDecision.PROCEED,
            UpdateCheckPolicy.networkDecision(manual = true, wifiOnlySwitch = false, kind = NetworkKind.NONE),
        )

        // --- AUTOMATIC (override = false) ---
        // Switch ON
        assertEquals(
            UpdateCheckPolicy.NetworkDecision.PROCEED,
            UpdateCheckPolicy.networkDecision(manual = false, wifiOnlySwitch = true, kind = NetworkKind.WIFI),
        )
        assertEquals(
            UpdateCheckPolicy.NetworkDecision.WAIT_FOR_WIFI,
            UpdateCheckPolicy.networkDecision(manual = false, wifiOnlySwitch = true, kind = NetworkKind.MOBILE),
        )
        assertEquals(
            UpdateCheckPolicy.NetworkDecision.WAIT_FOR_WIFI,
            UpdateCheckPolicy.networkDecision(manual = false, wifiOnlySwitch = true, kind = NetworkKind.NONE),
        )

        // Switch OFF
        assertEquals(
            UpdateCheckPolicy.NetworkDecision.PROCEED,
            UpdateCheckPolicy.networkDecision(manual = false, wifiOnlySwitch = false, kind = NetworkKind.WIFI),
        )
        assertEquals(
            UpdateCheckPolicy.NetworkDecision.PROCEED,
            UpdateCheckPolicy.networkDecision(manual = false, wifiOnlySwitch = false, kind = NetworkKind.MOBILE),
        )
        assertEquals(
            UpdateCheckPolicy.NetworkDecision.PROCEED,
            UpdateCheckPolicy.networkDecision(manual = false, wifiOnlySwitch = false, kind = NetworkKind.NONE),
        )

        // --- OVERRIDE THIS TIME (override = true) -> ALWAYS PROCEED ---
        for (manual in listOf(true, false)) {
            for (switchOn in listOf(true, false)) {
                for (kind in listOf(NetworkKind.WIFI, NetworkKind.MOBILE, NetworkKind.NONE)) {
                    assertEquals(
                        UpdateCheckPolicy.NetworkDecision.PROCEED,
                        UpdateCheckPolicy.networkDecision(
                            manual = manual,
                            wifiOnlySwitch = switchOn,
                            kind = kind,
                            overrideThisTime = true,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun updateState_fractionCalculation() {
        val dummyRelease = ReleaseInfo(
            version = "1.0.0",
            notes = null,
            apkUrl = "https://example.com/app.apk",
            apkSize = 1000L,
            pageUrl = "https://example.com",
        )

        // total <= 0 -> null
        assertNull(UpdateState.Downloading(dummyRelease, bytes = 0L, total = 0L).fraction)
        assertNull(UpdateState.Downloading(dummyRelease, bytes = 100L, total = -1L).fraction)

        // Valid progress
        assertEquals(0.5f, UpdateState.Downloading(dummyRelease, bytes = 50L, total = 100L).fraction ?: 0f, 0.0001f)
        assertEquals(0.0f, UpdateState.Downloading(dummyRelease, bytes = 0L, total = 100L).fraction ?: 1f, 0.0001f)
        assertEquals(1.0f, UpdateState.Downloading(dummyRelease, bytes = 100L, total = 100L).fraction ?: 0f, 0.0001f)

        // Coerced bounds
        assertEquals(1.0f, UpdateState.Downloading(dummyRelease, bytes = 150L, total = 100L).fraction ?: 0f, 0.0001f)
        assertEquals(0.0f, UpdateState.Downloading(dummyRelease, bytes = -10L, total = 100L).fraction ?: 1f, 0.0001f)
    }
}
