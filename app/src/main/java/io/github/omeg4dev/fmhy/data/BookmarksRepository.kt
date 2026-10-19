package io.github.omeg4dev.fmhy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.omeg4dev.fmhy.data.model.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
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
)

class BookmarksRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val KEY_BOOKMARKS_V1 = stringPreferencesKey("bookmarks_v1")
    }

    val bookmarks: Flow<List<Bookmark>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_BOOKMARKS_V1] ?: return@map emptyList()
        try {
            json.decodeFromString<List<Bookmark>>(raw)
                .sortedByDescending { it.savedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    val savedIds: Flow<Set<String>> = bookmarks.map { list ->
        list.mapTo(HashSet()) { it.entryId }
    }

    suspend fun toggle(entry: Entry, sectionTitle: String) {
        dataStore.edit { prefs ->
            val raw = prefs[KEY_BOOKMARKS_V1] ?: "[]"
            val existing = try {
                json.decodeFromString<List<Bookmark>>(raw).toMutableList()
            } catch (_: Exception) {
                mutableListOf()
            }
            val existingIndex = existing.indexOfFirst { it.entryId == entry.id }
            if (existingIndex != -1) {
                existing.removeAt(existingIndex)
            } else {
                val newBookmark = Bookmark(
                    entryId = entry.id,
                    name = entry.name,
                    url = entry.url,
                    sectionId = entry.sectionId,
                    sectionTitle = sectionTitle,
                    headingPath = entry.headingPath,
                    savedAt = clock(),
                )
                existing.add(0, newBookmark)
            }
            prefs[KEY_BOOKMARKS_V1] = json.encodeToString(existing)
        }
    }

    suspend fun remove(entryId: String) {
        dataStore.edit { prefs ->
            val raw = prefs[KEY_BOOKMARKS_V1] ?: return@edit
            val existing = try {
                json.decodeFromString<List<Bookmark>>(raw).toMutableList()
            } catch (_: Exception) {
                return@edit
            }
            if (existing.removeAll { it.entryId == entryId }) {
                prefs[KEY_BOOKMARKS_V1] = json.encodeToString(existing)
            }
        }
    }
}
