package io.github.omeg4dev.fmhy.ui.check

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.safety.SafeGuard
import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import io.github.omeg4dev.fmhy.data.safety.Verdict
import io.github.omeg4dev.fmhy.ui.common.EntryRow
import java.net.URI

@Composable
fun VerdictCard(
    verdict: Verdict,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit,
    onCopy: (() -> Unit)? = null,
    onOpenInFmhy: (() -> Unit)? = null,
    onShowSnackbar: (String) -> Unit = {},
    onCheckHost: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current

    // Colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outline
    val amberStarredColor = Color(0xFFFFC14D)
    val warningOrange = remember(errorColor, tertiaryColor) {
        Color(0xFFF57C00)
    }

    val (iconVector, iconTint) = when (verdict.status) {
        SafetyStatus.FMHY_SITE -> Icons.Default.Verified to primaryColor
        SafetyStatus.STARRED -> Icons.Default.Star to amberStarredColor
        SafetyStatus.LISTED -> Icons.Default.CheckCircle to secondaryColor
        SafetyStatus.DOMAIN_LISTED -> Icons.Default.Domain to secondaryColor
        SafetyStatus.POTENTIALLY_UNSAFE -> Icons.Default.Warning to warningOrange
        SafetyStatus.UNSAFE -> Icons.Default.Dangerous to errorColor
        SafetyStatus.SEARCH_ENGINE -> Icons.Default.Search to outlineColor
        SafetyStatus.NO_DATA -> Icons.AutoMirrored.Filled.HelpOutline to outlineColor
        SafetyStatus.INVALID -> Icons.Default.LinkOff to outlineColor
    }

    val title = verdictTitle(verdict.status)
    val body = verdictBody(verdict)
    val checkedHost = verdict.host ?: verdict.input
    val urlToOpen = if (verdict.input.contains("://")) verdict.input else verdict.normalizedUrl ?: "https://${verdict.input}"

    val inputHasPath = remember(urlToOpen) {
        try {
            val p = URI(urlToOpen).path
            !p.isNullOrEmpty() && p != "/"
        } catch (_: Exception) {
            false
        }
    }

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top row: Status icon (40dp), title and body
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Checked host in monospace
            if (checkedHost.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = checkedHost,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Lookalikes warning block (for NO_DATA and DOMAIN_LISTED)
            if (verdict.lookalikes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                val firstLookalike = verdict.lookalikes.first()
                val firstHost = SafeGuard.extractHostFromUrl(firstLookalike.url) ?: firstLookalike.url
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Similar names on FMHY \u2014 ${firstLookalike.name} ($firstHost). Fake copies of popular sites are common; use the listed address.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        verdict.lookalikes.forEach { lookalikeEntry ->
                            EntryRow(
                                entry = lookalikeEntry,
                                onOpenUrl = onOpenUrl,
                                onShowSnackbar = onShowSnackbar,
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f),
                            )
                        }
                    }
                }
            }

            // Matched entries
            if (verdict.matches.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                val headerText = if (verdict.status == SafetyStatus.DOMAIN_LISTED) {
                    "Listed pages on this domain"
                } else {
                    "Where it's listed"
                }
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))

                var showAllMatches by remember { mutableStateOf(false) }
                val displayedMatches = if (showAllMatches) verdict.matches else verdict.matches.take(5)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    displayedMatches.forEach { matchEntry ->
                        EntryRow(
                            entry = matchEntry,
                            onOpenUrl = onOpenUrl,
                            onShowSnackbar = onShowSnackbar,
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }

                if (verdict.matches.size > 5 && !showAllMatches) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { showAllMatches = true }) {
                        Text("Show all ${verdict.matches.size}")
                    }
                }
            }

            // "Check just <host>" button if a path was given on NO_DATA or DOMAIN_LISTED
            val targetHost = verdict.host
            if (onCheckHost != null && !targetHost.isNullOrBlank() && inputHasPath && verdict.sharedHostListedCount == 0 &&
                (verdict.status == SafetyStatus.NO_DATA || verdict.status == SafetyStatus.DOMAIN_LISTED)
            ) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onCheckHost(targetHost) }) {
                    Text("Check just $targetHost")
                }
            }

            // Buttons: Open and Copy (and optional Open in FMHY)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (verdict.status != SafetyStatus.INVALID) {
                    Button(onClick = { onOpenUrl(urlToOpen) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Open")
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (onCopy != null) {
                            onCopy()
                        } else {
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("URL", urlToOpen)
                                clipboard?.setPrimaryClip(clip)
                                onShowSnackbar("Copied to clipboard")
                            } catch (_: Exception) {}
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }

                if (onOpenInFmhy != null) {
                    FilledTonalButton(onClick = onOpenInFmhy) {
                        Text("Open in FMHY")
                    }
                }
            }

            // Footer text
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Based on FMHY's lists and FMHY SafeGuard",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
