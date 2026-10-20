package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.user.UserStore

val LocalDatasetStore = staticCompositionLocalOf<DatasetStore?> { null }
val LocalUserStore = staticCompositionLocalOf<UserStore?> { null }
