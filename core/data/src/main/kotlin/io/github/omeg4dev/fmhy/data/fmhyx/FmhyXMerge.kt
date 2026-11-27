package io.github.omeg4dev.fmhy.data.fmhyx

import io.github.omeg4dev.fmhy.data.user.BookmarksSnapshot
import io.github.omeg4dev.fmhy.data.user.Folder
import io.github.omeg4dev.fmhy.data.user.UserBookmark
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

private const val MAX_URL_LENGTH = 2048
private const val MAX_TITLE_LENGTH = 300
private const val MAX_DESCRIPTION_LENGTH = 240
private const val MAX_META_FIELD_LENGTH = 200
private const val MAX_COLLECTION_NAME_LENGTH = 60

private data class MergedCollection(
    val id: String,
    val updated: Long,
    val itemUrls: Set<String>,
)

internal fun isSyncableUrl(url: String): Boolean {
    if (url.length > MAX_URL_LENGTH) return false
    return url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
}

internal fun cutUtf16Safe(s: String, maxLength: Int): String {
    if (maxLength <= 0) return ""
    if (s.length <= maxLength) return s
    var end = maxLength
    if (end > 0 && s[end - 1].isHighSurrogate() && s[end].isLowSurrogate()) {
        end--
    }
    return s.substring(0, end)
}

internal fun linkComparisonName(name: String): String = cutUtf16Safe(name.trim(), MAX_COLLECTION_NAME_LENGTH)

internal fun outgoingCollectionName(localName: String): String {
    val trimmed = localName.trim()
    if (trimmed.isEmpty()) return "Folder"
    return cutUtf16Safe(trimmed, MAX_COLLECTION_NAME_LENGTH).ifBlank { "Folder" }
}

internal fun sha1Hex16(input: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
    val sb = StringBuilder(16)
    for (i in 0 until 8) {
        val b = digest[i].toInt() and 0xFF
        if (b < 16) sb.append('0')
        sb.append(Integer.toHexString(b))
    }
    return sb.toString()
}

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.longOrZero(key: String): Long =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: 0L

