package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.browser.formatRelativeTime
import io.github.omeg4dev.fmhy.data.adblock.FilterList
import io.github.omeg4dev.fmhy.data.adblock.FilterListCatalog
import io.github.omeg4dev.fmhy.data.adblock.ListState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockingPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val blockingPrefs by viewModel.blockingPrefs.collectAsStateWithLifecycle()
    val protectionPrefs by viewModel.protectionPrefs.collectAsStateWithLifecycle()
    val adblockStatus by viewModel.adblockStatus.collectAsStateWithLifecycle()
    val listStates by viewModel.listStates.collectAsStateWithLifecycle()
    val master = blockingPrefs.adblock

    var isAdvancedExpanded by remember {
        mutableStateOf(
            blockingPrefs.enabledLists != FilterListCatalog.balancedIds &&
                blockingPrefs.enabledLists != FilterListCatalog.strictIds
        )
    }

    var showCustomRulesDialog by remember { mutableStateOf(false) }
    var showAllowedSitesDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ad blocking & protection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1. Ad blocking section
            item {
                SettingsSection(title = "Ad blocking") {
                    SwitchRow(
                        title = "Block ads and trackers",
                        checked = blockingPrefs.adblock,
                        onChange = { checked ->
                            viewModel.updateBlocking { it.copy(adblock = checked) }
                        },
                    )

                    if (master) {
                        SettingsDivider()
                        OptionCardRow(
                            options = listOf(
                                OptionCardItem(
                                    value = "balanced",
                                    title = "Balanced",
                                    subtitle = "Ads, trackers and malware lists — recommended",
                                    isRecommended = true,
                                ),
                                OptionCardItem(
                                    value = "strict",
                                    title = "Strict",
                                    subtitle = "Adds cookie notices and annoyances",
                                ),
                                OptionCardItem(
                                    value = "custom",
                                    title = "Custom",
                                    subtitle = "Filter lists, your own rules, and advanced controls",
                                ),
                            ),
                            selected = when {
                                blockingPrefs.enabledLists == FilterListCatalog.balancedIds -> "balanced"
                                blockingPrefs.enabledLists == FilterListCatalog.strictIds -> "strict"
                                else -> "custom"
                            },
                            onSelect = { option ->
                                when (option) {
                                    "balanced" -> viewModel.updateBlocking { it.copy(enabledLists = FilterListCatalog.balancedIds) }
                                    "strict" -> viewModel.updateBlocking { it.copy(enabledLists = FilterListCatalog.strictIds) }
                                    "custom" -> isAdvancedExpanded = true
                                }
                            },
                        )
                    }
                }
            }

            // 2. Protection section
            item {
                SettingsSection(title = "Protection") {
                    SwitchRow(
                        title = "Warn before unsafe sites",
                        checked = protectionPrefs.unsafeWarning,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(unsafeWarning = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Warn before potentially unsafe sites",
                        checked = protectionPrefs.potentiallyUnsafeWarning,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(potentiallyUnsafeWarning = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Block pop-ups",
                        checked = protectionPrefs.popupBlocker,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(popupBlocker = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Block redirects without a tap",
                        checked = protectionPrefs.redirectGuard,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(redirectGuard = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Block links that open apps",
                        summary = "Ask before a page opens another app",
                        checked = protectionPrefs.blockAppLinks,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(blockAppLinks = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Ask before downloads",
                        checked = protectionPrefs.askBeforeDownload,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(askBeforeDownload = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Warn about APK and EXE downloads",
                        checked = protectionPrefs.warnRiskyDownloads,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(warnRiskyDownloads = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Google Safe Browsing",
                        checked = protectionPrefs.safeBrowsing,
                        onChange = { checked ->
                            viewModel.updateProtection { it.copy(safeBrowsing = checked) }
                        },
                    )
                }
            }

            // 3. Expandable Advanced Section
            item {
                SettingsSection {
                    ExpandableSection(
                        title = "Advanced",
                        summary = "Filter lists, your own rules, list status",
                        expanded = isAdvancedExpanded,
                        onExpandedChange = { isAdvancedExpanded = it },
                    ) {
                        SwitchRow(
                            title = "Show blocked count",
                            checked = blockingPrefs.showBlockedCount,
                            enabled = master,
                            onChange = { checked ->
                                viewModel.updateBlocking { it.copy(showBlockedCount = checked) }
                            },
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = "Hide page elements",
                            checked = blockingPrefs.elementHiding,
                            enabled = master,
                            onChange = { checked ->
                                viewModel.updateBlocking { it.copy(elementHiding = checked) }
                            },
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = "Fix broken pages",
                            summary = "Small page fixes from uBlock Origin",
                            checked = blockingPrefs.scriptlets,
                            enabled = master,
                            onChange = { checked ->
                                viewModel.updateBlocking { it.copy(scriptlets = checked) }
                            },
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = "Update lists automatically",
                            checked = blockingPrefs.autoUpdateLists,
                            enabled = master,
                            onChange = { checked ->
                                viewModel.updateBlocking { it.copy(autoUpdateLists = checked) }
                            },
                        )
                        SettingsDivider()

                        val groupTitles = mapOf(
                            FilterList.Group.UBLOCK to "uBlock Origin",
                            FilterList.Group.ADS to "Ads",
                            FilterList.Group.PRIVACY to "Privacy",
                            FilterList.Group.ANNOYANCES to "Annoyances",
                            FilterList.Group.FMHY to "FMHY",
                        )

                        FilterList.Group.entries.forEach { group ->
                            val listsInGroup = FilterListCatalog.all.filter { it.group == group }
                            if (listsInGroup.isNotEmpty()) {
                                SettingsGroupHeader(text = groupTitles[group] ?: group.name)
                                listsInGroup.forEachIndexed { idx, filterList ->
                                    if (idx > 0) SettingsDivider()
                                    SwitchRow(
                                        title = filterList.title,
                                        checked = blockingPrefs.enabledLists.contains(filterList.id),
                                        enabled = master,
                                        onChange = { checked ->
                                            viewModel.updateBlocking { p ->
                                                val updatedLists = if (checked) {
                                                    p.enabledLists + filterList.id
                                                } else {
                                                    p.enabledLists - filterList.id
                                                }
                                                p.copy(enabledLists = updatedLists)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        SettingsDivider()

                        val ruleCount = blockingPrefs.customRules.lines().count { it.isNotBlank() }
                        NavRow(
                            title = "Custom rules",
                            summary = "$ruleCount ${if (ruleCount == 1) "rule" else "rules"}",
                            enabled = master,
                            onClick = { showCustomRulesDialog = true },
                        )
                        SettingsDivider()

                        val siteCount = blockingPrefs.allowlist.size
                        NavRow(
                            title = "Allowed sites",
                            summary = "$siteCount ${if (siteCount == 1) "site" else "sites"}",
                            enabled = master,
                            onClick = { showAllowedSitesDialog = true },
                        )
                        SettingsDivider()

                        SettingsGroupHeader(text = "Status")
                        InfoRow(
                            title = "Rules active",
                            value = "${adblockStatus.ruleCount}",
                        )
                        SettingsDivider()
                        InfoRow(
                            title = "Last updated",
                            value = formatRelativeTime(adblockStatus.lastUpdated),
                        )

                        val updateButtonText = if (adblockStatus.progress != null && adblockStatus.updating) {
                            val pct = (adblockStatus.progress!! * 100).roundToInt()
                            "Updating lists… ($pct%)"
                        } else if (adblockStatus.updating) {
                            "Updating lists…"
                        } else {
                            "Update lists now"
                        }
                        ButtonRow(
                            text = updateButtonText,
                            loading = adblockStatus.updating,
                            enabled = !adblockStatus.updating,
                            onClick = { viewModel.updateAdblockNow() },
                        )

                        if (adblockStatus.error != null) {
                            Text(
                                text = "Update error: ${adblockStatus.error}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }

                        SettingsGroupHeader(text = "Enabled list status")
                        blockingPrefs.enabledLists.forEachIndexed { idx, listId ->
                            if (idx > 0) SettingsDivider()
                            val catalogList = FilterListCatalog.byId(listId)
                            val title = catalogList?.title ?: listId
                            val state = listStates[listId]
                            val desc = when {
                                state?.error != null -> "Error: ${state.error}"
                                state != null -> "${state.ruleLines} rules · updated ${formatRelativeTime(state.fetchedAt)}"
                                else -> "Not downloaded yet"
                            }
                            InfoRow(
                                title = title,
                                value = desc,
                            )
                        }
                    }
                }
            }

            extraRows()
        }
    }

    if (showCustomRulesDialog) {
        CustomRulesDialog(
            initialRules = blockingPrefs.customRules,
            onDismiss = { showCustomRulesDialog = false },
            onSave = { rules ->
                viewModel.updateBlocking { it.copy(customRules = rules) }
                showCustomRulesDialog = false
            },
        )
    }

    if (showAllowedSitesDialog) {
        AllowedSitesDialog(
            allowlist = blockingPrefs.allowlist,
            onDismiss = { showAllowedSitesDialog = false },
            onAdd = { site ->
                viewModel.updateBlocking { it.copy(allowlist = it.allowlist + site) }
            },
            onRemove = { site ->
                viewModel.updateBlocking { it.copy(allowlist = it.allowlist - site) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRulesDialog(
    initialRules: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialRules) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Custom rules") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        TextButton(onClick = { onSave(text) }) {
                            Text("Save")
                        }
                    }
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("One rule per line, uBO syntax") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun AllowedSitesDialog(
    allowlist: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var newSiteInput by remember { mutableStateOf("") }

    fun normalizeHost(input: String): String {
        var h = input.trim().lowercase()
        if (h.startsWith("http://")) h = h.removePrefix("http://")
        if (h.startsWith("https://")) h = h.removePrefix("https://")
        h = h.substringBefore('/')
        h = h.substringBefore(':')
        if (h.startsWith("www.")) h = h.removePrefix("www.")
        return h.trim()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allowed sites") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newSiteInput,
                        onValueChange = { newSiteInput = it },
                        label = { Text("Add site") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val normalized = normalizeHost(newSiteInput)
                                if (normalized.isNotEmpty()) {
                                    onAdd(normalized)
                                    newSiteInput = ""
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val normalized = normalizeHost(newSiteInput)
                            if (normalized.isNotEmpty()) {
                                onAdd(normalized)
                                newSiteInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (allowlist.isEmpty()) {
                    Text(
                        text = "No allowed sites added yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                    ) {
                        items(allowlist.toList()) { host ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = host,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { onRemove(host) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}
