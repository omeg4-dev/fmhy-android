package io.github.omeg4dev.fmhy.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {

    @Test
    fun parse_realisticGitHubReleaseJson() {
        val json = """
            {
              "tag_name": "v0.6.0",
              "html_url": "https://github.com/omeg4-dev/fmhy-android/releases/tag/v0.6.0",
              "body": "## What's new\n- Faster search\n- Fixes\n\n## Changes\n- refactor internal logic",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "SHA256SUMS",
                  "size": 128,
                  "browser_download_url": "https://github.com/omeg4-dev/fmhy-android/releases/download/v0.6.0/SHA256SUMS"
                },
                {
                  "name": "fmhy-0.6.0.apk",
                  "size": 15728640,
                  "browser_download_url": "https://github.com/omeg4-dev/fmhy-android/releases/download/v0.6.0/fmhy-0.6.0.apk"
                }
              ]
            }
        """.trimIndent()

        val info = ReleaseParser.parse(json)
        assertNotNull(info)
        assertEquals("0.6.0", info?.version)
        assertEquals("Faster search\nFixes", info?.notes)
        assertEquals("https://github.com/omeg4-dev/fmhy-android/releases/download/v0.6.0/fmhy-0.6.0.apk", info?.apkUrl)
        assertEquals(15728640L, info?.apkSize)
        assertEquals("https://github.com/omeg4-dev/fmhy-android/releases/tag/v0.6.0", info?.pageUrl)
    }

    @Test
    fun parse_returnsNullForDraftOrPrerelease() {
        val draftJson = """
            {
              "tag_name": "v0.6.0",
              "draft": true,
              "prerelease": false,
              "assets": [
                {
                  "name": "app.apk",
                  "size": 100,
                  "browser_download_url": "https://example.com/app.apk"
                }
              ]
            }
        """.trimIndent()
        assertNull(ReleaseParser.parse(draftJson))

        val prereleaseJson = """
            {
              "tag_name": "v0.6.0",
              "draft": false,
              "prerelease": true,
              "assets": [
                {
                  "name": "app.apk",
                  "size": 100,
                  "browser_download_url": "https://example.com/app.apk"
                }
              ]
            }
        """.trimIndent()
        assertNull(ReleaseParser.parse(prereleaseJson))
    }

    @Test
    fun parse_returnsNullWhenNoApkAsset() {
        val json = """
            {
              "tag_name": "v0.6.0",
              "html_url": "https://example.com/release",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "app.zip",
                  "size": 100,
                  "browser_download_url": "https://example.com/app.zip"
                },
                {
                  "name": "notes.txt",
                  "size": 50,
                  "browser_download_url": "https://example.com/notes.txt"
                }
              ]
            }
        """.trimIndent()
        assertNull(ReleaseParser.parse(json))
    }

    @Test
    fun parse_returnsNullForInvalidJsonOrMissingTag() {
        assertNull(ReleaseParser.parse("not json at all"))
        assertNull(ReleaseParser.parse("{ invalid json }"))

        val noTagJson = """
            {
              "html_url": "https://example.com/release",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "app.apk",
                  "size": 100,
                  "browser_download_url": "https://example.com/app.apk"
                }
              ]
            }
        """.trimIndent()
        assertNull(ReleaseParser.parse(noTagJson))

        val blankTagJson = """
            {
              "tag_name": "   ",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "app.apk",
                  "size": 100,
                  "browser_download_url": "https://example.com/app.apk"
                }
              ]
            }
        """.trimIndent()
        assertNull(ReleaseParser.parse(blankTagJson))

        val unparseableTagJson = """
            {
              "tag_name": "unparseable",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "app.apk",
                  "size": 100,
                  "browser_download_url": "https://example.com/app.apk"
                }
              ]
            }
        """.trimIndent()
        assertNull(ReleaseParser.parse(unparseableTagJson))
    }

    @Test
    fun parseVersion_handlesVariousFormats() {
        assertEquals(listOf(1, 2, 3), ReleaseParser.parseVersion("v1.2.3"))
        assertEquals(listOf(1, 2, 3), ReleaseParser.parseVersion("V1.2.3"))
        assertEquals(listOf(1, 2), ReleaseParser.parseVersion("1.2"))
        assertEquals(listOf(1, 2, 3), ReleaseParser.parseVersion("1.2.3-rc1"))
        assertEquals(listOf(1, 2, 3), ReleaseParser.parseVersion("v1.2.3-beta.2"))
        assertEquals(listOf(1, 2, 3), ReleaseParser.parseVersion("1.2.3+build42"))

        assertNull(ReleaseParser.parseVersion(""))
        assertNull(ReleaseParser.parseVersion("   "))
        assertNull(ReleaseParser.parseVersion("abc"))
        assertNull(ReleaseParser.parseVersion("1.x"))
        assertNull(ReleaseParser.parseVersion("v"))
        assertNull(ReleaseParser.parseVersion("1..2"))
        assertNull(ReleaseParser.parseVersion("1."))
        assertNull(ReleaseParser.parseVersion(".1"))
    }

    @Test
    fun compareVersions_numericComparisonWithMissingParts() {
        assertTrue(ReleaseParser.compareVersions("0.10.0", "0.9.9") > 0)
        assertTrue(ReleaseParser.compareVersions("0.9.9", "0.10.0") < 0)
        assertEquals(0, ReleaseParser.compareVersions("1.2", "1.2.0"))
        assertEquals(0, ReleaseParser.compareVersions("1.2.0", "1.2"))
        assertTrue(ReleaseParser.compareVersions("1.2.1", "1.2.0") > 0)
        assertTrue(ReleaseParser.compareVersions("1.2.0", "1.2.1") < 0)

        // Unparseable inputs return 0
        assertEquals(0, ReleaseParser.compareVersions("abc", "1.0.0"))
        assertEquals(0, ReleaseParser.compareVersions("1.0.0", "invalid"))
        assertEquals(0, ReleaseParser.compareVersions("xyz", "bad"))
    }

    @Test
    fun isNewer_handlesSkippedAndVersionOrdering() {
        // v0.6.0 skipped hides 0.6.0
        assertFalse(ReleaseParser.isNewer(available = "0.6.0", installed = "0.5.0", skipped = "v0.6.0"))
        assertFalse(ReleaseParser.isNewer(available = "v0.6.0", installed = "0.5.0", skipped = "0.6.0"))

        // But 0.6.1 is still newer when 0.6.0 is skipped
        assertTrue(ReleaseParser.isNewer(available = "0.6.1", installed = "0.5.0", skipped = "v0.6.0"))

        // Installed equal or newer -> false
        assertFalse(ReleaseParser.isNewer(available = "1.0.0", installed = "1.0.0", skipped = null))
        assertFalse(ReleaseParser.isNewer(available = "v1.0.0", installed = "1.0", skipped = null))
        assertFalse(ReleaseParser.isNewer(available = "0.9.0", installed = "1.0.0", skipped = null))

        // Unparseable available or installed -> false
        assertFalse(ReleaseParser.isNewer(available = "invalid", installed = "1.0.0", skipped = null))
        assertFalse(ReleaseParser.isNewer(available = "1.0.0", installed = "invalid", skipped = null))

        // Null or blank skipped does not suppress
        assertTrue(ReleaseParser.isNewer(available = "1.1.0", installed = "1.0.0", skipped = null))
        assertTrue(ReleaseParser.isNewer(available = "1.1.0", installed = "1.0.0", skipped = ""))
        assertTrue(ReleaseParser.isNewer(available = "1.1.0", installed = "1.0.0", skipped = "   "))
    }

    @Test
    fun extractNotes_extractsMarkdownNotesCorrectly() {
        // CRLF line endings
        val crlfBody = "## What's new\r\n- Feature A\r\n- Feature B\r\n\r\n## Changes\r\n- Fix 1"
        assertEquals("Feature A\nFeature B", ReleaseParser.extractNotes(crlfBody))

        // Curly apostrophe
        val curlyBody = "## What’s new\n- Feature C"
        assertEquals("Feature C", ReleaseParser.extractNotes(curlyBody))

        // Case-insensitive and "Whats new" without apostrophe, ### level
        val levelThreeBody = "### whats new\n* Feature D\n* Feature E"
        assertEquals("Feature D\nFeature E", ReleaseParser.extractNotes(levelThreeBody))

        // Bullets stripped, next heading stops extraction
        val multiHeadingBody = """
            # Intro
            Welcome to the app.

            ## What's new
            - First item
            * Second item
            Plain sentence without bullet

            ### Other notes
            - Should not be included
        """.trimIndent()
        assertEquals("First item\nSecond item\nPlain sentence without bullet", ReleaseParser.extractNotes(multiHeadingBody))

        // No heading -> null
        assertNull(ReleaseParser.extractNotes("## Other heading\n- Some bullet"))

        // Empty section under heading -> null
        assertNull(ReleaseParser.extractNotes("## What's new\n\n## Next heading\n- item"))

        // Null body -> null
        assertNull(ReleaseParser.extractNotes(null))
    }
}