internal fun JsonObject.boolOrFalse(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: false

internal fun JsonObject.isCollectionShared(): Boolean =
    str("share")?.isNotEmpty() == true

internal fun JsonObject.collectionItems(): List<JsonObject> =
    (this["items"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

internal fun computeExcludedUrls(
    snapshot: BookmarksSnapshot,
    base: BaseState,
    resolvedById: Map<String, ResolvedEntry>,
    nsfwUrls: Set<String>,
): Set<String> {
    val excluded = HashSet<String>()
    for (row in snapshot.bookmarks) {
        if (!isSyncableUrl(row.url) || row.sectionId == "nsfw") {
            excluded.add(row.url)
            continue
        }
        if (row.sectionId.isBlank() &&
            !row.entryId.startsWith("fmhyx:") &&
            row.url !in base.sites &&
            (row.entryId !in resolvedById || row.url in nsfwUrls)
        ) {
            excluded.add(row.url)
        }
    }
    return excluded
}

internal fun linkFolders(
    folders: List<Folder>,
    base: BaseState,
    remote: RemoteAccount,
    newId: () -> String,
): Map<Long, String> {
    val allRemoteIds = remote.collections.mapNotNullTo(HashSet()) { it.str("id") }
    val liveRemote = remote.collections.filter { !it.boolOrFalse("deleted") }

    val linkedIds = HashSet<String>()
    val unlinked = mutableListOf<Folder>()
    for (f in folders) {
        val sid = f.syncId
        val isLinked = sid != null &&
            (sid in base.collections || sid in allRemoteIds) &&
            sid !in linkedIds
        if (isLinked) {
            linkedIds.add(sid)
        } else {
            unlinked.add(f)
        }
    }

    val updates = LinkedHashMap<Long, String>()
    for (f in unlinked) {
        val key = linkComparisonName(f.name)
        val adoptableId = liveRemote.firstNotNullOfOrNull { col ->
            val cid = col.str("id") ?: return@firstNotNullOfOrNull null
            val cName = col.str("name") ?: return@firstNotNullOfOrNull null
            if (cid !in linkedIds && cid !in base.collections && linkComparisonName(cName) == key) cid else null
        }
        val targetSyncId = adoptableId
            ?: f.syncId?.takeIf { it !in linkedIds }
            ?: generateUniqueId(linkedIds, newId)
        linkedIds.add(targetSyncId)
        if (targetSyncId != f.syncId) {
            updates[f.id] = targetSyncId
        }
    }
    return updates
}

private fun generateUniqueId(used: Set<String>, newId: () -> String): String {
    while (true) {
        val candidate = newId()
        if (candidate !in used) return candidate
    }
}

internal fun buildSanitizedItem(
    row: UserBookmark,
    at: Long,
    resolved: ResolvedEntry?,
    existingRemoteItem: JsonObject? = null,
): JsonObject {
    val rawTitle = row.name.takeIf { it.isNotBlank() } ?: row.url
    val t = cutUtf16Safe(rawTitle, MAX_TITLE_LENGTH)
    val p = (resolved?.sectionId?.takeIf { it.isNotBlank() } ?: row.sectionId.takeIf { it.isNotBlank() })
        ?.takeIf { it.length <= MAX_META_FIELD_LENGTH }
    val h = resolved?.headingId?.substringAfter('/')
        ?.takeIf { it.isNotBlank() && it.length <= MAX_META_FIELD_LENGTH }
    val d = resolved?.description?.takeIf { it.isNotBlank() }
        ?.let { cutUtf16Safe(it, MAX_DESCRIPTION_LENGTH) }

    val map = LinkedHashMap<String, JsonElement>()
    if (existingRemoteItem != null) {
        for ((k, v) in existingRemoteItem) {
            map[k] = v
        }
    }
    map["u"] = JsonPrimitive(row.url)
    map["t"] = JsonPrimitive(t)
    map["at"] = JsonPrimitive(at)
    if (p != null) map["p"] = JsonPrimitive(p) else map.remove("p")
    if (h != null) map["h"] = JsonPrimitive(h) else map.remove("h")
    if (d != null) map["d"] = JsonPrimitive(d) else map.remove("d")
    return JsonObject(map)
}

internal fun indexRemoteFavorites(favorites: List<JsonObject>): Map<String, JsonObject> {
    val map = LinkedHashMap<String, JsonObject>()
    for (item in favorites) {
        val u = item.str("u") ?: continue
        val prev = map[u]
        if (prev == null || item.longOrZero("at") > prev.longOrZero("at")) {
            map[u] = item
        }
    }
    return map
}

internal fun indexRemoteDislikes(dislikes: List<JsonObject>): Map<String, JsonObject> {
    val map = LinkedHashMap<String, JsonObject>()
    for (item in dislikes) {
        val u = item.str("u") ?: continue
        val prev = map[u]
        if (prev == null || item.longOrZero("at") > prev.longOrZero("at")) {
            map[u] = item
        }
    }
    return map
}

internal fun partitionRemoteCollections(
    collections: List<JsonObject>,
): Pair<Map<String, JsonObject>, Map<String, JsonObject>> {
    val latestById = LinkedHashMap<String, JsonObject>()
    for (col in collections) {
        val id = col.str("id") ?: continue
        val prev = latestById[id]
        if (prev == null || col.longOrZero("updated") > prev.longOrZero("updated")) {
            latestById[id] = col
        }
    }
    val live = LinkedHashMap<String, JsonObject>()
    val deleted = LinkedHashMap<String, JsonObject>()
    for ((id, col) in latestById) {
        if (col.boolOrFalse("deleted")) {
            deleted[id] = col
        } else {
            live[id] = col
        }
    }
    return live to deleted
}

internal fun merge(
    local: BookmarksSnapshot,
    base: BaseState,
    remote: RemoteAccount,
    excluded: Set<String>,
    now: Long,
    resolvedById: Map<String, ResolvedEntry> = emptyMap(),
    resolvedByUrl: Map<String, List<ResolvedEntry>> = emptyMap(),
): MergeResult {
    val folderBySyncId = LinkedHashMap<String, Folder>()
    val folderSyncIdById = HashMap<Long, String>()
    for (f in local.folders) {
        val sid = f.syncId ?: continue
        folderBySyncId[sid] = f
        folderSyncIdById[f.id] = sid
    }

    val localRowsByUrl = local.bookmarks.groupBy { it.url }
    val claimedEntryIds = HashMap<String, String>()
    for (row in local.bookmarks) {
        claimedEntryIds[row.entryId] = row.url
    }

    val remoteFavByUrl = indexRemoteFavorites(remote.favorites)
    val remoteDislikeByUrl = indexRemoteDislikes(remote.dislikes)
    val (liveRemoteColById, deletedRemoteColById) = partitionRemoteCollections(remote.collections)

    val remoteColsByUrl = HashMap<String, MutableList<JsonObject>>()
    val remoteItemByUrlAndCol = HashMap<Pair<String, String>, JsonObject>()
    for ((cid, col) in liveRemoteColById) {
        for (item in col.collectionItems()) {
            val u = item.str("u") ?: continue
            remoteColsByUrl.getOrPut(u) { mutableListOf() }.add(col)
            remoteItemByUrlAndCol.putIfAbsent(u to cid, item)
        }
    }

    val favoritesToPush = mutableListOf<JsonObject>()
    val favoritesToRemove = mutableListOf<JsonObject>()
    val collectionsToPush = mutableListOf<JsonObject>()

    val deleteBookmarkUrls = LinkedHashSet<String>()
    val updateBookmarks = mutableListOf<UpdateBookmarkOp>()
    val pendingNewBookmarks = LinkedHashMap<String, UserBookmark>()
    val dislikeProtectedUrls = HashSet<String>()
    val userRemovedEntirely = HashSet<String>()

    val localFoldersByUrl = HashMap<String, Set<String>>()
    val localAtByUrl = HashMap<String, Long>()
    val newestLocalRowByUrl = HashMap<String, UserBookmark>()

    for ((u, rows) in localRowsByUrl) {
        localAtByUrl[u] = rows.maxOf { it.savedAt }
        localFoldersByUrl[u] = rows.mapNotNullTo(LinkedHashSet()) { it.folderId?.let(folderSyncIdById::get) }
        rows.maxWithOrNull(compareBy<UserBookmark> { it.savedAt }.thenBy { it.entryId })?.let { newest ->
            newestLocalRowByUrl[u] = newest
        }
    }

    val allSiteUrls = linkedSetOf<String>().apply {
        addAll(localRowsByUrl.keys)
        addAll(base.sites.keys)
        addAll(remoteFavByUrl.keys)
        addAll(remoteColsByUrl.keys)
        removeAll(excluded)
    }

    for (u in allSiteUrls) {
        val newestRow = newestLocalRowByUrl[u]
        val hasLocal = newestRow != null
        val localAt = localAtByUrl[u] ?: 0L
        val localFolders = localFoldersByUrl[u].orEmpty()
        val b = base.sites[u]
        val rFav = remoteFavByUrl[u]
        val rFavAt = rFav?.longOrZero("at")
        val rCols = remoteColsByUrl[u].orEmpty()
        val inAnyRemoteCol = rCols.isNotEmpty()
        val rDislike = remoteDislikeByUrl[u]

        if (rDislike != null) {
            val dislikeAt = rDislike.longOrZero("at")
            if (newestRow != null) {
                val activeDislike = dislikeAt > localAt && (rFavAt == null || dislikeAt >= rFavAt)
                if (activeDislike) {
                    deleteBookmarkUrls.add(u)
                    dislikeProtectedUrls.add(u)
                    continue
                }
            } else if (rFav == null) {
                if (b != null && dislikeAt > (b.favAt ?: 0L)) {
                    dislikeProtectedUrls.add(u)
                }
                continue
            }
        }

        if (newestRow != null && b == null) {
            if (rFav == null || rFavAt == null) {
                // Already in the matching collection on the web (e.g. after sign out and in): stays collection-only.
                if (rCols.none { it.str("id") in localFolders }) {
                    pushFavoriteIfSyncable(u, newestRow, localAt, null, resolvedById, favoritesToPush)
                }
            } else {
                resolveConflict(u, newestRow, localAt, rFav, rFavAt, resolvedById, favoritesToPush, updateBookmarks)
            }
        } else if (!hasLocal && b == null) {
            val srcItem = rFav ?: pickNewestCollectionItem(u, rCols, remoteItemByUrlAndCol)
            if (srcItem != null) {
                pendingNewBookmarks[u] = createBookmarkFromRemoteItem(u, srcItem, now, resolvedByUrl, claimedEntryIds)
            }
        } else if (!hasLocal && b != null) {
            val shouldPullBack = rFav != null && rFavAt != null && (b.favAt == null || rFavAt > b.favAt)
            if (shouldPullBack && rFav != null) {
                pendingNewBookmarks[u] = createBookmarkFromRemoteItem(u, rFav, now, resolvedByUrl, claimedEntryIds)
            } else {
                if (rFav != null) {
                    favoritesToRemove.add(rFav)
                }
                userRemovedEntirely.add(u)
            }
        } else if (newestRow != null && b != null && b.favAt != null) {
            if (rFav != null && rFavAt != null) {
                resolveConflict(u, newestRow, localAt, rFav, rFavAt, resolvedById, favoritesToPush, updateBookmarks)
            } else if (localAt > b.favAt) {
                pushFavoriteIfSyncable(u, newestRow, localAt, null, resolvedById, favoritesToPush)
            } else if (!inAnyRemoteCol) {
                deleteBookmarkUrls.add(u)
            }
        } else if (newestRow != null && b != null && b.favAt == null) {
            if (rFav != null && rFavAt != null) {
                if (localAt > rFavAt) {
                    pushFavoriteIfSyncable(u, newestRow, localAt, rFav, resolvedById, favoritesToPush)
                } else if (rFavAt > localAt) {
                    updateBookmarks.add(remoteUpdateOp(u, newestRow, rFav, rFavAt))
                }
            } else if (localRowsByUrl[u].orEmpty().none { it.folderId != null }) {
                pushFavoriteIfSyncable(u, newestRow, localAt, null, resolvedById, favoritesToPush)
            } else if (!inAnyRemoteCol && localFolders == b.folders) {
                deleteBookmarkUrls.add(u)
            }
        }
    }

    val createFolders = mutableListOf<CreateFolderOp>()
    val renameFolders = mutableListOf<RenameFolderOp>()
    val deleteFolderSyncIds = LinkedHashSet<String>()
    val mergedLiveCollections = LinkedHashMap<String, MergedCollection>()

    val allColIds = linkedSetOf<String>().apply {
        addAll(folderBySyncId.keys)
        addAll(base.collections.keys)
        addAll(liveRemoteColById.keys)
    }

    for (cid in allColIds) {
        val localFolder = folderBySyncId[cid]
        val bCol = base.collections[cid]
        val rCol = liveRemoteColById[cid]

        when {
            localFolder != null && rCol != null -> {
                mergeExistingCollection(
                    cid = cid,
                    localFolder = localFolder,
                    bCol = bCol,
                    rCol = rCol,
                    localRowsByUrl = localRowsByUrl,
                    localFoldersByUrl = localFoldersByUrl,
                    localAtByUrl = localAtByUrl,
                    folderSyncIdById = folderSyncIdById,
                    base = base,
                    excluded = excluded,
                    dislikeProtectedUrls = dislikeProtectedUrls,
                    deleteBookmarkUrls = deleteBookmarkUrls,
                    userRemovedEntirely = userRemovedEntirely,
                    resolvedById = resolvedById,
                    now = now,
                    renameFolders = renameFolders,
                    collectionsToPush = collectionsToPush,
                    mergedLiveCollections = mergedLiveCollections,
                )
            }
            localFolder != null && rCol == null && bCol != null -> {
                deleteFolderSyncIds.add(cid)
            }
            localFolder != null && rCol == null && bCol == null -> {
                val items = buildNewCollectionItems(
                    cid = cid,
                    localRowsByUrl = localRowsByUrl,
                    localFoldersByUrl = localFoldersByUrl,
                    localAtByUrl = localAtByUrl,
                    folderSyncIdById = folderSyncIdById,
                    excluded = excluded,
                    dislikeProtectedUrls = dislikeProtectedUrls,
                    deleteBookmarkUrls = deleteBookmarkUrls,
                    resolvedById = resolvedById,
                )
                val deletedMarkerUpdated = deletedRemoteColById[cid]?.longOrZero("updated")
                val newUpdated = if (deletedMarkerUpdated != null) {
                    maxOf(now, deletedMarkerUpdated + 1L)
                } else {
                    now
                }
                val colName = outgoingCollectionName(localFolder.name)
                val outCol = JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(cid),
                        "name" to JsonPrimitive(colName),
                        "items" to JsonArray(items),
                        "updated" to JsonPrimitive(newUpdated),
                    )
                )
                collectionsToPush.add(outCol)
                mergedLiveCollections[cid] = MergedCollection(
                    id = cid,
                    updated = newUpdated,
                    itemUrls = items.mapNotNullTo(HashSet()) { it.str("u") },
                )
            }
            localFolder == null && rCol != null && bCol != null -> {
                val newUpdated = maxOf(now, rCol.longOrZero("updated") + 1L)
                val tombstone = JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(cid),
                        "deleted" to JsonPrimitive(true),
                        "updated" to JsonPrimitive(newUpdated),
                    )
                )
                collectionsToPush.add(tombstone)
            }
            localFolder == null && rCol != null && bCol == null -> {
                val rName = rCol.str("name").orEmpty().ifBlank { "Folder" }
                createFolders.add(CreateFolderOp(syncId = cid, name = rName))
                mergedLiveCollections[cid] = MergedCollection(
                    id = cid,
                    updated = rCol.longOrZero("updated"),
                    itemUrls = rCol.collectionItems().mapNotNullTo(HashSet()) { it.str("u") },
                )
            }
        }
    }

    val sortedMerged = mergedLiveCollections.values.sortedWith(
        compareByDescending<MergedCollection> { it.updated }.thenBy { it.id }
    )
    val addBookmarks = mutableListOf<AddBookmarkOp>()
    val moveBookmarks = mutableListOf<MoveBookmarkOp>()

    for ((u, newBookmark) in pendingNewBookmarks) {
        val bestColId = sortedMerged.firstOrNull { u in it.itemUrls }?.id
        addBookmarks.add(AddBookmarkOp(bookmark = newBookmark, folderSyncId = bestColId))
    }

    for (u in allSiteUrls) {
        if (u in deleteBookmarkUrls || u in pendingNewBookmarks) continue
        val rows = localRowsByUrl[u].orEmpty()
        val newestRow = newestLocalRowByUrl[u] ?: continue
        val bestColId = sortedMerged.firstOrNull { u in it.itemUrls }?.id
        val anyHasFolder = rows.any { it.folderId != null }
        if (!anyHasFolder) {
            if (bestColId != null) {
                moveBookmarks.add(MoveBookmarkOp(newestRow.entryId, u, bestColId))
            }
        } else {
            for (row in rows) {
                val sid = row.folderId?.let(folderSyncIdById::get) ?: continue
                val mergedCol = mergedLiveCollections[sid]
                if (mergedCol == null || u !in mergedCol.itemUrls) {
                    moveBookmarks.add(MoveBookmarkOp(row.entryId, u, bestColId))
                }
            }
        }
    }

    return MergeResult(
        localOps = LocalOps(
            createFolders = createFolders,
            renameFolders = renameFolders,
            deleteFolderSyncIds = deleteFolderSyncIds,
            addBookmarks = addBookmarks,
            deleteBookmarkUrls = deleteBookmarkUrls,
            updateBookmarks = updateBookmarks,
            moveBookmarks = moveBookmarks,
        ),
        remoteOps = RemoteOps(
            favoritesToPush = favoritesToPush,
            favoritesToRemove = favoritesToRemove,
            collectionsToPush = collectionsToPush,
        ),
    )
}

