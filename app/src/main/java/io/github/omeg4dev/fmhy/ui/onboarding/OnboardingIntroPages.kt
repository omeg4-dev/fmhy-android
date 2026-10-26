package io.github.omeg4dev.fmhy.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.painterResource
import io.github.omeg4dev.fmhy.R
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.theme.ACCENT_PRESETS
import io.github.omeg4dev.fmhy.theme.paletteColorScheme
import io.github.omeg4dev.fmhy.ui.common.enterStagger
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics

private val LogoCyan = Color(0xFF00E5FF)
private val LogoViolet = Color(0xFF7C5CFF)
private val LogoMagenta = Color(0xFFFF3EC9)
private val LogoAmber = Color(0xFFFFC14D)

/** True when the user turned animations off in system settings (animator scale 0). */
@Composable
private fun rememberAnimationsOff(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

// ==========================================
// 1. WELCOME PAGE (pitch, no settings)
// ==========================================
@Composable
fun WelcomePageContent(modifier: Modifier = Modifier) {
    val key = OnboardingPage.WELCOME
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Hero: FMHY banner over a soft glow (index 0)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .enterStagger(index = 0, key = key),
                contentAlignment = Alignment.Center,
            ) {
                WelcomeGlow(modifier = Modifier.fillMaxSize())
                Image(
                    painter = painterResource(if (dark) R.drawable.fmhy_banner else R.drawable.fmhy_banner_light),
                    contentDescription = "FMHY banner",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Headline + one-liner (index 1)
            Column(
                modifier = Modifier.enterStagger(index = 1, key = key),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "The whole FMHY wiki, in your pocket",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Free, no account, works offline.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Value rows (index 2..4)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ValueRow(
                        icon = Icons.Default.Search,
                        tint = LogoCyan,
                        title = "Search everything",
                        text = "Every FMHY site, searchable offline, with stars for the best ones.",
                        modifier = Modifier.enterStagger(index = 2, key = key),
                    )
                    ValueRow(
                        icon = Icons.Default.VerifiedUser,
                        tint = LogoViolet,
                        title = "Check a link",
                        text = "See if a site is listed on FMHY or a known fake before you open it.",
                        modifier = Modifier.enterStagger(index = 3, key = key),
                    )
                    ValueRow(
                        icon = Icons.Default.Block,
                        tint = LogoMagenta,
                        title = "Browse ad-free",
                        text = "Built-in browser with uBlock-grade ad blocking.",
                        modifier = Modifier.enterStagger(index = 4, key = key),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Unofficial fan project. Not affiliated with FMHY.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.enterStagger(index = 5, key = key),
            )
        }
    }
}

@Composable
private fun ValueRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = lerp(tint, MaterialTheme.colorScheme.onSurface, 0.35f),
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Soft radial glow in the logo colours; drifts slowly (7 s) unless animations are off. */
@Composable
private fun WelcomeGlow(modifier: Modifier = Modifier) {
    val animationsOff = rememberAnimationsOff()
    val phase = if (animationsOff) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "welcome_glow")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
            label = "welcome_glow_phase",
        )
        p
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = h * 0.9f
        val blobs = listOf(
            Triple(LogoCyan, -0.20f, 0.0f),
            Triple(LogoViolet, -0.07f, 0.1f),
            Triple(LogoMagenta, 0.07f, 0.2f),
            Triple(LogoAmber, 0.20f, 0.3f),
        )
        blobs.forEachIndexed { i, (color, x, _) ->
            val dx = (phase - 0.5f) * w * 0.06f * (if (i % 2 == 0) 1 else -1)
            val center = Offset(w / 2 + w * x * 1.6f + dx, h / 2)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.22f), Color.Transparent),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }
    }
}

