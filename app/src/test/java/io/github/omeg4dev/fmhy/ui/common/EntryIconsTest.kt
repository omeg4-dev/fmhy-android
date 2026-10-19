package io.github.omeg4dev.fmhy.ui.common

import io.github.omeg4dev.fmhy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryIconsTest {

    @Test
    fun testFindPlatformSpans_noPlatforms() {
        val result = findPlatformSpans("Movies / TV / Anime / Auto-Next")
        assertTrue(result.isEmpty())
    }

    @Test
    fun testFindPlatformSpans_videoDownloader() {
        val text = "Video Downloader / Windows, Mac, Linux"
        val result = findPlatformSpans(text)
        assertEquals(3, result.size)

        assertEquals(19..25, result[0].first)
        assertEquals(Platform.WINDOWS, result[0].second)
        assertEquals("Windows", result[0].third)

        assertEquals(28..30, result[1].first)
        assertEquals(Platform.MACOS, result[1].second)
        assertEquals("Mac", result[1].third)

        assertEquals(33..37, result[2].first)
        assertEquals(Platform.LINUX, result[2].second)
        assertEquals("Linux", result[2].third)
    }

    @Test
    fun testFindPlatformSpans_onlyPlatformsWithoutSlash() {
        val text = "Windows, Android"
        val result = findPlatformSpans(text)
        assertEquals(2, result.size)

        assertEquals(0..6, result[0].first)
        assertEquals(Platform.WINDOWS, result[0].second)
        assertEquals("Windows", result[0].third)

        assertEquals(9..15, result[1].first)
        assertEquals(Platform.ANDROID, result[1].second)
        assertEquals("Android", result[1].third)
    }

    @Test
    fun testFindPlatformSpans_notFollowedBySeparatorOrPrecededByLowercase() {
        assertTrue(findPlatformSpans("Web Browser").isEmpty())
        assertTrue(findPlatformSpans("Use the Web").isEmpty())
    }

    @Test
    fun testFindPlatformSpans_emulator() {
        val text = "Emulator / iOS / Docker"
        val result = findPlatformSpans(text)
        assertEquals(2, result.size)

        assertEquals(11..13, result[0].first)
        assertEquals(Platform.IOS, result[0].second)
        assertEquals("iOS", result[0].third)

        assertEquals(17..22, result[1].first)
        assertEquals(Platform.DOCKER, result[1].second)
        assertEquals("Docker", result[1].third)
    }

    @Test
    fun testSocialIconFor() {
        assertNotNull(socialIconFor("discord"))
        assertEquals(R.drawable.ic_link_discord, socialIconFor("discord"))
        assertEquals(R.drawable.ic_link_discord, socialIconFor("Discord"))
        assertEquals(R.drawable.ic_link_discord, socialIconFor(" DISCORD "))

        assertEquals(R.drawable.ic_link_github, socialIconFor("GitHub"))
        assertEquals(R.drawable.ic_link_gitlab, socialIconFor("GitLab"))
        assertEquals(R.drawable.ic_link_source, socialIconFor("Source Code"))
        assertEquals(R.drawable.ic_link_telegram, socialIconFor("Telegram"))
        assertEquals(R.drawable.ic_link_reddit, socialIconFor("Subreddit"))
        assertEquals(R.drawable.ic_link_x, socialIconFor("X"))
        assertEquals(R.drawable.ic_link_mastodon, socialIconFor("Mastodon"))
        assertEquals(R.drawable.ic_link_bluesky, socialIconFor("BlueSky"))
        assertEquals(R.drawable.ic_link_onion, socialIconFor(".onion"))

        assertNull(socialIconFor("Status"))
        assertNull(socialIconFor("Guide"))
        assertNull(socialIconFor(""))
    }
}