private fun pushFavoriteIfSyncable(
    url: String,
    row: UserBookmark,
    localAt: Long,
    existingRemoteItem: JsonObject?,
    resolvedById: Map<String, ResolvedEntry>,
    out: MutableList<JsonObject>,
) {
    if (!isSyncableUrl(url)) return
    out.add(buildSanitizedItem(row, localAt, resolvedById[row.entryId], existingRemoteItem))
}

private fun resolveConflict(
    url: String,
    newestRow: UserBookmark,
    localAt: Long,
    rFav: JsonObject,
    rFavAt: Long,
    resolvedById: Map<String, ResolvedEntry>,
    favoritesToPush: MutableList<JsonObject>,
    updateBookmarks: MutableList<UpdateBookmarkOp>,
) {
    if (localAt > rFavAt) {
        pushFavoriteIfSyncable(url, newestRow, localAt, rFav, resolvedById, favoritesToPush)
    } else if (rFavAt > localAt) {
        updateBookmarks.add(remoteUpdateOp(url, newestRow, rFav, rFavAt))
    }
}

private fun remoteUpdateOp(
    url: String,
    newestRow: UserBookmark,
    rFav: JsonObject,
    rFavAt: Long,
): UpdateBookmarkOp {
    val newName = if (newestRow.entryId.startsWith("fmhyx:")) {
        rFav.str("t")?.takeIf { it.isNotBlank() } ?: url
    } else {
        null
    }
    return UpdateBookmarkOp(
        entryId = newestRow.entryId,
        url = url,
        savedAt = rFavAt,
        name = newName,
    )
}

