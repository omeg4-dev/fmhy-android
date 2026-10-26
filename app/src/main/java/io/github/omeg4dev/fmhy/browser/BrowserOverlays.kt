package io.github.omeg4dev.fmhy.browser

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.browser.download.DownloadOffer
import io.github.omeg4dev.fmhy.browser.download.DownloadOfferText
import io.github.omeg4dev.fmhy.ui.common.enterStagger

/**
 * Compose overlay shown over the WebView (never over the browser bar) when the main frame failed to
 * load. It takes every touch, so nothing underneath is tapped while it is up or fading out.
 */
@Composable
fun RescueOverlay(
    state: RescueState,
    onRetry: () -> Unit,
    onReloadWithoutBlocking: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onReport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Final).changes.forEach { it.consume() }
                }
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .enterStagger(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val icon = if (state.kind == LoadFailureKind.BLOCKED) Icons.Default.Shield else Icons.Default.CloudOff
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            val headline = RescueReason.title(state.kind)
            Text(
                headline,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (state.reason != headline) {
                Text(
                    state.reason,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                state.host,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                RescueReason.advice(state.kind),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (state.canSkipBlocking) {
                FilledTonalButton(
                    onClick = onReloadWithoutBlocking,
                    enabled = !state.retrying,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reload without blocking")
                }
            }
            Button(
                onClick = onRetry,
                enabled = !state.retrying,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.retrying) "Retrying…" else "Retry")
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

            TextButton(
                onClick = onReport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Report this")
            }
        }
    }
}

/** The one warning before a page FMHY lists as unsafe and/or an ad filter blocks. */
@Composable
fun InterstitialOverlay(
    state: InterstitialState,
    onGoBack: () -> Unit,
    onContinue: () -> Unit,
    onReport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val icon = when {
        state.reasons.contains(WarningReason.UNSAFE) -> Icons.Default.GppBad
        state.reasons.contains(WarningReason.POTENTIALLY_UNSAFE) -> Icons.Default.Warning
        else -> Icons.Default.Block
    }
    val iconTint = when {
        state.reasons.contains(WarningReason.UNSAFE) -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Centred while it fits, scrollable when the details make it taller than the screen.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .enterStagger(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = iconTint,
                )
                Text(
                    text = InterstitialMerge.sentence(state.reasons.first()),
                    style = MaterialTheme.typography.headlineSmall,
                    color = iconTint,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = state.host,
                    style = MaterialTheme.typography.titleMedium,
                )
                for (reason in state.reasons.drop(1)) {
                    Text(
                        text = InterstitialMerge.sentence(reason),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
                if (state.note != null) {
                    Text(
                        text = state.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (state.filterRule != null) {
                    var showDetails by rememberSaveable(state.url) { mutableStateOf(false) }
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("Details")
                            Icon(
                                imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                            )
                        }
                    }
                    if (showDetails) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Filter rule",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    text = state.filterRule,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
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
                TextButton(
                    onClick = onReport,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Report this")
                }
            }
        }
    }
}

@Composable
fun DownloadDialog(
    offer: DownloadOffer,
    onConfirm: (allowMobile: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var riskStep by remember(offer) { mutableStateOf(false) }
    var chosenAllowMobile by remember(offer) { mutableStateOf(false) }

    if (!riskStep) {
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = offer.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sizeText = DownloadOfferText.sizeText(offer.totalBytes)
                    if (sizeText != null) {
                        Text(
                            text = "Size: $sizeText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "From: ${offer.host.ifBlank { "unknown site" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Save to: ${offer.folderLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (offer.waitsForWifi) {
                        Text(
                            text = DownloadOfferText.WIFI_NOTE,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = {
                fun handleConfirm(allowMobile: Boolean) {
                    if (offer.isRisky && !riskStep) {
                        chosenAllowMobile = allowMobile
                        riskStep = true
                    } else {
                        onConfirm(allowMobile)
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (offer.waitsForWifi) {
                        TextButton(onClick = { handleConfirm(true) }) {
                            Text("Download now using mobile data")
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        TextButton(onClick = { handleConfirm(false) }) {
                            Text("Download")
                        }
                    }
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onCancel,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(DownloadOfferText.RISK_TITLE) },
            text = {
                Text(
                    text = RiskyDownload.warningMessage(offer.host.ifBlank { "this site" }),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    TextButton(
                        onClick = { onConfirm(chosenAllowMobile) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Download anyway")
                    }
                }
            },
        )
    }
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
