package io.github.omeg4dev.fmhy.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.browser.ExternalOpenResult
import io.github.omeg4dev.fmhy.browser.openInExternalBrowser
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.ProxyMode
import io.github.omeg4dev.fmhy.data.ProxyType
import io.github.omeg4dev.fmhy.net.AndroidWebProxyController
import io.github.omeg4dev.fmhy.net.ProxyEndpoint
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics

private const val ORBOT_PACKAGE = "org.torproject.android"
private const val ORBOT_MARKET_URL = "market://details?id=$ORBOT_PACKAGE"
private const val ORBOT_WEB_URL = "https://orbot.app"
private const val UNSUPPORTED_WEBVIEW_TEXT = "This phone's WebView cannot use a proxy"

internal fun isWebViewProxySupported(): Boolean = try {
    AndroidWebProxyController.supported
} catch (_: Throwable) {
    false
}

internal fun isOrbotInstalled(context: Context): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(ORBOT_PACKAGE, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(ORBOT_PACKAGE, 0)
    }
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
} catch (_: Throwable) {
    false
}

internal fun proxyRowEnabled(prefs: ProtectionPrefs, webViewSupported: Boolean): Boolean =
    webViewSupported || prefs.proxyMode != ProxyMode.OFF

internal fun proxyRowSummary(
    prefs: ProtectionPrefs,
    webViewSupported: Boolean,
    orbotInstalled: Boolean,
): String {
    if (!webViewSupported) return UNSUPPORTED_WEBVIEW_TEXT
    val base = when (prefs.proxyMode) {
        ProxyMode.OFF -> return "Off"
        ProxyMode.ORBOT -> if (orbotInstalled) {
            "Orbot (Tor) · ${ProxyEndpoint.ORBOT_HOST}:${ProxyEndpoint.ORBOT_PORT}"
        } else {
            "Orbot is not installed"
        }
        ProxyMode.CUSTOM -> {
            val endpoint = ProxyEndpoint.resolve(prefs)
            if (endpoint != null && !endpoint.invalid) {
                endpoint.summary
            } else {
                "${prefs.proxyType.name} · invalid (blocked)"
            }
        }
    }
    val jsSuffix = if (prefs.proxyAllowJavaScript) " · JavaScript on" else " · JavaScript off"
    return base + jsSuffix
}

internal fun proxyDialogHelperText(selectedMode: ProxyMode, allowJavaScript: Boolean): String {
    val middle = if (selectedMode != ProxyMode.OFF && allowJavaScript) {
        "Web calls are blocked where possible."
    } else {
        "JavaScript is off, so some sites will not work."
    }
    return "All tabs and app requests use the proxy. $middle Links opened in other apps do not use it."
}

internal fun openGetOrbot(context: Context) {
    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ORBOT_MARKET_URL)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val openedMarket = try {
        context.startActivity(marketIntent)
        true
    } catch (_: Throwable) {
        false
    }
    if (openedMarket) return

    if (openInExternalBrowser(context, ORBOT_WEB_URL) == ExternalOpenResult.NONE_FOUND) {
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ORBOT_WEB_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (_: Throwable) {}
    }
}

