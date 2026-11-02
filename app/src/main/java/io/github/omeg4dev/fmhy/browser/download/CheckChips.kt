package io.github.omeg4dev.fmhy.browser.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CheckChipTone { OK, WARN, DANGER, NEUTRAL, RUNNING }
enum class CheckChipIcon { SHIELD_CHECK, WARNING, SHIELD_X, SHIELD_UNKNOWN, SPINNER, CLOUD_OFF }
enum class CheckChipAction { SHOW_RESULT, UPLOAD, TOO_BIG, RETRY }

data class CheckChip(
    val service: ScanService,
    val icon: CheckChipIcon,
    val tone: CheckChipTone,
    val text: String,          // what the chip says after the service name, e.g. "0/72", "Unknown · Upload"
    val action: CheckChipAction,
)

object CheckChips {
    fun build(
        result: ScanResult,
        readyServices: List<ScanService>,
        fileSize: Long,                       // bytes, 0 if unknown
        maxUploadBytes: (ScanService) -> Long,
    ): List<CheckChip> {
        val chips = mutableListOf<CheckChip>()
        val handledServices = mutableSetOf<ScanService>()

        for (s in result.services) {
            if (handledServices.add(s.service)) {
                chips.add(buildChip(s.service, s, result, readyServices, fileSize, maxUploadBytes))
            }
        }

        for (p in result.pending) {
            if (handledServices.add(p.service)) {
                chips.add(buildChip(p.service, null, result, readyServices, fileSize, maxUploadBytes))
            }
        }

        return chips
    }

