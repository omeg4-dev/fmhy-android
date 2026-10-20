package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.user.Folder

data class SavedInfo(
    val folderId: Long? = null,
    val folderName: String? = null,
    val note: String = "",
)

class BookmarksState(
    val savedIds: Set<String> = emptySet(),
    val toggle: (Entry) -> Unit = {},
    val folders: List<Folder> = emptyList(),
    val savedInfo: (String) -> SavedInfo? = { null },
    val setNote: (entryId: String, note: String) -> Unit = { _, _ -> },
    val moveToFolder: (entryId: String, folderId: Long?) -> Unit = { _, _ -> },
    val createFolder: (name: String, onCreated: (Folder) -> Unit) -> Unit = { _, _ -> },
)

val LocalBookmarks = compositionLocalOf { BookmarksState() }
val LocalSnackbarHost = compositionLocalOf<SnackbarHostState?> { null }
