package io.github.omeg4dev.fmhy.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.user.Folder
import io.github.omeg4dev.fmhy.data.user.UserBookmark
import io.github.omeg4dev.fmhy.data.user.UserStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val Context.bookmarksDataStore: DataStore<Preferences> by preferencesDataStore(name = "bookmarks")

@Serializable
data class Bookmark(
    val entryId: String,
    val name: String,
    val url: String,
    val sectionId: String,
    val sectionTitle: String,
    val headingPath: String,
    val savedAt: Long,
    val note: String = "",
    val folderId: Long? = null,
)

class BookmarksRepository(
    private val userStore: UserStore,
    private val legacyDataStore: DataStore<Preferences>? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val KEY_BOOKMARKS_V1 = stringPreferencesKey("bookmarks_v1")
        private const val KV_MIGRATED_V1 = "bookmarks_migrated_v1"
    }

    private val migrationDeferred = CompletableDeferred<Unit>()

    init {
        if (legacyDataStore == null) {
            migrationDeferred.complete(Unit)
        }
    }

    suspend fun migrateLegacyIfNeeded() {
        try {
            if (userStore.get(KV_MIGRATED_V1) == "1") {
                return
            }
            val dataStore = legacyDataStore ?: return
            val prefs = try {
                dataStore.data.first()
            } catch (e: Exception) {
                logE("BookmarksRepository", "Failed to read legacy DataStore: ${e.message}", e)
                return
            }
            val raw = prefs[KEY_BOOKMARKS_V1]
            val legacyList = if (raw.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    json.decodeFromString<List<Bookmark>>(raw)
                } catch (e: Exception) {
                    logW("BookmarksRepository", "Corrupt legacy bookmarks JSON: ${e.message}")
                    emptyList()
                }
            }

            if (legacyList.isNotEmpty()) {
                val userBookmarks = legacyList.map { it.toUserBookmark() }
                userStore.insertBookmarksIfAbsent(userBookmarks)
                val currentIds = userStore.bookmarks().map { it.entryId }.toSet()
                val allPresent = legacyList.all { it.entryId in currentIds }
                if (!allPresent) {
                    logE("BookmarksRepository", "Migration verification failed: missing entryIds in UserStore")
                    return
                }
            }
            userStore.put(KV_MIGRATED_V1, "1")
        } catch (e: Exception) {
            logE("BookmarksRepository", "Migration failed: ${e.message}", e)
        } finally {
            migrationDeferred.complete(Unit)
        }
    }

    val bookmarks: Flow<List<Bookmark>> = flow {
        migrationDeferred.await()
        emitAll(
            userStore.changes.map {
                userStore.bookmarks().map { it.toBookmark() }
            }.distinctUntilChanged()
        )
    }

    val savedIds: Flow<Set<String>> = bookmarks.map { list ->
        list.mapTo(HashSet()) { it.entryId }
    }.distinctUntilChanged()

    val folders: Flow<List<Folder>> = flow {
        migrationDeferred.await()
        emitAll(
            userStore.changes.map {
                userStore.folders()
            }.distinctUntilChanged()
        )
    }

    suspend fun toggle(entry: Entry, sectionTitle: String) {
        val existing = userStore.bookmark(entry.id)
        if (existing != null) {
            userStore.removeBookmark(entry.id)
        } else {
            val newBookmark = UserBookmark(
                entryId = entry.id,
                name = entry.name,
                url = entry.url,
                sectionId = entry.sectionId,
                sectionTitle = sectionTitle,
                headingPath = entry.headingPath,
                savedAt = clock(),
            )
            userStore.upsertBookmark(newBookmark)
        }
    }

    suspend fun remove(entryId: String) {
        userStore.removeBookmark(entryId)
    }

    suspend fun setNote(entryId: String, note: String) {
        userStore.setNote(entryId, note)
    }

    suspend fun moveToFolder(entryId: String, folderId: Long?) {
        userStore.moveToFolder(entryId, folderId)
    }

    suspend fun createFolder(name: String): Folder {
        return userStore.createFolder(name)
    }

    suspend fun renameFolder(id: Long, name: String) {
        userStore.renameFolder(id, name)
    }

    suspend fun deleteFolder(id: Long) {
        userStore.deleteFolder(id)
    }
}

private fun UserBookmark.toBookmark(): Bookmark = Bookmark(
    entryId = entryId,
    name = name,
    url = url,
    sectionId = sectionId,
    sectionTitle = sectionTitle,
    headingPath = headingPath,
    savedAt = savedAt,
    note = note,
    folderId = folderId,
)

private fun Bookmark.toUserBookmark(): UserBookmark = UserBookmark(
    entryId = entryId,
    name = name,
    url = url,
    sectionId = sectionId,
    sectionTitle = sectionTitle,
    headingPath = headingPath,
    savedAt = savedAt,
    note = note,
    folderId = folderId,
)

private fun logW(tag: String, msg: String) {
    try {
        Log.w(tag, msg)
    } catch (_: Throwable) {
        println("WARN: [$tag] $msg")
    }
}

private fun logE(tag: String, msg: String, tr: Throwable? = null) {
    try {
        if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
    } catch (_: Throwable) {
        println("ERROR: [$tag] $msg ${tr?.message.orEmpty()}")
    }
}
