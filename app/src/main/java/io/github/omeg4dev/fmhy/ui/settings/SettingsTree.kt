package io.github.omeg4dev.fmhy.ui.settings

import io.github.omeg4dev.fmhy.SettingsPage

sealed interface HubItem {
    data class Page(val page: SettingsPage) : HubItem
    data object Following : HubItem
    data object WhatsNew : HubItem
}

data class HubGroup(
    val title: String,
    val items: List<HubItem>,
)

object SettingsTree {
    val groups: List<HubGroup> = listOf(
        HubGroup(
            title = "Look & feel",
            items = listOf(
                HubItem.Page(SettingsPage.APPEARANCE),
                HubItem.Page(SettingsPage.HOME),
                HubItem.Page(SettingsPage.LISTS),
            ),
        ),
        HubGroup(
            title = "Browsing",
            items = listOf(
                HubItem.Page(SettingsPage.BROWSER),
                HubItem.Page(SettingsPage.VIDEO),
                HubItem.Page(SettingsPage.DOWNLOADS),
            ),
        ),
        HubGroup(
            title = "Safety & privacy",
            items = listOf(
                HubItem.Page(SettingsPage.AD_BLOCKING),
                HubItem.Page(SettingsPage.PROTECTION),
                HubItem.Page(SettingsPage.CONNECTION),
                HubItem.Page(SettingsPage.PRIVACY),
            ),
        ),
        HubGroup(
            title = "Updates",
            items = listOf(
                HubItem.Following,
                HubItem.Page(SettingsPage.NOTIFICATIONS),
                HubItem.WhatsNew,
                HubItem.Page(SettingsPage.APP_UPDATES),
            ),
        ),
        HubGroup(
            title = "Your data",
            items = listOf(
                HubItem.Page(SettingsPage.DATA),
                HubItem.Page(SettingsPage.FMHYX_ACCOUNT),
            ),
        ),
        HubGroup(
            title = "Help & about",
            items = listOf(
                HubItem.Page(SettingsPage.REPORT),
                HubItem.Page(SettingsPage.ABOUT),
            ),
        ),
    )
}

fun SettingsPage.heading(): String =
    SettingsTree.groups.first { group ->
        group.items.any { it is HubItem.Page && it.page == this }
    }.title

fun SettingsPage.label(): String = when (this) {
    SettingsPage.APPEARANCE -> "Appearance"
    SettingsPage.HOME -> "Home"
    SettingsPage.LISTS -> "Lists & gestures"
    SettingsPage.BROWSER -> "Browser"
    SettingsPage.VIDEO -> "Video"
    SettingsPage.DOWNLOADS -> "Downloads"
    SettingsPage.AD_BLOCKING -> "Ad blocking"
    SettingsPage.PROTECTION -> "Protection"
    SettingsPage.CONNECTION -> "VPN & proxy"
    SettingsPage.PRIVACY -> "Privacy & history"
    SettingsPage.NOTIFICATIONS -> "Notifications"
    SettingsPage.APP_UPDATES -> "App updates"
    SettingsPage.DATA -> "Data & backup"
    SettingsPage.FMHYX_ACCOUNT -> "FMHY X account"
    SettingsPage.REPORT -> "Report a problem"
    SettingsPage.ABOUT -> "About"
}
