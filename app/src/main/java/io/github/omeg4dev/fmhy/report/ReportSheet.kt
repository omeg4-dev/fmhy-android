package io.github.omeg4dev.fmhy.report

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportSheet(
    request: ReportRequest,
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val technicalInfo = remember(context) {
        val versionName = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (_: Exception) {
            null
        } ?: "1.0.0"
        TechnicalInfo(
            appVersion = versionName,
            androidVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        )
    }

    var whatWentWrong by rememberSaveable { mutableStateOf("") }
    var expected by rememberSaveable { mutableStateOf("") }
    var selectedArea by rememberSaveable { mutableStateOf(request.defaultArea) }
    var includeTechnical by rememberSaveable { mutableStateOf(true) }
    var includePageUrl by rememberSaveable { mutableStateOf(false) }

    val isTechnicalLocked = request.crash != null
    val effectiveIncludeTechnical = if (isTechnicalLocked) true else includeTechnical

    val draft = remember(
        selectedArea,
        whatWentWrong,
        expected,
        effectiveIncludeTechnical,
        includePageUrl,
        request.pageUrl,
        request.isPrivateTab,
        technicalInfo,
        request.crash,
    ) {
        ReportDraft(
            area = selectedArea,
            whatWentWrong = whatWentWrong,
            expected = expected,
            includeTechnical = effectiveIncludeTechnical,
            includePageUrl = includePageUrl,
            pageUrl = request.pageUrl,
            isPrivateTab = request.isPrivateTab,
            technical = technicalInfo,
            crash = request.crash,
        )
    }

    val previewText = remember(draft) {
        ReportText.build(draft)
    }

    var showGitHubConfirmDialog by remember { mutableStateOf(false) }

    fun showMessage(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        container.showSnackbar(msg)
    }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("FMHY Report", text))
    }

    fun openViewIntent(url: String, onBrowserNotFound: () -> Unit) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            onBrowserNotFound()
        }
    }

    fun handleGitHubReport() {
        val issueLink = IssueUrlBuilder.build(draft)
        if (issueLink.truncated) {
            copyToClipboard(previewText)
            showMessage("Report copied")
        }
        openViewIntent(
            url = issueLink.url,
            onBrowserNotFound = {
                copyToClipboard(previewText)
                showMessage("No browser found")
            },
        )
        if (request.crash != null) {
            container.crashReportStore.clear()
        }
        onDismiss()
    }

    fun handleDiscordMessage() {
        copyToClipboard(previewText)
        showMessage("Report copied. Paste it in a message to the developer.")
        openViewIntent(
            url = DISCORD_PROFILE_URL,
            onBrowserNotFound = {
                showMessage("No browser found")
            },
        )
        if (request.crash != null) {
            container.crashReportStore.clear()
        }
        onDismiss()
    }

    fun handleCopyReport() {
        copyToClipboard(previewText)
        showMessage("Report copied")
        if (request.crash != null) {
            container.crashReportStore.clear()
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Report a problem",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = whatWentWrong,
                onValueChange = { if (it.length <= 1000) whatWentWrong = it },
                label = { Text("What went wrong?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                supportingText = {
                    Text(
                        text = "${whatWentWrong.length} / 1000",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (area in ReportArea.entries) {
                    FilterChip(
                        selected = selectedArea == area,
                        onClick = { selectedArea = area },
                        label = { Text(area.label) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isTechnicalLocked) {
                        includeTechnical = !includeTechnical
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Include technical details", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "App version, Android version and device model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = effectiveIncludeTechnical,
                    enabled = !isTechnicalLocked,
                    onCheckedChange = { includeTechnical = it },
                )
            }

            if (request.pageUrl != null) {
                val isPageSwitchEnabled = !request.isPrivateTab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isPageSwitchEnabled) {
                            includePageUrl = !includePageUrl
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Include the web address of this page", style = MaterialTheme.typography.bodyLarge)
                        if (request.isPrivateTab) {
                            Text(
                                "Not available in private tabs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = includePageUrl && isPageSwitchEnabled,
                        enabled = isPageSwitchEnabled,
                        onCheckedChange = { includePageUrl = it },
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = previewText,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (request.emphasizedAction == ReportAction.DISCORD) {
                    Button(
                        onClick = { handleDiscordMessage() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Message on Discord")
                    }
                    FilledTonalButton(
                        onClick = { showGitHubConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Report on GitHub")
                    }
                } else {
                    Button(
                        onClick = { showGitHubConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Report on GitHub")
                    }
                    FilledTonalButton(
                        onClick = { handleDiscordMessage() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Message on Discord")
                    }
                }
                TextButton(
                    onClick = { handleCopyReport() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Copy report")
                }
            }
        }
    }

    if (showGitHubConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showGitHubConfirmDialog = false },
            title = { Text("Report on GitHub") },
            text = { Text("Reports on GitHub are public. Do not include private information.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGitHubConfirmDialog = false
                        handleGitHubReport()
                    }
                ) {
                    Text("Open GitHub")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGitHubConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
