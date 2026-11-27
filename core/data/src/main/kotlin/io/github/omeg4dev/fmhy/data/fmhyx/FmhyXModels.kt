package io.github.omeg4dev.fmhy.data.fmhyx

import io.github.omeg4dev.fmhy.data.user.UserBookmark
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Snapshot of the user's FMHY X account returned by sync endpoints. */
@Serializable
data class RemoteAccount(
    val favorites: List<JsonObject>,
    val dislikes: List<JsonObject>,
    val collections: List<JsonObject>,
)

/** Server-enforced quotas checked prior to pushing. */
enum class SyncLimit {
    SAVED_ITEMS,
    FOLDERS,
    FOLDER_ITEMS,
    BODY_SIZE,
}

/** Errors thrown by [FmhyXClient] and [FmhyXSyncEngine]. Never includes tokens or request headers. */
sealed class FmhyXException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Offline(cause: Throwable? = null) : FmhyXException("Offline", cause)
    class Unauthorized : FmhyXException("Unauthorized")
    class RateLimited(val retryAfterSeconds: Long?) : FmhyXException(
        if (retryAfterSeconds != null) "Rate limited (retry after ${retryAfterSeconds}s)" else "Rate limited"
    )
    class Server(val code: Int) : FmhyXException("Server error HTTP $code")
    class BadResponse(cause: Throwable? = null) : FmhyXException("Bad response", cause)
    class LimitExceeded(val limit: SyncLimit) : FmhyXException("Limit exceeded: $limit")
}

/** Minimal wiki entry metadata needed to map between local bookmarks and FMHY X items. */
data class ResolvedEntry(
    val entryId: String,
    val url: String,
    val originalUrl: String?,
    val name: String,
    val sectionId: String,
    val sectionTitle: String,
    val headingId: String?,
    val headingPath: String,
    val description: String,
)

/** Resolves wiki entries and cached NSFW URLs for [FmhyXSyncEngine]. */
interface SyncEntryResolver {
    suspend fun byIds(ids: Collection<String>): Map<String, ResolvedEntry>
    suspend fun byUrls(urls: Collection<String>): Map<String, List<ResolvedEntry>>
    suspend fun nsfwUrls(): Set<String>
}

/** Outcome of one [FmhyXSyncEngine.sync] execution. */
data class SyncResult(
    val syncedAt: Long,
    val changesPending: Boolean,
)

@Serializable
internal data class BaseState(
    val sites: Map<String, BaseSite> = emptyMap(),
    val collections: Map<String, BaseCollection> = emptyMap(),
)

@Serializable
internal data class BaseSite(
    val favAt: Long? = null,
    val folders: Set<String> = emptySet(),
    val remote: Set<String> = emptySet(),
)

@Serializable
internal data class BaseCollection(
    val localName: String,
    val remoteName: String,
    val shared: Boolean = false,
)

internal data class CreateFolderOp(
    val syncId: String,
    val name: String,
)

internal data class RenameFolderOp(
    val folderId: Long,
    val syncId: String,
    val newName: String,
)

internal data class AddBookmarkOp(
    val bookmark: UserBookmark,
    val folderSyncId: String?,
)

internal data class UpdateBookmarkOp(
    val entryId: String,
    val url: String,
    val savedAt: Long,
    val name: String? = null,
)

internal data class MoveBookmarkOp(
    val entryId: String,
    val url: String,
    val folderSyncId: String?,
)

internal data class LocalOps(
    val createFolders: List<CreateFolderOp> = emptyList(),
    val renameFolders: List<RenameFolderOp> = emptyList(),
    val deleteFolderSyncIds: Set<String> = emptySet(),
    val addBookmarks: List<AddBookmarkOp> = emptyList(),
    val deleteBookmarkUrls: Set<String> = emptySet(),
    val updateBookmarks: List<UpdateBookmarkOp> = emptyList(),
    val moveBookmarks: List<MoveBookmarkOp> = emptyList(),
)

internal data class RemoteOps(
    val favoritesToPush: List<JsonObject> = emptyList(),
    val favoritesToRemove: List<JsonObject> = emptyList(),
    val collectionsToPush: List<JsonObject> = emptyList(),
)

internal data class MergeResult(
    val localOps: LocalOps,
    val remoteOps: RemoteOps,
)