private fun pickNewestCollectionItem(
    url: String,
    rCols: List<JsonObject>,
    remoteItemByUrlAndCol: Map<Pair<String, String>, JsonObject>,
): JsonObject? {
    val col = rCols.maxWithOrNull(
        compareBy<JsonObject> { it.longOrZero("updated") }.thenByDescending { it.str("id").orEmpty() }
    ) ?: return null
    val cid = col.str("id") ?: return null
    return remoteItemByUrlAndCol[url to cid]
}

private fun createBookmarkFromRemoteItem(
    url: String,
    item: JsonObject,
    now: Long,
    resolvedByUrl: Map<String, List<ResolvedEntry>>,
    claimedEntryIds: MutableMap<String, String>,
): UserBookmark {
    val itemP = item.str("p")?.takeIf { it.isNotEmpty() }
    val itemH = item.str("h")?.takeIf { it.isNotEmpty() }
    val itemAt = item.longOrZero("at").let { if (it == 0L) now else it }
    val candidates = resolvedByUrl[url].orEmpty()
    val match = candidates.sortedWith(
        compareByDescending<ResolvedEntry> { itemP != null && it.sectionId == itemP }
            .thenByDescending { itemH != null && it.headingId?.substringAfter('/') == itemH }
            .thenByDescending { it.url == url }
    ).firstOrNull()

    val canUseMatch = match != null && (claimedEntryIds[match.entryId] == null || claimedEntryIds[match.entryId] == url)
    return if (canUseMatch && match != null) {
        claimedEntryIds[match.entryId] = url
        UserBookmark(
            entryId = match.entryId,
            name = match.name,
            url = url,
            sectionId = match.sectionId,
            sectionTitle = match.sectionTitle,
            headingPath = match.headingPath,
            savedAt = itemAt,
            note = "",
            folderId = null,
        )
    } else {
        val fallbackId = "fmhyx:${sha1Hex16(url)}"
        claimedEntryIds[fallbackId] = url
        UserBookmark(
            entryId = fallbackId,
            name = item.str("t")?.takeIf { it.isNotBlank() } ?: url,
            url = url,
            sectionId = itemP ?: "",
            sectionTitle = "",
            headingPath = "",
            savedAt = itemAt,
            note = "",
            folderId = null,
        )
    }
}

