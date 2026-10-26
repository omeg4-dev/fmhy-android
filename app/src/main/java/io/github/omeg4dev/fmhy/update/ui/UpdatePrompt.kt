package io.github.omeg4dev.fmhy.update.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.update.ReleaseInfo
import io.github.omeg4dev.fmhy.update.UpdateController
import io.github.omeg4dev.fmhy.update.UpdateState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdatePrompt(
    controller: UpdateController,
    modifier: Modifier = Modifier,
) {
    if (!controller.enabled) return

    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (val s = state) {
        is UpdateState.Idle, is UpdateState.Checking, is UpdateState.UpToDate -> {
            if (s is UpdateState.UpToDate && s.manual) {
                Card(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "You have the latest version",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = controller::dismissMessage) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        is UpdateState.CheckFailed -> {
            Card(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = s.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = controller::dismissMessage) {
                        Text("OK")
                    }
                }
            }
        }

        is UpdateState.Available -> {
            var showNotes by remember(s.release.version) { mutableStateOf(false) }
            Card(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .animateContentSize(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Version ${s.release.version} is available",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    AnimatedVisibility(visible = showNotes) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        ) {
                            if (s.release.notes != null) {
                                val lines = s.release.notes.lines()
                                lines.forEach { line ->
                                    if (line.isNotBlank()) {
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 2.dp),
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "No summary for this release.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = {
                                        openUrl(context, s.release.pageUrl)
                                    },
                                ) {
                                    Text("Open release page")
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { showNotes = !showNotes },
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text("What's new in this version")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Button(onClick = { controller.install() }) {
                            Text("Install")
                        }
                        TextButton(onClick = controller::later) {
                            Text("Later")
                        }
                        TextButton(onClick = controller::skip) {
                            Text("Skip this version")
                        }
                    }
                }
            }
        }

        is UpdateState.WaitingForWifi -> {
            Card(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Waiting for Wi-Fi. The update will download on Wi-Fi.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Button(onClick = { controller.install(overrideMobileData = true) }) {
                            Text("Download now using mobile data")
                        }
                        TextButton(onClick = controller::later) {
                            Text("Later")
                        }
                    }
                }
            }
        }

        is UpdateState.Downloading -> {
            Card(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Downloading version ${s.release.version}…",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (s.fraction != null) {
                        LinearProgressIndicator(
                            progress = { s.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${(s.fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        is UpdateState.DownloadFailed -> {
            Card(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = s.reason,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Button(onClick = controller::retry) {
                            Text("Retry")
                        }
                        TextButton(onClick = controller::later) {
                            Text("Later")
                        }
                    }
                }
            }
        }

        is UpdateState.ReadyToInstall -> {
            Card(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Ready to install",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { controller.install() }) {
                        Text("Install")
                    }
                }
            }
        }

        is UpdateState.SignatureMismatch -> {
            Card(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "The system refused this update because it doesn't match the installed app. Builds older than 0.4.0 were signed with a different key and can't be updated this way. Install the new version from the release page instead.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Button(
                            onClick = {
                                openUrl(context, s.release.pageUrl)
                            },
                        ) {
                            Text("Open release page")
                        }
                        TextButton(onClick = controller::dismissMessage) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
