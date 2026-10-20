package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.materialkolor.hct.Hct
import io.github.omeg4dev.fmhy.theme.seedColorScheme
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
        modifier = modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
fun SwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
        modifier = modifier
            .then(if (!enabled) Modifier.alpha(0.38f) else Modifier)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.tick()
                    onChange(it)
                },
            ),
    )
}

@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    if (options.size <= 3) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .then(if (!enabled) Modifier.alpha(0.38f) else Modifier)
        ) {
            ListItem(
                headlineContent = { Text(title) },
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
            headlineContent = { Text(title) },
            supportingContent = { Text(selectedLabel) },
            modifier = modifier
                .then(if (!enabled) Modifier.alpha(0.38f) else Modifier)
                .clickable(enabled = enabled) { showDialog = true },
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
) {
    val haptics = rememberHaptics()
    var sliderValue by remember(value) { mutableFloatStateOf(value) }

    Column(
        modifier = modifier
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
fun NavRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        modifier = modifier
            .then(if (!enabled) Modifier.alpha(0.38f) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
fun InfoRow(
    title: String,
    value: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        modifier = modifier,
    )
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
        seedColorScheme(currentSeed, isDark)
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                ) {
                    listOf(
                        previewScheme.primary,
                        previewScheme.secondary,
                        previewScheme.tertiary,
                        previewScheme.primaryContainer,
                    ).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(color, CircleShape)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Hue", style = MaterialTheme.typography.bodyMedium)
                    Text("${hue.roundToInt()}°", style = MaterialTheme.typography.bodySmall)
                }
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Colourfulness", style = MaterialTheme.typography.bodyMedium)
                    Text("${chroma.roundToInt()}", style = MaterialTheme.typography.bodySmall)
                }
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
