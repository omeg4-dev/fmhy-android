package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.ProxyMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val protectionPrefs by viewModel.protectionPrefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var state by remember { mutableStateOf(readPrivateDnsState(context)) }
    var orbotInstalled by remember { mutableStateOf(isOrbotInstalled(context)) }
    val webViewProxySupported = remember { isWebViewProxySupported() }
    var showProxyDialog by remember { mutableStateOf(false) }
    var showOrbotMissingDialog by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state = readPrivateDnsState(context)
        orbotInstalled = isOrbotInstalled(context)
    }

    val eShowVpnState = SettingsCatalog.protectionShowVpnState
    val eBlockWithoutVpn = SettingsCatalog.protectionBlockWithoutVpn
    val eShowVpnHomeIcon = SettingsCatalog.protectionShowVpnHomeIcon
    val eShowIpCountry = SettingsCatalog.protectionShowIpCountry
    val eProxy = SettingsCatalog.protectionProxy
    val ePrivateDns = SettingsCatalog.protectionPrivateDns

    val vpnIds = listOf(
        eShowVpnState.id,
        eBlockWithoutVpn.id,
        eShowVpnHomeIcon.id,
        eShowIpCountry.id,
    )
    val proxyIds = listOf(eProxy.id)
    val dnsIds = if (privateDnsSupported()) listOf(ePrivateDns.id) else emptyList()
    val sectionSettingIds = remember {
        if (privateDnsSupported()) {
            listOf(vpnIds, proxyIds, dnsIds)
        } else {
            listOf(vpnIds, proxyIds)
        }
    }
    val listState = rememberFocusedListState(sectionSettingIds)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VPN & proxy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsSection(title = "VPN") {
                    SwitchRow(
                        title = eShowVpnState.title,
                        summary = eShowVpnState.description,
                        checked = protectionPrefs.showVpnState,
                        settingId = eShowVpnState.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(showVpnState = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eBlockWithoutVpn.title,
                        summary = eBlockWithoutVpn.description,
                        checked = protectionPrefs.blockWithoutVpn,
                        enabled = protectionPrefs.showVpnState,
                        settingId = eBlockWithoutVpn.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(blockWithoutVpn = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eShowVpnHomeIcon.title,
                        summary = eShowVpnHomeIcon.description,
                        checked = protectionPrefs.showVpnHomeIcon,
                        settingId = eShowVpnHomeIcon.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(showVpnHomeIcon = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eShowIpCountry.title,
                        summary = eShowIpCountry.description,
                        checked = protectionPrefs.showIpCountry,
                        settingId = eShowIpCountry.id,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(showIpCountry = checked) }
                        },
                    )
                }
            }

            item {
                SettingsSection(title = "Proxy") {
                    NavRow(
                        title = eProxy.title,
                        summary = proxyRowSummary(
                            prefs = protectionPrefs,
                            webViewSupported = webViewProxySupported,
                            orbotInstalled = orbotInstalled,
                        ),
                        enabled = proxyRowEnabled(
                            prefs = protectionPrefs,
                            webViewSupported = webViewProxySupported,
                        ),
                        settingId = eProxy.id,
                        onClick = { showProxyDialog = true },
                    )
                }
            }

            if (privateDnsSupported()) {
                item {
                    SettingsSection(title = "DNS") {
                        NavRow(
                            title = ePrivateDns.title,
                            summary = privateDnsSummary(state),
                            settingId = ePrivateDns.id,
                            onClick = { openPrivateDnsSettings(context) },
                        )
                    }
                }
            }

            extraRows()
        }
    }

    if (showProxyDialog) {
        ProxySettingsDialog(
            initialPrefs = protectionPrefs,
            webViewSupported = webViewProxySupported,
            onDismiss = { showProxyDialog = false },
            onConfirm = { mode, type, host, port, allowJavaScript ->
                showProxyDialog = false
                if (mode == ProxyMode.ORBOT && !isOrbotInstalled(context)) {
                    orbotInstalled = false
                    showOrbotMissingDialog = true
                } else {
                    viewModel.updateProtection {
                        when (mode) {
                            ProxyMode.OFF -> it.copy(proxyMode = mode)
                            ProxyMode.ORBOT -> it.copy(
                                proxyMode = mode,
                                proxyAllowJavaScript = allowJavaScript,
                            )
                            ProxyMode.CUSTOM -> it.copy(
                                proxyMode = mode,
                                proxyType = type,
                                proxyHost = host,
                                proxyPort = port,
                                proxyAllowJavaScript = allowJavaScript,
                            )
                        }
                    }
                }
            },
        )
    }

    if (showOrbotMissingDialog) {
        OrbotNotInstalledDialog(
            onGetOrbot = {
                showOrbotMissingDialog = false
                openGetOrbot(context)
            },
            onDismiss = { showOrbotMissingDialog = false },
        )
    }
}
