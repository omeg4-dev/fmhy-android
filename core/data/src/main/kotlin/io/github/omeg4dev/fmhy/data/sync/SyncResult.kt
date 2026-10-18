package io.github.omeg4dev.fmhy.data.sync

sealed interface SyncResult {
    data object Skipped : SyncResult
    data class UpToDate(val tookMs: Long) : SyncResult
    data class Updated(
        val added: Int,
        val removed: Int,
        val changedSections: Int,
        val failedSections: List<String> = emptyList(),
        val tookMs: Long,
    ) : SyncResult
    data class Failed(
        val reason: String,
        val tookMs: Long,
    ) : SyncResult
}
