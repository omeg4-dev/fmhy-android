package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.runtime.compositionLocalOf
import io.github.omeg4dev.fmhy.data.model.Entry

class BookmarksState(
    val savedIds: Set<String> = emptySet(),
    val toggle: (Entry) -> Unit = {},
)

val LocalBookmarks = compositionLocalOf { BookmarksState() }
