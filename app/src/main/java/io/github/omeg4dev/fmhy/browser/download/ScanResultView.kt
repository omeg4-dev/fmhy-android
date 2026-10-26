package io.github.omeg4dev.fmhy.browser.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScanResultView(
    result: ScanResult,
    onOpenReport: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLink: Boolean = false,
) {
    val titleText = if (isLink) {
        CheckUiText.linkVerdictTitle(result.verdict)
    } else {
        CheckUiText.verdictTitle(result.verdict)
    }

    val (titleColor, icon) = when (result.verdict) {
        ScanVerdict.DANGEROUS -> MaterialTheme.colorScheme.error to Icons.Default.Warning
        ScanVerdict.SUSPICIOUS -> MaterialTheme.colorScheme.tertiary to Icons.Default.Warning
        ScanVerdict.NO_FLAGS -> MaterialTheme.colorScheme.onSurface to Icons.Default.CheckCircle
        ScanVerdict.UNKNOWN -> MaterialTheme.colorScheme.onSurface to Icons.AutoMirrored.Filled.HelpOutline
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Title line
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = titleColor,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
            )
        }

        // When running
        if (result.isRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = "Analysis running. This can take a few minutes. You'll get a notification when it's done.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // One block per ServiceResult
        for (serviceResult in result.services) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = serviceResult.service.label,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = CheckUiText.serviceLine(serviceResult),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (serviceResult.scannedAt != null) {
                    val dateStr = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
                        .format(Instant.ofEpochMilli(serviceResult.scannedAt).atZone(ZoneId.systemDefault()))
                    Text(
                        text = "Scanned $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (serviceResult.reportUrl != null) {
                    TextButton(
                        onClick = { onOpenReport(serviceResult.reportUrl) },
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text("Full report")
                    }
                }
            }
        }

        // Last line always
        Text(
            text = ScanText.REMINDER,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
