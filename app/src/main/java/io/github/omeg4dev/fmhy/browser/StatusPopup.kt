package io.github.omeg4dev.fmhy.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Segment
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.text.font.FontWeight.Companion.SemiBold
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.omeg4dev.fmhy.browser.download.CheckChipTone
import io.github.omeg4dev.fmhy.browser.download.ScanError
import io.github.omeg4dev.fmhy.browser.download.ScanResult
import io.github.omeg4dev.fmhy.browser.download.ScanService
import io.github.omeg4dev.fmhy.browser.download.ScanText
import io.github.omeg4dev.fmhy.browser.download.ScanVerdict
import io.github.omeg4dev.fmhy.browser.download.VtMark
import io.github.omeg4dev.fmhy.browser.download.chipColors
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.safety.readableReason
import io.github.omeg4dev.fmhy.ui.common.Motion
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
internal fun statusGlyphVector(glyph: StatusGlyph): ImageVector = when (glyph) {
    StatusGlyph.STAR -> Icons.Filled.Star
    StatusGlyph.VERIFIED -> Icons.Filled.Verified
    StatusGlyph.INFO -> Icons.Outlined.Info
    StatusGlyph.WARNING -> Icons.Filled.GppMaybe
    StatusGlyph.ERROR -> Icons.Filled.GppBad
}

