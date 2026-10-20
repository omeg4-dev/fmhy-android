package io.github.omeg4dev.fmhy.ui.posts

import io.github.omeg4dev.fmhy.data.user.UserStore
import kotlinx.serialization.json.Json

object PostsReadState {
    const val KEY_POSTS_READ = "posts_read"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun getReadPaths(userStore: UserStore): Set<String>? {
        val raw = userStore.get(KEY_POSTS_READ) ?: return null
        return try {
            json.decodeFromString<List<String>>(raw).toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun markRead(userStore: UserStore, path: String) {
        val current = getReadPaths(userStore) ?: emptySet()
        val updated = current + path
        userStore.put(KEY_POSTS_READ, json.encodeToString(updated.toList()))
    }

    suspend fun markAllRead(userStore: UserStore, allPaths: List<String>) {
        val current = getReadPaths(userStore) ?: emptySet()
        val updated = current + allPaths
        userStore.put(KEY_POSTS_READ, json.encodeToString(updated.toList()))
    }

    suspend fun initializeIfFirstRun(userStore: UserStore, allPaths: List<String>): Set<String> {
        val raw = userStore.get(KEY_POSTS_READ)
        if (raw == null) {
            val allSet = allPaths.toSet()
            userStore.put(KEY_POSTS_READ, json.encodeToString(allSet.toList()))
            return allSet
        }
        return try {
            json.decodeFromString<List<String>>(raw).toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun getUnreadCount(userStore: UserStore, allPaths: List<String>): Int {
        if (allPaths.isEmpty()) return 0
        val raw = userStore.get(KEY_POSTS_READ)
        if (raw == null) {
            userStore.put(KEY_POSTS_READ, json.encodeToString(allPaths))
            return 0
        }
        val readPaths = try {
            json.decodeFromString<List<String>>(raw).toSet()
        } catch (_: Exception) {
            emptySet()
        }
        return allPaths.count { it !in readPaths }
    }

    /** Unread count for the cached posts listing (0 when nothing is cached yet). */
    suspend fun unreadCountFromCache(userStore: UserStore, filesDir: java.io.File): Int {
        val cached = PostsListing.readCache(java.io.File(filesDir, "posts-listing.json")) ?: return 0
        return getUnreadCount(userStore, cached.posts.map { it.path })
    }
}
