package io.github.omeg4dev.fmhy.shortcuts

import io.github.omeg4dev.fmhy.data.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconSwitcherTest {
    @Test fun everyIconMapsToItsOwnAlias() {
        assertEquals(".LauncherDefault", AppIconSwitcher.aliasFor(AppIcon.DEFAULT))
        assertEquals(".LauncherUwu", AppIconSwitcher.aliasFor(AppIcon.UWU))
        assertEquals(".LauncherMono", AppIconSwitcher.aliasFor(AppIcon.MONOCHROME))
        assertEquals(AppIcon.entries.size, AppIcon.entries.map { AppIconSwitcher.aliasFor(it) }.toSet().size)
    }

    @Test fun exactlyOneAliasIsEnabled() {
        for (icon in AppIcon.entries) {
            val states = AppIconSwitcher.desiredStates(icon)
            assertEquals(AppIconSwitcher.ALL_ALIASES.toSet(), states.keys)
            assertEquals(listOf(AppIconSwitcher.aliasFor(icon)), states.filterValues { it }.keys.toList())
        }
    }
}
