package io.github.omeg4dev.fmhy.update.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.update.UpdateController

@Composable
fun InstallPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(InstallPermissionText.TITLE) },
        text = { Text(InstallPermissionText.MESSAGE) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(InstallPermissionText.CONFIRM)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(InstallPermissionText.CANCEL)
            }
        },
    )
}

@Composable
fun InstallPermissionDialog(
    controller: UpdateController,
) {
    val needsPermission by controller.needsInstallPermission.collectAsStateWithLifecycle()
    if (needsPermission) {
        InstallPermissionDialog(
            onConfirm = controller::confirmInstallPermission,
            onDismiss = controller::cancelInstallPermission,
        )
    }
}
