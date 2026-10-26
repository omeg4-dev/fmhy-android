package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MobileDataConfirmationDialog(
    viewModel: SettingsViewModel,
) {
    val pendingAction by viewModel.pendingMobileAction.collectAsStateWithLifecycle()
    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMobileActionDialog,
            title = { Text("Use mobile data this time?") },
            text = { Text("Wi-Fi only is on and you are on mobile data. Run this once using mobile data?") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmMobileAction) {
                    Text("Use mobile data")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMobileActionDialog) {
                    Text("Cancel")
                }
            },
        )
    }
}
