package io.github.omeg4dev.fmhy.browser

import android.graphics.Bitmap
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Link
import io.github.omeg4dev.fmhy.data.user.SitePrefs
import kotlinx.serialization.Serializable

data class RescueState(
    val failingUrl: String,
    val host: String,
    val reason: String,
    val mirrors: List<Link> = emptyList(),
    val alternatives: List<Entry> = emptyList(),
)

data class InterstitialState(
    val host: String,
    val isUnsafe: Boolean, // true = UNSAFE, false = POTENTIALLY_UNSAFE
    val reason: String?,
)

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
    val rescueState: RescueState? = null,
    val siteBarState: SiteBarState? = null,
    val interstitialState: InterstitialState? = null,
    val isVideoPlaying: Boolean = false,
    /** width / height of the video last seen on the page, or null when unknown. */
    val videoAspect: Float? = null,
    val crashed: Boolean = false,
    val lastActiveTime: Long = 0L,
)

@Serializable
data class SavedTab(
    val url: String,
    val title: String,
    val isPrivate: Boolean = false,
)