// ==========================================
// 2. HELLO PAGE (appearance)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelloPageContent(
    state: OnboardingState,
    model: OnboardingModel,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val key = OnboardingPage.HELLO
    val isMaterialYouAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val materialYouSelected = isMaterialYouAvailable && state.colorSource == ColorSource.MATERIAL_YOU

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Title block (index 0)
            Column(
                modifier = Modifier.enterStagger(index = 0, key = key),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StepIcon()
                    Text(
                        text = "Make it yours",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pick a theme and an accent. You can change it any time in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Live preview hero (index 1)
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .enterStagger(index = 1, key = key)
                    .shadow(6.dp, RoundedCornerShape(16.dp)),
            ) {
                LookPreview(
                    themeMode = state.themeMode,
                    colorSource = state.colorSource,
                    accentSeed = state.accentSeed,
                    websiteStyle = state.websiteStyle,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Theme (index 2)
            Column(
                modifier = Modifier.enterStagger(index = 2, key = key),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val modes = listOf(
                    Triple(ThemeMode.SYSTEM, "System", Icons.Default.BrightnessAuto),
                    Triple(ThemeMode.LIGHT, "Light", Icons.Default.LightMode),
                    Triple(ThemeMode.DARK, "Dark", Icons.Default.DarkMode),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, (mode, label, icon) ->
                        SegmentedButton(
                            selected = state.themeMode == mode,
                            onClick = {
                                haptics.tick()
                                model.setThemeMode(mode)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = state.themeMode == mode) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                    )
                                }
                            },
                        ) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (state.themeMode == ThemeMode.SYSTEM) "System follows your phone (recommended)" else " ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Accent grid (index 3)
            Column(
                modifier = Modifier.enterStagger(index = 3, key = key),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                data class Swatch(val name: String, val color: Color?, val selected: Boolean, val onPick: () -> Unit)

                val isSystemDark = isSystemInDarkTheme()
                val isDark = when (state.themeMode) {
                    ThemeMode.SYSTEM -> isSystemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }

                val swatches = buildList {
                    if (isMaterialYouAvailable) {
                        add(Swatch("Material You", null, materialYouSelected) {
                            model.setColorSource(ColorSource.MATERIAL_YOU)
                        })
                    }
                    ACCENT_PRESETS.forEach { preset ->
                        add(
                            Swatch(
                                preset.name,
                                paletteColorScheme(preset.seed, isDark).primary,
                                state.colorSource == ColorSource.FMHY && state.accentSeed == preset.seed,
                            ) { model.setAccentSeed(preset.seed) },
                        )
                    }
                }
                swatches.chunked(6).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        rowItems.forEach { sw ->
                            AccentSwatch(
                                name = sw.name,
                                color = sw.color,
                                selected = sw.selected,
                                onClick = {
                                    haptics.tick()
                                    sw.onPick()
                                },
                            )
                        }
                    }
                }

                val selectedName = swatches.firstOrNull { it.selected }?.name ?: ""
                val caption = when (selectedName) {
                    "Material You" -> "Material You · follows your wallpaper"
                    else -> selectedName
                }
                AnimatedContent(
                    targetState = caption,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "accent_caption",
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // List style (index 4)
            Column(
                modifier = Modifier.enterStagger(index = 4, key = key),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "List style",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                ListStyleChoice(
                    websiteStyle = state.websiteStyle,
                    onSelect = {
                        haptics.tick()
                        model.setWebsiteStyle(it)
                    },
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

/** 44 dp slot; the colour disc shrinks to leave a gap inside the selection ring (spring pop). */
@Composable
private fun AccentSwatch(
    name: String,
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val disc by animateDpAsState(
        targetValue = if (selected) 32.dp else 40.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swatch_disc_$name",
    )
    val onColor = when {
        color == null -> Color.White
        color.luminance() > 0.4f -> Color.Black
        else -> Color.White
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
            )
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$name accent" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(disc)
                .clip(CircleShape)
                .then(
                    if (color != null) {
                        Modifier.background(color)
                    } else {
                        Modifier.background(
                            Brush.sweepGradient(listOf(LogoCyan, LogoViolet, LogoMagenta, LogoAmber, LogoCyan)),
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = onColor,
                    modifier = Modifier.size(18.dp),
                )
            } else if (color == null) {
                Icon(
                    Icons.Default.Wallpaper,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
