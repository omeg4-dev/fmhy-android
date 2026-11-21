package io.github.omeg4dev.fmhy.ui.common

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class NewBadgeFormatTest {

    private val locale = Locale.US
    private val today = LocalDate.of(2026, 11, 19)

    @Test
    fun testTooltipToday() {
        val addedOn = LocalDate.of(2026, 11, 19)
        assertEquals("Added Nov 19 · today", NewBadgeFormat.tooltip(addedOn, today, locale))
    }

    @Test
    fun testTooltipYesterday() {
        val addedOn = LocalDate.of(2026, 11, 18)
        assertEquals("Added Nov 18 · yesterday", NewBadgeFormat.tooltip(addedOn, today, locale))
    }

    @Test
    fun testTooltipDaysAgo() {
        val addedOn = LocalDate.of(2026, 11, 17)
        assertEquals("Added Nov 17 · 2 days ago", NewBadgeFormat.tooltip(addedOn, today, locale))

        val addedOn7 = LocalDate.of(2026, 11, 12)
        assertEquals("Added Nov 12 · 7 days ago", NewBadgeFormat.tooltip(addedOn7, today, locale))
    }

    @Test
    fun testTooltipFutureDateRendersAsToday() {
        val futureDate = LocalDate.of(2026, 11, 20)
        assertEquals("Added Nov 20 · today", NewBadgeFormat.tooltip(futureDate, today, locale))
    }

    @Test
    fun testAccessibilityText() {
        val addedOn = LocalDate.of(2026, 11, 17)
        assertEquals("New, added Nov 17", NewBadgeFormat.accessibilityText(addedOn, locale))
    }
}
