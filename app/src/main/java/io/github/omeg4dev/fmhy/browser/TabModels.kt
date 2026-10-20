package io.github.omeg4dev.fmhy.browser

import android.graphics.Bitmap
import io.github.omeg4dev.fmhy.data.user.SitePrefs
import kotlinx.serialization.Serializable

data class TabState(
    val id: Long,
    val url: String,
    val title: String,
    val favicon: Bitmap? = null,
    val isPrivate: Boolean = false,
    val entryId: String? = null,
    val blocked: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val desktopSite: Boolean = false,
    val darkPage: Boolean = false,
    val sitePrefs: SitePrefs? = null,
)

@Serializable
data class SavedTab(
    val url: String,
    val title: String,
    val isPrivate: Boolean = false,
)
