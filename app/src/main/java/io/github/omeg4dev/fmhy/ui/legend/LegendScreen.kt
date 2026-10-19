package io.github.omeg4dev.fmhy.ui.legend

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.ui.common.Platform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegendScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Icon legend") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            // Group 1: Markers
            item {
                LegendGroupHeader("Markers")
            }
            item {
                LegendRowItem(
                    name = "Recommended",
                    explanation = "FMHY's pick — standout site or tool recommended by the community.",
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_marker_star),
                        contentDescription = "Recommended",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            item {
                LegendRowItem(
                    name = "Top pick",
                    explanation = "Highlighted star — FMHY's absolute top choice in a category.",
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_marker_top),
                        contentDescription = "Top pick",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            item {
                LegendRowItem(
                    name = "Index",
                    explanation = "A curated list, directory, or collection linking to more sites.",
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_marker_index),
                        contentDescription = "Index",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            item {
                LegendRowItem(
                    name = "Section link",
                    explanation = "Opens another part of the wiki within the app.",
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_marker_section_link),
                        contentDescription = "Section link",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Group 2: Platforms
            item {
                LegendGroupHeader("Platforms")
            }
            item {
                PlatformLegendRow(Platform.WINDOWS, "Available for Windows")
            }
            item {
                PlatformLegendRow(Platform.MACOS, "Available for macOS")
            }
            item {
                PlatformLegendRow(Platform.LINUX, "Available for Linux")
            }
            item {
                PlatformLegendRow(Platform.ANDROID, "Available for Android")
            }
            item {
                PlatformLegendRow(Platform.IOS, "Available for iOS")
            }
            item {
                PlatformLegendRow(Platform.WEB, "Runs in the browser")
            }
            item {
                PlatformLegendRow(Platform.DOCKER, "Available for Docker")
            }

            // Group 3: Links & sources
            item {
                LegendGroupHeader("Links & sources")
            }
            item {
                LinkLegendRow(R.drawable.ic_link_discord, "Discord", "Opens the official Discord server")
            }
            item {
                LinkLegendRow(R.drawable.ic_link_github, "GitHub", "Opens the GitHub project repository")
            }
            item {
                LinkLegendRow(R.drawable.ic_link_gitlab, "GitLab", "Opens the GitLab project repository")
            }
            item {
                LinkLegendRow(R.drawable.ic_link_telegram, "Telegram", "Opens the Telegram channel or group")
            }
            item {
                LinkLegendRow(R.drawable.ic_link_reddit, "Reddit", "Opens the subreddit community")
            }
            item {
                LinkLegendRow(R.drawable.ic_link_x, "X", "Opens the X (formerly Twitter) profile")
            }
            item {
                LinkLegendRow(R.drawable.ic_link_mastodon, "Mastodon", "Opens the Mastodon profile or instance")
            }
            item {
                LinkLegendRow(R.drawable.ic_link_bluesky, "Bluesky", "Opens the Bluesky profile")
            }
            item {
                LinkLegendRow(R.drawable.ic_link_onion, ".onion", "Opens the Tor hidden service mirror (.onion link)")
            }
            item {
                LinkLegendRow(R.drawable.ic_link_source, "Source", "Opens the source code repository")
            }
            item {
                LinkLegendRow(R.drawable.ic_link_note, "Note", "Shows extra notes or instructions for the site")
            }

            // Group 4: Rows
            item {
                LegendGroupHeader("Rows")
            }
            item {
                LegendRowItem(
                    name = "+N pill",
                    explanation = "N more sites on this line — tap the row to see them",
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "+2",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            item {
                LegendRowItem(
                    name = "Mirrors (Name, 2)",
                    explanation = "Numbered mirrors — tap to open alternative domains for the site",
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Site",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = ",",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = " 2",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            item {
                LegendRowItem(
                    name = "Decoded link",
                    explanation = "Base64 link decoded automatically by the app",
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = "Decoded link",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            item {
                LegendRowItem(
                    name = "Saved bookmark",
                    explanation = "Bookmarked entry saved to your library",
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = "Saved",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            item {
                LegendRowItem(
                    name = "Cross-link",
                    explanation = "Opens another wiki section or page",
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Cross-link",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            item {
                LegendRowItem(
                    name = "TIP callout",
                    explanation = "Helpful context, recommendations, or section notes",
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
            item {
                LegendRowItem(
                    name = "WARNING callout",
                    explanation = "Important caution, security notice, or domain change alert",
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            // Group 5: Gestures
            item {
                LegendGroupHeader("Gestures")
            }
            item {
                LegendRowItem(
                    name = "Tap a row",
                    explanation = "Opens the details sheet with all sites on the line, mirrors, and extra links",
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TouchApp,
                        contentDescription = "Tap",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            item {
                LegendRowItem(
                    name = "Hold a row",
                    explanation = "Opens the primary website directly in your browser",
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Hold to open",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun PlatformLegendRow(platform: Platform, explanation: String) {
    LegendRowItem(
        name = platform.word,
        explanation = explanation,
    ) {
        Icon(
            painter = painterResource(platform.icon),
            contentDescription = platform.word,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LinkLegendRow(iconRes: Int, name: String, explanation: String) {
    LegendRowItem(
        name = name,
        explanation = explanation,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = name,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LegendRowItem(
    name: String,
    explanation: String,
    modifier: Modifier = Modifier,
    iconSlot: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(48.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            iconSlot()
        }
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
