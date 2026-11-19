package io.github.omeg4dev.fmhy.browser

/** Media playback, picture-in-picture, video controls, and playback speed for [TabManager]. */

import android.net.Uri
import io.github.omeg4dev.fmhy.browser.download.VideoSource
import io.github.omeg4dev.fmhy.browser.download.VideoSources
import io.github.omeg4dev.fmhy.data.AutoplayMode
import kotlinx.coroutines.launch

fun TabManager.setVideoPlaying(tabId: Long, isPlaying: Boolean) {
    val record = getTabRecord(tabId) ?: return
    record.isVideoPlaying = isPlaying
    updateTabsFlow()
    if (_currentTabId.value == tabId) {
        onVideoPlayingChanged?.invoke(isPlaying)
        onPipStateChanged?.invoke()
    }
}

/** Applies the autoplay setting to every live WebView straight away. */
internal fun TabManager.applyAutoplayToWebViews(mode: AutoplayMode) {
    val views = synchronized(records) { records.mapNotNull { it.webView } }
    val js = VideoJs.setAutoplayCall(autoplayScriptMode(mode))
    postToMain {
        views.forEach { wv ->
            try {
                wv.settings.mediaPlaybackRequiresUserGesture = autoplayNeedsGesture(mode)
                wv.evaluateJavascript(js, null)
            } catch (_: Throwable) {}
        }
    }
}

/** Pauses or resumes the media of [tabId]; used by the background playback notification. */
fun TabManager.controlMedia(tabId: Long, play: Boolean) {
    val record = getTabRecord(tabId) ?: return
    postToMain {
        val wv = record.webView ?: return@postToMain
        try {
            if (play) {
                // A programmatic play() has no user gesture; allow it for this call only.
                val strict = wv.settings.mediaPlaybackRequiresUserGesture
                if (strict) wv.settings.mediaPlaybackRequiresUserGesture = false
                wv.evaluateJavascript(VideoJs.RESUME_MEDIA, null)
                if (strict) mainHandler?.postDelayed({
                    try { wv.settings.mediaPlaybackRequiresUserGesture = currentBrowserPrefs.autoplay == AutoplayMode.BLOCK } catch (_: Throwable) {}
                }, 1500L)
            } else {
                wv.evaluateJavascript(VideoJs.PAUSE_MEDIA, null)
            }
        } catch (_: Throwable) {}
    }
}

/** Background playback is over (closed, idle too long): stop the media and pause the tab like normal. */
fun TabManager.endBackgroundPlayback() {
    val id = keepAliveTabId
    keepAliveTabId = null
    if (id == null) return
    val record = getTabRecord(id)
    postToMain {
        val wv = record?.webView
        if (wv != null) {
            try { wv.evaluateJavascript(VideoJs.PAUSE_MEDIA, null) } catch (_: Throwable) {}
        }
        if (!isActivityResumed) {
            if (record != null) pauseTab(record)
            // Timers are global: the kept tab's WebView was the one left running.
            try {
                val any = wv ?: synchronized(records) { records.firstNotNullOfOrNull { it.webView } }
                any?.pauseTimers()
            } catch (_: Throwable) {}
        }
    }
}

/** Skips the active tab's video by [seconds] (negative = back). */
fun TabManager.seekActiveVideo(seconds: Int) {
    val record = getActiveTabRecord() ?: return
    val js = VideoJs.seekCall(seconds)
    postToMain {
        try { record.webView?.evaluateJavascript(js, null) } catch (_: Throwable) {}
    }
}

fun TabManager.setHasMedia(tabId: Long) {
    val record = getTabRecord(tabId) ?: return
    if (record.hasMedia) return
    record.hasMedia = true
    updateTabsFlow()
}

fun TabManager.setVideoSources(tabId: Long, frame: String, list: List<VideoSource>) {
    val record = getTabRecord(tabId) ?: return
    if (list.isEmpty()) record.videoFrames.remove(frame) else record.videoFrames[frame] = list
    val merged = VideoSources.merge(record.videoFrames[""].orEmpty(), record.videoFrames.filterKeys { it.isNotEmpty() }.values)
    if (record.videoSources == merged) return
    record.videoSources = merged
    updateTabsFlow()
}

/** Called when a page starts loading: forgets the last page's media and looks up its remembered speed. */
fun TabManager.onPlaybackPageStarted(tabId: Long, url: String) {
    val record = getTabRecord(tabId) ?: return
    record.hasMedia = false
    record.desiredRate = 0f
    record.videoWidth = 0
    record.videoHeight = 0
    record.videoRect = null
    record.videoSources = emptyList()
    record.videoFrames.clear()
    val host = try { Uri.parse(url).host } catch (_: Exception) { null }
    appScope.launch {
        val stored = if (host.isNullOrEmpty()) null else try {
            parseStoredSpeed(userStore.get(videoSpeedKey(host)))
        } catch (_: Exception) { null }
        val rate = stored ?: currentBrowserPrefs.defaultSpeed.takeIf { it != 1f } ?: 0f
        record.desiredRate = rate
        if (rate > 0f) pushPlaybackRate(record)
        updateTabsFlow()
    }
}

/** Sets the playback speed for the tab's media and remembers it for the site (not in private tabs). */
fun TabManager.setPlaybackSpeed(tabId: Long, speed: Float) {
    val record = getTabRecord(tabId) ?: return
    record.desiredRate = speed
    pushPlaybackRate(record)
    updateTabsFlow(immediate = true)
    val host = try { Uri.parse(record.url).host } catch (_: Exception) { null }
    if (!record.isPrivate && !host.isNullOrEmpty()) {
        appScope.launch {
            try { userStore.put(videoSpeedKey(host), formatStoredSpeed(speed)) } catch (_: Exception) {}
        }
    }
}

private fun TabManager.pushPlaybackRate(record: TabRecord) {
    val js = VideoJs.setRateCall(record.desiredRate)
    postToMain {
        try { record.webView?.evaluateJavascript(js, null) } catch (_: Throwable) {}
    }
}

fun TabManager.setVideoSize(tabId: Long, width: Int, height: Int) {
    val record = getTabRecord(tabId) ?: return
    if (width <= 0 || height <= 0) return
    if (record.videoWidth == width && record.videoHeight == height) return
    record.videoWidth = width
    record.videoHeight = height
    updateTabsFlow()
    if (_currentTabId.value == tabId) onPipStateChanged?.invoke()
}

fun TabManager.setVideoRect(tabId: Long, left: Int, top: Int, right: Int, bottom: Int) {
    val record = getTabRecord(tabId) ?: return
    record.videoRect = if (right > left && bottom > top) intArrayOf(left, top, right, bottom) else null
    if (_currentTabId.value == tabId) onPipStateChanged?.invoke()
}

/** Makes the active tab's video fill the page while the picture-in-picture window shows it. */
fun TabManager.setPipActive(active: Boolean) {
    val record = getActiveTabRecord() ?: return
    val js = VideoJs.pipCall(active)
    postToMain {
        try { record.webView?.evaluateJavascript(js, null) } catch (_: Throwable) {}
    }
}

fun TabManager.isVideoPlaying(): Boolean {
    val id = _currentTabId.value ?: return false
    return getTabRecord(id)?.isVideoPlaying == true
}

fun TabManager.getActiveTabRecord(): TabRecord? {
    val id = _currentTabId.value ?: return null
    return getTabRecord(id)
}

