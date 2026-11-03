package io.github.omeg4dev.fmhy.browser

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.browser.download.CheckChipTone
import io.github.omeg4dev.fmhy.browser.download.ScanError
import io.github.omeg4dev.fmhy.browser.download.ScanResult
import io.github.omeg4dev.fmhy.browser.download.ScanService
import io.github.omeg4dev.fmhy.browser.download.ScanText
import io.github.omeg4dev.fmhy.browser.download.ScanVerdict
import io.github.omeg4dev.fmhy.browser.download.VtMark
import io.github.omeg4dev.fmhy.browser.download.chipColors
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/**
 * "Check with VirusTotal" block of the site menu (the sheet behind the site icon).
 * Idle button, running chip, result chip, or the remembered "No flags" mark.
 */
@Composable
internal fun VtCheckSection(
    isPrivate: Boolean,
    vtMark: VtMark?,
    vtReady: Boolean,
    onCheckVt: suspend () -> ScanResult?,
    onSetupVt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var localResult by remember { mutableStateOf<ScanResult?>(null) }
    val runCheck: () -> Unit = {
        isRunning = true
        scope.launch {
            localResult = onCheckVt()
            isRunning = false
        }
    }
    fun dateText(at: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
    fun flagsText(label: String, flagged: Int?, engines: Int?) =
        if (engines != null) "$label · ${flagged ?: 0}/$engines" else label

    Column(modifier = modifier, verticalArrangement = spacedBy(6.dp)) {
        val result = localResult
        val vt = result?.services?.firstOrNull { it.service == ScanService.VIRUSTOTAL }
        when {
            isRunning -> {
                VtChip(CheckChipTone.RUNNING, "Checking with VirusTotal…") { tint ->
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = tint)
                }
            }
            result != null && (vt == null || vt.error != null) -> {
                val message = ScanText.error(ScanService.VIRUSTOTAL, vt?.error ?: ScanError.OTHER)
                VtChip(CheckChipTone.NEUTRAL, message)
                TextButton(onClick = runCheck) { Text("Try again") }
            }
            vt != null -> when (vt.verdict) {
                ScanVerdict.NO_FLAGS -> {
                    VtChip(CheckChipTone.OK, flagsText("No flags", vt.flagged, vt.engines), icon = chipIcon(Icons.Filled.GppGood))
                    VtNote(
                        if (isPrivate) "Checked with VirusTotal just now (not remembered in private tabs)"
                        else "Checked with VirusTotal on ${dateText(result?.checkedAt ?: System.currentTimeMillis())}",
                    )
                }
                ScanVerdict.SUSPICIOUS -> {
                    VtChip(CheckChipTone.WARN, flagsText("Suspicious", vt.flagged, vt.engines), icon = chipIcon(Icons.Filled.Warning))
                    VtNote("Not remembered as safe.")
                }
                ScanVerdict.DANGEROUS -> {
                    VtChip(CheckChipTone.DANGER, flagsText("Dangerous", vt.flagged, vt.engines), icon = chipIcon(Icons.Filled.GppBad))
                    VtNote("Not remembered as safe.")
                }
                ScanVerdict.UNKNOWN -> {
                    VtChip(CheckChipTone.NEUTRAL, "Not known to VirusTotal yet", icon = chipIcon(Icons.Filled.GppMaybe))
                    VtNote("Not remembered.")
                }
            }
            vtMark != null -> {
                VtChip(CheckChipTone.OK, flagsText("No flags", vtMark.flagged, vtMark.engines), icon = chipIcon(Icons.Filled.GppGood))
                VtNote("Checked with VirusTotal on ${dateText(vtMark.checkedAt)}")
            }
            else -> {
                FilledTonalButton(onClick = if (vtReady) runCheck else onSetupVt) {
                    Text(if (vtReady) "Check with VirusTotal" else "Set up VirusTotal key")
                }
                VtNote("Sends only the site's domain to VirusTotal.")
            }
        }
    }
}

private fun chipIcon(vector: ImageVector): @Composable (Color) -> Unit = { tint ->
    Icon(imageVector = vector, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
}

@Composable
private fun VtNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun VtChip(
    tone: CheckChipTone,
    text: String,
    icon: (@Composable (contentColor: Color) -> Unit)? = null,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (containerColor, contentColor) = chipColors(tone, isDark, MaterialTheme.colorScheme)
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        modifier = Modifier.defaultMinSize(minHeight = 32.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                icon(contentColor)
                Spacer(Modifier.width(4.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = contentColor)
        }
    }
}
