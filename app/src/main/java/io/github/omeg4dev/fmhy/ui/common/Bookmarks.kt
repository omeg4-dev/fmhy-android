package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.user.Folder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

interface SaveActions {
    fun save(entry: Entry)
    fun unsave(entry: Entry)
    fun toggle(entry: Entry)
    fun isSaved(entry: Entry): Boolean = isSaved(entry.id)
    fun isSaved(entryId: String): Boolean = false
}

@Composable
fun rememberSaveActions(
    onShowSnackbar: (String) -> Unit = {},
): SaveActions {
    val bookmarksState = LocalBookmarks.current
    val userStore = LocalUserStore.current
    val snackbarHost = LocalSnackbarHost.current
    val haptics = rememberHaptics()
    val rowScope = rememberCoroutineScope()
    // Unsave + Undo outlives the row: in Saved the row leaves the list the moment its bookmark is removed,
    // which cancelled a row-scoped coroutine mid-snackbar and lost the Undo.
    val appScope = (LocalContext.current.applicationContext as? FmhyApp)?.container?.appScope
    val scope = remember(appScope, rowScope) {
        if (appScope != null) CoroutineScope(appScope.coroutineContext + Dispatchers.Main.immediate) else rowScope
    }

    return remember(bookmarksState, userStore, snackbarHost, haptics, scope, onShowSnackbar) {
        object : SaveActions {
            override fun isSaved(entryId: String): Boolean =
                entryId in bookmarksState.savedIds

            override fun save(entry: Entry) {
                haptics.confirm()
                bookmarksState.toggle(entry)
                val host = snackbarHost
                if (host != null) {
                    scope.launch { host.showSnackbar("Saved") }
                } else {
                    onShowSnackbar("Saved")
                }
            }

            override fun unsave(entry: Entry) {
                haptics.click()
                scope.launch {
                    val snapshot = userStore?.removeBookmarkWithSnapshot(entry.id)
                    if (snapshot == null) {
                        bookmarksState.toggle(entry)
                    }
                    val host = snackbarHost
                    if (host != null) {
                        if (host.showUndo("Removed from Saved")) {
                            if (snapshot != null) userStore.restoreBookmark(snapshot)
                            else bookmarksState.toggle(entry)
                        }
                    } else {
                        onShowSnackbar("Removed from Saved")
                    }
                }
            }

            override fun toggle(entry: Entry) {
                if (isSaved(entry)) {
                    unsave(entry)
                } else {
                    save(entry)
                }
            }
        }
    }
}
