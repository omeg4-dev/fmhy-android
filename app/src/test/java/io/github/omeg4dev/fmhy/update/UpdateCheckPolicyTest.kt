package io.github.omeg4dev.fmhy.update

import io.github.omeg4dev.fmhy.data.UpdateCheckInterval
import io.github.omeg4dev.fmhy.data.UpdatePrefs
import io.github.omeg4dev.fmhy.work.NetworkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {

    private fun makeRelease(version: String = "1.1.0"): ReleaseInfo = ReleaseInfo(
        version = version,
        notes = "Notes for $version",
        apkUrl = "https://example.com/app.apk",
        apkSize = 1000L,
        pageUrl = "https://example.com/release",
    )

    @Test
    fun shouldCheckOnStartup_evaluatesAutoCheck() {
        assertTrue(UpdateCheckPolicy.shouldCheckOnStartup(autoCheck = true))
        assertFalse(UpdateCheckPolicy.shouldCheckOnStartup(autoCheck = false))
    }

    @Test
    fun startupRelease_evaluatesConditions() {
        val now = 1_000_000_000L
        val release = makeRelease("1.1.0")
        val basePrefs = UpdatePrefs(
            autoCheck = true,
            knownRelease = release,
            skippedVersion = null,
            laterUntilMs = 0L,
        )

        // Known newer + not snoozed + not skipped -> that release
        assertEquals(release, UpdateCheckPolicy.startupRelease(basePrefs, installed = "1.0.0", nowMs = now))

        // Snoozed -> null
        val snoozedPrefs = basePrefs.copy(laterUntilMs = now + 10_000L)
        assertNull(UpdateCheckPolicy.startupRelease(snoozedPrefs, installed = "1.0.0", nowMs = now))

        // Later expired -> release
        val expiredLaterPrefs = basePrefs.copy(laterUntilMs = now - 1L)
        assertEquals(release, UpdateCheckPolicy.startupRelease(expiredLaterPrefs, installed = "1.0.0", nowMs = now))

        // Skipped version -> null
        val skippedPrefs = basePrefs.copy(skippedVersion = "1.1.0")
        assertNull(UpdateCheckPolicy.startupRelease(skippedPrefs, installed = "1.0.0", nowMs = now))

        // Not newer -> null
        assertNull(UpdateCheckPolicy.startupRelease(basePrefs, installed = "1.1.0", nowMs = now))
        assertNull(UpdateCheckPolicy.startupRelease(basePrefs, installed = "1.2.0", nowMs = now))

        // AutoCheck off -> null
        val autoCheckOffPrefs = basePrefs.copy(autoCheck = false)
        assertNull(UpdateCheckPolicy.startupRelease(autoCheckOffPrefs, installed = "1.0.0", nowMs = now))

        // KnownRelease null -> null
        val nullReleasePrefs = basePrefs.copy(knownRelease = null)
        assertNull(UpdateCheckPolicy.startupRelease(nullReleasePrefs, installed = "1.0.0", nowMs = now))
    }

    @Test
    fun tapRelease_evaluatesConditions() {
        val now = 1_000_000_000L
        val release = makeRelease("1.1.0")
        val basePrefs = UpdatePrefs(
            autoCheck = false,
            knownRelease = release,
            skippedVersion = null,
            laterUntilMs = now + 10_000L,
        )

        // Matches while snoozed -> release
        assertEquals(release, UpdateCheckPolicy.tapRelease(basePrefs, extraVersion = "1.1.0", installed = "1.0.0"))

        // Skipped -> null
        val skippedPrefs = basePrefs.copy(skippedVersion = "1.1.0")
        assertNull(UpdateCheckPolicy.tapRelease(skippedPrefs, extraVersion = "1.1.0", installed = "1.0.0"))

        // Version mismatch -> null
        assertNull(UpdateCheckPolicy.tapRelease(basePrefs, extraVersion = "1.2.0", installed = "1.0.0"))

        // Extra null -> null
        assertNull(UpdateCheckPolicy.tapRelease(basePrefs, extraVersion = null, installed = "1.0.0"))

        // Not newer -> null
        assertNull(UpdateCheckPolicy.tapRelease(basePrefs, extraVersion = "1.1.0", installed = "1.1.0"))
        assertNull(UpdateCheckPolicy.tapRelease(basePrefs, extraVersion = "1.1.0", installed = "1.2.0"))
    }

    @Test
    fun shouldNotify_evaluatesConditions() {
        val now = 500_000_000L

        // True for new unseen version
        assertTrue(
            UpdateCheckPolicy.shouldNotify(
                available = "1.1.0",
                installed = "1.0.0",
                skipped = null,
                laterUntilMs = 0L,
                notifiedVersion = null,
                nowMs = now,
            )
        )

        // False when skipped
        assertFalse(
            UpdateCheckPolicy.shouldNotify(
                available = "1.1.0",
                installed = "1.0.0",
                skipped = "1.1.0",
                laterUntilMs = 0L,
                notifiedVersion = null,
                nowMs = now,
            )
        )

        // False when snoozed
        assertFalse(
            UpdateCheckPolicy.shouldNotify(
                available = "1.1.0",
                installed = "1.0.0",
                skipped = null,
                laterUntilMs = now + 10_000L,
                notifiedVersion = null,
                nowMs = now,
            )
        )

        // False when notifiedVersion == available
        assertFalse(
            UpdateCheckPolicy.shouldNotify(
                available = "1.1.0",
                installed = "1.0.0",
                skipped = null,
                laterUntilMs = 0L,
                notifiedVersion = "1.1.0",
                nowMs = now,
            )
        )

        // False when not newer (equal or older)
        assertFalse(
            UpdateCheckPolicy.shouldNotify(
                available = "1.0.0",
                installed = "1.0.0",
                skipped = null,
                laterUntilMs = 0L,
                notifiedVersion = null,
                nowMs = now,
            )
        )
        assertFalse(
            UpdateCheckPolicy.shouldNotify(
                available = "0.9.0",
                installed = "1.0.0",
                skipped = null,
                laterUntilMs = 0L,
                notifiedVersion = null,
                nowMs = now,
            )
        )
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
