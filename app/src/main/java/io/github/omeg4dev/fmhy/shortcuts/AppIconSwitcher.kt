package io.github.omeg4dev.fmhy.shortcuts

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.github.omeg4dev.fmhy.data.AppIcon

/**
 * Launcher icon variants are `activity-alias` entries in the manifest; exactly one is enabled.
 * Names are relative to the package (`.LauncherDefault`) as in the manifest.
 */
object AppIconSwitcher {
    const val ALIAS_DEFAULT = ".LauncherDefault"
    const val ALIAS_UWU = ".LauncherUwu"
    const val ALIAS_MONO = ".LauncherMono"
    const val ALIAS_LIGHT = ".LauncherLight"
    const val ALIAS_BLUE = ".LauncherBlue"
    const val ALIAS_GREEN = ".LauncherGreen"
    const val ALIAS_RED = ".LauncherRed"
    const val ALIAS_BLACK = ".LauncherBlack"

    val ALIAS_BY_ICON: Map<AppIcon, String> = mapOf(
        AppIcon.DEFAULT to ALIAS_DEFAULT,
        AppIcon.UWU to ALIAS_UWU,
        AppIcon.MONOCHROME to ALIAS_MONO,
        AppIcon.LIGHT to ALIAS_LIGHT,
        AppIcon.BLUE to ALIAS_BLUE,
        AppIcon.GREEN to ALIAS_GREEN,
        AppIcon.RED to ALIAS_RED,
        AppIcon.BLACK to ALIAS_BLACK,
    )

    val ALL_ALIASES: List<String> = ALIAS_BY_ICON.values.toList()

    fun aliasFor(icon: AppIcon): String =
        ALIAS_BY_ICON[icon] ?: error("No alias for $icon")

    /** alias -> should be enabled; exactly one entry is true. */
    fun desiredStates(icon: AppIcon): Map<String, Boolean> =
        ALL_ALIASES.associateWith { it == aliasFor(icon) }

    fun apply(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        // Enable the wanted alias first so the app never has zero launcher entries.
        val states = desiredStates(icon).entries.sortedByDescending { it.value }
        for ((alias, enabled) in states) {
            val component = ComponentName(context.packageName, context.packageName + alias)
            val wanted = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            if (pm.getComponentEnabledSetting(component) != wanted) {
                pm.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
