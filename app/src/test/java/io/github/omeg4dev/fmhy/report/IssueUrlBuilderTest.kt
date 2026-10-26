package io.github.omeg4dev.fmhy.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueUrlBuilderTest {

    @Test
    fun fixedHeadingsPresentAndInOrder() {
        val draft = ReportDraft(
            area = ReportArea.SEARCH,
            whatWentWrong = "Typing query causes lag",
            expected = "Instant search results",
            includeTechnical = true,
            includePageUrl = true,
            pageUrl = "https://example.com/search",
            isPrivateTab = false,
            technical = TechnicalInfo("1.0.0", "14", "Pixel 8"),
            crash = null,
        )
        val text = ReportText.build(draft)

        val idxHappened = text.indexOf("## What happened")
        val idxExpected = text.indexOf("## What was expected")
        val idxTechnical = text.indexOf("## Technical details")
        val idxPage = text.indexOf("## Page")

        assertTrue("## What happened must be present", idxHappened >= 0)
        assertTrue("## What was expected must be present", idxExpected >= 0)
        assertTrue("## Technical details must be present", idxTechnical >= 0)
        assertTrue("## Page must be present", idxPage >= 0)

        assertTrue("Order must be What happened -> What was expected", idxHappened < idxExpected)
        assertTrue("Order must be What was expected -> Technical details", idxExpected < idxTechnical)
        assertTrue("Order must be Technical details -> Page", idxTechnical < idxPage)

        assertTrue(text.contains("Area: Search"))
        assertTrue(text.contains("Typing query causes lag"))
        assertTrue(text.contains("Instant search results"))
        assertTrue(text.contains("App version: 1.0.0"))
        assertTrue(text.contains("Android version: 14"))
        assertTrue(text.contains("Device model: Pixel 8"))
        assertTrue(text.contains("Web address: https://example.com/search"))
    }

    @Test
    fun notDescribedForBlankText() {
        val draft = ReportDraft(
            area = ReportArea.OTHER,
            whatWentWrong = "   ",
            expected = "",
            includeTechnical = false,
            includePageUrl = false,
            pageUrl = null,
            isPrivateTab = false,
            technical = null,
            crash = null,
        )
        val text = ReportText.build(draft)

        assertTrue(text.contains("## What happened\nArea: Other\n(not described)"))
        assertTrue(text.contains("## What was expected\n(not described)"))
        assertFalse("Technical details should be omitted when includeTechnical=false and crash=null", text.contains("## Technical details"))
        assertFalse("Page should be omitted when includePageUrl=false", text.contains("## Page"))
    }

    @Test
    fun lengthLimitShortensBodyAndSetsTruncated() {
        val longBody = (1..500).joinToString("\n") { "Line $it with a good amount of detailed text to make body very long" }
        val title = "[Search] Extremely long report body test"
        val maxLength = 2000

        val issueLink = IssueUrlBuilder.build(title = title, body = longBody, maxLength = maxLength)

        assertTrue("URL length must be <= maxLength ($maxLength), was ${issueLink.url.length}", issueLink.url.length <= maxLength)
        assertTrue("truncated must be true", issueLink.truncated)
        assertTrue("URL must have valid GitHub issues new prefix", issueLink.url.startsWith("https://github.com/omeg4-dev/fmhy-android/issues/new?"))
        assertTrue("URL must contain encoded title", issueLink.url.contains("title="))
        assertTrue("URL must contain encoded body", issueLink.url.contains("body="))
    }

    @Test
    fun privateTabNeverContainsUrlEvenWhenIncludePageUrlIsTrue() {
        val draft = ReportDraft(
            area = ReportArea.PAGE_DOES_NOT_LOAD,
            whatWentWrong = "Failed to open page",
            expected = "Page should load",
            includeTechnical = false,
            includePageUrl = true,
            pageUrl = "https://secret.example.com/sensitive-data",
            isPrivateTab = true,
            technical = null,
            crash = null,
        )
        val text = ReportText.build(draft)

        assertFalse("Page section must NOT be included for private tab", text.contains("## Page"))
        assertFalse("Private URL must not appear anywhere in report text", text.contains("secret.example.com"))
    }

    @Test
    fun normalTabIncludesUrlOnlyWhenSwitchIsOn() {
        val url = "https://public.example.com/page"
        val draftWithSwitchOn = ReportDraft(
            area = ReportArea.PAGE_DOES_NOT_LOAD,
            whatWentWrong = "Failed",
            expected = "Load",
            includeTechnical = false,
            includePageUrl = true,
            pageUrl = url,
            isPrivateTab = false,
            technical = null,
            crash = null,
        )
        val textOn = ReportText.build(draftWithSwitchOn)
        assertTrue("URL should be included when includePageUrl=true", textOn.contains("## Page"))
        assertTrue("Web address line should be present", textOn.contains("Web address: $url"))

        val draftWithSwitchOff = draftWithSwitchOn.copy(includePageUrl = false)
        val textOff = ReportText.build(draftWithSwitchOff)
        assertFalse("URL should NOT be included when includePageUrl=false", textOff.contains("## Page"))
        assertFalse("Web address should not appear when switch is off", textOff.contains(url))
    }

    @Test
    fun crashTextHasNoPageAddress() {
        val crash = CrashDetails(
            exceptionClass = "java.lang.IllegalStateException",
            frames = listOf("TestClass.run(TestClass.kt:10)"),
        )
        val draft = ReportDraft(
            area = ReportArea.OTHER,
            whatWentWrong = "",
            expected = "",
            includeTechnical = true,
            includePageUrl = true,
            pageUrl = "https://example.com/crashed-page",
            isPrivateTab = false,
            technical = TechnicalInfo("1.0.0", "14", "Pixel 8"),
            crash = crash,
        )
        val text = ReportText.build(draft)

        assertTrue(text.contains("## Technical details"))
        assertTrue(text.contains("Crash: java.lang.IllegalStateException"))
        assertTrue(text.contains("Where:\nTestClass.run(TestClass.kt:10)"))
        assertFalse("Crash report must NEVER contain page address", text.contains("## Page"))
        assertFalse("Crash report must not contain pageUrl", text.contains("https://example.com/crashed-page"))
    }

    @Test
    fun crashDetailsFromNeverContainsMessageAndOnlyAppPackageFrames() {
        val secretMessage = "Confidential Token 12345: https://user:pass@secret.com"
        val rootCause = IllegalArgumentException(secretMessage)
        val intermediate = RuntimeException("Wrapper exception with more secrets", rootCause)

        // Inject stack trace elements
        val appTrace = StackTraceElement("io.github.omeg4dev.fmhy.MainActivity", "onCreate", "MainActivity.kt", 42)
        val appTrace2 = StackTraceElement("io.github.omeg4dev.fmhy.report.CrashReportStore", "save", "CrashReportStore.kt", 15)
        val thirdPartyTrace = StackTraceElement("android.app.Activity", "performCreate", "Activity.java", 100)
        val thirdPartyTrace2 = StackTraceElement("okhttp3.OkHttpClient", "newCall", "OkHttpClient.kt", 50)

        rootCause.stackTrace = arrayOf(thirdPartyTrace, appTrace, thirdPartyTrace2, appTrace2)

        val crashDetails = CrashDetails.from(intermediate, appPackage = "io.github.omeg4dev.fmhy")

        assertEquals("Root cause class must be captured", "java.lang.IllegalArgumentException", crashDetails.exceptionClass)
        assertFalse("Crash details must never contain exception message", crashDetails.toString().contains(secretMessage))
        assertFalse("Crash details must never contain wrapper message", crashDetails.toString().contains("Wrapper exception"))

        // Only app-package frames
        assertEquals(2, crashDetails.frames.size)
        assertEquals("MainActivity.onCreate(MainActivity.kt:42)", crashDetails.frames[0])
        assertEquals("CrashReportStore.save(CrashReportStore.kt:15)", crashDetails.frames[1])
    }
}
