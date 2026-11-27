package io.github.omeg4dev.fmhy.data.fmhyx

import io.github.omeg4dev.fmhy.data.user.BookmarksSnapshot
import io.github.omeg4dev.fmhy.data.user.Folder
import io.github.omeg4dev.fmhy.data.user.SyncApplyResult
import io.github.omeg4dev.fmhy.data.user.SyncBookmarkAdd
import io.github.omeg4dev.fmhy.data.user.SyncBookmarkMove
import io.github.omeg4dev.fmhy.data.user.SyncBookmarkUpdate
import io.github.omeg4dev.fmhy.data.user.SyncFolderCreate
import io.github.omeg4dev.fmhy.data.user.SyncFolderRename
import io.github.omeg4dev.fmhy.data.user.UserBookmark
import io.github.omeg4dev.fmhy.data.user.UserStore
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

/**
 * Coordinates two-way sync between [UserStore] and an FMHY X account via [FmhyXClient].
 */
class FmhyXSyncEngine(
    private val store: UserStore,
    private val client: FmhyXClient,
    private val resolver: SyncEntryResolver,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val syncMutex = Mutex()
    private val resetGeneration = AtomicLong(0L)

    suspend fun reset() {
        resetGeneration.incrementAndGet()
        store.clearFmhyXBase()
    }

    suspend fun sharedFolderIds(): Set<Long> {
        val base = decodeBaseState(store.readFmhyXBase())
        val sharedSyncIds = base.collections
            .filterValues { it.shared }
            .keys
        if (sharedSyncIds.isEmpty()) return emptySet()
        return store.bookmarksSnapshot().folders
            .filter { it.syncId in sharedSyncIds }
            .mapTo(HashSet()) { it.id }
    }

    suspend fun sync(token: String): SyncResult = syncMutex.withLock {
        val startGen = resetGeneration.get()
        val oldBase = decodeBaseState(store.readFmhyXBase())
        val initialSnapshot = store.bookmarksSnapshot()

        val remoteInitial = client.pull(token)
        checkNotReset(startGen)

        val linkUpdates = linkFolders(initialSnapshot.folders, oldBase, remoteInitial, newId)
        val snapshot = if (linkUpdates.isNotEmpty()) {
            checkNotReset(startGen)
            store.setFolderSyncIds(linkUpdates)
            store.bookmarksSnapshot()
        } else {
            initialSnapshot
        }

        val resolvedById = resolveLocalEntriesById(snapshot)
        val nsfwUrls = resolver.nsfwUrls()
        val excluded = computeExcludedUrls(snapshot, oldBase, resolvedById, nsfwUrls)

        val queriedUrls = HashSet<String>()
        val resolvedByUrl = HashMap<String, List<ResolvedEntry>>()
        fetchMissingRemoteUrls(snapshot, remoteInitial, excluded, queriedUrls, resolvedByUrl)

        val now = clock()
        val initialMerge = merge(
            local = snapshot,
            base = oldBase,
            remote = remoteInitial,
            excluded = excluded,
            now = now,
            resolvedById = resolvedById,
            resolvedByUrl = resolvedByUrl,
        )

        checkLimits(remoteInitial, initialMerge.remoteOps)
        checkNotReset(startGen)

        val rPrime = pushAndRemove(token, remoteInitial, initialMerge.remoteOps)
        checkNotReset(startGen)

        fetchMissingRemoteUrls(snapshot, rPrime, excluded, queriedUrls, resolvedByUrl)
        val finalMerge = merge(
            local = snapshot,
            base = oldBase,
            remote = rPrime,
            excluded = excluded,
            now = now,
            resolvedById = resolvedById,
            resolvedByUrl = resolvedByUrl,
        )

        val changesPending = store.applyBookmarkSync { current ->
            checkNotReset(startGen)
            planTransactionApply(
                snapshot = snapshot,
                current = current,
                ops = finalMerge.localOps,
                rPrime = rPrime,
                excluded = excluded,
                clock = clock,
            )
        }

        SyncResult(syncedAt = now, changesPending = changesPending)
    }

    private fun checkNotReset(startGen: Long) {
        if (resetGeneration.get() != startGen) {
            throw CancellationException("FMHY X sync reset during run")
        }
    }

    private suspend fun resolveLocalEntriesById(snapshot: BookmarksSnapshot): Map<String, ResolvedEntry> {
        val ids = snapshot.bookmarks
            .asSequence()
            .filter { it.sectionId != "nsfw" && !it.entryId.startsWith("fmhyx:") }
            .map { it.entryId }
            .distinct()
            .toList()
        return if (ids.isEmpty()) emptyMap() else resolver.byIds(ids)
    }

    private suspend fun fetchMissingRemoteUrls(
        snapshot: BookmarksSnapshot,
        remote: RemoteAccount,
        excluded: Set<String>,
        queriedUrls: MutableSet<String>,
        resolvedByUrl: MutableMap<String, List<ResolvedEntry>>,
    ) {
        val localUrls = snapshot.bookmarks.mapTo(HashSet()) { it.url }
        val needed = LinkedHashSet<String>()
        for (fav in remote.favorites) {
            val u = fav.str("u") ?: continue
            if (u !in localUrls && u !in excluded && u !in queriedUrls) {
                needed.add(u)
            }
        }
        for (col in remote.collections) {
            if (col.boolOrFalse("deleted")) continue
            for (item in col.collectionItems()) {
                val u = item.str("u") ?: continue
                if (u !in localUrls && u !in excluded && u !in queriedUrls) {
                    needed.add(u)
                }
            }
        }
        if (needed.isEmpty()) return
        queriedUrls.addAll(needed)
        resolvedByUrl.putAll(resolver.byUrls(needed))
    }

    private fun checkLimits(remote: RemoteAccount, remoteOps: RemoteOps) {
        val currentFavs = indexRemoteFavorites(remote.favorites)
        val currentDislikes = indexRemoteDislikes(remote.dislikes)
        val removedUrls = remoteOps.favoritesToRemove.mapNotNullTo(HashSet()) { it.str("u") }
        val pushedFavs = indexRemoteFavorites(remoteOps.favoritesToPush)

        val projectedFavUrls = LinkedHashSet<String>(currentFavs.keys).apply {
            removeAll(removedUrls)
            addAll(pushedFavs.keys)
        }
        val projectedDislikeUrls = currentDislikes.entries
            .filter { (u, dislike) ->
                val pushed = pushedFavs[u]
                pushed == null || pushed.longOrZero("at") <= dislike.longOrZero("at")
            }
            .map { it.key }
        if (projectedFavUrls.size + projectedDislikeUrls.size > MAX_SAVED_ITEMS) {
            throw FmhyXException.LimitExceeded(SyncLimit.SAVED_ITEMS)
        }

        val (liveCols, _) = partitionRemoteCollections(remote.collections)
        val projectedLiveIds = LinkedHashSet<String>(liveCols.keys)
        for (col in remoteOps.collectionsToPush) {
            val cid = col.str("id") ?: continue
            if (col.boolOrFalse("deleted")) {
                projectedLiveIds.remove(cid)
            } else {
                projectedLiveIds.add(cid)
                if (col.collectionItems().size > MAX_FOLDER_ITEMS) {
                    throw FmhyXException.LimitExceeded(SyncLimit.FOLDER_ITEMS)
                }
            }
        }
        if (projectedLiveIds.size > MAX_FOLDERS) {
            throw FmhyXException.LimitExceeded(SyncLimit.FOLDERS)
        }

        if (remoteOps.favoritesToPush.isNotEmpty() || remoteOps.collectionsToPush.isNotEmpty()) {
            val pushJson = FmhyXClient.buildPushBodyJson(remoteOps.favoritesToPush, remoteOps.collectionsToPush)
            if (pushJson.toByteArray(Charsets.UTF_8).size.toLong() > MAX_BODY_BYTES) {
                throw FmhyXException.LimitExceeded(SyncLimit.BODY_SIZE)
            }
        }
    }

    private suspend fun pushAndRemove(
        token: String,
        remote: RemoteAccount,
        remoteOps: RemoteOps,
    ): RemoteAccount {
        if (remoteOps.favoritesToRemove.isEmpty() &&
            remoteOps.favoritesToPush.isEmpty() &&
            remoteOps.collectionsToPush.isEmpty()
        ) {
            return remote
        }
        for (item in remoteOps.favoritesToRemove) {
            client.removeFavorite(token, item)
        }
        return if (remoteOps.favoritesToPush.isNotEmpty() || remoteOps.collectionsToPush.isNotEmpty()) {
            client.push(token, remoteOps.favoritesToPush, remoteOps.collectionsToPush)
        } else {
            client.pull(token)
        }
    }

    private fun decodeBaseState(raw: String?): BaseState {
        if (raw.isNullOrBlank()) return BaseState()
        return runCatching {
            FmhyXClient.json.decodeFromString(BaseState.serializer(), raw)
        }.getOrElse { ex ->
            when (ex) {
                is SerializationException, is IllegalArgumentException -> BaseState()
                else -> throw ex
            }
        }
    }

    private fun planTransactionApply(
        snapshot: BookmarksSnapshot,
        current: BookmarksSnapshot,
        ops: LocalOps,
        rPrime: RemoteAccount,
        excluded: Set<String>,
        clock: () -> Long,
    ): Pair<SyncApplyResult, Boolean> {
        val snapFoldersById = snapshot.folders.associateBy { it.id }
        val currFoldersById = current.folders.associateBy { it.id }
        val changedFolderIds = HashSet<Long>()
        val changedFolderSyncIds = HashSet<String>()

        for ((id, sf) in snapFoldersById) {
            val cf = currFoldersById[id]
            if (cf == null || cf.name != sf.name || cf.syncId != sf.syncId) {
                changedFolderIds.add(id)
                sf.syncId?.let(changedFolderSyncIds::add)
                cf?.syncId?.let(changedFolderSyncIds::add)
            }
        }
        val addedCurrFolders = current.folders.filter { it.id !in snapFoldersById }
        val addedCurrFolderNames = addedCurrFolders.mapTo(HashSet()) { linkComparisonName(it.name) }
        val addedCurrFolderSyncIds = addedCurrFolders.mapNotNullTo(HashSet()) { it.syncId }

        val snapByUrl = snapshot.bookmarks.groupBy { it.url }
            .mapValues { (_, list) -> list.map { it.toSyncView() }.sortedBy { it.entryId } }
        val currByUrl = current.bookmarks.groupBy { it.url }
            .mapValues { (_, list) -> list.map { it.toSyncView() }.sortedBy { it.entryId } }
        val changedUrls = (snapByUrl.keys union currByUrl.keys)
            .filterTo(HashSet()) { u -> snapByUrl[u] != currByUrl[u] }
        val currEntryIds = current.bookmarks.mapTo(HashSet()) { it.entryId }
        val currFolderIdBySyncId = current.folders.mapNotNull { f -> f.syncId?.let { it to f.id } }.toMap()
        val snapFolderSyncIdById = snapshot.folders.mapNotNull { f -> f.syncId?.let { f.id to it } }.toMap()
        val snapRowByEntryId = snapshot.bookmarks.associateBy { it.entryId }

        var anyOpSkipped = false

        val appliedCreateFolders = mutableListOf<CreateFolderOp>()
        for (op in ops.createFolders) {
            if (op.syncId in currFolderIdBySyncId ||
                op.syncId in addedCurrFolderSyncIds ||
                op.syncId in changedFolderSyncIds ||
                linkComparisonName(op.name) in addedCurrFolderNames
            ) {
                anyOpSkipped = true
            } else {
                appliedCreateFolders.add(op)
            }
        }

        val appliedDeleteFolderSyncIds = LinkedHashSet<String>()
        val deleteFolderIds = mutableListOf<Long>()
        for (sid in ops.deleteFolderSyncIds) {
            val folderId = currFolderIdBySyncId[sid]
            if (sid in changedFolderSyncIds || folderId == null) {
                anyOpSkipped = true
            } else {
                appliedDeleteFolderSyncIds.add(sid)
                deleteFolderIds.add(folderId)
            }
        }

        val appliedRenameFolders = mutableListOf<RenameFolderOp>()
        for (op in ops.renameFolders) {
            if (op.folderId in changedFolderIds || op.syncId in changedFolderSyncIds || op.folderId !in currFoldersById) {
                anyOpSkipped = true
            } else {
                appliedRenameFolders.add(op)
            }
        }

        val liveSyncIdsAfterFolderOps = current.folders.mapNotNullTo(HashSet()) { it.syncId }.apply {
            removeAll(appliedDeleteFolderSyncIds)
            appliedCreateFolders.forEach { add(it.syncId) }
        }

        val appliedDeleteUrls = LinkedHashSet<String>()
        val deleteBookmarkEntryIds = mutableListOf<String>()
        for (u in ops.deleteBookmarkUrls) {
            if (u in changedUrls) {
                anyOpSkipped = true
            } else {
                appliedDeleteUrls.add(u)
                current.bookmarks.filter { it.url == u }.forEach { deleteBookmarkEntryIds.add(it.entryId) }
            }
        }

        val appliedUpdateBookmarks = mutableListOf<UpdateBookmarkOp>()
        for (op in ops.updateBookmarks) {
            if (op.url in changedUrls || op.entryId !in currEntryIds) {
                anyOpSkipped = true
            } else {
                appliedUpdateBookmarks.add(op)
            }
        }

        val appliedAddBookmarks = mutableListOf<AddBookmarkOp>()
        for (op in ops.addBookmarks) {
            val targetBlocked = op.folderSyncId != null &&
                (op.folderSyncId in changedFolderSyncIds || op.folderSyncId !in liveSyncIdsAfterFolderOps)
            if (op.bookmark.url in changedUrls || op.bookmark.entryId in currEntryIds || targetBlocked) {
                anyOpSkipped = true
            } else {
                appliedAddBookmarks.add(op)
            }
        }

        val appliedMoveBookmarks = mutableListOf<MoveBookmarkOp>()
        for (op in ops.moveBookmarks) {
            val snapFolderSid = snapRowByEntryId[op.entryId]?.folderId?.let(snapFolderSyncIdById::get)
            val targetBlocked = op.folderSyncId != null &&
                (op.folderSyncId in changedFolderSyncIds || op.folderSyncId !in liveSyncIdsAfterFolderOps)
            val sourceBlocked = snapFolderSid != null && snapFolderSid in changedFolderSyncIds
            if (op.url in changedUrls || op.entryId !in currEntryIds || targetBlocked || sourceBlocked) {
                anyOpSkipped = true
            } else {
                appliedMoveBookmarks.add(op)
            }
        }

        val appliedOps = LocalOps(
            createFolders = appliedCreateFolders,
            renameFolders = appliedRenameFolders,
            deleteFolderSyncIds = appliedDeleteFolderSyncIds,
            addBookmarks = appliedAddBookmarks,
            deleteBookmarkUrls = appliedDeleteUrls,
            updateBookmarks = appliedUpdateBookmarks,
            moveBookmarks = appliedMoveBookmarks,
        )
        val settled = applyLocalOpsInMemory(snapshot, appliedOps, clock)
        val newBase = buildBaseState(settled, rPrime, excluded)
        val newBaseJson = FmhyXClient.json.encodeToString(BaseState.serializer(), newBase)

        val applyResult = SyncApplyResult(
            createFolders = appliedCreateFolders.map { SyncFolderCreate(it.syncId, it.name) },
            renameFolders = appliedRenameFolders.map { SyncFolderRename(it.folderId, it.newName) },
            deleteFolderIds = deleteFolderIds,
            deleteBookmarkEntryIds = deleteBookmarkEntryIds,
            updateBookmarks = appliedUpdateBookmarks.map { SyncBookmarkUpdate(it.entryId, it.savedAt, it.name) },
            addBookmarks = appliedAddBookmarks.map { SyncBookmarkAdd(it.bookmark, it.folderSyncId) },
            moveBookmarks = appliedMoveBookmarks.map { SyncBookmarkMove(it.entryId, it.folderSyncId) },
            baseJson = newBaseJson,
        )

        val changesPending = changedUrls.isNotEmpty() ||
            changedFolderIds.isNotEmpty() ||
            addedCurrFolders.isNotEmpty() ||
            anyOpSkipped

        return applyResult to changesPending
    }

    private data class BookmarkSyncView(
        val entryId: String,
        val name: String,
        val url: String,
        val sectionId: String,
        val savedAt: Long,
        val folderId: Long?,
    )

    private fun UserBookmark.toSyncView(): BookmarkSyncView = BookmarkSyncView(
        entryId = entryId,
        name = name,
        url = url,
        sectionId = sectionId,
        savedAt = savedAt,
        folderId = folderId,
    )

    companion object {
        internal const val MAX_SAVED_ITEMS = 5000
        internal const val MAX_FOLDERS = 50
        internal const val MAX_FOLDER_ITEMS = 500
        internal const val MAX_BODY_BYTES = 20L * 1024L * 1024L
    }
}
