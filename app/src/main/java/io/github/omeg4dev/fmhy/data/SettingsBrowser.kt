package io.github.omeg4dev.fmhy.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_AUTOPLAY
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_AUTO_PIP
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_BACKGROUND_PLAYBACK
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_BUBBLE_HIDDEN
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_BUBBLE_SIDE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_BUBBLE_Y
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_BUTTON
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_DEAD_SITE_RESCUE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_DEFAULT_SPEED
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_DESKTOP_BY_DEFAULT
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_DOUBLE_TAP_SEEK
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_FORCE_DARK
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_FORCE_ZOOM
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_FULLSCREEN_ORIENTATION
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_JAVASCRIPT
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_KEEP_SCREEN_ON
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_MAX_TABS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_OPEN_LINKS_WITH
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_PICTURE_IN_PICTURE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_RESTORE_TABS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_SEARCH_ENGINE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_SITE_BAR
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_TABS_VIEW
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_TEXT_ZOOM_FOLLOWS_APP
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_TOOLBAR_POSITION
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_VIDEO_AUTO_ROTATE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BROWSER_VIDEO_DOWNLOAD_BUTTON
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_OPEN_EXTERNAL_BROWSER

// DefaultSettingsRepository read/write helpers: browser settings.

internal fun DefaultSettingsRepository.readBrowserPrefs(prefs: Preferences): BrowserPrefs {
    val default = BrowserPrefs()
    val openLinksWith = if (prefs.contains(KEY_BROWSER_OPEN_LINKS_WITH)) {
        prefs[KEY_BROWSER_OPEN_LINKS_WITH]?.let { name ->
            try { OpenLinksWith.valueOf(name) } catch (_: IllegalArgumentException) { default.openLinksWith }
        } ?: default.openLinksWith
    } else {
        if (prefs[KEY_OPEN_EXTERNAL_BROWSER] == true) OpenLinksWith.EXTERNAL else default.openLinksWith
    }
    val searchEngine = prefs[KEY_BROWSER_SEARCH_ENGINE]?.let { name ->
        try { SearchEngine.valueOf(name) } catch (_: IllegalArgumentException) { default.searchEngine }
    } ?: default.searchEngine
    val toolbarPosition = prefs[KEY_BROWSER_TOOLBAR_POSITION]?.let { name ->
        try { ToolbarPosition.valueOf(name) } catch (_: IllegalArgumentException) { default.toolbarPosition }
    } ?: default.toolbarPosition
    val browserButton = prefs[KEY_BROWSER_BUTTON]?.let { name ->
        try { BrowserButtonStyle.valueOf(name) } catch (_: IllegalArgumentException) { default.browserButton }
    } ?: default.browserButton
    val bubbleSide = prefs[KEY_BROWSER_BUBBLE_SIDE]?.let { name ->
        try { BubbleSide.valueOf(name) } catch (_: IllegalArgumentException) { default.bubbleSide }
    } ?: default.bubbleSide
    val tabsView = prefs[KEY_BROWSER_TABS_VIEW]?.let { name ->
        try { TabsView.valueOf(name) } catch (_: IllegalArgumentException) { default.tabsView }
    } ?: default.tabsView

    return BrowserPrefs(
        openLinksWith = openLinksWith,
        siteBar = prefs[KEY_BROWSER_SITE_BAR] ?: default.siteBar,
        deadSiteRescue = prefs[KEY_BROWSER_DEAD_SITE_RESCUE] ?: default.deadSiteRescue,
        maxTabs = prefs[KEY_BROWSER_MAX_TABS] ?: default.maxTabs,
        restoreTabs = prefs[KEY_BROWSER_RESTORE_TABS] ?: default.restoreTabs,
        searchEngine = searchEngine,
        toolbarPosition = toolbarPosition,
        browserButton = browserButton,
        browserBubbleHidden = prefs[KEY_BROWSER_BUBBLE_HIDDEN] ?: default.browserBubbleHidden,
        bubbleSide = bubbleSide,
        bubbleY = (prefs[KEY_BROWSER_BUBBLE_Y] ?: default.bubbleY).coerceIn(0f, 1f),
        tabsView = tabsView,
        javascript = prefs[KEY_BROWSER_JAVASCRIPT] ?: default.javascript,
        forceDark = prefs[KEY_BROWSER_FORCE_DARK] ?: default.forceDark,
        desktopByDefault = prefs[KEY_BROWSER_DESKTOP_BY_DEFAULT] ?: default.desktopByDefault,
        textZoomFollowsApp = prefs[KEY_BROWSER_TEXT_ZOOM_FOLLOWS_APP] ?: default.textZoomFollowsApp,
        forceZoom = prefs[KEY_BROWSER_FORCE_ZOOM] ?: default.forceZoom,
        fullscreenOrientation = resolveFullscreenOrientation(
            prefs[KEY_BROWSER_FULLSCREEN_ORIENTATION],
            prefs[KEY_BROWSER_VIDEO_AUTO_ROTATE],
        ),
        pictureInPicture = prefs[KEY_BROWSER_PICTURE_IN_PICTURE] ?: default.pictureInPicture,
        autoPip = prefs[KEY_BROWSER_AUTO_PIP] ?: default.autoPip,
        keepScreenOn = prefs[KEY_BROWSER_KEEP_SCREEN_ON] ?: default.keepScreenOn,
        backgroundPlayback = prefs[KEY_BROWSER_BACKGROUND_PLAYBACK] ?: default.backgroundPlayback,
        autoplay = prefs[KEY_BROWSER_AUTOPLAY]?.let { name -> AutoplayMode.entries.firstOrNull { it.name == name } } ?: default.autoplay,
        doubleTapSeek = prefs[KEY_BROWSER_DOUBLE_TAP_SEEK] ?: default.doubleTapSeek,
        defaultSpeed = prefs[KEY_BROWSER_DEFAULT_SPEED] ?: default.defaultSpeed,
        videoDownloadButton = prefs[KEY_BROWSER_VIDEO_DOWNLOAD_BUTTON] ?: default.videoDownloadButton,
    )
}
internal fun DefaultSettingsRepository.writeBrowserPrefs(prefs: MutablePreferences, p: BrowserPrefs) {
    prefs[KEY_BROWSER_OPEN_LINKS_WITH] = p.openLinksWith.name
    prefs[KEY_BROWSER_SITE_BAR] = p.siteBar
    prefs[KEY_BROWSER_DEAD_SITE_RESCUE] = p.deadSiteRescue
    prefs[KEY_BROWSER_MAX_TABS] = p.maxTabs
    prefs[KEY_BROWSER_RESTORE_TABS] = p.restoreTabs
    prefs[KEY_BROWSER_SEARCH_ENGINE] = p.searchEngine.name
    prefs[KEY_BROWSER_TOOLBAR_POSITION] = p.toolbarPosition.name
    prefs[KEY_BROWSER_BUTTON] = p.browserButton.name
    prefs[KEY_BROWSER_BUBBLE_HIDDEN] = p.browserBubbleHidden
    prefs[KEY_BROWSER_BUBBLE_SIDE] = p.bubbleSide.name
    prefs[KEY_BROWSER_BUBBLE_Y] = p.bubbleY
    prefs[KEY_BROWSER_TABS_VIEW] = p.tabsView.name
    prefs[KEY_BROWSER_JAVASCRIPT] = p.javascript
    prefs[KEY_BROWSER_FORCE_DARK] = p.forceDark
    prefs[KEY_BROWSER_DESKTOP_BY_DEFAULT] = p.desktopByDefault
    prefs[KEY_BROWSER_TEXT_ZOOM_FOLLOWS_APP] = p.textZoomFollowsApp
    prefs[KEY_BROWSER_FORCE_ZOOM] = p.forceZoom
    prefs[KEY_BROWSER_FULLSCREEN_ORIENTATION] = p.fullscreenOrientation.name
    prefs[KEY_BROWSER_PICTURE_IN_PICTURE] = p.pictureInPicture
    prefs[KEY_BROWSER_AUTO_PIP] = p.autoPip
    prefs[KEY_BROWSER_KEEP_SCREEN_ON] = p.keepScreenOn
    prefs[KEY_BROWSER_BACKGROUND_PLAYBACK] = p.backgroundPlayback
    prefs[KEY_BROWSER_AUTOPLAY] = p.autoplay.name
    prefs[KEY_BROWSER_DOUBLE_TAP_SEEK] = p.doubleTapSeek
    prefs[KEY_BROWSER_DEFAULT_SPEED] = p.defaultSpeed
    prefs[KEY_BROWSER_VIDEO_DOWNLOAD_BUTTON] = p.videoDownloadButton
}
