package io.github.omeg4dev.fmhy.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.Bookmark
import io.github.omeg4dev.fmhy.data.BookmarksRepository
import io.github.omeg4dev.fmhy.data.DataPrefs
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.user.Folder
import io.github.omeg4dev.fmhy.data.user.HistoryItem
import io.github.omeg4dev.fmhy.data.user.UserBookmark
import io.github.omeg4dev.fmhy.data.user.UserStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SavedEntryItem(
    val bookmark: Bookmark,
    val liveEntry: Entry?,
    val folderName: String? = null,
    val removedDateText: String? = null,
)

data class SavedGroup(
    val sectionTitle: String,
    val newestSavedAt: Long,
    val items: List<SavedEntryItem>,
)

data class SavedUiState(
    val isLoading: Boolean = true,
    val groups: List<SavedGroup> = emptyList(), // All groups
    val bookmarks: List<Bookmark> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val folderCounts: Map<Long, Int> = emptyMap(),
    val folderGroups: Map<Long, List<SavedGroup>> = emptyMap(),
    val historyGroups: List<HistoryGroup> = emptyList(),
    val keepHistory: Boolean = true,
)

class SavedViewModel(
    private val store: DatasetStore,
    private val bookmarksRepository: BookmarksRepository,
    private val userStore: UserStore? = null,
    private val settingsRepository: SettingsRepository? = null,
) : ViewModel() {

    private val dataPrefsFlow: Flow<DataPrefs> = settingsRepository?.dataPrefs ?: flowOf(DataPrefs())
    private val userChangesFlow: Flow<Long> = userStore?.changes ?: flowOf(0L)

    val uiState: StateFlow<SavedUiState> = combine(
        bookmarksRepository.bookmarks,
        bookmarksRepository.folders,
        store.changes,
        userChangesFlow,
        dataPrefsFlow,
    ) { bookmarks, folders, _, _, dataPrefs ->
        val folderMap = folders.associate { it.id to it.name }
        val folderCounts = bookmarks.groupBy { it.folderId }
            .mapNotNull { (fid, list) -> fid?.let { it to list.size } }
            .toMap()

        val ids = bookmarks.map { it.entryId }
        val liveEntriesMap = if (store.isAvailable) {
            try {
                store.entries(ids).associateBy { it.id }
            } catch (_: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }

        // Query removed date for bookmarks with no live entry
        val removedDates = mutableMapOf<String, String>()
        if (store.isAvailable) {
            for (bm in bookmarks) {
                if (liveEntriesMap[bm.entryId] == null) {
                    try {
                        val history = store.entryHistory(bm.entryId)
                        val removedChange = history.lastOrNull { it.kind == ChangeKind.REMOVED }
                        if (removedChange != null) {
                            removedDates[bm.entryId] = SavedGrouping.formatRemovedDate(removedChange.ts)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        val allGroups = SavedGrouping.groupBookmarks(
            bookmarks = bookmarks,
            liveEntriesMap = liveEntriesMap,
            folderMap = folderMap,
            removedDates = removedDates,
        )

        val folderGroups = folders.associate { folder ->
            folder.id to SavedGrouping.groupBookmarks(
                bookmarks = bookmarks.filter { it.folderId == folder.id },
                liveEntriesMap = liveEntriesMap,
                folderMap = folderMap,
                removedDates = removedDates,
            )
        }

        val historyGroups = if (userStore != null) {
            try {
                val historyItems = userStore.history()
                val historyEntryIds = historyItems.mapNotNull { it.entryId }.distinct()
                val historyLiveEntries = if (store.isAvailable && historyEntryIds.isNotEmpty()) {
                    store.entries(historyEntryIds).associateBy { it.id }
                } else {
                    emptyMap()
                }
                SavedGrouping.groupHistory(historyItems, historyLiveEntries)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        SavedUiState(
            isLoading = false,
            groups = allGroups,
            bookmarks = bookmarks,
            folders = folders,
            folderCounts = folderCounts,
            folderGroups = folderGroups,
            historyGroups = historyGroups,
            keepHistory = dataPrefs.keepHistory,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SavedUiState(isLoading = true),
    )

    fun removeBookmark(entryId: String) {
        viewModelScope.launch {
            bookmarksRepository.remove(entryId)
        }
    }

    suspend fun removeBookmarkWithSnapshot(entryId: String): UserBookmark? {
        return userStore?.removeBookmarkWithSnapshot(entryId)
    }

    suspend fun restoreBookmark(b: UserBookmark) {
        userStore?.restoreBookmark(b)
    }

    suspend fun deleteFolderWithSnapshot(id: Long): UserStore.DeletedFolderSnapshot? {
        return userStore?.deleteFolderWithSnapshot(id)
    }

    suspend fun restoreFolder(snapshot: UserStore.DeletedFolderSnapshot) {
        userStore?.restoreFolder(snapshot)
    }

    suspend fun clearHistoryWithSnapshot(): List<HistoryItem> {
        return userStore?.clearHistoryWithSnapshot() ?: emptyList()
    }

    suspend fun restoreHistory(items: List<HistoryItem>) {
        userStore?.restoreHistory(items)
    }

    fun setNote(entryId: String, note: String) {
        viewModelScope.launch {
            bookmarksRepository.setNote(entryId, note)
        }
    }

    fun moveToFolder(entryId: String, folderId: Long?) {
        viewModelScope.launch {
            bookmarksRepository.moveToFolder(entryId, folderId)
        }
    }

    fun createFolder(name: String, onCreated: ((Folder) -> Unit)? = null) {
        viewModelScope.launch {
            val f = bookmarksRepository.createFolder(name)
            onCreated?.invoke(f)
        }
    }

    fun renameFolder(id: Long, name: String) {
        viewModelScope.launch {
            bookmarksRepository.renameFolder(id, name)
        }
    }

    fun deleteFolder(id: Long) {
        viewModelScope.launch {
            bookmarksRepository.deleteFolder(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            userStore?.clearHistory()
        }
    }

    fun enableKeepHistory() {
        viewModelScope.launch {
            settingsRepository?.updateData { it.copy(keepHistory = true) }
        }
    }

    suspend fun getReplacements(bookmark: Bookmark): List<Entry> {
        if (!store.isAvailable) return emptyList()
        val sectionEntries = try {
            store.entriesForSection(bookmark.sectionId)
        } catch (_: Exception) {
            emptyList()
        }
        val headings = try {
            store.headings(bookmark.sectionId)
        } catch (_: Exception) {
            emptyList()
        }
        return SavedGrouping.findReplacements(bookmark, sectionEntries, headings)
    }
}