@Composable
internal fun ProxySettingsDialog(
    initialPrefs: ProtectionPrefs,
    webViewSupported: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ProxyMode, ProxyType, String, Int, Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    var selectedMode by remember(initialPrefs, webViewSupported) {
        mutableStateOf(if (webViewSupported) initialPrefs.proxyMode else ProxyMode.OFF)
    }
    var selectedType by remember(initialPrefs) { mutableStateOf(initialPrefs.proxyType) }
    var hostInput by remember(initialPrefs) { mutableStateOf(initialPrefs.proxyHost) }
    var portInput by remember(initialPrefs) {
        mutableStateOf(if (initialPrefs.proxyPort > 0) initialPrefs.proxyPort.toString() else "")
    }
    var allowJavaScript by remember(initialPrefs) { mutableStateOf(initialPrefs.proxyAllowJavaScript) }
    var hostTouched by remember { mutableStateOf(initialPrefs.proxyHost.isNotEmpty()) }
    var portTouched by remember { mutableStateOf(initialPrefs.proxyPort > 0) }

    val normalizedHost = ProxyEndpoint.normalizeHost(hostInput)
    val parsedPort = portInput.trim().toIntOrNull()?.takeIf { it in 1..65535 }
    val hostError = if (hostTouched || hostInput.isNotEmpty()) ProxyEndpoint.hostError(hostInput) else null
    val portError = if (portTouched || portInput.isNotEmpty()) ProxyEndpoint.portError(portInput) else null
    val customValid = normalizedHost != null && parsedPort != null

    val confirmEnabled = when (selectedMode) {
        ProxyMode.OFF -> true
        ProxyMode.ORBOT -> webViewSupported
        ProxyMode.CUSTOM -> webViewSupported && customValid
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Proxy") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!webViewSupported) {
                    Text(
                        text = UNSUPPORTED_WEBVIEW_TEXT,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                ProxyModeOptionRow(
                    title = "Off",
                    selected = selectedMode == ProxyMode.OFF,
                    enabled = true,
                    onClick = {
                        haptics.tick()
                        selectedMode = ProxyMode.OFF
                    },
                )
                ProxyModeOptionRow(
                    title = "Orbot (Tor)",
                    subtitle = "${ProxyEndpoint.ORBOT_HOST}:${ProxyEndpoint.ORBOT_PORT}",
                    selected = selectedMode == ProxyMode.ORBOT,
                    enabled = webViewSupported,
                    onClick = {
                        haptics.tick()
                        selectedMode = ProxyMode.ORBOT
                    },
                )
                ProxyModeOptionRow(
                    title = "Custom",
                    selected = selectedMode == ProxyMode.CUSTOM,
                    enabled = webViewSupported,
                    onClick = {
                        haptics.tick()
                        selectedMode = ProxyMode.CUSTOM
                    },
                )

                if (selectedMode == ProxyMode.CUSTOM && webViewSupported) {
                    val types = ProxyType.entries
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        types.forEachIndexed { index, type ->
                            SegmentedButton(
                                selected = selectedType == type,
                                onClick = {
                                    haptics.tick()
                                    selectedType = type
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                                label = { Text(type.name) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = {
                            hostInput = it
                            hostTouched = true
                        },
                        label = { Text("Host") },
                        placeholder = { Text("127.0.0.1 or proxy.example") },
                        singleLine = true,
                        isError = hostError != null,
                        supportingText = hostError?.let { msg -> { Text(msg) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = {
                            portInput = it
                            portTouched = true
                        },
                        label = { Text("Port") },
                        placeholder = { Text("1080") },
                        singleLine = true,
                        isError = portError != null,
                        supportingText = portError?.let { msg -> { Text(msg) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if ((selectedMode == ProxyMode.ORBOT || selectedMode == ProxyMode.CUSTOM) && webViewSupported) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .toggleable(
                                    value = allowJavaScript,
                                    role = Role.Switch,
                                    onValueChange = {
                                        haptics.tick()
                                        allowJavaScript = it
                                    },
                                )
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Allow JavaScript",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = allowJavaScript,
                                onCheckedChange = null,
                            )
                        }
                        Text(
                            text = "Sites can then learn your real IP address (WebRTC). The built-in block is not airtight.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (allowJavaScript) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                Text(
                    text = proxyDialogHelperText(selectedMode, allowJavaScript),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmEnabled,
                onClick = {
                    val finalHost = normalizedHost ?: ""
                    val finalPort = parsedPort ?: 0
                    onConfirm(selectedMode, selectedType, finalHost, finalPort, allowJavaScript)
                },
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ProxyModeOptionRow(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .then(if (!enabled) Modifier.alpha(0.38f) else Modifier)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun OrbotNotInstalledDialog(
    onGetOrbot: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Orbot is not installed") },
        text = { Text("Install Orbot to send traffic through Tor.") },
        confirmButton = {
            TextButton(onClick = onGetOrbot) {
                Text("Get Orbot")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
