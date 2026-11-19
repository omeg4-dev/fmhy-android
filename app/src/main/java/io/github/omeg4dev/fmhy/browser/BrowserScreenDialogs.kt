package io.github.omeg4dev.fmhy.browser

/** Dialogs and capture progress for [BrowserScreen]. */

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
internal fun BrowserScreenScope.BrowserScreenDialogs() {
    // Link Menu Sheet
    pendingLinkMenu?.let { req ->
        LinkMenuSheet(
            request = req,
            onLookupVerdict = { url ->
                val verdict = hooks?.checkVerdict(url)
                verdict?.let { SiteBarModel.from(it, isRedirectedFromEntry = false) }
            },
            onAction = { action ->
                pendingLinkMenu = null
                when (action) {
                    LinkMenuAction.OPEN_NEW_TAB -> {
                        val opener = tabManager.getTabRecord(req.tabId)
                        val isPrivate = tabPrivacyFor(opener?.isPrivate == true, action)
                        tabManager.openTab(url = req.url, isPrivate = isPrivate, select = true)
                    }
                    LinkMenuAction.OPEN_BACKGROUND -> {
                        val opener = tabManager.getTabRecord(req.tabId)
                        val isPrivate = tabPrivacyFor(opener?.isPrivate == true, action)
                        val newId = tabManager.openTab(url = req.url, isPrivate = isPrivate, select = false)
                        val event = BrowserSnackbarEvent(
                            message = "Opened in background",
                            actionLabel = "Switch",
                            onAction = { tabManager.selectTab(newId) },
                        )
                        if (hooks?.snackbarFlow?.tryEmit(event) != true) {
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = event.message,
                                    actionLabel = event.actionLabel,
                                    duration = SnackbarDuration.Long,
                                )
                                if (result == SnackbarResult.ActionPerformed) event.onAction?.invoke()
                            }
                        }
                    }
                    LinkMenuAction.OPEN_PRIVATE -> {
                        tabManager.openTab(url = req.url, isPrivate = true, select = true)
                    }
                    LinkMenuAction.COPY_LINK -> {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("Link", req.url))
                        val event = BrowserSnackbarEvent(message = "Link copied")
                        if (hooks?.snackbarFlow?.tryEmit(event) != true) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = event.message,
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    }
                    LinkMenuAction.SHARE_LINK -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, req.url)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share link"))
                    }
                    LinkMenuAction.OPEN_IMAGE_NEW_TAB -> {
                        val opener = tabManager.getTabRecord(req.tabId)
                        val isPrivate = tabPrivacyFor(opener?.isPrivate == true, action)
                        tabManager.openTab(url = req.url, isPrivate = isPrivate, select = true)
                    }
                    LinkMenuAction.COPY_IMAGE_LINK -> {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("Image link", req.url))
                        val event = BrowserSnackbarEvent(message = "Link copied")
                        if (hooks?.snackbarFlow?.tryEmit(event) != true) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = event.message,
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    }
                }
            },
            onDismiss = { pendingLinkMenu = null },
        )
    }

    // Hide Element Confirmation Dialog
    val ruleToHide = pendingHideElementRule
    if (ruleToHide != null) {
        AlertDialog(
            onDismissRequest = { pendingHideElementRule = null },
            title = { Text("Hide element on this site") },
            text = {
                Column {
                    Text("Add cosmetic filter rule:")
                    Text(
                        text = ruleToHide,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingHideElementRule = null
                        scope.launch {
                            settingsRepository.updateBlocking { bp ->
                                val existing = bp.customRules.trim()
                                val newRules = if (existing.isEmpty()) ruleToHide else "$existing\n$ruleToHide"
                                bp.copy(customRules = newRules)
                            }
                            currentTab?.let { tabManager.reloadTab(it.id) }
                        }
                    }
                ) {
                    Text("Hide")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingHideElementRule = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Open External App Dialog (call site 2)
    val externalApp = pendingExternalApp
    if (externalApp != null) {
        OpenInAnotherAppDialog(
            appName = externalApp.second,
            onConfirm = {
                val intent = externalApp.first
                pendingExternalApp = null
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) {}
            },
            onDismiss = { pendingExternalApp = null },
        )
    }

    if (showPictureChoice) {
        AlertDialog(
            onDismissRequest = { showPictureChoice = false },
            title = { Text("Save picture of page") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = {
                            showPictureChoice = false
                            onStartPictureCapture(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        ) {
                            Text(
                                text = "Visible area",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "What you see now",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        onClick = {
                            showPictureChoice = false
                            onStartPictureCapture(true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        ) {
                            Text(
                                text = "Full page",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "The whole page, top to bottom",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPictureChoice = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (isCapturing && isCapturingFullPage) {
        val (done, total) = captureProgress
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = { Text("Saving picture…") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (total == 0) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { done.toFloat() / total.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = "$done of $total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { captureJob?.cancel() }) {
                    Text("Cancel")
                }
            },
        )
    }
}