@Composable
internal fun statusToneColor(tone: StatusTone): Color = when (tone) {
    StatusTone.PRIMARY -> MaterialTheme.colorScheme.primary
    StatusTone.TERTIARY -> MaterialTheme.colorScheme.tertiary
    StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    StatusTone.ERROR -> MaterialTheme.colorScheme.error
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StatusPopup(
    state: SiteBarState,
    sectionTitle: String?,
    preferAbove: Boolean,
    onDismiss: () -> Unit,
    onShowInSection: (Entry) -> Unit,
    onEntryDetails: (Entry) -> Unit,
    host: String? = null,
    isPrivate: Boolean = false,
    vtMark: VtMark? = null,
    vtReady: Boolean = false,
    onCheckVt: suspend () -> ScanResult? = { null },
    onSetupVt: () -> Unit = {},
) {
    val density = LocalDensity.current
    val marginPx = with(density) { 16.dp.roundToPx() }
    val gapPx = with(density) { 6.dp.roundToPx() }
    val caretHalfPx = with(density) { 8.dp.roundToPx() }

    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var localResult by remember { mutableStateOf<ScanResult?>(null) }

    var placement by remember { mutableStateOf<StatusPopupPlacement?>(null) }

    val positionProvider = remember(preferAbove, marginPx, gapPx, caretHalfPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val p = statusPopupPlacement(
                    anchorLeft = anchorBounds.left,
                    anchorTop = anchorBounds.top,
                    anchorRight = anchorBounds.right,
                    anchorBottom = anchorBounds.bottom,
                    windowWidth = windowSize.width,
                    windowHeight = windowSize.height,
                    popupWidth = popupContentSize.width,
                    popupHeight = popupContentSize.height,
                    preferAbove = preferAbove,
                    marginPx = marginPx,
                    gapPx = gapPx,
                    caretHalfPx = caretHalfPx,
                )
                placement = p
                return IntOffset(p.x, p.y)
            }
        }
    }

    val configuration = LocalConfiguration.current
    val popupWidthDp = minOf(configuration.screenWidthDp - 32, 360).dp
    val maxHeightDp = (configuration.screenHeightDp * 0.6f).dp

    val spec = statusIconSpec(state.kind)
    val tone = statusToneColor(spec.tone)
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    val caretWidthDp = 16.dp
    val caretHeightDp = 8.dp

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val currentPlacement = placement
        val above = currentPlacement?.above ?: preferAbove

        val transitionState = remember { MutableTransitionState(false).apply { targetState = true } }
        val pivotX = currentPlacement?.let {
            val popupWidthPx = with(density) { popupWidthDp.toPx() }
            if (popupWidthPx > 0f) (it.caretX.toFloat() / popupWidthPx).coerceIn(0f, 1f) else 0.5f
        } ?: 0.5f
        val transformOrigin = TransformOrigin(pivotX, if (above) 1f else 0f)

        AnimatedVisibility(
            visibleState = transitionState,
            enter = fadeIn(Motion.enter()) + scaleIn(initialScale = 0.92f, transformOrigin = transformOrigin),
        ) {
            Column {
                val caretXPx = currentPlacement?.caretX

                if (!above) {
                    // Caret pointing UP (popup is below the icon)
                    Canvas(
                        modifier = Modifier
                            .width(popupWidthDp)
                            .height(caretHeightDp),
                    ) {
                        if (caretXPx != null) {
                            val cx = caretXPx.toFloat()
                            val hw = caretWidthDp.toPx() / 2f
                            val h = caretHeightDp.toPx()
                            val path = Path().apply {
                                moveTo(cx, 0f)
                                lineTo(cx - hw, h)
                                lineTo(cx + hw, h)
                                close()
                            }
                            drawPath(path, surfaceColor)
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = surfaceColor,
                    shadowElevation = 6.dp,
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .width(popupWidthDp)
                            .heightIn(max = maxHeightDp)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = spacedBy(12.dp),
                    ) {
                        // Verdict band
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .background(tone.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .semantics(mergeDescendants = true) { heading() },
                            verticalAlignment = CenterVertically,
                            horizontalArrangement = spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = statusGlyphVector(spec.glyph),
                                contentDescription = null,
                                tint = tone,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = statusTitle(state.kind),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = SemiBold,
                                color = tone,
                            )
                        }

                        // Kind-specific text
                        when (state.kind) {
                            SiteBarKind.NOT_ON_FMHY -> {
                                Text(
                                    "This site is not on the FMHY list. That does not mean it is unsafe.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            SiteBarKind.UNSAFE,
                            SiteBarKind.POTENTIALLY_UNSAFE -> {
                                readableReason(state.verdict.reason)?.let { reason ->
                                    Text(
                                        reason,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            else -> {}
                        }

                        // Places (matches)
                        state.verdict.matches.forEachIndexed { index, entry ->
                            if (index > 0) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }

                            Column(verticalArrangement = spacedBy(2.dp)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = placePath(entry, sectionTitle.takeIf { index == 0 }),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            FlowRow(
                                horizontalArrangement = spacedBy(8.dp),
                                verticalArrangement = spacedBy(8.dp),
                            ) {
                                Button(onClick = { onShowInSection(entry) }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Segment,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Show in section")
                                }
                                FilledTonalButton(onClick = { onEntryDetails(entry) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Entry details")
                                }
                            }
                        }

                        if (host != null && state.kind != SiteBarKind.FMHY_SITE && state.kind != SiteBarKind.UNSAFE) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )

                            Column(verticalArrangement = spacedBy(6.dp)) {
                                if (isRunning) {
                                    VtChip(
                                        tone = CheckChipTone.RUNNING,
                                        text = "Checking with VirusTotal…",
                                        icon = { tint ->
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = tint,
                                            )
                                        },
                                    )
                                    FilledTonalButton(
                                        onClick = {},
                                        enabled = false,
                                    ) {
                                        Text("Check with VirusTotal")
                                    }
                                } else if (localResult != null) {
                                    val vt = localResult?.services?.firstOrNull { it.service == ScanService.VIRUSTOTAL }
                                    if (vt?.error != null) {
                                        VtChip(
                                            tone = CheckChipTone.NEUTRAL,
                                            text = ScanText.error(ScanService.VIRUSTOTAL, vt.error),
                                        )
                                        TextButton(
                                            onClick = {
                                                isRunning = true
                                                scope.launch {
                                                    localResult = onCheckVt()
                                                    isRunning = false
                                                }
                                            },
                                        ) {
                                            Text("Try again")
                                        }
                                    } else if (vt != null) {
                                        when (vt.verdict) {
                                            ScanVerdict.NO_FLAGS -> {
                                                val flagText = if (vt.engines != null) "No flags · ${vt.flagged ?: 0}/${vt.engines}" else "No flags"
                                                val lineText = if (isPrivate) {
                                                    "Checked with VirusTotal just now (not remembered in private tabs)"
                                                } else {
                                                    val formattedDate = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(localResult?.checkedAt ?: System.currentTimeMillis()))
                                                    "Checked with VirusTotal on $formattedDate"
                                                }
                                                VtChip(
                                                    tone = CheckChipTone.OK,
                                                    text = flagText,
                                                    icon = { tint ->
                                                        Icon(
                                                            imageVector = Icons.Filled.GppGood,
                                                            contentDescription = null,
                                                            tint = tint,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    },
                                                )
                                                Text(
                                                    text = lineText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            ScanVerdict.SUSPICIOUS -> {
                                                val flagText = if (vt.engines != null) "Suspicious · ${vt.flagged ?: 0}/${vt.engines}" else "Suspicious"
                                                VtChip(
                                                    tone = CheckChipTone.WARN,
                                                    text = flagText,
                                                    icon = { tint ->
                                                        Icon(
                                                            imageVector = Icons.Filled.Warning,
                                                            contentDescription = null,
                                                            tint = tint,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    },
                                                )
                                                Text(
                                                    text = "Not remembered as safe.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            ScanVerdict.DANGEROUS -> {
                                                val flagText = if (vt.engines != null) "Dangerous · ${vt.flagged ?: 0}/${vt.engines}" else "Dangerous"
                                                VtChip(
                                                    tone = CheckChipTone.DANGER,
                                                    text = flagText,
                                                    icon = { tint ->
                                                        Icon(
                                                            imageVector = Icons.Filled.GppBad,
                                                            contentDescription = null,
                                                            tint = tint,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    },
                                                )
                                                Text(
                                                    text = "Not remembered as safe.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            ScanVerdict.UNKNOWN -> {
                                                VtChip(
                                                    tone = CheckChipTone.NEUTRAL,
                                                    text = "Not known to VirusTotal yet",
                                                    icon = { tint ->
                                                        Icon(
                                                            imageVector = Icons.Filled.GppMaybe,
                                                            contentDescription = null,
                                                            tint = tint,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    },
                                                )
                                                Text(
                                                    text = "Not remembered.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    } else {
                                        VtChip(
                                            tone = CheckChipTone.NEUTRAL,
                                            text = ScanText.error(ScanService.VIRUSTOTAL, ScanError.OTHER),
                                        )
                                        TextButton(
                                            onClick = {
                                                isRunning = true
                                                scope.launch {
                                                    localResult = onCheckVt()
                                                    isRunning = false
                                                }
                                            },
                                        ) {
                                            Text("Try again")
                                        }
                                    }
                                } else if (vtMark != null) {
                                    val flagText = if (vtMark.engines != null) "No flags · ${vtMark.flagged ?: 0}/${vtMark.engines}" else "No flags"
                                    val formattedDate = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(vtMark.checkedAt))
                                    VtChip(
                                        tone = CheckChipTone.OK,
                                        text = flagText,
                                        icon = { tint ->
                                            Icon(
                                                imageVector = Icons.Filled.GppGood,
                                                contentDescription = null,
                                                tint = tint,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                    )
                                    Text(
                                        text = "Checked with VirusTotal on $formattedDate",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    if (vtReady) {
                                        FilledTonalButton(
                                            onClick = {
                                                isRunning = true
                                                scope.launch {
                                                    localResult = onCheckVt()
                                                    isRunning = false
                                                }
                                            },
                                        ) {
                                            Text("Check with VirusTotal")
                                        }
                                    } else {
                                        FilledTonalButton(
                                            onClick = onSetupVt,
                                        ) {
                                            Text("Set up VirusTotal key")
                                        }
                                    }
                                    Text(
                                        text = "Sends only the site's domain to VirusTotal.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (above) {
                    // Caret pointing DOWN (popup is above the icon)
                    Canvas(
                        modifier = Modifier
                            .width(popupWidthDp)
                            .height(caretHeightDp),
                    ) {
                        if (caretXPx != null) {
                            val cx = caretXPx.toFloat()
                            val hw = caretWidthDp.toPx() / 2f
                            val h = caretHeightDp.toPx()
                            val path = Path().apply {
                                moveTo(cx - hw, 0f)
                                lineTo(cx + hw, 0f)
                                lineTo(cx, h)
                                close()
                            }
                            drawPath(path, surfaceColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VtChip(
    tone: CheckChipTone,
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable (contentColor: Color) -> Unit)? = null,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (containerColor, contentColor) = chipColors(tone, isDark, MaterialTheme.colorScheme)

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        modifier = modifier.defaultMinSize(minHeight = 32.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = CenterVertically,
        ) {
            if (icon != null) {
                icon(contentColor)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}
