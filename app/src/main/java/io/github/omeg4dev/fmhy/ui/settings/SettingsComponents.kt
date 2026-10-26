package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.ui.shell.JobRowUi
import com.materialkolor.hct.Hct
import io.github.omeg4dev.fmhy.theme.calloutColorsForSeed
import io.github.omeg4dev.fmhy.theme.paletteColorScheme
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import kotlin.math.roundToInt

@Composable
fun SettingsGroupHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun SettingsSection(
    title: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }
        if (!footer.isNullOrEmpty()) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

@Composable
fun SettingsDivider(
    modifier: Modifier = Modifier,
    hasIcon: Boolean = false,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = if (hasIcon) 56.dp else 16.dp, end = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
fun RowIcon(
    icon: ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun SwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    settingId: String? = null,
    onChange: (Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = summary?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = icon?.let { { RowIcon(icon = it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .settingHighlight(settingId)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .then(if (!enabled) Modifier.alpha(0.38f) else Modifier)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.toggle(it)
                    onChange(it)
                },
            ),
    )
}

@Composable
fun NavRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    badge: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    settingId: String? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    badge()
                }
            }
        },
        supportingContent = summary?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = icon?.let {
            { RowIcon(icon = it) }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .settingHighlight(settingId)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .then(if (!enabled) Modifier.alpha(0.38f) else Modifier)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    onClick()
                },
            ),
    )
}

@Composable
fun InfoRow(
    title: String,
    value: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    settingId: String? = null,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = icon?.let { { RowIcon(icon = it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .settingHighlight(settingId)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
    )
}

@Composable
fun JobProgressRow(
    row: JobRowUi,
    modifier: Modifier = Modifier,
) {
    val text = if (row.fraction != null) "${row.label} · ${row.percent}%" else row.label
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (row.fraction != null) {
            LinearProgressIndicator(
                progress = { row.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clearAndSetSemantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(row.fraction, 0f..1f)
                        stateDescription = text
                    },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clearAndSetSemantics {
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                        stateDescription = text
                    },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ButtonRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    settingId: String? = null,
) {
    Box(
        modifier = modifier
            .settingHighlight(settingId)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        FilledTonalButton(
            onClick = {
                onClick()
            },
            enabled = enabled && !loading,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(text)
        }
    }
}

@Composable
fun ExpandableSection(
    title: String = "Advanced",
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    settingIds: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focus = LocalSettingFocus.current
    val shouldStartExpanded = initiallyExpanded || (focus != null && focus in settingIds)
    var internalExpanded by remember { mutableStateOf(shouldStartExpanded) }
    val isExpanded = expanded ?: internalExpanded
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = Motion.standard(),
        label = "chevron_rotation",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = summary?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable {
                    if (onExpandedChange != null) {
                        onExpandedChange(!isExpanded)
                    } else {
                        internalExpanded = !internalExpanded
                    }
                },
        )
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = Motion.enter()) + fadeIn(animationSpec = Motion.enter()),
            exit = shrinkVertically(animationSpec = Motion.exit()) + fadeOut(animationSpec = Motion.exit()),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }
    }
}

data class OptionCardItem<T>(
    val value: T,
    val title: String,
    val subtitle: String? = null,
    val isRecommended: Boolean = false,
    val icon: ImageVector? = null,
)

@Composable
fun OptionCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isRecommended: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = {
                    haptics.tick()
                    onClick()
                },
            )
            .then(if (!enabled) Modifier.alpha(0.38f) else Modifier),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isRecommended) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ) {
                            Text(
                                text = "Recommended",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun <T> OptionCardRow(
    options: List<OptionCardItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    enabled: Boolean = true,
    settingId: String? = null,
) {
    Column(
        modifier = modifier
            .settingHighlight(settingId)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
        }
        options.forEach { item ->
            OptionCard(
                title = item.title,
                subtitle = item.subtitle,
                selected = selected == item.value,
                isRecommended = item.isRecommended,
                icon = item.icon,
                enabled = enabled,
                onClick = { onSelect(item.value) },
            )
        }
    }
}

@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    settingId: String? = null,
) {
    val haptics = rememberHaptics()
    val rowModifier = modifier.settingHighlight(settingId)
    if (options.size <= 3) {
        Column(
            modifier = rowModifier
                .fillMaxWidth()
                .then(if (!enabled) Modifier.alpha(0.38f) else Modifier)
        ) {
            ListItem(
                headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = selected == value,
                        enabled = enabled,
                        onClick = {
                            haptics.tick()
                            onSelect(value)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        label = { Text(label) },
                    )
                }
            }
        }
    } else {
        var showDialog by remember { mutableStateOf(false) }
        val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: ""

        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(selectedLabel) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = rowModifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .then(if (!enabled) Modifier.alpha(0.38f) else Modifier)
                .clickable(enabled = enabled) {
                    showDialog = true
                },
        )

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(title) },
                text = {
                    Column {
                        options.forEach { (value, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .selectable(
                                        selected = selected == value,
                                        role = Role.RadioButton,
                                        onClick = {
                                            haptics.tick()
                                            onSelect(value)
                                            showDialog = false
                                        }
                                    )
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected == value,
                                    onClick = null,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueLabel: String? = null,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    settingId: String? = null,
) {
    val haptics = rememberHaptics()
    var sliderValue by remember(value) { mutableFloatStateOf(value) }

    Column(
        modifier = modifier
            .settingHighlight(settingId)
            .fillMaxWidth()
            .then(if (!enabled) Modifier.alpha(0.38f) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (valueLabel != null) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Slider(
            value = sliderValue,
            enabled = enabled,
            onValueChange = {
                if ((it * 100).roundToInt() != (sliderValue * 100).roundToInt()) {
                    haptics.tick()
                }
                sliderValue = it
                onValueChange(it)
            },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
fun CustomColorDialog(
    initialSeed: Int,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val haptics = rememberHaptics()
    val initialHct = remember(initialSeed) { Hct.fromInt(initialSeed) }
    var hue by remember { mutableFloatStateOf(initialHct.hue.toFloat()) }
    var chroma by remember { mutableFloatStateOf(initialHct.chroma.toFloat().coerceIn(20f, 100f)) }

    val currentSeed = remember(hue, chroma) {
        Hct.from(hue.toDouble(), chroma.toDouble(), 50.0).toInt()
    }
    val previewScheme = remember(currentSeed, isDark) {
        paletteColorScheme(currentSeed, isDark)
    }
    val previewCallout = remember(currentSeed, isDark) {
        calloutColorsForSeed(currentSeed, isDark)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom colour") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(previewScheme.primary, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(previewCallout.tip.accent, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                }

                Text("Hue", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = hue,
                    onValueChange = {
                        if (it.roundToInt() != hue.roundToInt()) {
                            haptics.tick()
                        }
                        hue = it
                    },
                    valueRange = 0f..360f,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                Text("Colourfulness", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = chroma,
                    onValueChange = {
                        if (it.roundToInt() != chroma.roundToInt()) {
                            haptics.tick()
                        }
                        chroma = it
                    },
                    valueRange = 20f..100f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(currentSeed)
                onDismiss()
            }) {
                Text("Use colour")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
