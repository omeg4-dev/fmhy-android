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
    controller: UpdateController,
) {
    val needsPermission by controller.needsInstallPermission.collectAsStateWithLifecycle()
    if (needsPermission) {
        AlertDialog(
            onDismissRequest = controller::cancelInstallPermission,
            title = { Text(InstallPermissionText.TITLE) },
            text = { Text(InstallPermissionText.MESSAGE) },
            confirmButton = {
                TextButton(onClick = controller::confirmInstallPermission) {
                    Text(InstallPermissionText.CONFIRM)
                }
            },
            dismissButton = {
                TextButton(onClick = controller::cancelInstallPermission) {
                    Text(InstallPermissionText.CANCEL)
                }
            },
        )
    }
}
