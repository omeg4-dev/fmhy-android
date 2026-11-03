package io.github.omeg4dev.fmhy.ui.home.blocks

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.sectionDisplayTitle
import io.github.omeg4dev.fmhy.ui.common.sectionIcon
import io.github.omeg4dev.fmhy.ui.home.ResolvedPin

fun LazyListScope.pinnedBlock(
    pins: List<ResolvedPin>,
    followedKeys: Set<Pair<String, String>>,
    onNavigateToSection: (sectionId: String, anchor: String?) -> Unit,
    onUnpin: (pin: ResolvedPin) -> Unit,
    onToggleFollow: (pin: ResolvedPin, isFollowed: Boolean) -> Unit,
) {
    if (pins.isEmpty()) {
        item(key = "pins-empty-card") {
            Column(
                modifier = Modifier
                    .animateItem()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Pinned",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Pin sections or categories from their menu to see them here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    } else {
        item(key = "pins-block") {
            Column(
                modifier = Modifier
                    .animateItem()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Pinned",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    pins.chunked(2).forEach { rowPins ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                        ) {
                            rowPins.forEach { pin ->
                                val isFollowed = (pin.sectionId to pin.headingId) in followedKeys
                                PinTile(
                                    pin = pin,
                                    isFollowed = isFollowed,
                                    onNavigate = {
                                        onNavigateToSection(pin.sectionId, pin.anchor)
                                    },
                                    onUnpin = { onUnpin(pin) },
                                    onToggleFollow = { onToggleFollow(pin, isFollowed) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            }
                            if (rowPins.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinTile(
    pin: ResolvedPin,
    isFollowed: Boolean,
    onNavigate: () -> Unit,
    onUnpin: () -> Unit,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    Box(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 56.dp)
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = onNavigate,
                    onLongClick = {
                        haptics.longPress()
                        menuExpanded = true
                    },
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = sectionIcon(pin.sectionId),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    val displayTitle = if (pin.headingId.isEmpty()) sectionDisplayTitle(pin.sectionId, pin.title) else pin.title
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (pin.parentTitle != null) {
                        Text(
                            text = sectionDisplayTitle(pin.sectionId, pin.parentTitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Unpin") },
                onClick = {
                    menuExpanded = false
                    onUnpin()
                },
            )
            DropdownMenuItem(
                text = { Text(if (isFollowed) "Unfollow" else "Follow") },
                onClick = {
                    menuExpanded = false
                    onToggleFollow()
                },
            )
        }
    }
}
