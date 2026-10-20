package io.github.omeg4dev.fmhy.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
            contentPadding = innerPadding,
        ) {
            item {
                SwitchRow(
                    title = "Use fmhy-tracker",
                    summary = "Change history from fmhy-tracker.pages.dev",
                    checked = notificationPrefs.useTracker,
                    onChange = { checked ->
                        viewModel.updateNotifications { it.copy(useTracker = checked) }
                    },
                )
            }

            item {
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
            }

            item {
                SwitchRow(
                    title = "New sites in followed categories",
                    checked = notificationPrefs.notifyFollowed,
                    enabled = master,
                    onChange = { checked ->
                        viewModel.updateNotifications { it.copy(notifyFollowed = checked) }
                    },
                )
            }

            item {
                SwitchRow(
                    title = "Changes to saved sites",
                    summary = "Starred, removed or moved",
                    checked = notificationPrefs.watchSaved,
                    enabled = master,
                    onChange = { checked ->
                        viewModel.updateNotifications { it.copy(watchSaved = checked) }
                    },
                )
            }

            item {
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
            }

            item {
                SwitchRow(
                    title = "Only on Wi-Fi",
                    checked = notificationPrefs.wifiOnly,
                    enabled = master,
                    onChange = { checked ->
                        viewModel.updateNotifications { it.copy(wifiOnly = checked) }
                    },
                )
            }

            extraRows()
        }
    }
}