private fun mergeExistingCollection(
    cid: String,
    localFolder: Folder,
    bCol: BaseCollection?,
    rCol: JsonObject,
    localRowsByUrl: Map<String, List<UserBookmark>>,
    localFoldersByUrl: Map<String, Set<String>>,
    localAtByUrl: Map<String, Long>,
    folderSyncIdById: Map<Long, String>,
    base: BaseState,
    excluded: Set<String>,
    dislikeProtectedUrls: Set<String>,
    deleteBookmarkUrls: Set<String>,
    userRemovedEntirely: Set<String>,
    resolvedById: Map<String, ResolvedEntry>,
    now: Long,
    renameFolders: MutableList<RenameFolderOp>,
    collectionsToPush: MutableList<JsonObject>,
    mergedLiveCollections: MutableMap<String, MergedCollection>,
) {
    val rName = rCol.str("name").orEmpty()
    val rUpdated = rCol.longOrZero("updated")
    val rItems = rCol.collectionItems()

    val added = mutableListOf<String>()
    for ((u, folders) in localFoldersByUrl) {
        if (u in excluded || u in dislikeProtectedUrls || u in deleteBookmarkUrls) continue
        if (cid !in folders) continue
        val bSite = base.sites[u]
        if (bSite == null || cid !in bSite.folders) {
            added.add(u)
        }
    }

    val removed = HashSet<String>()
    for ((u, bSite) in base.sites) {
        if (u in excluded || u in dislikeProtectedUrls) continue
        val hasSurvivingLocal = localRowsByUrl[u].orEmpty().isNotEmpty() && u !in deleteBookmarkUrls
        if (hasSurvivingLocal && cid in bSite.folders && cid !in localFoldersByUrl[u].orEmpty()) {
            removed.add(u)
        } else if (u in userRemovedEntirely && cid in bSite.remote) {
            removed.add(u)
        }
    }

    val afterRemove = rItems.filter { (it.str("u") ?: "") !in removed }
    val removedAny = afterRemove.size != rItems.size
    val seen = LinkedHashSet<String>()
    val mergedItems = mutableListOf<JsonObject>()
    for (item in afterRemove) {
        val u = item.str("u") ?: continue
        if (seen.add(u)) {
            mergedItems.add(item)
        }
    }
    var addedAny = false
    for (u in added) {
        if (!isSyncableUrl(u) || !seen.add(u)) continue
        val rowsInFolder = localRowsByUrl[u].orEmpty().filter { it.folderId?.let(folderSyncIdById::get) == cid }
        val row = rowsInFolder.maxWithOrNull(compareBy<UserBookmark> { it.savedAt }.thenBy { it.entryId })
            ?: continue
        val at = localAtByUrl[u] ?: row.savedAt
        mergedItems.add(buildSanitizedItem(row, at, resolvedById[row.entryId]))
        addedAny = true
    }
    val itemsChanged = removedAny || addedAny

    val outName = outgoingCollectionName(localFolder.name)
    val targetRemoteName: String
    val nameChanged: Boolean
    if (bCol != null && localFolder.name != bCol.localName) {
        targetRemoteName = outName
        nameChanged = targetRemoteName != rName
    } else if (bCol != null && rName != bCol.remoteName) {
        if (rName.isNotEmpty() && rName != outName) {
            renameFolders.add(RenameFolderOp(localFolder.id, cid, rName))
        }
        targetRemoteName = rName
        nameChanged = false
    } else if (bCol == null) {
        if (rName.isNotEmpty() && rName != outName && rName != localFolder.name) {
            renameFolders.add(RenameFolderOp(localFolder.id, cid, rName))
        }
        targetRemoteName = rName
        nameChanged = false
    } else {
        targetRemoteName = rName
        nameChanged = false
    }

    if (itemsChanged || nameChanged) {
        val newUpdated = maxOf(now, rUpdated + 1L)
        val pushedName = targetRemoteName.ifBlank { outName }
        val map = LinkedHashMap<String, JsonElement>()
        for ((k, v) in rCol) {
            map[k] = v
        }
        map["id"] = JsonPrimitive(cid)
        map["name"] = JsonPrimitive(pushedName)
        map["items"] = JsonArray(mergedItems)
        map["updated"] = JsonPrimitive(newUpdated)
        collectionsToPush.add(JsonObject(map))
        mergedLiveCollections[cid] = MergedCollection(cid, newUpdated, seen)
    } else {
        mergedLiveCollections[cid] = MergedCollection(cid, rUpdated, seen)
    }
}

