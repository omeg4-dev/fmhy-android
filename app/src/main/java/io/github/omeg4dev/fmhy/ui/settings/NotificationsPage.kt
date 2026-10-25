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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    onNavigateToWhatsNew: () -> Unit = {},
    onNavigateToFollowing: () -> Unit = {},
    extraRows: LazyListScope.() -> Unit = {},
) {
    val notificationPrefs by viewModel.notificationPrefs.collectAsStateWithLifecycle()
    val followedCount by viewModel.followedSectionsCount.collectAsStateWithLifecycle()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val master = notificationPrefs.enabled

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
                title = { Text("What's new & notifications") },
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
            // 1. Notifications section
            item {
                SettingsSection(title = "Notifications") {
                    SwitchRow(
                        title = "Notifications",
                        checked = notificationPrefs.enabled,
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
                        title = "New sites in followed sections",
                        checked = notificationPrefs.notifyFollowed,
                        enabled = master,
                        onChange = { checked ->
                            viewModel.updateNotifications { it.copy(notifyFollowed = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Changes to saved sites",
                        summary = "Starred, removed or moved",
                        checked = notificationPrefs.watchSaved,
                        enabled = master,
                        onChange = { checked ->
                            viewModel.updateNotifications { it.copy(watchSaved = checked) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "New monthly posts",
                        summary = "Get a notification when FMHY publishes a new monthly post",
                        checked = notificationPrefs.notifyNewPosts,
                        enabled = master,
                        onChange = { checked ->
                            viewModel.updateNotifications { it.copy(notifyNewPosts = checked) }
                        },
                    )
                    SettingsDivider()
                    ChoiceRow(
                        title = "Check every",
                        options = listOf(
                            UpdateInterval.H12 to "12 hours",
                            UpdateInterval.DAILY to "Day",
                            UpdateInterval.WEEKLY to "Week",
                        ),
                        selected = notificationPrefs.interval,
                        enabled = master,
                        onSelect = { interval ->
                            viewModel.updateNotifications { it.copy(interval = interval) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Only on Wi-Fi",
                        checked = notificationPrefs.wifiOnly,
                        enabled = master,
                        onChange = { checked ->
                            viewModel.updateNotifications { it.copy(wifiOnly = checked) }
                        },
                    )
                }
            }

            // 2. Following
            item {
                SettingsSection {
                    val followSummary = if (followedCount > 0) {
                        if (followedCount == 1) "1 section followed" else "$followedCount sections followed"
                    } else {
                        "Nothing followed yet"
                    }
                    NavRow(
                        title = "Following",
                        summary = followSummary,
                        onClick = onNavigateToFollowing,
                    )
                }
            }

            // 3. Updates section
            item {
                SettingsSection(title = "Updates") {
                    NavRow(
                        title = "Open What's new",
                        summary = "Added, updated and removed sites",
                        onClick = onNavigateToWhatsNew,
                    )
                    SettingsDivider()
                    ButtonRow(
                        text = if (isCheckingUpdates) "Checking for updates…" else "Check for updates now",
                        loading = isCheckingUpdates,
                        enabled = !isCheckingUpdates,
                        onClick = {
                            viewModel.checkForUpdates { onShowSnackbar(it) }
                        },
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Detailed change history",
                        summary = "Uses fmhy-tracker for exact dates and changes",
                        checked = notificationPrefs.useTracker,
                        onChange = { checked ->
                            viewModel.updateNotifications { it.copy(useTracker = checked) }
                        },
                    )
                }
            }

            extraRows()
        }
    }
}
