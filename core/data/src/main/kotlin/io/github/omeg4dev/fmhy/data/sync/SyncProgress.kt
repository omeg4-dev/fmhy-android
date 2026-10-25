package io.github.omeg4dev.fmhy.data.sync

/** Steps of a wiki sync, in order. */
sealed interface SyncProgress {
    /** Fetching constants.ts, rentry and the filter files (cannot be measured). */
    data object Checking : SyncProgress
    /** Section files: [done] of [total] finished, [bytes] downloaded so far (body bytes of 200 responses).
     *  [firstDownload] is true when the dataset had no sections before this sync. */
    data class Files(val done: Int, val total: Int, val bytes: Long, val firstDownload: Boolean) : SyncProgress
    /** Parsing changed sections: [done] of [total]. */
    data class Reading(val done: Int, val total: Int) : SyncProgress
    /** Writing the database transaction (cannot be measured). */
    data object Saving : SyncProgress
}
