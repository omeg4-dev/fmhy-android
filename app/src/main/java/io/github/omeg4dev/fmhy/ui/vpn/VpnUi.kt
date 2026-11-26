package io.github.omeg4dev.fmhy.ui.vpn

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.work.IpInfoRepository
import io.github.omeg4dev.fmhy.work.IpResult
import io.github.omeg4dev.fmhy.work.VpnState

/** Tor's purple: the indicator colour while the Orbot proxy is selected. */
fun orbotColor(isDark: Boolean): Color = if (isDark) Color(0xFFCFA8FF) else Color(0xFF7D4698)

// In the functions below, orbot = the Orbot proxy is selected. It overrides the VPN state.

@Composable
fun vpnColor(
    state: VpnState,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
    orbot: Boolean = false,
): Color = when {
    orbot -> orbotColor(isDark)
    state == VpnState.ACTIVE -> if (isDark) Color(0xFF9BE0AC) else Color(0xFF1E8E3E)
    else -> if (isDark) Color(0xFFFFD27A) else Color(0xFFB26A00)
}

fun vpnIcon(state: VpnState, orbot: Boolean = false): ImageVector = when {
    orbot || state == VpnState.ACTIVE -> Icons.Filled.Lock
    else -> Icons.Filled.LockOpen
}

fun vpnStateText(state: VpnState, orbot: Boolean = false): String = when {
    orbot -> "Orbot proxy on"
    state == VpnState.ACTIVE -> "VPN active"
    else -> "No VPN detected"
}

fun vpnStateHint(state: VpnState, orbot: Boolean = false): String = when {
    orbot -> "Your traffic goes through Orbot (Tor). This cannot prove your real IP is hidden."
    state == VpnState.ACTIVE -> "Your traffic goes through a VPN. This cannot prove your real IP is hidden."
    else -> "Sites see your own IP address."
}

@Composable
fun connectionColor(
    level: ConnectionLevel,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
): Color = when (level) {
    ConnectionLevel.PROTECTED -> vpnColor(VpnState.ACTIVE, isDark)
    ConnectionLevel.ORBOT -> orbotColor(isDark)
    ConnectionLevel.ENCRYPTED -> vpnColor(VpnState.INACTIVE, isDark)
    ConnectionLevel.INSECURE -> MaterialTheme.colorScheme.error
}

/**
 * Ring around the site icon in the address pill. The colour is the connection level; tapping it
 * opens the connection details. A 2 dp gap separates the ring from the icon, like a story ring.
 */
@Composable
fun ConnectionRing(
    level: ConnectionLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val color = connectionColor(level)
    val label = ConnectionSafety.title(level)
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = "Connection details", onClick = onClick)
            .semantics { contentDescription = "Connection: $label" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(20.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

fun openVpnSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Do nothing
        }
    }
}

sealed interface IpLine {
    data object Hidden : IpLine
    data object Loading : IpLine
    data class Text(val text: String) : IpLine
}

fun ipLineOf(result: IpResult?): IpLine = when (result) {
    null -> IpLine.Loading
    IpResult.Disabled -> IpLine.Hidden
    IpResult.Unavailable -> IpLine.Text("Unavailable")
    is IpResult.Value -> IpLine.Text("${result.info.ip} · ${result.info.country}")
}

@Composable
fun rememberIpLine(
    repo: IpInfoRepository,
    enabled: Boolean,
    vpn: VpnState,
    active: Boolean,
): IpLine {
    val ipResult by produceState<IpResult?>(
        initialValue = null,
        key1 = enabled,
        key2 = vpn,
        key3 = active,
    ) {
        if (!enabled || !active) {
            value = IpResult.Disabled
        } else {
            value = null
            value = repo.get(enabled)
        }
    }
    if (!enabled || !active) return IpLine.Hidden
    return remember(ipResult) { ipLineOf(ipResult) }
}
