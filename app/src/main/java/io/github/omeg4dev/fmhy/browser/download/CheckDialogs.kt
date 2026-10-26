package io.github.omeg4dev.fmhy.browser.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UploadWarningDialog(
    service: ScanService,
    onUpload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload for analysis?") },
        text = {
            Text("${service.label} doesn't know this file yet. Uploading sends the whole file. Uploaded files may be shared with the service's security partners and researchers, so don't upload private files. New analyses can take a few minutes; you'll get a notification when it's done.")
        },
        confirmButton = {
            TextButton(onClick = {
                onUpload()
                onDismiss()
            }) {
                Text("Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun KeyPromptDialog(
    onSetUp: () -> Unit,
    onNotNow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text("Check risky files automatically?") },
        text = {
            Text("Automatic checks need a free VirusTotal or Hybrid Analysis key. Until you add one, nothing is sent.")
        },
        confirmButton = {
            TextButton(onClick = {
                onSetUp()
                onNotNow()
            }) {
                Text("Set up a key")
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text("Not now")
            }
        },
    )
}

@Composable
fun ChecksExplainDialog(
    onOk: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onOk,
        title = { Text("What file checks send") },
        text = {
            Text("Link checks send the web address, only when you tap “Check link first”. File checks send the file's fingerprint (a SHA-256 hash), not the file. A file is only uploaded after you agree. Automatic checks of risky files (APKs, executables, scripts) send only the fingerprint.")
        },
        confirmButton = {
            TextButton(onClick = onOk) {
                Text("OK")
            }
        },
    )
}

@Composable
fun UploadTooBigDialog(
    service: ScanService,
    onOpenWebsite: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File too big") },
        text = {
            Text(ScanText.error(service, ScanError.TOO_BIG))
        },
        confirmButton = {
            TextButton(onClick = {
                onOpenWebsite()
                onDismiss()
            }) {
                Text("Open website")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun SetUpChecksDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set up file checks") },
        text = {
            Text("Turn on VirusTotal or Hybrid Analysis and add a free key in settings to check files.")
        },
        confirmButton = {
            TextButton(onClick = {
                onOpenSettings()
                onDismiss()
            }) {
                Text("Open settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        },
    )
}

@Composable
fun DangerousActionConfirmDialog(
    isInstall: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isInstall) "Install a dangerous app?" else "Open a dangerous file?")
        },
        text = {
            Text(
                if (isInstall) {
                    "This app was flagged as dangerous. Installing it can harm your phone."
                } else {
                    "This file was flagged as dangerous. Opening it can harm your phone."
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(if (isInstall) "Install anyway" else "Open anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun DangerousKeepConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep a dangerous file?") },
        text = {
            Text("This file was flagged as dangerous. It will be moved to your download folder.")
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Keep anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun CheckChooserDialog(
    onChoose: (List<ScanService>) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check with") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "VirusTotal",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onChoose(listOf(ScanService.VIRUSTOTAL))
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                )
                Text(
                    text = "Hybrid Analysis",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onChoose(listOf(ScanService.HYBRID_ANALYSIS))
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                )
                Text(
                    text = "Both",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onChoose(listOf(ScanService.VIRUSTOTAL, ScanService.HYBRID_ANALYSIS))
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
