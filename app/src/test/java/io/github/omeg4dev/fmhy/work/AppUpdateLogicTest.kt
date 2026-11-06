package io.github.omeg4dev.fmhy.work

import io.github.omeg4dev.fmhy.data.UpdatePrefs
import io.github.omeg4dev.fmhy.update.ReleaseInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateLogicTest {

    private val nowMs = 1_000_000_000L

    private fun makeRelease(version: String, notes: String? = null): ReleaseInfo {
        return ReleaseInfo(
            version = version,
            notes = notes,
            apkUrl = "https://example.com/app.apk",
            apkSize = 1000L,
            pageUrl = "https://example.com/release",
        )
    }

    @Test
    fun decide_noRelease_returnsNoUpdate() {
        val result = AppUpdateLogic.decide(
            release = null,
            installed = "1.0.0",
            prefs = UpdatePrefs(),
            nowMs = nowMs,
        )
        assertEquals(AppUpdateDecision.NoUpdate, result)
    }

    @Test
    fun decide_sameOrOlderVersion_returnsNoUpdate() {
        val sameResult = AppUpdateLogic.decide(
            release = makeRelease("1.0.0"),
            installed = "1.0.0",
            prefs = UpdatePrefs(),
            nowMs = nowMs,
        )
        assertEquals(AppUpdateDecision.NoUpdate, sameResult)

        val olderResult = AppUpdateLogic.decide(
            release = makeRelease("0.9.0"),
            installed = "1.0.0",
            prefs = UpdatePrefs(),
            nowMs = nowMs,
        )
        assertEquals(AppUpdateDecision.NoUpdate, olderResult)
    }

    @Test
    fun decide_newerVersion_returnsNotify() {
        val release = makeRelease("1.2.0", notes = "## What's new\n- Faster downloads")
        val result = AppUpdateLogic.decide(
            release = release,
            installed = "1.0.0",
            prefs = UpdatePrefs(),
            nowMs = nowMs,
        )
        assertTrue(result is AppUpdateDecision.Notify)
        val notify = result as AppUpdateDecision.Notify
        assertEquals("1.2.0", notify.version)
        assertEquals("FMHY 1.2.0 is available", notify.title)
        assertEquals("What's new", notify.text)
    }

    @Test
    fun decide_newerButSkipped_returnsUpdateKnown() {
        val release = makeRelease("1.2.0")
        val result = AppUpdateLogic.decide(
            release = release,
            installed = "1.0.0",
            prefs = UpdatePrefs(skippedVersion = "1.2.0"),
            nowMs = nowMs,
        )
        assertEquals(AppUpdateDecision.UpdateKnown("1.2.0"), result)
    }

    @Test
    fun decide_newerAlreadyNotified_returnsUpdateKnown() {
        val release = makeRelease("1.2.0")
        val result = AppUpdateLogic.decide(
            release = release,
            installed = "1.0.0",
            prefs = UpdatePrefs(notifiedVersion = "1.2.0"),
            nowMs = nowMs,
        )
        assertEquals(AppUpdateDecision.UpdateKnown("1.2.0"), result)
    }

    @Test
    fun decide_newerSnoozed_returnsUpdateKnown() {
        val release = makeRelease("1.2.0")
        val result = AppUpdateLogic.decide(
            release = release,
            installed = "1.0.0",
            prefs = UpdatePrefs(laterUntilMs = nowMs + 10_000L),
            nowMs = nowMs,
        )
        assertEquals(AppUpdateDecision.UpdateKnown("1.2.0"), result)
    }

    @Test
    fun extractNotificationText_stripsMarkdownMarkers() {
        assertEquals("What's new", AppUpdateLogic.extractNotificationText("## What's new\n- Faster"))
        assertEquals("Faster downloads", AppUpdateLogic.extractNotificationText("- Faster downloads"))
        assertEquals("Performance improvements", AppUpdateLogic.extractNotificationText("* Performance improvements"))
        assertEquals("New design", AppUpdateLogic.extractNotificationText("\n\n   ###  New design\n- details"))
    }

    @Test
    fun extractNotificationText_fallbackText() {
        assertEquals("Tap to update", AppUpdateLogic.extractNotificationText(null))
        assertEquals("Tap to update", AppUpdateLogic.extractNotificationText(""))
        assertEquals("Tap to update", AppUpdateLogic.extractNotificationText("   \n\n  "))
        assertEquals("Tap to update", AppUpdateLogic.extractNotificationText("### - *   "))
    }

    @Test
    fun extractNotificationText_truncatesTo120Chars() {
        val longLine = "A".repeat(150)
        val result = AppUpdateLogic.extractNotificationText(longLine)
        assertEquals(120, result.length)
        assertEquals("A".repeat(120), result)
    }
}
