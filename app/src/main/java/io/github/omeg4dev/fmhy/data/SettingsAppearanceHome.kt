package io.github.omeg4dev.fmhy.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_ACCENT_SEED
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_APP_ICON
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_BOTTOM_BAR_STYLE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_COLOR_SOURCE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_HAPTIC_FEEDBACK
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_HOME_BLOCKS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_HOME_NEW_BADGE_DAYS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_HOME_PERSONAL_HOME
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_HOME_SEARCH_EXACT
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_HOME_SEARCH_TAP_TARGET
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_HOME_SHOW_NSFW_LINK
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_HOME_SWAP_TAP_HOLD
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_HOME_SWIPE_BETWEEN_SCREENS
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_HOME_SWIPE_TO_BOOKMARK
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_HOME_WEBSITE_CATEGORY_NAMES
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_PURE_BLACK
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_SECTION_LAYOUT
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_SECTION_ORDER
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_SHOW_INDEXES
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_SHOW_RANDOM_PICK
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_STARRED_ONLY
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_TEXT_SCALE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_THEME_MODE
import io.github.omeg4dev.fmhy.data.SettingsKeys.KEY_WEBSITE_STYLE
import io.github.omeg4dev.fmhy.nav.BottomBarStyle
import io.github.omeg4dev.fmhy.ui.home.HomeLayout

// DefaultSettingsRepository read/write helpers: appearance and home settings.

internal fun DefaultSettingsRepository.readAppearance(prefs: Preferences): Appearance {
    val themeMode = try {
        prefs[KEY_THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM
    } catch (_: IllegalArgumentException) {
        ThemeMode.SYSTEM
    }
    val colorSource = try {
        prefs[KEY_COLOR_SOURCE]?.let { ColorSource.valueOf(it) } ?: ColorSource.FMHY
    } catch (_: IllegalArgumentException) {
        ColorSource.FMHY
    }
    val sectionLayout = try {
        prefs[KEY_SECTION_LAYOUT]?.let { SectionLayout.valueOf(it) } ?: SectionLayout.CONTINUOUS
    } catch (_: IllegalArgumentException) {
        SectionLayout.CONTINUOUS
    }
    val pureBlack = prefs[KEY_PURE_BLACK] ?: false
    val accentSeed = prefs[KEY_ACCENT_SEED] ?: 0xFF7C5CFF.toInt()
    val textScale = (prefs[KEY_TEXT_SCALE] ?: 1.0f).coerceIn(0.85f, 1.30f)
    val sectionOrder = try {
        prefs[KEY_SECTION_ORDER]?.let { SectionOrder.valueOf(it) } ?: SectionOrder.WEBSITE
    } catch (_: IllegalArgumentException) {
        SectionOrder.WEBSITE
    }
    val showRandomPick = prefs[KEY_SHOW_RANDOM_PICK] ?: true
    val starredOnly = prefs[KEY_STARRED_ONLY] ?: false
    val showIndexes = prefs[KEY_SHOW_INDEXES] ?: true
    val hapticFeedback = prefs[KEY_HAPTIC_FEEDBACK] ?: true
    val websiteStyle = prefs[KEY_WEBSITE_STYLE] ?: false
    val appIcon = try {
        prefs[KEY_APP_ICON]?.let { AppIcon.valueOf(it) }?.takeUnless { it.hidden } ?: AppIcon.DEFAULT
    } catch (_: IllegalArgumentException) {
        AppIcon.DEFAULT
    }
    val bottomBarStyle = BottomBarStyle.fromName(prefs[KEY_BOTTOM_BAR_STYLE])

    return Appearance(
        themeMode = themeMode,
        colorSource = colorSource,
        pureBlack = pureBlack,
        accentSeed = accentSeed,
        sectionLayout = sectionLayout,
        textScale = textScale,
        sectionOrder = sectionOrder,
        showRandomPick = showRandomPick,
        starredOnly = starredOnly,
        showIndexes = showIndexes,
        hapticFeedback = hapticFeedback,
        websiteStyle = websiteStyle,
        appIcon = appIcon,
        bottomBarStyle = bottomBarStyle,
    )
}
internal fun DefaultSettingsRepository.readHomePrefs(prefs: Preferences): HomePrefs {
    val default = HomePrefs()
    val blocks = prefs[KEY_HOME_BLOCKS]?.let { HomeLayout.decode(it) }
        ?: HomeLayout.fromLegacy(
            prefs[KEY_HOME_PERSONAL_HOME] ?: false,
            prefs[KEY_SHOW_RANDOM_PICK] ?: true,
        )
    return HomePrefs(
        blocks = blocks,
        newBadges = (prefs[KEY_HOME_NEW_BADGE_DAYS] ?: 7) > 0,
        searchExact = prefs[KEY_HOME_SEARCH_EXACT] ?: default.searchExact,
        swipeToBookmark = prefs[KEY_HOME_SWIPE_TO_BOOKMARK] ?: default.swipeToBookmark,
        swapTapHold = prefs[KEY_HOME_SWAP_TAP_HOLD] ?: default.swapTapHold,
        searchTapTarget = prefs[KEY_HOME_SEARCH_TAP_TARGET]?.let { runCatching { SearchTapTarget.valueOf(it) }.getOrNull() } ?: default.searchTapTarget,
        swipeBetweenScreens = prefs[KEY_HOME_SWIPE_BETWEEN_SCREENS] ?: default.swipeBetweenScreens,
        websiteCategoryNames = prefs[KEY_HOME_WEBSITE_CATEGORY_NAMES] ?: default.websiteCategoryNames,
        showNsfwLink = prefs[KEY_HOME_SHOW_NSFW_LINK] ?: default.showNsfwLink,
    )
}
internal fun DefaultSettingsRepository.writeHomePrefs(prefs: MutablePreferences, p: HomePrefs) {
    prefs[KEY_HOME_BLOCKS] = p.blocks.encode()
    prefs[KEY_HOME_SEARCH_EXACT] = p.searchExact
    prefs[KEY_HOME_NEW_BADGE_DAYS] = if (p.newBadges) 7 else 0
    prefs[KEY_HOME_SWIPE_TO_BOOKMARK] = p.swipeToBookmark
    prefs[KEY_HOME_SWAP_TAP_HOLD] = p.swapTapHold
    prefs[KEY_HOME_SEARCH_TAP_TARGET] = p.searchTapTarget.name
    prefs[KEY_HOME_SWIPE_BETWEEN_SCREENS] = p.swipeBetweenScreens
    prefs[KEY_HOME_WEBSITE_CATEGORY_NAMES] = p.websiteCategoryNames
    prefs[KEY_HOME_SHOW_NSFW_LINK] = p.showNsfwLink
}
