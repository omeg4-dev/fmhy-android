package io.github.omeg4dev.fmhy.browser

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.browser.download.ScanResult
import io.github.omeg4dev.fmhy.browser.download.VtMark
import io.github.omeg4dev.fmhy.ui.vpn.ConnectionLevel
import io.github.omeg4dev.fmhy.ui.vpn.ConnectionSafety
import io.github.omeg4dev.fmhy.ui.vpn.IpLine
import io.github.omeg4dev.fmhy.ui.vpn.connectionColor
import io.github.omeg4dev.fmhy.ui.vpn.vpnColor
import io.github.omeg4dev.fmhy.ui.vpn.vpnIcon
import io.github.omeg4dev.fmhy.ui.vpn.vpnStateHint
import io.github.omeg4dev.fmhy.ui.vpn.vpnStateText
import io.github.omeg4dev.fmhy.work.VpnState

/** What the site menu needs for its VirusTotal block. */
class VtSheetState(
    val isPrivate: Boolean,
    val mark: VtMark?,
    val ready: Boolean,
    val onCheck: suspend () -> ScanResult?,
    val onSetup: () -> Unit,
)

/** Details behind the ring around the site icon: HTTPS or not, VPN state, optionally IP and country. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSheet(
    url: String,
    level: ConnectionLevel?,
    vpnState: VpnState?,
    ipLine: IpLine,
    onOpenVpnSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    vt: VtSheetState? = null,
) {
    val host = try { Uri.parse(url).host.orEmpty() } catch (_: Exception) { "" }
    val https = ConnectionSafety.isHttps(url)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = host.ifEmpty { "This page" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (level != null && vpnState != null) {
                val tone = connectionColor(level)
                Spacer(Modifier.height(12.dp))

                // Verdict band, same shape as the FMHY status popup
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(tone.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = ConnectionSafety.title(level),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tone,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = ConnectionSafety.detail(level),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))

                DetailRow(
                    icon = if (https) Icons.Default.Https else Icons.Default.NoEncryption,
                    tint = if (https) vpnColor(VpnState.ACTIVE) else MaterialTheme.colorScheme.error,
                    title = "Connection",
                    value = ConnectionSafety.connectionText(url),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DetailRow(
                    icon = vpnIcon(vpnState),
                    tint = vpnColor(vpnState),
                    title = "VPN",
                    value = vpnStateText(vpnState),
                    hint = vpnStateHint(vpnState),
                )
                when (ipLine) {
                    IpLine.Hidden -> Unit
                    IpLine.Loading -> IpRow("Checking IP…")
                    is IpLine.Text -> IpRow(ipLine.text)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenVpnSettings) { Text("Open VPN settings") }
            }
            if (vt != null) {
                if (level != null) HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) else Spacer(Modifier.height(12.dp))
                VtCheckSection(
                    isPrivate = vt.isPrivate,
                    vtMark = vt.mark,
                    vtReady = vt.ready,
                    onCheckVt = vt.onCheck,
                    onSetupVt = vt.onSetup,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    value: String,
    hint: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IpRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 40.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
