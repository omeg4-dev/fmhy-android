package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.BlockingPrefs
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.user.SitePrefs
import io.github.omeg4dev.fmhy.data.user.UserStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SiteSettingsScreen(
    userStore: UserStore,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val changes by userStore.changes.collectAsStateWithLifecycle()
    var sites by remember { mutableStateOf<List<SitePrefs>>(emptyList()) }

    LaunchedEffect(changes) {
        sites = userStore.allSitePrefs()
    }

    val blockingPrefs by settingsRepository.blockingPrefs.collectAsStateWithLifecycle(initialValue = BlockingPrefs())
    val protectionPrefs by settingsRepository.protectionPrefs.collectAsStateWithLifecycle(initialValue = ProtectionPrefs())
    val browserPrefs by settingsRepository.browserPrefs.collectAsStateWithLifecycle(initialValue = BrowserPrefs())

    var editingSite by remember { mutableStateOf<SitePrefs?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Site settings") },
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
        if (sites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No site-specific settings yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(sites, key = { it.host }) { site ->
                    val overrides = remember(site) {
                        buildList {
                            if (site.adblock != null) add(if (site.adblock == false) "ads allowed" else "ads blocked")
                            if (site.popups != null) add(if (site.popups == false) "pop-ups allowed" else "pop-ups blocked")
                            if (site.javascript != null) add(if (site.javascript == false) "JS off" else "JS on")
                            if (site.desktop != null) add(if (site.desktop == true) "desktop UA" else "mobile UA")
                            if (site.dark != null) add(if (site.dark == false) "light theme" else "dark theme")
                        }
                    }

                    ListItem(
                        headlineContent = {
                            Text(site.host, style = MaterialTheme.typography.titleMedium)
                        },
                        supportingContent = if (overrides.isNotEmpty()) {
                            {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    overrides.forEach { badge ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text(
                                                text = badge,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        } else null,
                        modifier = Modifier.clickable {
                            editingSite = site
                        },
                    )
                }
            }
        }
    }

    if (editingSite != null) {
        val site = editingSite!!
        var adblock by remember(site) { mutableStateOf(site.adblock ?: blockingPrefs.adblock) }
        var popups by remember(site) { mutableStateOf(site.popups ?: protectionPrefs.popupBlocker) }
        var javascript by remember(site) { mutableStateOf(site.javascript ?: browserPrefs.javascript) }
        var desktop by remember(site) { mutableStateOf(site.desktop ?: browserPrefs.desktopByDefault) }
        var dark by remember(site) { mutableStateOf(site.dark ?: browserPrefs.forceDark) }

        AlertDialog(
            onDismissRequest = { editingSite = null },
            title = { Text(site.host) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SwitchRow(
                        title = "Ad blocking",
                        checked = adblock,
                        onChange = { adblock = it },
                    )
                    SwitchRow(
                        title = "Block pop-ups",
                        checked = popups,
                        onChange = { popups = it },
                    )
                    SwitchRow(
                        title = "JavaScript",
                        checked = javascript,
                        onChange = { javascript = it },
                    )
                    SwitchRow(
                        title = "Desktop site",
                        checked = desktop,
                        onChange = { desktop = it },
                    )
                    SwitchRow(
                        title = "Dark theme",
                        checked = dark,
                        onChange = { dark = it },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated = SitePrefs(
                            host = site.host,
                            adblock = adblock,
                            popups = popups,
                            javascript = javascript,
                            desktop = desktop,
                            dark = dark,
                            updatedAt = System.currentTimeMillis(),
                        )
                        scope.launch {
                            userStore.setSitePrefs(updated)
                        }
                        editingSite = null
                    }
                ) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            userStore.clearSitePrefs(site.host)
                        }
                        editingSite = null
                    }
                ) {
                    Text("Reset to default")
                }
            },
        )
    }
}
