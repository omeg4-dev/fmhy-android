package io.github.omeg4dev.fmhy.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.BuildConfig
import io.github.omeg4dev.fmhy.data.Bookmark
import io.github.omeg4dev.fmhy.data.BookmarksRepository
import io.github.omeg4dev.fmhy.data.DataPrefs
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.HomePrefs
import io.github.omeg4dev.fmhy.data.SavedSort
import io.github.omeg4dev.fmhy.data.SavedViewPrefs
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.nsfw.NSFW_SECTION_ID
import io.github.omeg4dev.fmhy.data.nsfw.NsfwSource
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
    val items: List<SavedEntryItem> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val folderCounts: Map<Long, Int> = emptyMap(),
    val historyGroups: List<HistoryGroup> = emptyList(),
    val keepHistory: Boolean = true,
    val sort: SavedSort = SavedSort.RECENT,
    val groupBySection: Boolean = false,
)

private data class CombinedPrefs(
    val data: DataPrefs = DataPrefs(),
    val home: HomePrefs = HomePrefs(),
    val savedView: SavedViewPrefs = SavedViewPrefs(),
)

class SavedViewModel(
    private val store: DatasetStore,
    private val bookmarksRepository: BookmarksRepository,
    private val userStore: UserStore? = null,
    private val settingsRepository: SettingsRepository? = null,
    nsfwSource: NsfwSource? = null,
    nsfwEnabledInBuild: Boolean = BuildConfig.NSFW_LINK_ENABLED,
) : ViewModel() {

    private val prefsFlow: Flow<CombinedPrefs> = if (settingsRepository != null) {
        combine(
            settingsRepository.dataPrefs,
            settingsRepository.homePrefs,
            settingsRepository.savedViewPrefs,
        ) { dataPrefs, homePrefs, savedViewPrefs ->
            CombinedPrefs(dataPrefs, homePrefs, savedViewPrefs)
        }
    } else {
        flowOf(CombinedPrefs())
    }
    private val userChangesFlow: Flow<Long> = userStore?.changes ?: flowOf(0L)

    val uiState: StateFlow<SavedUiState> = combine(
        bookmarksRepository.bookmarks,
        bookmarksRepository.folders,
        store.changes,
        userChangesFlow,
        prefsFlow,
    ) { bookmarks, folders, _, _, prefs ->
        val folderMap = folders.associate { it.id to it.name }
        val folderCounts = bookmarks.groupBy { it.folderId }
            .mapNotNull { (fid, list) -> fid?.let { it to list.size } }
            .toMap()

        val dbIds = bookmarks.filter { it.sectionId != NSFW_SECTION_ID }.map { it.entryId }
        val dbLiveMap = if (store.isAvailable && dbIds.isNotEmpty()) {
            try {
                store.entries(dbIds).associateBy { it.id }
            } catch (_: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }

        val nsfwBookmarks = bookmarks.filter { it.sectionId == NSFW_SECTION_ID }
        val liveEntriesMap = if (nsfwBookmarks.isEmpty()) {
            dbLiveMap
        } else {
            val cachedById = if (nsfwEnabledInBuild && prefs.home.showNsfwLink) {
                runCatching { nsfwSource?.readCached()?.entries?.associateBy { it.id } }.getOrNull().orEmpty()
            } else {
                emptyMap()
            }
            val merged = dbLiveMap.toMutableMap()
            for (bm in nsfwBookmarks) {
                merged[bm.entryId] = cachedById[bm.entryId] ?: fallbackNsfwEntry(bm)
            }
            merged
        }

        // Query removed date for bookmarks with no live entry
        val removedDates = mutableMapOf<String, String>()
        if (store.isAvailable) {
            for (bm in bookmarks) {
                if (bm.sectionId != NSFW_SECTION_ID && liveEntriesMap[bm.entryId] == null) {
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

        val items = SavedGrouping.buildItems(
            bookmarks = bookmarks,
            liveEntriesMap = liveEntriesMap,
            folderMap = folderMap,
            removedDates = removedDates,
        )

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
            items = items,
            folders = folders,
            folderCounts = folderCounts,
            historyGroups = historyGroups,
            keepHistory = prefs.data.keepHistory,
            sort = prefs.savedView.sort,
            groupBySection = prefs.savedView.groupBySection,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SavedUiState(isLoading = true),
    )

    fun setSort(sort: SavedSort) {
        viewModelScope.launch {
            settingsRepository?.updateSavedViewPrefs { it.copy(sort = sort) }
        }
    }

    fun setGroupBySection(on: Boolean) {
        viewModelScope.launch {
            settingsRepository?.updateSavedViewPrefs { it.copy(groupBySection = on) }
        }
    }

    suspend fun removeBookmarksWithSnapshot(ids: Collection<String>): List<UserBookmark> {
        val store = userStore ?: return emptyList()
        return ids.mapNotNull { store.removeBookmarkWithSnapshot(it) }
    }

    suspend fun restoreBookmarks(list: List<UserBookmark>) {
        val store = userStore ?: return
        list.forEach { store.restoreBookmark(it) }
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

    fun moveToFolder(entryIds: Collection<String>, folderId: Long?) {
        viewModelScope.launch {
            entryIds.forEach { bookmarksRepository.moveToFolder(it, folderId) }
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

    fun enableKeepHistory() {
        viewModelScope.launch {
            settingsRepository?.updateData { it.copy(keepHistory = true) }
        }
    }

    suspend fun getReplacements(bookmark: Bookmark): List<Entry> {
        if (bookmark.sectionId == NSFW_SECTION_ID || !store.isAvailable) return emptyList()
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

    private fun fallbackNsfwEntry(bm: Bookmark): Entry = Entry(
        id = bm.entryId,
        num = 0,
        sectionId = bm.sectionId,
        headingId = null,
        ord = 0,
        name = bm.name,
        url = bm.url,
        originalUrl = null,
        description = "",
        tags = emptyList(),
        headingPath = bm.headingPath,
        tier = Tier.NORMAL,
        kind = EntryKind.NORMAL,
        line = 0,
        isInlineMore = false,
        isBase64 = false,
        mirrors = emptyList(),
        extraLinks = emptyList(),
        firstSeen = 0L,
        lastChanged = 0L,
        starredAt = null,
    )
}
