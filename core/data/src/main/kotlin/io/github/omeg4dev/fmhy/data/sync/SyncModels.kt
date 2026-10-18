package io.github.omeg4dev.fmhy.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class FileState(
    val etag: String? = null,
    val sha256: String,
)

@Serializable
data class SyncState(
    val lastCheck: Long = 0L,
    val lastSuccess: Long = 0L,
    val source: String = "primary",
    val files: Map<String, FileState> = emptyMap(),
    val version: Long = 0L,
)
