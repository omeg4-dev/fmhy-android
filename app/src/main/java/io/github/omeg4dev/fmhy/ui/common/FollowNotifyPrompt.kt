package io.github.omeg4dev.fmhy.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.omeg4dev.fmhy.data.NotificationPrefs
import io.github.omeg4dev.fmhy.data.SettingsRepository
import kotlinx.coroutines.launch

class FollowNotifyPromptState(
    var promptShown: Boolean = false,
    var notificationPrefs: NotificationPrefs = NotificationPrefs(),
) {
    var showPrompt by mutableStateOf(false)

    fun checkAndTrigger() {
        if (!promptShown && !(notificationPrefs.enabled && notificationPrefs.notifyFollowed)) {
            showPrompt = true
        }
    }
}

@Composable
fun rememberFollowNotifyPromptState(
    promptShown: Boolean,
    notificationPrefs: NotificationPrefs,
): FollowNotifyPromptState {
    val state = remember { FollowNotifyPromptState() }
    state.promptShown = promptShown
    state.notificationPrefs = notificationPrefs
    return state
}

@Composable
fun FollowNotifyPrompt(
    settingsRepository: SettingsRepository,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        scope.launch {
            settingsRepository.updateNotifications {
                it.copy(enabled = isGranted, notifyFollowed = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Get notified about new sites in sections you follow?")
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    scope.launch {
                        settingsRepository.setFollowNotifyPromptShown(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                settingsRepository.updateNotifications {
                                    it.copy(enabled = true, notifyFollowed = true)
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            settingsRepository.updateNotifications {
                                it.copy(enabled = true, notifyFollowed = true)
                            }
                        }
                    }
                }
            ) {
                Text("Turn on")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    scope.launch {
                        settingsRepository.setFollowNotifyPromptShown(true)
                    }
                }
            ) {
                Text("Not now")
            }
        }
    )
}
