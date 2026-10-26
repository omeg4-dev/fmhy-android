package io.github.omeg4dev.fmhy.browser

import android.graphics.Bitmap
import io.github.omeg4dev.fmhy.browser.download.VideoSource
import io.github.omeg4dev.fmhy.data.adblock.ResourceType
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
    /** The user pressed Retry and the page is loading again. */
    val retrying: Boolean = false,
    val kind: LoadFailureKind = LoadFailureKind.UNKNOWN,
    val canSkipBlocking: Boolean = false,
)

enum class WarningReason { UNSAFE, POTENTIALLY_UNSAFE, FILTER_BLOCK }

data class InterstitialState(
    val host: String,
    /** The blocked page's address. */
    val url: String,
    /** Every reason, most serious first; never empty. */
    val reasons: List<WarningReason>,
    /** Unsafe-list note without raw links; only when an unsafe-list reason is present. */
    val note: String?,
    /** Raw filter rule, shown only under "Details"; null unless FILTER_BLOCK is a reason. */
    val filterRule: String?,
)

data class BlockedRequest(
    val host: String,
    val url: String,
    val type: ResourceType,
    val rule: String,
)

data class BlockedHost(
    val host: String,
    val count: Int,
)

object BlockedHosts {
    fun group(requests: Collection<BlockedRequest>): List<BlockedHost> {
        return requests
            .groupingBy { it.host.lowercase() }
            .eachCount()
            .map { (host, count) -> BlockedHost(host, count) }
            .sortedWith(compareByDescending<BlockedHost> { it.count }.thenBy { it.host })
    }
}

data class TabState(
    val id: Long,
    val url: String,
    val title: String,
    val favicon: Bitmap? = null,
    val isPrivate: Boolean = false,
    /** The tab's address was a real website at least once; never reset. */
    val hadSite: Boolean = false,
    val entryId: String? = null,
    val blocked: Int = 0,
    val blockedHosts: List<BlockedHost> = emptyList(),
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
    /** The page on screen is the ad blocker's block page. */
    val showsBlockPage: Boolean = false,
    val isVideoPlaying: Boolean = false,
    /** width / height of the video last seen on the page, or null when unknown. */
    val videoAspect: Float? = null,
    /** The page has a <video> or <audio> element. */
    val hasMedia: Boolean = false,
    /** Current playback speed applied to the page's media (1 when untouched). */
    val playbackSpeed: Float = 1f,
    val videoSources: List<VideoSource> = emptyList(),
    val crashed: Boolean = false,
    val lastActiveTime: Long = 0L,
)

@Serializable
data class SavedTab(
    val url: String,
    val title: String,
    val isPrivate: Boolean = false,
)
