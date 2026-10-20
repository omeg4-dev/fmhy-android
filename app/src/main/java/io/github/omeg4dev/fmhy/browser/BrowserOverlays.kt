package io.github.omeg4dev.fmhy.browser

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("This site isn't loading", style = MaterialTheme.typography.headlineSmall)
            Text(state.reason, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Text(state.host, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
            state.mirrors.forEachIndexed { index, link ->
                val host = try { Uri.parse(link.url).host ?: link.url } catch (_: Exception) { link.url }
                OutlinedButton(onClick = { onOpenUrl(link.url) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Try mirror ${index + 2} · $host")
                }
            }
            OutlinedButton(
                onClick = { onOpenUrl("https://web.archive.org/web/2/${state.failingUrl}") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Wayback Machine") }

            if (state.alternatives.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Alternatives on FMHY", style = MaterialTheme.typography.titleMedium)
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
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = if (state.isUnsafe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(8.dp))
            Text(state.host, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(state.reason ?: fallback, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onGoBack, modifier = Modifier.fillMaxWidth()) { Text("Go back") }
            TextButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Continue anyway") }
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
        title = { Text(state.filename) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Size: ${state.sizeText}")
                Text("Type: ${state.mimeType.ifBlank { "unknown" }}")
                Text("From: ${state.host.ifBlank { "unknown host" }}")
                if (state.riskWarning != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.riskWarning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
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
