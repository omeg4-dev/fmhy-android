package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.di.AppContainer

val LocalDatasetStore = staticCompositionLocalOf<DatasetStore?> { null }
val LocalUserStore = staticCompositionLocalOf<UserStore?> { null }
val LocalAppContainer = staticCompositionLocalOf<AppContainer?> { null }
