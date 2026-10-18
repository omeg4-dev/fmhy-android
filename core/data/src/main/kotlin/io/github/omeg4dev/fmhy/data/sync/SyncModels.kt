package io.github.omeg4dev.fmhy.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SyncState(
    val etag: String? = null,
    val lastCheck: Long = 0L,
    val version: Long = 0L,
)

@Serializable
data class Manifest(
    val schema: Int,
    val version: Long,
    val created: Long = 0L,
    val source_commit: String = "",
    val entries: Int = 0,
    val full: ManifestFull,
    val deltas: List<ManifestDelta> = emptyList(),
)

@Serializable
data class ManifestFull(
    val file: String,
    val sha256: String,
    val size: Long = 0L,
    val raw_size: Long = 0L,
)

@Serializable
data class ManifestDelta(
    val from: Long,
    val to: Long,
    val file: String,
    val sha256: String,
    val size: Long = 0L,
)

@Serializable
data class DeltaPayload(
    val schema: Int,
    val from: Long,
    val to: Long,
    val upsert: DeltaUpsert = DeltaUpsert(),
    val delete: DeltaDelete = DeltaDelete(),
    val changes_append: List<JsonObject> = emptyList(),
    val changes_prune_before: Long? = null,
    val meta: Map<String, String> = emptyMap(),
)

@Serializable
data class DeltaUpsert(
    val sections: List<JsonObject> = emptyList(),
    val headings: List<JsonObject> = emptyList(),
    val entries: List<JsonObject> = emptyList(),
    val notes: List<JsonObject> = emptyList(),
    val posts: List<JsonObject> = emptyList(),
    val unsafe_domains: List<JsonObject> = emptyList(),
)

@Serializable
data class DeltaDelete(
    val sections: List<String> = emptyList(),
    val headings: List<String> = emptyList(),
    val entries: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val posts: List<String> = emptyList(),
    val unsafe_domains: List<String> = emptyList(),
)
