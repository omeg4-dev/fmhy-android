package io.github.omeg4dev.fmhy.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LinkOpener(
    private val context: Context,
    private val store: DatasetStore,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val onShowSnackbar: (String) -> Unit,
) {
    var pendingUnsafeUrl by mutableStateOf<String?>(null)
        private set
    var pendingUnsafeReason by mutableStateOf<String?>(null)
        private set

    fun openUrl(url: String) {
        coroutineScope.launch {
            val reason = store.unsafeReason(url)
            if (reason != null) {
                pendingUnsafeReason = reason
                pendingUnsafeUrl = url
            } else {
                openUrlDirectly(url)
            }
        }
    }

    fun confirmOpenUnsafe() {
        val url = pendingUnsafeUrl
        pendingUnsafeUrl = null
        pendingUnsafeReason = null
        if (url != null) {
            openUrlDirectly(url)
        }
    }

    fun cancelOpenUnsafe() {
        pendingUnsafeUrl = null
        pendingUnsafeReason = null
    }

    private fun openUrlDirectly(url: String) {
        coroutineScope.launch {
            val openExternal = settingsRepository.openInExternalBrowser.first()
            val uri = url.toUri()
            if (openExternal) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    onShowSnackbar("No browser found to open link")
                }
            } else {
                try {
                    val customTabsIntent = CustomTabsIntent.Builder()
                        .setShowTitle(true)
                        .build()
                    customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    customTabsIntent.launchUrl(context, uri)
                } catch (_: ActivityNotFoundException) {
                    onShowSnackbar("No browser found to open link")
                } catch (_: Exception) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        onShowSnackbar("No browser found to open link")
                    }
                }
            }
        }
    }
}

@Composable
fun UnsafeWarningDialog(opener: LinkOpener) {
    val reason = opener.pendingUnsafeReason ?: return
    AlertDialog(
        onDismissRequest = { opener.cancelOpenUnsafe() },
        title = { Text("FMHY lists this site as unsafe") },
        text = { Text(reason) },
        confirmButton = {
            TextButton(onClick = { opener.confirmOpenUnsafe() }) {
                Text("Open anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = { opener.cancelOpenUnsafe() }) {
                Text("Cancel")
            }
        }
    )
}
