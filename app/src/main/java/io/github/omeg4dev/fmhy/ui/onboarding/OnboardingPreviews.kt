package io.github.omeg4dev.fmhy.ui.onboarding

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.ColorSource
import io.github.omeg4dev.fmhy.data.OpenLinksWith
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.theme.LocalCalloutColors
import io.github.omeg4dev.fmhy.theme.calloutColorsForSeed
import io.github.omeg4dev.fmhy.theme.materialYouCalloutColors
import io.github.omeg4dev.fmhy.theme.paletteColorScheme
import io.github.omeg4dev.fmhy.ui.common.BookmarksState
import io.github.omeg4dev.fmhy.ui.common.EntryRow
import io.github.omeg4dev.fmhy.ui.common.LocalBookmarks
import io.github.omeg4dev.fmhy.ui.common.LocalHapticsEnabled
import io.github.omeg4dev.fmhy.ui.common.Motion
import kotlinx.coroutines.delay

@Composable
fun LookPreview(
    themeMode: ThemeMode,
    colorSource: ColorSource,
    accentSeed: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val baseColorScheme = remember(themeMode, colorSource, accentSeed, isDark) {
        if (colorSource == ColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            paletteColorScheme(accentSeed, isDark)
        }
    }

    // Animate color changes so tapping swatches smoothly transitions colors
    val animatedPrimary by animateColorAsState(baseColorScheme.primary, Motion.standard(Motion.MEDIUM), label = "primary")
    val animatedPrimaryContainer by animateColorAsState(baseColorScheme.primaryContainer, Motion.standard(Motion.MEDIUM), label = "primaryContainer")
    val animatedOnPrimaryContainer by animateColorAsState(baseColorScheme.onPrimaryContainer, Motion.standard(Motion.MEDIUM), label = "onPrimaryContainer")
    val animatedSurface by animateColorAsState(baseColorScheme.surface, Motion.standard(Motion.MEDIUM), label = "surface")
    val animatedSurfaceContainerLow by animateColorAsState(baseColorScheme.surfaceContainerLow, Motion.standard(Motion.MEDIUM), label = "surfaceContainerLow")
    val animatedSurfaceContainerLowest by animateColorAsState(baseColorScheme.surfaceContainerLowest, Motion.standard(Motion.MEDIUM), label = "surfaceContainerLowest")
    val animatedOnSurface by animateColorAsState(baseColorScheme.onSurface, Motion.standard(Motion.MEDIUM), label = "onSurface")
    val animatedOnSurfaceVariant by animateColorAsState(baseColorScheme.onSurfaceVariant, Motion.standard(Motion.MEDIUM), label = "onSurfaceVariant")
    val animatedOutlineVariant by animateColorAsState(baseColorScheme.outlineVariant, Motion.standard(Motion.MEDIUM), label = "outlineVariant")

    val animatedColorScheme = baseColorScheme.copy(
        primary = animatedPrimary,
        primaryContainer = animatedPrimaryContainer,
        onPrimaryContainer = animatedOnPrimaryContainer,
        surface = animatedSurface,
        surfaceContainerLow = animatedSurfaceContainerLow,
        surfaceContainerLowest = animatedSurfaceContainerLowest,
        onSurface = animatedOnSurface,
        onSurfaceVariant = animatedOnSurfaceVariant,
        outlineVariant = animatedOutlineVariant,
    )

    val sampleEntry = remember {
        Entry(
            id = "ublock",
            num = 1,
            sectionId = "adblocking",
            headingId = null,
            ord = 1,
            name = "uBlock Origin",
            url = "https://ublockorigin.pages.dev/",
            originalUrl = null,
            description = "Adblocker / Element Blocker",
            tags = listOf("Adblocker"),
            headingPath = "Adblocking / Extensions",
            tier = Tier.STAR,
            isInlineMore = false,
            isBase64 = false,
            mirrors = emptyList(),
            extraLinks = emptyList(),
            firstSeen = 0L,
            lastChanged = 0L,
            starredAt = 1000L,
        )
    }

    val dummyBookmarks = remember {
        BookmarksState(
            savedIds = emptySet(),
            toggle = {},
            folders = emptyList(),
            savedInfo = { null },
            setNote = { _, _ -> },
            moveToFolder = { _, _ -> },
            createFolder = { _, _ -> },
        )
    }

    val calloutColors = remember(colorSource, accentSeed, baseColorScheme, isDark) {
        if (colorSource == ColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            materialYouCalloutColors(baseColorScheme.primary, baseColorScheme.surface, isDark)
        } else {
            calloutColorsForSeed(accentSeed, isDark)
        }
    }

    CompositionLocalProvider(
        LocalCalloutColors provides calloutColors,
    ) {
        MaterialTheme(colorScheme = animatedColorScheme) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Live appearance preview with theme and accent colors" },
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Mini Home header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "FMHY",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ) {
                            Text(
                                text = if (isDark) "Dark" else "Light",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    // 2 sample section rows
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Video / Movies & TV",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Audio / Music Streaming",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    // Sample EntryRow
                    CompositionLocalProvider(
                        LocalBookmarks provides dummyBookmarks,
                        LocalHapticsEnabled provides false,
                    ) {
                        EntryRow(
                            entry = sampleEntry,
                            onOpenUrl = {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BrowsingPreview(
    openLinksWith: OpenLinksWith,
    adblockLevel: AdblockLevel,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Browser preview: $openLinksWith" },
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            when (openLinksWith) {
                OpenLinksWith.IN_APP -> {
                    InAppBrowsingMock(adblockLevel = adblockLevel)
                }
                OpenLinksWith.CUSTOM_TAB -> {
                    CustomTabMock()
                }
                OpenLinksWith.EXTERNAL -> {
                    ExternalBrowserMock()
                }
            }
        }
    }
}

@Composable
private fun InAppBrowsingMock(adblockLevel: AdblockLevel) {
    // How many of the three ads are gone. Saveable so the demo plays once and doesn't loop or replay on
    // recomposition; turning the adblocker off and on again plays it once more.
    var removedAds by rememberSaveable { mutableIntStateOf(0) }
    val blockedCount = if (adblockLevel == AdblockLevel.OFF) 0 else removedAds

    LaunchedEffect(adblockLevel == AdblockLevel.OFF) {
        if (adblockLevel == AdblockLevel.OFF) {
            removedAds = 0
            return@LaunchedEffect
        }
        if (removedAds == 0) delay(350L)
        while (removedAds < 3) {
            removedAds++
            if (removedAds < 3) delay(450L)
        }
    }

    // Browser toolbar mock
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shield badge counter with animated slide-up numbers
            Surface(
                color = if (adblockLevel == AdblockLevel.OFF) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = CircleShape,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (adblockLevel == AdblockLevel.OFF) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(16.dp),
                    )
                    AnimatedContent(
                        targetState = if (adblockLevel == AdblockLevel.OFF) "Off" else "$blockedCount blocked",
                        transitionSpec = {
                            (slideInVertically(Motion.standard(Motion.SHORT)) { it } + fadeIn(Motion.standard(Motion.SHORT)))
                                .togetherWith(slideOutVertically(Motion.standard(Motion.SHORT)) { -it } + fadeOut(Motion.standard(Motion.SHORT)))
                        },
                        label = "shield_badge_counter",
                    ) { counterText ->
                        Text(
                            text = counterText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (adblockLevel == AdblockLevel.OFF) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Address pill
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "streamsite.example",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Tabs "2"
            Surface(
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.size(20.dp),
                color = Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "2",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // FMHY site bar
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "★ Starred on FMHY · Video › Streaming",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Mock page with 3 ad blocks that collapse one after another. Gaps live inside each block (no spacedBy),
    // so the page and the card around it shrink smoothly instead of jumping when a block leaves.
    Column(modifier = Modifier.fillMaxWidth()) {
        MockAdBlock(label = "Banner Advertisement", visible = removedAds < 1)
        MockPageContent()
        Spacer(modifier = Modifier.height(6.dp))
        MockAdBlock(label = "Sponsored Ad", visible = removedAds < 2)
        MockPageContent()
        MockAdBlock(label = "Pop-up / Tracker Ad", visible = removedAds < 3, gapAbove = true)
    }
}

@Composable
private fun MockAdBlock(
    label: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    gapAbove: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(Motion.enter(Motion.LONG)) + fadeIn(Motion.enter(Motion.LONG)),
        exit = shrinkVertically(Motion.standard(Motion.LONG)) +
            fadeOut(Motion.exit(Motion.MEDIUM)) +
            scaleOut(Motion.standard(Motion.LONG), targetScale = 0.92f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (gapAbove) Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = "Ad",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            if (!gapAbove) Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun CustomTabMock() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "streamsite.example",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    MockPageContent()
}

@Composable
private fun ExternalBrowserMock() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Opens in your browser app",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tapping links will open your default browser app (Firefox, Brave, Chrome).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun StayUpdatedPreview(
    followedSections: Set<String> = emptySet(),
    sections: List<Section> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val bounce = remember { Animatable(1f) }

    LaunchedEffect(followedSections) {
        bounce.snapTo(1f)
        bounce.animateTo(1.04f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    val firstFollowedTitle = remember(followedSections, sections) {
        if (followedSections.isEmpty()) null
        else sections.firstOrNull { it.id in followedSections }?.title ?: followedSections.first()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Sample notification card for new site updates" },
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // Notification card with bounce on change
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = bounce.value
                        scaleY = bounce.value
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "FMHY",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "just now",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (firstFollowedTitle != null) {
                                "3 new sites in $firstFollowedTitle"
                            } else {
                                "Follow a section to get updates"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (firstFollowedTitle != null) {
                                "Fresh sites and resources added to community lists."
                            } else {
                                "Choose categories below to receive notifications when new sites are added."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MockPageContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
        )
    }
}
