package io.github.omeg4dev.fmhy.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.UpdateInterval

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val notificationPrefs by viewModel.notificationPrefs.collectAsStateWithLifecycle()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val master = notificationPrefs.enabled

    val eNotifications = SettingsCatalog.notificationsEnabled
    val eNotifyFollowed = SettingsCatalog.notificationsNotifyFollowed
    val eWatchSaved = SettingsCatalog.notificationsWatchSaved
    val eNotifyNewPosts = SettingsCatalog.notificationsNotifyNewPosts
    val eCheckEvery = SettingsCatalog.notificationsCheckEvery
    val eDetailedChangeHistory = SettingsCatalog.notificationsDetailedChangeHistory
    val eCheckNow = SettingsCatalog.notificationsCheckNow

    val notifIds = listOf(
        eNotifications.id,
        eNotifyFollowed.id,
        eWatchSaved.id,
        eNotifyNewPosts.id,
    )
    val advancedIds = listOf(
        eCheckEvery.id,
        eDetailedChangeHistory.id,
        eCheckNow.id,
    )

    val sectionSettingIds = remember { listOf(notifIds, advancedIds) }
    val listState = rememberFocusedListState(sectionSettingIds)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.updateNotifications { it.copy(enabled = true) }
        } else {
            viewModel.updateNotifications { it.copy(enabled = false) }
            onShowSnackbar("Notification permission was denied")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
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
            // Notifications section
            item {
                SettingsSection(title = "Notifications") {
                    SwitchRow(
                        title = eNotifications.title,
                        summary = eNotifications.description,
                        checked = notificationPrefs.enabled,
                        settingId = eNotifications.id,
                        onChange = { turningOn ->
                            if (turningOn) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        viewModel.updateNotifications { it.copy(enabled = true) }
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    viewModel.updateNotifications { it.copy(enabled = true) }
                                }
                            } else {
                                viewModel.updateNotifications { it.copy(enabled = false) }
                            }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eNotifyFollowed.title,
                        summary = eNotifyFollowed.description,
                        checked = notificationPrefs.notifyFollowed,
                        enabled = master,
                        settingId = eNotifyFollowed.id,
                        onChange = { checked ->
                            viewModel.updateNotifications { it.copy(notifyFollowed = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eWatchSaved.title,
                        summary = eWatchSaved.description,
                        checked = notificationPrefs.watchSaved,
                        enabled = master,
                        settingId = eWatchSaved.id,
                        onChange = { checked ->
                            viewModel.updateNotifications { it.copy(watchSaved = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eNotifyNewPosts.title,
                        summary = eNotifyNewPosts.description,
                        checked = notificationPrefs.notifyNewPosts,
                        enabled = master,
                        settingId = eNotifyNewPosts.id,
                        onChange = { checked ->
                            viewModel.updateNotifications { it.copy(notifyNewPosts = checked) }
                        },
                    )
                }
            }

            // Advanced section
            item {
                ExpandableSection(
                    title = "Advanced",
                    settingIds = advancedIds,
                ) {
                    ChoiceRow(
                        title = eCheckEvery.title,
                        options = listOf(
                            UpdateInterval.H12 to "12 hours",
                            UpdateInterval.DAILY to "Day",
                            UpdateInterval.WEEKLY to "Week",
                        ),
                        selected = notificationPrefs.interval,
                        enabled = master,
                        settingId = eCheckEvery.id,
                        onSelect = { interval ->
                            viewModel.updateNotifications { it.copy(interval = interval) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = eDetailedChangeHistory.title,
                        summary = eDetailedChangeHistory.description,
                        checked = notificationPrefs.useTracker,
                        settingId = eDetailedChangeHistory.id,
                        onChange = { checked ->
                            viewModel.updateNotifications { it.copy(useTracker = checked) }
                        },
                    )
                    SettingsDivider()
                    ButtonRow(
                        text = if (isCheckingUpdates) "Checking for updates…" else eCheckNow.title,
                        loading = isCheckingUpdates,
                        enabled = !isCheckingUpdates,
                        settingId = eCheckNow.id,
                        onClick = {
                            viewModel.checkForUpdates { onShowSnackbar(it) }
                        },
                    )
                }
            }

            extraRows()
        }

        MobileDataConfirmationDialog(viewModel)
    }
}
