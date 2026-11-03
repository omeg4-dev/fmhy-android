package io.github.omeg4dev.fmhy.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import sh.calvin.reorderable.ReorderableColumn

@Composable
fun EditHomeList(
    layout: HomeLayout,
    onChange: ((HomeLayout) -> HomeLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.verticalScroll(scrollState),
    ) {
        Text(
            text = "Drag the handle to reorder. Tap the eye to show or hide a block.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        ReorderableColumn(
            list = layout.entries,
            onSettle = { from, to -> onChange { it.move(from, to) } },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { index, entry, isDragging ->
            val elevation by animateDpAsState(
                targetValue = if (isDragging) 4.dp else 0.dp,
                label = "elevation",
            )
            val blockInfo = homeBlockInfo(entry.block)
            val actions = remember(index, layout.entries.size) {
                buildList {
                    if (index > 0) {
                        add(
                            CustomAccessibilityAction("Move up") {
                                onChange { it.move(index, index - 1) }
                                true
                            }
                        )
                    }
                    if (index < layout.entries.lastIndex) {
                        add(
                            CustomAccessibilityAction("Move down") {
                                onChange { it.move(index, index + 1) }
                                true
                            }
                        )
                    }
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = elevation,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .semantics { customActions = actions }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Reorder ${blockInfo.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .draggableHandle()
                            .size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    val contentAlpha = if (entry.enabled) 1f else 0.5f
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .alpha(contentAlpha),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = blockInfo.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = blockInfo.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = blockInfo.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            onChange { it.withEnabled(entry.block, !entry.enabled) }
                        },
                    ) {
                        Icon(
                            imageVector = if (entry.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (entry.enabled) "Hide ${blockInfo.title}" else "Show ${blockInfo.title}",
                            tint = if (entry.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 16.dp + LocalBottomBarPadding.current,
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            TextButton(
                onClick = { onChange { it.reset() } },
                enabled = layout != HomeLayout.default(),
            ) {
                Text("Reset to default")
            }
        }
    }
}
