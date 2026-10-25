package io.github.omeg4dev.fmhy.browser.adblock

import io.github.omeg4dev.fmhy.data.adblock.ResourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EssentialRequestsTest {

    @Test
    fun testFirstPartyScriptGenericRuleExempted() {
        assertTrue(
            EssentialRequests.exempt(
                requestUrl = "https://chat.example.com/assets/analytics-bundle.js",
                pageUrl = "https://chat.example.com/",
                type = ResourceType.SCRIPT,
                rule = "/analytics-",
            )
        )
    }

    @Test
    fun testSameSiteSubdomainXhrExempted() {
        assertTrue(
            EssentialRequests.exempt(
                requestUrl = "https://api.example.com/v1/chat",
                pageUrl = "https://www.example.com/c/1",
                type = ResourceType.XHR,
                rule = "/v1/chat",
            )
        )
    }

    @Test
    fun testFirstPartySiteSpecificAnchorRuleNotExempted() {
        assertFalse(
            EssentialRequests.exempt(
                requestUrl = "https://ads.example.com/x.js",
                pageUrl = "https://example.com/",
                type = ResourceType.SCRIPT,
                rule = "||ads.example.com^",
            )
        )
    }

    @Test
    fun testFirstPartyDomainOptionRuleNotExempted() {
        assertFalse(
            EssentialRequests.exempt(
                requestUrl = "https://example.com/a.js",
                pageUrl = "https://example.com/",
                type = ResourceType.SCRIPT,
                rule = "/a.js\$script,domain=example.com",
            )
        )
    }

    @Test
    fun testFirstPartyImageGenericRuleNotExempted() {
        assertFalse(
            EssentialRequests.exempt(
                requestUrl = "https://example.com/logo.png",
                pageUrl = "https://example.com/",
                type = ResourceType.IMAGE,
                rule = "/logo",
            )
        )
    }

    @Test
    fun testThirdPartyAdScriptNotExempted() {
        assertFalse(
            EssentialRequests.exempt(
                requestUrl = "https://ads.tracker.net/t.js",
                pageUrl = "https://example.com/",
                type = ResourceType.SCRIPT,
                rule = "||tracker.net^",
            )
        )
    }

    @Test
    fun testTurnstileExemptedOnAnyPage() {
        assertTrue(
            EssentialRequests.exempt(
                requestUrl = "https://challenges.cloudflare.com/turnstile/v0/api.js",
                pageUrl = "https://example.com/",
                type = ResourceType.SCRIPT,
                rule = "||cloudflare.com^",
            )
        )
    }

    @Test
    fun testRecaptchaVerification() {
        assertTrue(EssentialRequests.isVerification("https://www.google.com/recaptcha/api.js"))
        assertTrue(
            EssentialRequests.exempt(
                requestUrl = "https://www.google.com/recaptcha/api.js",
                pageUrl = "https://example.com/",
                type = ResourceType.SCRIPT,
                rule = "/recaptcha/",
            )
        )
        assertFalse(EssentialRequests.isVerification("https://www.google.com/ads/ga-audiences?x"))
        assertFalse(
            EssentialRequests.exempt(
                requestUrl = "https://www.google.com/ads/ga-audiences?x",
                pageUrl = "https://example.com/",
                type = ResourceType.SCRIPT,
                rule = "/ga-audiences?",
            )
        )
    }

    @Test
    fun testAliCdnVerification() {
        assertFalse(EssentialRequests.isVerification("https://g.alicdn.com/AWSC/et/1.83.41/et_f.js"))
        assertTrue(EssentialRequests.isVerification("https://g.alicdn.com/captcha-frontend/x.js"))
    }

    @Test
    fun testSiteSpecificRuleDetection() {
        assertFalse(
            EssentialRequests.isSiteSpecificRule(
                rule = "||doubleclick.net^\$object,redirect=noopjs",
                pageDomain = "example.com",
            )
        )
        assertTrue(
            EssentialRequests.isSiteSpecificRule(
                rule = "||sub.example.co.uk^",
                pageDomain = "example.co.uk",
            )
        )
    }

    @Test
    fun testOtherVerificationProviders() {
        assertTrue(EssentialRequests.isVerification("https://hcaptcha.com/1/api.js"))
        assertTrue(EssentialRequests.isVerification("https://recaptcha.net/recaptcha/api.js"))
        assertTrue(EssentialRequests.isVerification("https://client-api.arkoselabs.com/fc/api/"))
        assertTrue(EssentialRequests.isVerification("https://funcaptcha.com/fc/api/"))
        assertTrue(EssentialRequests.isVerification("https://static.geetest.com/static/js/gt.js"))
        assertTrue(EssentialRequests.isVerification("https://api.geevisit.com/ajax.php"))
        assertTrue(EssentialRequests.isVerification("https://www.gstatic.com/recaptcha/releases/v1/recaptcha__en.js"))
        assertTrue(EssentialRequests.isVerification("https://shanghai.captcha-open.aliyuncs.com/v2/init"))
        assertTrue(EssentialRequests.isVerification("https://hangzhou.captcha.aliyuncs.com/init"))
        assertFalse(EssentialRequests.isVerification("https://example.com/other"))
        assertFalse(EssentialRequests.isVerification("not-a-valid-url"))
    }
}
