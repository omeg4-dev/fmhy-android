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
import io.github.omeg4dev.fmhy.ui.shell.JobKind
import io.github.omeg4dev.fmhy.ui.shell.failedListDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBlockingPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val blockingPrefs by viewModel.blockingPrefs.collectAsStateWithLifecycle()
    val adblockStatus by viewModel.adblockStatus.collectAsStateWithLifecycle()
    val listStates by viewModel.listStates.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val master = blockingPrefs.adblock

    val eBlockAdsAndTrackers = SettingsCatalog.adBlockingBlockAdsAndTrackers
    val eBlockingLevel = SettingsCatalog.adBlockingBlockingLevel
    val eShowBlockedCount = SettingsCatalog.adBlockingShowBlockedCount
    val eHidePageElements = SettingsCatalog.adBlockingHidePageElements
    val eFixBrokenPages = SettingsCatalog.adBlockingFixBrokenPages
    val eUpdateListsAutomatically = SettingsCatalog.adBlockingUpdateListsAutomatically
    val eListStatus = SettingsCatalog.adBlockingListStatus
    val eFilterLists = SettingsCatalog.adBlockingFilterLists
    val eCustomRules = SettingsCatalog.adBlockingCustomRules
    val eAllowedSites = SettingsCatalog.adBlockingAllowedSites

    val adblockIds = if (master) {
        listOf(
            eBlockAdsAndTrackers.id,
            eBlockingLevel.id,
            eShowBlockedCount.id,
            eHidePageElements.id,
            eFixBrokenPages.id,
            eUpdateListsAutomatically.id,
        )
    } else {
        listOf(eBlockAdsAndTrackers.id)
    }
    val statusIds = listOf(eListStatus.id)
    val advancedIds = listOf(eFilterLists.id, eCustomRules.id, eAllowedSites.id)

    val sectionSettingIds = remember(master) {
        listOf(adblockIds, statusIds, advancedIds)
    }
    val listState = rememberFocusedListState(sectionSettingIds)

    val focusedSetting = LocalSettingFocus.current
    var isAdvancedExpanded by remember {
        mutableStateOf(
            (blockingPrefs.enabledLists != FilterListCatalog.balancedIds &&
                blockingPrefs.enabledLists != FilterListCatalog.strictIds) ||
                (focusedSetting != null && focusedSetting in advancedIds)
        )
    }

    var showCustomRulesDialog by remember { mutableStateOf(false) }
    var showAllowedSitesDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ad blocking") },
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
            // Ad blocking section
            item {
                SettingsSection(title = "Ad blocking") {
                    SwitchRow(
                        title = eBlockAdsAndTrackers.title,
                        summary = eBlockAdsAndTrackers.description,
                        checked = blockingPrefs.adblock,
                        settingId = eBlockAdsAndTrackers.id,
                        onChange = { checked ->
                            viewModel.updateBlocking { it.copy(adblock = checked) }
                        },
                    )

                    if (master) {
                        SettingsDivider()
                        OptionCardRow(
                            title = eBlockingLevel.title,
                            options = listOf(
                                OptionCardItem(
                                    value = "balanced",
                                    title = "Balanced",
                                    subtitle = "Ads, trackers and malware lists",
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
                            settingId = eBlockingLevel.id,
                            onSelect = { option ->
                                when (option) {
                                    "balanced" -> viewModel.updateBlocking { it.copy(enabledLists = FilterListCatalog.balancedIds) }
                                    "strict" -> viewModel.updateBlocking { it.copy(enabledLists = FilterListCatalog.strictIds) }
                                    "custom" -> isAdvancedExpanded = true
                                }
                            },
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = eShowBlockedCount.title,
                            summary = eShowBlockedCount.description,
                            checked = blockingPrefs.showBlockedCount,
                            enabled = master,
                            settingId = eShowBlockedCount.id,
                            onChange = { checked ->
                                viewModel.updateBlocking { it.copy(showBlockedCount = checked) }
                            },
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = eHidePageElements.title,
                            summary = eHidePageElements.description,
                            checked = blockingPrefs.elementHiding,
                            enabled = master,
                            settingId = eHidePageElements.id,
                            onChange = { checked ->
                                viewModel.updateBlocking { it.copy(elementHiding = checked) }
                            },
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = eFixBrokenPages.title,
                            summary = eFixBrokenPages.description,
                            checked = blockingPrefs.scriptlets,
                            enabled = master,
                            settingId = eFixBrokenPages.id,
                            onChange = { checked ->
                                viewModel.updateBlocking { it.copy(scriptlets = checked) }
                            },
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = eUpdateListsAutomatically.title,
                            summary = eUpdateListsAutomatically.description,
                            checked = blockingPrefs.autoUpdateLists,
                            enabled = master,
                            settingId = eUpdateListsAutomatically.id,
                            onChange = { checked ->
                                viewModel.updateBlocking { it.copy(autoUpdateLists = checked) }
                            },
                        )
                    }
                }
            }

            // List status section
            item {
                SettingsSection(title = "List status") {
                    InfoRow(
                        title = "Rules active",
                        value = "${adblockStatus.ruleCount}",
                        settingId = eListStatus.id,
                    )
                    SettingsDivider()
                    InfoRow(
                        title = "Last updated",
                        value = formatRelativeTime(adblockStatus.lastUpdated),
                    )

                    val adblockJob = jobs.firstOrNull { it.kind == JobKind.ADBLOCK }
                    if (adblockJob != null) {
                        JobProgressRow(adblockJob)
                    }

                    ButtonRow(
                        text = "Update now",
                        enabled = !adblockStatus.updating,
                        onClick = viewModel::updateAdblockNow,
                    )

                    if (adblockStatus.waitingForWifi) {
                        Text(
                            text = "Waiting for Wi-Fi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    if (adblockStatus.failedLists.isNotEmpty()) {
                        adblockStatus.failedLists.forEach { failedList ->
                            Text(
                                text = failedListDetail(failedList),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                            )
                        }
                    }

                    if (adblockStatus.error != null) {
                        Text(
                            text = "Update error: ${adblockStatus.error}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Advanced section
            item {
                ExpandableSection(
                    title = "Advanced",
                    summary = "Filter lists, your own rules, allowed sites",
                    expanded = isAdvancedExpanded,
                    onExpandedChange = { isAdvancedExpanded = it },
                    settingIds = advancedIds,
                ) {
                    val groupTitles = mapOf(
                        FilterList.Group.UBLOCK to "uBlock Origin",
                        FilterList.Group.ADS to "Ads",
                        FilterList.Group.PRIVACY to "Privacy",
                        FilterList.Group.ANNOYANCES to "Annoyances",
                        FilterList.Group.FMHY to "FMHY",
                    )

                    Column(modifier = Modifier.settingHighlight(eFilterLists.id)) {
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
                    }

                    SettingsDivider()

                    val ruleCount = blockingPrefs.customRules.lines().count { it.isNotBlank() }
                    NavRow(
                        title = eCustomRules.title,
                        summary = "$ruleCount ${if (ruleCount == 1) "rule" else "rules"}",
                        enabled = master,
                        settingId = eCustomRules.id,
                        onClick = { showCustomRulesDialog = true },
                    )
                    SettingsDivider()

                    val siteCount = blockingPrefs.allowlist.size
                    NavRow(
                        title = eAllowedSites.title,
                        summary = "$siteCount ${if (siteCount == 1) "site" else "sites"}",
                        enabled = master,
                        settingId = eAllowedSites.id,
                        onClick = { showAllowedSitesDialog = true },
                    )
                    SettingsDivider()

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

    MobileDataConfirmationDialog(viewModel)
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
                    },
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
