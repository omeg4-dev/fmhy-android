package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.selection.toggleable
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.SectionLayout
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.theme.LocalIsDarkTheme
import io.github.omeg4dev.fmhy.theme.seedColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current

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
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size),
                            label = { Text(label) },
                        )
                    }
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
                    // FMHY Card
                    val isFmhySelected = state.appearance.colorSource == ColorSource.FMHY
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = isFmhySelected,
                                role = Role.RadioButton,
                                onClick = { viewModel.setColorSource(ColorSource.FMHY) }
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
                                Text("FMHY", style = MaterialTheme.typography.titleSmall)
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
                        onValueChange = { viewModel.setPureBlack(it) },
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
                                onClick = { viewModel.setSectionLayout(layout) },
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

            // Group 2: Links
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
                        onValueChange = { viewModel.setOpenInExternalBrowser(it) },
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
                        Text(if (state.entryCount > 0) "${state.entryCount} entries" else "No dataset loaded")
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
