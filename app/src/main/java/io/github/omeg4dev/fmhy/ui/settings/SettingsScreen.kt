package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialkolor.hct.Hct
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.SectionOrder
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.theme.ACCENT_PRESETS
import io.github.omeg4dev.fmhy.theme.LocalIsDarkTheme
import io.github.omeg4dev.fmhy.theme.seedColorScheme
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToLegend: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val haptics = rememberHaptics()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            // Group 1: Appearance
            item {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                )
            }

            // Theme selector
            item {
                val themeModes = listOf(
                    ThemeMode.SYSTEM to "System",
                    ThemeMode.LIGHT to "Light",
                    ThemeMode.DARK to "Dark",
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    themeModes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = state.appearance.themeMode == mode,
                            onClick = {
                                haptics.tick()
                                viewModel.setThemeMode(mode)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size),
                            label = { Text(label) },
                        )
                    }
                }
            }

            // Text size row
            item {
                var textScaleSliderValue by remember(state.appearance.textScale) {
                    mutableFloatStateOf(state.appearance.textScale)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Text size", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${(textScaleSliderValue * 100).roundToInt()} %",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = textScaleSliderValue,
                    onValueChange = {
                        if ((it * 100).roundToInt() != (textScaleSliderValue * 100).roundToInt()) {
                            haptics.tick()
                        }
                        textScaleSliderValue = it
                    },
                    onValueChangeFinished = { viewModel.setTextScale(textScaleSliderValue) },
                    valueRange = 0.85f..1.30f,
                    steps = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                val currentDensity = LocalDensity.current
                val previewFontScale = if (state.appearance.textScale > 0f) {
                    (currentDensity.fontScale / state.appearance.textScale) * textScaleSliderValue
                } else {
                    currentDensity.fontScale * textScaleSliderValue
                }
                CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, previewFontScale)) {
                    Text(
                        text = "uBlock Origin — Adblocker / Element Blocker",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
            }

            // Colours selector
            item {
                val fmhyPreviewScheme = remember(state.appearance.accentSeed, isDark) {
                    seedColorScheme(state.appearance.accentSeed, isDark)
                }
                val isMaterialYouAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val materialYouPreviewScheme = remember(isDark, isMaterialYouAvailable) {
                    if (isMaterialYouAvailable) {
                        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    } else {
                        null
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Accent Card
                    val isFmhySelected = state.appearance.colorSource == ColorSource.FMHY
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = isFmhySelected,
                                role = Role.RadioButton,
                                onClick = {
                                    haptics.tick()
                                    viewModel.setColorSource(ColorSource.FMHY)
                                }
                            ),
                        border = BorderStroke(
                            if (isFmhySelected) 2.dp else 1.dp,
                            if (isFmhySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                listOf(
                                    fmhyPreviewScheme.primary,
                                    fmhyPreviewScheme.secondary,
                                    fmhyPreviewScheme.tertiary,
                                    fmhyPreviewScheme.surfaceContainerHighest,
                                ).forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(color, CircleShape)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Accent", style = MaterialTheme.typography.titleSmall)
                                if (isFmhySelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Material You Card
                    val isMaterialYouSelected = state.appearance.colorSource == ColorSource.MATERIAL_YOU
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = isMaterialYouSelected,
                                enabled = isMaterialYouAvailable,
                                role = Role.RadioButton,
                                onClick = {
                                    if (isMaterialYouAvailable) {
                                        haptics.tick()
                                        viewModel.setColorSource(ColorSource.MATERIAL_YOU)
                                    }
                                }
                            )
                            .then(if (!isMaterialYouAvailable) Modifier.alpha(0.38f) else Modifier),
                        border = BorderStroke(
                            if (isMaterialYouSelected) 2.dp else 1.dp,
                            if (isMaterialYouSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                val colors = if (materialYouPreviewScheme != null) {
                                    listOf(
                                        materialYouPreviewScheme.primary,
                                        materialYouPreviewScheme.secondary,
                                        materialYouPreviewScheme.tertiary,
                                        materialYouPreviewScheme.surfaceContainerHighest,
                                    )
                                } else {
                                    listOf(
                                        fmhyPreviewScheme.primary,
                                        fmhyPreviewScheme.secondary,
                                        fmhyPreviewScheme.tertiary,
                                        fmhyPreviewScheme.surfaceContainerHighest,
                                    )
                                }
                                colors.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(color, CircleShape)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Material You", style = MaterialTheme.typography.titleSmall)
                                if (isMaterialYouSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (!isMaterialYouAvailable) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Needs Android 12+",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (state.appearance.colorSource == ColorSource.FMHY) {
                    var showCustomDialog by remember { mutableStateOf(false) }
                    val isCustomSelected = ACCENT_PRESETS.none { it.seed == state.appearance.accentSeed }
                    val sweepBrush = remember {
                        Brush.sweepGradient(
                            listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red,
                            )
                        )
                    }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ACCENT_PRESETS.forEach { preset ->
                            // The raw seed reads better than the pastel scheme primary: red, orange and pink stay distinct.
                            val swatch = Color(preset.seed)
                            val isSelected = state.appearance.accentSeed == preset.seed
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .background(swatch, CircleShape)
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            haptics.tick()
                                            viewModel.setAccentSeed(preset.seed)
                                        }
                                    )
                                    .semantics { contentDescription = preset.name },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (swatch.luminance() > 0.4f) Color.Black else Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

                        val customColor = remember(state.appearance.accentSeed, isDark) {
                            seedColorScheme(state.appearance.accentSeed, isDark).primary
                        }
                        val customOnColor = remember(state.appearance.accentSeed, isDark) {
                            seedColorScheme(state.appearance.accentSeed, isDark).onPrimary
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .then(
                                    if (isCustomSelected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .then(
                                    if (isCustomSelected) {
                                        Modifier.background(customColor, CircleShape)
                                    } else {
                                        Modifier.background(sweepBrush, CircleShape)
                                    }
                                )
                                .selectable(
                                    selected = isCustomSelected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        haptics.tick()
                                        showCustomDialog = true
                                    }
                                )
                                .semantics { contentDescription = "Custom" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isCustomSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = customOnColor,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    if (showCustomDialog) {
                        CustomColorDialog(
                            initialSeed = state.appearance.accentSeed,
                            isDark = isDark,
                            onDismiss = { showCustomDialog = false },
                            onConfirm = { seed ->
                                viewModel.setAccentSeed(seed)
                            }
                        )
                    }
                }
            }

            // Pure black switch
            item {
                ListItem(
                    headlineContent = { Text("Pure black") },
                    supportingContent = {
                        Text(
                            if (isDark) "Black background in dark mode (saves battery on OLED)"
                            else "Only in dark mode"
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = state.appearance.pureBlack,
                            onCheckedChange = null,
                            enabled = isDark,
                        )
                    },
                    modifier = Modifier.toggleable(
                        value = state.appearance.pureBlack,
                        enabled = isDark,
                        role = Role.Switch,
                        onValueChange = {
                            haptics.tick()
                            viewModel.setPureBlack(it)
                        },
                    ),
                )
            }

            // Haptic feedback switch
            item {
                ListItem(
                    headlineContent = { Text("Haptic feedback") },
                    supportingContent = {
                        Text("Vibration on taps, holds, and toggles")
                    },
                    trailingContent = {
                        Switch(
                            checked = state.appearance.hapticFeedback,
                            onCheckedChange = null,
                        )
                    },
                    modifier = Modifier.toggleable(
                        value = state.appearance.hapticFeedback,
                        role = Role.Switch,
                        onValueChange = {
                            haptics.tick()
                            viewModel.setHapticFeedback(it)
                        },
                    ),
                )
            }

            // Section layout
            item {
                ListItem(
                    headlineContent = { Text("Section layout") }
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    val layouts = listOf(
                        SectionLayout.CONTINUOUS to "Continuous",
                        SectionLayout.CATEGORIES to "Categories",
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        layouts.forEachIndexed { index, (layout, label) ->
                            SegmentedButton(
                                selected = state.appearance.sectionLayout == layout,
                                onClick = {
                                    haptics.tick()
                                    viewModel.setSectionLayout(layout)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = layouts.size),
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (state.appearance.sectionLayout) {
                            SectionLayout.CONTINUOUS -> "One scroll with a category bar"
                            SectionLayout.CATEGORIES -> "Pick a category first"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
            }

            // Spacer between groups
            item {
                Spacer(Modifier.height(8.dp))
            }

            // Group: Home
            item {
                Text(
                    text = "Home",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Show random pick") },
                    supportingContent = { Text("A random recommended site on the Browse tab") },
                    trailingContent = {
                        Switch(
                            checked = state.appearance.showRandomPick,
                            onCheckedChange = null,
                        )
                    },
                    modifier = Modifier.toggleable(
                        value = state.appearance.showRandomPick,
                        role = Role.Switch,
                        onValueChange = {
                            haptics.tick()
                            viewModel.setShowRandomPick(it)
                        },
                    ),
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Section order") }
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    val orders = listOf(
                        SectionOrder.WEBSITE to "Website",
                        SectionOrder.ALPHABETICAL to "A–Z",
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        orders.forEachIndexed { index, (order, label) ->
                            SegmentedButton(
                                selected = state.appearance.sectionOrder == order,
                                onClick = {
                                    haptics.tick()
                                    viewModel.setSectionOrder(order)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = orders.size),
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Website uses the order of fmhy.net's sidebar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
            }

            // Spacer between groups
            item {
                Spacer(Modifier.height(8.dp))
            }

            // Group: Lists
            item {
                Text(
                    text = "Lists",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Starred only") },
                    supportingContent = { Text("Show only sites FMHY recommends (⭐ / 🌟)") },
                    trailingContent = {
                        Switch(
                            checked = state.appearance.starredOnly,
                            onCheckedChange = null,
                        )
                    },
                    modifier = Modifier.toggleable(
                        value = state.appearance.starredOnly,
                        role = Role.Switch,
                        onValueChange = {
                            haptics.tick()
                            viewModel.setStarredOnly(it)
                        },
                    ),
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Show indexes") },
                    supportingContent = { Text("Include 🌐 index sites that list other sites") },
                    trailingContent = {
                        Switch(
                            checked = state.appearance.showIndexes,
                            onCheckedChange = null,
                        )
                    },
                    modifier = Modifier.toggleable(
                        value = state.appearance.showIndexes,
                        role = Role.Switch,
                        onValueChange = {
                            haptics.tick()
                            viewModel.setShowIndexes(it)
                        },
                    ),
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Icon legend") },
                    supportingContent = { Text("What every icon means") },
                    modifier = Modifier.clickable { onNavigateToLegend() },
                )
            }

            // Spacer between groups
            item {
                Spacer(Modifier.height(8.dp))
            }

            // Group: Links
            item {
                Text(
                    text = "Links",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Open links in external browser") },
                    supportingContent = {
                        Text(if (state.openInExternalBrowser) "Always opens in default browser" else "Opens in Chrome Custom Tabs")
                    },
                    leadingContent = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = state.openInExternalBrowser,
                            onCheckedChange = null,
                        )
                    },
                    modifier = Modifier.toggleable(
                        value = state.openInExternalBrowser,
                        role = Role.Switch,
                        onValueChange = {
                            haptics.tick()
                            viewModel.setOpenInExternalBrowser(it)
                        },
                    ),
                )
            }

            // Spacer between groups
            item {
                Spacer(Modifier.height(8.dp))
            }

            // Group 3: Data
            item {
                Text(
                    text = "Data",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Sites") },
                    supportingContent = {
                        Text(if (state.entryCount > 0) "%,d sites".format(state.entryCount) else "No dataset loaded")
                    },
                    leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Last synced") },
                    supportingContent = { Text(state.lastSyncedText) },
                    leadingContent = { Icon(Icons.Default.Sync, contentDescription = null) }
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.updateNow() },
                        enabled = !state.isUpdating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isUpdating) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Syncing…")
                        } else {
                            Text("Sync now")
                        }
                    }
                }
                if (!state.updateStatusMessage.isNullOrEmpty()) {
                    Text(
                        text = state.updateStatusMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // Spacer between groups
            item {
                Spacer(Modifier.height(8.dp))
            }

            // Group 4: About
            item {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("About FMHY") },
                    supportingContent = {
                        Text("Unofficial client for FMHY. Data: github.com/fmhy/edit. GPL-3.0.")
                    },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }

            item {
                val versionName = remember(context) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.packageManager.getPackageInfo(
                                context.packageName,
                                PackageManager.PackageInfoFlags.of(0)
                            ).versionName
                        } else {
                            @Suppress("DEPRECATION")
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        }
                    } catch (_: Exception) {
                        null
                    } ?: "1.0.0"
                }
                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text(versionName) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
private fun CustomColorDialog(
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
