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

    val ALL_ALIASES = listOf(ALIAS_DEFAULT, ALIAS_UWU, ALIAS_MONO)

    fun aliasFor(icon: AppIcon): String = when (icon) {
        AppIcon.DEFAULT -> ALIAS_DEFAULT
        AppIcon.UWU -> ALIAS_UWU
        AppIcon.MONOCHROME -> ALIAS_MONO
    }

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
