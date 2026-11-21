package io.github.omeg4dev.fmhy.data.newbadge

import io.github.omeg4dev.fmhy.data.model.Entry
import java.time.LocalDate

class NewBadgeIndex(
    internal val marks: Map<String, LocalDate> = emptyMap(),
) {
    companion object {
        val EMPTY = NewBadgeIndex(emptyMap())
        const val WINDOW_DAYS = 7
    }

    /**
     * Date the entry was added per fmhy.net if active at [today] (offset in -1..WINDOW_DAYS days inclusive), else null.
     * Matches page-scoped key "<sectionId>|<urlKey>" on entry.url and entry.originalUrl (primary entry only).
     */
    fun activeDate(entry: Entry, today: LocalDate): LocalDate? {
        fun check(url: String?): LocalDate? {
            if (url.isNullOrEmpty()) return null
            val date = marks[NewBadgeKeys.pageKey(entry.sectionId, url)] ?: return null
            val diff = (today.toEpochDay() - date.toEpochDay()).toInt()
            return if (diff in -1..WINDOW_DAYS) date else null
        }
        return check(entry.url) ?: check(entry.originalUrl)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NewBadgeIndex) return false
        return marks == other.marks
    }

    override fun hashCode(): Int = marks.hashCode()

    override fun toString(): String = "NewBadgeIndex(marksCount=${marks.size})"
}