private fun buildNewCollectionItems(
    cid: String,
    localRowsByUrl: Map<String, List<UserBookmark>>,
    localFoldersByUrl: Map<String, Set<String>>,
    localAtByUrl: Map<String, Long>,
    folderSyncIdById: Map<Long, String>,
    excluded: Set<String>,
    dislikeProtectedUrls: Set<String>,
    deleteBookmarkUrls: Set<String>,
    resolvedById: Map<String, ResolvedEntry>,
): List<JsonObject> {
    val items = mutableListOf<JsonObject>()
    for ((u, folders) in localFoldersByUrl) {
        if (u in excluded || u in dislikeProtectedUrls || u in deleteBookmarkUrls) continue
        if (cid !in folders || !isSyncableUrl(u)) continue
        val rowsInFolder = localRowsByUrl[u].orEmpty().filter { it.folderId?.let(folderSyncIdById::get) == cid }
        val row = rowsInFolder.maxWithOrNull(compareBy<UserBookmark> { it.savedAt }.thenBy { it.entryId })
            ?: continue
        val at = localAtByUrl[u] ?: row.savedAt
        items.add(buildSanitizedItem(row, at, resolvedById[row.entryId]))
    }
    return items
}

internal fun applyLocalOpsInMemory(
    snapshot: BookmarksSnapshot,
    ops: LocalOps,
    clock: () -> Long,
): BookmarksSnapshot {
    val foldersBySyncId = LinkedHashMap<String, Folder>()
    val remainingFolders = mutableListOf<Folder>()
    var nextTempId = (snapshot.folders.maxOfOrNull { it.id } ?: 0L) + 1L
    var nextOrd = (snapshot.folders.maxOfOrNull { it.ord } ?: -1) + 1
    val deletedFolderIds = HashSet<Long>()

    for (f in snapshot.folders) {
        val sid = f.syncId
        if (sid != null && sid in ops.deleteFolderSyncIds) {
            deletedFolderIds.add(f.id)
            continue
        }
        val renamed = ops.renameFolders.firstOrNull { it.folderId == f.id }?.let { f.copy(name = it.newName) } ?: f
        remainingFolders.add(renamed)
        if (sid != null) {
            foldersBySyncId[sid] = renamed
        }
    }

    for (c in ops.createFolders) {
        val created = Folder(
            id = nextTempId++,
            name = c.name,
            ord = nextOrd++,
            createdAt = clock(),
            syncId = c.syncId,
        )
        remainingFolders.add(created)
        foldersBySyncId[c.syncId] = created
    }

    val updatesByEntryId = ops.updateBookmarks.associateBy { it.entryId }
    val movesByEntryId = ops.moveBookmarks.associateBy { it.entryId }

    val updatedBookmarks = mutableListOf<UserBookmark>()
    for (b in snapshot.bookmarks) {
        if (b.url in ops.deleteBookmarkUrls) continue
        var cur = b
        if (cur.folderId != null && cur.folderId in deletedFolderIds) {
            cur = cur.copy(folderId = null)
        }
        updatesByEntryId[cur.entryId]?.let { upd ->
            cur = cur.copy(savedAt = upd.savedAt, name = upd.name ?: cur.name)
        }
        movesByEntryId[cur.entryId]?.let { mv ->
            cur = cur.copy(folderId = mv.folderSyncId?.let { foldersBySyncId[it]?.id })
        }
        updatedBookmarks.add(cur)
    }

    for (add in ops.addBookmarks) {
        val folderId = add.folderSyncId?.let { foldersBySyncId[it]?.id }
        updatedBookmarks.add(add.bookmark.copy(folderId = folderId))
    }

    return BookmarksSnapshot(folders = remainingFolders, bookmarks = updatedBookmarks)
}

