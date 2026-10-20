package io.github.omeg4dev.fmhy.browser

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.ui.common.enterStagger

/** Full-size Compose overlay shown over the WebView when the main frame failed to load. */
@Composable
fun RescueOverlay(
    state: RescueState,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .enterStagger(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                "This site isn't loading",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                state.reason,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                state.host,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Retry")
            }
            state.mirrors.forEachIndexed { index, link ->
                val host = try { Uri.parse(link.url).host ?: link.url } catch (_: Exception) { link.url }
                OutlinedButton(
                    onClick = { onOpenUrl(link.url) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Try mirror ${index + 2} · $host")
                }
            }
            OutlinedButton(
                onClick = { onOpenUrl("https://web.archive.org/web/2/${state.failingUrl}") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Wayback Machine")
            }

            if (state.alternatives.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Alternatives on FMHY",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start),
                )
                state.alternatives.forEach { entry ->
                    io.github.omeg4dev.fmhy.ui.common.EntryRow(
                        entry = entry,
                        onOpenUrl = onOpenUrl,
                    )
                }
            }
        }
    }
}

/** Interstitial shown before an UNSAFE / POTENTIALLY_UNSAFE site; same wording as LinkOpener's dialog. */
@Composable
fun InterstitialOverlay(
    state: InterstitialState,
    onGoBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (state.isUnsafe) "FMHY lists this site as unsafe" else "FMHY flags this site as potentially unsafe"
    val fallback = if (state.isUnsafe) "This site is reported as unsafe on the FMHY safety list." else
        "This site may be unsafe. Proceed with care."
    val icon = if (state.isUnsafe) Icons.Default.GppBad else Icons.Default.Warning
    val iconTint = if (state.isUnsafe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .enterStagger(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = iconTint,
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = iconTint,
            )
            Text(state.host, style = MaterialTheme.typography.titleMedium)
            Text(state.reason ?: fallback, style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = onGoBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Go back")
            }
            TextButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Continue anyway")
            }
        }
    }
}

@Composable
fun DownloadDialog(
    state: DownloadDialogState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        title = { Text("Download file") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = state.filename,
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.sizeText.isNotBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text(state.sizeText) },
                        )
                    }
                    if (state.mimeType.isNotBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text(state.mimeType) },
                        )
                    }
                }
                Text(
                    text = "From: ${state.host.ifBlank { "unknown host" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.riskWarning != null) {
                    Text(
                        state.riskWarning,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val v = state.safeGuardVerdict
                if (v != null && (v.status == io.github.omeg4dev.fmhy.data.safety.SafetyStatus.UNSAFE ||
                        v.status == io.github.omeg4dev.fmhy.data.safety.SafetyStatus.POTENTIALLY_UNSAFE)) {
                    val label = if (v.status == io.github.omeg4dev.fmhy.data.safety.SafetyStatus.UNSAFE) "Unsafe on FMHY" else "Potentially unsafe on FMHY"
                    Text(
                        listOfNotNull(label, v.reason).joinToString(": "),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Download") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** Shared dialog for opening URLs in external applications. */
@Composable
fun OpenInAnotherAppDialog(
    appName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open in another app?") },
        text = { Text("Do you want to open this link in $appName?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Open") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
