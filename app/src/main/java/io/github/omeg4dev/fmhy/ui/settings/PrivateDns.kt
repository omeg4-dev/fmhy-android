package io.github.omeg4dev.fmhy.ui.settings

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings

sealed interface PrivateDnsState {
    data object Off : PrivateDnsState
    data object Automatic : PrivateDnsState
    data class Provider(val host: String) : PrivateDnsState
    data object Unknown : PrivateDnsState
}

fun privateDnsStateOf(mode: String?, specifier: String?): PrivateDnsState {
    // Android never writes private_dns_mode until the user opens the screen once; the system default is then
    // "opportunistic" (Automatic), so a missing or empty value means Automatic.
    return when (mode?.lowercase()?.ifEmpty { "opportunistic" } ?: "opportunistic") {
        "off" -> PrivateDnsState.Off
        "opportunistic" -> PrivateDnsState.Automatic
        "hostname" -> {
            val trimmed = specifier?.trim()
            if (!trimmed.isNullOrEmpty()) {
                PrivateDnsState.Provider(trimmed)
            } else {
                PrivateDnsState.Unknown
            }
        }
        else -> PrivateDnsState.Unknown
    }
}

fun privateDnsSummary(state: PrivateDnsState): String = when (state) {
    PrivateDnsState.Off -> "Off"
    PrivateDnsState.Automatic -> "Automatic"
    is PrivateDnsState.Provider -> state.host
    PrivateDnsState.Unknown -> "Open the system setting to see it"
}

fun privateDnsSupported(): Boolean = Build.VERSION.SDK_INT >= 28

fun readPrivateDnsState(context: Context): PrivateDnsState {
    if (Build.VERSION.SDK_INT < 28) {
        return PrivateDnsState.Unknown
    }
    return runCatching {
        val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode")
        val specifier = Settings.Global.getString(context.contentResolver, "private_dns_specifier")
        if (mode != null) {
            privateDnsStateOf(mode, specifier)
        } else {
            // Setting never written: the system default applies (Automatic); a strict provider shows on the link.
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val lp = activeNetwork?.let { cm.getLinkProperties(it) }
            val serverName = if (lp != null && lp.isPrivateDnsActive) lp.privateDnsServerName else null
            if (serverName != null) PrivateDnsState.Provider(serverName) else PrivateDnsState.Automatic
        }
    }.getOrDefault(PrivateDnsState.Unknown)
}

fun openPrivateDnsSettings(context: Context) {
    val intents = listOf(
        Intent("android.settings.PRIVATE_DNS_SETTINGS"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
            // Try next fallback
        }
    }
}
