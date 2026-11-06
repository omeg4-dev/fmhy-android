package io.github.omeg4dev.fmhy.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.UpdateCheckInterval
import io.github.omeg4dev.fmhy.update.UpdateCheckPolicy
import io.github.omeg4dev.fmhy.update.UpdateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdatesPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onShowSnackbar: (String) -> Unit = {},
    extraRows: LazyListScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val updateController = viewModel.updateController
    val updatePrefs by viewModel.updatePrefs.collectAsStateWithLifecycle()
    val updateState by updateController.state.collectAsStateWithLifecycle()
    val availableVersion by updateController.availableVersion.collectAsStateWithLifecycle()

    val eAutoCheck = SettingsCatalog.appUpdatesAutoCheck
    val eNotify = SettingsCatalog.appUpdatesNotify
    val eInterval = SettingsCatalog.appUpdatesInterval
    // Check for updates
    val eCheckNow = SettingsCatalog.appUpdatesCheckNow
    val eSkippedVersion = SettingsCatalog.appUpdatesSkippedVersion

    val sectionSettingIds = remember(updateController.enabled) {
        if (updateController.enabled) {
            listOf(listOf(eAutoCheck.id, eNotify.id, eInterval.id, eCheckNow.id, eSkippedVersion.id))
        } else {
            emptyList()
        }
    }
    val listState = rememberFocusedListState(sectionSettingIds)

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission()) }
    var permissionDenied by remember { mutableStateOf(false) }
    // Re-check when returning from the system dialog or the app's notification settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsGranted = hasNotificationPermission()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationsGranted = isGranted
        if (!isGranted) {
            permissionDenied = true
            onShowSnackbar("Notification permission was denied")
        }
    }

    val versionName = remember(context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (_: Exception) {
            null
        } ?: "1.0.0"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App updates") },
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
            if (!updateController.enabled) {
                item {
                    SettingsSection(title = "Updates") {
                        Text(
                            text = "Updates come from the app store on this build.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                item {
                    SettingsSection(title = "Updates") {
                        SwitchRow(
                            title = eAutoCheck.title,
                            summary = eAutoCheck.description,
                            checked = updatePrefs.autoCheck,
                            settingId = eAutoCheck.id,
                            onChange = { checked ->
                                viewModel.updateUpdatePrefs { it.copy(autoCheck = checked) }
                            },
                        )
                        SettingsDivider()
                        SwitchRow(
                            title = eNotify.title,
                            summary = eNotify.description,
                            checked = updatePrefs.notify,
                            enabled = updatePrefs.autoCheck && notificationsGranted,
                            settingId = eNotify.id,
                            onChange = { checked ->
                                viewModel.updateUpdatePrefs { it.copy(notify = checked) }
                            },
                        )
                        if (!notificationsGranted) {
                            TextButton(
                                onClick = {
                                    if (permissionDenied) {
                                        // The system will not ask again: send the user to the app's notification settings.
                                        context.startActivity(
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text("Grant permission")
                            }
                        }
                        SettingsDivider()
                        ChoiceRow(
                            title = eInterval.title,
                            options = listOf(
                                UpdateCheckInterval.H6 to "Every 6 hours",
                                UpdateCheckInterval.H12 to "Every 12 hours",
                                UpdateCheckInterval.H24 to "Every 24 hours",
                                UpdateCheckInterval.WEEK to "Every week",
                            ),
                            selected = updatePrefs.interval,
                            enabled = updatePrefs.autoCheck,
                            settingId = eInterval.id,
                            onSelect = { interval ->
                                viewModel.updateUpdatePrefs { it.copy(interval = interval) }
                            },
                        )
                        SettingsDivider()
                        val checkSummary = when {
                            updateState is UpdateState.Checking -> "Checking…"
                            updateState is UpdateState.WaitingForWifi -> "Waiting for Wi-Fi"
                            availableVersion != null -> "Version $availableVersion is available"
                            updateState is UpdateState.UpToDate -> "You have the latest version"
                            else -> null
                        }
                        val showMarker = UpdateCheckPolicy.showSettingsMarker(availableVersion, versionName)
                        // Check for updates
                        NavRow(
                            title = eCheckNow.title,
                            summary = checkSummary,
                            badge = if (showMarker) { { Badge() } } else null,
                            settingId = eCheckNow.id,
                            onClick = {
                                updateController.checkNow()
                            },
                        )
                        SettingsDivider()
                        InfoRow(
                            title = eSkippedVersion.title,
                            value = updatePrefs.skippedVersion ?: "None",
                            settingId = eSkippedVersion.id,
                        )
                    }
                }
            }

            extraRows()
        }
    }
}
