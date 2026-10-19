package io.github.omeg4dev.fmhy.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.Bookmark
import io.github.omeg4dev.fmhy.data.BookmarksRepository
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.Entry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SavedEntryItem(
    val bookmark: Bookmark,
    val liveEntry: Entry?,
)

data class SavedGroup(
    val sectionTitle: String,
    val newestSavedAt: Long,
    val items: List<SavedEntryItem>,
)

data class SavedUiState(
    val isLoading: Boolean = true,
    val groups: List<SavedGroup> = emptyList(),
)

class SavedViewModel(
    private val store: DatasetStore,
    private val bookmarksRepository: BookmarksRepository,
) : ViewModel() {

    val uiState: StateFlow<SavedUiState> = combine(
        bookmarksRepository.bookmarks,
        store.changes,
    ) { bookmarks, _ ->
        if (bookmarks.isEmpty()) {
            return@combine SavedUiState(isLoading = false, groups = emptyList())
        }
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

        val groups = bookmarks
            .groupBy { it.sectionTitle }
            .map { (sectionTitle, bms) ->
                val newest = bms.maxOfOrNull { it.savedAt } ?: 0L
                val items = bms.map { bm ->
                    SavedEntryItem(
                        bookmark = bm,
                        liveEntry = liveEntriesMap[bm.entryId],
                    )
                }
                SavedGroup(
                    sectionTitle = sectionTitle,
                    newestSavedAt = newest,
                    items = items,
                )
            }
            .sortedByDescending { it.newestSavedAt }

        SavedUiState(isLoading = false, groups = groups)
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
}
