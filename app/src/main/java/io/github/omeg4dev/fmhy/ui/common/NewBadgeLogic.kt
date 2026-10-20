package io.github.omeg4dev.fmhy.ui.common

object NewBadgeLogic {
    /**
     * Determines whether an entry is "NEW" based on the timestamp it was added,
     * the current timestamp, and the user's [newBadgeDays] preference (0 = off).
     * All timestamps are in epoch seconds.
     */
    fun isNew(addedAtSec: Long?, nowSec: Long, newBadgeDays: Int): Boolean {
        if (newBadgeDays <= 0 || addedAtSec == null || addedAtSec <= 0L) return false
        val cutoffSec = nowSec - (newBadgeDays * 86400L)
        return addedAtSec >= cutoffSec
    }
}
