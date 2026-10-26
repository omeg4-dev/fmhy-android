package io.github.omeg4dev.fmhy.update.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.update.UpdateController

@Composable
fun UpdateMobileDataDialog(
    controller: UpdateController,
) {
    val pendingAsk by controller.pendingMobileAsk.collectAsStateWithLifecycle()
    if (pendingAsk) {
        AlertDialog(
            onDismissRequest = controller::cancelMobileAsk,
            title = { Text("Use mobile data this time?") },
            text = { Text("Wi-Fi only is on and you are on mobile data. Run this once using mobile data?") },
            confirmButton = {
                TextButton(onClick = controller::confirmMobileAsk) {
                    Text("Use mobile data")
                }
            },
            dismissButton = {
                TextButton(onClick = controller::cancelMobileAsk) {
                    Text("Cancel")
                }
            },
        )
    }
}