internal fun buildBaseState(
    settled: BookmarksSnapshot,
    confirmedRemote: RemoteAccount,
    excluded: Set<String>,
): BaseState {
    val folderSyncIdById = HashMap<Long, String>()
    val settledFoldersBySyncId = LinkedHashMap<String, Folder>()
    for (f in settled.folders) {
        val sid = f.syncId ?: continue
        folderSyncIdById[f.id] = sid
        settledFoldersBySyncId[sid] = f
    }

    val rFavByUrl = indexRemoteFavorites(confirmedRemote.favorites)
    val (rLiveColsById, _) = partitionRemoteCollections(confirmedRemote.collections)
    val rColIdsByUrl = HashMap<String, MutableSet<String>>()
    for ((cid, col) in rLiveColsById) {
        for (item in col.collectionItems()) {
            val u = item.str("u") ?: continue
            rColIdsByUrl.getOrPut(u) { LinkedHashSet() }.add(cid)
        }
    }

    val settledFoldersByUrl = HashMap<String, MutableSet<String>>()
    for (row in settled.bookmarks) {
        if (row.url in excluded) continue
        val set = settledFoldersByUrl.getOrPut(row.url) { LinkedHashSet() }
        row.folderId?.let(folderSyncIdById::get)?.let(set::add)
    }

    val baseSites = LinkedHashMap<String, BaseSite>()
    for ((u, sFolders) in settledFoldersByUrl) {
        val rFav = rFavByUrl[u]
        val rMems = rColIdsByUrl[u].orEmpty()
        if (rFav == null && rMems.isEmpty()) continue
        baseSites[u] = BaseSite(
            favAt = rFav?.longOrZero("at"),
            folders = sFolders.filterTo(LinkedHashSet()) { it in rMems },
            remote = rMems.toCollection(LinkedHashSet()),
        )
    }

    val baseCollections = LinkedHashMap<String, BaseCollection>()
    for ((cid, localFolder) in settledFoldersBySyncId) {
        val rCol = rLiveColsById[cid] ?: continue
        baseCollections[cid] = BaseCollection(
            localName = localFolder.name,
            remoteName = rCol.str("name").orEmpty(),
            shared = rCol.isCollectionShared(),
        )
    }

    return BaseState(sites = baseSites, collections = baseCollections)
}
