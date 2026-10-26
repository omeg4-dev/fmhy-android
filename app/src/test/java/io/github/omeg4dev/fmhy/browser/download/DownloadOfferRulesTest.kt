package io.github.omeg4dev.fmhy.browser.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadOfferRulesTest {

    @Test
    fun `emphasizeLinkCheck for all kinds and risky flag combinations`() {
        // STARRED: always false
        assertFalse(DownloadOfferRules.emphasizeLinkCheck(SourceKind.STARRED, isRisky = false))
        assertFalse(DownloadOfferRules.emphasizeLinkCheck(SourceKind.STARRED, isRisky = true))

        // LISTED: true only if isRisky
        assertFalse(DownloadOfferRules.emphasizeLinkCheck(SourceKind.LISTED, isRisky = false))
        assertTrue(DownloadOfferRules.emphasizeLinkCheck(SourceKind.LISTED, isRisky = true))

        // NOT_LISTED: always true
        assertTrue(DownloadOfferRules.emphasizeLinkCheck(SourceKind.NOT_LISTED, isRisky = false))
        assertTrue(DownloadOfferRules.emphasizeLinkCheck(SourceKind.NOT_LISTED, isRisky = true))

        // UNSAFE: always true
        assertTrue(DownloadOfferRules.emphasizeLinkCheck(SourceKind.UNSAFE, isRisky = false))
        assertTrue(DownloadOfferRules.emphasizeLinkCheck(SourceKind.UNSAFE, isRisky = true))

        // LOOKALIKE: always true
        assertTrue(DownloadOfferRules.emphasizeLinkCheck(SourceKind.LOOKALIKE, isRisky = false))
        assertTrue(DownloadOfferRules.emphasizeLinkCheck(SourceKind.LOOKALIKE, isRisky = true))

        // null: matches isRisky
        assertFalse(DownloadOfferRules.emphasizeLinkCheck(null, isRisky = false))
        assertTrue(DownloadOfferRules.emphasizeLinkCheck(null, isRisky = true))
    }

    @Test
    fun `firmRiskConfirm for all kinds and risky flag combinations`() {
        // When isRisky = false, always false regardless of kind
        assertFalse(DownloadOfferRules.firmRiskConfirm(SourceKind.STARRED, isRisky = false))
        assertFalse(DownloadOfferRules.firmRiskConfirm(SourceKind.LISTED, isRisky = false))
        assertFalse(DownloadOfferRules.firmRiskConfirm(SourceKind.NOT_LISTED, isRisky = false))
        assertFalse(DownloadOfferRules.firmRiskConfirm(SourceKind.UNSAFE, isRisky = false))
        assertFalse(DownloadOfferRules.firmRiskConfirm(SourceKind.LOOKALIKE, isRisky = false))
        assertFalse(DownloadOfferRules.firmRiskConfirm(null, isRisky = false))

        // When isRisky = true, true only for NOT_LISTED, LOOKALIKE, UNSAFE
        assertFalse(DownloadOfferRules.firmRiskConfirm(SourceKind.STARRED, isRisky = true))
        assertFalse(DownloadOfferRules.firmRiskConfirm(SourceKind.LISTED, isRisky = true))
        assertTrue(DownloadOfferRules.firmRiskConfirm(SourceKind.NOT_LISTED, isRisky = true))
        assertTrue(DownloadOfferRules.firmRiskConfirm(SourceKind.UNSAFE, isRisky = true))
        assertTrue(DownloadOfferRules.firmRiskConfirm(SourceKind.LOOKALIKE, isRisky = true))
        assertFalse(DownloadOfferRules.firmRiskConfirm(null, isRisky = true))
    }
}
