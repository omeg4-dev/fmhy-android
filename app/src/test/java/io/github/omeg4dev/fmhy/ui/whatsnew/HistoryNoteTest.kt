package io.github.omeg4dev.fmhy.ui.whatsnew

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryNoteTest {

    private val day = 86_400L
    private val now = 1_793_700_000L // 2026-11-03
    private val utc = ZoneOffset.UTC

    @Test
    fun noNoteWhenHistoryCoversThePeriod() {
        assertNull(historyNote(WhatsNewPeriod.MONTH, now - 100 * day, now, utc))
        assertNull(historyNote(WhatsNewPeriod.WEEK, now - 7 * day, now, utc))
    }

    @Test
    fun noteWhenHistoryIsShorterThanThePeriod() {
        assertEquals("history starts 1 Nov", historyNote(WhatsNewPeriod.WEEK, 1_793_491_200L, now, utc))
        assertEquals("history starts 1 Nov", historyNote(WhatsNewPeriod.MONTH, 1_793_491_200L, now, utc))
    }

    @Test
    fun noNoteForShortOrOpenPeriodsAndMissingHistory() {
        assertNull(historyNote(WhatsNewPeriod.DAY, now - 3600, now, utc)) // a day of slack
        assertNull(historyNote(WhatsNewPeriod.SINCE_LAST_VISIT, now - 3600, now, utc))
        assertNull(historyNote(WhatsNewPeriod.WEEK, null, now, utc))
    }
}
