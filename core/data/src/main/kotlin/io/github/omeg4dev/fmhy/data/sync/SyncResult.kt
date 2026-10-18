package io.github.omeg4dev.fmhy.data.sync

sealed interface SyncResult {
    data object Skipped : SyncResult
    data object NotModified : SyncResult
    data object UpToDate : SyncResult
    data class Updated(val from: Long, val to: Long, val added: Int) : SyncResult
    data class Replaced(val from: Long, val to: Long) : SyncResult
    data class Failed(val reason: String) : SyncResult
}
