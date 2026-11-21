package io.github.omeg4dev.fmhy.ui.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object NewBadgeFormat {

    fun tooltip(
        addedOn: LocalDate,
        today: LocalDate = LocalDate.now(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d", locale)
        val dateStr = addedOn.format(formatter)
        val diff = ChronoUnit.DAYS.between(addedOn, today).toInt()
        val rel = when {
            diff <= 0 -> "today"
            diff == 1 -> "yesterday"
            else -> "$diff days ago"
        }
        return "Added $dateStr · $rel"
    }

    fun accessibilityText(
        addedOn: LocalDate,
        locale: Locale = Locale.getDefault(),
    ): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d", locale)
        val dateStr = addedOn.format(formatter)
        return "New, added $dateStr"
    }
}