    private fun buildChip(
        service: ScanService,
        serviceResult: ServiceResult?,
        result: ScanResult,
        readyServices: List<ScanService>,
        fileSize: Long,
        maxUploadBytes: (ScanService) -> Long,
    ): CheckChip {
        // 1. s.service is in result.pending (analysis running) -> SPINNER, RUNNING, "Analysis running", SHOW_RESULT.
        if (result.pending.any { it.service == service }) {
            return CheckChip(
                service = service,
                icon = CheckChipIcon.SPINNER,
                tone = CheckChipTone.RUNNING,
                text = "Analysis running",
                action = CheckChipAction.SHOW_RESULT,
            )
        }

        // 2. s.error != null
        val error = serviceResult?.error
        if (error != null) {
            return when (error) {
                ScanError.OFFLINE, ScanError.OTHER -> CheckChip(
                    service = service,
                    icon = CheckChipIcon.CLOUD_OFF,
                    tone = CheckChipTone.NEUTRAL,
                    text = "Retry",
                    action = CheckChipAction.RETRY,
                )
                ScanError.BAD_KEY -> CheckChip(
                    service = service,
                    icon = CheckChipIcon.CLOUD_OFF,
                    tone = CheckChipTone.NEUTRAL,
                    text = "Key problem",
                    action = CheckChipAction.SHOW_RESULT,
                )
                ScanError.LIMIT -> CheckChip(
                    service = service,
                    icon = CheckChipIcon.CLOUD_OFF,
                    tone = CheckChipTone.NEUTRAL,
                    text = "Limit reached",
                    action = CheckChipAction.SHOW_RESULT,
                )
                ScanError.TOO_BIG -> CheckChip(
                    service = service,
                    icon = CheckChipIcon.CLOUD_OFF,
                    tone = CheckChipTone.NEUTRAL,
                    text = "Too big",
                    action = CheckChipAction.SHOW_RESULT,
                )
            }
        }

        // 3. verdict == NO_FLAGS, SUSPICIOUS, DANGEROUS
        val verdict = serviceResult?.verdict ?: ScanVerdict.UNKNOWN
        when (verdict) {
            ScanVerdict.NO_FLAGS, ScanVerdict.SUSPICIOUS, ScanVerdict.DANGEROUS -> {
                val (icon, tone) = when (verdict) {
                    ScanVerdict.NO_FLAGS -> CheckChipIcon.SHIELD_CHECK to CheckChipTone.OK
                    ScanVerdict.SUSPICIOUS -> CheckChipIcon.WARNING to CheckChipTone.WARN
                    ScanVerdict.DANGEROUS -> CheckChipIcon.SHIELD_X to CheckChipTone.DANGER
                }
                val text = if (serviceResult?.flagged != null && serviceResult.engines != null && serviceResult.engines > 0) {
                    "${serviceResult.flagged}/${serviceResult.engines}"
                } else {
                    verdict.label
                }
                return CheckChip(
                    service = service,
                    icon = icon,
                    tone = tone,
                    text = text,
                    action = CheckChipAction.SHOW_RESULT,
                )
            }
            ScanVerdict.UNKNOWN -> {
                // 4. verdict == UNKNOWN (error is null here)
                val isReady = service in readyServices
                return when {
                    isReady && fileSize > 0 && fileSize > maxUploadBytes(service) -> {
                        CheckChip(
                            service = service,
                            icon = CheckChipIcon.SHIELD_UNKNOWN,
                            tone = CheckChipTone.NEUTRAL,
                            text = "Unknown · too big",
                            action = CheckChipAction.TOO_BIG,
                        )
                    }
                    isReady -> {
                        CheckChip(
                            service = service,
                            icon = CheckChipIcon.SHIELD_UNKNOWN,
                            tone = CheckChipTone.NEUTRAL,
                            text = "Unknown · Upload",
                            action = CheckChipAction.UPLOAD,
                        )
                    }
                    else -> {
                        CheckChip(
                            service = service,
                            icon = CheckChipIcon.SHIELD_UNKNOWN,
                            tone = CheckChipTone.NEUTRAL,
                            text = "Unknown",
                            action = CheckChipAction.SHOW_RESULT,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckChipRow(
    chips: List<CheckChip>,
    onClick: (CheckChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Chips are a dense row: drop the 48 dp touch-target padding that would stretch the wrap gap.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        BoxWithConstraints(modifier = modifier) {
            val maxChipWidth = maxWidth
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (chip in chips) {
                    CheckChipItem(
                        chip = chip,
                        onClick = { onClick(chip) },
                        maxWidth = maxChipWidth,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckChipItem(
    chip: CheckChip,
    onClick: () -> Unit,
    maxWidth: Dp,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (containerColor, contentColor) = chipColors(chip.tone, isDark, MaterialTheme.colorScheme)

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        onClick = onClick,
        modifier = Modifier
            .defaultMinSize(minHeight = 32.dp)
            .widthIn(max = maxWidth),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (chip.icon) {
                CheckChipIcon.SHIELD_CHECK -> Icon(
                    imageVector = Icons.Filled.GppGood,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
                CheckChipIcon.WARNING -> Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
                CheckChipIcon.SHIELD_X -> Icon(
                    imageVector = Icons.Filled.GppBad,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
                CheckChipIcon.SHIELD_UNKNOWN -> Icon(
                    imageVector = Icons.Filled.GppMaybe,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
                CheckChipIcon.CLOUD_OFF -> Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
                CheckChipIcon.SPINNER -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${chip.service.label} · ${chip.text}",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

private fun chipColors(tone: CheckChipTone, isDark: Boolean, colorScheme: ColorScheme): Pair<Color, Color> {
    return when (tone) {
        CheckChipTone.OK -> if (isDark) {
            Color(0xFF1D3B26) to Color(0xFF9BE0AC)
        } else {
            Color(0xFFDDF3E0) to Color(0xFF14532D)
        }
        CheckChipTone.WARN -> if (isDark) {
            Color(0xFF46330C) to Color(0xFFFFD27A)
        } else {
            Color(0xFFFFEFCC) to Color(0xFF6B4300)
        }
        CheckChipTone.DANGER -> if (isDark) {
            Color(0xFF5A1E1A) to Color(0xFFFFB4AB)
        } else {
            Color(0xFFFCDEDB) to Color(0xFF8C1A12)
        }
        CheckChipTone.RUNNING -> if (isDark) {
            Color(0xFF1B3556) to Color(0xFFA9CCFF)
        } else {
            Color(0xFFDBEAFE) to Color(0xFF0B4A8F)
        }
        CheckChipTone.NEUTRAL -> {
            colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
        }
    }
}
