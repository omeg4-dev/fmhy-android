package io.github.omeg4dev.fmhy.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import io.github.omeg4dev.fmhy.data.AppIcon
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.theme.ACCENT_PRESETS
import io.github.omeg4dev.fmhy.theme.LocalIsDarkTheme
import io.github.omeg4dev.fmhy.theme.seedColorScheme
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearancePage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appearance = state.appearance
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val haptics = rememberHaptics()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            // Theme selector
            item {
                ChoiceRow(
                    title = "Theme",
                    options = listOf(
                        ThemeMode.SYSTEM to "System",
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark",
                    ),
                    selected = appearance.themeMode,
                    onSelect = { viewModel.setThemeMode(it) },
                )
            }

            // Text size row + preview
            item {
                var textScaleSliderValue by remember(appearance.textScale) {
                    mutableFloatStateOf(appearance.textScale)
                }
                SliderRow(
                    title = "Text size",
                    value = textScaleSliderValue,
                    valueRange = 0.85f..1.30f,
                    steps = 8,
                    valueLabel = "${(textScaleSliderValue * 100).roundToInt()} %",
                    onValueChange = { textScaleSliderValue = it },
                    onValueChangeFinished = { viewModel.setTextScale(textScaleSliderValue) },
                )
                val currentDensity = LocalDensity.current
                val previewFontScale = if (appearance.textScale > 0f) {
                    (currentDensity.fontScale / appearance.textScale) * textScaleSliderValue
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
                val fmhyPreviewScheme = remember(appearance.accentSeed, isDark) {
                    seedColorScheme(appearance.accentSeed, isDark)
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
                    val isFmhySelected = appearance.colorSource == ColorSource.FMHY
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
                    val isMaterialYouSelected = appearance.colorSource == ColorSource.MATERIAL_YOU
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

                if (appearance.colorSource == ColorSource.FMHY) {
                    var showCustomDialog by remember { mutableStateOf(false) }
                    val isCustomSelected = ACCENT_PRESETS.none { it.seed == appearance.accentSeed }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ACCENT_PRESETS.forEach { preset ->
                            val swatch = Color(preset.seed)
                            val isSelected = appearance.accentSeed == preset.seed
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

                        val customColor = remember(appearance.accentSeed, isDark) {
                            seedColorScheme(appearance.accentSeed, isDark).primary
                        }
                        val customOnColor = remember(appearance.accentSeed, isDark) {
                            seedColorScheme(appearance.accentSeed, isDark).onPrimary
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
                                .background(if (isCustomSelected) customColor else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    if (showCustomDialog) {
                        CustomColorDialog(
                            initialSeed = appearance.accentSeed,
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
                SwitchRow(
                    title = "Pure black",
                    summary = if (isDark) "Black background in dark mode (saves battery on OLED)" else "Only in dark mode",
                    checked = appearance.pureBlack,
                    enabled = isDark,
                    onChange = { viewModel.setPureBlack(it) },
                )
            }

            // Haptic feedback switch
            item {
                SwitchRow(
                    title = "Haptic feedback",
                    summary = "Vibration on taps, holds, and toggles",
                    checked = appearance.hapticFeedback,
                    onChange = { viewModel.setHapticFeedback(it) },
                )
            }

            // App icon
            item {
                ChoiceRow(
                    title = "App icon",
                    options = listOf(
                        AppIcon.DEFAULT to "Default",
                        AppIcon.UWU to "UwU",
                        AppIcon.MONOCHROME to "Monochrome",
                    ),
                    selected = appearance.appIcon,
                    onSelect = { viewModel.setAppIcon(it) },
                )
                Text(
                    text = "The launcher may take a moment to update.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            extraRows()
        }
    }
}
