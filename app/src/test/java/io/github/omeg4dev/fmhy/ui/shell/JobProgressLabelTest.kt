package io.github.omeg4dev.fmhy.ui.shell

import io.github.omeg4dev.fmhy.data.adblock.FilterListProgress
import io.github.omeg4dev.fmhy.data.sync.SyncProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class JobProgressLabelTest {

    @Test
    fun noStepNameIsBareSaving() {
        val adblockNames = stepNames(JobKind.ADBLOCK)
        for (name in adblockNames) {
            assertNotEquals("Saving", name)
        }
        assertEquals("Saving the ad-block rules", adblockNames.last())

        val wikiNames = stepNames(JobKind.WIKI)
        for (name in wikiNames) {
            assertNotEquals("Saving", name)
        }
        assertEquals("Saving the wiki update", wikiNames.last())
    }

    @Test
    fun noAdblockStepIsBareSavingAndSavingMatchesExpectedLabel() {
        val variants = listOf(
            FilterListProgress.Downloading(done = 0, total = 5),
            FilterListProgress.Preparing,
            FilterListProgress.Saving,
        )
        for (p in variants) {
            val step = adblockStep(p)
            assertNotEquals("Saving", step.label)
        }
        assertEquals("Saving the ad-block rules", adblockStep(FilterListProgress.Saving).label)
    }

    @Test
    fun noWikiStepIsBareSavingAndSavingMatchesExpectedLabel() {
        val variants = listOf(
            SyncProgress.Checking,
            SyncProgress.Files(done = 1, total = 10, bytes = 1024L, firstDownload = false),
            SyncProgress.Reading(done = 1, total = 10),
            SyncProgress.Saving,
        )
        for (p in variants) {
            val step = wikiStep(p)
            assertNotEquals("Saving", step.label)
        }
        assertEquals("Saving the wiki update", wikiStep(SyncProgress.Saving).label)
    }
}
